/**
 * Created by mark on 2016-11-01.
 */

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;

/**
 *
 */

/**
 * @author Donald Acton
 *         This example is adapted from Kurose & Ross
 */
public class DNSlookup {
    private static final int MIN_PERMITTED_ARGUMENT_COUNT = 2;
    private static boolean tracingOn = false;
    private static InetAddress rootNameServer;
    private static int queryID;
    private static int MAX_NUM_QUERIES = 30;
    private static InetAddress nameServer;
    
    // sends a query (to root name server as of now) where
    // - address is ip address of name server
    // - fqdn is fully qualified domain name that is to be resolved
    // - port number is 53 by default
    // user of this method must handle both exceptions: SocketException, IOException
    // returns the server's response
    private static void sendQuery(DatagramSocket socket, InetAddress address, String fqdn) throws SocketException, IOException {
        byte[] dnsQuery = convertDomainNameToDNSQuery(fqdn);
        DatagramPacket packet = new DatagramPacket(dnsQuery, dnsQuery.length, address, 53);
        socket.send(packet);
    }
    
    private static DNSResponse receiveResponse(DatagramSocket socket) throws IOException {
        // **** if no response within 5 secs, resend packet *****
        // ******** if still no response, throw exception/print error code
        // if truncated send error
        
        byte[] buf = new byte[512];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        boolean transactionSuccess = false;
        while (!transactionSuccess) {
            // if transaction id from response is different from query, discard response and
            // start receiving again
            socket.receive(packet);
            byte[] data = packet.getData();
            int rQueryID = ((data[0] << 8) + (data[1] & 0xff));
            if (rQueryID == queryID) {
                transactionSuccess = true;
            }
        }
        byte[] data = packet.getData();
        if ((data[3] & 0b0000_0010) == 0b0000_0010) { //response is truncated
            // report error
        }
        return new DNSResponse(data, data.length);
    }
    
    private static byte[] convertDomainNameToDNSQuery(String fqdn) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        // Creates a random queryID between 0 - (2^16 - 1) inclusive
        Random r = new Random();
        byte[] query = new byte[2];
        r.nextBytes(query);
        queryID = ((query[0] << 8) + (query[1] & 0xff));
        System.out.println(queryID);
        
        byte[] prefix = {query[0], query[1], (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00};
        outputStream.write(prefix);
        String[] strings = fqdn.split(Pattern.quote("."));
        convertToByteArray(outputStream, strings);
        byte[] postfix = {(byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x00, (byte) 0x01};
        outputStream.write(postfix);
        return outputStream.toByteArray();
    }
    
    private static void convertToByteArray(ByteArrayOutputStream outputStream, String[] strings) throws IOException {
        for (int i = 0; i < strings.length; i++) {
            byte len = (byte) strings[i].length();
            outputStream.write(len);
            outputStream.write(strings[i].getBytes());
        }
    }
    
    /**
     * @param args
     */
    public static void main(String[] args) throws Exception {
        String fqdn;
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
        try (
             DatagramSocket socket = new DatagramSocket();
             ) {
            int queryCount = 0;
            sendQuery(socket, rootNameServer, fqdn);
            while (queryCount != MAX_NUM_QUERIES) {
                if (queryCount > 0) {
                    //sendQuery(socket, nameServer, fqdn);
                }
                DNSResponse response = receiveResponse(socket);
                List<ResourceRecord> records = response.getRecords();
                for (ResourceRecord record : records) {
                    if (record.getType() == 0x01 && record.getName().equals(fqdn)) {
                        // checks if there is a record with the proper type that corresponds to the FQDN you were looking for
                        if (!tracingOn) {
                            // if user didn't input -t
                            System.out.printf("%s %d %s", fqdn, record.getTTL(), InetAddress.getByAddress(record.getRData()).getHostAddress());
                        }
                        else {
                            // if user input -t
                        }
                        
                    } else if (record.getType() == 0x05 && record.getName().equals(fqdn)) {
                        // checks if there is a CNAME record that corresponds to the FQDN you were looking for,
                        // and if so repeats the process (recursively) with that name
                    } else {
                        // selects a potential nameserver, and if its address is in the additional
                        // information (or is known to you through other means), repeat the query (recursively) to this new nameserver;
                        // if the address of the nameserver is unknown, make a new query for the nameserver's name (recursively) using
                        // the root nameserver, then use the result as a nameserver to repeat the original query (recursively);
                        // if there is no nameserver, return an error.
                    }
                }
                queryCount++;
            }
        } catch (SocketException e) {
            //do something
        } catch (IOException e) {
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


