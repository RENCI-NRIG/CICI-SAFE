package exoplex.sdx.core;

import com.google.inject.Inject;
import exoplex.common.utils.HttpUtil;
import exoplex.sdx.advertise.AdvertiseBase;
import exoplex.sdx.advertise.AdvertiseManager;
import exoplex.sdx.advertise.PolicyAdvertise;
import exoplex.sdx.advertise.RouteAdvertise;
import exoplex.sdx.bro.BroManager;
import exoplex.sdx.core.exogeni.SliceHelper;
import exoplex.sdx.core.restutil.NotifyResult;
import exoplex.sdx.core.restutil.PeerRequest;
import exoplex.sdx.network.AbstractRoutingManager;
import exoplex.sdx.network.Link;
import exoplex.sdx.network.RoutingManager;
import exoplex.sdx.safe.SafeManager;
import exoplex.sdx.slice.SliceManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import safe.Authority;
import safe.SdxRoutingSlang;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
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

public class SdxManagerBase extends SliceHelper implements SdxManagerInterface {
  protected static final String dpidPattern = "^[a-f0-9]{16}";
  protected Logger logger = LogManager.getLogger(SdxManagerBase.class);
  protected final ReentrantLock iplock = new ReentrantLock();
  protected final ReentrantLock linklock = new ReentrantLock();
  protected final ReentrantLock nodelock = new ReentrantLock();
  protected final ReentrantLock brolock = new ReentrantLock();
  protected HashMap<String, ArrayList<String>> edgeRouters = new HashMap<String, ArrayList<String>>();
  protected AbstractRoutingManager routingManager;
  protected AdvertiseManager advertiseManager;
  protected SafeManager safeManager = null;
  protected SliceManager serverSlice = null;
  protected BroManager broManager = null;
  protected String SDNController;
  protected String OVSController;
  protected int groupID = 0;
  protected String logPrefix = "";
  protected boolean safeChecked = false;
  protected HashSet<Integer> usedip = new HashSet<Integer>();

  protected ConcurrentHashMap<String, String> prefixGateway = new ConcurrentHashMap<String, String>();
  protected ConcurrentHashMap<String, HashSet<String>> gatewayPrefixes = new ConcurrentHashMap();
  protected ConcurrentHashMap<String, String> customerGateway = new ConcurrentHashMap<String,
    String>();

  protected ConcurrentHashMap<String, String> prefixKeyHash = new ConcurrentHashMap<String, String>();

  //the ip prefixes that a customer has advertised
  protected ConcurrentHashMap<String, HashSet<String>> customerPrefixes = new ConcurrentHashMap();

  //The node reservation IDs that a customer has stitched to SDX
  protected ConcurrentHashMap<String, HashSet<String>> customerNodes = new ConcurrentHashMap<>();

  protected ConcurrentHashMap<String, String> peerUrls = new ConcurrentHashMap<>();

  //The name of the link in SDX slice stitched to customer node
  protected ConcurrentHashMap<String, String> stitchNet = new ConcurrentHashMap<>();


  @Inject
  public SdxManagerBase(Authority authority, AbstractRoutingManager routingManager) {
    super(authority);
    this.routingManager = routingManager;
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

  public String getPid() {
    return safeManager.getSafeKeyHash();
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

  public void loadSlice() throws Exception {
  }

  public void initializeSdx() throws Exception {
  }

  public void startSdxServer(CoreProperties coreProperties) throws Exception {
  }

  public void delFlows() {}

  public String adminCmd(String operation, String[] params) {
    return "AdminCmd unimplemented";
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

  synchronized public JSONObject stitchRequest(
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
    return res;
  }

  synchronized public String undoStitch(String customerSafeKeyHash, String customerSlice,
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
    routingManager.removeExternalLink(stitchName, stitchName.split("_")[1]);
    releaseIP(Integer.valueOf(stitchName.split("_")[2]));
    updateOvsInterface(stitchNodeName);
    logger.debug("Finished UnStitching, time elapsed: " + (t2 - t1) + "\n");
    logger.info("Finished UnStitching, time elapsed: " + (t2 - t1) + "\n");

    return "Finished Unstitching";
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

  protected  String findGatewayForPrefix(String prefix) {
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
    return "not impelmented";
  }

  public void reset() {
    delFlows();
    //delBridges();
    broManager.reset();
    logger.info("SDX network reset");
  }

  public String processPolicyAdvertise(PolicyAdvertise policyAdvertise) {
    throw new NotImplementedException();
  }

  public String processBgpAdvertise(RouteAdvertise routeAdvertise) {
    throw new NotImplementedException();
  }

  synchronized public PeerRequest processPeerRequest(PeerRequest peerRequest) {
    updatePeer(peerRequest);
    PeerRequest reply = new PeerRequest();
    reply.peerUrl = coreProperties.getServerUrl();
    reply.peerPID = safeManager.getSafeKeyHash();
    return reply;
  }

  synchronized public NotifyResult notifyPrefix(String dest, String gateway,
                                                String customer_keyhash) {
    logger.info(logPrefix + "received notification for ip prefix " + dest);
    NotifyResult notifyResult = new NotifyResult();
    notifyResult.message = "received notification for " + dest;
    boolean flag = false;
    String router = routingManager.getEdgeRouterByGateway(gateway);
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
      if (coreProperties.isSafeEnabled()) {
        notifyResult.safeKeyHash = safeManager.getSafeKeyHash();
      }
      //monitor the frist package
      //routingManager.monitorOnAllRouter(dest, SdnUtil.DEFAULT_ROUTE);
      //routingManager.monitorOnAllRouter(SdnUtil.DEFAULT_ROUTE, dest);
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
    if (customerPrefixes.containsKey(customerSafeKeyHash)) {
      customerPrefixes.get(customerSafeKeyHash).remove(prefix);
    }
    routingManager.retriveRouteOfPrefix(prefix, SDNController);
  }

  synchronized public String stitchChameleon(String site, String nodeName, String customer_keyhash, String stitchport,
                                             String vlan, String gateway, String ip, String creservid) {
    String res = "Stitch request unauthorized";
    return res;
  }

  protected void putComputeNode(String node) {
    if (computenodes.containsKey(serverSlice.getNodeDomain(node))) {
      computenodes.get(serverSlice.getNodeDomain(node)).add(node);
      Collections.sort(computenodes.get(serverSlice.getNodeDomain(node)));
    } else {
      ArrayList<String> l = new ArrayList<>();
      l.add(node);
      computenodes.put(serverSlice.getNodeDomain(node), l);
    }
  }

  protected void putEdgeRouter(String node) {
    if (edgeRouters.containsKey(serverSlice.getNodeDomain(node))) {
      edgeRouters.get(serverSlice.getNodeDomain(node)).add(node);
      Collections.sort(edgeRouters.get(serverSlice.getNodeDomain(node)));
    } else {
      ArrayList<String> l = new ArrayList<>();
      l.add(node);
      edgeRouters.put(serverSlice.getNodeDomain(node), l);
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
    return null;
  }

  public void checkFlowTableForPair(String src, String dst, String p1, String p2) {
  }

  public int getNumRouteEntries(String routerName, String flowPattern) {
    int num = 0;
    return num;
  }

  public String logFlowTables(List<String> patterns, List<String> unwantedPatterns) {
    String res = "";
    return res;
  }

  public void logFib() {
  }

  String getEchoTimeCMD() {
    return "echo currentMillis:$(/bin/date \"+%s%3N\");";
  }

  public void collectSupportBundle() {
  }
}

