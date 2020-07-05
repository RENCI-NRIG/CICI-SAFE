package exoplex.sdx.core.vfc;

import aqt.PrefixUtil;
import aqt.Range;
import com.google.inject.Inject;
import exoplex.sdx.advertise.RouteAdvertise;
import exoplex.sdx.core.CoreProperties;
import exoplex.sdx.core.SdxManagerBase;
import exoplex.sdx.core.restutil.NotifyResult;
import exoplex.sdx.network.AbstractRoutingManager;
import exoplex.sdx.network.Link;
import exoplex.sdx.network.SdnUtil;
import exoplex.sdx.safe.SafeManager;
import exoplex.sdx.slice.SliceManager;
import exoplex.sdx.slice.vfc.VfcSliceManager;
import org.apache.logging.log4j.LogManager;
import org.json.JSONObject;
import safe.Authority;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class VfcSdxManager extends SdxManagerBase {

  static String eRouterPattern = "(vfc.*)";
  static String routerPattern = "(vfc.*)";

  @Inject
  public VfcSdxManager(Authority authority, AbstractRoutingManager routingManager) {
    super(authority, routingManager);
    logger = LogManager.getLogger(VfcSdxManager.class);
  }

  @Override
  public void loadSlice() throws Exception {
    serverSlice = sliceManagerFactory.create(
      coreProperties.getSliceName(),
      "",
      "",
      "",
      "");
    ((VfcSliceManager)serverSlice).loadSlice(coreProperties.getTopologyFile());
  }

  @Override
  public void initializeSdx() throws Exception {
    SDNController = coreProperties.getSdnControllerIp() + ":8080";
    OVSController = coreProperties.getSdnControllerIp() + ":6653";
    if (coreProperties.isSafeEnabled()) {
      if (coreProperties.isSafeInSlice()) {
        coreProperties.setSafeServerIp(serverSlice.getManagementIP("safe-server"));
      }
      safeManager = new SafeManager(coreProperties.getSafeServerIp(), coreProperties.getSafeKeyFile(),
        coreProperties.getSshKey(), true);
    }
  }

  @Override
  public void startSdxServer(CoreProperties coreProperties) throws Exception {
    this.coreProperties = coreProperties;
    if (coreProperties.getIpPrefix() != null) {
      computeIP(coreProperties.getIpPrefix());
    }
    loadSlice();
    initializeSdx();
    loadSdxNetwork(routerPattern);
    configRouting();
    if(coreProperties.getReset()) {
      logger.info("delete flows");
      delFlows();
      return;
    }
  }

  private void loadSdxNetwork(String routerpattern) {
    logger.debug("Loading Sdx Network Topology");
    try {
      //Nodes: Get all router information
      for (String node : serverSlice.getComputeNodes()) {
        if (node.matches(routerpattern)) {
          putComputeNode(node);
          if (node.matches(eRouterPattern)) {
            putEdgeRouter(node);
          }
        }
      }
      for(String linkName: serverSlice.getLinks()) {
        Link link = ((VfcSliceManager) serverSlice).getLink(linkName);
        if(link.getNodeA()!= null && link.getNodeB()!= null) {
          links.put(linkName, link);
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Override
  protected void configRouter(String nodeName) {
    logger.debug(String.format("Configuring router %s", nodeName));
    String result = serverSlice.getDpid(nodeName, coreProperties.getSshKey());
    routingManager.newRouter(nodeName, result, serverSlice.getController(nodeName)
      , null);
  }

  @Override
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

  private void configRouting() {
    logger.debug("Configurating Routing");
    // run ovsbridge scritps to add the all interfaces to the ovsbridge br0, if new interface is
    // added to the ovs bridge, then we reset the controller?
    // FIXME: maybe this is not the best way to do.
    //add all interfaces other than eth0 to ovs bridge br0
    configRouters(serverSlice);

    //routingManager.waitTillAllOvsConnected(SDNController, serverSlice.mocked);

    Set<String> keyset = links.keySet();
    //logger.debug(keyset);
    for (String k : keyset) {
      Link logLink = links.get(k);
      logger.debug("Setting up logLink " + logLink.getLinkName());
      logger.debug("Setting up logLink " + logLink.getLinkName());
      if (logLink.getIpPrefix().equals("")) {
        int ip_to_use = getAvailableIP();
        logLink.setIP(IPPrefix + ip_to_use);
        logLink.setMask(mask);
      }
      //logger.debug(logLink.nodea+":"+logLink.getIP(1)+" "+logLink.nodeb+":"+logLink.getIP(2));
      routingManager.newInternalLink(logLink.getLinkName(), logLink.getIP(1),
        logLink.getNodeA(), logLink.getIP(2), logLink.getNodeB(), logLink.getCapacity());;
    }
    routingManager.setOvsdbAddr();
  }

  private int getAvailableIP() {
    int ip_to_use;
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

  @Override
  public void delFlows() {
    routingManager.deleteAllFlows();
  }

  synchronized public JSONObject stitchVfc(
    String customerSafeKeyHash,
    String customerSlice,
    String site,
    String vlanTag,
    String gateway,
    String ip) throws Exception {
    long start = System.currentTimeMillis();
    JSONObject res = new JSONObject();
    res.put("ip", "");
    res.put("gateway", "");
    res.put("message", "");

    logger.info(logPrefix + "new stitch request from " + customerSafeKeyHash + " for " + coreProperties.getSliceName() + " at " +
     site);
    if (!coreProperties.isSafeEnabled() || safeManager.authorizeChameleonStitchRequest(customerSafeKeyHash
      , site, vlanTag)) {
      if (coreProperties.isSafeEnabled()) {
        logger.info("Authorized: stitch request for " + coreProperties.getSliceName());
      }
      String stitchName = ((VfcSliceManager)serverSlice).getStitchName(site, vlanTag);
      String node = ((VfcSliceManager)serverSlice).getNodeBySite(site);
      Link logLink = new Link();
      logLink.setName(stitchName);
      logLink.addNode(node);
      links.put(stitchName, logLink);
      res.put("ip", ip);
      res.put("gateway", gateway);
      res.put("reservID", stitchName);
      if (coreProperties.isSafeEnabled()) {
        res.put("safeKeyHash", safeManager.getSafeKeyHash());
      }
      routingManager.newExternalLink(
        logLink.getLinkName(),
        ip,
        logLink.getNodeA(),
        gateway);
      //routingManager.configurePath(ip,node.getName(),ip.split("/")[0],SDNController);
      logger.info(logPrefix + "stitching operation  completed, time elapsed(s): " + (System
        .currentTimeMillis() - start) / 1000);

      //update states
      String reserveId = site + vlanTag;
      stitchNet.put(reserveId, stitchName);
      if (!customerNodes.containsKey(customerSafeKeyHash)) {
        customerNodes.put(customerSafeKeyHash, new HashSet<>());
      }
      customerNodes.get(customerSafeKeyHash).add(reserveId);
      customerGateway.put(reserveId, gateway);
    } else {
      logger.info("Unauthorized: stitch request for " + coreProperties.getSliceName());
      res.put("message", String.format("Unauthorized stitch request from (%s, %s)",
        customerSafeKeyHash, customerSlice));
    }
    return res;
  }

  @Override
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
    }
    return notifyResult;
  }

  @Override
  synchronized public String connectionRequest(String self_prefix,
                                               String target_prefix, long bandwidth) throws Exception {
    logger.info(String.format("Connection request between %s and %s", self_prefix, target_prefix));
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
    if (!routingManager.findPath(n1, n2, bandwidth)) {
      return "Cannot find a path";
    } else {
      logger.debug("Available path found, configuring routes");
      if (routingManager.configurePath(self_prefix, n1, target_prefix, n2, findGatewayForPrefix
        (self_prefix), bandwidth)
      ) {
        logger.info(logPrefix + "Routing set up for " + self_prefix + " and " + target_prefix);
        logger.debug(logPrefix + "Routing set up for " + self_prefix + " and " + target_prefix);
      } else {
        logger.info(logPrefix + "Route for " + self_prefix + " and " + target_prefix +
          "Failed");
        logger.debug(logPrefix + "Route for " + self_prefix + " and " + target_prefix +
          "Failed");
        return "route not successfully configured";
      }
      if (routingManager.configurePath(target_prefix, n2, self_prefix, n1,
        findGatewayForPrefix
        (target_prefix), bandwidth)
      ) {
        logger.info(logPrefix + "Routing set up for " + self_prefix + " and " + target_prefix);
        logger.debug(logPrefix + "Routing set up for " + self_prefix + " and " + target_prefix);
        if(bandwidth > 0) {
          setQos(self_prefix, target_prefix, bandwidth);
          logger.info(String.format("Set qos rule for %s %s %s", self_prefix,
           target_prefix, bandwidth));
        }
        return "route configured";
      } else {
        logger.info(logPrefix + "Route for " + self_prefix + " and " + target_prefix +
          "Failed");
        logger.debug(logPrefix + "Route for " + self_prefix + " and " + target_prefix +
          "Failed");
        return "route not successfully configured";
      }
    }
  }

}
