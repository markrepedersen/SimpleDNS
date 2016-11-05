/**
 * Created by mark on 2016-11-03.
 */

//Defines a resource record with type A, NS, CNAME, or AAAA
public class ResourceRecord {
    private String name;
    private int type;
    private int clss;
    private int ttl;
    private int RDLength;
    private byte[] rData;
    
    public int getType() {
        return type;
    }
    
    public byte[] getRData() {
        return rData;
    }
    
    public String getName() {
        return name;
    }
    
    public int getTTL() {
        return ttl;
    }
    
    public ResourceRecord(String name, int type, int clss, int ttl, int RDLength, byte[] rData) {
        this.name = name;
        this.type = type;
        this.clss = clss;
        this.ttl = ttl;
        this.RDLength = RDLength;
        this.rData = rData;
    }
    
    
}
