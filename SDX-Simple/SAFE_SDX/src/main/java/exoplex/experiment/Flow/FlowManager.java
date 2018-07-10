package exoplex.experiment.Flow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class FlowManager {
  static int DEFAULT_PORT = 5001;
  static int DEFAULT_TIME = 60;
  HashMap<String, IperfServer> iperfServers= new HashMap<>();
  ArrayList<IperfFlow> flows = new ArrayList<>();
  protected final ArrayList<String[]> iperfClientOut = new ArrayList<>();
  protected final HashMap<String, List<String[]>> iperfServerOut = new HashMap<>();
  String sshKey;

  public FlowManager(String sshKey){
    this.sshKey = sshKey;
  }

  public boolean addUdpFlow(String c1, String server, String serverDpIP, String bw){
    IperfServer iperfServer = new IperfServer(server, DEFAULT_PORT, IperfServer.UDP, this.sshKey);
    if(iperfServers.containsKey(iperfServer.toString())){
      if(!iperfServers.get(iperfServer.toString()).transportProto.equals(IperfServer.UDP)){
        return false;
      }
    }
    iperfServers.put(iperfServer.toString(), iperfServer);
    IperfFlow flow = new IperfFlow(c1, serverDpIP, sshKey, DEFAULT_PORT,
        DEFAULT_TIME, bw, IperfServer.UDP, iperfServer.toString());
    flows.add(flow);
    return true;
  }

  public void clearResults(){
    for(IperfServer server: iperfServers.values()){
      server.clearResults();
    }
    for(IperfFlow flow: flows){
      flow.clearResults();
    }
  }

  public void startFlows(){
    for(IperfServer server: iperfServers.values()){
      server.start();
    }
    for(IperfFlow flow: flows){
      flow.start();
    }
  }

  public void stopFlows(){
    for(IperfServer server: iperfServers.values()){
      server.stop();
    }
    for(IperfFlow flow: flows){
      flow.stop();
    }
  }

  public List<String[]> getClientResults(){
    for(IperfFlow flow:flows){
      iperfClientOut.addAll(flow.getResults());
    }
    return iperfClientOut;
  }

  public List<String[]> getServerResults(){
    ArrayList<String[]> results = new ArrayList<>();
    for(IperfServer server:iperfServers.values()){
      iperfServerOut.put(server.toString(), server.getResults());
      results.addAll(server.getResults());
    }
    return results;
  }
}
