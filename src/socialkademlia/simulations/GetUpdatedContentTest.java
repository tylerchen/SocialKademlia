package socialkademlia.simulations;

import java.io.IOException;
import socialkademlia.JSocialKademliaNode;
import socialkademlia.dht.GetParameterFUC;
import socialkademlia.exceptions.UpToDateContentException;
import kademlia.node.KademliaId;
import kademlia.simulations.DHTContentImpl;
import socialkademlia.dht.JSocialKademliaStorageEntry;
import socialkademlia.dht.SocialKademliaStorageEntry;

/**
 * We put the content on the network, and then check for an up to date version.
 *
 * @author Joshua Kissoon
 * @since 20140422
 */
public class GetUpdatedContentTest
{

    public static void main(String[] args)
    {

        try
        {
            /* Setting up 2 Kad networks */
            JSocialKademliaNode kad1 = new JSocialKademliaNode("JoshuaK", new KademliaId("ASF45678947584567467"), 7574);
            JSocialKademliaNode kad2 = new JSocialKademliaNode("Crystal", new KademliaId("ASERTKJDHGVHERJHGFLK"), 7572);

            /* Create 2 content */
            DHTContentImpl c1 = new DHTContentImpl(kad2.getOwnerId(), "Joshua Book 1st Edition.");
            DHTContentImpl c2 = new DHTContentImpl(kad2.getOwnerId(), "Joshua Bio 1st Edition.");
            /* Connect the 2 nodes */
            kad2.bootstrap(kad1.getNode());

            /* Lets share the contents */
            kad1.put(c1);
            kad1.put(c2);

            for (long i = 0; i < 9999990000L; i++)
            {

            }

            /* Update c2 and store it on kad1 only */
            System.out.println("\n\n ************** Updating C2");
            long c2OldTs = c2.getLastUpdatedTimestamp();
            c2.setData("Joshua Bio 2nd Edition");
            kad1.putLocally(c2);

            /* Get updated version of c1 */
            try
            {
                /* Lets see if there is an updated version of the content */
                System.out.println("\n\nRetrieving Content C1");
                GetParameterFUC gp = new GetParameterFUC(c1.getKey(), DHTContentImpl.TYPE, c1.getOwnerId(), c1.getLastUpdatedTimestamp());
                JSocialKademliaStorageEntry conte = kad2.getUpdated(gp);
                System.out.println("Updated Content Found: " + new DHTContentImpl().fromSerializedForm(conte.getContent()));
                System.out.println("Updated Content Metadata: " + conte.getContentMetadata());
            }
            catch (IOException | UpToDateContentException ex)
            {
                System.err.println(ex.getMessage());
            }

            /* Get updated version of C2 */
            try
            {
                /* Lets retrieve the content c2 if it's updated */
                System.out.println("\n\n\n****************Retrieving Content c2 Again");

                /* We use c2 old TS, since that's the TS of the version kad2 has */
                GetParameterFUC gp = new GetParameterFUC(c2.getKey(), DHTContentImpl.TYPE, c2.getOwnerId(), c2OldTs);
                SocialKademliaStorageEntry conte = kad2.getUpdated(gp);
                System.out.println("Updated Content Found: " + new DHTContentImpl().fromSerializedForm(conte.getContent()));
                System.out.println("Updated Content Metadata: " + conte.getContentMetadata());

            }
            catch (IOException | UpToDateContentException ex)
            {
                System.err.println(ex.getMessage());
            }

        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

    }
}
