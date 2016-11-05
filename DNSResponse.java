/**
 * Created by mark on 2016-10-28.
 */

import java.io.ByteArrayInputStream;
import java.net.InetAddress;
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
    private int position = 0;
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
    long startTime;
   
    
   //Sets system time to check against
   public void setTime(){
	   startTime = System.currentTimeMillis();
    }
  
   
  //Checks against last set to see if 500ms has passed 
  public boolean isExpiredTime(){
	
	 if( System.currentTimeMillis()-startTime<500){
		 return true;
	 }
	 else
	  return false;
	   
   }
    
  
  void dumpResponse() {
  }
  
  public List<ResourceRecord> getRecords() {
      return resourceRecords;
  }
  
  //Helper method for reading the sequence of bytes interpreted as the FQDN
  public String readFQDN(byte[] data, int start) {
      byte[] buf = new byte[255]; // buffer representing a fqdn with length max size of a domain name
      int endOfBuffer = 0; // the first empty position in buffer
      int amountToRead;   // the integer before a label indicating how long the label is
      int pos = start;
      boolean isOnPointer = false;
      int lastPos = 0;     // last position in array (this is for when our position is changed with pointers)
      for (int i = pos; i < 255; i++) {   // 255 max size (in bytes) of a domain name
          if (data[pos] == (byte) 0x00) { // indicates end of name
              if (!isOnPointer) { // if not on pointer when byte is zero, return domain name
                  break;
              }
              isOnPointer = false;
              pos = lastPos;      // else return to last position
          }
          else if ((data[pos] & 0b1100_0000) == (byte) 0x00) { // regular label
              amountToRead = (int) data[pos];
              for (int j = 0; j < amountToRead; j++) {
                  pos++;
                  buf[endOfBuffer] = data[pos];
                  endOfBuffer++;
              }
              buf[endOfBuffer] = (byte) 0x2e;
              endOfBuffer++;
              pos++;
          }
          else { // pointer
              isOnPointer = true;
              lastPos = pos + 2;
              pos = (((data[pos] & 0b0011_1111) & 0xff) << 8) | (data[pos + 1] & 0xff);
              
          }
      }
      position = pos;
      
      // goddamn this code is atrocious
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
  
  public DNSResponse (byte[] data, int len) {
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
      
      byte b = data[4];
      if ((b & (byte) 0x01) != (byte) 0x00) {   //response code indicates error
          return; // error - return -1 or smth
      }
      authoritative = (data[2] & 0b0000_0100) == 0b0000_0100;
      System.out.println(authoritative);
      QDCount = ((data[4] << 8) + (data[5] & 0xff));
      System.out.println(QDCount);
      answerCount = ((data[6] << 8) + (data[7] & 0xff));
      System.out.println(answerCount);
      nsCount = ((data[8] << 8) + (data[9] & 0xff));
      System.out.println(nsCount);
      additionalCount = ((data[10] << 8) + (data[11] & 0xff));
      System.out.println(additionalCount);
      qName = readFQDN(data, 12);
      QType = ((data[position] << 8) + (data[position + 1] & 0xff));
      position += 2;
      QClass = ((data[position] << 8) + (data[position + 1] & 0xff));
      position += 2;
      for (int i = 0; i < answerCount; i++) {
          addRecord(data, position);
      }
      for (int i = 0; i < nsCount; i++) {
          addRecord(data, position);
      }
      for (int i = 0; i < additionalCount; i++) {
          addRecord(data, position);
      }
  }
  
  public void addRecord(byte[] data, int position) {
      String name = readFQDN(data, position); //this method already increments position for us
      position = this.position;
      int type = ((data[position] << 8) + (data[position + 1] & 0xff));
      position += 2;
      int clss = ((data[position] << 8) + (data[position + 1] & 0xff));
      position += 2;
      byte[] newInt = {data[position], data[position + 1], data[position + 2], data[position + 3]};
      int ttl = ByteBuffer.wrap(newInt).getInt();
      position += 4;
      int RDLength = ((data[position] << 8) + (data[position + 1] & 0xff));
      position += 2;
      byte[] RData = new byte[RDLength];
      for (int j = 0; j < RDLength; j++) {
          RData[j] = data[position];
          position++;
      }
      resourceRecords.add(new ResourceRecord(name, type, clss, ttl, RDLength, RData));
  }
  
  
  // You will probably want a methods to extract a compressed FQDN, IP address
  // cname, authoritative DNS servers and other values like the query ID etc.
  
  
  // You will also want methods to extract the response records and record
  // the important values they are returning. Note that an IPV6 reponse record
  // is of type 28. It probably wouldn't hurt to have a response record class to hold
  // these records.
}
