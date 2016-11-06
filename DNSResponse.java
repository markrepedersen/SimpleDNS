/**
 * Created by mark on 2016-10-28.
 */

import java.io.ByteArrayInputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;


// Lots of the action associated with handling a DNS query is processing
// the response. Although not required you might find the following skeleton of
// a DNSresponse helpful. The class below has a bunch of instance data that typically needs to be
// parsed from the response. If you decide to use this class keep in mind that it is just a
// suggestion and feel free to add or delete methods to better suit your implementation as
// well as instance variables.


public class DNSResponse {
    private List<ResourceRecord> resourceRecords = new ArrayList<>();
    private List<ResourceRecord> ansRecords = new ArrayList<>();    // a and aaaa records
    private List<ResourceRecord> nsRecords = new ArrayList<>();
    private List<ResourceRecord> additionalRecords = new ArrayList<>();
    private static int position = 0;
    private int queryID;                  // this is for the response it must match the one in the request
    private String qName;
    private int QDCount;
    private int answerCount = 0;          // number of answers
    private boolean decoded = false;      // Was this response successfully decoded
    private int nsCount = 0;              // number of nscount response records
    private int additionalCount = 0;      // number of additional (alternate) response records
    private boolean authoritative = false;// Is this an authoritative record
    private int QType;
    private int QClass;
    private byte[] data;
    public String answerFQDN="";
    
    // Note you will almost certainly need some additional instance variables.
    
    public int getQueryID() {
        return queryID;
    }
    
    // When in trace mode you probably want to dump out all the relevant information in a response
    void dumpResponse() {
        System.out.println("Response ID: " + queryID + " Authoritative " + authoritative);
        System.out.println("  Answers " + answerCount);
        for (ResourceRecord record : ansRecords) {
            printHelper(record);
            //answerFQDN= record.getName();
        }
        System.out.println("  Nameservers " + nsCount);
        for (ResourceRecord record : nsRecords) {
            printHelper(record);
        }
        System.out.println("  Additional Information " + additionalCount);
        for (ResourceRecord record : additionalRecords) {
            printHelper(record);
        }
    }
    
    String convertToIP(byte[] data) throws UnknownHostException {
        return InetAddress.getByAddress(data).getHostAddress();
    }
    
    public void printHelper(ResourceRecord record) {
        String type = null;
        String rdata = null;
        int ttl = record.getTTL();
        try {
            switch (record.getType()) {
                case 0x01:
                    type = "A";
                    rdata = convertToIP(record.getRData());
                    break;
                case 0x02:
                    type = "NS";
                    rdata = readFQDN(data, record.getRData(), 0);
                    break;
                case 0x05:
                    type = "CN";
                    rdata = readFQDN(data, record.getRData(), 0);
                    break;
                case 28:
                    type = "AAAA";
                    rdata = convertToIP(record.getRData());
                    break;
                default:
                    type = String.valueOf(record.getType());
                    rdata = "";
            }
        }
        catch (UnknownHostException e) {
            ttl = -4;
        }
        System.out.format("       %-30s %-10d %-4s %s\n", record.getName(), ttl, type, rdata);
    }
    
    
    
    public List<ResourceRecord> getRecords() {
        return resourceRecords;
    }
    
    public boolean isAuthoritative() {
        return authoritative;
    }
    
    public int getNsCount() {
        return nsCount;
    }
    
    public int getAdditionalCount() {
        return additionalCount;
    }
    
    public int getAnswerCount() {
        return answerCount;
    }
    
    public ResourceRecord getFirstNSRecord() {
        return nsRecords.get(0);
    }
    
    public List<ResourceRecord> getAnsRecords() {
        return ansRecords;
    }
    
    public List<ResourceRecord> getAdditionalRecords() {
        return additionalRecords;
    }
    
    public List<ResourceRecord> getNsRecords() {
        return nsRecords;
    }
    
    public byte[] getData() {
        return data;
    }
    
    // if a name server is found and its address is in additional section, return this record
    // return any ns if no mapping found
    // O(n^2) performance can hopefully be ignored, since only small amount of records
    public ResourceRecord selectNameServer() {
        if (additionalCount != 0) {
            for (ResourceRecord nsRecord : nsRecords) {
                for (ResourceRecord adRecords : additionalRecords) {
                    if (DNSResponse.readFQDN(data, nsRecord.getRData(), 0).equals(adRecords.getName())) {
                        return adRecords;
                    }
                }
            }
        }
        return null;
    }
    
    //Helper method for reading the sequence of bytes interpreted as the FQDN
    public static String readFQDN(byte[] data, int start) {
        byte[] buf = new byte[255]; // buffer representing a fqdn with length max size of a domain name
        int endOfBuffer = 0; // the first empty position in buffer
        int amountToRead;   // the integer before a label indicating how long the label is
        int pos = start;
        boolean isOnPointer = false;
        int lastPos = 0;     // last position in array (this is for when our position is changed with pointers)
        for (int i = pos; i < data.length; i++) {   // 255 max size (in bytes) of a domain name
            if (data[pos] == (byte) 0x00) { // indicates end of name
                if (!isOnPointer) { // if not on pointer when byte is zero, return domain name
                    break;
                }
                isOnPointer = false;
                pos = lastPos;      // else return to last position
            } else if ((data[pos] & 0b1100_0000) == (byte) 0x00) { // regular label
                amountToRead = (int) data[pos];
                for (int j = 0; j < amountToRead; j++) {
                    pos++;
                    buf[endOfBuffer] = data[pos];
                    endOfBuffer++;
                }
                buf[endOfBuffer] = (byte) 0x2e;
                endOfBuffer++;
                pos++;
            } else { // pointer
                if (!isOnPointer) {
                    lastPos = pos + 2;
                }
                isOnPointer = true;
                pos = (((data[pos] & 0b0011_1111) & 0xff) << 8) | (data[pos + 1] & 0xff);
            }
        }
        position = pos;
        int count = 0;
        for (int i = 0; i < buf.length; i++) {
            if (buf[i] == 0) {
                break;
            }
            count++;
        }
        byte[] b = new byte[count - 1];
        for (int i = 0; i < b.length; i++) {
            b[i] = buf[i];
        }
        position++;
        return new String(b);
    }
    
    //Helper method for reading the sequence of bytes interpreted as the FQDN
    public static String readFQDN(byte[] data, byte[] rdata, int start) {
        byte[] buf = new byte[255]; // buffer representing a fqdn with length max size of a domain name
        int endOfBuffer = 0; // the first empty position in buffer
        int amountToRead;   // the integer before a label indicating how long the label is
        int pos = start;
        byte[] lastRData = null;
        boolean isOnPointer = false;
        int lastPos = 0;     // last position in array (this is for when our position is changed with pointers)
        for (int i = pos; i < rdata.length; i++) {   // 255 max size (in bytes) of a domain name
            if (rdata[pos] == (byte) 0x00) { // indicates end of name
                if (!isOnPointer) { // if not on pointer when byte is zero, return domain name
                    break;
                }
                isOnPointer = false;
                pos = lastPos;      // else return to last position
                rdata = lastRData;
                if (pos >= rdata.length) {
                    break;
                }
            }
            
            else if ((rdata[pos] & 0b1100_0000) == (byte) 0x00) { // regular label
                amountToRead = (int) rdata[pos];
                for (int j = 0; j < amountToRead; j++) {
                    pos++;
                    buf[endOfBuffer] = rdata[pos];
                    endOfBuffer++;
                }
                buf[endOfBuffer] = (byte) 0x2e;
                endOfBuffer++;
                pos++;
                
            } else { // pointer
                if (!isOnPointer) {
                    lastRData = rdata;
                    lastPos = pos + 2;
                }
                pos = (((rdata[pos] & 0b0011_1111) & 0xff) << 8) | (rdata[pos + 1] & 0xff);
                rdata = data;
                isOnPointer = true;
            }
        }
        position = pos;
        int count = 0;
        for (int i = 0; i < buf.length; i++) {
            if (buf[i] == 0) {
                break;
            }
            count++;
        }
        byte[] b = new byte[count - 1];
        for (int i = 0; i < b.length; i++) {
            b[i] = buf[i];
        }
        position++;
        return new String(b);
    }
    
    // The constructor: you may want to add additional parameters, but the two shown are
    // probably the minimum that you need.
    
    public DNSResponse(byte[] data, int len) {
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        // The following are probably some of the things
        // you will need to do.
        // Extract the query ID
        
        // Make sure the message is a query response and determine
        // if it is an authoritative response or note
        
        // determine answer count
        
        // determine NS Count
        
        // determine additional record count
        
        // Extract list of answers, name server, and additional information response
        // records
        this.data = data;
        byte b = data[4];
        if ((b & (byte) 0x01) != (byte) 0x00) {   //response code indicates error
            return; // error - return -1 or smth
        }
        queryID = ((data[0] << 8) + (data[1] & 0xff));
        authoritative = (data[2] & 0b0000_0100) == 0b0000_0100;
        QDCount = ((data[4] << 8) + (data[5] & 0xff));
        answerCount = ((data[6] << 8) + (data[7] & 0xff));
        nsCount = ((data[8] << 8) + (data[9] & 0xff));
        additionalCount = ((data[10] << 8) + (data[11] & 0xff));
        qName = readFQDN(data, 12);
        QType = ((data[position] << 8) + (data[position + 1] & 0xff));
        position += 2;
        QClass = ((data[position] << 8) + (data[position + 1] & 0xff));
        position += 2;
        for (int i = 0; i < answerCount; i++) {
            addRecord(data, position, ansRecords);
        }
        for (int i = 0; i < nsCount; i++) {
            addRecord(data, position, nsRecords);
        }
        for (int i = 0; i < additionalCount; i++) {
            addRecord(data, position, additionalRecords);
        }
        position = 0;
    }
    
    private void addRecord(byte[] data, int position1, List<ResourceRecord> list) {
        String name = readFQDN(data, position1); //this method already increments position for us
        position1 = position - 1;
        int type = ((data[position1] << 8) + (data[position1 + 1] & 0xff));
        position1 += 2;
        int clss = ((data[position1] << 8) + (data[position1 + 1] & 0xff));
        position1 += 2;
        byte[] newInt = {data[position1], data[position1 + 1], data[position1 + 2], data[position1 + 3]};
        int ttl = ByteBuffer.wrap(newInt).getInt();
        position1 += 4;
        int RDLength = ((data[position1] << 8) + (data[position1 + 1] & 0xff));
        position1 += 2;
        byte[] RData = new byte[RDLength];
        for (int j = 0; j < RDLength; j++) {
            RData[j] = data[position1];
            position1++;
        }
        list.add(new ResourceRecord(name, type, clss, ttl, RDLength, RData));
        resourceRecords.add(new ResourceRecord(name, type, clss, ttl, RDLength, RData));
        position = position1;
    }
    
    
    // You will probably want a methods to extract a compressed FQDN, IP address
    // cname, authoritative DNS servers and other values like the query ID etc.
    
    
    // You will also want methods to extract the response records and record
    // the important values they are returning. Note that an IPV6 reponse record
    // is of type 28. It probably wouldn't hurt to have a response record class to hold
    // these records.
}
