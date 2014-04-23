package kademlia.tests;

import java.util.Timer;
import java.util.TimerTask;
import kademlia.core.DefaultConfiguration;
import kademlia.Kademlia;
import kademlia.core.KadConfiguration;
import kademlia.dht.GetParameter;
import kademlia.node.NodeId;

/**
 * Testing the cache content system
 *
 * @author Joshua Kissoon
 * @since 20140423
 */
public class CachedContentTest
{

    public CachedContentTest()
    {
        try
        {
            /* Setting up 2 Kad networks */
            final Kademlia kad1 = new Kademlia("JoshuaK", new NodeId("ASF456789djem45674DH"), 12049);
            final Kademlia kad2 = new Kademlia("Crystal", new NodeId("AJDHR678947584567464"), 4585);
            final Kademlia kad3 = new Kademlia("Shameer", new NodeId("AS84k6789KRNS45KFJ8W"), 8104);
            final Kademlia kad4 = new Kademlia("Lokesh.", new NodeId("AS84kUD89YU58456dyrj"), 8335);
            final Kademlia kad5 = new Kademlia("Chandu.", new NodeId("AS84kUD894758456dyrj"), 13345);

            /* Connecting nodes */
            System.out.println("Connecting Nodes");
            kad2.bootstrap(kad1.getNode());
            kad3.bootstrap(kad2.getNode());
            kad4.bootstrap(kad2.getNode());
            kad5.bootstrap(kad4.getNode());

            DHTContentImpl c = new DHTContentImpl(new NodeId("AS84k678947584567465"), kad1.getOwnerId());
            c.setData("Setting the data");

            System.out.println("\n Content ID: " + c.getKey());
            System.out.println(kad1.getNode() + " Distance from content: " + kad1.getNode().getNodeId().getDistance(c.getKey()));
            System.out.println(kad2.getNode() + " Distance from content: " + kad2.getNode().getNodeId().getDistance(c.getKey()));
            System.out.println(kad3.getNode() + " Distance from content: " + kad3.getNode().getNodeId().getDistance(c.getKey()));
            System.out.println(kad4.getNode() + " Distance from content: " + kad4.getNode().getNodeId().getDistance(c.getKey()));
            System.out.println(kad5.getNode() + " Distance from content: " + kad5.getNode().getNodeId().getDistance(c.getKey()));
            System.out.println("\nSTORING CONTENT 1 locally on " + kad1.getOwnerId() + "\n\n\n\n");

            kad1.putLocally(c);
            System.out.println(kad1);
            System.out.println(kad2);
            System.out.println(kad3);
            System.out.println(kad4);
            System.out.println(kad5);

            /* From the IDs above we notice that the content will not be stored permanently on kad1, so lets try caching it and see if it stays permanently */
            kad1.cache(c);
            System.out.println(kad1);


            /* Print the node states every few minutes */
            KadConfiguration config = new DefaultConfiguration();
            Timer timer = new Timer(true);
            timer.schedule(
                    new TimerTask()
                    {
                        @Override
                        public void run()
                        {
                            System.out.println(kad1);
                            System.out.println(kad2);
                            System.out.println(kad3);
                            System.out.println(kad4);
                            System.out.println(kad5);

                            try
                            {
                                // Lets get the content from cached space     
                                GetParameter gp = new GetParameter(c.getKey(), DHTContentImpl.TYPE, c.getOwnerId());
                                System.out.println("Cached Content Found: " + new DHTContentImpl().fromBytes(kad1.getCachedContent(gp).getContent().getBytes()));
                            }
                            catch (Exception e)
                            {
                                e.printStackTrace();
                            }
                        }
                    },
                    // Delay                        // Interval
                    config.restoreInterval(), config.restoreInterval()
            );
        }

        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public static void main(String[] args)
    {
        new CachedContentTest();
    }
}
