/**
 * Created by mark on 2016-11-01.
 */

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Random;

/**
 *
 */

/**
 * @author Donald Acton
 * This example is adapted from Kurose & Ross
 *
 */
public class DNSlookup {
    
    
    static final int MIN_PERMITTED_ARGUMENT_COUNT = 2;
    static boolean tracingOn = false;
    static InetAddress rootNameServer;
    
    // sends a query (to root name server as of now) where
    // - address is ip address of name server
    // - fqdn is fully qualified domain name that is to be resolved
    // - port number is 53 by default
    // user of this method must handle both exceptions: SocketException, IOException
    // returns the server's response
    public static void sendQuery(InetAddress address, String fqdn) throws SocketException, IOException {
        // SENDIN' DA PACKAGE..
        DatagramSocket socket = new DatagramSocket();
        byte[] ip = fqdn.getBytes();
        DatagramPacket packet = new DatagramPacket(ip, ip.length, address, 53);
        socket.send(packet);
        
        // RECEIVIN' DA PACKAGE..
        // **** if no response within 5 secs, resend packet *****
        // ******** if still no response, throw exception/print error code
        // *********** MAKE SURE TO CHECK THAT RECEIVED PACKET IS NOT TRUNCATED *********
        // *********** IF TRUNCATED (IF PAYLOAD WAS TOO LARGE FOR PACKET) THROW SOME ERROR *********
        byte[] buf = new byte[1023];
        packet = new DatagramPacket(buf, buf.length);
        boolean isGoodTransactionID = false;
        while (isGoodTransactionID) {
            // if transaction id from response is different from query, discard response and
            // start receiving again
            socket.receive(packet);
        }
        
        
    }
    
    /**
     * @param args
     */
    public static void main(String[] args) throws Exception {
        String fqdn;
        DNSResponse response; // Just to force compilation
        int argCount = args.length;
        
        if (argCount < 2 || argCount > 3) {
            usage();
            return;
        }
        
        rootNameServer = InetAddress.getByName(args[0]);
        fqdn = args[1];
        
        if (argCount == 3 && args[2].equals("-t"))
            tracingOn = true;
        
        // Start adding code here to initiate the lookup
        try {
            sendQuery(rootNameServer, fqdn);
        }
        catch (SocketException e) {
            //do something
        }
        catch (IOException e) {
            //do something
        }
        
    }
    
    private static void usage() {
        System.out.println("Usage: java -jar DNSlookup.jar rootDNS name [-t]");
        System.out.println("   where");
        System.out.println("       rootDNS - the IP address (in dotted form) of the root");
        System.out.println("                 DNS server you are to start your search at");
        System.out.println("       name    - fully qualified domain name to lookup");
        System.out.println("       -t      -trace the queries made and responses received");
    }
}


