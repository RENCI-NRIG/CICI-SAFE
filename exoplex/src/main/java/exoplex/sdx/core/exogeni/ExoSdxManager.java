package exoplex.sdx.core.exogeni;

import aqt.PrefixUtil;
import aqt.Range;
import com.google.inject.Inject;
import exoplex.common.utils.HttpUtil;
import exoplex.sdx.advertise.*;
import exoplex.sdx.bro.BroManager;
import exoplex.sdx.core.CoreProperties;
import exoplex.sdx.core.SdxManagerBase;
import exoplex.sdx.core.restutil.PeerRequest;
import exoplex.sdx.network.AbstractRoutingManager;
import exoplex.sdx.network.Link;
import exoplex.sdx.network.RoutingManager;
import exoplex.sdx.network.SdnUtil;
import exoplex.sdx.safe.SafeManager;
import exoplex.sdx.slice.SliceManager;
import exoplex.sdx.slice.SliceProperties;
import exoplex.sdx.slice.exogeni.SiteBase;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.logging.log4j.LogManager;
import org.json.JSONObject;
import org.renci.ahab.libndl.resources.request.InterfaceNode2Net;
import safe.Authority;
import safe.SdxRoutingSlang;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.PrintWriter;
import java.io.StringWriter;

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

public class ExoSdxManager extends SdxManagerBase {
  private static final String dpidPattern = "^[a-f0-9]{16}";
  static final String STITCHPORT_TACC = "http://geni-orca.renci.org/owl/ion" +
    ".rdf#AL2S/TACC/Cisco/6509/TenGigabitEthernet/1/1";
  static final String STITCHPORT_UC = "http://geni-orca.renci.org/owl/ion" +
    ".rdf#AL2S/Chameleon/Cisco/6509/GigabitEthernet/1/1";

  @Inject
  public ExoSdxManager(Authority authority, AbstractRoutingManager routingManager) {
    super(authority, routingManager);
    logger = LogManager.getLogger(ExoSdxManager.class);
  }

  public String getSDNControllerIP() {
    return coreProperties.getSdnControllerIp();
  }

  public String getSDNController() {
    return SDNController;
  }

  public String getManagementIP(String nodeName) {
    return (serverSlice.getManagementIP(nodeName));
  }

  private String getSafeServer() {
    return coreProperties.getSafeServer();
  }

  private String getSafeServerIP() {
    return coreProperties.getSafeServerIp();
  }

  public String getDPID(String rname) {
    return routingManager.getDPID(rname);
  }

  protected void configSdnControllerAddr(String ip) {
    coreProperties.setSdnControllerIp(ip);
    //SDNControllerIP="152.3.136.36";
    //logger.debug("plexuscontroler managementIP = " + SDNControllerIP);
    SDNController = coreProperties.getSdnControllerIp() + ":8080";
    OVSController = coreProperties.getSdnControllerIp() + ":6633";
  }

  @Override
  public void loadSlice() throws Exception {
    serverSlice = sliceManagerFactory.create(
      coreProperties.getSliceName(),
      coreProperties.getExogeniKey(),
      coreProperties.getExogeniKey(),
      coreProperties.getExogeniSm(),
      coreProperties.getSshKey());
    try {
      serverSlice.loadSlice();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Override
  public void initializeSdx() throws Exception {
    loadSlice();
    broManager = new BroManager(serverSlice, routingManager, this);
    logPrefix += "[" + coreProperties.getSliceName() + "]";
    checkSdxPrerequisites(serverSlice);

    if (coreProperties.isPlexusInSlice()) {
      configSdnControllerAddr(serverSlice.getManagementIP(plexusName));
    } else {
      configSdnControllerAddr(coreProperties.getSdnControllerIp());
    }
    if (coreProperties.isSafeEnabled()) {
      if (coreProperties.isSafeInSlice()) {
        coreProperties.setSafeServerIp(serverSlice.getManagementIP("safe-server"));
      }
      safeManager = new SafeManager(coreProperties.getSafeServerIp(), coreProperties.getSafeKeyFile(),
        coreProperties.getSshKey(), true);
    } else {
      safeManager = new SafeManager(coreProperties.getSafeServerIp(),
        coreProperties.getSafeKeyFile(), coreProperties.getSshKey(), false);
    }
    advertiseManager = new AdvertiseManager(safeManager.getSafeKeyHash(), safeManager);
    //configRouting(serverslice,OVSController,SDNController,"(c\\d+)","(sp-c\\d+.*)");
    loadSdxNetwork(routerPattern, stitchPortPattern, broPattern);
  }

  @Override
  public void startSdxServer(CoreProperties coreProperties) throws Exception {
    this.coreProperties = coreProperties;
    if (coreProperties.getIpPrefix() != null) {
      computeIP(coreProperties.getIpPrefix());
    }
    if (serverSlice == null) {
      loadSlice();
    }
    if (this.coreProperties.getReset()) {
      clearSdx();
    }
    initializeSdx();
    configRouting();
    startBro();
  }

  void clearSdx() throws Exception {
    boolean flag = false;
    for (String node : serverSlice.getComputeNodes()) {
      if (node.matches(broPattern)) {
        serverSlice.deleteResource(node);
        flag = true;
      } else {
        String nodeStitchingGUID = serverSlice.getStitchingGUID(node);
        try {
          serverSlice.revokeStitch(nodeStitchingGUID);
        } catch (Exception e) {
        }
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


  @Override
  public void delFlows() {
    //routingManager.deleteAllFlows(getSDNController());
    serverSlice.runCmdSlice(String.format("sudo ovs-ofctl %s del-flows br0", SliceProperties.OFP),
      coreProperties.getSshKey(),
      routerPattern,
      false);
  }

  public void delBridges() {
    routingManager = new RoutingManager();
    serverSlice.runCmdSlice(String.format("sudo ovs-vsctl %s del-br br0", SliceProperties.OFP),
      coreProperties.getSshKey(),
      routerPattern,
      false);
  }

  public void initBridges() {
    configRouters(serverSlice);
  }

  private boolean addLink(String linkName, String
    node1, String node2, long bw) {
    serverSlice.lockSlice();
    serverSlice.expectOneMoreInterface(node1);
    serverSlice.expectOneMoreInterface(node2);
    try {
      while (true) {
        serverSlice.addLink(linkName, node1, node2, bw);
        if (serverSlice.commitAndWait(10, Arrays.asList(linkName))) {
          serverSlice.waitForNewInterfaces(node1);
          serverSlice.waitForNewInterfaces(node2);
          break;
        }
        serverSlice.deleteResource(linkName);
        serverSlice.commitAndWait();
        serverSlice.refresh();
      }
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    } finally {
      serverSlice.unLockSlice();
    }
    updateMacAddr(node1);
    updateMacAddr(node2);
    return true;
  }

  private boolean addLink(String stitchName, String nodeName, long bw) {
    //TODO use another SliceManager module that mimic the addition of the stitch link
    logger.info(String.format("Adding link %s %s %s Mbps", stitchName, nodeName, bw / 1000000));
    if (serverSlice.getResourceByName(stitchName) != null) {
      return false;
    }
    serverSlice.lockSlice();
    serverSlice.refresh();
    serverSlice.expectOneMoreInterface(nodeName);
    try {
      int times = 1;
      while (true) {
        serverSlice.addLink(stitchName, nodeName, bw);
        if (serverSlice.commitAndWait(10, Arrays.asList(stitchName))) {
          serverSlice.waitForNewInterfaces(nodeName);
          break;
        } else {
          serverSlice.deleteResource(stitchName);
          serverSlice.commitAndWait();
          serverSlice.refresh();
          times++;
        }
      }
      updateMacAddr(nodeName);
      return true;
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    } finally {
      serverSlice.unLockSlice();
    }
  }

  private boolean addStitchPort(String spName, String nodeName, String stitchUrl, String vlan, long
    bw) {
    serverSlice.lockSlice();
    serverSlice.refresh();
    serverSlice.expectOneMoreInterface(nodeName);
    try {
      String node = serverSlice.getComputeNode(nodeName);
      while (true) {
        String mysp = serverSlice.addStitchPort(spName, vlan, stitchUrl, bw);
        serverSlice.stitchSptoNode(mysp, node);
        int newNum;
        if (serverSlice.commitAndWait(10, Arrays.asList(spName + "-net"))) {
          serverSlice.waitForNewInterfaces(nodeName);
          break;
        } else {
          serverSlice.deleteResource(spName);
          serverSlice.commitAndWait();
          serverSlice.refresh();
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    } finally {
      serverSlice.unLockSlice();
    }
    updateMacAddr(nodeName);
    return true;
  }

  @Override
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
   *
   * @param src
   * @param dest
   * @return
   */
  public String processPacketIn(String src, String dest) {
    logger.info(String.format("Packet in event: src %s dst: %s", src, dest));
    try {
      String res = this.connectionRequest(dest, src, 0);
      logger.info(res);
      return res;
    } catch (Exception e) {
      logger.warn(String.format("failed to enable connection for %s <-> %s",
        src, dest));
      return "failure";
    }
  }

  /*
  Params:
  params [serverURI, myNode, myAddress, urAddressPrefix]
   */
  synchronized private String processStitchCmd(String[] params) {
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
      jsonparams.put("cslice", coreProperties.getSliceName());
      jsonparams.put("creservid", node0_s2_stitching_GUID);
      jsonparams.put("secret", secret);
      jsonparams.put("gateway", myAddress);
      jsonparams.put("ip", urAddressPrefix);
      if (params.length > 2) {
        jsonparams.put("sdxnode", params[2]);
      }

      if (coreProperties.isSafeEnabled()) {
        if (!safeChecked) {
          if (serverSlice.getResourceByName("safe-server") != null) {
            coreProperties.setSafeServerIp(serverSlice.getManagementIP("safe-server"));
          }
          safeManager.setSafeServerIp(coreProperties.getSafeServerIp());
          safeChecked = true;
        }
        jsonparams.put("ckeyhash", safeManager.getSafeKeyHash());
      } else {
        jsonparams.put("ckeyhash", coreProperties.getSliceName());
      }
      logger.debug("Sending stitch request to Sdx server");
      serverSlice.expectOneMoreInterface(myNode);
      String r = HttpUtil.postJSON(serverURI + "sdx/stitchrequest", jsonparams);
      logger.debug(r);
      JSONObject res = new JSONObject(r);
      logger.info(logPrefix + "Got Stitch Information From Server:\n " + res.toString());
      if (!res.getBoolean("result")) {
        logger.warn(logPrefix + "stitch request failed");
      } else {
        links.put(l1.getLinkName(), l1);
        String gateway = urAddressPrefix.split("/")[0];
        serverSlice.waitForNewInterfaces(myNode);
        updateOvsInterface(myNode);
        routingManager.newExternalLink(l1.getLinkName(), ip, myNode, gateway);
        String remoteGUID = res.getString("reservID");
        String remoteSafeKeyHash = res.getString("safeKeyHash");
        //Todo: be careful when we want to unstitch from the link side.
        // as the net is virtual
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
        peerRequest.peerUrl = coreProperties.getServerUrl();
        String peerRes = HttpUtil.postJSON(serverURI + "sdx/peer",
          peerRequest.toJsonObject());
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
    //propagate routes
    ArrayList<RouteAdvertise> advertises = advertiseManager.getAllAdvertises();
    for (RouteAdvertise routeAdvertise : advertises) {
      if (!routeAdvertise.advertiserPID.equals(newPeer)) {
        if (!routeAdvertise.route.contains(newPeer.peerPID)) {
          RouteAdvertise propagateAdvertise = new RouteAdvertise(routeAdvertise, this.getPid());
          propagateRouteToPeer(propagateAdvertise, newPeer.peerPID);
        }
      }
    }
    //propagate Policies at new peering request
    for (PolicyAdvertise policyAdvertise : advertiseManager.getAllPolicies()) {
      propagatePolicyToPeer(policyAdvertise, newPeer.peerPID);
    }
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
    Thread thread = new Thread() {
      @Override
      public void run() {
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
      jsonparams.put("cslice", coreProperties.getSliceName());
      jsonparams.put("creservid", node0_s2_stitching_GUID);
      if (coreProperties.isSafeEnabled()) {
        jsonparams.put("ckeyhash", safeManager.getSafeKeyHash());
      } else {
        jsonparams.put("ckeyhash", coreProperties.getSliceName());
      }
      logger.debug("Sending unstitch request to Sdx server");
      String r = HttpUtil.postJSON(coreProperties.getServerUrl() + "sdx/undostitch", jsonparams);
      logger.debug(r);
      logger.info(logPrefix + "Unstitch result:\n " + r);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  public void unlockSlice() {
    serverSlice.unLockSlice();
  }

  @Override
  public JSONObject stitchRequest(
    String site,
    String customerSafeKeyHash,
    String customerSlice,
    String reserveId,
    String secret,
    String sdxnode,
    String gateway,
    String ip) throws Exception {
    long start = System.currentTimeMillis();
    JSONObject res = new JSONObject();
    res.put("ip", "");
    res.put("gateway", "");
    res.put("message", "");

    logger.info(logPrefix + "new stitch request from " + customerSlice + " for " + coreProperties.getSliceName() + " at " +
      "" + site);
    logger.debug("new stitch request for " + coreProperties.getSliceName() + " at " + site);
    //if(!coreProperties.isSafeEnabled() || authorizeStitchRequest(customer_slice,customerName,reserveId, safeKeyHash,
    if (!coreProperties.isSafeEnabled() || safeManager.authorizeStitchRequest(customerSafeKeyHash, customerSlice)) {
      if (coreProperties.isSafeEnabled()) {
        logger.info("Authorized: stitch request for " + coreProperties.getSliceName());
      }
      boolean addComputeNodeandEdgeRouter = false;
      String stitchname = null;
      String net = null;
      String node = null;
      serverSlice.loadSlice();
      if (sdxnode != null && serverSlice.getComputeNode(sdxnode) != null) {
        node = serverSlice.getComputeNode(sdxnode);
        stitchname = allocateStitchLinkName(ip, node);
        addLink(stitchname, node, coreProperties.getBw());
      } else if (sdxnode == null && edgeRouters.containsKey(site) && edgeRouters.get(site).size() > 0) {
        node = serverSlice.getComputeNode(edgeRouters.get(site).get(0));
        stitchname = allocateStitchLinkName(ip, node);
        addLink(stitchname, node, coreProperties.getBw());
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

        net = serverSlice.addBroadcastLink(stitchname, coreProperties.getBw());
        serverSlice.stitchNetToNode(net, node);

        serverSlice.commitAndWait(10, Arrays.asList(stitchname, node));
        serverSlice.unLockSlice();
        copyRouterScript(serverSlice, node);
        configRouter(node);
        logger.debug("Configured the new router in RoutingManager");
        addComputeNodeandEdgeRouter = true;
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
      if (coreProperties.isSafeEnabled()) {
        res.put("safeKeyHash", safeManager.getSafeKeyHash());
      }
      updateOvsInterface(node);
      routingManager.newExternalLink(logLink.getLinkName(),
        ip,
        logLink.getNodeA(),
        gateway);
      //routingManager.configurePath(ip,node.getName(),ip.split("/")[0],SDNController);
      logger.info(logPrefix + "stitching operation  completed, time elapsed(s): " + (System
        .currentTimeMillis() - start) / 1000);

      //update states
      stitchNet.put(reserveId, stitchname);
      if (!customerNodes.containsKey(customerSafeKeyHash)) {
        customerNodes.put(customerSafeKeyHash, new HashSet<>());
      }
      customerNodes.get(customerSafeKeyHash).add(reserveId);
      customerGateway.put(reserveId, gateway);
      if (addComputeNodeandEdgeRouter) {
        Pattern pattern = Pattern.compile(routerPattern);
        if (pattern.matcher(node).find()) {
          putComputeNode(node);
          if (node.matches(eRouterPattern)) {
            putEdgeRouter(node);
          }
        }
      }
    } else {
      logger.info("Unauthorized: stitch request for " + coreProperties.getSliceName());
      res.put("message", String.format("Unauthorized stitch request from (%s, %s)",
        customerSafeKeyHash, customerSlice));
    }
    return res;
  }

  public String undoStitch(
    String customerSafeKeyHash,
    String customerSlice,
    String customerReserveId) throws Exception {
    logger.debug("ndllib TestDriver: START");
    logger.info(String.format("Undostitch request from %s for (%s, %s)", customerSafeKeyHash,
      customerSlice, customerReserveId));
    if (customerNodes.containsKey(customerSafeKeyHash) && customerNodes.get(customerSafeKeyHash).contains(customerReserveId)) {

    } else {
      return String.format("%s in slice %s is not stitched to SDX", customerReserveId, customerSlice);
    }

    Long t1 = System.currentTimeMillis();

    serverSlice.loadSlice();
    try {
      serverSlice.lockSlice();
      String stitchLinkName = stitchNet.get(customerReserveId);
      String stitchNodeName = stitchLinkName.split("_")[1];

      logger.info("stitchLinkName=" + stitchLinkName);
      logger.info("stitchNodeName=" + stitchNodeName);

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
      routingManager.removeExternalLink(stitchName, stitchName.split("_")[1]);
      releaseIP(Integer.valueOf(stitchName.split("_")[2]));
      updateOvsInterface(stitchNodeName);
      logger.debug("Finished UnStitching, time elapsed: " + (t2 - t1) + "\n");
      logger.info("Finished UnStitching, time elapsed: " + (t2 - t1) + "\n");

      return "Finished Unstitching";
    } finally {
      serverSlice.unLockSlice();
    }
  }

  private String updateOvsInterface(String routerName) {
    String res = serverSlice.runCmdNode("/bin/bash ~/ovsbridge.sh " + OVSController,
      routerName, true);
    return res;
  }

  //TODO thread safe operation
  public String deployBro(String routerName) throws Exception {
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
    long brobw = coreProperties.getBroBw();
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
    String resource_dir = coreProperties.getResourceDir();
    serverSlice.configBroNode(broName, routerName, resource_dir, coreProperties.getSdnControllerIp(),
      coreProperties.getServerUrl(),
      coreProperties.getSshKey());
    updateOvsInterface(routerName);
    Link logLink = new Link();
    logLink.setName(getBroLinkName(broName, ip_to_use));
    logLink.addNode(router);
    usedip.add(ip_to_use);
    logLink.setIP(IPPrefix + ip_to_use);
    logLink.setMask(mask);
    routingManager.newExternalLink(logLink.getLinkName(),
      logLink.getIP(1),
      logLink.getNodeA(),
      logLink.getIP(2).split("/")[0]);
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
    String stitch = "stitch_" + nodeName + "_" + sname;
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
    return routingManager.getNeighbors(edgeRouterName).get(0);
  }

  @Override
  synchronized public String connectionRequest(String self_prefix,
                                               String target_prefix, long bandwidth) throws Exception {
    logger.info(String.format("Connection request between %s and %s", self_prefix, target_prefix));
    //String n1=computenodes.get(site1).get(0);
    //String n2=computenodes.get(site2).get(0);
    String targetHash = null;
    String cKeyHash = null;
    Range selfRange, targetRange;
    if (self_prefix.matches(SdnUtil.IP_PATTERN)) {
      selfRange = PrefixUtil.addressToRange(self_prefix);
    } else {
      selfRange = PrefixUtil.prefixToRange(self_prefix);
    }
    if (target_prefix.matches(SdnUtil.IP_PATTERN)) {
      targetRange = PrefixUtil.addressToRange(target_prefix);
    } else {
      targetRange = PrefixUtil.prefixToRange(target_prefix);
    }
    for (String prefix : prefixKeyHash.keySet()) {
      if (PrefixUtil.prefixToRange(prefix).covers(selfRange)) {
        cKeyHash = prefixKeyHash.get(prefix);
        self_prefix = prefix;
      }
      if (PrefixUtil.prefixToRange(prefix).covers(targetRange)) {
        targetHash = prefixKeyHash.get(prefix);
        target_prefix = prefix;
      }
    }
    if (cKeyHash == null) {
      RouteAdvertise advertise = advertiseManager.getAdvertise(self_prefix,
        target_prefix);
      if (advertise == null) {
        return String.format("prefix %s unrecognized.", self_prefix);
      } else {
        targetHash = advertise.ownerPID;
      }
    }
    if (targetHash == null) {
      RouteAdvertise advertise = advertiseManager.getAdvertise(target_prefix, self_prefix);
      if (advertise == null) {
        return String.format("prefix %s unrecognized.", target_prefix);
      } else {
        targetHash = advertise.ownerPID;
      }
    }
    if (coreProperties.isSafeEnabled()) {
      if (!safeManager.authorizeConnectivity(cKeyHash, self_prefix, targetHash,
        target_prefix)) {
        logger.info("Unauthorized connection request");
        return "Unauthorized connection request";
      } else {
        logger.info(String.format("Authorized connection request <%s, %s>",
          self_prefix, target_prefix));
      }
    }
    String n1 = routingManager.getEdgeRouterByGateway(prefixGateway.get(self_prefix));
    String n2 = null;
    if (prefixGateway.containsKey(target_prefix)) {
      n2 = routingManager.getEdgeRouterByGateway(prefixGateway.get(target_prefix));
    } else {
      RouteAdvertise advertise = advertiseManager.getAdvertise(target_prefix, self_prefix);
      if (advertise != null) {
        String peerNode = customerNodes.get(advertise.advertiserPID).iterator().next();
        n2 = routingManager.getEdgeRouterByGateway(customerGateway.get(peerNode));
      }
    }
    if (n1 == null || n2 == null) {
      return "Prefix unrecognized.";
    }
    boolean res = true;
    if (!routingManager.findPath(n1, n2, bandwidth)) {
      serverSlice.loadSlice();
      String link1 = allocateCLinkName();
      logger.debug(logPrefix + "Add link: " + link1);
      Link l1 = new Link();
      l1.setName(link1);
      l1.addNode(n1);
      l1.addNode(n2);
      l1.setMask(mask);
      long linkbw =(long)(1.5 * bandwidth);
      if(linkbw > 0) {
        addLink(link1, n1, n2, linkbw);
        l1.setCapacity(linkbw);
      } else {
        addLink(link1, n1, n2, coreProperties.getBw());
        l1.setCapacity(coreProperties.getBw());
      }
      links.put(link1, l1);
      int ip_to_use = getAvailableIP();
      l1.setIP(IPPrefix + ip_to_use);
      String param = "";
      int numPort1 = routingManager.getPortCount(SDNController, n1);
      int numPort2 = routingManager.getPortCount(SDNController, n2);
      updateOvsInterface(n1);
      updateOvsInterface(n2);
      while (routingManager.getPortCount(SDNController, n1) == numPort1
        || routingManager.getPortCount(SDNController, n2) == numPort2) {
        sleep(5);
        logger.debug("Wait for new port to be reported to sdn controller");
      }
      //TODO: why nodeb dpid could be null
      res = routingManager.newInternalLink(l1.getLinkName(),
        l1.getIP(1),
        l1.getNodeA(),
        l1.getIP(2),
        l1.getNodeB(),
        linkbw);
      logger.debug("Link added successfully");
    }
    //configure routing
    if (res) {
      logger.debug("Available path found, configuring routes");
      if (routingManager.configurePath(self_prefix, n1, target_prefix, n2,
        findGatewayForPrefix(self_prefix), bandwidth) &&
          routingManager.configurePath(target_prefix, n2, self_prefix, n1,
            findGatewayForPrefix(target_prefix), 0)) {
        logger.info(logPrefix + "Routing set up for " + self_prefix + " and " + target_prefix);
        logger.debug(logPrefix + "Routing set up for " + self_prefix + " and " + target_prefix);
        //TODO: auto select edge router
        //setMirror(n1, self_prefix, target_prefix, bandwidth);
        if (bandwidth > 0) {
          setQos(self_prefix, target_prefix, bandwidth);
        }
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

  synchronized public void setQos(String prefix1, String prefix2,
                                    long bandwidth)  {
    String n1 =
      routingManager.getEdgeRouterByGateway(prefixGateway.get(prefix1));
    String n2 =
      routingManager.getEdgeRouterByGateway(prefixGateway.get(prefix2));
    routingManager.setQos(SDNController, routingManager.getDPID(n1), prefix1,
      prefix2, bandwidth);
    routingManager.setQos(SDNController, routingManager.getDPID(n2), prefix2,
      prefix1, bandwidth);
  }

  public void reset() {
    delFlows();
    //delBridges();
    broManager.reset();
    logger.info("SDX network reset");
  }

  public synchronized String processPolicyAdvertise(PolicyAdvertise policyAdvertise) {
    if (!coreProperties.doRouteAdvertise()) {
      return "Safe routing disabled, no processing this request";
    }
    /*
    if(safeEnabled && !safeManager.authorizeOwnPrefix(policyAdvertise.ownerPID, policyAdvertise.srcPrefix)){
      logger.debug(String.format("%s doesn't own the source prefix %s", policyAdvertise.ownerPID,
          policyAdvertise.srcPrefix));
      return "Authorized policy advertise, the owner doesn't own the prefix";
    }
    */
    //Verify the owner owns the source IP prefix
    // route with both source and destination address, find matching pairs
    ImmutablePair<List<ForwardInfo>, List<AdvertiseBase>> newPair =
      advertiseManager.receiveOutboundPolicy(policyAdvertise);
    for(AdvertiseBase newAdvertise: newPair.getRight()) {
      if(newAdvertise instanceof PolicyAdvertise) {
        propagatePolicyAdvertise((PolicyAdvertise) newAdvertise);
      } else {
        propagateBgpAdvertise((RouteAdvertise) newAdvertise);
      }
    }
    for(ForwardInfo forwardInfo: newPair.getLeft()) {
      configFib(forwardInfo);
    }
    return String.format("%s: Config for %s routes and " +
      "propaget %s advertises", serverSlice.getName(),
      newPair.getLeft().size(), newPair.getRight().size());
  }

  public synchronized String processBgpAdvertise(RouteAdvertise routeAdvertise) {
    logger.debug(String.format("%s process bpg advertise %s", getSliceName(),
      routeAdvertise));
    if (!coreProperties.doRouteAdvertise()) {
      return "Safe routing disabled, not processing this request";
    }
    if (coreProperties.isSafeEnabled() && !safeManager.authorizeBgpAdvertise(routeAdvertise)) {
      logger.warn(String.format("Unauthorized routeAdvertise :%s", routeAdvertise));
      return "";
    }
    ImmutablePair<List<ForwardInfo>, RouteAdvertise> pair =
      advertiseManager.receiveAdvertise(routeAdvertise);
    for (ForwardInfo forwardInfo : pair.getLeft()) {
      configFib(forwardInfo);
    }
    if (pair.getRight() != null) {
      propagateBgpAdvertise(pair.getRight());
      return pair.getRight().toString();
    }
    return "no route to propagate";
  }

  void configFib(ForwardInfo forwardInfo) {
    String customerReservId =
      customerNodes.get(forwardInfo.neighborPid).iterator().next();
    String gateway = customerGateway.get(customerReservId);
    if (!gateway.equals(routingManager.getGateway(forwardInfo.destPrefix,
      forwardInfo.srcPrefix))) {
      String edgeNode = routingManager.getEdgeRouterByGateway(gateway);
      logger.debug(String.format("[%s] Debug Msg: configuring route for %s",
        getSliceName(), forwardInfo));
      if(forwardInfo.srcPrefix != null &&
        !forwardInfo.srcPrefix.equals(AdvertiseManager.DEFAULT_PREFIX)) {
        routingManager.configurePath(forwardInfo.destPrefix, forwardInfo.srcPrefix
          , edgeNode, gateway);
      } else {
        routingManager.configurePath(forwardInfo.destPrefix, edgeNode, gateway);
      }
    }
  }

  synchronized public PeerRequest processPeerRequest(PeerRequest peerRequest) {
    updatePeer(peerRequest);
    PeerRequest reply = new PeerRequest();
    reply.peerUrl = coreProperties.getServerUrl();
    reply.peerPID = safeManager.getSafeKeyHash();
    return reply;
  }

  private void propagatePolicyAdvertise(PolicyAdvertise advertise) {
    logger.debug(String.format("Propagate policy advertise %s", advertise));
    Thread t = new Thread() {
      @Override
      public void run() {
        for (String peer : peerUrls.keySet()) {
          propagatePolicyToPeer(advertise, peer);
        }
      }
    };
    t.start();
    try {
      t.join();
    } catch (Exception e) {
    }
  }

  private void propagatePolicyToPeer(PolicyAdvertise advertise, String peer) {
    if (!peer.equals(advertise.ownerPID) && !peer.equals(advertise.advertiserPID)) {
      if (!advertise.route.contains(peer)) {
        if ((advertise.srcPrefix == null
          || advertise.srcPrefix.equals(AdvertiseManager.DEFAULT_PREFIX))
          && safeManager.verifyAS(advertise.ownerPID, advertise
          .getDestPrefix(), peer, advertise.safeToken)) {
          advertisePolicyAsync(peerUrls.get(peer), advertise);
        } else if (advertise.srcPrefix != null && safeManager.verifyAS(advertise.ownerPID,
          advertise.getSrcPrefix(), advertise.getDestPrefix(), peer, advertise.safeToken)) {
          advertisePolicyAsync(peerUrls.get(peer), advertise);
        }
      }
    }
  }

  private void propagateBgpAdvertise(RouteAdvertise advertise) {
    advertise = new RouteAdvertise(advertise, getPid());
    logger.debug(String.format("[%s] Propagate route advertise %s",
      getSliceName(), advertise));
    //Thread t = new Thread() {
    //  @Override
    //  public void run() {
        for (String peer : peerUrls.keySet()) {
          propagateRouteToPeer(advertise, peer);
        }
    //  }
    //};
    //t.start();
    //try {
    //  t.join();
    //}catch (Exception e) {
    //  e.printStackTrace();
    //}
  }

  private void propagateRouteToPeer(RouteAdvertise advertise,
                                    String peer) {
    if (!peer.equals(advertise.ownerPID) && !peer.equals(advertise.advertiserPID)) {
      if (!advertise.route.contains(peer)) {
        if ((advertise.srcPrefix == null ||
          advertise.srcPrefix.equals(AdvertiseManager.DEFAULT_PREFIX))
          && safeManager.verifyAS(advertise.ownerPID,
          advertise
          .getDestPrefix(), peer, advertise.safeToken)) {
          logger.debug(String.format("propagate to peer %s the advertise %s",
            peer, advertise));
          String path = advertise.getFormattedPath();
          String[] params = new String[4];
          params[0] = advertise.getDestPrefix();
          params[1] = path;
          params[2] = peer;
          params[3] = advertise.safeToken;
          String token = safeManager.post(SdxRoutingSlang.postAdvertise, params);
          advertise.safeToken = token;
          advertiseBgpAsync(peerUrls.get(peer), advertise);
        } else if (advertise.srcPrefix != null && safeManager.verifyAS(advertise.ownerPID,
          advertise.getSrcPrefix(), advertise.getDestPrefix(), peer, advertise.safeToken)) {
          logger.debug(String.format("propagate to peer %s the advertise %s",
            peer, advertise));
          String path = advertise.getFormattedPath();
          String[] params = new String[5];
          params[0] = advertise.getSrcPrefix();
          params[1] = advertise.getDestPrefix();
          params[2] = path;
          params[3] = peer;
          params[4] = advertise.safeToken;
          //TODO: update Safe script and modify this part
          String token = safeManager.post(SdxRoutingSlang.postAdvertiseSD, params);
          advertise.safeToken = token;
          advertiseBgpAsync(peerUrls.get(peer), advertise);
        } else {
          logger.debug(String.format("Not propagating to %s", peer));
        }
      }
    }
  }

    private void revokePrefix(String customerSafeKeyHash, String prefix) {
      prefixGateway.remove(prefix);
      prefixKeyHash.remove(prefix);
      if (customerPrefixes.containsKey(customerSafeKeyHash)) {
      customerPrefixes.get(customerSafeKeyHash).remove(prefix);
    }
    routingManager.retriveRouteOfPrefix(prefix, SDNController);
  }

  @Override
  synchronized public String stitchChameleon(String site, String nodeName, String customer_keyhash,
                                             String stitchport, String vlan, String gateway, String ip, String creservid) {
    String res = "Stitch request unauthorized";
    String sdxsite = SiteBase.get(site);
    if (stitchport.toLowerCase().equals("tacc")) {
      stitchport = STITCHPORT_TACC;
    } else if (stitchport.toLowerCase().equals("uc")) {
      stitchport = STITCHPORT_UC;
    }
    if (!coreProperties.isSafeEnabled() || safeManager.authorizeChameleonStitchRequest(customer_keyhash, stitchport,
      vlan)) {
      //FIX ME: do stitching
      logger.info("Chameleon Stitch Request from " + customer_keyhash + " Authorized");
      try {
        boolean addComputeNodeandEdgeRouter = false;
        //FIX ME: do stitching
        logger.info(logPrefix + "Chameleon Stitch Request from " + customer_keyhash + " Authorized");
        serverSlice.loadSlice();
        if (nodeName != null) {
          logger.info(logPrefix + " nodename not null");
          nodeName = serverSlice.getComputeNode(nodeName);
        } else if (nodeName == null && edgeRouters.containsKey(sdxsite) && edgeRouters.get(sdxsite).size() > 0) {
          logger.info(logPrefix + " edge already exists");
          nodeName = serverSlice.getComputeNode(edgeRouters.get(sdxsite).get(0));
        } else {
          //if node not exists, add another node to the slice
          //add a node and configure it as a router.
          //later when a customer requests connection between site a and site b, we add another logLink to meet
          // the requirments
          logger.debug("No existing router at requested site, adding new router");
          nodeName = allcoateERouterName(sdxsite);
          serverSlice.addOVSRouter(sdxsite, nodeName);
          serverSlice.commitAndWait(10, Arrays.asList(nodeName));
          serverSlice.unLockSlice();
          copyRouterScript(serverSlice, nodeName);
          configRouter(nodeName);
          logger.debug("Configured the new router in RoutingManager");
          addComputeNodeandEdgeRouter = true;
        }
        String stitchname = "sp_" + nodeName + "_" + ip.replace("/", "_").replace(".", "_");
        logger.info(logPrefix + "Stitching to Chameleon {" + "stitchname: " + stitchname + " vlan:" +
          vlan + " stithport: " + stitchport + "}");
        addStitchPort(stitchname, nodeName, stitchport, vlan, coreProperties.getBw());
        updateOvsInterface(nodeName);
        //routingManager.replayCmds(routingManager.getDPID(nodeName));
        routingManager.newExternalLink(stitchname, ip, nodeName, gateway);
        if (addComputeNodeandEdgeRouter) {
          if (nodeName.matches(routerPattern)) {
            putComputeNode(nodeName);
            if (nodeName.matches(eRouterPattern)) {
              putEdgeRouter(nodeName);
            }
          }
        }

        //update states (Added on similar lines as for Exogeni Stitch)
        stitchNet.put(creservid, stitchname);
        if (!customerNodes.containsKey(customer_keyhash)) {
          customerNodes.put(customer_keyhash, new HashSet<>());
        }
        customerNodes.get(customer_keyhash).add(creservid);
        customerGateway.put(creservid, gateway);

        res = "Stitch operation Completed";
        logger.info(logPrefix + res);
      } catch (Exception e) {
        StringWriter errors = new StringWriter();
        e.printStackTrace(new PrintWriter(errors));
        logger.error(errors.toString());
        res = "Stitch request failed.\n SdxServer exception in commiting stitching opoeration";
      }
    } else {
      logger.info("Chameleon Stitch Request from " + customer_keyhash + " unauthorized");
    }
    logger.info(logPrefix + res);
    return res;
  }


  public String broload(String broip, double broload) throws Exception {
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
        + "ryu-manager --log-file ~/log --default-log-level 10 --verbose "
        + "ryu/ryu/app/rest_conf_switch.py ryu/ryu/app/rest_router.py "
        + "ryu/ryu/app/ofctl_rest.py %s\"\n";
      // Set public url to plexus server
      if (coreProperties.isPlexusInSlice()) {
        String publicUrl = "http://" + plexusip + ":8888/";
        coreProperties.setPublicUrl(publicUrl);
      }
      String sdxMonitorUrl = coreProperties.getPublicUrl() + "sdx/flow/packetin";
      //reuse ryu-manager option for sdx url
      script = String.format(script, String.format("--zapi-db-url %s", sdxMonitorUrl));
      //String script = "docker exec -d plexus /bin/bash -c  \"cd /root;pkill ryu-manager;
      // ryu-manager ryu/ryu/app/rest_router.py|tee log\"\n";
      serverSlice.runCmdByIP(script, plexusip, false);
    } else if(type.equals("rest_mirror")) {
      //rest router application with QoS table and mirror function
      logger.debug("Restarting Plexus Controller");
      logger.info(logPrefix + "Restarting Plexus Controller: " + plexusip);
      delFlows();
      String script = "sudo docker exec -d plexus /bin/bash -c  \"cd /root;pkill ryu-manager; "
        + "ryu-manager --log-file ~/log --default-log-level 10 --verbose "
        + "ryu/ryu/app/rest_conf_switch.py ryu/ryu/app/rest_router_mirror.py "
        + "ryu/ryu/app/rest_qos.py "
        + "ryu/ryu/app/ofctl_rest.py %s\"\n";
      // Set public url to plexus server
      if (coreProperties.isPlexusInSlice()) {
        String publicUrl = "http://" + plexusip + ":8888/";
        coreProperties.setPublicUrl(publicUrl);
      }
      String sdxMonitorUrl = coreProperties.getPublicUrl() + "sdx/flow/packetin";
      //reuse ryu-manager option for sdx url
      script = String.format(script, String.format("--zapi-db-url %s", sdxMonitorUrl));
      //String script = "docker exec -d plexus /bin/bash -c  \"cd /root;pkill ryu-manager;
      // ryu-manager ryu/ryu/app/rest_router.py|tee log\"\n";
      serverSlice.runCmdByIP(script, plexusip, false);
    }
  }

  public void restartPlexus() {
    coreProperties.setSdnControllerIp(serverSlice.getManagementIP(plexusName));
    //restartPlexus(coreProperties.getSdnControllerIp(), "rest_router");
    restartPlexus(coreProperties.getSdnControllerIp(), coreProperties.getSdnApp());
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
          if (logLink.getLinkName().matches(stosVlanPattern) || logLink.getLinkName().matches(
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
      updateMacAddr();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private boolean validDPID(String dpid) {
    if (dpid == null) {
      return false;
    }
    dpid = dpid.replace("\n", "").replace(" ", "");
    return dpid.length() >= 16;
  }

  protected void configRouter(String nodeName) {
    logger.debug(String.format("Configuring router %s", nodeName));
    String mip = serverSlice.getManagementIP(nodeName);
    checkOVS(serverSlice, nodeName);
    checkScripts(serverSlice, nodeName);
    logger.debug(nodeName + " " + mip);
    updateOvsInterface(nodeName);
    String result = serverSlice.getDpid(nodeName, coreProperties.getSshKey());
    logger.debug("Trying to get DPID of the router " + nodeName);
    while (result == null || !validDPID(result)) {
      updateOvsInterface(nodeName);
      result = serverSlice.getDpid(nodeName, coreProperties.getSshKey());
    }
    result = result.replace("\n", "");
    logger.debug(String.format("Get router info %s %s %s", nodeName, mip, result));
    routingManager.newRouter(nodeName, result, getSDNController(), mip);
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
    String res = routingManager.delMirror(SDNController, dpid, source, dst);
    res += "\n" + routingManager.delMirror(SDNController, dpid, dst, source);
    return res;
  }

  protected void configRouting() {
    logger.debug("Configurating Routing");
    if (coreProperties.isPlexusInSlice()) {
      restartPlexus();
    }
    // run ovsbridge scritps to add the all interfaces to the ovsbridge br0, if new interface is
    // added to the ovs bridge, then we reset the controller?
    // FIXME: maybe this is not the best way to do.
    //add all interfaces other than eth0 to ovs bridge br0
    configRouters(serverSlice);

    routingManager.waitTillAllOvsConnected(SDNController, serverSlice.mocked);

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
      routingManager.newExternalLink(sp, ip, nodeName, gw);
    }

    Set<String> keyset = links.keySet();
    //logger.debug(keyset);
    for (String k : keyset) {
      Link logLink = links.get(k);
      logger.debug("Setting up stitch " + logLink.getLinkName());
      if (k.matches(stosVlanPattern) || k.matches(broLinkPattern)) {
        usedip.add(Integer.valueOf(logLink.getIP(1).split("\\.")[2]));
        routingManager.newExternalLink(logLink.getLinkName(),
          logLink.getIP(1),
          logLink.getNodeA(),
          logLink.getIP(2).split("/")[0]);
      }
    }

    //To Emulate dynamic allocation of links, we don't use links whose name does't contain "link"
    for (String k : keyset) {
      Link logLink = links.get(k);
      logger.debug("Setting up logLink " + logLink.getLinkName());
      if (isValidLink(k)) {
        logger.debug("Setting up logLink " + logLink.getLinkName());
        if (logLink.getIpPrefix().equals("")) {
          int ip_to_use = getAvailableIP();
          logLink.setIP(IPPrefix + ip_to_use);
          logLink.setMask(mask);
        }
        //logger.debug(logLink.nodea+":"+logLink.getIP(1)+" "+logLink.nodeb+":"+logLink.getIP(2));
        routingManager.newInternalLink(logLink.getLinkName(),
          logLink.getIP(1),
          logLink.getNodeA(),
          logLink.getIP(2),
          logLink.getNodeB(),
          logLink.getCapacity());
      }
    }
    //set ovsdb address
    routingManager.updateAllPorts(SDNController);
    routingManager.setOvsdbAddr(SDNController);
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


  @Override
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

  @Override
  public void checkFlowTableForPair(String src, String dst, String p1, String p2) {
    routingManager.checkFLowTableForPair(p1, p2, src, dst, coreProperties.getSshKey(), logger);
  }

  @Override
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

  @Override
  public String logFlowTables(List<String> patterns, List<String> unwantedPatterns) {
    String res = "";
    for (String node : serverSlice.getComputeNodes()) {
      if (node.matches(routerPattern)) {
        res = res + String.format("\n--------------------\nFlow on node %s of slice %s:\n", node,
          coreProperties.getSliceName());
        res = res + logFlowTables(node, patterns, unwantedPatterns);
      }
    }
    return res;
  }

  private String logFlowTables(String node, List<String> patterns, List<String> unwantedPatterns) {
    logger.debug("------------------");
    logger.debug(String.format("Flow table: %s - %s", coreProperties.getSliceName(), node));
    String result = serverSlice.runCmdNode(
      "sudo ovs-ofctl dump-flows br0",
      node);
    String[] parts = result.split("\n");
    String ret = "";
    for (String s : parts) {
      for (String upattern : unwantedPatterns) {
        if (s.matches(upattern)) {
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

  protected void updateMacAddr() {
    HashMap<String, String> mac2EtherName = new HashMap<>();
    for(String node: serverSlice.getComputeNodes()) {
      if(node.matches(routerPattern)) {
        for(ImmutablePair<String, String> iface:
          serverSlice.getPhysicalInterfaces(node)) {
          mac2EtherName.put(iface.right, iface.left);
        }
      }
    }
    for (String i : serverSlice.getInterfaces()) {
      String node = serverSlice.getNodeOfInterface(i);
      String mac =serverSlice.getMacAddressOfInterface(i);
      routingManager.updateInterfaceMac(node,
        serverSlice.getLinkOfInterface(i),
        mac,
        mac2EtherName.get(mac));
    }
  }

  protected void updateMacAddr(String node) {
    HashMap<String, String> mac2EtherName = new HashMap<>();
      for(ImmutablePair<String, String> iface:
        serverSlice.getPhysicalInterfaces(node)) {
        mac2EtherName.put(iface.right, iface.left);
      }
    for (String i : serverSlice.getInterfaces()) {
      String nodeName = serverSlice.getNodeOfInterface(i);
      if(nodeName.equals(node)) {
        String mac = serverSlice.getMacAddressOfInterface(i);
        routingManager.updateInterfaceMac(node,
          serverSlice.getLinkOfInterface(i),
          mac,
          mac2EtherName.get(mac));
      }
    }
  }

  @Override
  public void logFib() {
    logger.info(serverSlice.getName() + "\n" + advertiseManager.logFib());
  }

  String getEchoTimeCMD() {
    return "echo currentMillis:$(/bin/date \"+%s%3N\");";
  }

  public void collectSupportBundle() {

  }
}

