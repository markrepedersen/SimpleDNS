/**
 * Created by mark on 2016-11-01.
 */

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.*;
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
    private static int numTimeOuts = 0;
    private static String nameBeingLookUp;
    
    
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
  //Check for specific Rcode errors
    private static void checkRcode(byte[] data) throws UnknownHostException  {
		
		if((data[3]& 0b0000_1111) == 0b0000_0001){
			//Format error - 
			//The name server was unable to interpret the query.
			System.out.println(nameBeingLookUp+ " -4 " +"0.0.0.0");
			throw new Error();	
			
		}
		if((data[3]& 0b0000_1111) == 0b0000_0010){
			//Server failure - 
			//The name server was unable to process this query due to a problem with the name server.
			System.out.println(nameBeingLookUp+ " -4 " +"0.0.0.0");
			throw new Error();	
		}
		if((data[3]& 0b0000_1111) == 0b0000_0011){
			//Name Error - 
			//Meaningful only for responses from an authoritative name server, 
			//this code signifies that the domain name referenced in the query does not exist
			System.out.println(nameBeingLookUp+ " -1 " +"0.0.0.0");
			throw new Error();		
		}
		
		if((data[3]& 0b0000_1111) == 0b0000_0100){
			//Not Implemented - 
			//The name server does not support the requested kind of query.
			System.out.println(nameBeingLookUp+ " -4 " +"0.0.0.0");
			throw new Error();	
			
		}
		if((data[3]& 0b0000_1111) == 0b0000_0101){
			//Refused - 
			//The name server refuses to perform the specified operation for policy reasons. 
			System.out.println(nameBeingLookUp+ " -4 " +"0.0.0.0");
			throw new Error();	
		}

    	
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
        if ((data[2] & 0b0000_0010) == 0b0000_0010) { //response is truncated
            // report error
        }
        checkRcode(data);
        return new DNSResponse(data, data.length);
    }
    
    private static byte[] convertDomainNameToDNSQuery(String fqdn) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        // Creates a random queryID between 0 - (2^16 - 1) inclusive
        Random r = new Random();
        byte[] query = new byte[2];
        r.nextBytes(query);
        queryID = ((query[0] << 8) + (query[1] & 0xff));
        
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
        nameBeingLookUp=args[1];
        fqdn = args[1];
        
        if (argCount == 3 && args[2].equals("-t"))
            tracingOn = true;
        
        // Start adding code here to initiate the lookup
        try (
             DatagramSocket socket = new DatagramSocket();
             ) {
            // sets a timer so that a call to receive will only block for 5 seconds,
            // else SocketTimeOutException thrown
            socket.setSoTimeout(5000);
            int queryCount = 0;
            while (queryCount != MAX_NUM_QUERIES) {
                sendQuery(socket, rootNameServer, fqdn);
                if (tracingOn) {
                    System.out.println("\n\nQuery ID     " + queryID + " -> " + rootNameServer.getHostAddress());
                }
                try {
                    DNSResponse response = receiveResponse(socket);
                    if (tracingOn) {
                        response.dumpResponse();
                    }
                    if (response.getAnswerCount() != 0) {
                        for (ResourceRecord record : response.getAnsRecords()) {
                            if (record.getType() == 0x05 && record.getName().equals(fqdn)) {
                                // checks if there is a CNAME record that corresponds to the FQDN you were looking for,
                                // and if so repeats the process (recursively) with that name
                                fqdn = DNSResponse.readFQDN(response.getData(), record.getRData(), 0);
                                rootNameServer = InetAddress.getByName(args[0]);
                            }
                            else {
                                if (!tracingOn) {
                                    // if user didn't input -t
                                    System.out.printf("%s %d %s", fqdn, record.getTTL(), InetAddress.
                                                      getByAddress(record.getRData()).
                                                      getHostAddress());
                                } else {
                                    // if user input -t
                                }
                            }
                        }
                    }
                    else if (response.getNsCount() != 0) {
                        ResourceRecord ns = response.selectNameServer();
                        if (ns != null) {
                            // match found, start new query at new ns
                            rootNameServer = InetAddress.getByAddress(ns.getRData());
                        }
                        else {
                            // no match in additional section, must look up ns in a new query
                            String nsFQDN = DNSResponse.readFQDN(response.getData(), response.getFirstNSRecord().getRData(), 0);
                            sendQuery(socket, InetAddress.getByName(args[0]), nsFQDN);
                            DNSResponse newQuery = receiveResponse(socket);
                        }
                    }
                    queryCount++;
                    //Throw error is queryCount exceeds MAX 
                    if (queryCount>MAX_NUM_QUERIES){
            			System.out.println(nameBeingLookUp+ " -3 " +"0.0.0.0");
            			throw new Error();	
                    }
                }
                catch (SocketTimeoutException e) {
                    // If you send a query and don't get a response in 5 seconds you are to
                    // resend the query to the same name server. If you still don't get a response
                    // you are to indicate that the name could not be looked up by reporting a TTL of -2 and host ID of 0.0.0.0
                    if (numTimeOuts == 2) {
            			System.out.println(nameBeingLookUp+ " -2 " +"0.0.0.0");
            			throw new Error();	
                    }
                }
                // if nameserver ip address is invalid somehow?
                catch (UnknownHostException e) {
                    // throw some error
                }
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

