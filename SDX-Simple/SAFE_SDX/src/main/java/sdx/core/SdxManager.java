package sdx.core;

import common.slice.SliceCommon;
import org.json.zip.None;
import org.renci.ahab.libndl.resources.request.*;
import sdx.core.bro.BroManager;
import sdx.networkmanager.Link;
import sdx.networkmanager.NetworkManager;
import common.utils.Exec;
import common.utils.SafePost;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.log4j.Logger;
import org.apache.commons.cli.*;

import org.renci.ahab.libndl.Slice;
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
import sun.nio.ch.Net;

/*

 * @author geni-orca
 * @author Yuanjun Yao, yjyao@cs.duke.edu
 * This is the server for carrier slice. It's run by the carrier_slice owner to do the following
 * things:
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
  final Logger logger = Logger.getLogger(SdxManager.class);

  private NetworkManager routingmanager = new NetworkManager();
  private BroManager broManager  = null;
  int curip = 128;
  private String mask = "/24";
  private String SDNController;
  private Slice serverSlice = null;
  private String OVSController;
  public String serverurl;
  private String logPrefix = "";
  private HashSet<Integer> usedip = new HashSet<Integer>();
  private final ReentrantLock iplock=new ReentrantLock();
  private final ReentrantLock linklock=new ReentrantLock();
  private final ReentrantLock nodelock=new ReentrantLock();
  private final ReentrantLock brolock=new ReentrantLock();
  private HashMap<String,String>prefixgateway=new HashMap<String,String>();

  public static String routerPattern = "(^c\\d+)";
  public static String broPattern = "(bro\\d+_c\\d+)";
  public static String stitchPortPattern = "(^sp-c\\d+.*)";
  public static String stosVlanPattern = "(^stitch_c\\d+_\\d+)";
  public static String linkPattern = "(^clink\\d+)";
  public static String broLinkPattern = "(^blink_\\d+)";


  public Slice getSdxSlice(){
    return serverSlice;
  }
  public String getSDNControllerIP() {
    return SDNControllerIP;
  }
  public String getSDNController(){
    return SDNController;
  }

  public String getManagementIP(String nodeName){
    return ((ComputeNode) serverSlice.getResourceByName(nodeName)).getManagementIP();
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

  public String getDPID(String rname) {
    return routingmanager.getDPID(rname);
  }

  public void startSdxServer(String[] args) {
    System.out.println(logPrefix + "Carrier Slice server with Service API: START");
    CommandLine cmd = parseCmd(args);
    String configfilepath = cmd.getOptionValue("config");
    readConfig(configfilepath);
    IPPrefix = conf.getString("config.ipprefix");
    serverurl = conf.getString("config.serverurl");
    //type=sdxconfig.type;
    computeIP(IPPrefix);
    //System.out.print(pemLocation);
    refreshSliceProxy();
    if(cmd.hasOption('r')){
      clearSdx();
    }
    serverSlice = getSlice();
    broManager = new BroManager(serverSlice, routingmanager, this);
    logPrefix += "vSDX Server [" + sliceName + "]: ";
    //runCmdSlice(serverSlice, "ovs-ofctl del-flows br0", "(^c\\d+)", false, true);
    SDNControllerIP = ((ComputeNode) serverSlice.getResourceByName("plexuscontroller")).getManagementIP();
    //SDNControllerIP="152.3.136.36";
    //logger.debug("plexuscontroler managementIP = " + SDNControllerIP);
    SDNController=SDNControllerIP+":8080";
    OVSController=SDNControllerIP+":6633";
    //configRouting(serverslice,OVSController,SDNController,"(c\\d+)","(sp-c\\d+.*)");
    loadSdxNetwork(serverSlice,routerPattern,stitchPortPattern, broPattern);
    configRouting(serverSlice,OVSController,SDNController,routerPattern,stitchPortPattern);
    startBro();
  }

  private void startBro(){
    for(ComputeNode node: serverSlice.getComputeNodes()){
      if(node.getName().matches(broPattern)){
        Exec.sshExec("root", node.getManagementIP(), "/usr/bin/rm *.log; pkill bro;" +
          "/usr/bin/screen -d -m /opt/bro/bin/bro -i eth1 test-all-policy.bro", sshkey);
      }
    }
  }

  public void delFlows() {
    runCmdSlice(serverSlice, "ovs-ofctl del-flows br0", routerPattern, false, true);
  }

  private void clearSdx(){
    serverSlice = getSlice();
    for(ComputeNode node: serverSlice.getComputeNodes()){
      if(node.getName().matches(broPattern)&& !node.getName().equals("bro0_c0")){
        node.delete();
      }
    }
    for(Network link: serverSlice.getBroadcastLinks()){
      if(link.getName().matches(broLinkPattern) && !link.getName().equals("blink_128")){
        link.delete();
      }
      if(link.getName().matches(stosVlanPattern)){
        link.delete();
      }
    }
    commitAndWait(serverSlice);
  }

  private void addLink(Slice s, String linkName, String ip, String netmask, String nodeName, long
    bw){
    ComputeNode node = (ComputeNode) s.getResourceByName(nodeName);
    Network net = serverSlice.addBroadcastLink(linkName, bw);
    InterfaceNode2Net ifaceNode0 = (InterfaceNode2Net) net.stitch(node);
    ifaceNode0.setIpAddress(ip);
    ifaceNode0.setNetmask(netmask);
    try {
      s.commit();
    }catch (Exception e){
    }
    String res[] = Exec.sshExec("root", node.getManagementIP(), "ifconfig -a|grep \"eth\"|grep" +
      " " +
      "-v \"eth0\"|sed 's/[ \\t].*//;/^$/d'",sshkey);
    int num = res[0].split("\n").length;
    while(true){
      serverSlice.refresh();
      if (serverSlice.getResourceByName(linkName).getState() =="Active"){
        sleep(15);
        res = Exec.sshExec("root", node.getManagementIP(), "ifconfig -a|grep \"eth\"|grep" +
          " " +
          "-v \"eth0\"|sed 's/[ \\t].*//;/^$/d'",sshkey);
        int n1 = res[0].split("\n").length;
        if(n1 > num){
          break;
        }else {
          s.getResourceByName(linkName).delete();
          commitAndWait(s);
          node = (ComputeNode) s.getResourceByName(nodeName);
          net = serverSlice.addBroadcastLink(linkName, bw);
          ifaceNode0 = (InterfaceNode2Net) net.stitch(node);
          ifaceNode0.setIpAddress(ip);
          ifaceNode0.setNetmask(netmask);
          try {
            s.commit();
          }catch (Exception e){
          }
        }
      }
      sleep(10);
    }
  }

  private void addLink(Slice s, String linkName, String ip1, String ip2, String netmask, String
    node1, String node2, long bw){
    ComputeNode node_1 = (ComputeNode) s.getResourceByName(node1);
    ComputeNode node_2 = (ComputeNode) s.getResourceByName(node2);
    Network net = serverSlice.addBroadcastLink(linkName, bw);
    InterfaceNode2Net ifaceNode0 = (InterfaceNode2Net) net.stitch(node_1);
    ifaceNode0.setIpAddress(ip1);
    ifaceNode0.setNetmask(netmask);
    InterfaceNode2Net ifaceNode1 = (InterfaceNode2Net) net.stitch(node_2);
    ifaceNode1.setIpAddress(ip2);
    ifaceNode1.setNetmask(netmask);
    try {
      s.commit();
    }catch (Exception e){
    }
    String res1[] = Exec.sshExec("root", node_1.getManagementIP(), "ifconfig -a|grep " +
      "\"eth\"|grep -v \"eth0\"|sed 's/[ \\t].*//;/^$/d'",sshkey);
    int num1 = res1[0].split("\n").length;
    String res2[] = Exec.sshExec("root", node_2.getManagementIP(), "ifconfig -a|grep " +
      "\"eth\"|grep -v \"eth0\"|sed 's/[ \\t].*//;/^$/d'",sshkey);
    int num2 = res2[0].split("\n").length;
    while(true){
      serverSlice.refresh();
      if (serverSlice.getResourceByName(linkName).getState() =="Active"){
        sleep(10);
        res1 = Exec.sshExec("root", node_1.getManagementIP(), "ifconfig -a|grep \"eth\"|grep" +
          " " +
          "-v \"eth0\"|sed 's/[ \\t].*//;/^$/d'",sshkey);
        int n1 = res1[0].split("\n").length;
        res2 = Exec.sshExec("root", node_2.getManagementIP(), "ifconfig -a|grep \"eth\"|grep" +
          " " +
          "-v \"eth0\"|sed 's/[ \\t].*//;/^$/d'",sshkey);
        int n2 = res1[0].split("\n").length;
        if(n1 > num1 && n2>num2){
          break;
        }else {
          s.getResourceByName(linkName).delete();
          commitAndWait(s);
          s.refresh();
          try {
            node_1 = (ComputeNode) s.getResourceByName(node1);
            node_2 = (ComputeNode) s.getResourceByName(node2);
            net = serverSlice.addBroadcastLink(linkName, bw);
            ifaceNode0 = (InterfaceNode2Net) net.stitch(node_1);
            ifaceNode0.setIpAddress(ip1);
            ifaceNode0.setNetmask(netmask);
            ifaceNode1 = (InterfaceNode2Net) net.stitch(node_2);
            ifaceNode1.setIpAddress(ip2);
            ifaceNode1.setNetmask(netmask);
            s.commit();
          }catch (Exception e){
          }
        }
      }
      sleep(10);
    }
  }

  public String[] stitchRequest(String sdxslice,
                                String site,
                                String customer_slice,
                                String customerName,
                                String ResrvID,
                                String secret,
                                String sdxnode) {
    long start = System.currentTimeMillis();
    System.out.println(logPrefix + "new stitch request from " + customerName + " for " + sdxslice + " at " +
      "" + site);
    logger.debug("new stitch request for " + sdxslice + " at " + site);
    serverSlice = getSlice();
    String[] res = new String[2];
    res[0] = null;
    res[1] = null;
    String stitchname = null;
    Network net = null;
    ComputeNode node = null;
    int ip_to_use = 0;
    if (sdxnode != null) {
      node = (ComputeNode) serverSlice.getResourceByName(sdxnode);
      ip_to_use = getAvailableIP();
      stitchname = "stitch_" + node.getName() + "_" + ip_to_use;
      usedip.add(ip_to_use);
      addLink(serverSlice, stitchname, "192.168." + String.valueOf(ip_to_use) + ".1", "255.255.255.0", node.getName(), 1000000000);
    }
    else if (sdxnode == null && computenodes.containsKey(site) && computenodes.get(site).size() >
      0) {
      node = (ComputeNode) serverSlice.getResourceByName(computenodes.get(site).get(0));
      ip_to_use = getAvailableIP();
      stitchname = "stitch_" + node.getName() + "_" + ip_to_use;
      usedip.add(ip_to_use);
      addLink(serverSlice, stitchname, "192.168." + String.valueOf(ip_to_use) + ".1", "255.255.255.0", node.getName(), 1000000000);
    } else {
      //if node not exists, add another node to the slice
      //add a node and configure it as a router.
      //later when a customer requests connection between site a and site b, we add another link to meet
      // the requirments
      logger.debug("No existing router at requested site, adding new router");
      int max = -1;
      String routerName = allcoateRouterName(site);
      node = addOVSRouter(serverSlice, site, routerName);
      ip_to_use = getAvailableIP();
      stitchname = "stitch_" + node.getName() + "_" + ip_to_use;
      usedip.add(ip_to_use);
      net = serverSlice.addBroadcastLink(stitchname, 1000000000);
      InterfaceNode2Net ifaceNode0 = (InterfaceNode2Net) net.stitch(node);
      ifaceNode0.setIpAddress("192.168." + String.valueOf(ip_to_use) + ".1");
      ifaceNode0.setNetmask("255.255.255.0");
      commitAndWait(serverSlice, 10, Arrays.asList(new String[]{stitchname, routerName}));
      serverSlice.refresh();
      node = (ComputeNode) serverSlice.getResourceByName(routerName);
      copyRouterScript(serverSlice, node);
      configRouter(node);
      logger.debug("Configured the new router in RoutingManager");
    }

    net = (BroadcastNetwork) serverSlice.getResourceByName(stitchname);
    String net1_stitching_GUID = net.getStitchingGUID();
    logger.debug("net1_stitching_GUID: " + net1_stitching_GUID);
    Link link = new Link();
    link.setName(stitchname);
    link.addNode(node.getName());
    link.setIP(IPPrefix + String.valueOf(ip_to_use));
    link.setMask(mask);
    links.put(stitchname, link);
    String gw = link.getIP(1);
    String ip = link.getIP(2);
    stitch(customerName, ResrvID, sdxslice, net1_stitching_GUID, secret, ip);
    res[0] = gw;
    res[1] = ip;
    sleep(15);
    Exec.sshExec("root", node.getManagementIP(), "/bin/bash ~/ovsbridge.sh " +
      OVSController, sshkey);
    routingmanager.newLink(link.getIP(1), link.nodea, ip.split("/")[0], SDNController);
    //routingmanager.configurePath(ip,node.getName(),ip.split("/")[0],SDNController);
    System.out.println(logPrefix + "stitching operation  completed, time elapsed(s): " + (System
      .currentTimeMillis() - start)/1000);
    return res;
  }

  public String deployBro(String routerName) {
    try{
      brolock.lock();
    }catch (Exception e){
      e.printStackTrace();
    }
    refreshSliceProxy();
    serverSlice.refresh();
    System.out.println(logPrefix + "deploying new bro instance to " + routerName);
    Long t1 = System.currentTimeMillis();
    ComputeNode router =  (ComputeNode) serverSlice.getResourceByName(routerName);
    String broName = getBroName(router.getName());
    long brobw = conf.getLong("config.brobw");
    int ip_to_use = getAvailableIP();
    addBro(serverSlice, broName, router);
    try {
      serverSlice.commit();
      ArrayList<String> resources= new ArrayList<String>();
      resources.add(broName);
      waitTillActive(serverSlice, 10, resources);
    } catch (Exception e) {
      e.printStackTrace();
    }
    serverSlice.refresh();
    addLink(serverSlice, getBroLinkName(ip_to_use),"192.168." + ip_to_use + ".1", "192.168." +
        ip_to_use + ".2", "255.255.255.0", routerName, broName, brobw);
    configBroNodes(serverSlice, "(" + broName + ")");
    Exec.sshExec("root", router.getManagementIP(), "/bin/bash ~/ovsbridge.sh " +
      OVSController, sshkey);
    Link link=new Link();
    String broLinkName = getBroLinkName(ip_to_use);
    link.setName(getBroLinkName(ip_to_use));
    link.addNode(router.getName());
    usedip.add(ip_to_use);
    link.setIP(IPPrefix+ip_to_use);
    link.setMask(mask);
    routingmanager.newLink(link.getIP(1), link.nodea, link.getIP(2).split("/")[0],
      SDNController);
    Long t2 = System.currentTimeMillis();
    System.out.println(logPrefix + "Deployed new Bro node successfully, time elapsed: " + (t2- t1) +
      "milliseconds");
    brolock.unlock();
    return link.getIP(2).split("/")[0];
  }

  private String getBroName(String routerName) {
    String broPattern = "(bro\\d+_" + routerName + ")";
    Pattern pattern = Pattern.compile(broPattern);
    HashSet<Integer> nameSet = new HashSet<Integer>();
    for(ComputeNode c: serverSlice.getComputeNodes()){
      if (pattern.matcher(c.getName()).matches()) {
        int number = Integer.valueOf(c.getName().split("_")[0].replace("bro", ""));
        nameSet.add(number);
      }
    }
    int start = 0;
    while(nameSet.contains(start)){
      start ++;
    }
    return "bro" + start + "_" + routerName;
  }

  private  String allocateLinkName() {
    //TODO
    String linkname;
    linklock.lock();
    int max = -1;
    try {
      for (String key : links.keySet()) {
        Link link = links.get(key);
        if(Pattern.compile(linkPattern).matcher(link.linkname).matches()) {
          int number = Integer.valueOf(link.linkname.replace("clink", ""));
          max = Math.max(max, number);
        }
      }
      linkname = "clink" + (max + 1);
      Link l1 = new Link();
      l1.setName(linkname);
      links.put(linkname, l1);
      logger.debug("Name of new link: " + linkname);
    } finally {
      linklock.unlock();
    }
    return linkname;
  }

  private String allcoateRouterName(String site){
    String routername = null;
    int max = - 1;
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
    return routername;
  }

  public void removePath(String src_prefix, String target_prefix) {
    routingmanager.removePath(src_prefix, target_prefix, SDNController);
    routingmanager.removePath(target_prefix, src_prefix, SDNController);
  }

  public String connectionRequest(String ckeyhash, String self_prefix, String target_prefix, long bandwidth) {
    //String n1=computenodes.get(site1).get(0);
    //String n2=computenodes.get(site2).get(0);
    String n1=routingmanager.getRouterbyGateway(prefixgateway.get(self_prefix));
    String n2=routingmanager.getRouterbyGateway(prefixgateway.get(target_prefix));
    if(n1==null ||n2==null){
      return "Prefix unrecognized.";
    }
    boolean res=true;
    //routingmanager.printLinks();
    if(!routingmanager.findPath(n1,n2,bandwidth)) {
      /*========
      //this is for emulation dynamic links
      for(Link link : links.values()){
        if(link.match(n1, n2) && link.capacity >= bandwidth) {
          res = routingmanager.newLink(link.getIP(1), link.nodea, link.getIP(2), link.nodeb,
            SDNController, link.capacity);
        }
      }
      ===========*/
      Slice s=getSlice();
      ComputeNode node1=(ComputeNode)s.getResourceByName(n1);
      ComputeNode node2=(ComputeNode)s.getResourceByName(n2);

      String link1 = allocateLinkName();
      System.out.println(logPrefix + "Add link: " + link1);
      long linkbw=2*bandwidth;
      Network net1=s.addBroadcastLink(link1, linkbw);
      net1.stitch(node1);
      net1.stitch(node2);
      commitAndWait(s);
      /*else{
          System.out.println(logPrefix + "Now add a link named \"" + link1 + "\" between " + n1 + " and " + n2
            + " with bandwidht " + linkbw);
          try {
            java.io.BufferedReader stdin = new java.io.BufferedReader(new java.io.InputStreamReader(System.in));
            stdin.readLine();
            System.out.println(logPrefix + "Continue");
          } catch (Exception e) {
            e.printStackTrace();
          }
        }
      waitTillActive(s);
        */
      s = getSlice();
      //add routers first

      sleep(10);
      Link l1 = links.get(link1);
      l1.setName(link1);
      l1.addNode(node1.getName());
      l1.addNode(node2.getName());
      l1.setCapacity(linkbw);
      l1.setMask(mask);
      links.put(link1, l1);
      int ip_to_use = getAvailableIP();
      l1.setIP(IPPrefix + ip_to_use);
      String param = "";
      Exec.sshExec("root", node1.getManagementIP(), "/bin/bash ~/ovsbridge.sh " + OVSController, sshkey);
      Exec.sshExec("root", node2.getManagementIP(), "/bin/bash ~/ovsbridge.sh " + OVSController, sshkey);

      //TODO: why nodeb dpid could be null
      res = routingmanager.newLink(l1.getIP(1), l1.nodea, l1.getIP(2), l1.nodeb, SDNController,linkbw);
      //set ip address
      //add link to links
    }
    //configure routing
    if(res) {
      writeLinks(topofile);
      logger.debug("Link added successfully, configuring routes");
      if (routingmanager.configurePath(self_prefix,n1,target_prefix,n2,prefixgateway.get
          (self_prefix), SDNController,bandwidth) &&
          routingmanager.configurePath(target_prefix,n2,self_prefix,n1,prefixgateway.get
        (target_prefix),SDNController,0)) {
        System.out.println(logPrefix + "Routing set up for " + self_prefix + " and " + target_prefix);
        logger.debug(logPrefix +"Routing set up for " + self_prefix + " and " + target_prefix);
        setMirror("c0", self_prefix, target_prefix,400000000);
        if(bandwidth>0) {
          routingmanager.setQos(SDNController, routingmanager.getDPID(n1), self_prefix,
            target_prefix, bandwidth);
          routingmanager.setQos(SDNController, routingmanager.getDPID(n2), target_prefix,
            self_prefix, bandwidth);
        }
        return "route configured: " + res;
      }else{
        System.out.println(logPrefix + "Route for " + self_prefix + " and " + target_prefix +
          "Failed");
        logger.debug(logPrefix + "Route for " + self_prefix + " and " + target_prefix +
          "Failed");
      }
    }
    return "route configured: " + res;
  }

  public void reset() {
    delFlows();
    broManager.reset();
    System.out.println("SDX network reset");
  }

  public String notifyPrefix(String dest, String gateway, String customer_keyhash) {
    System.out.println(logPrefix + "received notification for ip prefix "+dest);
    String res="received notification for "+dest;
    boolean flag=false;
    String router=routingmanager.getRouterbyGateway(gateway);
    prefixgateway.put(dest,gateway);
    if(router==null){
      System.out.println(logPrefix + "Cannot find a router with cusotmer gateway"+gateway);
      res=res+" Cannot find a router with customer gateway "+gateway;
    }
    return res;
  }

  public String stitchChameleon(String sdxslice,String nodeName, String customer_keyhash,String stitchport,
                                String vlan, String gateway, String ip) {
    String res="Stitch request unauthorized";
    try {
      //FIX ME: do stitching
      System.out.println(logPrefix + "Chameleon Stitch Request from " + customer_keyhash + " Authorized");
      Slice s = null;
      refreshSliceProxy();
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
      System.out.println(logPrefix + "Stitching to Chameleon {" + "stitchname: " + stitchname + " vlan:" +
        vlan + " stithport: " + stitchport + "}");
      StitchPort mysp = s.addStitchPort(stitchname, vlan, stitchport, 100000000l);
      ComputeNode mynode = (ComputeNode) s.getResourceByName(nodeName);
      mysp.stitch(mynode);
      s.commit();
      waitTillActive(s);
      Exec.sshExec("root", mynode.getManagementIP(), "/bin/bash ~/ovsbridge.sh " +
        OVSController, sshkey);
      //routingmanager.replayCmds(routingmanager.getDPID(nodeName));
      Exec.sshExec("root", mynode.getManagementIP(), "ifconfig;ovs-vsctl list port", sshkey);
      routingmanager.newLink(ip, nodeName, gateway, SDNController);
      res = "Stitch operation Completed";
      System.out.println(logPrefix + res);
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
    logger.debug("Finished Stitching, set ip address of the new interface to "+newip+"  time elapsed: "
      +String.valueOf(t2-t1)+"\n");
  }

  public String broload(String broip, double broload) {
    // TODO Make this a config
    if (broload > 80) {
      Slice s = getSlice();
      // Find router associated with this bro node.
      Optional<ComputeNode> node = s.getComputeNodes().stream().filter(w -> w.getManagementIP() == broip).findAny();
      if (node.isPresent()) {
        ComputeNode n = node.get();
        Pattern pattern = Pattern.compile(routerPattern);
        Matcher match = pattern.matcher(n.getName());
        if (match.find()) {
          System.out.println(logPrefix + "Overloaded bro is attached to " + match.group(1));
          return deployBro(match.group(1));
        }
      }
    }
    return "";
  }

  private void restartPlexus(String plexusip) {
    logger.debug("Restarting Plexus Controller");
    System.out.println(logPrefix + "Restarting Plexus Controller");
    if (checkPlexus(plexusip)) {
      //String script="docker exec -d plexus /bin/bash -c  \"cd /root;pkill ryu-manager;
      // ryu-manager plexus/plexus/app.py ryu/ryu/app/rest_conf_switch.py ryu/ryu/app/rest_qos.py |tee log\"\n";
      delFlows();
      /*
      script="docker exec -d plexus /bin/bash -c  \"cd /root;pkill ryu-manager; ryu-manager" +
        " ryu/ryu/app/rest_conf_switch.py ryu/ryu/app/rest_qos.py ryu/ryu/app/rest_router_mirror" +
        ".py ryu/ryu/app/ofctl_rest.py|tee log\"\n";
      */
      String script="docker exec -d plexus /bin/bash -c  \"cd /root;pkill ryu-manager; " +
        "ryu-manager ryu/ryu/app/rest_conf_switch.py ryu/ryu/app/rest_qos.py " +
        "ryu/ryu/app/rest_router_mirror.py ryu/ryu/app/ofctl_rest.py |tee log\"\n";
      //String script = "docker exec -d plexus /bin/bash -c  \"cd /root;pkill ryu-manager;
      // ryu-manager ryu/ryu/app/rest_router.py|tee log\"\n";
      logger.debug(sshkey);
      logger.debug(plexusip);
      Exec.sshExec("root", plexusip, script, sshkey);
    }
  }

  public void restartPlexus() {
    restartPlexus(SDNControllerIP);
  }

  private void putComputeNode(ComputeNode node){
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

  public void loadSdxNetwork(Slice s, String routerpattern, String stitchportpattern, String bropattern) {
    logger.debug("Loading Sdx Network Topology");
    try {
      Pattern pattern = Pattern.compile(routerpattern);
      Pattern stitchpattern = Pattern.compile(stitchportpattern);
      Pattern bropatn = Pattern.compile(bropattern);
      //Nodes: Get all router information
      for (ComputeNode node : s.getComputeNodes()) {
        if (pattern.matcher(node.getName()).find()) {
          if (computenodes.containsKey(node.getDomain())) {
            computenodes.get(node.getDomain()).add(node.getName());
            Collections.sort(computenodes.get(node.getDomain()));
          } else {
            ArrayList<String> l=new ArrayList<>();
            l.add(node.getName());
            computenodes.put(node.getDomain(),l);
          }
        }
        else if (bropatn.matcher(node.getName()).find()) {
          InterfaceNode2Net intf = (InterfaceNode2Net)node.getInterfaces().toArray()[0];
          String  ip = intf.getLink().getName().split("_")[1];
          broManager.addBroInstance(IPPrefix + ip + ".2", 500000000);
        }
      }
      logger.debug("get links from Slice");
      usedip=new HashSet<Integer>();
      HashSet<String> ifs=new HashSet<String>();
      // get all links, and then
      for (Interface i: s.getInterfaces()) {
        InterfaceNode2Net inode2net=(InterfaceNode2Net)i;
        if (i.getName().contains("node") || i.getName().contains("bro")) {
          continue;
        }
        logger.debug(i.getName());
        logger.debug("linkname: "+inode2net.getLink().toString()+" bandwidth: "+ inode2net.getLink().getBandwidth());
        if (ifs.contains(i.getName())||!pattern.matcher(inode2net.getNode().getName()).find()) {
          logger.debug("continue");
          continue;
        }
        ifs.add(i.getName());
        Link link=links.get(inode2net.getLink().toString());

        if (link == null) {
          link = new Link();
          link.setName(inode2net.getLink().toString());
          link.addNode(inode2net.getNode().toString());
          if (patternMatch(link.linkname, stosVlanPattern) || patternMatch(link.linkname,
            broLinkPattern)) {
            String[] parts=link.linkname.split("_");
            String ip=parts[parts.length-1];
            usedip.add(Integer.valueOf(ip));
            link.setIP(IPPrefix+ip);
            link.setMask(mask);
          }
        } else {
          link.addNode(inode2net.getNode().toString());
        }
        logger.debug(inode2net.getLink().getBandwidth());
        links.put(inode2net.getLink().toString(),link);
        //System.out.println(logPrefix + inode2net.getNode()+" "+inode2net.getLink());
      }
      //read links to get bandwidth infomation
      if (topofile != null) {
        for (Link link : readLinks(topofile)) {
          if(isValidLink(link.linkname)) {
            links.put(link.linkname, link);
          }
        }
      }
      //Stitchports
      logger.debug("setting up sttichports");
      for (StitchPort sp : s.getStitchPorts()) {
        logger.debug(sp.getName());
        Matcher matcher = stitchpattern.matcher(sp.getName());
        if (!matcher.find()) {
          continue;
        }
        stitchports.add(sp);
      }
    } catch(Exception e) {
      e.printStackTrace();
    }
  }

  private boolean validDPID(String dpid){
    if(dpid == null){
      return false;
    }
    dpid = dpid.replace("\n", "").replace(" ","");
    if(dpid.length()>=16){
      return  true;
    }
    return  false;
  }

  private void configRouter(ComputeNode node) {
    String mip = node.getManagementIP();
    logger.debug(node.getName() + " " + mip);
    String res = Exec.sshExec("root", mip, "/bin/bash ~/ovsbridge.sh " + OVSController, sshkey)
      [0];
    if(res.contains("ovs-vsctl: command not found")){
      logger.debug("OVS not installed, trying again");
      res = Exec.sshExec("root", mip, getOVSScript(), sshkey)[0];
      Exec.sshExec("root", mip, "/bin/bash ~/ovsbridge.sh " + OVSController, sshkey);
    }
    String []result=Exec.sshExec("root", mip, "/bin/bash ~/dpid.sh", sshkey)[0].split(" ");
    logger.debug("Trying to get DPID of the router "+node.getName());
    while(result==null || result.length <2 || !validDPID(result[1])) {
      Exec.sshExec("root", mip, "/bin/bash ~/ovsbridge.sh " + OVSController, sshkey)[0].split
        (" ");
      sleep(1);
      result=Exec.sshExec("root", mip, "/bin/bash ~/dpid.sh", sshkey)[0].split(" ");
    }
    result[1] = result[1].replace("\n", "");
    logger.debug("Get router info " + result[0] + " " + result[1]);
    routingmanager.newRouter(node.getName(), result[1], Integer.valueOf(result[0]), mip);
  }

  public void waitTillAllOvsConnected() {
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
                String res = Exec.sshExec("root", mip, cmd, sshkey)[0];
                while (!res.contains("is_connected: true")) {
                  sleep(5);
                  res = Exec.sshExec("root", mip, cmd, sshkey)[0];
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
          System.out.println(logPrefix + "exception when copying config file");
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


  public String setMirror(String routerName, String source, String dst) {
    long bw = 100000000;
    broManager.setMirrorAsync(routerName, source, dst, bw);
    return "Mirroring job submitted";
  }

  /*
  public String setMirror(String routerName, String source, String dst, String broIP) {
    String res = routingmanager.setMirror(SDNController, dpid, source, dst, broIP);
    res += routingmanager.setMirror(SDNController, dpid, dst, source, broIP);
    return res;
  }
  */

  public String setMirror(String routerName, String source, String dst, long bw) {
    broManager.setMirrorAsync(routerName, source, dst, bw);
    return "Mirroring job submitted";
  }

  public String delMirror(String dpid, String source, String dst) {
    String res =  routingmanager.delMirror(SDNController, dpid, source, dst);
    res += "\n" + routingmanager.delMirror(SDNController, dpid, dst, source);
    return res;
  }

  public void configRouting(){
    configRouting(serverSlice, OVSController, SDNController, routerPattern, stitchPortPattern);
  }

  public void configRouting(Slice s,String ovscontroller, String httpcontroller, String
    routerpattern,String stitchportpattern) {
    logger.debug("Configurating Routing");
    restartPlexus(SDNControllerIP);
    // run ovsbridge scritps to add the all interfaces to the ovsbridge br0, if new interface is
    // added to the ovs bridge, then we reset the controller?
    // FIXME: maybe this is not the best way to do.
    //add all interfaces other than eth0 to ovs bridge br0
    runCmdSlice(s, "/bin/bash ~/ovsbridge.sh " + ovscontroller, routerpattern, false, true);
    try {
      for (String k : computenodes.keySet()) {
        for (String cname : computenodes.get(k)) {
          //logger.debug("mip node managment: " + node.getManagementIP());
          ComputeNode node=(ComputeNode) s.getResourceByName(cname);
          String mip = node.getManagementIP();
          logger.debug(node.getName() + " " + mip);
          Exec.sshExec("root", mip, "/bin/bash ~/ovsbridge.sh " + ovscontroller, sshkey)[0].split
            (" ");
          String[] result = Exec.sshExec("root", mip, "/bin/bash ~/dpid.sh", sshkey)[0].split("" +
            " ");
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

    Set<String> keyset = links.keySet();
    //logger.debug(keyset);
    for (String k : keyset) {
      Link link = links.get((String) k);
      logger.debug("Setting up stitch "+link.linkname);
      if( patternMatch(k, stosVlanPattern) || patternMatch(k, broLinkPattern)){
        usedip.add(Integer.valueOf(link.getIP(1).split("\\.")[2]));
        routingmanager.newLink(link.getIP(1), link.nodea, link.getIP(2).split("/")[0],
          httpcontroller);
      }
    }

    //To Emulate dynamic allocation of links, we don't use links whose name does't contain "link"
    for (String k : keyset) {
      Link link = links.get((String) k);
      logger.debug("Setting up link "+link.linkname);
      if(isValidLink(k)){
        logger.debug("Setting up link " + link.linkname);
        if(link.ipprefix.equals("")) {
          int ip_to_use = getAvailableIP();
          link.setIP(IPPrefix + String.valueOf(ip_to_use));
          link.setMask(mask);
        }
        //logger.debug(link.nodea+":"+link.getIP(1)+" "+link.nodeb+":"+link.getIP(2));
        routingmanager.newLink(link.getIP(1), link.nodea, link.getIP(2), link.nodeb,
          httpcontroller,link.capacity);
      }
    }
    //set ovsdb address
    routingmanager.setOvsdbAddr(httpcontroller);
  }

  private int getAvailableIP() {
    int ip_to_use = curip;
    iplock.lock();
    try {
      while (usedip.contains(curip)) {
        curip++;
      }
      ip_to_use = curip;
      usedip.add(ip_to_use);
      curip++;
    } finally {
      iplock.unlock();
    }
    return ip_to_use;
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
      sliceProxy.undoSliceStitch(customerName, node0_s2_stitching_GUID, sdxslice,
        net1_stitching_GUID);
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

  public String getFlowInstallationTime(String routername, String flowPattern){
    logger.debug("Get flow installation time on " + routername + " for " + flowPattern);
    try {
      String result = Exec.sshExec("root", getManagementIP(routername), getEchoTimeCMD() +
        "ovs-ofctl dump-flows br0", sshkey)[0];
      String[] parts = result.split("\n");
      String curMillis = parts[0].split(":")[1];
      String flow = "";
      Pattern pattern = Pattern.compile(flowPattern);
      for (String s : parts) {
        if (s.matches(flowPattern)) {
          flow = s;
          break;
        }
      }
      String duration = flow.split("duration=")[1].split("s")[0];
      Long installTime = Long.valueOf(curMillis) - (long)(1000 * Double.valueOf(duration));
      logger.debug("flow installation time result: " + installTime);
      return String.valueOf(installTime);
    }catch (Exception e){
      logger.debug("result: null");
      return null;
    }
  }

  public int getNumRouteEntries(String routerName, String flowPattern){
    String result = Exec.sshExec("root", getManagementIP(routerName), getEchoTimeCMD() +
      "ovs-ofctl dump-flows br0", sshkey)[0];
    String[] parts = result.split("\n");
    String curMillis = parts[0].split(":")[1];
    int num =0;
    for (String s : parts) {
      if(s.matches(flowPattern)){
        logger.debug(s);
        num ++;
      }
    }
    return num;
  }
}

