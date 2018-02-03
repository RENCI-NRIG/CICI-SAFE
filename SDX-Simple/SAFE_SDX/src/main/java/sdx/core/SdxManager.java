package sdx.core;

import sdx.networkmanager.NetworkManager;
import sdx.networkmanager.Link;
import sdx.utils.Exec;
import sdx.utils.SafePost;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.log4j.Logger;
import org.apache.commons.cli.*;

import org.renci.ahab.libndl.Slice;
import org.renci.ahab.libndl.resources.common.ModelResource;
import org.renci.ahab.libndl.resources.request.BroadcastNetwork;
import org.renci.ahab.libndl.resources.request.ComputeNode;
import org.renci.ahab.libndl.resources.request.Interface;
import org.renci.ahab.libndl.resources.request.InterfaceNode2Net;
import org.renci.ahab.libndl.resources.request.Network;
import org.renci.ahab.libndl.resources.request.StitchPort;
import org.renci.ahab.libtransport.ISliceTransportAPIv1;
import org.renci.ahab.libtransport.SSHAccessToken;
import org.renci.ahab.libtransport.SliceAccessContext;
import org.renci.ahab.libtransport.util.ContextTransportException;
import org.renci.ahab.libtransport.util.SSHAccessTokenFileFactory;
import org.renci.ahab.libtransport.util.TransportException;
import org.renci.ahab.libtransport.util.UtilTransportException;
import org.renci.ahab.libtransport.xmlrpc.XMLRPCTransportException;
import org.renci.ahab.ndllib.transport.OrcaSMXMLRPCProxy;

import java.net.MalformedURLException;

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

public class SdxManager extends SliceManager {
  public SdxManager() {
  }

  final Logger logger = Logger.getLogger(SdxManager.class);


  private NetworkManager routingmanager = new NetworkManager();
  private String IPPrefix = "192.168.";
  int curip = 128;
  private String mask = "/24";
  private String SDNController;
  private Slice serverSlice = null;
  private String OVSController;
  public String serverurl;
  private HashSet<Integer> usedip = new HashSet<Integer>();
  private final ReentrantLock iplock=new ReentrantLock();
  private  final ReentrantLock nodelock=new ReentrantLock();
  private  final ReentrantLock linklock=new ReentrantLock();
  private  HashMap<String,String>prefixgateway=new HashMap<String,String>();
  //private static String type;
  private  ArrayList<String[]> advertisements=new ArrayList<String[]>();

  public String getSDNControllerIP(){
    return SDNControllerIP;
  }

  public  void replayCMD(String dpid){
    routingmanager.replayCmds(dpid);
  }

  private void addEntry_HashList(HashMap<String, ArrayList<String>> map, String key, String entry) {
    if (map.containsKey(key)) {
      ArrayList<String> l = map.get(key);
      l.add(entry);
    } else {
      ArrayList<String> l = new ArrayList<String>();
      l.add(entry);
      map.put(key, l);
    }
  }

  private ArrayList<String[]> getAllElments_HashList(HashMap<String, ArrayList<String>> map) {
    ArrayList<String[]> res = new ArrayList<String[]>();
    for (String key : map.keySet()) {
      for (String ip : map.get(key)) {
        String[] pair = new String[2];
        pair[0] = key;
        pair[1] = ip;
        res.add(pair);
      }
    }
    return res;
  }

  public String getDPID(String rname){
    return routingmanager.getDPID(rname);
  }

  public void startSdxServer(String[] args) {

    logger.debug("Carrier Slice server with Service API: START");
    CommandLine cmd = parseCmd(args);
    if (cmd.hasOption('n')) {
      safeauth = false;
      System.out.println("Safe disabled, allowing all requests");
    } else {
      safeauth = true;
    }
    String configfilepath = cmd.getOptionValue("config");
    readConfig(configfilepath);
    IPPrefix = conf.getString("config.ipprefix");
    serverurl = conf.getString("config.serverurl");

    //type=sdxconfig.type;
    computeIP(IPPrefix);
    //System.out.print(pemLocation);
    sliceProxy = getSliceProxy(pemLocation, keyLocation, controllerUrl);
    //SSH context
    sctx = new SliceAccessContext<>();
    try {
      SSHAccessTokenFileFactory fac;
      fac = new SSHAccessTokenFileFactory(sshkey + ".pub", false);
      SSHAccessToken t = fac.getPopulatedToken();
      sctx.addToken("root", "root", t);
      sctx.addToken("root", t);
    } catch (UtilTransportException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    try {
      serverSlice = Slice.loadManifestFile(sliceProxy, sliceName);
      //System.out.println("safe-server managementIP = " + safe.getManagementIP());
      if(safeauth) {
        ComputeNode safe = (ComputeNode) serverSlice.getResourceByName("safe-server");
        safeserver = safe.getManagementIP() + ":7777";
      }
    } catch (ContextTransportException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (TransportException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    catch (Exception e){
      e.printStackTrace();
    }
    //SDNControllerIP="152.3.136.36";
    runCmdSlice(serverSlice, "ovs-ofctl del-flows br0", sshkey, "(c\\d+)", true, true);
    SDNControllerIP = ((ComputeNode) serverSlice.getResourceByName("plexuscontroller")).getManagementIP();
    //System.out.println("plexuscontroler managementIP = " + SDNControllerIP);
    SDNController = SDNControllerIP + ":8080";
    OVSController = SDNControllerIP + ":6633";
    loadSdxNetwork(serverSlice,"(c\\d+)","(sp-c\\d+.*)");
    configRouting(serverSlice,OVSController,SDNController,"(c\\d+)","(sp-c\\d+.*)");
  }

  public void delFlows(){
    runCmdSlice(serverSlice, "ovs-ofctl del-flows br0", sshkey, "(c\\d+)", true, true);
  }

  public String[] stitchRequest(String sdxslice, String site, String customer_slice,String customerName, String ResrvID,String secret,String sdxnode) {
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
      boolean newrouter=false;
      if(sdxnode!=null){
        node=(ComputeNode)s1.getResourceByName(sdxnode);
      }
      if (sdxnode==null &&computenodes.containsKey(site) && computenodes.get(site).size() > 0) {
        node = (ComputeNode) s1.getResourceByName(computenodes.get(site).get(0));
      }
      else if(node==null){
        //if node not exists, add another node to the slice
        //add a node and configure it as a router.
        //later when a customer requests connection between site a and site b, we add another link to meet the requirments
        newrouter = true;
        logger.debug("No existing router at requested site, adding new router");
        int max = -1;
        String routername = null;
        nodelock.lock();
        try {
          for (String key : computenodes.keySet()) {
            for (String cname : computenodes.get(key)) {
              int number = Integer.valueOf(cname.replace("c", ""));
              max = Math.max(max, number);
            }
          }
          ArrayList<String> l = new ArrayList<>();
          routername = "c" + (max + 1);
          l.add(routername);
          logger.debug("Name of new router: " + routername);
          computenodes.put(site, l);
        } finally {
          nodelock.unlock();
        }
        addOVSRouter(s1,site,routername);
        try{
          s1.commit();
          waitTillActive(s1);
        } catch (Exception e) {
          e.printStackTrace();
        }
        s1 = getSlice();
        node = (ComputeNode) s1.getResourceByName(routername);
        copyRouterScript(s1,node);
        configRouter(node);
        logger.debug("Configured the new router in RoutingManager");
      }

      int ip_to_use=0;
      iplock.lock();
      String stitchname;
      try {
        while (usedip.contains(curip)) curip++;
        stitchname = "stitch_" + node.getName() + "_" + curip;
        ip_to_use = curip;
        usedip.add(ip_to_use);
        curip++;
      }finally{
        iplock.unlock();
      }
      Network net=s1.addBroadcastLink(stitchname);
      InterfaceNode2Net ifaceNode0 = (InterfaceNode2Net) net.stitch(node);
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
      if(!newrouter) {
        routingmanager.replayCmds(routingmanager.getDPID(node.getName()));
      }else {
        configRouter(node);
      }
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
      String gw = link.getIP(1);
      String ip=link.getIP(2);
      stitch(customerName,ResrvID,sdxslice,net1_stitching_GUID,secret,ip);
      res[0]=gw;
      res[1]=ip;
      routingmanager.newLink(link.getIP(1), link.nodea,ip.split("/")[0], SDNController);
      //routingmanager.configurePath(ip,node.getName(),ip.split("/")[0],SDNController);
      System.out.println("stitching operation  completed");
    }
    else{
      System.out.println("Unauthorized: stitch request for"+sdxslice +" at "+site);
      logger.debug("Stitching Authorization Failed");
    }
    return res;
  }

  private  String allocateLinkName(){
    //TODO
    return null;
  }

	public  String connectionRequest(String ckeyhash, String self_prefix, String target_prefix,long bandwidth){

	  //String n1=computenodes.get(site1).get(0);
	  //String n2=computenodes.get(site2).get(0);
    String n1=routingmanager.getRouterbyGateway(prefixgateway.get(self_prefix));
    String n2=routingmanager.getRouterbyGateway(prefixgateway.get(target_prefix));
    if(n1==null ||n2==null){
      return "Prefix unrecognized.";
    }
	  boolean res=true;
    routingmanager.printLinks();
	  if(!routingmanager.findPath(n1,n2,bandwidth)) {
      //find name for the new two nodes
      Slice s=getSlice();
      ComputeNode node1=(ComputeNode)s.getResourceByName(n1);
      ComputeNode node2=(ComputeNode)s.getResourceByName(n2);
      String name1 = null, name2 = null;
      String link1 = null;
      //FIXME: if we can't find path bewteen the requested prefix, allcoate new links to meet the requirements

      linklock.lock();
      try {
        link1 = allocateLinkName();
        Link l1 = new Link();
        l1.setName(link1);
        links.put(link1, l1);
      } finally {
        linklock.unlock();
      }
      logger.debug("Add link: " + link1);
      long linkbw=2*bandwidth;
      if(node1.getDomain().equals(node2.getDomain())){
        Network net1=s.addBroadcastLink(link1);
        net1.stitch(node1);
        net1.stitch(node2);

        try {
          s.commit();
        } catch (XMLRPCTransportException e1) {
          // TODO Auto-generated catch block
          e1.printStackTrace();
          System.out.println("Link addition failed.");
          logger.debug("Link addition failed");
        }
      }else{
          System.out.println("Now add a link named \"" + link1 + "\" between " + n1 + " and " + n2 + " with bandwidht " + linkbw);
          try {
            java.io.BufferedReader stdin = new java.io.BufferedReader(new java.io.InputStreamReader(System.in));
            stdin.readLine();
            System.out.println("Continue");
          } catch (Exception e) {
            e.printStackTrace();
          }
        }
      waitTillActive(s);
      s = getSlice();
      //add routers first

      Link l1 = links.get(link1);
      l1.setName(link1);
      l1.addNode(node1.getName());
      l1.addNode(node2.getName());
      l1.setCapacity(linkbw);
      links.put(link1, l1);

      int ip_to_use = 0;
      iplock.lock();
      try {
        while (usedip.contains(curip)) {
          curip++;
        }
        ip_to_use = curip;
        l1.setIP(IPPrefix + String.valueOf(ip_to_use));
        l1.setMask(mask);
        curip++;
      } finally {
        iplock.unlock();
      }
      String param = "";
      Exec.sshExec("root", node1.getManagementIP(), "/bin/bash ~/ovsbridge.sh " + OVSController, sshkey);
      routingmanager.replayCmds(routingmanager.getDPID(node1.getName()));
      Exec.sshExec("root", node1.getManagementIP(), "ifconfig;ovs-vsctl list port", sshkey);
      Exec.sshExec("root", node2.getManagementIP(), "/bin/bash ~/ovsbridge.sh " + OVSController, sshkey);
      routingmanager.replayCmds(routingmanager.getDPID(node2.getName()));
      Exec.sshExec("root", node2.getManagementIP(), "ifconfig;ovs-vsctl list port", sshkey);

      //TODO: why nodeb dpid could be null
      res = routingmanager.newLink(l1.getIP(1), l1.nodea, l1.getIP(2), l1.nodeb, SDNController,linkbw);
      //set ip address
      //add link to links
    }
    //configure routing
    if(res){
      writeLinks(topofile);
      System.out.println("Link added successfully, configuring routes");
      routingmanager.configurePath(self_prefix,n1,target_prefix,n2,prefixgateway.get(self_prefix),SDNController,bandwidth);
      routingmanager.configurePath(target_prefix,n2,self_prefix,n1,prefixgateway.get(target_prefix),SDNController,0);
      System.out.println("Routing set up for "+self_prefix+" and "+target_prefix);
      if(bandwidth>0) {
        routingmanager.setQos(SDNController, routingmanager.getDPID(n1), self_prefix, target_prefix, bandwidth);
        routingmanager.setQos(SDNController, routingmanager.getDPID(n2), target_prefix, self_prefix, bandwidth);
      }
    }

	  return "link added and route configured: "+res;
  }

  public void clear(){
    advertisements.clear();
  }

  public String notifyPrefix(String dest, String gateway, String customer_keyhash){
    logger.debug("received notification for ip prefix "+dest);
    String res="received notification for "+dest;
    if(!safeauth || authorizePrefix(customer_keyhash,dest)){
      if(safeauth) {
        res = res + " [authorization success]";
      }
      boolean flag=false;
      String router=routingmanager.getRouterbyGateway(gateway);
      prefixgateway.put(dest,gateway);
      if(router==null){
        logger.debug("Cannot find a router with cusotmer gateway"+gateway);
        res=res+" Cannot find a router with customer gateway "+gateway;
        return res;
      }
      /*
      for(String[]pair:advertisements){
        if(pair[0].equals(customer_keyhash)&&pair[1].equals(dest)){
          flag=true;
          pair[2]=gateway;
          pair[3]=router;
          continue;
        }
        if(!safeauth || authorizeConnectivity(pair[0],pair[1],customer_keyhash,dest)){
          System.out.println("Connection between "+pair[1]+" and "+dest+" allowed");
          routingmanager.configurePath(dest,router,pair[1],pair[3],gateway,SDNController);
          routingmanager.configurePath(pair[1],pair[3],dest,router,pair[2],SDNController);
        }
      }
      if (!flag) {
        String[] newpair = new String[4];
        newpair[0] = customer_keyhash;
        newpair[1] = dest;
        newpair[2] = gateway;
        newpair[3] = router;
        advertisements.add(newpair);
      }
      */
    } else {
      res = res + " [authorization failed]";
    }
    return res;
  }

  private boolean authorizePrefix(String cushash, String cusip) {
    String[] othervalues = new String[2];
    othervalues[0] = cushash;
    othervalues[1] = cusip;
    String message = SafePost.postSafeStatements(safeserver, "ownPrefix", keyhash, othervalues);
    if (message != null && message.contains("Unsatisfied")) {
      return false;
    } else
      return true;
  }


  private boolean authorizeConnectivity(String srchash, String srcip, String dsthash, String dstip) {
    String[] othervalues = new String[4];
    othervalues[0] = srchash;
    othervalues[1] = dsthash;
    othervalues[2] = srcip;
    othervalues[3] = dstip;
    String message = SafePost.postSafeStatements(safeserver, "connectivity", keyhash, othervalues);
    if (message != null && message.contains("Unsatisfied")) {
      return false;
    } else
      return true;
  }

  public String stitchChameleon(String sdxslice, String nodeName, String customer_keyhash, String
    stitchport, String vlan, String gateway, String ip) {
    String res = "Stitch request unauthorized";
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
          res = "Stitch request failed.\n SdxServer exception in loadManiFestFile";
          e.printStackTrace();
        } catch (TransportException e) {
          res = "Stitch request failed.\n SdxServer exception in loadManiFestFile";
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
        String stitchname = "sp-" + nodeName + "-" + ip.replace("/", "__").replace(".", "_");
        System.out.println("Stitching to Chameleon {" + "stitchname: " + stitchname + " vlan:" + vlan + " stithport: " + stitchport + "}");
        StitchPort mysp = s.addStitchPort(stitchname, vlan, stitchport, 100000000l);
        ComputeNode mynode = (ComputeNode) s.getResourceByName(nodeName);
        mysp.stitch(mynode);
        s.commit();
        waitTillActive(s);
        Exec.sshExec("root", mynode.getManagementIP(), "/bin/bash ~/ovsbridge.sh " + OVSController, sshkey);
        routingmanager.replayCmds(routingmanager.getDPID(nodeName));
        Exec.sshExec("root", mynode.getManagementIP(), "ifconfig;ovs-vsctl list port", sshkey);
        routingmanager.newLink(ip, nodeName, gateway, SDNController);
        res = "Stitch operation Completed";
        System.out.println(res);
      } else {
        System.out.println("Chameleon Stitch Request from " + customer_keyhash + " Unauthorized");
      }
    } catch (Exception e) {
      res = "Stitch request failed.\n SdxServer exception in commiting stitching opoeration";
      e.printStackTrace();
    }
    return res;
  }

  public void stitch(String sdxslice, String RID, String customerName, String CID, String secret,
                     String newip) {
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
    logger.debug("Finished Stitching, set ip address of the new interface to " + newip + "  time elapsed: " + String.valueOf(t2 - t1) + "\n");
    logger.debug("finished sending reconfiguration command");
  }

  public boolean authorizeStitchRequest(String customer_slice, String customerName, String ReservID, String keyhash, String slicename, String nodename) {
    /** Post to remote safesets using apache httpclient */
    String[] othervalues = new String[5];
    othervalues[0] = customer_slice;
    othervalues[1] = customerName;
    othervalues[2] = ReservID;
    othervalues[3] = slicename;
    othervalues[4] = nodename;
    String message = SafePost.postSafeStatements(safeserver, "verifyStitch", keyhash, othervalues);
    if (message == null || message.contains("Unsatisfied")) {
      return false;
    } else
      return true;
  }

  public boolean authorizeStitchChameleon(String customer_keyhash, String stitchport, String vlan, String gateway, String slicename, String nodename) {
    /** Post to remote safesets using apache httpclient */
    String[] othervalues = new String[6];
    othervalues[0] = customer_keyhash;
    othervalues[1] = stitchport;
    othervalues[2] = vlan;
    othervalues[3] = gateway;
    othervalues[4] = slicename;
    othervalues[5] = nodename;

    String message = SafePost.postSafeStatements(safeserver, "verifyChameleonStitch", keyhash, othervalues);
    if (message == null || message.contains("Unsatisfied")) {
      return false;
    } else
      return true;
  }


  private void restartPlexus(String plexusip) {
    logger.debug("Restarting Plexus Controller");
    System.out.println("Restarting Plexus Controller");
    if (checkPlexus(plexusip)) {
      //String script="docker exec -d plexus /bin/bash -c  \"cd /root;pkill ryu-manager;ryu-manager plexus/plexus/app.py ryu/ryu/app/rest_conf_switch.py ryu/ryu/app/rest_qos.py |tee log\"\n";
      String script="pkill ryu-manager;";
      Exec.sshExec("root", plexusip, script, sshkey);
      delFlows();
      script="docker exec -d plexus /bin/bash -c  \"cd /root;pkill ryu-manager; ryu-manager" +
        " ryu/ryu/app/rest_conf_switch.py ryu/ryu/app/rest_qos.py ryu/ryu/app/rest_router_mirror" +
        ".py ryu/ryu/app/ofctl_rest.py|tee log\"\n";
      //String script = "docker exec -d plexus /bin/bash -c  \"cd /root;pkill ryu-manager;ryu-manager ryu/ryu/app/rest_router.py|tee log\"\n";
      logger.debug(sshkey);
      logger.debug(plexusip);
      Exec.sshExec("root", plexusip, script, sshkey);
    }
  }

  public void restartPlexus() {
    restartPlexus(SDNControllerIP);
  }


  private  void putComputeNode(ComputeNode node){
    if(computenodes.containsKey(node.getDomain())) {
      computenodes.get(node.getDomain()).add(node.getName());
      Collections.sort(computenodes.get(node.getDomain()));
    }
    else{
      ArrayList<String> l=new ArrayList<>();
      l.add(node.getName());
      computenodes.put(node.getDomain(),l);
    }
  }
  /*
   * Load the topology information from exogeni with ahab, put the links, stitch_ports, and
   * normal stitches connecting nodes in other slices.
   * By default, routers has the pattern "c\\d+"
   * stitches: stitch_c0_20
   * brolink:
   */
  public void loadSdxNetwork(Slice s, String routerpattern, String stitchportpattern){
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
          Collections.sort(computenodes.get(node.getDomain()));
        }
        else{
          ArrayList<String> l=new ArrayList<>();
          l.add(node.getName());
          computenodes.put(node.getDomain(),l);
        }
      }
      logger.debug("setting up links");
      usedip=new HashSet<Integer>();
      HashSet<String> ifs=new HashSet<String>();
      // get all links, and then
      for(Interface i: s.getInterfaces()){
        InterfaceNode2Net inode2net=(InterfaceNode2Net)i;
        if (i.getName().contains("node") || i.getName().contains("bro"))
        {
          continue;
        }
        System.out.println(i.getName());
        logger.debug("linkname: "+inode2net.getLink().toString()+" bandwidth: "+ inode2net.getLink().getBandwidth());
        if(ifs.contains(i.getName())||!pattern.matcher(inode2net.getNode().getName()).find()){
          logger.debug("continue");
          continue;
        }
        ifs.add(i.getName());
        Link link=links.get(inode2net.getLink().toString());

        if(link==null){
          link=new Link();
          link.setName(inode2net.getLink().toString());
          link.addNode(inode2net.getNode().toString());
          if(link.linkname.contains("stitch") || link.linkname.contains("blink")){
            String[] parts=link.linkname.split("_");
            String ip=parts[parts.length-1];
            usedip.add(Integer.valueOf(ip));
            link.setIP(IPPrefix+ip);
            link.setMask(mask);
          }
        }
        else{
          link.addNode(inode2net.getNode().toString());
        }
        links.put(inode2net.getLink().toString(),link);
        //logger.debug(inode2net.getNode()+" "+inode2net.getLink());
      }
      //read crosssite links
      if(topofile!=null) {
        for (Link link : readLinks(topofile)) {
          links.put(link.linkname, link);
        }
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

  private void configRouter(ComputeNode node){

    String mip = node.getManagementIP();
    logger.debug(node.getName() + " " + mip);
    Exec.sshExec("root", mip, "/bin/bash ~/ovsbridge.sh " + OVSController, sshkey).split(" ");
    String []result=Exec.sshExec("root", mip, "/bin/bash ~/dpid.sh", sshkey).split(" ");
    logger.debug("Trying to get DPID of the router "+node.getName());
    while(result==null || result[1].equals("")||result[1]==null) {
      Exec.sshExec("root", mip, "/bin/bash ~/ovsbridge.sh " + OVSController, sshkey).split(" ");
      sleep(1);
      result = Exec.sshExec("root", mip, "/bin/bash ~/dpid.sh", sshkey).split(" ");
    }
    result[1] = result[1].replace("\n", "");
    logger.debug("Get router info " + result[0] + " " + result[1]);
    routingmanager.newRouter(node.getName(), result[1], Integer.valueOf(result[0]), mip);
  }

  public void waitTillAllOvsConnected(){
    logger.debug("Wait until all ovs bridges have connected to SDN controller");
    ArrayList<Thread> tlist = new ArrayList<Thread>();
    for (String k : computenodes.keySet()) {
      for (final String cname : computenodes.get(k)) {
        final ComputeNode node=(ComputeNode) serverSlice.getResourceByName(cname);
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
                String res = Exec.sshExec("root", mip, cmd, sshkey);
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
  }

  public  String setMirror(String controller, String dpid, String source, String dst, String gw) {
    return routingmanager.setMirror(controller, dpid, source, dst, gw);
  }

  public void configRouting(Slice s,String ovscontroller, String httpcontroller, String routerpattern,String stitchportpattern) {
    logger.debug("Configurating Routing");
    restartPlexus(SDNControllerIP);
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

    waitTillAllOvsConnected();

    logger.debug("setting up sttichports");
    HashSet<Integer> usedip=new HashSet<Integer>();
    HashSet<String> ifs=new HashSet<String>();
    for (StitchPort sp : stitchports) {
      logger.debug("Setting up stitchport "+sp.getName());
      String[] parts = sp.getName().split("-");
      String ip = parts[2].replace("__", "/").replace("_", ".");
      String nodeName = parts[1];
      String[] ipseg=ip.split("\\.");
      String gw=ipseg[0]+"."+ipseg[1]+"."+ipseg[2]+"."+ parts[3];
      routingmanager.newLink(ip, nodeName,gw, SDNController);
    }

    Set keyset = links.keySet();
    //logger.debug(keyset);
    for (Object k : keyset) {
      Link link = links.get((String) k);
      logger.debug("Setting up stitch "+link.linkname);
      if(((String) k).contains("stitch") || ((String) k).contains("blink")){
        usedip.add(Integer.valueOf(link.getIP(1).split("\\.")[2]));
        routingmanager.newLink(link.getIP(1), link.nodea, link.getIP(2).split("/")[0], httpcontroller);
      }
    }

    for (Object k : keyset) {
      Link link = links.get((String) k);
      logger.debug("Setting up link "+link.linkname);
      if (!((String) k).contains("stitch") && !((String )k).contains("blink")) {
        logger.debug("Setting up link " + link.linkname);
        int ip_to_use = 0;
        iplock.lock();
        try {
          while (usedip.contains(curip)) {
            curip++;
          }
          ip_to_use = curip;
          usedip.add(ip_to_use);
          curip++;
        }finally {
          iplock.unlock();
        }
        link.setIP(IPPrefix + String.valueOf(ip_to_use));
        link.setMask(mask);
        //logger.debug(link.nodea+":"+link.getIP(1)+" "+link.nodeb+":"+link.getIP(2));
        routingmanager.newLink(link.getIP(1), link.nodea, link.getIP(2), link.nodeb, httpcontroller,link.capacity);
      }
    }
    //set ovsdb address
    //TODO: comment for simple use
    //routingmanager.setOvsdbAddr(httpcontroller);
  }

  public void undoStitch(String sdxslice, String customerName, String netName, String nodeName) {
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
    logger.debug("Finished UnStitching, time elapsed: " + String.valueOf(t2 - t1) + "\n");
  }

  public void printSlice(){
    Slice s = getSlice(sliceProxy, sliceName);
    printSliceInfo(s);
  }
}

