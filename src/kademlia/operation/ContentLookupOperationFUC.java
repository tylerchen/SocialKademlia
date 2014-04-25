package kademlia.operation;

import kademlia.message.Receiver;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import kademlia.dht.GetParameter;
import kademlia.core.KadConfiguration;
import kademlia.core.KadServer;
import kademlia.dht.GetParameterFUC;
import kademlia.dht.StorageEntry;
import kademlia.exceptions.RoutingException;
import kademlia.exceptions.UnknownMessageException;
import kademlia.exceptions.UpToDateContentException;
import kademlia.message.ContentLookupMessageFUC;
import kademlia.message.ContentMessage;
import kademlia.message.Message;
import kademlia.message.NodeReplyMessage;
import kademlia.message.UpToDateContentMessage;
import kademlia.node.KeyComparator;
import kademlia.node.Node;

/**
 * Looks up a specified Content and returns it
 *
 * @author Joshua Kissoon
 * @since 20140422
 */
public class ContentLookupOperationFUC implements Operation, Receiver
{

    /* Constants */
    private static final Byte UNASKED = (byte) 0x00;
    private static final Byte AWAITING = (byte) 0x01;
    private static final Byte ASKED = (byte) 0x02;
    private static final Byte FAILED = (byte) 0x03;

    private final KadServer server;
    private final Node localNode;
    private final GetParameter params;
    private final List<StorageEntry> contentFound;
    private int numNodesToQuery;
    private final KadConfiguration config;

    private final Message lookupMessage;

    private boolean contentsFound;
    private boolean newerContentExist = false; // Whether the content we have is up to date
    private final SortedMap<Node, Byte> nodes;

    /* Tracks messages in transit and awaiting reply */
    private final Map<Integer, Node> messagesTransiting;

    /* Used to sort nodes */
    private final Comparator comparator;

    
    {
        contentFound = new ArrayList<>();
        messagesTransiting = new HashMap<>();
        contentsFound = false;
    }

    /**
     * @param server
     * @param localNode
     * @param params          The parameters to search for the content which we need to find
     * @param numNodesToQuery The number of nodes to query to get this content. We return the content among these nodes.
     * @param config
     */
    public ContentLookupOperationFUC(KadServer server, Node localNode, GetParameterFUC params, int numNodesToQuery, KadConfiguration config)
    {
        /* Construct our lookup message */
        this.lookupMessage = new ContentLookupMessageFUC(localNode, params);

        this.server = server;
        this.localNode = localNode;
        this.params = params;
        this.numNodesToQuery = numNodesToQuery;
        this.config = config;

        /**
         * We initialize a TreeMap to store nodes.
         * This map will be sorted by which nodes are closest to the lookupId
         */
        this.comparator = new KeyComparator(params.getKey());
        this.nodes = new TreeMap(this.comparator);
    }

    /**
     * @throws java.io.IOException
     * @throws kademlia.exceptions.RoutingException
     */
    @Override
    public synchronized void execute() throws IOException, RoutingException
    {
        try
        {
            /* Set the local node as already asked */
            nodes.put(this.localNode, ASKED);

            this.addNodes(this.localNode.getRoutingTable().getAllNodes());

            /**
             * If we haven't found the requested amount of content as yet,
             * keey trying until config.operationTimeout() time has expired
             */
            int totalTimeWaited = 0;
            int timeInterval = 100;     // We re-check every 300 milliseconds
            while (totalTimeWaited < this.config.operationTimeout())
            {
                if (!this.askNodesorFinish() && !contentsFound)
                {
                    wait(timeInterval);
                    totalTimeWaited += timeInterval;
                }
                else
                {
                    break;
                }
            }
        }
        catch (InterruptedException e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * Add nodes from this list to the set of nodes to lookup
     *
     * @param list The list from which to add nodes
     */
    public void addNodes(List<Node> list)
    {
        for (Node o : list)
        {
            /* If this node is not in the list, add the node */
            if (!nodes.containsKey(o))
            {
                nodes.put(o, UNASKED);
            }
        }
    }

    /**
     * Asks some of the K closest nodes seen but not yet queried.
     * Assures that no more than DefaultConfiguration.CONCURRENCY messages are in transit at a time
     *
     * This method should be called every time a reply is received or a timeout occurs.
     *
     * If all K closest nodes have been asked and there are no messages in transit,
     * the algorithm is finished.
     *
     * @return <code>true</code> if finished OR <code>false</code> otherwise
     */
    private boolean askNodesorFinish() throws IOException
    {
        /* If >= CONCURRENCY nodes are in transit, don't do anything */
        if (this.config.maxConcurrentMessagesTransiting() <= this.messagesTransiting.size())
        {
            return false;
        }

        /* Get unqueried nodes among the K closest seen that have not FAILED */
        List<Node> unasked = this.closestNodesNotFailed(UNASKED);

        if (unasked.isEmpty() && this.messagesTransiting.isEmpty())
        {
            /* We have no unasked nodes nor any messages in transit, we're finished! */
            return true;
        }

        /* Sort nodes according to criteria */
        Collections.sort(unasked, this.comparator);

        /**
         * Send messages to nodes in the list;
         * making sure than no more than CONCURRENCY messsages are in transit
         */
        for (int i = 0; (this.messagesTransiting.size() < this.config.maxConcurrentMessagesTransiting()) && (i < unasked.size()); i++)
        {
            Node n = (Node) unasked.get(i);

            int comm = server.sendMessage(n, lookupMessage, this);

            this.nodes.put(n, AWAITING);
            this.messagesTransiting.put(comm, n);
        }

        /* We're not finished as yet, return false */
        return false;
    }

    /**
     * Find The K closest nodes to the target lookupId given that have not FAILED.
     * From those K, get those that have the specified status
     *
     * @param status The status of the nodes to return
     *
     * @return A List of the closest nodes
     */
    private List<Node> closestNodesNotFailed(Byte status)
    {
        List<Node> closestNodes = new ArrayList<>(this.config.k());
        int remainingSpaces = this.config.k();

        for (Map.Entry e : this.nodes.entrySet())
        {
            if (!FAILED.equals(e.getValue()))
            {
                if (status.equals(e.getValue()))
                {
                    /* We got one with the required status, now add it */
                    closestNodes.add((Node) e.getKey());
                }

                if (--remainingSpaces == 0)
                {
                    break;
                }
            }
        }

        return closestNodes;
    }

    @Override
    public synchronized void receive(Message incoming, int comm) throws IOException, RoutingException
    {
        if (this.contentsFound)
        {
            return;
        }

        if (incoming instanceof ContentMessage)
        {
            /* The reply received is a content message with the required content, take it in */
            ContentMessage msg = (ContentMessage) incoming;

            /* Add the origin node to our routing table */
            this.localNode.getRoutingTable().insert(msg.getOrigin());

            /* Get the Content and check if it satisfies the required parameters */
            StorageEntry content = msg.getContent();
            System.out.println("Content Received: " + content);

            this.contentFound.add(content);

            if (this.contentFound.size() == this.numNodesToQuery)
            {
                /* We've got all the content required, let's stop the loopup operation */
                this.contentsFound = true;
            }

            /* We've received a newer content than our own, lets specify that newer content exist */
            this.newerContentExist = true;
        }
        else if (incoming instanceof UpToDateContentMessage)
        {
            /**
             * The content we have is up to date
             * No sense in adding our own content to the resultset, so lets just decrement the number of nodes left to query
             */
            numNodesToQuery--;
        }
        else
        {
            /* The reply received is a NodeReplyMessage with nodes closest to the content needed */
            NodeReplyMessage msg = (NodeReplyMessage) incoming;

            /* Add the origin node to our routing table */
            Node origin = msg.getOrigin();
            this.localNode.getRoutingTable().insert(origin);

            /* Set that we've completed ASKing the origin node */
            this.nodes.put(origin, ASKED);

            /* Remove this msg from messagesTransiting since it's completed now */
            this.messagesTransiting.remove(comm);

            /* Add the received nodes to our nodes list to query */
            this.addNodes(msg.getNodes());
            this.askNodesorFinish();
        }
    }

    /**
     * A node does not respond or a packet was lost, we set this node as failed
     *
     * @param comm
     *
     * @throws java.io.IOException
     */
    @Override
    public synchronized void timeout(int comm) throws IOException
    {
        /* Get the node associated with this communication */
        Node n = this.messagesTransiting.get(new Integer(comm));

        if (n == null)
        {
            throw new UnknownMessageException("Unknown comm: " + comm);
        }

        /* Mark this node as failed */
        this.nodes.put(n, FAILED);
        this.localNode.getRoutingTable().remove(n);
        this.messagesTransiting.remove(comm);

        this.askNodesorFinish();
    }

    /**
     * @return The list of all content found during the lookup operation
     */
    public List<StorageEntry> getContentFound()
    {
        return this.contentFound;
    }

    /**
     * Check the content found and get the most up to date version
     *
     * @return The latest version of the content from all found
     *
     * @throws kademlia.exceptions.UpToDateContentException
     */
    public StorageEntry getLatestContentFound() throws UpToDateContentException
    {
        /* We don't have any newer content */
        if (this.contentFound.isEmpty())
        {
            throw new UpToDateContentException("The content is up to date");
        }

        /* We have some newer content, lets get it */
        StorageEntry latest = null;

        for (StorageEntry e : this.contentFound)
        {
            if (latest == null)
            {
                latest = e;
            }

            if (e.getContentMetadata().getLastUpdatedTimestamp() > latest.getContentMetadata().getLastUpdatedTimestamp())
            {
                latest = e;
            }
        }

        return latest;
    }

    /**
     * @return Boolean whether newer content exist or not
     */
    public boolean newerContentExist()
    {
        return this.newerContentExist;
    }
}
