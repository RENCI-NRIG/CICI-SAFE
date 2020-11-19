package exoplex.sdx.network;

import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.util.*;

public class RoutingManagerMock extends AbstractRoutingManager {
  private NetworkManager networkManager;
  private HashMap<String, ArrayList<Long>> router_queues = new HashMap<>();
  private HashMap<String, String> macInterfaceMap = new HashMap<>();
  private HashMap<String, String> macPortMap = new HashMap<>();
  private HashMap<String, String> linkGateway = new HashMap<>();

  //pathIds of paths configured for the ip prefix

  public RoutingManagerMock() {
    networkManager = new NetworkManager();
  }

  synchronized public void newRouter(String routerName, String dpid,
                                     String controller, String managementIP) {
    if (networkManager.getRouter(routerName) == null) {
      //      logger.debug(dpid+":my dpid");
      networkManager.putRouter(new Router(routerName, dpid, controller,
        managementIP));
      ArrayList<Long> newqueue = new ArrayList<>();
      newqueue.add(Long.valueOf(1000000));
      router_queues.put(dpid, newqueue);
    }
  }

  /**
   * @param linkName
   * @param ipa
   * @param routerA
   * @param gw
   * @return
   */
  public boolean newExternalLink(String linkName, String ipa, String routerA,
                                 String gw) {
    networkManager.addLink(linkName, ipa, routerA, gw);
    String dpid = networkManager.getRouter(routerA).getDPID();
    String controller = networkManager.getRouter(routerA).getController();
    return true;
  }

  public void removeExternalLink(String linkName, String routerName){
    String gw = linkGateway.remove(linkName);
    networkManager.delLink(linkName, routerName, gw);
  }

  /**
   * @param linkName
   * @param ipa         IP prefix :192.168.10.1/24
   * @param routerA     nodeName of router: c0
   * @return
   */
  public boolean newInternalLink(String linkName, String ipa, String routerA, String ipb,
                                 String routerB, String cap) {
    long capacity = Long.valueOf(cap);
    return newInternalLink(linkName, ipa, routerA, ipb, routerB, capacity);
  }

  public boolean newInternalLink(String linkName, String ipa, String routerA, String ipb,
                                 String routerB, long capacity) {
    networkManager.addLink(linkName, ipa, routerA, ipb, routerB, capacity);
    String dpid = getDPID(routerA);
    String controller = networkManager.getRouter(routerA).getController();
    return true;
  }

  /**
   * Forward matched packets to controller
   */
  public void monitorOnAllRouter(String dstIP, String srcIP, int tableId) {}

  /**
   * configure path for destIP in the network.
   *
   * @param destIP     destination IP prefix: 192.168.10.1/24
   * @param nodename   edgerouter for destIP (redundant param)
   * @param gateway    gateway for destIp
   */
  public void configurePath(String destIP, String nodename, String gateway) {
  }

  public void configurePath(String destIP, String srcIP, String nodename,
                            String gateway) {
  }

  //gateway is the gateway for nodename
  public boolean configurePath(String dstIP, String dstNode, String srcIP, String srcNode,
                               String gateway, String bandWitdth) {
    return true;
  }

  /**
   * Configure routing in one direction:
   *
   * @param dstIP      192.168.10.1/24
   * @param dstNode    e0
   * @param srcIP      192.168.20.1/24
   * @param srcNode    e2
   * @param gateway    192.168.10.1
   * @param bw
   * @return
   */
  public boolean configurePath(String dstIP, String dstNode, String srcIP,
                                            String srcNode, String gateway,
                                            long bw) {
    return true;
  }

  public String getGateway(String dstIP) {
    return null;
  }

  public String getGateway(String dstIP, String srcIP) {
    return null;
  }

  public void removePath(String dstIP) {
  }

  public void removePath(String dstIP, String srcIP) {
  }

  public void retriveRouteOfPrefix(String prefix) {
  }

  public String setMirror(String dpid, String source, String dst, String gw) {
    return null;
  }

  public String delMirror(String dpid, String source, String dst) {
    return null;
  }

  public String singleStepRouting(String dest, String gateway, String dpid) {
    return null;
  }

  public String singleStepRouting(String dest, String src, String gateway, String dpid) {
    return null;
  }

  public void checkFLowTableForPair(String srcIp, String destIp, String p1, String p2,
                                    String sshKey, Logger logger) {
  }

  public boolean findPath(String node1, String node2, String bandWidth) {
    return true;
  }

  public boolean findPath(String node1, String node2, long bw) {
    return true;
  }

  //==========SEPERATOR===================//

  public void setOvsdbAddr() {
  }

  synchronized public void setInterfaceMac(String node, String link,
                                           String mac, String eth) {
    if (mac != null) {
      String oldValue = macInterfaceMap.put(mac, NetworkUtil.computeInterfaceName(node, link));
      if (!mac.equals(oldValue)) {
        if (macPortMap.containsKey(mac)) {
          networkManager.updateInterface(NetworkUtil.computeInterfaceName(node, link),
            macPortMap.get(mac), mac);
        }
      }
    }
  }

  synchronized public void queryPortData(String dpid) {
  }

  public void queryAllPortData() {
  }

  public boolean waitTillAllOvsConnected(String controller, boolean mocked) {
    return true;
  }

  public int queryPortCount(String nodeName) {
    return 0;
  }

  public boolean setNextHops(String nodeName, String controller, int groupId, String destIP,
                             HashMap<String, Integer> nbs) {
    return true;
  }

  public boolean setOutPort(String nodeName, String controller, String linkName, String destIP) {
    return true;
  }

  public void setQos(String dpid, String srcip, String destip, String bw) {
  }

  public void setQos(String dpid, String srcip, String destip, long bw) {
  }

  public void replayCmds(String dpid) {
  }

  public String getDPID(String routerName) {
    return networkManager.getRouter(routerName).getDPID();
  }

  public String getEdgeRouterByGateway(String gw) {
    return networkManager.getRouterByGateway(gw);
  }

  public List<String> getNeighbors(String routerName) {
    return networkManager.getNeighborNodes(routerName);
  }

  public String getFlowOnRouter(String ip, String srcIp, String destIp, String sshKey) {
    return null;
  }

  public List<String> getFlowsOnRouter(Map<String, String> fieldMap, String ip, String sshKey) {
    return new ArrayList<>();
  }

  public void deleteAllFlows() {
  }

  public void printLinks() {
  }

  public void allowIngress(String dstIP, String srcIP, String routerName, String linkName,
                           int priority, String controller) {}
}
