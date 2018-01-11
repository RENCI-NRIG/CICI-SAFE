package sdx.networkmanager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import org.json.HTTP;
import org.json.JSONObject;
import sdx.utils.Exec;

import java.util.HashSet;
import java.lang.System;

import sdx.utils.HttpUtil;

public class NetworkManager {
  final private static Logger logger = Logger.getLogger(NetworkManager.class);
  private  ArrayList<Router> routers=new ArrayList<Router>();
  private  ArrayList<String[]>ip_router=new ArrayList<String[]>();
  private  ArrayList<String[]>links=new ArrayList<String[]>();
  private HashMap<String, ArrayList<String[]>> sdncmds=new HashMap<String,ArrayList<String[]>>();

  public String getDPID(String routerid) {
    Router router = getRouter(routerid);
    if (router != null)
      return router.getDPID();
    else
      return null;
  }

  public String findRouterbyGateway(String gw){
    for(Router r:routers){
      if(r.hasGateway(gw)){
        return r.getRouterID();
      }
    }
    return null;
  }

  public  void addLink(String ipa, String ra, String gw){
    Router router=getRouter(ra);
    if(router!=null){
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

  public void addLink(String ipa, String ra, String ipb, String rb) {
    //logger.debug(ipa+" "+ipb);
    if (!ipa.equals("") && !ipb.equals("")) {
      putLink(ipa, ipb);
      putLink(ipb, ipa);
    }
    Router router = getRouter(ra);
    if (router != null) {
      router.addInterface(ipa);
      putPairRouter(ipa, router.getDPID());
      if (!rb.equals("")) {
        //logger.debug("addneighbor"+ipa+" "+ipb);
        router.addNeighbor(ipb);
        putRouter(router);
        router = getRouter(rb);
        router.addInterface(ipb);
        router.addNeighbor(ipa);
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
    } else {
      Router router = getRouter(routerid);
      router.updateInterfaceNum(numinterfaces);
    }
  }

  public void newRouter(String routerid, String dpid, int numInterfaces, String mip) {
    logger.debug("RoutingManager: new router " + routerid + " " + dpid);
    addRouter(routerid, dpid, numInterfaces, mip);
  }

  public void newLink(String ipa, String ra, String gw,String controller) {
    logger.debug("RoutingManager: new link "+ra+ipa);
    System.out.println("new stitch "+ra +" gateway:"+ipa);
    addLink(ipa,ra, gw);
    String dpid= getRouter(ra).getDPID();
    String cmd[] = addrCMD(ipa,dpid,controller);
    String res = HttpUtil.postJSON(cmd[0],new JSONObject(cmd[1]));
    System.out.println(res);
    System.out.println(cmd[0]);
    System.out.println(cmd[1]);
    addEntry_HashList(sdncmds,dpid,cmd);
  }

  public void newLink(String ipa, String ra, String ipb, String rb, String controller){
    logger.debug("RoutingManager: new link "+ra+ipa+rb+ipb);
    System.out.println("new link  ra "+ra+" ipa "+ipa+ " rb "+rb +" ipb "+ipb);
    addLink(ipa,ra,ipb,rb);
    String dpid=getDPID(ra);
    String[] cmd = addrCMD(ipa,dpid,controller);
    String res = HttpUtil.postJSON(cmd[0],new JSONObject(cmd[1]));
    System.out.println(res);
    System.out.println(cmd[0]);
    System.out.println(cmd[1]);
    addEntry_HashList(sdncmds,dpid,cmd);
    dpid=getDPID(rb);
    cmd = addrCMD(ipb,dpid,controller);
    res = HttpUtil.postJSON(cmd[0],new JSONObject(cmd[1]));
    System.out.println(cmd[0]);
    System.out.println(cmd[1]);
    System.out.println(res);
    addEntry_HashList(sdncmds,dpid,cmd);
  }

  public void configurePath(String dest, String nodename, String gateway, String controller) {
    String gwdpid = getRouter(nodename).getDPID();
    if (gwdpid == null) {
      logger.debug("No router named " + nodename + " not found");
      return;
    }
    ArrayList<String[]>paths=getBroadcastRoutes(gwdpid,gateway);
    for(String[] path: paths){
      String []cmd=routingCMD(dest, path[1], path[0], controller);
      HttpUtil.postJSON(cmd[0],new JSONObject(cmd[1]));
      addEntry_HashList(sdncmds,path[0],cmd);
      //logger.debug(path[0]+" "+path[1]);
    }
  }

  public void configurePath(String dest, String nodename, String targetIP, String targetnodename, String gateway, String controller) {
    logger.debug("Network Manager: Configuring path for " + dest + " " + nodename + " " + targetIP + " " + targetnodename + " " + gateway);
    String gwdpid = getRouter(nodename).getDPID();
    String targetdpid = getRouter(targetnodename).getDPID();
    if (gwdpid == null || targetdpid == null) {
      logger.debug("No router named " + nodename + " not found");
      return;
    }
    ArrayList<String[]>paths=getPairRoutes(gwdpid,targetdpid,gateway);
    for(String[] path: paths){
      String []cmd=routingCMD(dest,targetIP, path[1], path[0], controller);
      String res = HttpUtil.postJSON(cmd[0],new JSONObject(cmd[1]));
      System.out.println(cmd[0]);
      System.out.println(cmd[1]);
      System.out.println(res);
      addEntry_HashList(sdncmds,path[0],cmd);
      //logger.debug(path[0]+" "+path[1]);
    }
  }

  public Router getRouter(String routername) {
    for (Router r : routers) {
      if (r.getRouterID().equals(routername)) {
        return r;
      }
    }
    return null;
  }

  public void setOvsdbAddr(String controller){
    for(Router r:routers){
      String[] cmd=ovsdbCMD(r,controller);
      String res=HttpUtil.putString(cmd[0],cmd[1]);
      addEntry_HashList(sdncmds,r.getDPID(),cmd);
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

  private void addEntry_HashList(HashMap<String,ArrayList<String[]>> map,String key, String[] entry){
    if(map.containsKey(key)){
      ArrayList<String[]> l=map.get(key);
      l.add(entry);
    }
    else{
      ArrayList<String[]> l=new ArrayList<String[]>();
      l.add(entry);
      map.put(key, l);
    }
  }

  //TODO: get shortest path for two pairs
  private ArrayList<String[]> getPairRoutes(String srcdpid, String dstdpid, String gateway) {

    HashSet<String> knownrouters = new HashSet<String>();
    //path queue: [dpid, path]
    //format of path:Arraylist([router_id,gateway])
    ArrayList<String> queue = new ArrayList<String>();
    queue.add(srcdpid);
    knownrouters.add(srcdpid);
    //{router-id:gateway}
    ArrayList<String[]> knownpaths = new ArrayList<String[]>();
    String[] initroute = new String[2];
    initroute[0] = srcdpid;
    initroute[1] = gateway;
    knownpaths.add(initroute);
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
        ArrayList<String> nips = getNeighborIPs(rid);
        for (String ip : nips) {
          //logger.debug("neighborIP: "+ip);
          String pairrouter = getPairRouter(ip);
          if (!knownrouters.contains(pairrouter)) {
            knownrouters.add(pairrouter);
            end += 1;
            String[] path = new String[3];
            path[0] = getPairRouter(ip);
            logger.debug(ip);
            path[1] = getPairIP(ip).split("/")[0];
            path[2] = getPairIP(ip);
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
    String[] curpath = knownpaths.get(knownpaths.size() - 1);
    spaths.add(curpath);
    for (int i = knownpaths.size() - 2; i >= 0; i--) {
      if (knownpaths.get(i)[0].equals(getPairRouter(curpath[2]))) {
        spaths.add(knownpaths.get(i));
        curpath = knownpaths.get(i);
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

  private  String[] addrCMD(String addr, String dpid, String controller){
    //String cmd="curl -X POST -d {\"address\":\""+addr+"\"} "+controller+"/router/"+dpid;
    String[]res=new String[3];
    res[0]="http://"+controller+"/router/"+dpid;
    res[1]="{\"address\":\""+addr+"\"} ";
    res[2]="postJSON";
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
    String[] res=new String[3];
    res[0]="http://"+controller+"/router/"+dpid;
    res[1]="{\"destination\":"+dst+"\",\"gateway\":\""+gw+"\"}";
    res[2]="postJSON";
    return res;
  }

  private  String[] routingCMD(String dst,String src,String gw, String dpid, String controller){
    //String cmd="curl -X POST -d {\"destination\":\""+dst+"\",\"source\":\""+src+"\",\"gateway\":\""+gw+"\"} "+controller+"/router/"+dpid;
    String[] cmd= new String[3];
    cmd[0]="http://"+controller+"/router/"+dpid;
    cmd[1]="{\"destination\":\""+dst+"\",\"source\":\""+src+"\",\"gateway\":\""+gw+"\"}";
    cmd[2]="postJSON";
    return cmd;
  }

  private String[] ovsdbCMD(Router r, String controller) {
    //String cmd="curl -X PUT -d \'\"tcp:"+r.getManagementIP()+":6632\"\' "+controller+"/v1.0/conf/switches/"+r.getDPID()+"/ovsdb_addr";
    String[]res=new String[3];
    res[1]="\"tcp:"+r.getManagementIP()+":6632\"";
    res[0]="http://"+controller+"/v1.0/conf/switches/"+r.getDPID()+"/ovsdb_addr";
    res[2]="putString";
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

  private void putLink(String linka, String linkb) {
    for (String[] link : links) {
      if (link[0].equals(linka) && link[1].equals(linkb)) {
        return;
      }
    }
    String[] link = new String[2];
    link[0] = linka;
    link[1] = linkb;
    links.add(link);
  }

  private String getPairIP(String ip) {
    for (String[] link : links) {
      if (link[0].equals(ip))
        return link[1];
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


class RLink {
  private String ifa = "";
  private String ifb = "";
  private String ra = "";
  private String rb = "";

  public RLink(String ia, String ib, String routera, String routerb) {
    ifa = ia;
    ifb = ib;
    ra = routera;
    rb = routerb;
  }

  public String pair_ip(String ip) {
    if (ip.equals(ifa))
      return ifb;
    else if (ip.equals(ifb))
      return ifa;
    else
      return "";
  }

  public boolean equals(RLink link) {
    if (ifa == link.ifa && ifb == link.ifb || ifa == link.ifb && ifb == link.ifa) {
      if (ra == link.ra && rb == link.rb || ra == link.rb && rb == link.ra) {
        return true;
      }
    }
    return false;
  }
}

