package exoplex.sdx.core;

import aqt.PrefixUtil;
import aqt.Range;
import com.google.inject.Inject;
import exoplex.common.utils.HttpUtil;
import exoplex.common.utils.ServerOptions;
import exoplex.sdx.advertise.AdvertiseBase;
import exoplex.sdx.advertise.AdvertiseManager;
import exoplex.sdx.advertise.PolicyAdvertise;
import exoplex.sdx.advertise.RouteAdvertise;
import exoplex.sdx.bro.BroManager;
import exoplex.sdx.core.restutil.NotifyResult;
import exoplex.sdx.core.restutil.PeerRequest;
import exoplex.sdx.network.Link;
import exoplex.sdx.network.RoutingManager;
import exoplex.sdx.network.SdnUtil;
import exoplex.sdx.safe.SafeManager;
import exoplex.sdx.slice.SliceProperties;
import exoplex.sdx.slice.SliceManager;
import exoplex.sdx.slice.exogeni.SiteBase;
import org.apache.commons.cli.CommandLine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.renci.ahab.libndl.resources.request.InterfaceNode2Net;
import org.renci.ahab.libtransport.util.TransportException;
import safe.Authority;
import safe.SdxRoutingSlang;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
  private static final String dpidPattern = "^[a-f0-9]{16}";
  final Logger logger = LogManager.getLogger(SdxManager.class);
  private final ReentrantLock iplock = new ReentrantLock();
  private final ReentrantLock linklock = new ReentrantLock();
  private final ReentrantLock nodelock = new ReentrantLock();
  private final ReentrantLock brolock = new ReentrantLock();
  protected HashMap<String, ArrayList<String>> edgeRouters = new HashMap<String, ArrayList<String>>();
  protected RoutingManager routingmanager = new RoutingManager();
  protected AdvertiseManager advertiseManager;
  protected SafeManager safeManager = null;
  protected SliceManager serverSlice = null;
  private BroManager broManager = null;
  private String mask = "/24";
  private String SDNController;
  private String OVSController;
  private int groupID = 0;
  private String logPrefix = "";
  private boolean safeChecked = false;
  private HashSet<Integer> usedip = new HashSet<Integer>();
  private String publicUrl = null;

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


  @Inject
  public SdxManager(Authority authority) {
    super(authority);
  }

  public String getSDNControllerIP() {
    return SDNControllerIP;
  }

  public String getSDNController() {
    return SDNController;
  }

  public String getManagementIP(String nodeName) {
    return (serverSlice.getManagementIP(nodeName));
  }

  private String getSafeServer() {
    return safeServer;
  }

  private String getSafeServerIP() {
    return safeServerIp;
  }

  String getPid() {
    return safeManager.getSafeKeyHash();
  }

  public String getDPID(String rname) {
    return routingmanager.getDPID(rname);
  }

  protected void configSdnControllerAddr(String ip) {
    SDNControllerIP = ip;
    //SDNControllerIP="152.3.136.36";
    //logger.debug("plexuscontroler managementIP = " + SDNControllerIP);
    SDNController = SDNControllerIP + ":8080";
    OVSController = SDNControllerIP + ":6633";
  }

  public void loadSlice() throws TransportException {
    serverSlice = sliceManagerFactory.create(sliceName, pemLocation, keyLocation, controllerUrl, sshKey);
    try {
      serverSlice.loadSlice();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void initializeSdx() throws TransportException {
    loadSlice();
    broManager = new BroManager(serverSlice, routingmanager, this);
    logPrefix += "[" + sliceName + "]";
    if(conf.hasPath("config.publicurl")){
      this.publicUrl = conf.getString("config.publicurl");
      logger.info(String.format("public url: %s", this.publicUrl));
    } else {
      this.publicUrl = serverurl;
      logger.warn(String.format("config.publicurl not found in configuration file, using %s" +
        " instead\n Sdn controller might not be able to notify new packet to " +
        "sdx", serverurl));
    }
    checkSdxPrerequisites(serverSlice);

    if (plexusInSlice) {
      configSdnControllerAddr(serverSlice.getManagementIP(plexusName));
    } else {
      configSdnControllerAddr(conf.getString("config.plexusserver"));
    }
    if (safeEnabled) {
      if (safeInSlice) {
        setSafeServerIp(serverSlice.getManagementIP("safe-server"));
      } else {
        setSafeServerIp(conf.getString("config.safeserver"));
      }
      safeManager = new SafeManager(safeServerIp, safeKeyFile, sshKey);
      advertiseManager = new AdvertiseManager(safeManager.getSafeKeyHash(), safeManager);
    }
    //configRouting(serverslice,OVSController,SDNController,"(c\\d+)","(sp-c\\d+.*)");
    loadSdxNetwork(routerPattern, stitchPortPattern, broPattern);
  }


  public void startSdxServer(String[] args) throws TransportException, Exception {
    CommandLine cmd = ServerOptions.parseCmd(args);
    this.readConfig(cmd.getOptionValue("config"));
    if (conf.hasPath("config.ipprefix")) {
      IPPrefix = conf.getString("config.ipprefix");
      computeIP(IPPrefix);
    }

    if (cmd.hasOption('r')) {
      clearSdx();
    }
    initializeSdx();
    configRouting();
    startBro();
  }

  void startSdxServer(String[] args, String sliceName) throws TransportException, Exception {
    CommandLine cmd = ServerOptions.parseCmd(args);
    this.readConfig(cmd.getOptionValue("config"));
    this.sliceName = sliceName;
    if (cmd.hasOption('r')) {
      clearSdx();
    }
    initializeSdx();
    configRouting();
    startBro();
  }

  private void startBro() {
    for (String node : serverSlice.getComputeNodes()) {
      if (node.matches(broPattern)) {
        serverSlice.runCmdNode(
          "/usr/bin/rm *.log; pkill bro;" +
            "/usr/bin/screen -d -m /opt/bro/bin/bro -i eth1 test-all-policy.bro", node
        );
      }
    }
  }


  public void delFlows() {
    //routingmanager.deleteAllFlows(getSDNController());
    serverSlice.runCmdSlice(String.format("sudo ovs-ofctl %s del-flows br0", SliceProperties.OFP),
      sshKey,
      routerPattern,
      false);
  }

  public void delBridges() {
    routingmanager = new RoutingManager();
    serverSlice.runCmdSlice(String.format("sudo ovs-vsctl %s del-br br0", SliceProperties.OFP),
      sshKey,
      routerPattern,
      false);
  }

  public void initBridges() {
    configRouters(serverSlice);
  }

  private void clearSdx() throws TransportException, Exception {
    boolean flag = false;
    if (serverSlice == null) {
      loadSlice();
    }
    for (String node : serverSlice.getComputeNodes()) {
      if (node.matches(broPattern)) {
        serverSlice.deleteResource(node);
        flag = true;
      }
    }
    for (String link : serverSlice.getBroadcastLinks()) {
      if (link.matches(broLinkPattern)) {
        serverSlice.deleteResource(link);
        flag = true;
      }
      if (link.matches(stosVlanPattern)) {
        serverSlice.deleteResource(link);
        flag = true;
      }
    }
    if (flag) {
      serverSlice.commitAndWait();
    }
    serverSlice.sleep(60);
    delFlows();
  }


  private boolean addLink(String linkName, String
    node1, String node2, long bw) {
    int numInterfaces1 = serverSlice.getPhysicalInterfaces(node1).size();
    int numInterfaces2 = serverSlice.getPhysicalInterfaces(node2).size();
    serverSlice.lockSlice();
    try {
      int times = 1;
      while (true) {
        serverSlice.addLink(linkName, node1, node2, bw);
        if (serverSlice.commitAndWait(10, Arrays.asList(linkName))) {
          int newNum1 = serverSlice.getPhysicalInterfaces(node1).size();
          int newNum2 = serverSlice.getPhysicalInterfaces(node2).size();
          while (newNum1 <= numInterfaces1 || newNum2 <= numInterfaces2) {
            serverSlice.sleep(5);
            newNum1 = serverSlice.getPhysicalInterfaces(node1).size();
            newNum2 = serverSlice.getPhysicalInterfaces(node2).size();
          }
          if (times > 1) {
            logger.warn(String.format("Tried %s times to add a stitchlink", times));
          }
          break;
        }
        serverSlice.deleteResource(linkName);
        serverSlice.commitAndWait();
        serverSlice.refresh();
        times++;
      }
    } catch (Exception e){
        e.printStackTrace();
        return false;
    } finally {
      serverSlice.unLockSlice();
    }
    updateMacAddr();
    return true;
  }

  private boolean addLink(String stitchName, String nodeName, long bw) {
    //TODO use another SliceManager module that mimic the addition of the stitch link
    logger.info(String.format("Adding link %s %s %s Mbps", stitchName, nodeName, bw/1000000));
    if (serverSlice.getResourceByName(stitchName) != null) {
      return false;
    }
    int numInterfaces = serverSlice.getPhysicalInterfaces(nodeName).size();
    serverSlice.lockSlice();
    serverSlice.refresh();
    try {
      int times = 1;
      while (true) {
        serverSlice.addLink(stitchName, nodeName, bw);
        if (serverSlice.commitAndWait(10, Arrays.asList(new String[]{stitchName}))) {
          int newNum;
          do {
            serverSlice.sleep(10);
            newNum = serverSlice.getPhysicalInterfaces(nodeName).size();
          } while (newNum <= numInterfaces);
          if (times > 1) {
            logger.warn(String.format("Tried %s times to add a stitchlink", times));
          }
          break;
        } else {
          serverSlice.deleteResource(stitchName);
          serverSlice.commitAndWait();
          serverSlice.refresh();
          times++;
        }
      }
    }catch (Exception e){
      e.printStackTrace();
      return false;
    } finally {
      serverSlice.unLockSlice();
    }
    updateMacAddr();
    return true;
  }

  private boolean addStitchPort(String spName, String nodeName, String stitchUrl, String vlan, long
    bw) {
    int numInterfaces = serverSlice.getPhysicalInterfaces(nodeName).size();
    serverSlice.lockSlice();
    serverSlice.refresh();
    try {
      int times = 1;
      String node = serverSlice.getComputeNode(nodeName);
      while (true) {
        String mysp = serverSlice.addStitchPort(spName, vlan, stitchUrl, bw);
        serverSlice.stitchSptoNode(mysp, node);
        int newNum;
        if (serverSlice.commitAndWait(10, Arrays.asList(new String[]{spName + "-net"}))) {
          do {
            serverSlice.sleep(5);
            newNum = serverSlice.getPhysicalInterfaces(nodeName).size();
          } while (newNum <= numInterfaces);
          if (times > 1) {
            logger.warn(String.format("Tried %s times to add a stitchlink", times));
          }
          break;
        } else {
          serverSlice.deleteResource(spName);
          serverSlice.commitAndWait();
          serverSlice.refresh();
          times++;
        }
      }
    } catch (Exception e){
      e.printStackTrace();
      return false;
    } finally {
      serverSlice.unLockSlice();
    }
    updateMacAddr();
    return true;
  }

  public String adminCmd(String operation, String[] params) {
    String[] supportedOperations = new String[]{"stitch"};
    if (operation.equals("stitch")) {
      return processStitchCmd(params);

    } else {
      return String.format("Unrecognized operation: %s\n Supported Operations: %s", operation,
        String.join(",", supportedOperations));
    }
  }

  /**
   * Now this will be triggered by the first incoming packet, so the
   * self_prefix should be dest
   * @param src
   * @param dest
   * @return
   */
  public String processPacketIn(String src, String dest){
    logger.info(String.format("Packet in event: src %s dst: %s", src, dest));
    try {
      String res = this.connectionRequest(dest, src, 0);
      logger.info(res);
      return res;
    } catch (Exception e){
      logger.warn(String.format("failed to enable connection for %s <-> %s",
        src, dest));
      return "failure";
    }
  }

  /*
  Params:
  params [serverURI, myNode, myAddress, urAddressPrefix]
   */
  private synchronized String processStitchCmd(String[] params) {
    try {
      if (serverSlice == null) {
        loadSlice();
      }
      if (params.length < 2) {
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

      String node0_s2 = serverSlice.getComputeNode(myNode);
      String node0_s2_stitching_GUID = serverSlice.getStitchingGUID(node0_s2);
      String secret = serverSlice.permitStitch(node0_s2_stitching_GUID);
      String sdxsite = serverSlice.getNodeDomain(node0_s2);
      //post stitch request to SAFE
      JSONObject jsonparams = new JSONObject();
      jsonparams.put("sdxsite", sdxsite);
      jsonparams.put("cslice", sliceName);
      jsonparams.put("creservid", node0_s2_stitching_GUID);
      jsonparams.put("secret", secret);
      jsonparams.put("gateway", myAddress);
      jsonparams.put("ip", urAddressPrefix);
      if (params.length > 2) {
        jsonparams.put("sdxnode", params[2]);
      }

      if (safeEnabled) {
        if (!safeChecked) {
          if (serverSlice.getResourceByName("safe-server") != null) {
            setSafeServerIp(serverSlice.getManagementIP("safe-server"));
          } else {
            setSafeServerIp(conf.getString("config.safeserver"));
          }
          safeManager.setSafeServerIp(safeServerIp);
          safeChecked = true;
        }
        jsonparams.put("ckeyhash", safeManager.getSafeKeyHash());
      } else {
        jsonparams.put("ckeyhash", sliceName);
      }
      logger.debug("Sending stitch request to Sdx server");
      int interfaceNum = serverSlice.getPhysicalInterfaces(myNode).size();
      String r = HttpUtil.postJSON(serverURI + "sdx/stitchrequest", jsonparams);
      logger.debug(r);
      JSONObject res = new JSONObject(r);
      logger.info(logPrefix + "Got Stitch Information From Server:\n " + res.toString());
      if (!res.getBoolean("result")) {
        logger.warn(logPrefix + "stitch request failed");
      } else {
        links.put(l1.getLinkName(), l1);
        String gateway = urAddressPrefix.split("/")[0];
        int ifNumAfter = interfaceNum;
        do {
          ifNumAfter = serverSlice.getPhysicalInterfaces(myNode).size();
          updateOvsInterface(myNode);
        } while (ifNumAfter <= interfaceNum);
        routingmanager.newExternalLink(l1.getLinkName(), ip, myNode, gateway, SDNController);
        String remoteGUID = res.getString("reservID");
        String remoteSafeKeyHash = res.getString("safeKeyHash");
        //Todo: be careful when we want to unstitch from the link side. as the net is virtual
        stitchNet.put(remoteGUID, l1.getLinkName());
        if (!customerNodes.containsKey(remoteSafeKeyHash)) {
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
        if (newPeer.peerPID != "") {
          updatePeer(newPeer);
        }
        return myAddress;
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  private void updatePeer(PeerRequest newPeer) {
    this.peerUrls.put(newPeer.peerPID, newPeer.peerUrl);
    ArrayList<RouteAdvertise> advertises = advertiseManager.getAllAdvertises();
    for (RouteAdvertise routeAdvertise : advertises) {
      if (!routeAdvertise.advertiserPID.equals(newPeer)) {
        if (!routeAdvertise.route.contains(newPeer.peerPID)) {
          advertiseBgpAsync(newPeer.peerUrl, routeAdvertise);
        }
      }
    }
    //Todo: make advertisements
    //TODO: update Policyes
  }

  private void advertiseBgp(String peerUrl, RouteAdvertise advertise) {
    logger.info(String.format("advertiseBgp %s", advertise.toString()));
    HttpUtil.postJSON(peerUrl + "sdx/bgp", advertise.toJsonObject());
  }

  private void advertisePolicy(String peerUrl, PolicyAdvertise advertise) {
    logger.info(String.format("advertisePolicy %s", advertise.toString()));
    HttpUtil.postJSON(peerUrl + "sdx/policy", advertise.toJsonObject());
  }

  private void advertiseBgpAsync(String peerUrl, RouteAdvertise advertise) {
    Thread thread = new Thread(){
      @Override
      public void run(){
        HttpUtil.postJSON(peerUrl + "sdx/bgp", advertise.toJsonObject());
      }
    };
    thread.start();
  }

  private void advertisePolicyAsync(String peerUrl, PolicyAdvertise advertise) {
    Thread thread = new Thread() {
      @Override
      public void run() {
        HttpUtil.postJSON(peerUrl + "sdx/policy", advertise.toJsonObject());
      }
    };
    thread.start();
  }

  private String processUnStitchCmd(String[] params) {
    try {
      if (serverSlice == null) {
        loadSlice();
      }
      String node0_s2 = serverSlice.getResourceByName(params[1]);
      String node0_s2_stitching_GUID = serverSlice.getStitchingGUID(node0_s2);
      logger.debug("node0_s2_stitching_GUID: " + node0_s2_stitching_GUID);
      JSONObject jsonparams = new JSONObject();
      jsonparams.put("cslice", sliceName);
      jsonparams.put("creservid", node0_s2_stitching_GUID);
      if (safeEnabled) {
        jsonparams.put("ckeyhash", safeManager.getSafeKeyHash());
      } else {
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

  public void unlockSlice(){
    serverSlice.unLockSlice();
  }

  synchronized public JSONObject stitchRequest(
    String site,
    String customerSafeKeyHash,
    String customerSlice,
    String reserveId,
    String secret,
    String sdxnode,
    String gateway,
    String ip) throws TransportException, Exception {
    long start = System.currentTimeMillis();
    JSONObject res = new JSONObject();
    res.put("ip", "");
    res.put("gateway", "");
    res.put("message", "");

    logger.info(logPrefix + "new stitch request from " + customerSlice + " for " + sliceName + " at " +
      "" + site);
    logger.debug("new stitch request for " + sliceName + " at " + site);
    //if(!safeEnabled || authorizeStitchRequest(customer_slice,customerName,reserveId, safeKeyHash,
    if (!safeEnabled || safeManager.authorizeStitchRequest(customerSafeKeyHash, customerSlice)) {
      if (safeEnabled) {
        logger.info("Authorized: stitch request for " + sliceName);
      }
      String stitchname = null;
      String net = null;
      String node = null;
      serverSlice.loadSlice();
      if (sdxnode != null && serverSlice.getComputeNode(sdxnode) != null) {
        node = serverSlice.getComputeNode(sdxnode);
        stitchname = allocateStitchLinkName(ip, node);
        addLink(stitchname, node, bw);
      } else if (sdxnode == null && edgeRouters.containsKey(site) && edgeRouters.get(site).size() >
        0) {
        node = serverSlice.getComputeNode(edgeRouters.get(site).get(0));
        stitchname = allocateStitchLinkName(ip, node);
        addLink(stitchname, node, bw);

      } else {
        //if node not exists, add another node to the slice
        //add a node and configure it as a router.
        //later when a customer requests connection between site a and site b, we add another logLink to meet
        // the requirments
        logger.debug("No existing router at requested site, adding new router");
        node = allcoateERouterName(site);
        serverSlice.lockSlice();
        serverSlice.addOVSRouter(site, node);
        stitchname = allocateStitchLinkName(ip, node);

        net = serverSlice.addBroadcastLink(stitchname, bw);
        serverSlice.stitchNetToNode(net, node);

        serverSlice.commitAndWait(10, Arrays.asList(stitchname, node));
        serverSlice.unLockSlice();
        copyRouterScript(serverSlice, node);
        configRouter(node);
        logger.debug("Configured the new router in RoutingManager");
      }

      String net1_stitching_GUID = serverSlice.getStitchingGUID(stitchname);
      logger.debug("net1_stitching_GUID: " + net1_stitching_GUID);
      Link logLink = new Link();
      logLink.setName(stitchname);
      logLink.addNode(node);
      links.put(stitchname, logLink);
      serverSlice.stitch(net1_stitching_GUID, customerSlice, reserveId, secret, gateway + "/" + ip
        .split("/")[1]);
      res.put("ip", ip);
      res.put("gateway", gateway);
      res.put("reservID", net1_stitching_GUID);
      if(safeEnabled) {
        res.put("safeKeyHash", safeManager.getSafeKeyHash());
      };
      updateOvsInterface(node);
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
      if (!customerNodes.containsKey(customerSafeKeyHash)) {
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

  synchronized public String undoStitch(String customerSafeKeyHash, String customerSlice,
                            String
    customerReserveId) throws TransportException, Exception {
    logger.debug("ndllib TestDriver: START");
    logger.info(String.format("Undostitch request from %s for (%s, %s)", customerSafeKeyHash,
      customerSlice, customerReserveId));
    if (customerNodes.containsKey(customerSafeKeyHash) && customerNodes.get(customerSafeKeyHash)
      .contains(customerReserveId)) {

    } else {
      return String.format("%s in slice %s is not stitched to SDX", customerReserveId,
        customerSlice);
    }

    Long t1 = System.currentTimeMillis();

    serverSlice.loadSlice();
    String stitchLinkName = stitchNet.get(customerReserveId);
    String stitchNodeName = stitchLinkName.split("_")[1];

    serverSlice.unstitch(stitchLinkName, customerSlice, customerReserveId);

    //clean status after undo stitching
    customerNodes.get(customerSafeKeyHash).remove(customerReserveId);
    String stitchName = stitchNet.remove(customerReserveId);
    String gateway = customerGateway.get(customerReserveId);
    if (gatewayPrefixes.containsKey(gateway)) {
      for (String prefix : gatewayPrefixes.get(gateway)) {
        revokePrefix(customerSafeKeyHash, prefix);
      }
    }
    Long t2 = System.currentTimeMillis();
    serverSlice.removeLink(stitchLinkName);
    serverSlice.commitAndWait();
    routingmanager.removeExternalLink(stitchName, stitchName.split("_")[1], SDNController);
    releaseIP(Integer.valueOf(stitchName.split("_")[2]));
    updateOvsInterface(stitchNodeName);
    logger.debug("Finished UnStitching, time elapsed: " + String.valueOf(t2 - t1) + "\n");
    logger.info("Finished UnStitching, time elapsed: " + String.valueOf(t2 - t1) + "\n");

    return "Finished Unstitching";
  }

  private String updateOvsInterface(String routerName) {
    String res = serverSlice.runCmdNode("/bin/bash ~/ovsbridge.sh " + OVSController,
      routerName, true);
    return res;
  }

  //TODO thread safe operation
  public String deployBro(String routerName) throws TransportException, Exception {
    try {
      brolock.lock();
    } catch (Exception e) {
      e.printStackTrace();
    }
    serverSlice.lockSlice();
    serverSlice.refresh();
    logger.info(logPrefix + "deploying new bro instance to " + routerName);
    Long t1 = System.currentTimeMillis();
    String router = serverSlice.getComputeNode(routerName);
    String broName = getBroName(router);
    long brobw = conf.getLong("config.brobw");
    int ip_to_use = getAvailableIP();
    serverSlice.addBro(broName, serverSlice.getNodeDomain(router));
    ArrayList<String> resources = new ArrayList<String>();
    String linkName = getBroLinkName(broName, ip_to_use);
    serverSlice.addLink(linkName, "192.168." + ip_to_use + ".1", "192.168." +
      ip_to_use + ".2", "255.255.255.0", routerName, broName, brobw);
    resources.add(broName);
    resources.add(linkName);
    serverSlice.commitAndWait(10, resources);
    serverSlice.unLockSlice();
    serverSlice.refresh();
    String resource_dir = conf.getString("config.resourcedir");
    serverSlice.configBroNode(broName, routerName, resource_dir, SDNControllerIP, serverurl, sshKey);
    updateOvsInterface(routerName);
    Link logLink = new Link();
    logLink.setName(getBroLinkName(broName, ip_to_use));
    logLink.addNode(router);
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
    for (String c : serverSlice.getComputeNodes()) {
      if (pattern.matcher(c).matches()) {
        int number = Integer.valueOf(c.split("_")[0].replace("bro", ""));
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

  private String allocateStitchLinkName(String ip, String nodeName) {
    String sname = ip.replace(".", "_").replace("/", "_");
    String stitch =  "stitch_" + nodeName + "_" + sname;
    logger.debug(String.format("Name of new link %s", stitch));
    return stitch;
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

  private String getCoreRouterByEdgeRouter(String edgeRouterName) {
    return routingmanager.getNeighbors(edgeRouterName).get(0);
  }

  private String findGatewayForPrefix(String prefix) {
    if (prefixGateway.containsKey(prefix)) {
      return prefixGateway.get(prefix);
    } else {
      RouteAdvertise advertise = advertiseManager.getAdvertise(prefix);
      if (advertise != null) {
        return customerGateway.getOrDefault(advertise.advertiserPID, null);
      } else {
        return null;
      }
    }
  }

  synchronized public String connectionRequest(String self_prefix,
                                   String target_prefix, long bandwidth) throws Exception {
    logger.info(String.format("Connection request between %s and %s", self_prefix, target_prefix));
    //String n1=computenodes.get(site1).get(0);
    //String n2=computenodes.get(site2).get(0);
    if (safeEnabled) {
      String targetHash = null;
      String cKeyHash = null;
      Range selfRange, targetRange;
      if(self_prefix.matches(SdnUtil.IP_PATTERN)){
        selfRange = PrefixUtil.addressToRange(self_prefix);
      } else {
        selfRange = PrefixUtil.prefixToRange(self_prefix);
      }
      if(target_prefix.matches(SdnUtil.IP_PATTERN)){
        targetRange = PrefixUtil.addressToRange(target_prefix);
      } else {
        targetRange = PrefixUtil.prefixToRange(target_prefix);
      }
      for(String prefix: prefixKeyHash.keySet()){
        if(PrefixUtil.prefixToRange(prefix).covers(selfRange)) {
          cKeyHash = prefixKeyHash.get(prefix);
          self_prefix = prefix;
        }
        if(PrefixUtil.prefixToRange(prefix).covers(targetRange)) {
          targetHash = prefixKeyHash.get(prefix);
          target_prefix = prefix;
        }
      }
      if(cKeyHash == null) {
        RouteAdvertise advertise = advertiseManager.getAdvertise(self_prefix,
          target_prefix);
        if (advertise == null) {
          return String.format("prefix %s unrecognized.", self_prefix);
        } else {
          targetHash = advertise.ownerPID;
        }
      }
      if(targetHash == null){
        RouteAdvertise advertise = advertiseManager.getAdvertise(target_prefix, self_prefix);
        if (advertise == null) {
          return String.format("prefix %s unrecognized.", target_prefix);
        } else {
          targetHash = advertise.ownerPID;
        }
      }
      if (!safeManager.authorizeConnectivity(cKeyHash, self_prefix, targetHash,
        target_prefix)) {
        logger.info("Unauthorized connection request");
        return "Unauthorized connection request";
      } else {
        logger.info(String.format("Authorized connection request <%s, %s>",
            self_prefix, target_prefix));
      }
    }
    String n1 = routingmanager.getEdgeRouterByGateway(prefixGateway.get(self_prefix));
    String n2 = null;
    if (prefixGateway.containsKey(target_prefix)) {
      n2 = routingmanager.getEdgeRouterByGateway(prefixGateway.get(target_prefix));
    } else {
      RouteAdvertise advertise = advertiseManager.getAdvertise(target_prefix, self_prefix);
      if (advertise != null) {
        String peerNode = customerNodes.get(advertise.advertiserPID).iterator().next();
        n2 = routingmanager.getEdgeRouterByGateway(customerGateway.get(peerNode));
      }
    }
    if (n1 == null || n2 == null) {
      return "Prefix unrecognized.";
    }
    boolean res = true;
    if (!routingmanager.findPath(n1, n2, bandwidth)) {
      serverSlice.loadSlice();
      String link1 = allocateCLinkName();
      logger.debug(logPrefix + "Add link: " + link1);
      long linkbw = 2 * bandwidth;
      addLink(link1, n1, n2, bw);

      Link l1 = new Link();
      l1.setName(link1);
      l1.addNode(n1);
      l1.addNode(n2);
      l1.setCapacity(linkbw);
      l1.setMask(mask);
      links.put(link1, l1);
      int ip_to_use = getAvailableIP();
      l1.setIP(IPPrefix + ip_to_use);
      String param = "";
      updateOvsInterface(n1);
      updateOvsInterface(n2);

      //TODO: why nodeb dpid could be null
      res = routingmanager.newInternalLink(l1.getLinkName(),
          l1.getIP(1),
          l1.getNodeA(),
          l1.getIP(2),
          l1.getNodeB(),
          SDNController,
          linkbw);
    }
    //configure routing
    if (res) {
      writeLinks(topofile);
      logger.debug("Link added successfully, configuring routes");
      if (routingmanager.configurePath(self_prefix, n1, target_prefix, n2, findGatewayForPrefix
          (self_prefix), SDNController, bandwidth)
        //&&
        //  routingmanager.configurePath(target_prefix, n2, self_prefix, n1, prefixGateway.get
        //      (target_prefix), SDNController, 0)){
      ) {
        logger.info(logPrefix + "Routing set up for " + self_prefix + " and " + target_prefix);
        logger.debug(logPrefix + "Routing set up for " + self_prefix + " and " + target_prefix);
        //TODO: auto select edge router
        //setMirror(n1, self_prefix, target_prefix, bandwidth);
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
    //delBridges();
    broManager.reset();
    logger.info("SDX network reset");
  }

  public String processPolicyAdvertise(PolicyAdvertise policyAdvertise) {
    /*
    if(safeEnabled && !safeManager.authorizeOwnPrefix(policyAdvertise.ownerPID, policyAdvertise.srcPrefix)){
      logger.debug(String.format("%s doesn't own the source prefix %s", policyAdvertise.ownerPID,
          policyAdvertise.srcPrefix));
      return "Authorized policy advertise, the owner doesn't own the prefix";
    }
    */
    //Verify the owner owns the source IP prefix

    // route with both source and destination address, find matching pairs
    ArrayList<AdvertiseBase> newAdvertises = advertiseManager.receiveStPolicy
      (policyAdvertise);
    for (int i = 0; i < newAdvertises.size(); i++) {
      AdvertiseBase newAdvertise = newAdvertises.get(i);
      if (newAdvertise instanceof RouteAdvertise) {
        logger.info(String.format("%s Updating Bgp advertisement after receiving policies " +
          "advertisement%s", sliceName, policyAdvertise.toString()));
        logger.info(String.format("new advertise: %s", newAdvertise.toString()));
        if (newAdvertise.route.size() > 1) {
          //configure the route if the advertisement is not from a direct customer for access control
          //routingmanager.retriveRouteOfPrefix(routeAdvertise.prefix, SDNController);
          String customerReservId = customerNodes.get(((RouteAdvertise) newAdvertise).srcPid).iterator().next();
          String gateway = customerGateway.get(customerReservId);
          String edgeNode = routingmanager.getEdgeRouterByGateway(gateway);
          if (newAdvertise.srcPrefix != null) {
            logger.debug(String.format("Debug Msg: configuring route for policy %s\n new " +
              "advertise: %s", policyAdvertise.toString(), newAdvertise.toString()));
            routingmanager.removePath(newAdvertise.destPrefix, newAdvertise.srcPrefix,
              getSDNController());
            routingmanager.configurePath(newAdvertise.destPrefix, newAdvertise.srcPrefix,
              edgeNode, gateway,
              getSDNController
                ());
          }else{
            logger.debug(String.format("Debug Msg: configuring route for policy %s\n new " +
              "advertise: %s", policyAdvertise.toString(), newAdvertise.toString()));
            routingmanager.removePath(newAdvertise.destPrefix,
              getSDNController());
            routingmanager.configurePath(newAdvertise.destPrefix,
              edgeNode, gateway,
              getSDNController
                ());
          }
        }
        propagateBgpAdvertise((RouteAdvertise) newAdvertise, ((RouteAdvertise) newAdvertise).srcPid);
      } else {
        propagatePolicyAdvertise((PolicyAdvertise) newAdvertise);
      }
    }
    return newAdvertises.stream().map(AdvertiseBase::toString).collect(Collectors.joining(","));
  }

  public String processBgpAdvertise(RouteAdvertise routeAdvertise) {
    if (safeEnabled && !safeManager.authorizeBgpAdvertise(routeAdvertise)) {
      logger.warn(String.format("Unauthorized routeAdvertise :%s", routeAdvertise));
      return "";
    }
    safeManager.postPathToken(routeAdvertise);
    if (!routeAdvertise.hasSrcPrefix()) {
      // routes with destination address only
      //TODO: find mathcing pairs and correct the routes
      RouteAdvertise newAdvertise = advertiseManager.receiveAdvertise(routeAdvertise);
      if (newAdvertise == null) {
        //No change
        return "";
      } else {
        newAdvertise.safeToken = routeAdvertise.safeToken;
        //Updates
        //TODO retrive previous routes, how to do it safely?
        if (routeAdvertise.route.size() > 1) {
          //configure the route if the advertisement is not from a direct customer for access control
          //routingmanager.retriveRouteOfPrefix(routeAdvertise.prefix, SDNController);
          String customerReservId = customerNodes.get(routeAdvertise.advertiserPID).iterator().next();
          String gateway = customerGateway.get(customerReservId);
          String edgeNode = routingmanager.getEdgeRouterByGateway(gateway);
          logger.debug(String.format("Debug Msg: configuring route for %s", routeAdvertise.toString
            ()));
          routingmanager.removePath(routeAdvertise.destPrefix, getSDNController());
          routingmanager.configurePath(routeAdvertise.destPrefix, edgeNode, gateway, getSDNController
            ());
        }
        propagateBgpAdvertise(newAdvertise, newAdvertise.srcPid);
        return newAdvertise.toString();
      }
    } else {
      // route with both source and destination address, find matching pairs
      ArrayList<RouteAdvertise> newAdvertises = advertiseManager.receiveStAdvertise(routeAdvertise);
      if (newAdvertises.size() > 0) {
        String customerReservId = customerNodes.get(routeAdvertise.advertiserPID).iterator().next();
        String gateway = customerGateway.get(customerReservId);
        String edgeNode = routingmanager.getEdgeRouterByGateway(gateway);
        logger.debug(String.format("Debug Msg: configuring route for %s", routeAdvertise.toString
          ()));
        routingmanager.removePath(routeAdvertise.destPrefix, routeAdvertise.srcPrefix,
          getSDNController());
        routingmanager.configurePath(routeAdvertise.destPrefix, routeAdvertise.srcPrefix, edgeNode,
          gateway, getSDNController());
      }
      for (RouteAdvertise newAdvertise : newAdvertises) {
        propagateBgpAdvertise(newAdvertise, routeAdvertise.advertiserPID);
      }
      return newAdvertises.stream().map(RouteAdvertise::toString).collect(Collectors.joining(","));
    }
  }

  synchronized public PeerRequest processPeerRequest(PeerRequest peerRequest) {
    updatePeer(peerRequest);
    PeerRequest reply = new PeerRequest();
    reply.peerUrl = serverurl;
    reply.peerPID = safeManager.getSafeKeyHash();
    return reply;
  }

  synchronized public NotifyResult notifyPrefix(String dest, String gateway,
                                                String customer_keyhash) {
    logger.info(logPrefix + "received notification for ip prefix " + dest);
    NotifyResult notifyResult = new NotifyResult();
    notifyResult.message = "received notification for " + dest;
    boolean flag = false;
    String router = routingmanager.getEdgeRouterByGateway(gateway);
    if (router == null) {
      logger.warn(logPrefix + "Cannot find a router with cusotmer gateway" + gateway);
      notifyResult.message = notifyResult.message + " Cannot find a router with customer gateway " +
        gateway;
    } else {
      prefixGateway.put(dest, gateway);
      prefixKeyHash.put(dest, customer_keyhash);
      if (!customerPrefixes.containsKey(customer_keyhash)) {
        customerPrefixes.put(customer_keyhash, new HashSet<>());
      }
      customerPrefixes.get(customer_keyhash).add(dest);
      if (!gatewayPrefixes.containsKey(gateway)) {
        gatewayPrefixes.put(gateway, new HashSet());
      }
      gatewayPrefixes.get(gateway).add(dest);
      notifyResult.result = true;
      if(safeEnabled) {
        notifyResult.safeKeyHash = safeManager.getSafeKeyHash();
      }
      //monitor the frist package
      routingmanager.monitorOnAllRouter(dest, SdnUtil.DEFAULT_ROUTE,
        SDNController);
      routingmanager.monitorOnAllRouter(SdnUtil.DEFAULT_ROUTE, dest,
        SDNController);
    }
    return notifyResult;
  }

  private void propagatePolicyAdvertise(PolicyAdvertise advertise) {
    logger.debug(String.format("Propagate policy advertise %s", advertise));
    for (String peer : peerUrls.keySet()) {
      if (!peer.equals(advertise.ownerPID) && !peer.equals(advertise.advertiserPID)) {
        if (!advertise.route.contains(peer)) {
          if (advertise.srcPrefix == null && safeManager.verifyAS(advertise.ownerPID, advertise
            .getDestPrefix(), peer, advertise.safeToken)) {
            advertisePolicyAsync(peerUrls.get(peer), advertise);
          } else if (advertise.srcPrefix != null && safeManager.verifyAS(advertise.ownerPID,
            advertise.getSrcPrefix(), advertise.getDestPrefix(), peer, advertise.safeToken)) {
            advertisePolicyAsync(peerUrls.get(peer), advertise);
          }
        }
      }
    }
  }

  private void propagateBgpAdvertise(RouteAdvertise advertise, String srcPid) {
    for (String peer : peerUrls.keySet()) {
      if (!peer.equals(advertise.ownerPID) && !peer.equals(advertise.advertiserPID)) {
        if (!advertise.route.contains(peer)) {
          if (advertise.srcPrefix == null && safeManager.verifyAS(advertise.ownerPID, advertise
            .getDestPrefix(), peer, advertise.safeToken)) {
            String path = advertise.getFormattedPath();
            String[] params = new String[5];
            params[0] = advertise.getDestPrefix();
            params[1] = path;
            params[2] = peer;
            params[3] = srcPid;
            params[4] = advertise.getLength(1);
            String token = safeManager.post(SdxRoutingSlang.postAdvertise, params);
            advertise.safeToken = token;
            advertiseBgpAsync(peerUrls.get(peer), advertise);
          } else if (advertise.srcPrefix != null && safeManager.verifyAS(advertise.ownerPID,
            advertise.getSrcPrefix(), advertise.getDestPrefix(), peer, advertise.safeToken)) {
            String path = advertise.getFormattedPath();
            String[] params = new String[6];
            params[0] = advertise.getSrcPrefix();
            params[1] = advertise.getDestPrefix();
            params[2] = path;
            params[3] = peer;
            params[4] = srcPid;
            params[5] = advertise.getLength(1);
            //TODO: update Safe script and modify this part
            String token = safeManager.post(SdxRoutingSlang.postAdvertiseSD, params);
            advertise.safeToken = token;
            advertiseBgpAsync(peerUrls.get(peer), advertise);
          }
        }
      }
    }
  }

  private void revokePrefix(String customerSafeKeyHash, String prefix) {
    prefixGateway.remove(prefix);
    prefixKeyHash.remove(prefix);
    if (customerPrefixes.containsKey(customerSafeKeyHash) && customerPrefixes.get
      (customerSafeKeyHash).contains(prefix)) {
      customerPrefixes.get(customerSafeKeyHash).remove(prefix);
    }
    routingmanager.retriveRouteOfPrefix(prefix, SDNController);
  }

  synchronized public String stitchChameleon(String site, String nodeName, String customer_keyhash, String
    stitchport,
                                String vlan, String gateway, String ip) {
    String res = "Stitch request unauthorized";
    String sdxsite = SiteBase.get(site);
    if (!safeEnabled || safeManager.authorizeChameleonStitchRequest(customer_keyhash, stitchport,
      vlan)) {
      //FIX ME: do stitching
      logger.info("Chameleon Stitch Request from " + customer_keyhash + " Authorized");
      serverSlice.lockSlice();
      try {
        //FIX ME: do stitching
        logger.info(logPrefix + "Chameleon Stitch Request from " + customer_keyhash + " Authorized");
        serverSlice.refresh();
        String node = null;
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
          serverSlice.refresh();
          serverSlice.addOVSRouter(sdxsite, eRouterName);
          node = serverSlice.getComputeNode(eRouterName);
          serverSlice.commitAndWait(10, Arrays.asList(new String[]{eRouterName}));
          serverSlice.unLockSlice();
          copyRouterScript(serverSlice, eRouterName);
          configRouter(eRouterName);
          nodeName = node;
          logger.debug("Configured the new router in RoutingManager");
        }
        String stitchname = "sp-" + nodeName + "-" + ip.replace("/", "__").replace(".", "_");
        logger.info(logPrefix + "Stitching to Chameleon {" + "stitchname: " + stitchname + " vlan:" +
          vlan + " stithport: " + stitchport + "}");
        addStitchPort(stitchname, nodeName, stitchport, vlan, bw);
        updateOvsInterface(nodeName);
        //routingmanager.replayCmds(routingmanager.getDPID(nodeName));
        routingmanager.newExternalLink(stitchname, ip, nodeName, gateway, SDNController);
        res = "Stitch operation Completed";
        logger.info(logPrefix + res);
      } catch (Exception e) {
        res = "Stitch request failed.\n SdxServer exception in commiting stitching opoeration";
        e.printStackTrace();
      } finally {
        serverSlice.unLockSlice();
      }
    }
    return res;
  }


  public String broload(String broip, double broload) throws TransportException, Exception {
    // TODO Make this a config
    if (broload > 80) {
      // Find router associated with this bro node.
      Optional<String> node = serverSlice.getComputeNodes().stream().filter(w ->
        serverSlice.getManagementIP(w) == broip).findAny();
      if (node.isPresent()) {
        String n = node.get();
        if (n.matches(eRouterPattern)) {
          logger.info(logPrefix + "Overloaded bro is attached to " + n);
          return deployBro(n);
        }
      }
    }
    return "";
  }

  private void restartPlexus(String plexusip, String type) {
    if (type.equals("rest_router")) {
      logger.debug("Restarting Plexus Controller");
      logger.info(logPrefix + "Restarting Plexus Controller: " + plexusip);
      delFlows();
      String script = "sudo docker exec -d plexus /bin/bash -c  \"cd /root;pkill ryu-manager; "
        + "ryu-manager --log-file ~/log --default-log-level 1 "
        + "ryu/ryu/app/rest_conf_switch.py ryu/ryu/app/rest_router.py "
        + "ryu/ryu/app/ofctl_rest.py %s\"\n";
      String sdxMonitorUrl = this.publicUrl + "sdx/flow/packetin";
      //reuse ryu-manager option for sdx url
      script = String.format(script, String.format("--zapi-db-url %s", sdxMonitorUrl));
      //String script = "docker exec -d plexus /bin/bash -c  \"cd /root;pkill ryu-manager;
      // ryu-manager ryu/ryu/app/rest_router.py|tee log\"\n";
      serverSlice.runCmdByIP(script, plexusip, false);
    }
  }

  public void restartPlexus() {
    SDNControllerIP = serverSlice.getManagementIP(plexusName);
    restartPlexus(SDNControllerIP, "rest_router");
  }

  private void putComputeNode(String node) {
    if (computenodes.containsKey(serverSlice.getNodeDomain(node))) {
      computenodes.get(serverSlice.getNodeDomain(node)).add(node);
      Collections.sort(computenodes.get(serverSlice.getNodeDomain(node)));
    } else {
      ArrayList<String> l = new ArrayList<>();
      l.add(node);
      computenodes.put(serverSlice.getNodeDomain(node), l);
    }
  }

  private void putEdgeRouter(String node) {
    if (edgeRouters.containsKey(serverSlice.getNodeDomain(node))) {
      edgeRouters.get(serverSlice.getNodeDomain(node)).add(node);
      Collections.sort(edgeRouters.get(serverSlice.getNodeDomain(node)));
    } else {
      ArrayList<String> l = new ArrayList<>();
      l.add(node);
      edgeRouters.put(serverSlice.getNodeDomain(node), l);
    }
  }
  /*
   * Load the topology information from exogeni with ahab, put the links, stitch_ports, and
   * normal stitches connecting nodes in other slices.
   * By default, routers has the pattern "c\\d+"
   * stitches: stitch_c0_20
   * brolink:
   */

  private void loadSdxNetwork(String routerpattern, String stitchportpattern, String
    bropattern) {
    logger.debug("Loading Sdx Network Topology");
    try {
      Pattern pattern = Pattern.compile(routerpattern);
      Pattern stitchpattern = Pattern.compile(stitchportpattern);
      Pattern bropatn = Pattern.compile(bropattern);
      //Nodes: Get all router information
      for (String node : serverSlice.getComputeNodes()) {
        if (pattern.matcher(node).find()) {
          putComputeNode(node);
          if (node.matches(eRouterPattern)) {
            putEdgeRouter(node);
          }
        } else if (bropatn.matcher(node).find()) {
          try {
            InterfaceNode2Net intf = (InterfaceNode2Net) serverSlice.getNodeInterfaces(node).toArray
              ()[0];
            String[] parts = intf.getLink().getName().split("_");
            String ip = parts[parts.length - 1];
            broManager.addBroInstance(node, IPPrefix + ip + ".2", 500000000);
          } catch (Exception e) {
          }
        }
      }
      logger.debug("get links from Slice");
      usedip = new HashSet<Integer>();
      HashSet<String> ifs = new HashSet<String>();
      // get all links, and then
      for (String i : serverSlice.getInterfaces()) {
        routingmanager.updateInterfaceMac(serverSlice.getNodeOfInterface(i),
          serverSlice.getLinkOfInterface(i),
          serverSlice.getMacAddressOfInterface(i)
        );
        if (i.contains("node") || i.contains("bro")) {
          continue;
        }
        logger.debug(i);
        logger.debug("linkname: " + serverSlice.getLinkOfInterface(i) + " bandwidth: " +
          serverSlice.getBandwidthOfLink(serverSlice.getLinkOfInterface(i)));
        if (ifs.contains(i) || !pattern.matcher(serverSlice.getNodeOfInterface(i)).find()) {
          logger.debug("continue");
          continue;
        }
        ifs.add(i);
        Link logLink = links.get(serverSlice.getLinkOfInterface(i));

        if (logLink == null) {
          logLink = new Link();
          logLink.setName(serverSlice.getLinkOfInterface(i));
          logLink.addNode(serverSlice.getNodeOfInterface(i));
          if (patternMatch(logLink.getLinkName(), stosVlanPattern) || patternMatch(logLink.getLinkName(),
            broLinkPattern)) {
            String[] parts = logLink.getLinkName().split("_");
            String ip = parts[parts.length - 1];
            usedip.add(Integer.valueOf(ip));
            logLink.setIP(IPPrefix + ip);
            logLink.setMask(mask);
          }
        } else {
          logLink.addNode(serverSlice.getNodeOfInterface(i));
        }
        logger.debug(serverSlice.getBandwidthOfLink(serverSlice.getLinkOfInterface(i)));
        links.put(serverSlice.getLinkOfInterface(i), logLink);
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
      for (String sp : serverSlice.getStitchPorts()) {
        logger.debug(sp);
        Matcher matcher = stitchpattern.matcher(sp);
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
    String mip = serverSlice.getManagementIP(nodeName);
    checkOVS(serverSlice, nodeName);
    checkScripts(serverSlice, nodeName);
    logger.debug(nodeName + " " + mip);
    updateOvsInterface(nodeName);
    String result = serverSlice.getDpid(nodeName, sshKey);
    logger.debug("Trying to get DPID of the router " + nodeName);
    while (result == null || !validDPID(result)) {
      updateOvsInterface(nodeName);
      result = serverSlice.getDpid(nodeName, sshKey);
    }
    result = result.replace("\n", "");
    logger.debug(String.format("Get router info %s %s %s", nodeName, mip, result));
    routingmanager.newRouter(nodeName, result, mip);
  }

  protected void configRouters(SliceManager slice) {
    ArrayList<Thread> tlist = new ArrayList<>();
    for (String node : slice.getComputeNodes()) {
      if (node.matches(routerPattern)) {
        try {
          Thread thread = new Thread() {
            @Override
            public void run() {
              configRouter(node);
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

  public String setMirror(String routerName, String source, String dst) {
    setMirror(routerName, source, dst, 100000000);
    return "Mirroring job submitted";
  }

  public String setMirror(String routerName, String source, String dst, long bw) {
    try {
      broManager.setMirrorAsync(routerName, source, dst, bw);
    } catch (Exception e) {

    }
    return "Mirroring job submitted";
  }

  public String delMirror(String dpid, String source, String dst) {
    String res = routingmanager.delMirror(SDNController, dpid, source, dst);
    res += "\n" + routingmanager.delMirror(SDNController, dpid, dst, source);
    return res;
  }

  private void configRouting() {
    logger.debug("Configurating Routing");
    if (plexusInSlice) {
      restartPlexus();
    }
    // run ovsbridge scritps to add the all interfaces to the ovsbridge br0, if new interface is
    // added to the ovs bridge, then we reset the controller?
    // FIXME: maybe this is not the best way to do.
    //add all interfaces other than eth0 to ovs bridge br0
    configRouters(serverSlice);

    routingmanager.waitTillAllOvsConnected(SDNController, serverSlice.mocked);

    logger.debug("setting up sttichports");
    HashSet<Integer> usedip = new HashSet<Integer>();
    HashSet<String> ifs = new HashSet<String>();
    for (String sp : stitchports) {
      logger.debug("Setting up stitchport " + sp);
      String[] parts = sp.split("-");
      String ip = parts[2].replace("__", "/").replace("_", ".");
      String nodeName = parts[1];
      String[] ipseg = ip.split("\\.");
      String gw = ipseg[0] + "." + ipseg[1] + "." + ipseg[2] + "." + parts[3];
      routingmanager.newExternalLink(sp, ip, nodeName, gw, SDNController);
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
          SDNController);
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
          SDNController,
          logLink.getCapacity());
      }
    }
    //set ovsdb address
    routingmanager.updateAllPorts(SDNController);
    routingmanager.setOvsdbAddr(SDNController);
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

  private void releaseIP(int ip) {
    iplock.lock();
    try {
      if (usedip.contains(ip)) {
        usedip.remove(ip);
        if (curip > ip) {
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
      String result = serverSlice.runCmdNode(
        getEchoTimeCMD() + "sudo ovs-ofctl dump-flows br0",
        routername);
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

  public void checkFlowTableForPair(String src, String dst, String p1, String p2) {
    routingmanager.checkFLowTableForPair(p1, p2, src, dst, sshKey, logger);
  }

  public int getNumRouteEntries(String routerName, String flowPattern) {
    String result = serverSlice.runCmdNode(
      getEchoTimeCMD() + "sudo ovs-ofctl dump-flows br0",
      routerName);
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

  public String logFlowTables(List<String> patterns, List<String> unwantedPatterns) {
    String res = "";
    for (String node : serverSlice.getComputeNodes()) {
      if (node.matches(routerPattern)) {
        res = res + String.format("\n--------------------\nFlow on node %s of slice %s:\n", node,
          sliceName);
        res = res + logFlowTables(node, patterns, unwantedPatterns);
      }
    }
    return res;
  }

  private String logFlowTables(String node, List<String> patterns, List<String> unwantedPatterns) {
    logger.debug("------------------");
    logger.debug(String.format("Flow table: %s - %s", sliceName, node));
    String result = serverSlice.runCmdNode(
      "sudo ovs-ofctl dump-flows br0",
      node);
    String[] parts = result.split("\n");
    String ret = "";
    for (String s : parts) {
      for(String upattern: unwantedPatterns){
          if(s.matches(upattern)){
            break;
          }
      }
      if (!patterns.isEmpty()) {
        for (String pattern : patterns) {
          if (s.matches(pattern)) {
            logger.debug(s);
            ret = ret + s + "\n";
            break;
          }
        }
      } else {
        ret = ret + s + "\n";
        logger.debug(s);
      }
    }
    logger.debug("------------------");
    return ret;
  }

  private void updateMacAddr() {
    for (String i : serverSlice.getInterfaces()) {
      routingmanager.updateInterfaceMac(serverSlice.getNodeOfInterface(i),
        serverSlice.getLinkOfInterface(i),
        serverSlice.getMacAddressOfInterface(i));
    }
  }

  String getEchoTimeCMD() {
    return "echo currentMillis:$(/bin/date \"+%s%3N\");";
  }
}

