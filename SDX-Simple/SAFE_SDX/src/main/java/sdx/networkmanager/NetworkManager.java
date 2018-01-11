package sdx.networkmanager;

import java.util.*;

import java.util.ArrayList;
import java.util.HashMap;

import org.apache.log4j.Logger;

import org.json.HTTP;
import org.json.JSONArray;
import org.json.JSONObject;
import sdx.utils.Exec;

import java.lang.System;

import sdx.utils.HttpUtil;
import sun.awt.image.ImageWatched;

public class NetworkManager {
  final static Logger logger = Logger.getLogger(NetworkManager.class);

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
      if (ifa == link.ifa && ifb == link.ifb || ifa == link.ifb && ifb == link.ifa) {
        if (ra == link.ra && rb == link.rb || ra == link.rb && rb == link.ra) {
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

    public HashMap<String, Link> getNeighbors() {
      return neighbors;
    }

    public Router(String rid, String switch_id, int numintf, String ip) {
      routerid = rid;
      dpid = switch_id;
      this.ip = ip;
      numInterfaces = numintf;
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

  private HashMap<String, ArrayList<Long>> router_queues = new HashMap<>();
  private HashMap<String, ArrayList<JSONObject>> router_matches = new HashMap<>();
  private ArrayList<Router> routers = new ArrayList<Router>();
  private ArrayList<String[]> ip_router = new ArrayList<String[]>();
  private ArrayList<Link> links = new ArrayList<Link>();
  private HashMap<String, ArrayList<String[]>> sdncmds = new HashMap<String, ArrayList<String[]>>();
  private HashMap<String, Integer> route_id = new HashMap<>();
  private HashMap<String, ArrayList<String>> routes = new HashMap<>();

  public String getDPID(String routerid) {
    Router router = getRouter(routerid);
    if (router != null)
      return router.getDPID();
    else
      return null;
  }

  public String getRouterbyGateway(String gw) {
    for (Router r : routers) {
      if (r.hasGateway(gw)) {
        return r.getRouterID();
      }
    }
    return null;
  }

  public void setQos(String controller, String dpid, String srcip, String destip, long bw) {
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
    JSONObject qdata = queueData(2000000, router_queues.get(dpid));
    String res = HttpUtil.postJSON(qurl, qdata);
    logger.debug(res.toString());
    String qosurl = qosRuleURL(controller, dpid);
    JSONObject qosdata = qosRuleData(match, router_queues.get(dpid).size() - 1);
    String qosres = HttpUtil.postJSON(qosurl, qosdata);
    logger.debug(qosres.toString());
  }

  public void addLink(String ipa, String ra, String gw) {
    Router router = getRouter(ra);
    if (router != null) {
      router.addGateway(gw);
      router.addInterface(ipa);
      putPairRouter(ipa, router.getDPID());
      //routers.put(ra,router);
    }
  }

  private ArrayList<String> getNeighborIPs(String routerid) {
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

  public void addLink(String ipa, String ra, String ipb, String rb, long cap) {
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

  public void addRouter(String routerid, String dpid, int numinterfaces, String mip) {
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

  public void newRouter(String routerid, String dpid, int numInterfaces, String mip) {
    logger.debug("RoutingManager: new router " + routerid + " " + dpid);
    addRouter(routerid, dpid, numInterfaces, mip);
  }

  public boolean newLink(String ipa, String ra, String gw, String controller) {
    logger.debug("RoutingManager: new stitchpoint " + ra + " " + ipa);
    System.out.println("new stitch " + ra + " gateway:" + ipa);
    addLink(ipa, ra, gw);
    String dpid = getRouter(ra).getDPID();
    String cmd[] = addrCMD(ipa, dpid, controller);
    boolean result = true;
    String res = HttpUtil.postJSON(cmd[0], new JSONObject(cmd[1]));
    System.out.println(res);
    if (res.toString().contains("success")) {
      addEntry_HashList(sdncmds, dpid, cmd);
    } else {
      result = false;
    }
    return result;
  }

  public boolean newLink(String ipa, String ra, String ipb, String rb, String controller, long capacity) {
    logger.debug("RoutingManager: new link " + ra + " " + ipa + " " + rb + " " + ipb);
    System.out.println("new link  ra " + ra + " ipa " + ipa + " rb " + rb + " ipb " + ipb + " cap:" + capacity);
    addLink(ipa, ra, ipb, rb, capacity);
    String dpid = getDPID(ra);
    String[] cmd = addrCMD(ipa, dpid, controller);
    boolean result = true;
    String res = HttpUtil.postJSON(cmd[0], new JSONObject(cmd[1]));
    System.out.println(res);
    if (res.toString().contains("success")) {
      addEntry_HashList(sdncmds, dpid, cmd);
    } else {
      result = false;
    }
    dpid = getDPID(rb);
    cmd = addrCMD(ipb, dpid, controller);
    res = HttpUtil.postJSON(cmd[0], new JSONObject(cmd[1]));
    System.out.println(res);
    if (res.toString().contains("success")) {
      addEntry_HashList(sdncmds, dpid, cmd);
    } else {
      result = false;
    }
    return result;
  }

  public void configurePath(String dest, String nodename, String gateway, String controller) {
    String gwdpid = getRouter(nodename).getDPID();
    if (gwdpid == null) {
      logger.debug("No router named " + nodename + " not found");
      return;
    }
    ArrayList<String[]> paths = getBroadcastRoutes(gwdpid, gateway);
    for (String[] path : paths) {
      String[] cmd = routingCMD(dest, path[1], path[0], controller);
      String res = HttpUtil.postJSON(cmd[0], new JSONObject(cmd[1]));
      System.out.println(res);
      addEntry_HashList(sdncmds, path[0], cmd);
      //logger.debug(path[0]+" "+path[1]);
    }
  }

  private void deleteRoute(String dpid, String routeid, String controller) {
  }

  //gateway is the gateway for nodename
  public void configurePath(String dest, String nodename, String targetIP, String targetnodename, String gateway, String controller, long bw) {
    logger.debug("Network Manager: Configuring path for " + dest + " " + nodename + " " + targetIP + " " + targetnodename + " " + gateway);
    String gwdpid = getRouter(nodename).getDPID();
    String targetdpid = getRouter(targetnodename).getDPID();
    if (gwdpid == null || targetdpid == null) {
      logger.debug("No router named " + nodename + " not found");
      return;
    }
    ArrayList<String[]> paths = getPairRoutes(gwdpid, targetdpid, gateway, bw);
    ArrayList<String> dpids = new ArrayList<String>();
    for (String[] path : paths) {
      //Path [dpid,gateway,neighborip]
      Router router = getRouterByDPID(path[0]);
      if (path[2] != null) {
        router.getNeighbors().get(path[2]).useBW(bw);
      }
      String[] cmd = routingCMD(dest, targetIP, path[1], path[0], controller);
      String res = HttpUtil.postJSON(cmd[0], new JSONObject(cmd[1]));
      System.out.println(res);
      if (res.contains("success")) {
        int id = Integer.valueOf(res.split("route_id=")[1].split("]")[0]);
        route_id.put(dest + targetIP + path[0], id);
        dpids.add(path[0]);
        addEntry_HashList(sdncmds, path[0], cmd);
      } else {
        //revoke all previous routes
        //TODO
      }
      //logger.debug(path[0]+" "+path[1]);
    }
  }

  public boolean findPath(String node1, String node2, long bw) {
    ArrayList<String[]> paths = getPairRoutes(getDPID(node1), getDPID(node2), "test", bw);
    return paths.size() > 0;
  }

  public Router getRouter(String routername) {
    for (Router r : routers) {
      if (r.getRouterID().equals(routername)) {
        return r;
      }
    }
    return null;
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

  public void setOvsdbAddr(String controller) {
    for (Router r : routers) {
      String[] cmd = ovsdbCMD(r, controller);
      String res = HttpUtil.putString(cmd[0], cmd[1]);
      addEntry_HashList(sdncmds, r.getDPID(), cmd);
      logger.debug(res);
    }
  }

  public String setMirror(String controller, String dpid, String source, String dst, String gw){
    String[] cmd = mirrorCMD(controller, dpid, source, dst, gw);
    addEntry_HashList(sdncmds, dpid, cmd);
    String res=HttpUtil.postJSON(cmd[0], new JSONObject(cmd[1]));
    return res;
  }

  public void replayCmds(String dpid){
    if(sdncmds.containsKey(dpid)){
      ArrayList<String[]> l=sdncmds.get(dpid);
      for(String[] cmd: l){
        logger.debug("Replay:" + cmd[0] + cmd[1]);
        System.out.println("Replay:" + cmd[0] + cmd[1]);
        if(cmd[2]=="postJSON"){
          String result = HttpUtil.postJSON(cmd[0],new JSONObject(cmd[1]));
          System.out.println(result);
        }
        else{
          HttpUtil.putString(cmd[0],cmd[1]);
        }
      }
    } else {
      logger.debug("No cmd to replay");
    }
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
    //format of path:Arraylist([router_id,gateway])
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
    if(srcdpid == dstdpid){
      foundpath = true;
    }
    while (start <= end && !foundpath) {
      //logger.debug("queue[start]"+queue.get(start));
      String rid = queue.get(start);
      start += 1;
      Router router = getRouterByDPID(rid);
      if (router != null) {
        HashMap<String, Link> nbs = router.getNeighbors();
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
            path[0] = getPairRouter(ip);
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

  public void printLinks() {
    for (Link l : links) {
      logger.debug(l.toString());
    }
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
        ArrayList<String> nips = getNeighborIPs(rid);
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
    String[] res = new String[3];
    res[0] = "http://" + controller + "/router/" + dpid;
    res[1] = "{\"address\":\"" + addr + "\"} ";
    res[2] = "postJSON";
    return res;
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

  private  String[] routingCMD(String dst,String gw, String dpid, String controller){
    //String cmd="curl -X POST -d {\"destination\":\""+dst+"\",\"gateway\":\""+gw+"\"} "+controller+"/router/"+dpid;
    String[] res = new String[3];
    res[0] = "http://" + controller + "/router/" + dpid;
    res[1] = "{\"destination\":" + dst + "\",\"gateway\":\"" + gw + "\"}";
    res[2] = "postJSON";
    return res;
  }

  private String[] routingCMD(String dst, String src, String gw, String dpid, String controller) {
    //String cmd="curl -X POST -d {\"destination\":\""+dst+"\",\"source\":\""+src+"\",\"gateway\":\""+gw+"\"} "+controller+"/router/"+dpid;
    String[] cmd = new String[3];
    cmd[0] = "http://" + controller + "/router/" + dpid;
    cmd[1] = "{\"destination\":\"" + dst + "\",\"source\":\"" + src + "\",\"gateway\":\"" + gw + "\"}";
    cmd[2] = "postJSON";
    return cmd;
  }

  //TODO
  private String[] del_routingCMD(String dpid, String routeid, String controller) {
    //String cmd="curl -X POST -d {\"destination\":\""+dst+"\",\"source\":\""+src+"\",\"gateway\":\""+gw+"\"} "+controller+"/router/"+dpid;
    String[] cmd = new String[3];
    cmd[0] = "http://" + controller + "/router/" + dpid;
    cmd[2] = "DELETE";
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
}

