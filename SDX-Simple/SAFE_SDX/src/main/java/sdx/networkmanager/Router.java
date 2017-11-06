package sdx.networkmanager;

import java.util.HashSet;

import org.apache.log4j.Logger;

import sdx.utils.Exec;

public class Router{
  final static Logger logger = Logger.getLogger(Exec.class);	

    private String routerid="";
    private String dpid="";
    private String ip="";
    private HashSet<String> interfaces=new HashSet<String>();
    private HashSet<String> neighbors=new HashSet<String>();
    private HashSet<String> customergateways=new HashSet<>();
    private int numInterfaces=0;

    public HashSet<String> getNeighbors(){
      return neighbors;
    }

    public Router(String rid, String switch_id, int numintf, String ip){
      routerid=rid;
      dpid=switch_id;
      this.ip=ip;
      numInterfaces=numintf;
    }

    public void addInterface(String interfaceIP){
      interfaces.add(interfaceIP);
    }
    public void addGateway(String gw){
      customergateways.add(gw);
    }

    public boolean hasGateway(String gw){
      return customergateways.contains(gw);
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
    public String getManagementIP(){
      return this.ip;
    }
}
