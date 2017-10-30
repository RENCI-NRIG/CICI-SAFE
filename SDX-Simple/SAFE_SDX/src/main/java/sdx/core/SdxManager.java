package sdx.core;

import sdx.networkmanager.Link;
import sdx.networkmanager.Router;
import sdx.networkmanager.NetworkManager;
import sdx.utils.Exec;
import sdx.utils.SafePost;

import java.lang.reflect.Array;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URL;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.SimpleLayout;
import org.apache.commons.cli.*;
import org.apache.commons.cli.DefaultParser;

import org.renci.ahab.libndl.LIBNDL;
import org.renci.ahab.libndl.Slice;
import org.renci.ahab.libndl.SliceGraph;
import org.renci.ahab.libndl.extras.PriorityNetwork;
import org.renci.ahab.libndl.resources.common.ModelResource;
import org.renci.ahab.libndl.resources.request.BroadcastNetwork;
import org.renci.ahab.libndl.resources.request.ComputeNode;
import org.renci.ahab.libndl.resources.request.Interface;
import org.renci.ahab.libndl.resources.request.InterfaceNode2Net;
import org.renci.ahab.libndl.resources.request.Network;
import org.renci.ahab.libndl.resources.request.Node;
import org.renci.ahab.libndl.resources.request.StitchPort;
import org.renci.ahab.libndl.resources.request.StorageNode;
import org.renci.ahab.libtransport.ISliceTransportAPIv1;
import org.renci.ahab.libtransport.ITransportProxyFactory;
import org.renci.ahab.libtransport.JKSTransportContext;
import org.renci.ahab.libtransport.PEMTransportContext;
import org.renci.ahab.libtransport.SSHAccessToken;
import org.renci.ahab.libtransport.SliceAccessContext;
import org.renci.ahab.libtransport.TransportContext;
import org.renci.ahab.libtransport.util.ContextTransportException;
import org.renci.ahab.libtransport.util.SSHAccessTokenFileFactory;
import org.renci.ahab.libtransport.util.TransportException;
import org.renci.ahab.libtransport.util.UtilTransportException;
import org.renci.ahab.libtransport.xmlrpc.XMLRPCProxyFactory;
import org.renci.ahab.libtransport.xmlrpc.XMLRPCTransportException;
import org.renci.ahab.ndllib.transport.OrcaSMXMLRPCProxy;

import java.net.MalformedURLException;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;

/*

 * @author geni-orca
 * @author Yuanjun Yao, yjyao@cs.duke.edu
 * This is the server for carrier slice. It's run by the carrier_slice owner to do the following things
 * 1. Load carriers slice information from exogeniSM, compute the topology
 * 2. Public: Take stitch request from customer slice
 *    Input: Request(carrier_slicename, nodename/sitename,customer_sliceauth_information)
 *    Output: yes or no
 *    Question: Shall carrier slice perform the stitching directly
 * 3. Private: Authorize stitching request:
 *    Call SAFE to authorize the request
 *
 * 4. Private Perform slice stitch
 *    Create a link for stitching
 *
 * 5. Get the connectivity list from SAFE
 *
 * 6. Call SDN controller to install the rules
 */

public class SdxManager extends SliceCommon{
  public SdxManager(){}

  final static Logger logger = Logger.getLogger(SdxManager.class);

  
  private static NetworkManager routingmanager=new NetworkManager();
  private static HashMap<String, Link> links=new HashMap<String, Link>();
  private static HashMap<String, ArrayList<String>>computenodes=new HashMap<String,ArrayList<String>>();
  private static ArrayList<StitchPort>stitchports=new ArrayList<>();
  private static String IPPrefix="192.168.";
  static int curip=128;
  private static String mask="/24";
  private static String SDNController;
  private static String OVSController;
  public static String serverurl;
  private static final ReentrantLock iplock=new ReentrantLock();
  private static final ReentrantLock nodelock=new ReentrantLock();
  private static final ReentrantLock linklock=new ReentrantLock();
  //private static String type;
  private static ArrayList<String[]> advertisements=new ArrayList<String[]>();
  private static HashSet<Integer> usedip=new HashSet<Integer>();

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

  private ArrayList<String[]> getAllElments_HashList(HashMap<String,ArrayList<String>>  map){
    ArrayList<String[]> res=new ArrayList<String[]>();
    for(String key:map.keySet()){
        for(String ip:map.get(key)){
          String[] pair=new String[2];
          pair[0]=key;
          pair[1]=ip;
          res.add(pair);
        }
    }
    return res;
  }

  private static  void computeIP(String prefix){
    String[] ip_mask=prefix.split("/");
    String[] ip_segs=ip_mask[0].split("\\.");
    IPPrefix=ip_segs[0]+"."+ip_segs[1]+".";
    curip=Integer.valueOf(ip_segs[2]);
  }
	
	public static void startSdxServer(String [] args){

		logger.debug("Carrier Slice server with Service API: START");
    CommandLine cmd=parseCmd(args);
    if(cmd.hasOption('n')){
      safeauth=false;
      System.out.println("Safe disabled, allowing all requests");
    }
    else{
      safeauth=true;
    }
		String configfilepath=cmd.getOptionValue("config");
    readConfig(configfilepath);
    IPPrefix=conf.getString("config.ipprefix");
    serverurl=conf.getString("config.serverurl");

    //type=sdxconfig.type;
    computeIP(IPPrefix);
    //System.out.print(pemLocation);
		sliceProxy = getSliceProxy(pemLocation,keyLocation, controllerUrl);
		//SSH context
		sctx = new SliceAccessContext<>();
		try {
			SSHAccessTokenFileFactory fac;
			fac = new SSHAccessTokenFileFactory(sshkey+".pub", false);
			SSHAccessToken t = fac.getPopulatedToken();
			sctx.addToken("root", "root", t);
			sctx.addToken("root", t);
		} catch (UtilTransportException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    Slice serverslice = null;
    try {
      serverslice = Slice.loadManifestFile(sliceProxy, sliceName);
      ComputeNode safe=(ComputeNode)serverslice.getResourceByName("safe-server");
      //System.out.println("safe-server managementIP = " + safe.getManagementIP());
      safeserver=safe.getManagementIP()+":7777";
    } catch (ContextTransportException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (TransportException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    //SDNControllerIP="152.3.136.36";
    SDNControllerIP=((ComputeNode)serverslice.getResourceByName("plexuscontroller")).getManagementIP();
    //System.out.println("plexuscontroler managementIP = " + SDNControllerIP);
    SDNController=SDNControllerIP+":8080";
    OVSController=SDNControllerIP+":6633";

    //configRouting(serverslice,OVSController,SDNController,"(c\\d+)","(sp-c\\d+.*)");
    loadSdxNetwork(serverslice,"(c\\d+)","(sp-c\\d+.*)");
    configRouting(serverslice,OVSController,SDNController,"(c\\d+)","(sp-c\\d+.*)");
	}


	private static String allocateLinkName(){
	  for(int i=0;;i++){
	    if(!links.containsKey("link"+i)){
	      return "link"+i;
      }
    }
  }

	public static String connectionRequest(String site1, String site2){
	  Slice s=getSlice();
	  String n1=computenodes.get(site1).get(0);
	  String n2=computenodes.get(site2).get(0);
	  ComputeNode node1=(ComputeNode)s.getResourceByName(n1);
	  ComputeNode node2=(ComputeNode)s.getResourceByName(n2);
	  String linkname;
	  linklock.lock();
	  try {
      linkname = allocateLinkName();
      Link link = new Link();
      link.setName(linkname);
      links.put(linkname, link);
    }finally {
      linklock.unlock();
    }
    logger.debug("Add link: " +linkname);
    Network net=s.addBroadcastLink(linkname);
    InterfaceNode2Net ifaceNode0 = (InterfaceNode2Net) net.stitch(node1);
    ifaceNode0.setIpAddress("192.168.1.1");
    ifaceNode0.setNetmask("255.255.255.0");
    InterfaceNode2Net ifaceNode1 = (InterfaceNode2Net) net.stitch(node2);
    ifaceNode1.setIpAddress("192.168.1.2");
    ifaceNode1.setNetmask("255.255.255.0");
    try {
      s.commit();
    } catch (XMLRPCTransportException e1) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
    }
    waitTillActive(s,new ArrayList<String>(Arrays.asList(linkname)));
    //add link to links
	  return "link added";
  }

  public static String notifyPrefix(String dest, String gateway, String router,String customer_keyhash){
    logger.debug("received notification for ip prefix "+dest);
    String res="received notification for "+dest;
    if(!safeauth || authorizePrefix(customer_keyhash,dest)){
      if(safeauth) {
        res = res + " [authorization success]";
      }
      boolean flag=false;
      for(String[]pair:advertisements){
        if(pair[0].equals(customer_keyhash)&&pair[1].equals(dest)){
          flag=true;
          pair[2]=gateway;
          pair[3]=router;
          continue;
        }
        if(!safeauth || authorizeConnectivity(pair[0],pair[1],customer_keyhash,dest)){
    //      res.add(pair[1]);
          System.out.println("Connection between "+pair[0]+" and "+pair[1]+" allowed");
          routingmanager.configurePath(dest,router,pair[1],pair[3],gateway,SDNController);
          routingmanager.configurePath(pair[1],pair[3],dest,router,pair[2],SDNController);
        }
      }
      if(!flag){
        String[] newpair=new String[4];
        newpair[0]=customer_keyhash;
        newpair[1]=dest;
        newpair[2]=gateway;
        newpair[3]=router;
        advertisements.add(newpair);
      }
    }
    else{
      res=res+" [authorization failed]";
    }
    return res;
  }

  private static boolean authorizePrefix(String cushash, String cusip){
    String[] othervalues=new String[2];
    othervalues[0]=cushash;
    othervalues[1]=cusip;
    String message=SafePost.postSafeStatements(safeserver,"ownPrefix",keyhash,othervalues);
    if(message !=null && message.contains("Unsatisfied")){
      return false;
    }
    else
      return true;
  }


  private static boolean authorizeConnectivity(String srchash, String srcip, String dsthash, String dstip){
    String[] othervalues=new String[4];
    othervalues[0]=srchash;
    othervalues[1]=dsthash;
    othervalues[2]=srcip;
    othervalues[3]=dstip;
    String message=SafePost.postSafeStatements(safeserver,"connectivity",keyhash,othervalues);
    if(message !=null && message.contains("Unsatisfied")){
      return false;
    }
    else
      return true;
  }

  public static String stitchChameleon(String sdxslice,String nodeName, String customer_keyhash,String stitchport,String vlan, String gateway, String ip) {
    String res="Stitch request unauthorized";
    try {
      if (!safeauth || authorizeStitchChameleon(customer_keyhash, stitchport, vlan, gateway, sdxslice, nodeName)) {
        //FIX ME: do stitching
        System.out.println("Chameleon Stitch Request from " + customer_keyhash + " Authorized");
        Slice s = null;
        ISliceTransportAPIv1 sliceProxy = getSliceProxy(pemLocation, keyLocation, controllerUrl);
        try {
          s = Slice.loadManifestFile(sliceProxy, sdxslice);
        } catch (ContextTransportException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        } catch (TransportException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
        String stitchname = "sp-" + nodeName + "-" + ip.replace("/", "__").replace(".", "_");
        System.out.println("Stitching to Chameleon {"+"stitchname: " + stitchname + " vlan:" + vlan + " stithport: " + stitchport+"}");
        StitchPort mysp = s.addStitchPort(stitchname, vlan, stitchport, 100000000l);
        ComputeNode mynode = (ComputeNode) s.getResourceByName(nodeName);
        mysp.stitch(mynode);
        s.commit();
        waitTillActive(s);
        routingmanager.newLink(ip, nodeName, SDNController);
        res="Stitch operation Completed";
        System.out.println(res);
      } else {
        System.out.println("Chameleon Stitch Request from " + customer_keyhash + " Unauthorized");
      }
    }catch(Exception e){
      e.printStackTrace();
    }
    return res;
  }

  public static String[] stitchRequest(String sdxslice, String site, String customer_slice,String customerName, String ResrvID,String secret) {
    logger.debug("new stitch request from "+customerName+" for "+sdxslice +" at "+site);
    System.out.println("new stitch request for "+sdxslice +" at "+site);
    String[] res=new String[2];
    res[0]=null;
    res[1]=null;
    if(!safeauth || authorizeStitchRequest(customer_slice,customerName,ResrvID, keyhash,sdxslice, site)){
      if(safeauth){
        System.out.println("Authorized: stitch request for"+sdxslice +" and "+site);
      }
      Slice s1 = null;
      ISliceTransportAPIv1 sliceProxy = getSliceProxy(pemLocation,keyLocation, controllerUrl);		
      try {
        s1 = Slice.loadManifestFile(sliceProxy, sdxslice);
      } catch (ContextTransportException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } catch (TransportException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      ComputeNode node=null;
      if(computenodes.containsKey(site)&&computenodes.get(site).size()>0) {
        node=(ComputeNode)s1.getResourceByName( computenodes.get(site).get(0));
      }else{
        //if node not exists, add another node to the slice
        //add a node and configure it as a router.
        //later when a customer requests connection between site a and site b, we add another link to meet the requirments
        logger.debug("No existing router at requested site, adding new router");
        int max=-1;
        nodelock.lock();
        try {
          for (String key : computenodes.keySet()) {
            for (String cname : computenodes.get(key)) {
              int number = Integer.valueOf(cname.replace("c", ""));
              max = Math.max(max, number);
            }
          }
          ArrayList<String> l = new ArrayList<>();
          String routername="c"+(max+1);
          l.add(routername);
          logger.debug("Name of new router: "+routername);
          computenodes.put(site, l);
        }finally {
          nodelock.unlock();
        }
        SliceManager.addOVSRouter(s1,site,"c"+(max+1));
        node = (ComputeNode) s1.getResourceByName("c" + (max + 1));
        configRouter(node);
        logger.debug("Configured the new router in RoutingManager");
      }
      int interfaceNum=routingmanager.getRouter(node.getName()).getInterfaceNum();
      int ip_to_use=0;
      iplock.lock();
      String stitchname;
      while(usedip.contains(curip))curip++;
      try{
        stitchname="stitch_"+node.getName()+"_"+curip;
        ip_to_use=curip;
        curip++;
      }finally{
        iplock.unlock();
      }
      Network net=s1.addBroadcastLink(stitchname);
      InterfaceNode2Net ifaceNode0 = (InterfaceNode2Net) net.stitch(node);
      ifaceNode0.setIpAddress("192.168.1.1");
      ifaceNode0.setNetmask("255.255.255.0");
      try {
        s1.commit();
      } catch (XMLRPCTransportException e1) {
        // TODO Auto-generated catch block
        e1.printStackTrace();

      }
      int N=0;
      waitTillActive(s1,Arrays.asList(stitchname));
      sleep(10);
      //System.out.println("Node managmentIP: " + node.getManagementIP());
      Exec.sshExec("root",node.getManagementIP(),"/bin/bash ~/ovsbridge.sh "+OVSController,sshkey);
      routingmanager.replayCmds(routingmanager.getDPID(node.getName()));
      Exec.sshExec("root",node.getManagementIP(),"ifconfig;ovs-vsctl list port",sshkey);
      s1.refresh();
      net=(BroadcastNetwork)s1.getResourceByName(stitchname);
      String net1_stitching_GUID = net.getStitchingGUID();
      logger.debug("net1_stitching_GUID: " + net1_stitching_GUID);
      Link link=new Link();
      link.setName(stitchname);
      link.addNode(node.getName());
      link.setIP(IPPrefix+String.valueOf(ip_to_use));
      link.setMask(mask);
      links.put(stitchname,link);
      routingmanager.newLink(link.getIP(1), link.nodea, SDNController);
      String gw = link.getIP(1);
      String ip=link.getIP(2);
      stitch(customerName,ResrvID,sdxslice,net1_stitching_GUID,secret,ip);
      res[0]=gw;
      res[1]=ip;
      //routingmanager.configurePath(ip,node.getName(),ip.split("/")[0],SDNController);
      System.out.println("stitching operation  completed");
    }
    else{
      System.out.println("Unauthorized: stitch request for"+sdxslice +" at "+site);
      logger.debug("Stitching Authorization Failed");
    }
    return res;
  }

	public static void stitch(String sdxslice, String RID,String customerName, String CID,String secret,String newip){
		logger.debug("ndllib TestDriver: START");
		//Main Example Code
    Long t1 = System.currentTimeMillis();
		try {
			//s2
			Properties p = new Properties();
			p.setProperty("ip", newip);
			sliceProxy.performSliceStitch(customerName, CID, sdxslice, RID, secret, p);
		} catch (TransportException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    Long t2 = System.currentTimeMillis();
    logger.debug("Finished Stitching, set ip address of the new interface to "+newip+"  time elapsed: "+String.valueOf(t2-t1)+"\n");
    logger.debug("finished sending reconfiguration command");
	}

  public static boolean authorizeStitchRequest(String customer_slice,String customerName,String ReservID,String keyhash,String slicename, String nodename){
		/** Post to remote safesets using apache httpclient */
    String[] othervalues=new String[5];
    othervalues[0]=customer_slice;
    othervalues[1]=customerName;
    othervalues[2]=ReservID;
    othervalues[3]=slicename;
    othervalues[4]=nodename;
    String message=SafePost.postSafeStatements(safeserver,"verifyStitch",keyhash,othervalues);
    if(message ==null || message.contains("Unsatisfied")){
      return false;
    }
    else
      return true;
  }

  public static boolean authorizeStitchChameleon(String customer_keyhash,String stitchport,String vlan,String gateway,String slicename, String nodename){
		/** Post to remote safesets using apache httpclient */
    String[] othervalues=new String[6];
    othervalues[0]=customer_keyhash;
    othervalues[1]=stitchport;
    othervalues[2]=vlan;
    othervalues[3]=gateway;
    othervalues[4]=slicename;
    othervalues[5]=nodename;

    String message=SafePost.postSafeStatements(safeserver,"verifyChameleonStitch",keyhash,othervalues);
    if(message ==null || message.contains("Unsatisfied")){
      return false;
    }
    else
      return true;
  }


  private static void restartPlexus(String plexusip){
    logger.debug("Restarting Plexus Controller");
    String script="docker exec -d plexus /bin/bash -c  \"cd /root;pkill ryu-manager;ryu-manager plexus/plexus/app.py ryu/ryu/app/rest_conf_switch.py ryu/ryu/app/rest_qos.py |tee log\"\n";
    logger.debug(sshkey);
    logger.debug(plexusip);
    Exec.sshExec("root",plexusip,script,sshkey);
  }

  public static void loadSdxNetwork(Slice s, String routerpattern, String stitchportpattern){
    logger.debug("Loading Sdx Network Topology");
    try{
      Pattern pattern = Pattern.compile(routerpattern);
      Pattern stitchpattern = Pattern.compile(stitchportpattern);
      //Nodes: Get all router information
      for(ComputeNode node : s.getComputeNodes()){
        Matcher matcher = pattern.matcher(node.getName());
        if (!matcher.find())
        {
          continue;
        }
        if(computenodes.containsKey(node.getDomain())) {
          computenodes.get(node.getDomain()).add(node.getName());
        }
        else{
          ArrayList<String> l=new ArrayList<>();
          l.add(node.getName());
          computenodes.put(node.getDomain(),l);
        }
      }
      logger.debug("setting up links");
      HashSet<Integer> usedip=new HashSet<Integer>();
      HashSet<String> ifs=new HashSet<String>();
      // get all links, and then
      for(Interface i: s.getInterfaces()){
        InterfaceNode2Net inode2net=(InterfaceNode2Net)i;
        logger.debug("linkname: "+inode2net.getLink().toString()+" bandwidth: "+ inode2net.getLink().getBandwidth());
        if(ifs.contains(i.getName()) || !computenodes.containsKey(inode2net.getNode().toString())|| computenodes.get(inode2net.getNode().toString()).size()==0){
          continue;
        }
        ifs.add(i.getName());
        Link link=links.get(inode2net.getLink().toString());

        if(link==null){
          link=new Link();
          link.setName(inode2net.getLink().toString());
          link.addNode(inode2net.getNode().toString());
          if(link.linkname.contains("stitch")){
            String[] parts=link.linkname.split("_");
            String ip=parts[2];
            link.setIP(IPPrefix+ip);
            link.setMask(mask);
            usedip.add(Integer.valueOf(parts[2]));
          }
        }
        else{
          link.addNode(inode2net.getNode().toString());
        }
        links.put(inode2net.getLink().toString(),link);
        //logger.debug(inode2net.getNode()+" "+inode2net.getLink());
      }
      //Stitchports
      logger.debug("setting up sttichports");
      for(StitchPort sp : s.getStitchPorts()){
        System.out.println(sp.getName());
        Matcher matcher = stitchpattern.matcher(sp.getName());
        if (!matcher.find())
        {
          continue;
        }
        stitchports.add(sp);
      }

    }catch(Exception e){
      e.printStackTrace();
    }
  }

  private static void configRouter(ComputeNode node){
    String mip = node.getManagementIP();
    logger.debug(node.getName() + " " + mip);
    Exec.sshExec("root", mip, "/bin/bash ~/ovsbridge.sh " + OVSController, sshkey).split(" ");
    String[] result = Exec.sshExec("root", mip, "/bin/bash ~/dpid.sh", sshkey).split(" ");

    result[1] = result[1].replace("\n", "");
    logger.debug("Get router info " + result[0] + " " + result[1]);
    routingmanager.newRouter(node.getName(), result[1], Integer.valueOf(result[0]), mip);
  }
  public static void configRouting1(Slice s,String ovscontroller, String httpcontroller, String routerpattern,String stitchportpattern) {
    logger.debug("Configurating Routing");
    restartPlexus(SDNControllerIP);
    sleep(5);
    // run ovsbridge scritps to add the all interfaces to the ovsbridge br0, if new interface is added to the ovs bridge, then we reset the controller?
    // FIXME: maybe this is not the best way to do.
    //add all interfaces other than eth0 to ovs bridge br0
    runCmdSlice(s, "/bin/bash ~/ovsbridge.sh " + ovscontroller, sshkey, "(c\\d+)", false, true);
    try {
      for (String k : computenodes.keySet()) {
        for (String cname : computenodes.get(k)) {
          //System.out.println("mip node managment: " + node.getManagementIP());
          ComputeNode node=(ComputeNode) s.getResourceByName(cname);
          String mip = node.getManagementIP();
          logger.debug(node.getName() + " " + mip);
          Exec.sshExec("root", mip, "/bin/bash ~/ovsbridge.sh " + ovscontroller, sshkey).split(" ");
          String[] result = Exec.sshExec("root", mip, "/bin/bash ~/dpid.sh", sshkey).split(" ");
          result[1] = result[1].replace("\n", "");
          logger.debug("Get router info " + result[0] + " " + result[1]);
          routingmanager.newRouter(node.getName(), result[1], Integer.valueOf(result[0]), mip);
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    logger.debug("Wait until all ovs bridges have connected to SDN controller");
    ArrayList<Thread> tlist = new ArrayList<Thread>();
    for (String k : computenodes.keySet()) {
      for (final String cname : computenodes.get(k)) {
        final ComputeNode node=(ComputeNode) s.getResourceByName(cname);
        final String mip = node.getManagementIP();
        try {
          //      logger.debug(mip+" run commands:"+cmd);
          //      //ScpTo.Scp(lfile,"root",mip,rfile,privkey);
          Thread thread = new Thread() {
            @Override
            public void run() {
              try {
                String cmd = "ovs-vsctl show";
                logger.debug(mip + " run commands:" + cmd);
                String res = Exec.sshExec("root", mip, "ovs-vsctl show", sshkey);
                while (!res.contains("is_connected: true")) {
                  sleep(5);
                  res = Exec.sshExec("root", mip, cmd, sshkey);
                }
                logger.debug(node.getName() + " connected");
              } catch (Exception e) {
                e.printStackTrace();
              }
            }
          };
          thread.start();
          tlist.add(thread);
        } catch (Exception e) {
          System.out.println("exception when copying config file");
          logger.error("exception when copying config file");
        }
      }
    }
    try {
      for (Thread t : tlist) {
        t.join();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

    logger.debug("setting up sttichports");
    HashSet<Integer> usedip=new HashSet<Integer>();
    HashSet<String> ifs=new HashSet<String>();
    for (StitchPort sp : stitchports) {
      String[] parts = sp.getName().split("-");
      String ip = parts[2].replace("_", ".").replace("__", "/");
      String nodeName = parts[1];
      routingmanager.newLink(ip, nodeName, SDNController);
    }

    Set keyset = links.keySet();
    //logger.debug(keyset);

    for (Object k : keyset) {
      Link link = links.get((String) k);
      if (!((String) k).contains("stitch")) {
        while (usedip.contains(curip)) {
          curip++;
        }
        link.setIP(IPPrefix + String.valueOf(curip));
        link.setMask(mask);
        curip++;
      }
      String param = "";
      if (link.nodeb != "") {
        //logger.debug(link.nodea+":"+link.getIP(1)+" "+link.nodeb+":"+link.getIP(2));
        routingmanager.newLink(link.getIP(1), link.nodea, link.getIP(2), link.nodeb, httpcontroller);
      } else {
        //logger.debug(link.nodea+" gateway address:"+link.getIP(1));
        routingmanager.newLink(link.getIP(1), link.nodea, httpcontroller);
      }
    }

    //set ovsdb address
    routingmanager.setOvsdbAddr(httpcontroller);
  }


  public static void configRouting(Slice s,String ovscontroller, String httpcontroller, String routerpattern,String stitchportpattern){
    logger.debug("Configurating Routing");
    restartPlexus(SDNControllerIP);
    sleep(5);
    // run ovsbridge scritps to add the all interfaces to the ovsbridge br0, if new interface is added to the ovs bridge, then we reset the controller?
    // FIXME: maybe this is not the best way to do.
    //add all interfaces other than eth0 to ovs bridge br0
    runCmdSlice(s,"/bin/bash ~/ovsbridge.sh "+ovscontroller,sshkey,"(c\\d+)",false,true);
    try{
      Pattern pattern = Pattern.compile(routerpattern);
      Pattern stitchpattern = Pattern.compile(stitchportpattern);
      //Nodes: Get all router information
      for(ComputeNode node : s.getComputeNodes()){
        Matcher matcher = pattern.matcher(node.getName());
        if (!matcher.find())
        {
          continue;
        }
        //System.out.println("mip node managment: " + node.getManagementIP());
        String mip= node.getManagementIP();
        logger.debug(node.getName()+" "+mip);
        Exec.sshExec("root",mip,"/bin/bash ~/ovsbridge.sh "+ ovscontroller,sshkey).split(" ");
        String[] result=Exec.sshExec("root",mip,"/bin/bash ~/dpid.sh",sshkey).split(" ");
        try{
          result[1]=result[1].replace("\n","");
          logger.debug("Get router info "+result[0]+" "+result[1]);
          routingmanager.newRouter(node.getName(),result[1],Integer.valueOf(result[0]),mip);
        }catch(Exception e){
          e.printStackTrace();
        }
      }
      logger.debug("setting up links");
      HashSet<String> ifs=new HashSet<String>();
      // get all links, and then
      Collection<BroadcastNetwork> blinks=s.getBroadcastLinks();
      Collection<Interface> intfs=s.getInterfaces();
      Collection<ModelResource> ress=s.getAllResources();
      for(Interface i: s.getInterfaces()){
        InterfaceNode2Net inode2net=(InterfaceNode2Net)i;
        logger.debug("linkname: "+inode2net.getLink().toString()+" bandwidth: "+ inode2net.getLink().getBandwidth());
        if(ifs.contains(i.getName()) || routingmanager.getRouter(inode2net.getNode().toString())==null){
          continue;
        }
        ifs.add(i.getName());
        Link link=links.get(inode2net.getLink().toString());

        if(link==null){
          link=new Link();
          link.setName(inode2net.getLink().toString());
          link.addNode(inode2net.getNode().toString());
          if(link.linkname.contains("stitch")){
            String[] parts=link.linkname.split("_");
            String ip=parts[2];
            link.setIP(IPPrefix+ip);
            link.setMask(mask);
            usedip.add(Integer.valueOf(parts[2]));
          }
        }
        else{
          link.addNode(inode2net.getNode().toString());
        }
        links.put(inode2net.getLink().toString(),link);
        //logger.debug(inode2net.getNode()+" "+inode2net.getLink());
      }
      //Stitchports
      logger.debug("setting up sttichports");
      for(StitchPort sp : s.getStitchPorts()){
        System.out.println(sp.getName());
        Matcher matcher = stitchpattern.matcher(sp.getName());
        if (!matcher.find())
        {
          continue;
        }
        String[] parts=sp.getName().split("-");
        String ip=parts[2].replace("_",".").replace("__","/");
        String nodeName=parts[1];
        routingmanager.newLink(ip, nodeName, SDNController);
      }

      Set keyset=links.keySet();
      //logger.debug(keyset);
      logger.debug("Wait until all ovs bridges have connected to SDN controller");
      ArrayList<Thread> tlist=new ArrayList<Thread>();
      for(final ComputeNode node : s.getComputeNodes()){
        Matcher matcher = pattern.matcher(node.getName());
        if(matcher.matches()){
          final String mip=node.getManagementIP();
          try{
      //      logger.debug(mip+" run commands:"+cmd);
      //      //ScpTo.Scp(lfile,"root",mip,rfile,privkey);
            Thread thread=new Thread(){
              @Override public void run(){
                try{
                  String cmd="ovs-vsctl show";
                  logger.debug(mip+" run commands:"+cmd);
                  String res=Exec.sshExec("root",mip,"ovs-vsctl show",sshkey);
                  while(!res.contains("is_connected: true")){
                    sleep(5);
                    res=Exec.sshExec("root",mip,cmd,sshkey);
                  }
                  logger.debug(node.getName() +" connected");
                }catch(Exception e){
                  e.printStackTrace();
                }
              }
            };
            thread.start();
            tlist.add(thread);
          }catch (Exception e){
            System.out.println("exception when copying config file");
            logger.error("exception when copying config file");
          }
        }
      }
      try{
        for(Thread t:tlist){
          t.join();
        }
      }catch (Exception e){
        e.printStackTrace();
      }


      for(Object k: keyset){
        Link link=links.get((String)k);
        if(!((String)k).contains("stitch")){
          while(usedip.contains(curip)){
            curip++;
          }
          link.setIP(IPPrefix+String.valueOf(curip));
          link.setMask(mask);
          curip++;
        }
        String param="";
        if(link.nodeb!=""){
          //logger.debug(link.nodea+":"+link.getIP(1)+" "+link.nodeb+":"+link.getIP(2));
          routingmanager.newLink(link.getIP(1), link.nodea, link.getIP(2), link.nodeb, httpcontroller);
        }
        else{
          //logger.debug(link.nodea+" gateway address:"+link.getIP(1));
          routingmanager.newLink(link.getIP(1), link.nodea, httpcontroller);
        }
      }

      //set ovsdb address
      routingmanager.setOvsdbAddr(httpcontroller);
    }catch(Exception e){
      e.printStackTrace();
    }
  }

	

	public static void undoStitch(String sdxslice, String customerName, String netName, String nodeName){
		logger.debug("ndllib TestDriver: START");
		
		//Main Example Code
		
		Slice s1 = null;
		Slice s2 = null;
		
		try {
			s1 = Slice.loadManifestFile(sliceProxy, sdxslice);
			s2 = Slice.loadManifestFile(sliceProxy, customerName);
		} catch (ContextTransportException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (TransportException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
    }
				
		Network net1 = (Network) s1.getResourceByName(netName);
		String net1_stitching_GUID = net1.getStitchingGUID();
		
		ComputeNode node0_s2 = (ComputeNode) s2.getResourceByName(nodeName);
		String node0_s2_stitching_GUID = node0_s2.getStitchingGUID();
		
		logger.debug("net1_stitching_GUID: " + net1_stitching_GUID);
		logger.debug("node0_s2_stitching_GUID: " + node0_s2_stitching_GUID);
    Long t1 = System.currentTimeMillis();
			
		try {
			//s1
			//sliceProxy.permitSliceStitch(sdxslice, net1_stitching_GUID, "stitchSecret");
			//s2
			sliceProxy.undoSliceStitch(customerName, node0_s2_stitching_GUID, sdxslice, net1_stitching_GUID);
		} catch (TransportException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    Long t2 = System.currentTimeMillis();
    logger.debug("Finished UnStitching, time elapsed: "+String.valueOf(t2-t1)+"\n");
	}
}

