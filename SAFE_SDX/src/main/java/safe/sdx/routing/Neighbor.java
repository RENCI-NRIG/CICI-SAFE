package safe.sdx;

import java.util.ArrayList;

public class Neighbor{
  public String id;
  public String gateway;
  public String edgerouter;
  public String edgeip;
  public String sliceName;

  public Neighbor(String pid, String pgateway, String pedgerouter, String pedgeip, String pslice){
    id=pid;
    gateway=pgateway;
    edgerouter=pedgerouter;
    edgeip=pedgeip;
    sliceName=pslice;
  }
  public String toString(){
    return "gw:"+gateway+" edgerouter:"+edgerouter+" edgeip:"+edgeip+" keyhash:"+id+" nbname:"+sliceName+"\n";

  }
}
