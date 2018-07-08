package exoplex.sdx.network;

import exoplex.common.utils.Exec;
import exoplex.common.utils.HttpUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

public class RoutingManager {
  final static Logger logger = LogManager.getLogger(RoutingManager.class);
  private NetworkManager networkManager;

  final static int MAX_RATE = 2000000;
  private HashMap<String, ArrayList<Long>> router_queues = new HashMap<>();
  private HashMap<String, ArrayList<JSONObject>> router_matches = new HashMap<>();
  private HashMap<String, ArrayList<String[]>> sdncmds = new HashMap<String, ArrayList<String[]>>();
  private HashMap<String, Integer> route_id = new HashMap<>();
  private HashMap<String, Integer> mirror_id = new HashMap<>();
  private HashMap<String, ArrayList<String[]>> pairPath = new HashMap<>();
  private HashMap<String, ArrayList<String>> routes = new HashMap<>();
  private HashMap<String, String> macInterfaceMap = new HashMap<>();
  private HashMap<String, String> macPortMap = new HashMap<>();
  private long linkId=0;
  public RoutingManager() {
    logger.debug("initialize network manager");
    networkManager = new NetworkManager();
  }

  public String getDPID(String routerName) {
    logger.info(String.format("getDPID %s", routerName));
    return networkManager.getRouter(routerName).getDPID();
  }

  public String getEdgeRouterbyGateway(String gw) {
    logger.info(String.format("getEdgeRouterbyGateway %s", gw));
    return networkManager.getRouterByGateway(gw);
  }

  public void setQos(String controller, String dpid, String srcip, String destip, String bw) {
    setQos(controller, dpid, srcip, destip, Long.valueOf(bw));
  }

  public void setQos(String controller, String dpid, String srcip, String destip, long bw) {
    logger.info(String.format("setQos %s %s %s %s %s", controller, dpid, srcip, destip, bw));
    JSONObject match = new JSONObject();
    match.put("nw_src", srcip);
    match.put("nw_dst", destip);
    if (router_matches.containsKey(dpid)) {
      router_matches.get(dpid).add(match);
    } else {
      ArrayList<JSONObject> m = new ArrayList<>();
      m.add(match);
      router_matches.put(dpid, m);
    }
    router_queues.get(dpid).add(bw);
    String qurl = SdnUtil.queueURL(controller, dpid);
    JSONObject qdata = SdnUtil.queueData(MAX_RATE, router_queues.get(dpid));
    String res = HttpUtil.postJSON(qurl, qdata);
    logger.debug(res);
    String qosurl = SdnUtil.qosRuleURL(controller, dpid);
    JSONObject qosdata = SdnUtil.qosRuleData(match, router_queues.get(dpid).size() - 1);
    String qosres = HttpUtil.postJSON(qosurl, qosdata);
    logger.debug(qosres);
  }

  private String getNewLinkName(){
    linkId ++;
    return "link" + linkId;
  }

  synchronized public void newRouter(String routerName, String dpid, String mip) {
    logger.debug("RoutingManager: new router " + routerName + " " + dpid);
    logger.info(String.format("newRouter %s %s %s", routerName, dpid, mip));
    if (networkManager.getRouter(routerName) == null) {
//      logger.debug(dpid+":my dpid");
      networkManager.putRouter(new Router(routerName, dpid, mip));
      ArrayList<Long> newqueue = new ArrayList<>();
      newqueue.add(Long.valueOf(1000000));
      router_queues.put(dpid, newqueue);
    }
  }

  public boolean newLink(String linkName, String ipa, String ra, String gw, String controller) {
    logger.info(String.format("newLink %s %s %s %s %s", linkName, ipa, ra, gw, controller));
    logger.debug("RoutingManager: new stitchpoint " + ra + " " + ipa);
    networkManager.addLink(linkName, ipa, ra, gw);
    String dpid = networkManager.getRouter(ra).getDPID();
    String cmd[] = SdnUtil.addrCMD(ipa, dpid, controller);
    boolean result = true;
    String res = HttpUtil.postJSON(cmd[0], new JSONObject(cmd[1]));
    logger.debug(res);
    cmd[cmd.length - 1] = res;
    if (res.toString().contains("success")) {
      addEntry_HashList(sdncmds, dpid, cmd);
    } else {
      result = false;
    }
    return result;
  }

  public boolean newLink(String linkName, String ipa, String ra, String ipb, String rb, String controller, String cap) {
    long capacity = Long.valueOf(cap);
    return newLink(linkName, ipa, ra, ipb, rb, controller, capacity);
  }

  public boolean newLink(String linkName, String ipa, String ra, String ipb, String rb, String controller, long capacity) {
    logger.info(String.format("newLink %s %s %s %s %s %s %s", linkName, ipa, ra, ipb, rb, controller, capacity));
    logger.debug("RoutingManager: new link " + ra + " " + ipa + " " + rb + " " + ipb);
    networkManager.addLink(linkName, ipa, ra, ipb, rb, capacity);
    String dpid = getDPID(ra);
    String[] cmd = SdnUtil.addrCMD(ipa, dpid, controller);
    boolean result = true;
    String res = HttpUtil.postJSON(cmd[0], new JSONObject(cmd[1]));
    logger.debug(res);
    cmd[cmd.length - 1] = res;
    if (res.toString().contains("success")) {
      addEntry_HashList(sdncmds, dpid, cmd);
    } else {
      result = false;
    }
    dpid = getDPID(rb);
    cmd = SdnUtil.addrCMD(ipb, dpid, controller);
    res = HttpUtil.postJSON(cmd[0], new JSONObject(cmd[1]));
    if (res.toString().contains("success")) {
      addEntry_HashList(sdncmds, dpid, cmd);
      logger.debug(res);
    } else {
      logger.warn(res);
      result = false;
    }
    return result;
  }

  public List<String> getNeighbors(String routerName){
    return networkManager.getNeighborNodes(routerName);
  }

  public void configurePath(String dest, String nodename, String gateway, String controller) {
    logger.info(String.format("configurePath %s %s %s %s", dest, nodename, gateway, controller));
    String gwdpid = networkManager.getRouter(nodename).getDPID();
    if (gwdpid == null) {
      logger.debug("No router named " + nodename + " not found");
      return;
    }
    ArrayList<String[]> paths = getBroadcastRoutes(gwdpid, gateway);
    for (String[] path : paths) {
      String res = singleStepRouting(dest, path[1], path[0], controller);
      //logger.debug(path[0]+" "+path[1]);
    }
  }

  public String singleStepRouting(String dest, String gateway, String dpid, String controller){
    String[] cmd = SdnUtil.routingCMD(dest, gateway, dpid, controller);
    String res = HttpUtil.postJSON(cmd[0], new JSONObject(cmd[1]));
    if (res.contains("success")) {
      logger.debug(res);
    } else {
      logger.warn(res);
    }
    cmd[cmd.length - 1] = res;
    addEntry_HashList(sdncmds, dpid, cmd);
    return  res;
  }

  //gateway is the gateway for nodename
  public boolean configurePath(String dest, String nodename, String targetIP, String targetnodename,
                               String gateway, String controller, String bandWitdth) {
    long bw = Long.valueOf(bandWitdth);
    return configurePath(dest, nodename, targetIP, targetnodename, gateway, controller, bw);
  }

  public boolean configurePath(String dest, String nodename, String targetIP, String targetnodename,
                               String gateway, String controller, long bw) {
    logger.info(String.format("configurePath %s %s %s %s %s %s %s", dest, nodename, targetIP, targetnodename, gateway, controller, bw));
    logger.debug("Network Manager: Configuring path for " + dest + " " + nodename + " " + targetIP + " " + targetnodename + " " + gateway);
    String gwdpid = networkManager.getRouter(nodename).getDPID();
    String targetdpid = networkManager.getRouter(targetnodename).getDPID();
    if (gwdpid == null || targetdpid == null) {
      logger.debug("No router named " + nodename + " not found");
      return false;
    }
    ArrayList<String[]> paths = getPairRoutes(gwdpid, targetdpid, gateway, bw);
    pairPath.put(getPathID(dest, targetIP), paths);
    ArrayList<String> dpids = new ArrayList<String>();
    boolean res = true;
    for (String[] path : paths) {
      Router logRouter = networkManager.getRouterByDPID(path[0]);
      if (path[2] != null) {
        for(String interfaceName: logRouter.getInterfaces()){
          Link link = networkManager.getLink(networkManager.getInterface(interfaceName).getLinkName());
          if(link.hasNode(path[2])){
            link.useBW(bw);
            networkManager.putLink(link);
            break;
          }
        }
      }
      res &= addRoute(dest, targetIP, path[1], path[0], controller);
    }
    return res;
  }

  public void removePath(String srcIP, String dstIP, String controller) {
    logger.info(String.format("removePath %s %s %s", srcIP, dstIP, controller));
    ArrayList<String[]> paths = pairPath.get(getPathID(srcIP, dstIP));
    for (String[] path : paths) {
      int routeid = route_id.get(getRouteKey(srcIP, dstIP, path[0]));
      deleteRoute(path[0], String.valueOf(routeid), controller);
    }
  }

  public void checkFLowTableForPair(String srcIp, String destIp, String p1, String p2, String sshKey, Logger logger){
    ArrayList<String[]> paths = pairPath.getOrDefault(getPathID(srcIp, destIp),new ArrayList<>());
    if(paths.isEmpty()){
      logger.warn(String.format("No path configured from %s to %s", srcIp, destIp));
    }
    for(int i=0;i<paths.size(); i++){
      String[] hop = paths.get(i);
      String src = networkManager.getRouterByDPID(hop[0]).getRouterName();
      String dst=null;
      if(i<paths.size()-1){
        dst = networkManager.getRouterByDPID(paths.get(i+1)[0]).getRouterName();
      }
      String flow = getFlowOnRouter(networkManager.getRouterByDPID(hop[0]).getManagementIP(), p1, p2, sshKey);
      if(flow.contains("actions=CONTROLLER")) {
        logger.warn(String.format("%s %s -> %s: Failure\n %s", hop[0], src, dst, flow));
      }else if(flow.contains("output:")){
        logger.info(String.format("%s %s -> %s: Success\n %s", hop[0], src, dst, flow));
      }else{
        logger.warn(String.format("%s %s -> %s: Unknown\n %s", hop[0], src, dst, flow));
      }
    }

    paths = pairPath.getOrDefault(getPathID(destIp, srcIp),new ArrayList<>());
    if(paths.isEmpty()){
      logger.warn(String.format("No path configured from %s to %s", destIp, srcIp));
    }
    for(int i=0;i<paths.size(); i++){
      String[] hop = paths.get(i);
      String src = networkManager.getRouterByDPID(hop[0]).getRouterName();
      String dst=null;
      if(i<paths.size()-1){
        dst = networkManager.getRouterByDPID(paths.get(i+1)[0]).getRouterName();
      }
      String flow = getFlowOnRouter(networkManager.getRouterByDPID(hop[0]).getManagementIP(), p1, p2, sshKey);
      if(flow.contains("actions=CONTROLLER")) {
        logger.warn(String.format("%s %s -> %s: Failure\n %s", hop[0], src, dst, flow));
      }else if(flow.contains("output:")){
        logger.info(String.format("%s %s -> %s: Success\n %s", hop[0], src, dst, flow));
      }else{
        logger.warn(String.format("%s %s -> %s: Unknown\n %s", hop[0], src, dst, flow));
      }
    }
  }

  public String getFlowOnRouter(String ip, String srcIp, String destIp, String sshKey){
    String result = Exec.sshExec("root", ip,
        "ovs-ofctl dump-flows br0", sshKey)[0];
    String[]parts = result.split("\n");
    String res ="";
    for(String s: parts){
      if((s.contains("nw_src="+srcIp) && s.contains("nw_dst="+destIp))){
        res = res + s + "\n";
      }
    }
    return res;
  }

  public String setMirror(String controller, String dpid, String source, String dst, String gw) {
    logger.info(String.format("setMirror %s %s %s %s %s", controller, dpid, source, dst, gw));
    String[] cmd = SdnUtil.mirrorCMD(controller, dpid, source, dst, gw);
    addEntry_HashList(sdncmds, dpid, cmd);
    String res = HttpUtil.postJSON(cmd[0], new JSONObject(cmd[1]));
    logger.debug(res);
    if (res.contains("success")) {
      int id = Integer.valueOf(res.split("mirror_id=")[1].split("]")[0]);
      mirror_id.put(getRouteKey(source, dst, dpid), id);
      cmd[cmd.length - 1] = res;
    } else {
      //revoke all previous routes
      //TODO
    }
    return res;
  }

  public String delMirror(String controller, String dpid, String source, String dst) {
    logger.info(String.format("delMirror %s %s %s %s %s", controller, dpid, source, dst));
    int id = mirror_id.get(getRouteKey(source, dst, dpid));
    String[] cmd = SdnUtil.delMirrorCMD(String.valueOf(id), dpid, controller);
    addEntry_HashList(sdncmds, dpid, cmd);
    String res = HttpUtil.delete(cmd[0], cmd[1]);
    if (res.contains("success")) {
      logger.debug(res);
    } else {
      logger.warn(res);
      //revoke all previous routes
      //TODO
    }
    return res;
  }

  public boolean findPath(String node1, String node2, String bandWidth) {
    long bw = Long.valueOf(bandWidth);
    return findPath(node1, node2, Long.valueOf(bandWidth));
  }

  public boolean findPath(String node1, String node2, long bw) {
    logger.info(String.format("findPath %s %s %s", node1, node2, bw));
    ArrayList<String[]> paths = getPairRoutes(getDPID(node1), getDPID(node2), "demo", bw);
    return paths.size() > 0;
  }

  public void setOvsdbAddr(String controller) {
    logger.info(String.format("setOvsdbAddr %s", controller));
    for (Router r : networkManager.getRouters()) {
      String[] cmd = SdnUtil.ovsdbCMD(r.managementIP, r.dpid, controller);
      String res = HttpUtil.putString(cmd[0], cmd[1]);
      addEntry_HashList(sdncmds, r.getDPID(), cmd);
      logger.debug(res);
    }
  }

  public void replayCmds(String dpid) {
    logger.info(String.format("replayCmds %s", dpid));
    if (sdncmds.containsKey(dpid)) {
      ArrayList<String[]> l = sdncmds.get(dpid);
      for (String[] cmd : l) {
        logger.debug("Replay:" + cmd[0] + cmd[1]);
        if (cmd[2].equals("postJSON")) {
          String result = HttpUtil.postJSON(cmd[0], new JSONObject(cmd[1]));
          if (result.contains("success")) {
            logger.debug(result);
          } else {
            logger.warn(result);
          }
        } else {
          HttpUtil.putString(cmd[0], cmd[1]);
        }
      }
    } else {
      logger.debug("No cmd to replay");
    }
  }

  public void printLinks() {
    logger.info("printLinks");
    for (Link l : networkManager.getLinks()) {
      logger.debug(l.toString());
    }
  }

  private boolean addRoute(String destIp, String srcIp, String gateWay, String dpid, String
      controller) {
    String[] cmd = SdnUtil.routingCMD(destIp, srcIp, gateWay, dpid, controller);
    String res = HttpUtil.postJSON(cmd[0], new JSONObject(cmd[1]));
    logger.debug(String.join("\n" + cmd));
    logger.debug(res);
    if (res.contains("success")) {
      int id = Integer.valueOf(res.split("route_id=")[1].split("]")[0]);
      cmd[cmd.length - 1] = res;
      addEntry_HashList(sdncmds, dpid, cmd);
      return true;
    } else {
      return false;
    }
  }

  private boolean deleteRoute(String dpid, String routeid, String controller) {
    String[] cmd = SdnUtil.delRoutingCMD(routeid, dpid, controller);
    String res = HttpUtil.delete(cmd[0], cmd[1]);
    if (res.contains("success")) {
      logger.debug(res);
      addEntry_HashList(sdncmds, dpid, cmd);
      return true;
    } else {
      logger.warn(res);
    }
    return false;
  }

  private String getPathID(String src, String dst) {
    return src + dst;
  }

  /*
  private boolean deleteRoute(String dpid, String routeid, String controller) {
    String[] cmd = delRoutingCMD(dpid, routeid, controller);
    String res = HttpUtil.delete(cmd[0], cmd[1]);
    System.out.println(res);
    if (res.contains("success")) {
      int id = Integer.valueOf(res.split("route_id=")[1].split("]")[0]);
      cmd[cmd.length-1] = res;
      addEntry_HashList(sdncmds, dpid, cmd);
      return true;
    } else {
      return false;
    }
  }*/

  private String getRouteKey(String src, String dst, String dpid) {
    return src + dst + dpid;
  }

  private void addEntry_HashList(HashMap<String, ArrayList<String[]>> map, String key, String[] entry) {
    if (map.containsKey(key)) {
      ArrayList<String[]> l = map.get(key);
      l.add(entry);
    } else {
      ArrayList<String[]> l = new ArrayList<String[]>();
      l.add(entry);
      map.put(key, l);
    }
  }

  //TODO: get shortest path for two pairs
  private ArrayList<String[]> getPairRoutes(String srcdpid, String dstdpid, String gateway, long bw) {
    HashSet<String> knownrouters = new HashSet<String>();
    //path queue: [dpid, path]
    //format of path:Arraylist([router_id,gateway, IP prefix of the gateway])
    ArrayList<String> queue = new ArrayList<String>();
    queue.add(srcdpid);
    knownrouters.add(srcdpid);
    //{router-id:gateway}
    ArrayList<String[]> knownpaths = new ArrayList<String[]>();
    String[] initroute = new String[3];
    initroute[0] = srcdpid;
    initroute[1] = gateway;
    initroute[2] = null;
    knownpaths.add(initroute);
    if (srcdpid.equals(dstdpid)) {
      return knownpaths;
    }
    int start = 0;
    int end = 0;
    boolean foundpath = false;
    if (srcdpid.equals(dstdpid)) {
      foundpath = true;
    }
    while (start <= end && !foundpath) {
      //logger.debug("queue[start]"+queue.get(start));
      String rid = queue.get(start);
      start += 1;
      Router logRouter = networkManager.getRouterByDPID(rid);
      if (logRouter != null) {
        for (Interface neighborInterface : networkManager.getNeighborInterfaces(logRouter.getRouterName())) {
          //ip the ip of hte neighbor interface
          Link link  = networkManager.getLink(neighborInterface.getLinkName());
          String pairip = neighborInterface.getIp();
          //logger.debug("neighborIP: "+ip);
          if (link.getAvailableBW() < bw) {
            continue;
          }
          String pairrouter = getDPID(neighborInterface.getNodeName());
          if (!knownrouters.contains(pairrouter)) {
            knownrouters.add(pairrouter);
            end += 1;
            String[] path = new String[3];
            path[0] = pairrouter;
            logger.debug(pairip);
            String ip = networkManager.getInterface(link.getPairInterfaceName(neighborInterface.getName())).getIp();
            path[1] = ip.split("/")[0];
            path[2] = logRouter.getRouterName();
            logger.debug(path[1]);
            knownpaths.add(path);
            if (pairrouter.equals(dstdpid)) {
              foundpath = true;
              break;
            } else {
              queue.add(pairrouter);
            }
          }
        }
      } else {
        logger.debug("LogRouter null");
      }
    }
    ArrayList<String[]> spaths = new ArrayList<String[]>();
    if (foundpath) {
      String[] curpath = knownpaths.get(knownpaths.size() - 1);
      spaths.add(curpath);
      for (int i = knownpaths.size() - 2; i >= 0; i--) {
        if (knownpaths.get(i)[0].equals(getDPID(curpath[2]))) {
          spaths.add(knownpaths.get(i));
          curpath = knownpaths.get(i);
        }
      }
    }
    return spaths;
  }

  //FIXME: There might be bug, but haven't got the chance to look into it.
  private ArrayList<String[]> getBroadcastRoutes(String gwdpid, String gateway) {
    //logger.debug("All logRouters and logLinks");
    //for(String[] link:logLinks){
    //  //logger.debug(link[0]+" "+link[1]);
    //}
    //for(String[] link:ip_router){
    //  //logger.debug(link[0]+" "+link[1]);
    //}
    HashSet<String> knownrouters = new HashSet<String>();
    //path queue: [dpid, path]
    //format of path:Arraylist([router_id,gateway])
    ArrayList<String> queue = new ArrayList<String>();
    queue.add(gwdpid);
    knownrouters.add(gwdpid);
    //{router-id:gateway}
    ArrayList<String[]> knownpaths = new ArrayList<String[]>();
    String[] initroute = new String[2];
    initroute[0] = gwdpid;
    initroute[1] = gateway;
    knownpaths.add(initroute);
    int start = 0;
    int end = 0;
    while (start <= end) {
      //logger.debug("queue[start]"+queue.get(start));
      String rid = queue.get(start);
      start += 1;
      Router logRouter = networkManager.getRouterByDPID(rid);
      if (logRouter != null) {
        for (Interface neighborInterface : networkManager.getNeighborInterfaces(logRouter.getRouterName())) {
          //ip the ip of hte neighbor interface
          Link link  = networkManager.getLink(neighborInterface.getLinkName());
          String ip = neighborInterface.getIp();
          //logger.debug("neighborIP: "+ip);
          String pairrouter = getDPID(neighborInterface.getNodeName());
          if (!knownrouters.contains(pairrouter)) {
            knownrouters.add(pairrouter);
            queue.add(pairrouter);
            end += 1;
            String[] path = new String[2];
            path[0] = getDPID(networkManager.getPairRouter(ip));
            path[1] = networkManager.getPairIP(ip).split("/")[0];
            println(path[1]);
            knownpaths.add(path);
          }
        }
      } else {
        logger.debug("LogRouter null");
      }
    }
    return knownpaths;
  }

  private void println(String out) {
    logger.debug(out);
  }

  synchronized public void updateInterfaceMac(String node, String link, String mac){
    if(mac != null){
        String oldValue = macInterfaceMap.put(mac, NetworkUtil.computeInterfaceName(node, link));
        if(!mac.equals(oldValue)){
          logger.debug(String.format("Mac address for %s updated from %s to %s", NetworkUtil.computeInterfaceName(node
          , link), oldValue, mac));
          if(macPortMap.containsKey(mac)){
            networkManager.updateInterface(NetworkUtil.computeInterfaceName(node, link), macPortMap.get(mac), mac);
          }
        }
    }
  }

  synchronized public void updatePortMac(String controller, String dpid){
    JSONObject portDesc = SdnUtil.getPortDesc(controller, dpid);
    try {
      for (Object key : portDesc.keySet()) {
        JSONArray ports = portDesc.getJSONArray((String) key);
        for (int i = 0; i < ports.length(); i++) {
          try {
            JSONObject port = ports.getJSONObject(i);
            String hwAddr = port.getString("hw_addr");
            String portNum = String.valueOf(port.getInt("port_no"));
            String oldVal = macPortMap.put(hwAddr, portNum);
            if (!portNum.equals(oldVal)) {
              logger.debug(String.format("Port number for hw: %s on dpid: %s updated from %s to %s",
                  hwAddr, dpid, oldVal, portNum));
              if (macInterfaceMap.containsKey(hwAddr)) {
                networkManager.updateInterface(macInterfaceMap.get(hwAddr), portNum, hwAddr);
              }
            }
          }catch (Exception e){
            logger.trace(e.getMessage());
          }
        }
      }
    }catch (Exception e){
      logger.error(e.getMessage());
    }
  }

  public void updateAllPorts(String controller){
    for(String dpid: networkManager.getAllDpids()){
      updatePortMac(controller, dpid);
    }
  }

  public boolean waitTillAllOvsConnected(String controller){
    boolean flag = true;
    while(flag) {
      flag = false;
      Collection<String> dpids = SdnUtil.getAllSwitches(controller);
      for (String dpid: networkManager.getAllDpids()){
        if(!dpids.contains(dpid)){
          flag = true;
          break;
        }
      }
      if(!flag){
        return true;
      }{
        try{
          Thread.sleep(5000);
        }catch (Exception e){
          ;
        }
      }
    }
    return true;
  }

  public boolean setNextHops(String nodeName, String controller, int groupId,
                             String destIP, HashMap<String, Integer> nbs){
    String dpid = getDPID(nodeName);
    HashMap<Integer, Integer> ports = new HashMap<>();
    for(String ifaceName: networkManager.getRouter(nodeName).getInterfaces()){
      String pairIface = networkManager.getPairInterface(ifaceName);
      if(pairIface!= null) {
        String neighborNode = networkManager.getInterface(pairIface).getNodeName();
        if (nbs.containsKey(neighborNode)) {
          ports.put(Integer.valueOf(networkManager.getInterface(ifaceName).getPort()), nbs.get(neighborNode));
        }
      }
    }
    String res = SdnUtil.addSelectGroup(controller, dpid, groupId, ports);
    logger.debug(res);
    res = SdnUtil.setRoutingFlow(controller, dpid, destIP, groupId );
    logger.debug(res);
    return true;
  }

  public boolean setOutPort(String nodeName, String controller, String linkName, String destIP){
    Link link = networkManager.getLink(linkName);
    if(nodeName.equals(networkManager.getInterface(link.getInterfaceA()).getNodeName())) {
      int port = Integer.valueOf(networkManager.getInterface(link.getInterfaceA()).getPort());
      String dpid = getDPID(nodeName);
      SdnUtil.setRoutingOutputFlow(controller, dpid, destIP, port);
    }else{
      int port = Integer.valueOf(networkManager.getInterface(link.getInterfaceB()).getPort());
      String dpid = getDPID(nodeName);
      SdnUtil.setRoutingOutputFlow(controller, dpid, destIP, port);
    }
    return true;
  }
}
