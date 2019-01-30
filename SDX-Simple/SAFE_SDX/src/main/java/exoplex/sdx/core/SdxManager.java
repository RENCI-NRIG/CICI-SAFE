package exoplex.sdx.core;

import exoplex.common.slice.SliceManager;
import exoplex.common.slice.SiteBase;
import exoplex.common.utils.Exec;
import exoplex.common.utils.HttpUtil;
import exoplex.common.utils.ServerOptions;
import exoplex.sdx.bgp.BgpAdvertise;
import exoplex.sdx.bgp.BgpManager;
import exoplex.sdx.safe.SafeManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.renci.ahab.libndl.resources.request.*;
import org.renci.ahab.libtransport.util.TransportException;
import exoplex.sdx.bro.BroManager;
import exoplex.sdx.network.RoutingManager;
import exoplex.sdx.network.Link;

import org.apache.commons.cli.CommandLine;
import safe.SdxRoutingSlang;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;
import java.util.Collections;
import java.util.Set;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

public class SdxManager extends SliceHelper {
  final Logger logger = LogManager.getLogger(SdxManager.class);
  private final ReentrantLock iplock = new ReentrantLock();
  private final ReentrantLock linklock = new ReentrantLock();
  private final ReentrantLock nodelock = new ReentrantLock();
  private final ReentrantLock brolock = new ReentrantLock();
  private static final String dpidPattern = "^[a-f0-9]{16}";
  protected HashMap<String, ArrayList<String>> edgeRouters = new HashMap<String, ArrayList<String>>();
  protected RoutingManager routingmanager = new RoutingManager();
  protected BgpManager bgpManager;
  protected SafeManager safeManager = null;
  private BroManager broManager = null;
  private String mask = "/24";
  private String SDNController;
  protected SliceManager serverSlice = null;
  private String OVSController;
  private int groupID = 0;
  private String logPrefix = "";
  private boolean safeChecked = false;
  private HashSet<Integer> usedip = new HashSet<Integer>();

  private ConcurrentHashMap<String, String> prefixGateway = new ConcurrentHashMap<String, String>();
  private ConcurrentHashMap<String, HashSet<String>> gatewayPrefixes = new ConcurrentHashMap();
  private ConcurrentHashMap<String, String> customerGateway = new ConcurrentHashMap<String,
      String>();

  private ConcurrentHashMap<String, String> prefixKeyHash = new ConcurrentHashMap<String, String>();

  //the ip prefixes that a customer has advertised
  private ConcurrentHashMap<String, HashSet<String>> customerPrefixes = new ConcurrentHashMap();

  //The node reservation IDs that a customer has stitched to SDX
  private ConcurrentHashMap<String, HashSet<String>> customerNodes = new ConcurrentHashMap<>();

  private ConcurrentHashMap<String, String> peerUrls = new ConcurrentHashMap<>();

  //The name of the link in SDX slice stitched to customer node
  private ConcurrentHashMap<String, String> stitchNet = new ConcurrentHashMap<>();

  public SliceManager getSdxSlice() {
    return serverSlice;
  }

  public String getSDNControllerIP() {
    return SDNControllerIP;
  }

  public String getSDNController() {
    return SDNController;
  }

  public String getManagementIP(String nodeName) {
    return (serverSlice.getComputeNode(nodeName)).getManagementIP();
  }

  public String getSafeServer(){
    return safeServer;
  }

  public String getSafeServerIP(){
    return safeServerIp;
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

  protected void configSdnControllerAddr(String ip){
    SDNControllerIP = ip;
    //SDNControllerIP="152.3.136.36";
    //logger.debug("plexuscontroler managementIP = " + SDNControllerIP);
    SDNController = SDNControllerIP + ":8080";
    OVSController = SDNControllerIP + ":6633";
  }

  public void loadSlice() throws TransportException {
    serverSlice = SliceManager.loadManifestFile(sliceName, pemLocation, keyLocation, controllerUrl);
  }

  public void initializeSdx() throws TransportException {
    loadSlice();
    broManager = new BroManager(serverSlice, routingmanager, this);
    logPrefix += "[" + sliceName + "]";
    //runCmdSlice(serverSlice, "ovs-ofctl del-flows br0", "(^c\\d+)", false, true);

    checkSdxPrerequisites(serverSlice);
    if(plexusAndSafeInSlice){
      configSdnControllerAddr(serverSlice.getComputeNode(plexusName).getManagementIP());
    }else {
      configSdnControllerAddr(conf.getString("config.plexusserver"));
    }
    if(safeEnabled) {
      if(plexusAndSafeInSlice) {
        setSafeServerIp(serverSlice.getComputeNode("safe-server").getManagementIP());
      }else{
        setSafeServerIp(conf.getString("config.safeserver"));
      }
      safeManager = new SafeManager(safeServerIp, safeKeyFile, sshkey);
      bgpManager = new BgpManager(safeManager.getSafeKeyHash());
    }
    //configRouting(serverslice,OVSController,SDNController,"(c\\d+)","(sp-c\\d+.*)");
    loadSdxNetwork(serverSlice, routerPattern, stitchPortPattern, broPattern);
  }


  public void startSdxServer(String[] args) throws TransportException, Exception {
    logger.info(logPrefix + "Carrier Slice server with Service API: START");
    CommandLine cmd = ServerOptions.parseCmd(args);
    initializeExoGENIContexts(cmd.getOptionValue("config"));
    if(conf.hasPath("config.ipprefix")) {
      IPPrefix = conf.getString("config.ipprefix");
      computeIP(IPPrefix);
    }

    if (cmd.hasOption('r')) {
      clearSdx();
    }
    initializeSdx();
    configRouting(OVSController, SDNController, routerPattern, stitchPortPattern);
    startBro();
  }

  public void startSdxServer(String[] args, String sliceName) throws TransportException, Exception {
    logger.info(logPrefix + "Carrier Slice server with Service API: START");
    CommandLine cmd = ServerOptions.parseCmd(args);
    initializeExoGENIContexts(cmd.getOptionValue("config"));
    this.sliceName = sliceName;
    if (cmd.hasOption('r')) {
      clearSdx();
    }
    initializeSdx();
    configRouting(OVSController, SDNController, routerPattern, stitchPortPattern);
    startBro();
  }

  private void startBro() {
    for (ComputeNode node : serverSlice.getComputeNodes()) {
      if (node.getName().matches(broPattern)) {
        Exec.sshExec("root", node.getManagementIP(), "/usr/bin/rm *.log; pkill bro;" +
            "/usr/bin/screen -d -m /opt/bro/bin/bro -i eth1 test-all-policy.bro", sshkey);
      }
    }
  }


  public void delFlows() {
    serverSlice.runCmdSlice("ovs-ofctl -O OpenFlow15 del-flows br0", sshkey, routerPattern,
      false);
  }

  public void delBridges() {
    serverSlice.runCmdSlice("ovs-vsctl del-br br0", sshkey, routerPattern,
      false);
  }

  private void clearSdx() throws TransportException, Exception{
    boolean flag = false;
    if(serverSlice ==null) {
      serverSlice = SliceManager.loadManifestFile(sliceName, pemLocation, keyLocation, controllerUrl);
    }
    for (ComputeNode node : serverSlice.getComputeNodes()) {
      if (node.getName().matches(broPattern)) {
        node.delete();
        flag = true;
      }
    }
    for (Network link : serverSlice.getBroadcastLinks()) {
      if (link.getName().matches(broLinkPattern)) {
        link.delete();
        flag = true;
      }
      if (link.getName().matches(stosVlanPattern)) {
        link.delete();
        flag = true;
      }
    }
    if(flag) {
      serverSlice.commitAndWait();
    }
    delBridges();
  }


  private boolean addLink(String linkName, String
      node1, String node2, long bw) throws TransportException, Exception{
    String ip1 = serverSlice.getComputeNode(node1).getManagementIP();
    String ip2 = serverSlice.getComputeNode(node2).getManagementIP();
    int numInterfaces1 = getInterfaceNum(ip1);
    int numInterfaces2 = getInterfaceNum(ip2);
    serverSlice.lockSlice();
    int times = 1;
    while(true) {
      serverSlice.addLink(linkName, node1, node2, bw);
      if (serverSlice.commitAndWait(10, Arrays.asList(new String[]{linkName}))) {
        sleep(10);
        int newNum1 = getInterfaceNum(ip1);
        int newNum2 = getInterfaceNum(ip2);
        if (newNum1 > numInterfaces1 && newNum2 > numInterfaces2) {
          if (times > 1) {
            logger.warn(String.format("Tried %s times to add a stitchlink", times));
          }
          break;
        }

        sleep(30);
        newNum1 = getInterfaceNum(ip1);
        newNum2 = getInterfaceNum(ip2);
        if (newNum1 > numInterfaces1 && newNum2 > numInterfaces2) {
          if (times > 1) {
            logger.warn(String.format("Tried %s times to add a stitchlink", times));
          }
          break;
        }
      }
      serverSlice.getResourceByName(linkName).delete();
      serverSlice.commitAndWait();
      serverSlice.refresh();
      times++;
    }
    updateMacAddr();
    return true;
  }

  private boolean addLink(String stitchName, String nodeName, long bw) throws  TransportException, Exception{
    String ip = serverSlice.getComputeNode(nodeName).getManagementIP();
    int numInterfaces = getInterfaceNum(ip);
    serverSlice.lockSlice();
    serverSlice.reloadSlice();
    int times = 1;
    while(true) {
      serverSlice.addLink(stitchName, nodeName, bw);
      serverSlice.commitAndWait(10, Arrays.asList(new String[]{stitchName}));
      sleep(10);
      int newNum = getInterfaceNum(ip);
      if(newNum > numInterfaces){
        if(times>1){
          logger.warn(String.format("Tried %s times to add a stitchlink", times));
        }
        break;
      }else{
        sleep(30);
        newNum = getInterfaceNum(ip);
        if(newNum > numInterfaces) {
          if (times > 1) {
            logger.warn(String.format("Tried %s times to add a stitchlink", times));
          }
          break;
        }
        serverSlice.getResourceByName(stitchName).delete();
        serverSlice.commitAndWait();
        serverSlice.refresh();
        times++;
      }
    }
    updateMacAddr();
    return true;
  }

  private boolean addStitchPort(String spName, String nodeName, String stitchUrl, String vlan, long
                                bw) throws
    TransportException, Exception{
    String ip = serverSlice.getComputeNode(nodeName).getManagementIP();
    int numInterfaces = getInterfaceNum(ip);
    serverSlice.lockSlice();
    serverSlice.reloadSlice();
    int times = 1;
    ComputeNode node = serverSlice.getComputeNode(nodeName);
    while(true) {
      StitchPort mysp = serverSlice.addStitchPort(spName, vlan, stitchUrl, bw);
      mysp.stitch(node);
      serverSlice.commitAndWait(10, Arrays.asList(new String[]{spName + "-net"}));
      sleep(10);
      int newNum = getInterfaceNum(ip);
      if(newNum > numInterfaces){
        if(times>1){
          logger.warn(String.format("Tried %s times to add a stitchlink", times));
        }
        break;
      }else{
        sleep(30);
        newNum = getInterfaceNum(ip);
        if(newNum > numInterfaces) {
          if (times > 1) {
            logger.warn(String.format("Tried %s times to add a stitchlink", times));
          }
          break;
        }
        serverSlice.getResourceByName(spName).delete();
        serverSlice.commitAndWait();
        serverSlice.refresh();
        times++;
      }
    }
    updateMacAddr();
    return true;
  }

  private int getInterfaceNum(String ip){
    //String res[] = Exec.sshExec("root", ip, "ifconfig -a|grep \"eth\"|grep" +
    //    " " +
    //    "-v \"eth0\"|sed 's/[ \\t].*//;/^$/d'", sshkey);
    String res[] = Exec.sshExec("root", ip, "/bin/bash /root/ifaces.sh", sshkey);
    logger.debug("Interfaces before");
    logger.debug(res[0]);
    int num = res[0].split("\n").length;
    return num;
  }

  public String adminCmd(String operation, String[]params){
    String[] supportedOperations = new String[]{"stitch"};
    if(operation.equals("stitch")){
      return processStitchCmd(params);

    }else{
      return String.format("Unrecognized operation: %s\n Supported Operations: %s", operation,
        String.join(",",supportedOperations));
    }
  }

  /*
  Params:
  params [serverURI, myNode, myAddress, urAddressPrefix]
   */
  private String processStitchCmd(String[] params) {
    if(serverSlice==null){
      try {
        serverSlice = SliceManager.loadManifestFile(sliceName, pemLocation, keyLocation, controllerUrl);
      }catch (Exception e){
        logger.error(e.getMessage());
      }
    }
    try {
      if(params.length<2){
        return "Missing parameters: [serverURI, myNode]";
      }
      String serverURI = params[0];
      String myNode = params[1];
      Link l1 = new Link();
      l1.addNode(myNode);
      //Set link capacity here
      l1.setMask(mask);
      int ip_to_use = getAvailableIP();
      l1.setIP(IPPrefix + ip_to_use);
      l1.setName(allocateStitchLinkName(l1.getIP(2), myNode));
      String ip = l1.getIP(2);
      String myAddress = ip.split("/")[0];
      String urAddressPrefix = l1.getIP(1);

      ComputeNode node0_s2 = (ComputeNode) serverSlice.getResourceByName(myNode);
      String node0_s2_stitching_GUID = node0_s2.getStitchingGUID();
      String secret = "mysecret";
      logger.debug("node0_s2_stitching_GUID: " + node0_s2_stitching_GUID);
      try {
        serverSlice.permitStitch(secret, node0_s2_stitching_GUID);
      } catch (TransportException e) {
        // TODO Auto-generated catch block
        logger.warn(logPrefix + "Failed to permit stitch");
        e.printStackTrace();
        return null;
      }
      String sdxsite = node0_s2.getDomain();
      //post stitch request to SAFE
      JSONObject jsonparams = new JSONObject();
      jsonparams.put("sdxsite", sdxsite);
      jsonparams.put("cslice", sliceName);
      jsonparams.put("creservid", node0_s2_stitching_GUID);
      jsonparams.put("secret", secret);
      jsonparams.put("gateway", myAddress);
      jsonparams.put("ip", urAddressPrefix);

      if(safeEnabled) {
        if (!safeChecked) {
          if(serverSlice.getResourceByName("safe-server")!=null){
            setSafeServerIp(serverSlice.getComputeNode("safe-server").getManagementIP());
          }else {
            setSafeServerIp(conf.getString("config.safeserver"));
          }
          //sm.verifySafeInstallation(riakIp);
          safeChecked = true;
        }
        jsonparams.put("ckeyhash", safeManager.getSafeKeyHash());
        /*
        postSafeStitchRequest(safeKeyHash, sliceName, node0_s2_stitching_GUID, params[2],
            params[3]);
        */
      }else {
        jsonparams.put("ckeyhash", sliceName);
      }
      logger.debug("Sending stitch request to Sdx server");
      String r = HttpUtil.postJSON(serverURI + "sdx/stitchrequest", jsonparams);
      logger.debug(r);
      JSONObject res = new JSONObject(r);
      logger.info(logPrefix + "Got Stitch Information From Server:\n " + res.toString());
      if (!res.getBoolean("result")) {
        logger.warn(logPrefix + "stitch request failed");
      } else {
        links.put(l1.getLinkName(), l1);
        String gateway = urAddressPrefix.split("/")[0];
        updateOvsInterface(serverSlice, myNode);
        routingmanager.newExternalLink(l1.getLinkName(), ip, myNode, gateway, SDNController);

        String remoteGUID = res.getString("reservID");
        String remoteSafeKeyHash = res.getString("safeKeyHash");
        //Todo: be careful when we want to unstitch from the link side. as the net is virtual
        stitchNet.put(remoteGUID, l1.getLinkName());
        if(!customerNodes.containsKey(remoteSafeKeyHash)){
          customerNodes.put(remoteSafeKeyHash, new HashSet<>());
        }
        customerNodes.get(remoteSafeKeyHash).add(remoteGUID);
        customerGateway.put(remoteGUID, gateway);
        logger.info(logPrefix + "stitch completed.");

        //Peer
        PeerRequest peerRequest = new PeerRequest();
        peerRequest.peerPID = safeManager.getSafeKeyHash();
        peerRequest.peerUrl = this.serverurl;
        String peerRes = HttpUtil.postJSON(serverURI + "sdx/peer", peerRequest.toJsonObject());
        PeerRequest newPeer = new PeerRequest(peerRes);
        if(newPeer.peerPID!=""){
          updatePeer(newPeer);
        }
        return myAddress;
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  private void updatePeer(PeerRequest newPeer){
    this.peerUrls.put(newPeer.peerPID, newPeer.peerUrl);
    ArrayList<BgpAdvertise> advertises = bgpManager.getAllAdvertises();
    for(BgpAdvertise bgpAdvertise: advertises){
      if(!bgpAdvertise.advertiserPID.equals(newPeer)){
        if(!bgpAdvertise.route.contains(newPeer.peerPID)) {
          advertiseBgp(newPeer.peerUrl, bgpAdvertise);
        }
      }
    }
    //Todo: make advertisements
  }

  private void advertiseBgp(String peerUrl, BgpAdvertise advertise){
    logger.info(String.format("advertiseBgp %s", advertise.toString()));
    HttpUtil.postJSON(peerUrl + "sdx/bgp", advertise.toJsonObject());
  }

  private String processUnStitchCmd(String[] params) {
    if(serverSlice==null){
      try {
        serverSlice = SliceManager.loadManifestFile(sliceName, pemLocation, keyLocation, controllerUrl);
      }catch (Exception e){
        logger.error(e.getMessage());
      }
    }
    try {
      ComputeNode node0_s2 = (ComputeNode) serverSlice.getResourceByName(params[1]);
      String node0_s2_stitching_GUID = node0_s2.getStitchingGUID();
      logger.debug("node0_s2_stitching_GUID: " + node0_s2_stitching_GUID);
      JSONObject jsonparams = new JSONObject();
      jsonparams.put("cslice", sliceName);
      jsonparams.put("creservid", node0_s2_stitching_GUID);
      if(safeEnabled) {
        jsonparams.put("ckeyhash", safeManager.getSafeKeyHash());
        /*
        postSafeStitchRequest(safeKeyHash, sliceName, node0_s2_stitching_GUID, params[2],
            params[3]);
        */
      }else{
        jsonparams.put("ckeyhash", sliceName);
      }
      logger.debug("Sending unstitch request to Sdx server");
      String r = HttpUtil.postJSON(serverurl + "sdx/undostitch", jsonparams);
      logger.debug(r);
      logger.info(logPrefix + "Unstitch result:\n " + r);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  public JSONObject stitchRequest(
    String site,
    String customerSafeKeyHash,
    String customerSlice,
    String reserveId,
    String secret,
    String sdxnode,
    String gateway,
    String ip) throws TransportException, Exception{
    long start = System.currentTimeMillis();
    JSONObject res = new JSONObject();
    res.put("ip", "");
    res.put("gateway", "");
    res.put("message", "");

    logger.info(logPrefix + "new stitch request from " + customerSlice+ " for " + sliceName + " at " +
        "" + site);
    logger.debug("new stitch request for " + sliceName + " at " + site);
    //if(!safeEnabled || authorizeStitchRequest(customer_slice,customerName,reserveId, safeKeyHash,
    if(!safeEnabled || safeManager.authorizeStitchRequest(customerSafeKeyHash,customerSlice)){
      if (safeEnabled) {
        logger.info("Authorized: stitch request for " + sliceName);
      }
      serverSlice.reloadSlice();
      String stitchname = null;
      Network net = null;
      ComputeNode node = null;
      if (sdxnode != null && serverSlice.getResourceByName(sdxnode)!= null) {
        node = serverSlice.getComputeNode(sdxnode);
      /*
      serverSlice.lockSlice();
      serverSlice.addLink(stitchname,  node.getName(), bw);
      serverSlice.commitAndWait(10, Arrays.asList(new String[]{stitchname}));
      */
        addLink(stitchname, node.getName(), bw);
        serverSlice.refresh();
      } else if (sdxnode == null && edgeRouters.containsKey(site) && edgeRouters.get(site).size() >
          0) {
        node = serverSlice.getComputeNode(edgeRouters.get(site).get(0));
        stitchname = allocateStitchLinkName(ip, node.getName());
      /*
      serverSlice.lockSlice();
      serverSlice.addLink(stitchname, node.getName(), bw);
      serverSlice.commitAndWait(10, Arrays.asList(new String[]{stitchname}));
      */
        addLink(stitchname, node.getName(), bw);
        serverSlice.refresh();

      } else {
        //if node not exists, add another node to the slice
        //add a node and configure it as a router.
        //later when a customer requests connection between site a and site b, we add another logLink to meet
        // the requirments
        logger.debug("No existing router at requested site, adding new router");
        String eRouterName = allcoateERouterName(site);
        String cRouterName = allcoateCRouterName(site);
        String eLinkName = allocateELinkName();
        serverSlice.lockSlice();
        serverSlice.reloadSlice();
        serverSlice.addOVSRouter(site, eRouterName);
        node = (ComputeNode) serverSlice.getResourceByName(eRouterName);
        stitchname = allocateStitchLinkName(ip, node.getName());
        net = serverSlice.addBroadcastLink(stitchname, bw);
        InterfaceNode2Net ifaceNode0 = (InterfaceNode2Net) net.stitch(node);
        ifaceNode0.setIpAddress(ip);
        ifaceNode0.setNetmask("255.255.255.0");
        serverSlice.commitAndWait(10, Arrays.asList(new String[]{stitchname, eRouterName}));
        serverSlice.reloadSlice();
        copyRouterScript(serverSlice, eRouterName);
        configRouter(eRouterName);
        //Link internal_Log_link = new Link(eLinkName, cRouterName, eRouterName);
        //int ip_1 = getAvailableIP();
        //internal_Log_link.setIP(IPPrefix + String.valueOf(ip_1));
        //links.put(eLinkName, internal_Log_link);
        //routingmanager.newInternalLink(internal_Log_link.getLinkName(),
        //    internal_Log_link.getIP(1),
        //    internal_Log_link.getNodeA(),
        //    internal_Log_link.getIP(2),
        //    internal_Log_link.getNodeB(),
        //    SDNController,
        //    bw);
        logger.debug("Configured the new router in RoutingManager");
      }

      net = (BroadcastNetwork) serverSlice.getResourceByName(stitchname);
      String net1_stitching_GUID = net.getStitchingGUID();
      logger.debug("net1_stitching_GUID: " + net1_stitching_GUID);
      Link logLink = new Link();
      logLink.setName(stitchname);
      logLink.addNode(node.getName());
      links.put(stitchname, logLink);
      serverSlice.stitch(net1_stitching_GUID, customerSlice, reserveId, secret, gateway + "/" +  ip
        .split("/")[1]);
      res.put("ip", ip);
      res.put("gateway", gateway);
      res.put("reservID", net1_stitching_GUID);
      res.put("safeKeyHash", safeManager.getSafeKeyHash());
      sleep(15);
      updateOvsInterface(serverSlice, node.getName());
      routingmanager.newExternalLink(logLink.getLinkName(),
          ip,
          logLink.getNodeA(),
          gateway,
          SDNController);
      //routingmanager.configurePath(ip,node.getName(),ip.split("/")[0],SDNController);
      logger.info(logPrefix + "stitching operation  completed, time elapsed(s): " + (System
          .currentTimeMillis() - start) / 1000);

      //update states
      stitchNet.put(reserveId, stitchname);
      if(!customerNodes.containsKey(customerSafeKeyHash)){
        customerNodes.put(customerSafeKeyHash, new HashSet<>());
      }
      customerNodes.get(customerSafeKeyHash).add(reserveId);
      customerGateway.put(reserveId, gateway);
    }else{
      logger.info("Unauthorized: stitch request for " + sliceName);
      res.put("message", String.format("Unauthorized stitch request from (%s, %s)",
        customerSafeKeyHash, customerSlice));
    }
    return res;
  }

  public String undoStitch( String customerSafeKeyHash, String customerSlice, String
      customerReserveId) throws TransportException, Exception {
    logger.debug("ndllib TestDriver: START");
    logger.info(String.format("Undostitch request from %s for (%s, %s)", customerSafeKeyHash,
      customerSlice, customerReserveId));
    if(customerNodes.containsKey(customerSafeKeyHash) && customerNodes.get(customerSafeKeyHash)
        .contains(customerReserveId)){

    }else{
      return String.format("%s in slice %s is not stitched to SDX", customerReserveId,
          customerSlice);
    }

    Long t1 = System.currentTimeMillis();

    serverSlice.reloadSlice();

    BroadcastNetwork net = (BroadcastNetwork) serverSlice.getResourceByName(stitchNet.get(customerReserveId));
    String stitchNetReserveId = net.getStitchingGUID();
    sliceProxy.undoSliceStitch(sliceName, stitchNetReserveId,customerSlice,
        customerReserveId);

    //clean status after undo stitching
    customerNodes.get(customerSafeKeyHash).remove(customerReserveId);
    String stitchName = stitchNet.remove(customerReserveId);
    String gateway = customerGateway.get(customerReserveId);
    if(gatewayPrefixes.containsKey(gateway)) {
      for (String prefix: gatewayPrefixes.get(gateway)){
        revokePrefix(customerSafeKeyHash, prefix);
      }
    }
    Long t2 = System.currentTimeMillis();
    net.delete();
    serverSlice.commitAndWait();
    routingmanager.removeExternalLink(stitchName, stitchName.split("_")[1], SDNController);
    releaseIP(Integer.valueOf(stitchName.split("_")[2]));
    logger.debug("Finished UnStitching, time elapsed: " + String.valueOf(t2 - t1) + "\n");
    logger.info("Finished UnStitching, time elapsed: " + String.valueOf(t2 - t1) + "\n");
    return "Finished Unstitching";
  }

  private String updateOvsInterface(SliceManager slice, String routerName){
    String res =  slice.runCmdNode( "/bin/bash ~/ovsbridge.sh " + OVSController, sshkey, routerName, true);
    return res;
   }

  //TODO thread safe operation
  public String deployBro(String routerName) throws TransportException , Exception{
    try {
      brolock.lock();
    } catch (Exception e) {
      e.printStackTrace();
    }
    serverSlice.lockSlice();
    serverSlice.reloadSlice();
    logger.info(logPrefix + "deploying new bro instance to " + routerName);
    Long t1 = System.currentTimeMillis();
    ComputeNode router = serverSlice.getComputeNode(routerName);
    String broName = getBroName(router.getName());
    long brobw = conf.getLong("config.brobw");
    int ip_to_use = getAvailableIP();
    serverSlice.addBro(broName, router.getDomain());
    ArrayList<String> resources = new ArrayList<String>();
    String linkName = getBroLinkName(broName, ip_to_use);
    serverSlice.addLink(linkName, "192.168." + ip_to_use + ".1", "192.168." +
        ip_to_use + ".2", "255.255.255.0", routerName, broName, brobw);
    resources.add(broName);
    resources.add(linkName);
    serverSlice.commitAndWait(10, resources);
    serverSlice.refresh();
    String resource_dir = conf.getString("config.resourcedir");
    serverSlice.configBroNode(broName, routerName, resource_dir, SDNControllerIP, serverurl, sshkey);
    updateOvsInterface(serverSlice,routerName);
    Link logLink = new Link();
    logLink.setName(getBroLinkName(broName, ip_to_use));
    logLink.addNode(router.getName());
    usedip.add(ip_to_use);
    logLink.setIP(IPPrefix + ip_to_use);
    logLink.setMask(mask);
    routingmanager.newExternalLink(logLink.getLinkName(),
        logLink.getIP(1),
        logLink.getNodeA(),
        logLink.getIP(2).split("/")[0],
        SDNController);
    Long t2 = System.currentTimeMillis();
    logger.info(logPrefix + "Deployed new Bro node successfully, time elapsed: " + (t2 - t1) +
        "milliseconds");
    logger.debug(logPrefix + "Deployed new Bro node successfully, time elapsed: " + (t2 - t1) +
        "milliseconds");
    brolock.unlock();
    return logLink.getIP(2).split("/")[0];
  }

  private String getBroName(String routerName) {
    String broPattern = "(bro\\d+_" + routerName + ")";
    Pattern pattern = Pattern.compile(broPattern);
    HashSet<Integer> nameSet = new HashSet<Integer>();
    for (ComputeNode c : serverSlice.getComputeNodes()) {
      if (pattern.matcher(c.getName()).matches()) {
        int number = Integer.valueOf(c.getName().split("_")[0].replace("bro", ""));
        nameSet.add(number);
      }
    }
    int start = 0;
    while (nameSet.contains(start)) {
      start++;
    }
    return "bro" + start + "_" + routerName;
  }

  private String allocateCLinkName() {
    //TODO
    String linkname;
    linklock.lock();
    int max = -1;
    try {
      for (String key : links.keySet()) {
        Link logLink = links.get(key);
        if (Pattern.compile(cLinkPattern).matcher(logLink.getLinkName()).matches()) {
          int number = Integer.valueOf(logLink.getLinkName().replace("clink", ""));
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

  private String allocateELinkName() {
    String linkname;
    linklock.lock();
    int max = -1;
    try {
      for (String key : links.keySet()) {
        Link logLink = links.get(key);
        if (Pattern.compile(eLinkPattern).matcher(logLink.getLinkName()).matches()) {
          int number = Integer.valueOf(logLink.getLinkName().replace("elink", ""));
          max = Math.max(max, number);
        }
      }
      linkname = "elink" + (max + 1);
      Link l1 = new Link();
      l1.setName(linkname);
      links.put(linkname, l1);
      logger.debug("Name of new link: " + linkname);
    } finally {
      linklock.unlock();
    }
    return linkname;
  }

  private String allocateStitchLinkName(String ip, String nodeName){
    String sname = ip.replace(".", "_").replace("/","_");
    return "stitch_" + nodeName + "_" + sname;
  }

  private String allcoateCRouterName(String site) {
    String routername = null;
    int max = -1;
    nodelock.lock();
    try {
      for (String key : computenodes.keySet()) {
        for (String cname : computenodes.get(key)) {
          if (cname.matches(cRouterPattern)) {
            int number = Integer.valueOf(cname.replace("c", ""));
            max = Math.max(max, number);
          }
        }
      }
      ArrayList<String> l = computenodes.getOrDefault(site, new ArrayList<>());
      routername = "c" + (max + 1);
      l.add(routername);
      logger.debug("Name of new router: " + routername);
      computenodes.put(site, l);
    } finally {
      nodelock.unlock();
    }
    return routername;
  }

  private String allcoateERouterName(String site) {
    String routername = null;
    int max = -1;
    nodelock.lock();
    try {
      for (String key : computenodes.keySet()) {
        for (String cname : computenodes.get(key)) {
          if (cname.matches(eRouterPattern)) {
            int number = Integer.valueOf(cname.replace("e", ""));
            max = Math.max(max, number);
          }
        }
      }
      ArrayList<String> l = computenodes.getOrDefault(site, new ArrayList<>());
      routername = "e" + (max + 1);
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

  private String getCoreRouterByEdgeRouter(String edgeRouterName) {
    return routingmanager.getNeighbors(edgeRouterName).get(0);
  }

  private String findGatewayForPrefix(String prefix){
    if(prefixGateway.containsKey(prefix)) {
      return prefixGateway.get(prefix);
    }else{
      BgpAdvertise advertise = bgpManager.getAdvertise(prefix);
      if(advertise != null){
        return customerGateway.getOrDefault(advertise.advertiserPID, null);
      }else{
        return null;
      }
    }
  }

  public String connectionRequest(String ckeyhash, String self_prefix, String target_prefix, long bandwidth) throws  Exception{
    logger.info(String.format("Connection request between %s and %s", self_prefix, target_prefix));
    //String n1=computenodes.get(site1).get(0);
    //String n2=computenodes.get(site2).get(0);
    if(safeEnabled) {
      String targetHash = null;
      if(prefixKeyHash.containsKey(target_prefix)) {
        targetHash = prefixKeyHash.get(target_prefix);
      }else {
        BgpAdvertise advertise = bgpManager.getAdvertise(target_prefix);
        if(advertise == null){
          return "target prefix unrecognized.";
        }else {
          targetHash = advertise.ownerPID;
        }
      }
      if(! safeManager.authorizeConnectivity(ckeyhash, self_prefix, targetHash, target_prefix)){
        logger.info("Unauthorized connection request");
        return "Unauthorized connection request";
      }else{
        logger.info("Authorized connection request");
      }
    }
    String n1 = routingmanager.getEdgeRouterByGateway(prefixGateway.get(self_prefix));
    String n2 = null;
    if(prefixGateway.containsKey(target_prefix)){
      n2 = routingmanager.getEdgeRouterByGateway(prefixGateway.get(target_prefix));
    }else{
      BgpAdvertise advertise = bgpManager.getAdvertise(target_prefix);
      if(advertise!= null) {
        String peerNode = customerNodes.get(advertise.advertiserPID).iterator().next();
        n2 = routingmanager.getEdgeRouterByGateway(customerGateway.get(peerNode));
      }
    }
    if (n1 == null || n2 == null) {
      return "Prefix unrecognized.";
    }
    boolean res = true;
    //routingmanager.printLinks();
    if (!routingmanager.findPath(n1, n2, bandwidth)) {
      /*========
      //this is for emulation dynamic links
      for(Link link : links.values()){
        if(link.match(n1, n2) && link.capacity >= bandwidth) {
          res = routingmanager.newLink(link.getIP(1), link.nodea, link.getIP(2), link.nodeb,
            SDNController, link.capacity);
        }
      }
      ===========*/
      serverSlice.lockSlice();
      serverSlice.reloadSlice();
      String c1 = getCoreRouterByEdgeRouter(n1);
      String c2 = getCoreRouterByEdgeRouter(n2);
      ComputeNode node1 = serverSlice.getComputeNode(c1);
      ComputeNode node2 = serverSlice.getComputeNode(c2);

      String link1 = allocateCLinkName();
      logger.debug(logPrefix + "Add link: " + link1);
      long linkbw = 2 * bandwidth;
      addLink(link1, node1.getName(), node2.getName(), bw);

      Link l1 = new Link();
      l1.setName(link1);
      l1.addNode(node1.getName());
      l1.addNode(node2.getName());
      l1.setCapacity(linkbw);
      l1.setMask(mask);
      links.put(link1, l1);
      int ip_to_use = getAvailableIP();
      l1.setIP(IPPrefix + ip_to_use);
      String param = "";
      updateOvsInterface(serverSlice, c1);
      updateOvsInterface(serverSlice, c2);

      //TODO: why nodeb dpid could be null
      res = routingmanager.newInternalLink(l1.getLinkName(),
          l1.getIP(1),
          l1.getNodeA(),
          l1.getIP(2),
          l1.getNodeB(),
          SDNController,
          linkbw);
      //set ip address
      //add link to links
    }
    //configure routing
    if (res) {
      writeLinks(topofile);
      logger.debug("Link added successfully, configuring routes");
      if (routingmanager.configurePath(self_prefix, n1, target_prefix, n2, findGatewayForPrefix
        (self_prefix) , SDNController, bandwidth)
        //&&
        //  routingmanager.configurePath(target_prefix, n2, self_prefix, n1, prefixGateway.get
        //      (target_prefix), SDNController, 0)){
        ){
        logger.info(logPrefix + "Routing set up for " + self_prefix + " and " + target_prefix);
        logger.debug(logPrefix + "Routing set up for " + self_prefix + " and " + target_prefix);
        //TODO: auto select edge router
        setMirror(n1, self_prefix, target_prefix, bandwidth);
        /*
        TODO: qos
        if (bandwidth > 0) {
          routingmanager.setQos(SDNController, routingmanager.getDPID(n1), self_prefix,
              target_prefix, bandwidth);
          routingmanager.setQos(SDNController, routingmanager.getDPID(n2), target_prefix,
              self_prefix, bandwidth);
        }
        */
        return "route configured: " + res;
      } else {
        logger.info(logPrefix + "Route for " + self_prefix + " and " + target_prefix +
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
    logger.info("SDX network reset");
  }


  public String processBgpAdvertise(BgpAdvertise bgpAdvertise){
    if(safeEnabled && !safeManager.authorizeBgpAdvertise(bgpAdvertise)){
      logger.warn(String.format("Unauthorized bgpAdvertise :%s", bgpAdvertise));
      return "";
    }
    BgpAdvertise newAdvertise = bgpManager.receiveAdvertise(bgpAdvertise);
    safeManager.postPathToken(bgpAdvertise);
    if(newAdvertise == null) {
      //No change
      return "";
    }else{
      newAdvertise.safeToken = bgpAdvertise.safeToken;
      //Updates
      //TODO retrive previous routes, how to to it safely?
      if(bgpAdvertise.route.size() > 1) {
        //configure the route if the advertisement is not from a direct customer for access control
        //routingmanager.retriveRouteOfPrefix(bgpAdvertise.prefix, SDNController);
        String customerReservId = customerNodes.get(bgpAdvertise.advertiserPID).iterator().next();
        String gateway = customerGateway.get(customerReservId);
        String edgeNode = routingmanager.getEdgeRouterByGateway(gateway);
        routingmanager.configurePath(bgpAdvertise.prefix, edgeNode, gateway, getSDNController());
      }
      propagateBgpAdvertise(newAdvertise, bgpAdvertise.advertiserPID);
      return newAdvertise.toString();
    }
  }

  public PeerRequest processPeerRequest(PeerRequest peerRequest){
    updatePeer(peerRequest);
    PeerRequest reply = new PeerRequest();
    reply.peerUrl = serverurl;
    reply.peerPID = safeManager.getSafeKeyHash();
    return reply;
  }

  public NotifyResult notifyPrefix(String dest, String gateway, String customer_keyhash) {
    logger.info(logPrefix + "received notification for ip prefix " + dest);
    NotifyResult notifyResult = new NotifyResult();
    notifyResult.message = "received notification for " + dest;
    boolean flag = false;
    String router = routingmanager.getEdgeRouterByGateway(gateway);
    if (router == null) {
      logger.warn(logPrefix + "Cannot find a router with cusotmer gateway" + gateway);
      notifyResult.message = notifyResult.message + " Cannot find a router with customer gateway " +
        gateway;
    }else {
      prefixGateway.put(dest, gateway);
      prefixKeyHash.put(dest, customer_keyhash);
      if(!customerPrefixes.containsKey(customer_keyhash)){
        customerPrefixes.put(customer_keyhash, new HashSet<>());
      }
      customerPrefixes.get(customer_keyhash).add(dest);
      if(!gatewayPrefixes.containsKey(gateway)){
        gatewayPrefixes.put(gateway, new HashSet());
      }
      gatewayPrefixes.get(gateway).add(dest);
      //BgpAdvertise advertise = bgpManager.initAdvertise(customer_keyhash, dest);
      //propagateBgpAdvertise(advertise);
      notifyResult.result= true;
      notifyResult.safeKeyHash = safeManager.getSafeKeyHash();
    }
    return notifyResult;
  }

  private void propagateBgpAdvertise(BgpAdvertise advertise, String srcPid){
    for(String peer: peerUrls.keySet()){
      if(!peer.equals(advertise.ownerPID) && !peer.equals(advertise.advertiserPID)){
        if(!advertise.route.contains(peer) && safeManager.verifyAS(advertise.ownerPID, advertise
          .getPrefix(), peer, advertise.safeToken)) {
          String path = advertise.getPath();
          String[] params = new String[5];
          params[0] = advertise.getPrefix();
          params[1] = path;
          params[2] = peer;
          params[3] = srcPid;
          params[4] = advertise.getLength(1);
          String token = safeManager.post(SdxRoutingSlang.postAdvertise, params);
          advertise.safeToken = token;
          advertiseBgp(peerUrls.get(peer), advertise);
        }
      }
    }

  }

  private void revokePrefix(String customerSafeKeyHash, String prefix){
    prefixGateway.remove(prefix);
    prefixKeyHash.remove(prefix);
    if(customerPrefixes.containsKey(customerSafeKeyHash) && customerPrefixes.get
        (customerSafeKeyHash).contains(prefix)){
      customerPrefixes.get(customerSafeKeyHash).remove(prefix);
    }
    routingmanager.retriveRouteOfPrefix(prefix, SDNController);
  }

  public String stitchChameleon(String site, String nodeName, String customer_keyhash, String
    stitchport,
                                String vlan, String gateway, String ip) {
    String res = "Stitch request unauthorized";
    String sdxsite = SiteBase.get(site);
    if (!safeEnabled || safeManager.authorizeChameleonStitchRequest(customer_keyhash, stitchport,
      vlan)) {
      //FIX ME: do stitching
      logger.info("Chameleon Stitch Request from " + customer_keyhash + " Authorized");
      try {
        //FIX ME: do stitching
        logger.info(logPrefix + "Chameleon Stitch Request from " + customer_keyhash + " Authorized");
        serverSlice.reloadSlice();
        ComputeNode node = null;
        if (nodeName != null) {
          node = serverSlice.getComputeNode(nodeName);
        } else if (nodeName == null && edgeRouters.containsKey(sdxsite) && edgeRouters.get(sdxsite)
          .size() >
          0) {
          node = serverSlice.getComputeNode(edgeRouters.get(sdxsite).get(0));
        } else {
          //if node not exists, add another node to the slice
          //add a node and configure it as a router.
          //later when a customer requests connection between site a and site b, we add another logLink to meet
          // the requirments
          logger.debug("No existing router at requested site, adding new router");
          String eRouterName = allcoateERouterName(sdxsite);
          serverSlice.lockSlice();
          serverSlice.reloadSlice();
          serverSlice.addOVSRouter(sdxsite, eRouterName);
          //serverSlice.addCoreEdgeRouterPair(sdxsite, cRouterName, eRouterName, eLinkName, bw);
          node = (ComputeNode) serverSlice.getResourceByName(eRouterName);
          serverSlice.commitAndWait(10, Arrays.asList(new String[]{eRouterName}));
          serverSlice.reloadSlice();
          //copyRouterScript(serverSlice, cRouterName);
          //configRouter(cRouterName);
          copyRouterScript(serverSlice, eRouterName);
          configRouter(eRouterName);
          //Link internal_Log_link = new Link(eLinkName, cRouterName, eRouterName);
          //int ip_1 = getAvailableIP();
          //internal_Log_link.setIP(IPPrefix + String.valueOf(ip_1));
          //links.put(eLinkName, internal_Log_link);
          //routingmanager.newInternalLink(internal_Log_link.getLinkName(),
          //  internal_Log_link.getIP(1),
          //  internal_Log_link.getNodeA(),
          //  internal_Log_link.getIP(2),
          //  internal_Log_link.getNodeB(),
          //  SDNController,
          //  bw);
          nodeName = node.getName();
          logger.debug("Configured the new router in RoutingManager");
        }
        String stitchname = "sp-" + nodeName + "-" + ip.replace("/", "__").replace(".", "_");
        logger.info(logPrefix + "Stitching to Chameleon {" + "stitchname: " + stitchname + " vlan:" +
            vlan + " stithport: " + stitchport + "}");
        addStitchPort(stitchname, nodeName, stitchport, vlan, bw);
        //StitchPort mysp = serverSlice.addStitchPort(stitchname, vlan, stitchport, bw);
        //mysp.stitch(node);
        //serverSlice.commit();
        //serverSlice.waitTillActive();
        updateOvsInterface(serverSlice, nodeName);
        //routingmanager.replayCmds(routingmanager.getDPID(nodeName));
        Exec.sshExec("root", node.getManagementIP(), "ifconfig;ovs-vsctl list port", sshkey);
        routingmanager.newExternalLink(stitchname, ip, nodeName, gateway, SDNController);
        res = "Stitch operation Completed";
        logger.info(logPrefix + res);
      } catch (Exception e) {
        res = "Stitch request failed.\n SdxServer exception in commiting stitching opoeration";
        e.printStackTrace();
      }
    }
    return res;
  }


  public String broload(String broip, double broload) throws TransportException, Exception {
    // TODO Make this a config
    if (broload > 80) {
      // Find router associated with this bro node.
      Optional<ComputeNode> node = serverSlice.getComputeNodes().stream().filter(w -> w.getManagementIP() == broip).findAny();
      if (node.isPresent()) {
        ComputeNode n = node.get();
        if (n.getName().matches(eRouterPattern)) {
          logger.info(logPrefix + "Overloaded bro is attached to " + n.getName());
          return deployBro(n.getName());
        }
      }
    }
    return "";
  }

  private void restartPlexus(String plexusip) {
    logger.debug("Restarting Plexus Controller");
    logger.info(logPrefix + "Restarting Plexus Controller: " + plexusip);
    delFlows();
    String script = "docker exec -d plexus /bin/bash -c  \"cd /root;pkill ryu-manager; " +
          "ryu-manager ryu/ryu/app/rest_conf_switch.py ryu/ryu/app/rest_qos.py " +
          "ryu/ryu/app/rest_router_mirror.py ryu/ryu/app/ofctl_rest.py |tee log\"\n";
      //String script = "docker exec -d plexus /bin/bash -c  \"cd /root;pkill ryu-manager;
      // ryu-manager ryu/ryu/app/rest_router.py|tee log\"\n";
    Exec.sshExec("root", plexusip, script, sshkey);
    serverSlice.runCmdByIP(script, sshkey, plexusip, true);
  }

  public void restartPlexus() {
    SDNControllerIP = serverSlice.getComputeNode(plexusName).getManagementIP();
    restartPlexus(SDNControllerIP, "rest_router");
  }

  private void putComputeNode(ComputeNode node) {
    if (computenodes.containsKey(node.getDomain())) {
      computenodes.get(node.getDomain()).add(node.getName());
      Collections.sort(computenodes.get(node.getDomain()));
    } else {
      ArrayList<String> l = new ArrayList<>();
      l.add(node.getName());
      computenodes.put(node.getDomain(), l);
    }
  }

  private void putEdgeRouter(ComputeNode node) {
    if (edgeRouters.containsKey(node.getDomain())) {
      edgeRouters.get(node.getDomain()).add(node.getName());
      Collections.sort(edgeRouters.get(node.getDomain()));
    } else {
      ArrayList<String> l = new ArrayList<>();
      l.add(node.getName());
      edgeRouters.put(node.getDomain(), l);
    }
  }
  /*
   * Load the topology information from exogeni with ahab, put the links, stitch_ports, and
   * normal stitches connecting nodes in other slices.
   * By default, routers has the pattern "c\\d+"
   * stitches: stitch_c0_20
   * brolink:
   */

  public void loadSdxNetwork(SliceManager s, String routerpattern, String stitchportpattern, String bropattern) {
    logger.debug("Loading Sdx Network Topology");
    try {
      Pattern pattern = Pattern.compile(routerpattern);
      Pattern stitchpattern = Pattern.compile(stitchportpattern);
      Pattern bropatn = Pattern.compile(bropattern);
      //Nodes: Get all router information
      for (ComputeNode node : s.getComputeNodes()) {
        if (pattern.matcher(node.getName()).find()) {
          putComputeNode(node);
          if (node.getName().matches(eRouterPattern)) {
            putEdgeRouter(node);
          }
        } else if (bropatn.matcher(node.getName()).find()) {
          try {
            InterfaceNode2Net intf = (InterfaceNode2Net) node.getInterfaces().toArray()[0];
            String[] parts = intf.getLink().getName().split("_");
            String ip = parts[parts.length-1];
            broManager.addBroInstance(node.getName(),IPPrefix + ip + ".2", 500000000);
          }catch (Exception e){}
        }
      }
      logger.debug("get links from Slice");
      usedip = new HashSet<Integer>();
      HashSet<String> ifs = new HashSet<String>();
      // get all links, and then
      for (Interface i : s.getInterfaces()) {
        InterfaceNode2Net inode2net = (InterfaceNode2Net) i;
        routingmanager.updateInterfaceMac(inode2net.getNode().getName(),
            inode2net.getLink().getName(),
            inode2net.getMacAddress());
        if (i.getName().contains("node") || i.getName().contains("bro")) {
          continue;
        }
        logger.debug(i.getName());
        logger.debug("linkname: " + inode2net.getLink().toString() + " bandwidth: " + inode2net.getLink().getBandwidth());
        if (ifs.contains(i.getName()) || !pattern.matcher(inode2net.getNode().getName()).find()) {
          logger.debug("continue");
          continue;
        }
        ifs.add(i.getName());
        Link logLink = links.get(inode2net.getLink().toString());

        if (logLink == null) {
          logLink = new Link();
          logLink.setName(inode2net.getLink().toString());
          logLink.addNode(inode2net.getNode().toString());
          if (patternMatch(logLink.getLinkName(), stosVlanPattern) || patternMatch(logLink.getLinkName(),
              broLinkPattern)) {
            String[] parts = logLink.getLinkName().split("_");
            String ip = parts[parts.length - 1];
            usedip.add(Integer.valueOf(ip));
            logLink.setIP(IPPrefix + ip);
            logLink.setMask(mask);
          }
        } else {
          logLink.addNode(inode2net.getNode().toString());
        }
        logger.debug(inode2net.getLink().getBandwidth());
        links.put(inode2net.getLink().toString(), logLink);
        //System.out.println(logPrefix + inode2net.getNode()+" "+inode2net.getLink());
      }
      //read links to get bandwidth infomation
      if (topofile != null) {
        for (Link logLink : readLinks(topofile)) {
          if (isValidLink(logLink.getLinkName())) {
            links.put(logLink.getLinkName(), logLink);
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
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private boolean validDPID(String dpid) {
    if (dpid == null) {
      return false;
    }
    dpid = dpid.replace("\n", "").replace(" ", "");
    if (dpid.length() >= 16) {
      return true;
    }
    return false;
  }

  protected void configRouter(String nodeName) {
    logger.info(String.format("Configuring router %s", nodeName));
    String mip = serverSlice.getComputeNode(nodeName).getManagementIP();
    checkOVS(serverSlice, nodeName);
    checkScripts(serverSlice, nodeName);
    logger.debug(nodeName + " " + mip);
    updateOvsInterface(serverSlice, nodeName);
    String result = serverSlice.getDpid(nodeName, sshkey);
    logger.debug("Trying to get DPID of the router " + nodeName);
    while (result == null || !validDPID(result)) {
      updateOvsInterface(serverSlice, nodeName);
      sleep(1);
      result = serverSlice.getDpid(nodeName, sshkey);
    }
    result = result.replace("\n", "");
    logger.debug(String.format("Get router info %s %s %s", nodeName, mip, result));
    routingmanager.newRouter(nodeName, result, mip);
  }

  protected void configRouters(SliceManager slice) {
    ArrayList<Thread> tlist = new ArrayList<>();
    for(ComputeNode node : slice.getComputeNodes()){
      if(node.getName().matches(routerPattern)){
        try {
          Thread thread = new Thread() {
            @Override
            public void run() {
              configRouter(node.getName());
            }
          };
          thread.start();
          tlist.add(thread);
        } catch (Exception e) {
          logger.error("Exception when configuring routers");
          logger.error(e.getMessage());
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

  public void waitTillAllOvsConnected(){
    routingmanager.waitTillAllOvsConnected(SDNController);
  }


  public String setMirror(String routerName, String source, String dst) {
    setMirror(routerName, source, dst, 100000000);
    return "Mirroring job submitted";
  }

  public String setMirror(String routerName, String source, String dst, long bw) {
    try {
      broManager.setMirrorAsync(routerName, source, dst, bw);
    }catch (Exception e){

    }
    return "Mirroring job submitted";
  }

  public String delMirror(String dpid, String source, String dst) {
    String res = routingmanager.delMirror(SDNController, dpid, source, dst);
    res += "\n" + routingmanager.delMirror(SDNController, dpid, dst, source);
    return res;
  }

  public void configRouting() {
    configRouting(OVSController, SDNController, routerPattern, stitchPortPattern);
  }

  public void configRouting(String ovscontroller, String httpcontroller, String
      routerpattern, String stitchportpattern) {
    logger.debug("Configurating Routing");
    if(plexusAndSafeInSlice) {
      restartPlexus(SDNControllerIP);
    }
    // run ovsbridge scritps to add the all interfaces to the ovsbridge br0, if new interface is
    // added to the ovs bridge, then we reset the controller?
    // FIXME: maybe this is not the best way to do.
    //add all interfaces other than eth0 to ovs bridge br0
    configRouters(serverSlice);

    routingmanager.waitTillAllOvsConnected(SDNController);

    logger.debug("setting up sttichports");
    HashSet<Integer> usedip = new HashSet<Integer>();
    HashSet<String> ifs = new HashSet<String>();
    for (StitchPort sp : stitchports) {
      logger.debug("Setting up stitchport " + sp.getName());
      String[] parts = sp.getName().split("-");
      String ip = parts[2].replace("__", "/").replace("_", ".");
      String nodeName = parts[1];
      String[] ipseg = ip.split("\\.");
      String gw = ipseg[0] + "." + ipseg[1] + "." + ipseg[2] + "." + parts[3];
      routingmanager.newExternalLink(sp.getName(), ip, nodeName, gw, SDNController);
    }

    Set<String> keyset = links.keySet();
    //logger.debug(keyset);
    for (String k : keyset) {
      Link logLink = links.get((String) k);
      logger.debug("Setting up stitch " + logLink.getLinkName());
      if (patternMatch(k, stosVlanPattern) || patternMatch(k, broLinkPattern)) {
        usedip.add(Integer.valueOf(logLink.getIP(1).split("\\.")[2]));
        routingmanager.newExternalLink(logLink.getLinkName(),
            logLink.getIP(1),
            logLink.getNodeA(),
            logLink.getIP(2).split("/")[0],
            httpcontroller);
      }
    }

    //To Emulate dynamic allocation of links, we don't use links whose name does't contain "link"
    for (String k : keyset) {
      Link logLink = links.get((String) k);
      logger.debug("Setting up logLink " + logLink.getLinkName());
      if (isValidLink(k)) {
        logger.debug("Setting up logLink " + logLink.getLinkName());
        if (logLink.getIpPrefix().equals("")) {
          int ip_to_use = getAvailableIP();
          logLink.setIP(IPPrefix + String.valueOf(ip_to_use));
          logLink.setMask(mask);
        }
        //logger.debug(logLink.nodea+":"+logLink.getIP(1)+" "+logLink.nodeb+":"+logLink.getIP(2));
        routingmanager.newInternalLink(logLink.getLinkName(),
            logLink.getIP(1),
            logLink.getNodeA(),
            logLink.getIP(2),
            logLink.getNodeB(),
            httpcontroller,
            logLink.getCapacity());
      }
    }
    //set ovsdb address
    routingmanager.updateAllPorts(SDNController);
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

  private void releaseIP(int ip){
    iplock.lock();
    try {
      if(usedip.contains(ip)){
        usedip.remove(ip);
        if(curip>ip){
          curip = ip;
        }
      }
    } finally {
      iplock.unlock();
    }
  }


  public String getFlowInstallationTime(String routername, String flowPattern) {
    logger.debug("Get flow installation time on " + routername + " for " + flowPattern);
    try {
      String result = Exec.sshExec("root", getManagementIP(routername), getEchoTimeCMD() +
          "ovs-ofctl -O OpenFlow15 dump-flows br0", sshkey)[0];
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
      Long installTime = Long.valueOf(curMillis) - (long) (1000 * Double.valueOf(duration));
      logger.debug("flow installation time result: " + installTime);
      return String.valueOf(installTime);
    } catch (Exception e) {
      logger.debug("result: null");
      return null;
    }
  }

  public void checkFlowTableForPair(String src, String dst, String p1, String p2){
    routingmanager.checkFLowTableForPair(p1, p2,src, dst,  sshkey, logger);
  }

  public int getNumRouteEntries(String routerName, String flowPattern) {
    String result = Exec.sshExec("root", getManagementIP(routerName), getEchoTimeCMD() +
        "ovs-ofctl -O OpenFlow15 dump-flows br0", sshkey)[0];
    String[] parts = result.split("\n");
    String curMillis = parts[0].split(":")[1];
    int num = 0;
    for (String s : parts) {
      if (s.matches(flowPattern)) {
        logger.debug(s);
        num++;
      }
    }
    return num;
  }

  public void logFlowTables() {
    for (ComputeNode node : serverSlice.getComputeNodes()) {
      if (node.getName().matches(routerPattern)) {
        logFlowTables(node.getName());
      }
    }
  }

  public void logFlowTables(String node) {
      logger.debug("------------------");
      logger.debug("Flow table: " + node);
      String result = Exec.sshExec("root", getManagementIP(node), getEchoTimeCMD() +
          "ovs-ofctl -O OpenFlow15 dump-flows br0", sshkey)[0];
      String[] parts = result.split("\n");
      for (String s : parts) {
        logger.debug(s);
      }
      logger.debug("------------------");
  }

   public void updateMacAddr(){
     for (Interface i : serverSlice.getInterfaces()) {
       InterfaceNode2Net inode2net = (InterfaceNode2Net) i;
       routingmanager.updateInterfaceMac(inode2net.getNode().getName(),
           inode2net.getLink().getName(),
           inode2net.getMacAddress());
     }
  }

  protected String getEchoTimeCMD() {
    return "echo currentMillis:$(/bin/date \"+%s%3N\");";
  }
}

