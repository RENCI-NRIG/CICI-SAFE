package sdx.networkmanager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import sdx.utils.Exec;

import java.util.HashSet;
import java.lang.System;

public class NetworkManager{
  final static Logger logger = Logger.getLogger(Exec.class);	

	
  private  ArrayList<Router> routers=new ArrayList<Router>();
  private  ArrayList<String[]>ip_router=new ArrayList<String[]>();
  private  ArrayList<String[]>links=new ArrayList<String[]>();
  private HashMap<String, ArrayList<String>> sdncmds=new HashMap<String,ArrayList<String>>();

  public  String getDPID(String routerid){
    Router router=getRouter(routerid);
    if(router!=null)
      return router.getDPID();
    else
      return null;
  }

  public  void addLink(String ipa, String ra){
    Router router=getRouter(ra);
    if(router!=null){
      router.addInterface(ipa);
      putPairRouter(ipa, router.getDPID());
      //routers.put(ra,router);
    }
  }

  private  ArrayList<String> getNeighborIPs(String routerid){
    ArrayList<String> ips=new ArrayList<String>();
    for(String [] intf:ip_router){
      if(intf[1].equals(routerid)){
        String pairip=getPairIP(intf[0]);
        if(pairip!=null)
          ips.add(pairip);
      }
    }
    return ips;
  }

  public  void addLink(String ipa, String ra, String ipb,String rb){
    //logger.debug(ipa+" "+ipb);
    if(!ipa.equals("") && !ipb.equals("")){
      putLink(ipa,ipb);
      putLink(ipb,ipa);
    }
    Router router=getRouter(ra);
    if(router!=null){
      router.addInterface(ipa);
      putPairRouter(ipa, router.getDPID());
      if(!rb.equals("")){
        //logger.debug("addneighbor"+ipa+" "+ipb);
        router.addNeighbor(ipb);
        putRouter(router);
        router=getRouter(rb);
        router.addInterface(ipb);
        router.addNeighbor(ipa);
        putRouter(router);
        putPairRouter(ipb, router.getDPID());
      }
     // routers.put(ra,router);
    }
  }

  public  void addRouter(String routerid, String dpid, int numinterfaces){
    if(getRouter(routerid)==null){
      routers.add(new Router(routerid,dpid,numinterfaces));
    }
    else{
      Router router=getRouter(routerid);
      router.updateInterfaceNum(numinterfaces);
    }
  }

  public void newRouter(String routerid, String dpid, int numInterfaces) {
    logger.debug("RoutingManager: new router "+routerid+ " "+dpid);
    addRouter(routerid, dpid, numInterfaces);
  }

  public void newLink(String ipa, String ra, String controller) {
    logger.debug("RoutingManager: new link "+ra+ipa);
    addLink(ipa,ra);
    String dpid= getRouter(ra).getDPID();
    String cmd = addrCMD(ipa,dpid,controller);
    Exec.exec(cmd);
    addEntry_HashList(sdncmds,dpid,cmd);
  }

  public void newLink(String ipa, String ra, String ipb, String rb, String controller){
    logger.debug("RoutingManager: new link "+ra+ipa+rb+ipb);
    addLink(ipa,ra,ipb,rb);
    String dpid=getDPID(ra);
    String cmd=addrCMD(ipa,dpid, controller);
    Exec.exec(cmd);
    addEntry_HashList(sdncmds,dpid,cmd);
    dpid=getDPID(rb);
    cmd=addrCMD(ipb,dpid, controller);
    Exec.exec(cmd);
    addEntry_HashList(sdncmds,dpid,cmd);
  }

  public void configurePath(String dest, String nodename, String gateway, String controller) {
    String gwdpid=getRouter(nodename).getDPID();
    if(gwdpid==null){
      logger.debug("No router named "+nodename+" not found");
      return;
    }
    ArrayList<String[]>paths=getBroadcastRoutes(gwdpid,gateway);
    for(String[] path: paths){
      String cmd=routingCMD(dest, path[1], path[0], controller);
      Exec.exec(cmd);
      addEntry_HashList(sdncmds,path[0],cmd);
      //logger.debug(path[0]+" "+path[1]);
    }
  }

  public void configurePath(String dest, String nodename,String targetIP,String targetnodename, String gateway, String controller) {
    String gwdpid=getRouter(nodename).getDPID();
    String targetdpid=getRouter(targetnodename).getDPID();
    if(gwdpid==null ||targetdpid==null){
      logger.debug("No router named "+nodename+" not found");
      return;
    }
    ArrayList<String[]>paths=getPairRoutes(gwdpid,targetdpid,gateway);
    for(String[] path: paths){
      String cmd=routingCMD(dest,targetIP, path[1], path[0], controller);
      Exec.exec(cmd);
      addEntry_HashList(sdncmds,path[0],cmd);
      //logger.debug(path[0]+" "+path[1]);
    }
  }
  public  Router getRouter(String routername){
    for (Router r: routers){
      if(r.getRouterID().equals(routername)){
        return r;
      }
    }
    return null;
  }

  public void replayCmds(String dpid){
    if(sdncmds.containsKey(dpid)){
      ArrayList<String> l=sdncmds.get(dpid);
      for(String cmd: l){
        logger.debug("Replay:"+cmd);
        Exec.exec(cmd);
      }
    }
    else{
      logger.debug("No cmd to replay");
    }
  }

  private void addEntry_HashList(HashMap<String,ArrayList<String>>  map,String key, String entry){
    if(map.containsKey(key)){
      ArrayList<String> l=map.get(key);
      l.add(entry);
    }
    else{
      ArrayList<String> l=new ArrayList<String>();
      l.add(entry);
      map.put(key,l);
    }
  }

  //TODO: get shortest path for two pairs
  private ArrayList<String[]> getPairRoutes(String srcdpid, String dstdpid, String gateway){
    logger.debug("shortest path");

    HashSet<String> knownrouters=new HashSet<String>();
    //path queue: [dpid, path]
    //format of path:Arraylist([router_id,gateway])
    ArrayList<String>queue=new ArrayList<String>();
    queue.add(srcdpid);
    knownrouters.add(srcdpid);
    //{router-id:gateway}
    ArrayList<String[]>knownpaths=new ArrayList<String[]>();
    String[] initroute=new String[2];
    initroute[0]=srcdpid;
    initroute[1]=gateway;
    knownpaths.add(initroute);
    int start=0;
    int end=0;
    while(start<=end){
      //logger.debug("queue[start]"+queue.get(start));
      String rid=queue.get(start);
      start+=1;
      Router router=getRouterByDPID(rid);
      if(router!=null){
        ArrayList<String> nips=getNeighborIPs(rid);
        for (String ip:nips){
          //logger.debug("neighborIP: "+ip);
          if(!knownrouters.contains(getPairRouter(ip))){
            knownrouters.add(getPairRouter(ip));
            queue.add(getPairRouter(ip));
            end+=1;
            String[]path=new String[3];
            path[0]=getPairRouter(ip);
            println(ip);
            path[1]=getPairIP(ip).split("/")[0];
            path[2]=getPairIP(ip);
            println(path[1]);
            knownpaths.add(path);
          }
        }
        if(rid.equals(dstdpid)){
          break;
        }
      }
      else{
        logger.debug("Router null");
      }
    }
    ArrayList<String[]> spaths=new ArrayList<String[]>();
    String[] curpath=knownpaths.get(knownpaths.size()-1);
    spaths.add(curpath);
    for(int i=knownpaths.size()-2;i>=0;i--){
      if(knownpaths.get(i)[0].equals(getPairRouter(curpath[2]))){
        spaths.add(knownpaths.get(i));
        curpath=knownpaths.get(i);
      }
    }
    return spaths;
  }

  private  ArrayList<String[]> getBroadcastRoutes(String gwdpid, String gateway){
    //logger.debug("All routers and links");
    //for(String[] link:links){
    //  //logger.debug(link[0]+" "+link[1]);
    //}
    //for(String[] link:ip_router){
    //  //logger.debug(link[0]+" "+link[1]);
    //}

    HashSet<String> knownrouters=new HashSet<String>();
    //path queue: [dpid, path]
    //format of path:Arraylist([router_id,gateway])
    ArrayList<String>queue=new ArrayList<String>();
    queue.add(gwdpid);
    knownrouters.add(gwdpid);
    //{router-id:gateway}
    ArrayList<String[]>knownpaths=new ArrayList<String[]>();
    String[] initroute=new String[2];
    initroute[0]=gwdpid;
    initroute[1]=gateway;
    knownpaths.add(initroute);
    int start=0;
    int end=0;
    while(start<=end){
      //logger.debug("queue[start]"+queue.get(start));
      String rid=queue.get(start);
      start+=1;
      Router router=getRouterByDPID(rid);
      if(router!=null){
        ArrayList<String> nips=getNeighborIPs(rid);
        for (String ip:nips){
          //logger.debug("neighborIP: "+ip);
          if(!knownrouters.contains(getPairRouter(ip))){
            knownrouters.add(getPairRouter(ip));
            queue.add(getPairRouter(ip));
            end+=1;
            String[]path=new String[2];
            path[0]=getPairRouter(ip);
            println(ip);
            path[1]=getPairIP(ip).split("/")[0];
            println(path[1]);
            knownpaths.add(path);
          }
        }
      }
      else{
        logger.debug("Router null");
      }
    }
    return knownpaths;
  }

  private  String addrCMD(String addr, String dpid, String controller){
    String cmd="curl -X POST -d {\"address\":\""+addr+"\"} "+controller+"/router/"+dpid;
    return cmd;
  }

  private  String routingCMD(String dst,String gw, String dpid, String controller){
    String cmd="curl -X POST -d {\"destination\":\""+dst+"\",\"gateway\":\""+gw+"\"} "+controller+"/router/"+dpid;
    return cmd;
  }

  private  String routingCMD(String dst,String src,String gw, String dpid, String controller){
    String cmd="curl -X POST -d {\"destination\":\""+dst+"\",\"source\":\""+src+"\",\"gateway\":\""+gw+"\"} "+controller+"/router/"+dpid;
    return cmd;
  }

  private  Router getRouterByDPID(String routername){
    for (Router r: routers){
      if(r.getDPID().equals(routername)){
        return r;
      }
    }
    return null;
  }

  private  void putRouter(Router router){
    for(int i=0;i<routers.size();i++){
      if(routers.get(i).getDPID().equals(router.getDPID())){
        routers.set(i,router);
        return;
      }
    }
    routers.add(router);
  }

  private  void putLink(String linka, String linkb){
    for(String[] link:links){
      if(link[0].equals(linka) && link[1].equals(linkb)){
        return;
      }
    }
    String[] link=new String[2];
    link[0]=linka;
    link[1]=linkb;
    links.add(link);
  }

  private  String getPairIP(String ip){
    for (String[] link: links){
      if(link[0].equals(ip))
        return link[1];
    }
    return null;
  }
  private  String getPairRouter(String ip){
    for (String[] link: ip_router){
      //logger.debug(link[0]+" "+link[1]);
      if(link[0].equals(ip))
        return link[1];
    }
    return null;
  }

  private  void putPairRouter(String ip,String dpid){
    if(getPairRouter(ip)==null){
      String[] iprouter=new String[2];
      iprouter[0]=ip;
      iprouter[1]=dpid;
      ip_router.add(iprouter);
    }
  }

  private  void println(String out){
    logger.debug(out);
  }
}


class RLink{
  private String ifa="";
  private String ifb="";
  private String ra="";
  private String rb="";
  public RLink(String ia, String ib, String routera, String routerb){
    ifa=ia;
    ifb=ib;
    ra=routera;
    rb=routerb;
  }

  public String pair_ip(String ip){
    if(ip.equals(ifa))
      return ifb;
    else if(ip.equals(ifb))
      return ifa;
    else
      return "";
  }

  public boolean equals(RLink link){
    if(ifa==link.ifa && ifb==link.ifb  || ifa==link.ifb && ifb==link.ifa){
        if(ra==link.ra && rb==link.rb  || ra==link.rb && rb==link.ra){
            return true;
        }
    }
    return false;
  }
}

