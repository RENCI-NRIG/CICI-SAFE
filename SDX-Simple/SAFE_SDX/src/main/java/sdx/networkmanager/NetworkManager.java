package sdx.networkmanager;

import common.utils.Exec;
import common.utils.HttpUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class NetworkManager {
  final static Logger logger = LogManager.getLogger(NetworkManager.class);

  final static int MAX_RATE = 2000000;
  private HashMap<String, ArrayList<Long>> router_queues = new HashMap<>();
  private HashMap<String, ArrayList<JSONObject>> router_matches = new HashMap<>();
  private ArrayList<Router> routers = new ArrayList<Router>();
  private ArrayList<String[]> ip_router = new ArrayList<String[]>();
  private ArrayList<Link> links = new ArrayList<Link>();
  private HashMap<String, ArrayList<String[]>> sdncmds = new HashMap<String, ArrayList<String[]>>();
  private HashMap<String, Integer> route_id = new HashMap<>();
  private HashMap<String, Integer> mirror_id = new HashMap<>();
  private HashMap<String, ArrayList<String[]>> pairPath = new HashMap<>();
  private HashMap<String, ArrayList<String>> routes = new HashMap<>();
  public NetworkManager() {
    logger.debug("initialize network manager");
  }

  private static String[] mirrorCMD(String controller, String dpid, String source, String dst, String gw) {
    String[] res = new String[3];
    res[0] = "http://" + controller + ":8080/router/" + dpid;
    //res[1] = "{\"source\":\"" + source + "\", \"destination\": \"" + dst + "\", \"mirror\":\"" + gw + "\"}";
    JSONObject params = new JSONObject();
    params.put("mirror", gw);
    if (source != null) {
      params.put("source", source);
    }
    if (dst != null) {
      params.put("destination", dst);
    }
    res[1] = params.toString();
    res[2] = "postJSON";
    return res;
  }

  public String getDPID(String routerid) {
    logger.info(String.format("getDPID %s", routerid));
    Router router = getRouter(routerid);
    if (router != null)
      return router.getDPID();
    else
      return null;
  }

  public String getEdgeRouterbyGateway(String gw) {
    logger.info(String.format("getEdgeRouterbyGateway %s", gw));
    for (Router r : routers) {
      if (r.hasGateway(gw)) {
        return r.getRouterID();
      }
    }
    return null;
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
    String qurl = queueURL(controller, dpid);
    JSONObject qdata = queueData(MAX_RATE, router_queues.get(dpid));
    String res = HttpUtil.postJSON(qurl, qdata);
    logger.debug(res);
    String qosurl = qosRuleURL(controller, dpid);
    JSONObject qosdata = qosRuleData(match, router_queues.get(dpid).size() - 1);
    String qosres = HttpUtil.postJSON(qosurl, qosdata);
    logger.debug(qosres);
  }

  private void addLink(String ipa, String ra, String gw) {
    Router router = getRouter(ra);
    if (router != null) {
      router.addGateway(gw);
      router.addInterface(ipa);
      putPairRouter(ipa, router.getDPID());
      //routers.put(ra,router);
    }
  }

  private void addLink(String ipa, String ra, String ipb, String rb, long cap) {
    //logger.debug(ipa+" "+ipb);
    Router router = getRouter(ra);
    Link link = new Link(ipa, ipb, ra, rb, cap);
    if (!ipa.equals("") && !ipb.equals("")) {
      putLink(link);
    }
    if (router != null) {
      router.addInterface(ipa);
      putPairRouter(ipa, router.getDPID());
      if (!rb.equals("")) {
        //logger.debug("addneighbor"+ipa+" "+ipb);
        router.addNeighbor(ipb, link);
        putRouter(router);
        router = getRouter(rb);
        router.addInterface(ipb);
        router.addNeighbor(ipa, link);
        putRouter(router);
        putPairRouter(ipb, router.getDPID());
      }
      // routers.put(ra,router);
    }
  }

  public void newRouter(String routerid, String dpid, String numInt, String mip) {
    int numinterfaces = Integer.valueOf(numInt);
    newRouter(routerid, dpid, numinterfaces, mip);
  }

  public void newRouter(String routerid, String dpid, int numinterfaces, String mip) {
    logger.debug("RoutingManager: new router " + routerid + " " + dpid);
    logger.info(String.format("newRouter %s %s %s %s", routerid, dpid, numinterfaces, mip));
    if (getRouter(routerid) == null) {
//      logger.debug(dpid+":my dpid");
      routers.add(new Router(routerid, dpid, numinterfaces, mip));
      ArrayList<Long> newqueue = new ArrayList<>();
      newqueue.add(Long.valueOf(1000000));
      router_queues.put(dpid, newqueue);
    } else {
      Router router = getRouter(routerid);
      router.updateInterfaceNum(numinterfaces);
    }
  }

  public boolean newLink(String ipa, String ra, String gw, String controller) {
    logger.info(String.format("newLink %s %s %s %s", ipa, ra, gw, controller));
    logger.debug("RoutingManager: new stitchpoint " + ra + " " + ipa);
    addLink(ipa, ra, gw);
    String dpid = getRouter(ra).getDPID();
    String cmd[] = addrCMD(ipa, dpid, controller);
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

  public boolean newLink(String ipa, String ra, String ipb, String rb, String controller, String cap) {
    long capacity = Long.valueOf(cap);
    return newLink(ipa, ra, ipb, rb, controller, capacity);
  }

  public boolean newLink(String ipa, String ra, String ipb, String rb, String controller, long capacity) {
    logger.info(String.format("newLink %s %s %s %s %s %s", ipa, ra, ipb, rb, controller, capacity));
    logger.debug("RoutingManager: new link " + ra + " " + ipa + " " + rb + " " + ipb);
    addLink(ipa, ra, ipb, rb, capacity);
    String dpid = getDPID(ra);
    String[] cmd = addrCMD(ipa, dpid, controller);
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
    cmd = addrCMD(ipb, dpid, controller);
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

  public void configurePath(String dest, String nodename, String gateway, String controller) {
    logger.info(String.format("configurePath %s %s %s %s", dest, nodename, gateway, controller));
    String gwdpid = getRouter(nodename).getDPID();
    if (gwdpid == null) {
      logger.debug("No router named " + nodename + " not found");
      return;
    }
    ArrayList<String[]> paths = getBroadcastRoutes(gwdpid, gateway);
    for (String[] path : paths) {
      String[] cmd = routingCMD(dest, path[1], path[0], controller);
      String res = HttpUtil.postJSON(cmd[0], new JSONObject(cmd[1]));
      if (res.contains("success")) {
        logger.debug(res);
      } else {
        logger.warn(res);
      }
      cmd[cmd.length - 1] = res;
      addEntry_HashList(sdncmds, path[0], cmd);
      //logger.debug(path[0]+" "+path[1]);
    }
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
    String gwdpid = getRouter(nodename).getDPID();
    String targetdpid = getRouter(targetnodename).getDPID();
    if (gwdpid == null || targetdpid == null) {
      logger.debug("No router named " + nodename + " not found");
      return false;
    }
    ArrayList<String[]> paths = getPairRoutes(gwdpid, targetdpid, gateway, bw);
    pairPath.put(getPathID(dest, targetIP), paths);
    ArrayList<String> dpids = new ArrayList<String>();
    boolean res = true;
    for (String[] path : paths) {
      //Path [dpid,gateway,neighborip]
      Router router = getRouterByDPID(path[0]);
      if (path[2] != null) {
        router.getNeighborLinks().get(path[2]).useBW(bw);
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
      String src = getRouterByDPID(hop[0]).getRouterID();
      String dst=null;
      if(i<paths.size()-1){
        dst = getRouterByDPID(paths.get(i+1)[0]).getDPID();
      }
      String flow = getFlowOnRouter(getRouterByDPID(hop[0]).ip, p1, p2, sshKey);
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
      String src = getRouterByDPID(hop[0]).getRouterID();
      String dst=null;
      if(i<paths.size()-1){
        dst = getRouterByDPID(paths.get(i+1)[0]).getRouterID();
      }
      String flow = getFlowOnRouter(getRouterByDPID(hop[0]).ip, p1, p2, sshKey);
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
    String[] cmd = mirrorCMD(controller, dpid, source, dst, gw);
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
    String[] cmd = delMirrorCMD(String.valueOf(id), dpid, controller);
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
    ArrayList<String[]> paths = getPairRoutes(getDPID(node1), getDPID(node2), "test", bw);
    return paths.size() > 0;
  }

  public Router getRouter(String routername) {
    logger.info(String.format("getRouter %s", routername));
    for (Router r : routers) {
      if (r.getRouterID().equals(routername)) {
        return r;
      }
    }
    return null;
  }

  public void setOvsdbAddr(String controller) {
    logger.info(String.format("setOvsdbAddr %s", controller));
    for (Router r : routers) {
      String[] cmd = ovsdbCMD(r, controller);
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
    for (Link l : links) {
      logger.debug(l.toString());
    }
  }

  public List<String> getNeighbors(String routerName) {
    logger.info(String.format("getNeighbors %s", routerName));
    ArrayList<String> nbs = new ArrayList<String>();
    for (Link link : getRouter(routerName).getNeighborLinks().values()) {
      if (link.ra.equals(routerName) && !link.rb.equals("")) {
        nbs.add(link.rb);
      } else if (link.rb.equals(routerName)) {
        nbs.add(link.ra);
      }
    }
    return nbs;
  }

  private List<String> getNeighborIPs(String routerid) {
    logger.info("getNeighborIPs %s", routerid);
    ArrayList<String> ips = new ArrayList<String>();
    for (String[] intf : ip_router) {
      if (intf[1].equals(routerid)) {
        String pairip = getPairIP(intf[0]);
        if (pairip != null)
          ips.add(pairip);
      }
    }
    return ips;
  }

  private boolean addRoute(String destIp, String srcIp, String gateWay, String dpid, String
      controller) {
    String[] cmd = routingCMD(destIp, srcIp, gateWay, dpid, controller);
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
    String[] cmd = delRoutingCMD(routeid, dpid, controller);
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

  private String queueURL(String controller, String dpid) {
    return "http://" + controller + "/qos/queue/" + dpid;
  }

  private JSONObject queueData(int maxrate, List<Long> queuerate) {
    JSONObject params = new JSONObject();
    params.put("type", "linux-htb");
    params.put("max_rate", String.valueOf(maxrate));
    JSONArray queues = new JSONArray();
    for (Long r : queuerate) {
      JSONObject q = new JSONObject();
      q.put("max_rate", String.valueOf(r));
      queues.put(q);
    }
    params.put("queues", queues);
    logger.debug("queueData" + params.toString());
    return params;
  }

  private String qosRuleURL(String controller, String dpid) {
    return "http://" + controller + "/qos/rules/" + dpid;
  }

  private JSONObject qosRuleData(JSONObject match, int queue_id) {
    JSONObject params = new JSONObject();
    params.put("match", match);
    JSONObject actionjson = new JSONObject();
    actionjson.put("queue", String.valueOf(queue_id));
    params.put("actions", actionjson);
    logger.debug("qosRuleData: " + params.toString());
    return params;
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
      Router router = getRouterByDPID(rid);
      if (router != null) {
        HashMap<String, Link> nbs = router.getNeighborLinks();
        //ArrayList<String> nips=getNeighborIPs(rid);
        for (String ip : nbs.keySet()) {
          //logger.debug("neighborIP: "+ip);
          if (nbs.get(ip).getAvailableBW() < bw) {
            continue;
          }
          String pairrouter = getPairRouter(ip);
          if (!knownrouters.contains(pairrouter)) {
            knownrouters.add(pairrouter);
            end += 1;
            String[] path = new String[3];
            path[0] = pairrouter;
            logger.debug(ip);
            String pairip = getPairIP(ip);
            path[1] = pairip.split("/")[0];
            path[2] = pairip;
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
        logger.debug("Router null");
      }
    }
    ArrayList<String[]> spaths = new ArrayList<String[]>();
    if (foundpath) {
      String[] curpath = knownpaths.get(knownpaths.size() - 1);
      spaths.add(curpath);
      for (int i = knownpaths.size() - 2; i >= 0; i--) {
        if (knownpaths.get(i)[0].equals(getPairRouter(curpath[2]))) {
          spaths.add(knownpaths.get(i));
          curpath = knownpaths.get(i);
        }
      }
    }
    return spaths;
  }

  //FIXME: There might be bug, but haven't got the chance to look into it.
  private ArrayList<String[]> getBroadcastRoutes(String gwdpid, String gateway) {
    //logger.debug("All routers and links");
    //for(String[] link:links){
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
      Router router = getRouterByDPID(rid);
      if (router != null) {
        List<String> nips = getNeighborIPs(rid);
        for (String ip : nips) {
          //logger.debug("neighborIP: "+ip);
          if (!knownrouters.contains(getPairRouter(ip))) {
            knownrouters.add(getPairRouter(ip));
            queue.add(getPairRouter(ip));
            end += 1;
            String[] path = new String[2];
            path[0] = getPairRouter(ip);
            println(ip);
            path[1] = getPairIP(ip).split("/")[0];
            println(path[1]);
            knownpaths.add(path);
          }
        }
      } else {
        logger.debug("Router null");
      }
    }
    return knownpaths;
  }

  private String[] addrCMD(String addr, String dpid, String controller) {
    //String cmd="curl -X POST -d {\"address\":\""+addr+"\"} "+controller+"/router/"+dpid;
    String[] res = new String[4];
    res[0] = "http://" + controller + "/router/" + dpid;
    res[1] = "{\"address\":\"" + addr + "\"} ";
    res[2] = "postJSON";
    //res[3] will be replaced with command result
    res[3] = "resultHolder";
    return res;
  }

  private String[] delMirrorCMD(String routeId, String dpid, String controller) {
    String[] cmd = new String[3];
    cmd[0] = "http://" + controller + "/router/" + dpid;
    cmd[1] = "{\"mirror_id\":\"" + routeId + "\"}";
    cmd[2] = "delete";
    return cmd;
  }

  private String[] routingCMD(String dst, String gw, String dpid, String controller) {
    //String cmd="curl -X POST -d {\"destination\":\""+dst+"\",\"gateway\":\""+gw+"\"} "+controller+"/router/"+dpid;
    String[] res = new String[4];
    res[0] = "http://" + controller + "/router/" + dpid;
    res[1] = "{\"destination\":" + dst + "\",\"gateway\":\"" + gw + "\"}";
    res[2] = "postJSON";
    res[3] = "resultHolder";
    return res;
  }

  private String[] routingCMD(String dst, String src, String gw, String dpid, String controller) {
    //String cmd="curl -X POST -d {\"destination\":\""+dst+"\",\"source\":\""+src+"\",\"gateway\":\""+gw+"\"} "+controller+"/router/"+dpid;
    String[] cmd = new String[4];
    cmd[0] = "http://" + controller + "/router/" + dpid;
    cmd[1] = "{\"destination\":\"" + dst + "\",\"source\":\"" + src + "\",\"gateway\":\"" + gw + "\"}";
    cmd[2] = "postJSON";
    cmd[3] = "resultHolder";
    return cmd;
  }

  private String[] delRoutingCMD(String routeId, String dpid, String controller) {
    String[] cmd = new String[3];
    cmd[0] = "http://" + controller + "/router/" + dpid;
    cmd[1] = "{\"route_id\":\"" + routeId + "\"}";
    cmd[2] = "delete";
    return cmd;
  }

  private String[] ovsdbCMD(Router r, String controller) {
    //String cmd="curl -X PUT -d \'\"tcp:"+r.getManagementIP()+":6632\"\' "+controller+"/v1.0/conf/switches/"+r.getDPID()+"/ovsdb_addr";
    String[] res = new String[3];
    res[1] = "\"tcp:" + r.getManagementIP() + ":6632\"";
    res[0] = "http://" + controller + "/v1.0/conf/switches/" + r.getDPID() + "/ovsdb_addr";
    res[2] = "putString";
    return res;
  }

  private Router getRouterByDPID(String routername) {
    for (Router r : routers) {
      if (r.getDPID().equals(routername)) {
        return r;
      }
    }
    return null;
  }

  private void putRouter(Router router) {
    for (int i = 0; i < routers.size(); i++) {
      if (routers.get(i).getDPID().equals(router.getDPID())) {
        routers.set(i, router);
        return;
      }
    }
    routers.add(router);
  }

  private void putLink(Link l) {
    links.add(l);
  }

  private String getPairIP(String ip) {
    for (Link link : links) {
      if (link.ifa.equals(ip))
        return link.ifb;
      else if (link.ifb.equals(ip)) {
        return link.ifa;
      }
    }
    return null;
  }

  private String getPairRouter(String ip) {
    for (String[] link : ip_router) {
      //logger.debug(link[0]+" "+link[1]);
      if (link[0].equals(ip))
        return link[1];
    }
    return null;
  }

  private void putPairRouter(String ip, String dpid) {
    if (getPairRouter(ip) == null) {
      String[] iprouter = new String[2];
      iprouter[0] = ip;
      iprouter[1] = dpid;
      ip_router.add(iprouter);
    }
  }

  private void println(String out) {
    logger.debug(out);
  }

  class Link {
    private String ifa = "";
    private String ifb = "";
    private String ra = "";
    private String rb = "";
    private long capacity;
    private long usedbw;

    public Link(String ia, String ib, String routera, String routerb, long capacity) {
      this.ifa = ia;
      this.ifb = ib;
      this.ra = routera;
      this.rb = routerb;
      this.capacity = capacity;
      this.usedbw = 0;
    }

    public String pair_ip(String ip) {
      if (ip.equals(ifa))
        return ifb;
      else if (ip.equals(ifb))
        return ifa;
      else
        return "";
    }

    public long getAvailableBW() {
      return this.capacity - this.usedbw;
    }

    public void useBW(long bw) {
      this.usedbw += bw;
    }

    public void releaseBW(long bw) {
      this.usedbw -= bw;
    }

    public boolean equals(Link link) {
      if (ifa.equals(link.ifa) && ifb.equals(link.ifb) || ifa.equals(link.ifb) && ifb.equals(link.ifa)) {
        if (ra.equals(link.ra) && rb.equals(link.rb) || ra.equals(link.rb) && rb.equals(link.ra)) {
          return true;
        }
      }
      return false;
    }

    public String toString() {
      return ra + ":" + ifa + ", " + rb + ":" + ifb + ", cap" + getAvailableBW();
    }
  }

  class Router {
    private String routerid = "";
    private String dpid = "";
    private String ip = "";
    private HashSet<String> interfaces = new HashSet<String>();
    private HashMap<String, Link> neighbors = new HashMap<String, Link>();
    private HashSet<String> customergateways = new HashSet<>();
    private int numInterfaces = 0;

    public Router(String rid, String switch_id, int numintf, String ip) {
      routerid = rid;
      dpid = switch_id;
      this.ip = ip;
      numInterfaces = numintf;
    }

    public HashMap<String, Link> getNeighborLinks() {
      return neighbors;
    }

    public void addInterface(String interfaceIP) {
      interfaces.add(interfaceIP);
    }

    public void addGateway(String gw) {
      logger.debug("Gateway " + gw + " added to " + routerid);
      customergateways.add(gw);
    }

    public boolean hasGateway(String gw) {
      return customergateways.contains(gw);
    }

    public boolean hasIP(String ip) {
      return interfaces.contains(ip);
    }

    public void addNeighbor(String neighborIP, Link link) {
      neighbors.put(neighborIP, link);
    }

    public void updateInterfaceNum(int newnum) {
      numInterfaces = newnum;
    }

    public int getInterfaceNum() {
      return numInterfaces;
    }

    public String getDPID() {
      return dpid;
    }

    public String getRouterID() {
      return routerid;
    }

    public String getManagementIP() {
      return this.ip;
    }
  }
}
