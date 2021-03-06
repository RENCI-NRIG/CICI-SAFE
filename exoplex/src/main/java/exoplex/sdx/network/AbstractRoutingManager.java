package exoplex.sdx.network;

import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class AbstractRoutingManager {

  public abstract void newRouter(String routerName, String dpid,
                                     String controller, String managementIP);

  /**
   * @param linkName
   * @param ipa
   * @param routerA
   * @param gw
   * @return
   */
  public abstract boolean newExternalLink(String linkName, String ipa, String routerA,
                                 String gw, boolean ingress);

  /**
   * Set default ingress filter to drop all IP packets from the ingress port,
   * and allow arp packets in the same subnet
   * @param linkName
   * @param routerName
   * @return
   */
  public abstract boolean installDefaultIngressFilter(
    String linkName,
    String ipa,
    String routerName,
    String gw);

  public abstract void removeExternalLink(String linkName, String routerName);

  /**
   * @param linkName
   * @param ipa         IP prefix :192.168.10.1/24
   * @param routerA     nodeName of router: c0
   * @return
   */
  public abstract boolean newInternalLink(String linkName, String ipa, String routerA, String ipb,
                                 String routerB, String cap);

  public abstract boolean newInternalLink(String linkName, String ipa, String routerA, String ipb,
                                 String routerB, long capacity);

  /**
   * Forward matched packets to controller
   */
  public abstract void monitorOnAllRouter(String dstIP, String srcIP,
                                          int tableId);

  /**
   *
   * @param dstIP
   * @param srcIP
   * @param routerName
   * @param gateway Gateway address in peer node or customer node. Routing
   *                manager figures out the ingress interface by the gateway
   *                address
   * @param dlType ip, arp or all
   * @param drop
   */
  public abstract boolean installIngressFilter(
    String dstIP,
    String srcIP,
    String routerName,
    String gateway,
    String dlType,
    boolean drop);

  /**
   * configure path for destIP in the network.
   *
   * @param destIP     destination IP prefix: 192.168.10.1/24
   * @param nodename   edgerouter for destIP (redundant param)
   * @param gateway    gateway for destIp
   */
  public abstract void configurePath(String destIP, String nodename, String gateway);

  public abstract void configurePath(String destIP, String srcIP, String nodename,
                            String gateway);

  public abstract String getGateway(String destIP);

  public abstract String getGateway(String destIP, String srcIP);

  //gateway is the gateway for nodename
  public abstract boolean configurePath(String dstIP, String dstNode, String srcIP, String srcNode,
                               String gateway, String bandWitdth);

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
  public abstract boolean configurePath(String dstIP, String dstNode, String srcIP,
                                            String srcNode, String gateway,
                                            long bw);


  public abstract void removePath(String dstIP);

  public abstract void removePath(String dstIP, String srcIP);

  public abstract void retriveRouteOfPrefix(String prefix);

  public abstract String setMirror(String dpid, String source, String dst, String gw);

  public abstract String delMirror(String dpid, String source, String dst);

  public abstract String singleStepRouting(String dest, String gateway, String dpid);

  public abstract String singleStepRouting(String dest, String src,
                                           String gateway, String dpid);

  public abstract void checkFLowTableForPair(String srcIp, String destIp, String p1, String p2,
                                    String sshKey, Logger logger);

  public abstract boolean findPath(String node1, String node2, String bandWidth);

  public abstract boolean findPath(String node1, String node2, long bw);

  public abstract void setInterfaceMac(String node, String link, String mac);

  /**
   * Query and save port desc data from SDN controller, including the mac addresses
   * @param routerName
   */
  public abstract void queryPortData(String routerName);

  public abstract void queryAllPortData();

  public abstract boolean waitTillAllOvsConnected(String controller,
                                                  boolean mocked);

  public abstract int queryPortCount(String nodeName);

  public abstract boolean setNextHops(String nodeName,
                                      String controller,
                                      int groupId,
                                      String destIP,
                             HashMap<String, Integer> nbs);

  public abstract boolean setOutPort(String nodeName,
                                     String controller,
                                     String linkName,
                                     String destIP);

  public abstract void setQos(String dpid, String srcip, String destip, String bw);

  public abstract void setQos(String dpid, String srcip, String destip, long bw);

  public abstract void replayCmds(String dpid);

  public abstract String getDPID(String routerName);

  public abstract String getEdgeRouterByGateway(String gw);

  public abstract List<String> getNeighbors(String routerName);

  public abstract String getFlowOnRouter(String ip, String srcIp, String destIp, String sshKey);

  public abstract List<String> getFlowsOnRouter(Map<String, String> fieldMap, String ip, String sshKey);

  public abstract void deleteAllFlows();

  public abstract void printLinks();

  public abstract void setOvsdbAddr();

  public abstract void setMacEth(String mac, String ethName);
}
