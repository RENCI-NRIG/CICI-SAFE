package safe.sdx.sdx;

import java.util.HashSet;

public class Router{
    private String routerid="";
    private String dpid="";
    private HashSet<String> interfaces=new HashSet<String>();
    private HashSet<String> neighbors=new HashSet<String>();
    private int numInterfaces=0;

    public HashSet<String> getNeighbors(){
      return neighbors;
    }

    public Router(String rid, String switch_id,  int numintf){
      routerid=rid;
      dpid=switch_id;
      numInterfaces=numintf;
    }

    public void addInterface(String interfaceIP){
      interfaces.add(interfaceIP);
    }

    public void addNeighbor(String neighborIP){
      if(neighbors.contains(neighborIP)){
        neighbors.add(neighborIP);
      }
    }

    public void updateInterfaceNum(int newnum){
      numInterfaces=newnum;
    }

    public int getInterfaceNum(){
      return numInterfaces;
    }
    public String getDPID(){
      return dpid;
    }

    public String getRouterID(){
      return routerid;
    }
}
