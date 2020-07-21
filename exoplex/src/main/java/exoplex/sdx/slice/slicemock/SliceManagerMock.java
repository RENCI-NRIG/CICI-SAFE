package exoplex.sdx.slice.slicemock;


import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import exoplex.sdx.core.CoreProperties;
import exoplex.sdx.slice.Scripts;
import exoplex.sdx.slice.SliceManager;
import exoplex.sdx.slice.SliceProperties;
import exoplex.sdx.slice.exogeni.NodeBase;
import exoplex.sdx.slice.exogeni.NodeBaseInfo;
import exoplex.sdx.slice.exogeni.SiteBase;
import org.apache.commons.lang.time.DateUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.renci.ahab.libndl.Slice;
import org.renci.ahab.libndl.resources.common.ModelResource;
import org.renci.ahab.libndl.resources.request.*;
import org.renci.ahab.libtransport.*;
import org.renci.ahab.libtransport.util.SSHAccessTokenFileFactory;
import org.renci.ahab.libtransport.util.TransportException;
import org.renci.ahab.libtransport.util.UtilTransportException;
import org.renci.ahab.libtransport.xmlrpc.XMLRPCProxyFactory;
import org.renci.ahab.libtransport.xmlrpc.XMLRPCTransportException;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/*
Mocked Slice manager:
1. provide
1. work on existing slices only
 */
public class SliceManagerMock extends SliceManager implements Serializable {
  final static long DEFAULT_BW = 10000000;
  final static Logger logger = LogManager.getLogger(SliceManagerMock.class);
  private static final long serialVersionUID = 1L;
  private static final int COMMIT_COUNT = 5;
  private static final int INTERVAL = 10;
  static int dpidCount = 1;
  public HashMap<String, String> dpidMap = new HashMap<>();
  public HashMap<String, Integer> interfaceNumMap = new HashMap<>();
  private ReentrantLock lock = new ReentrantLock();
  private ISliceTransportAPIv1 sliceProxy;
  private SliceAccessContext<SSHAccessToken> sctx;
  private Slice slice;

  @Inject
  public SliceManagerMock(@Assisted("sliceName") String sliceName,
                          @Assisted("pem") String pemLocation,
                          @Assisted("key") String keyLocation,
                          @Assisted("controller") String controllerUrl,
                          @Assisted("ssh") String sshKey) {
    super(sliceName, pemLocation, keyLocation, controllerUrl, sshKey);
    this.sliceProxy = getSliceProxy(pemLocation, keyLocation, controllerUrl);
    refreshSshContext();
    this.slice = null;
    this.mocked = true;
  }

  public static Collection<String> getDomains() {
    return Slice.getDomains();
  }

  private static ISliceTransportAPIv1 getSliceProxy(String pem, String key, String controllerUrl) {
    ISliceTransportAPIv1 sliceProxy = null;
    try {
      //ExoGENI controller context
      ITransportProxyFactory ifac = new XMLRPCProxyFactory();
      TransportContext ctx = new PEMTransportContext("", pem, key);
      sliceProxy = ifac.getSliceProxy(ctx, new URL(controllerUrl));
    } catch (Exception e) {
      e.printStackTrace();
      assert (false);
    }
    return sliceProxy;
  }

  public void expectOneInterfaceDiff(String node, boolean add){

  }

  public void waitForInterfaces(String node){}

  public void writeToFile(String fileName) {
    File f = new File(fileName);
    try {
      ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(f));
      oos.writeObject(this);
      oos.flush();
      oos.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void loadFromFile(String fileName) {
    File f = new File(fileName);
    try {
      ObjectInputStream oos = new ObjectInputStream(new FileInputStream(f));
      SliceManagerMock sliceManagerMock = (SliceManagerMock) oos.readObject();
      this.slice = sliceManagerMock.slice;
      this.dpidMap = sliceManagerMock.dpidMap;
      this.interfaceNumMap = sliceManagerMock.interfaceNumMap;
      oos.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public Collection<String> getBroadcastLinks() {
    ArrayList<String> res = new ArrayList<>();
    for (BroadcastNetwork net : slice.getBroadcastLinks()) {
      res.add(net.getName());
    }
    return res;
  }

  private void refreshSshContext() {
    //SSH context
    sctx = new SliceAccessContext<>();
    try {
      SSHAccessTokenFileFactory fac;
      fac = new SSHAccessTokenFileFactory(sshKey + ".pub", false);
      SSHAccessToken t = fac.getPopulatedToken();
      sctx.addToken(SliceProperties.userName, SliceProperties.userName, t);
      sctx.addToken(SliceProperties.userName, t);
    } catch (UtilTransportException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  public void createSlice() {
    logger.info(String.format("create %s", sliceName));
    slice = Slice.create(sliceProxy, sctx, sliceName);
  }

  public boolean revokeStitch(String GUID) throws TransportException {
    return true;
  }

  public void permitStitch(String secret, String GUID) throws TransportException {
    int times = 0;
    while (times < COMMIT_COUNT) {
      try {
        //s1
        sliceProxy = getSliceProxy(pemLocation, keyLocation, controllerUrl);
        sliceProxy.permitSliceStitch(sliceName, GUID, secret);
        break;
      } catch (TransportException e) {
        // TODO Auto-generated catch block
        logger.warn("Failed to permit stitch, retry");
        times++;
        if (times == COMMIT_COUNT) {
          throw e;
        }
        try {
          Thread.sleep(INTERVAL * 1000);
        } catch (InterruptedException var6) {
          Thread.currentThread().interrupt();
        }
      }
    }
  }

  public String permitStitch(String GUID) throws TransportException {
    int times = 0;
    while (times < COMMIT_COUNT) {
      //s1
      String secret = RandomStringUtils.randomAlphabetic(10);
      return secret;
    }
    return null;
  }

  public void lockSlice() {
    lock.lock();
  }

  public void unLockSlice() {
    lock.unlock();
  }

  public void abort() {
    try {
      reloadSlice();
      lock.unlock();
    } catch (Exception e) {

    }
  }

  public void loadSlice() throws Exception {
    reloadSlice();
  }

  public void reloadSlice() throws Exception {
    int i = 0;
    sliceProxy = getSliceProxy(pemLocation, keyLocation, controllerUrl);
    do {
      try {
        slice = Slice.loadManifestFile(sliceProxy, sliceName);
        if (slice != null) {
          Date date = DateUtils.addDays(new Date(), extensionDays);
          renew(date);
          return;
        }
      } catch (XMLRPCTransportException e) {
        logger.warn(e.getMessage());
        try {
          Thread.sleep(INTERVAL * 1000 * (i + 1));
        } catch (InterruptedException var6) {
          Thread.currentThread().interrupt();
        }

      } catch (TransportException ex) {
        logger.warn(ex.getMessage());
        try {
          Thread.sleep(INTERVAL * 1000 * (i + 1));
        } catch (InterruptedException var6) {
          Thread.currentThread().interrupt();
        }
        sliceProxy = getSliceProxy(pemLocation, keyLocation, controllerUrl);
      }
      i++;
      sliceProxy = getSliceProxy(pemLocation, keyLocation, controllerUrl);
      refreshSshContext();
      slice = null;
    } while (i < COMMIT_COUNT);
    logger.error("failed to reload slice");
    throw new Exception(String.format("Unable to find %s among active slices", sliceName));
  }

  public void resetHostNames() {
    for (ComputeNode node : slice.getComputeNodes()) {
      runCmdByIP(String.format("sudo hostnamectl set-hostname %s-%s", sliceName, node.getName()),
        node.getManagementIP(), false);
    }
  }

  public String addComputeNode(String name) {
    logger.info(String.format("addComputeNode %s", name));
    this.slice.addComputeNode(name);
    return name;
  }

  public String stitchNetToNode(String netName, String nodeName) {
    Network net0 = (Network) slice.getResourceByName(netName);
    InterfaceNode2Net ifaceNode0 = (InterfaceNode2Net) net0.stitch(slice.getResourceByName(nodeName));
    return ifaceNode0.getName();
  }

  public String stitchNetToNode(String netName, String nodeName, String ip, String
    netmask) {
    Network net0 = (Network) slice.getResourceByName(netName);
    InterfaceNode2Net ifaceNode0 = (InterfaceNode2Net) net0.stitch(slice.getResourceByName(nodeName));
    ifaceNode0.setIpAddress(ip);
    ifaceNode0.setNetmask(netmask);
    return ifaceNode0.getName();
  }

  public String addComputeNode(
    String name, String nodeImageURL,
    String nodeImageHash, String nodeImageShortName, String nodeNodeType, String site,
    String nodePostBootScript) {
    ComputeNode node0 = this.slice.addComputeNode(name);
    node0.setImage(nodeImageURL, nodeImageHash, nodeImageShortName);
    node0.setNodeType(nodeNodeType);
    node0.setDomain(SiteBase.get(site));
    if (nodePostBootScript != null) {
      node0.setPostBootScript(nodePostBootScript);
    }
    return name;
  }

  public String addComputeNode(String site, String name) {
    logger.debug(String.format("Adding new compute node %s to slice %s", name, sliceName));
    if (slice == null) {
      createSlice();
    }
    NodeBaseInfo ninfo = NodeBase.getImageInfo(SliceProperties.CustomerVMVersion);
    String nodeImageShortName = ninfo.imageName;
    String nodeImageURL = ninfo.imageUrl;
    //http://geni-images.renci.org/images/standard/ubuntu/ub1304-ovs-opendaylight-v1.0.0.xml
    String nodeImageHash = ninfo.imageHash;
    String nodeNodeType = "XO Medium";
    String nodePostBootScript = Scripts.getCustomerScript();
    ComputeNode node0 = slice.addComputeNode(name);
    node0.setImage(nodeImageURL, nodeImageHash, nodeImageShortName);
    node0.setNodeType(nodeNodeType);
    node0.setDomain(SiteBase.get(site));
    node0.setPostBootScript(nodePostBootScript);
    return node0.getName();
  }

  public StorageNode addStorageNode(String name, long capacity, String mountpnt) {
    return this.slice.addStorageNode(name, capacity, mountpnt);
  }

  public String addStitchPort(String name, String label, String port, long bandwidth) {
    logger.info(String.format("addStitchPort %s %s %s %s", name, label, port, bandwidth));
    return this.slice.addStitchPort(name, label, port, bandwidth).getName();
  }

  public void stitchSptoNode(String spName, String nodeName) {
    StitchPort sp = (StitchPort) slice.getResourceByName(spName);
    ComputeNode node = (ComputeNode) slice.getResourceByName(nodeName);
    sp.stitch(node);
  }

  public String addBroadcastLink(String name, long bandwidth) {
    logger.info(String.format("addBroadcastLink %s %s", name, bandwidth));
    return this.slice.addBroadcastLink(name, bandwidth).getName();
  }

  public String addBroadcastLink(String name) {
    return this.addBroadcastLink(name, DEFAULT_BW);
  }

  public String attach(String nodeName, String linkName, String ip, String netmask) {
    ComputeNode node = null;
    BroadcastNetwork link = null;
    RequestResource obj;
    if ((obj = slice.getResourceByName(nodeName)) instanceof BroadcastNetwork) {
      link = (BroadcastNetwork) obj;
    } else {
      node = (ComputeNode) obj;
    }
    if ((obj = slice.getResourceByName(linkName)) instanceof BroadcastNetwork) {
      link = (BroadcastNetwork) obj;
    } else {
      node = (ComputeNode) obj;
    }

    InterfaceNode2Net ifaceNode1 = (InterfaceNode2Net) link.stitch(node);
    if (ip != null) {
      ifaceNode1.setIpAddress(ip);
      ifaceNode1.setNetmask(netmask);
    }
    return ifaceNode1.getName();
  }

  public String attach(String nodeName, String linkName) {
    ComputeNode node = null;
    BroadcastNetwork link = null;
    RequestResource obj;
    if ((obj = slice.getResourceByName(nodeName)) instanceof BroadcastNetwork) {
      link = (BroadcastNetwork) obj;
    } else {
      node = (ComputeNode) obj;
    }
    if ((obj = slice.getResourceByName(linkName)) instanceof BroadcastNetwork) {
      link = (BroadcastNetwork) obj;
    } else {
      node = (ComputeNode) obj;
    }

    InterfaceNode2Net ifaceNode1 = (InterfaceNode2Net) link.stitch(node);
    return ifaceNode1.getName();
  }

  public String getStitchingGUID(String netName) {
    return RandomStringUtils.randomAlphanumeric(40);
  }

  public String getComputeNode(String nm) {
    ComputeNode node = (ComputeNode) this.slice.getResourceByName(nm);
    return node.getName();
  }

  public Interface stitch(RequestResource r1, RequestResource r2) {
    return slice.stitch(r1, r2);
  }

  public void unstitch(String stitchLinkName, String customerSlice, String customerGUID) {
    BroadcastNetwork net = (BroadcastNetwork) slice.getResourceByName(stitchLinkName);
    String stitchNetReserveId = net.getStitchingGUID();
    try {
      sliceProxy.undoSliceStitch(sliceName, stitchNetReserveId, customerSlice,
        customerGUID);
    } catch (TransportException e) {
      e.printStackTrace();
    }
  }

  public String getName() {
    return sliceName;
  }

  public void setName(String sliceName) {
    this.sliceName = sliceName;
    slice.setName(sliceName);
  }

  public boolean isNewSlice() {
    return this.slice.isNewSlice();
  }

  public void commit(int count, int sleepInterval) throws XMLRPCTransportException {
    logger.debug("mocked commit operation");
  }

  public void commit() throws XMLRPCTransportException {
    logger.debug("mocked commit operation");
  }

  public void delete() {
    logger.debug("mocked delete slice operation");
  }

  public String enableSliceStitching(RequestResource r, String secret) {
    return secret;
  }

  public Collection<String> getAllResources() {
    return maptoNames(slice.getAllResources());
  }

  private Collection<String> maptoNames(Collection<ModelResource> resources) {
    ArrayList<String> res = new ArrayList<>();
    for (ModelResource resource : resources) {
      res.add(resource.getName());
    }
    return res;
  }

  public Collection<String> getInterfaces() {
    ArrayList<String> res = new ArrayList<>();
    for (Interface intf : slice.getInterfaces()) {
      res.add(intf.getName());
    }
    return res;
  }

  public Collection<String> getLinks() {
    ArrayList<String> res = new ArrayList<>();
    for (Network net : slice.getLinks()) {
      res.add(net.getName());
    }
    return res;
  }

  public Collection<String> getComputeNodes() {
    ArrayList<String> res = new ArrayList<>();
    for (ComputeNode node : slice.getComputeNodes()) {
      res.add(node.getName());
    }
    return res;
  }

  public Collection<String> getStitchPorts() {
    ArrayList<String> res = new ArrayList<>();
    for (StitchPort sp : slice.getStitchPorts()) {
      res.add(sp.getName());
    }
    return res;
  }

  public void refresh() {
    logger.debug("mocked refresh");
  }

  public void commitSlice() throws TransportException {
    commit();
  }

  public void commitAndWait() throws Exception {
    logger.debug("mocked commit and wait");
  }

  public boolean commitAndWait(int interval) throws Exception {
    logger.debug("mocked commit and wait");
    return true;
  }

  public boolean commitAndWait(int interval, List<String> resources) throws Exception {
    logger.debug("mocked commit and wait");
    return true;
  }

  public void waitTillActive() throws Exception {
    waitTillActive(INTERVAL);
  }

  public void waitTillActive(int interval) throws Exception {
    List<String> computeNodes = getComputeNodes().stream().collect
      (Collectors.toList());
    List<String> links = getBroadcastLinks().stream().collect(Collectors.toList());
    computeNodes.addAll(links);
    waitTillActive(interval, computeNodes);
  }

  public String getState(String resourceName) {
    logger.debug("mocked get state");
    return "Active";
  }

  public boolean waitTillActive(int interval, List<String> resources) throws Exception {
    logger.debug("mocked waitTillActive");
    return true;
  }

  public void copyFile2Slice(String lfile, String rfile, String privkey) {
    logger.debug("mocked copying file to slice");
  }

  public void copyFile2Slice(String lfile, String rfile, String privkey,
                             String patn) {
    logger.debug("mocked copying file to slice");
  }

  public String getManagementIP(String nodeName) {
    return ((ComputeNode) slice.getResourceByName(nodeName)).getManagementIP();
  }

  public void copyFile2Node(String lfile, String rfile, String privkey, String nodeName) {
    logger.debug("mocked copying file to node");
  }

  private void runCmdSlice(Set<ComputeNode> nodes, String cmd, String sshkey,
                           boolean repeat) {
    logger.debug(String.format("mocked run cmds: %s", cmd));
  }

  /**
   * @param mip
   * @param res
   * @param sshkey
   * @return true is there is uninstalled software
   */
  private boolean processCmdRes(String mip, String res, String sshkey) {
    return false;
  }

  public String runCmdNode(final String cmd, String nodeName, boolean repeat) {
    //Todo: mock results returned
    return "to be implemented";
  }

  public String runCmdNode(final String cmd, String nodeName) {
    //Todo: mock results returned
    return "to be implemented";
  }

  public List<ImmutablePair<String, String>> getPhysicalInterfaces(String nodeName) {
    String key = String.format("%s_%s", sliceName, nodeName);
    if (interfaceNumMap.containsKey(key)) {
      int num = interfaceNumMap.get(key);
      num++;
      interfaceNumMap.put(key, num);
    } else {
      interfaceNumMap.put(key, 1);
    }
    int num = interfaceNumMap.get(key);
    ArrayList<ImmutablePair<String, String>> res = new ArrayList<>();
    for (int i = 1; i <= num; i++) {
      res.add(new ImmutablePair<>("eth" + i, String.format("00:00:00:00:00:0" +
        "%s", i)));
    }
    return res;
  }

  public String runCmdByIP(final String cmd, String mip, boolean repeat) {
    //Todo: mock results returned
    return "to be implemented";
  }

  public void runCmdSlice(final String cmd, final String sshkey, final String pattern,
                          final boolean repeat) {
  }

  public void addLink(String linkName, String nodeName, long
    bw) {
    logger.info(String.format("addLink %s %s %s", linkName, nodeName, bw));
    ComputeNode node = (ComputeNode) slice.getResourceByName(nodeName);
    Network net = slice.addBroadcastLink(linkName, bw);
    InterfaceNode2Net ifaceNode0 = (InterfaceNode2Net) net.stitch(node);
  }

  public void removeLink(String linkName) {
    BroadcastNetwork net = (BroadcastNetwork) slice.getResourceByName(linkName);
    net.delete();
  }

  public void addLink(String linkName, String ip, String netmask, String nodeName, long
    bw) {
    logger.info(String.format("addLink %s %s %s %s %s", linkName, ip, netmask, nodeName, bw));
    ComputeNode node = (ComputeNode) slice.getResourceByName(nodeName);
    Network net = slice.addBroadcastLink(linkName, bw);
    InterfaceNode2Net ifaceNode0 = (InterfaceNode2Net) net.stitch(node);
    ifaceNode0.setIpAddress(ip);
    ifaceNode0.setNetmask(netmask);
  }

  public void addLink(String linkName, String ip1, String ip2, String netmask, String
    node1, String node2, long bw) {
    logger.info(String.format("addLink %s %s %s %s %s %s %s", linkName, ip1, ip2, netmask, node1, node2, bw));
    ComputeNode node_1 = (ComputeNode) slice.getResourceByName(node1);
    ComputeNode node_2 = (ComputeNode) slice.getResourceByName(node2);
    Network net = slice.addBroadcastLink(linkName, bw);
    InterfaceNode2Net ifaceNode0 = (InterfaceNode2Net) net.stitch(node_1);
    ifaceNode0.setIpAddress(ip1);
    ifaceNode0.setNetmask(netmask);
    InterfaceNode2Net ifaceNode1 = (InterfaceNode2Net) net.stitch(node_2);
    ifaceNode1.setIpAddress(ip2);
    ifaceNode1.setNetmask(netmask);
  }

  public void addLink(String linkName, String
    node1, String node2, long bw) {
    logger.info(String.format("addLink %s %s %s %s", linkName, node1, node2, bw));
    ComputeNode node_1 = (ComputeNode) slice.getResourceByName(node1);
    ComputeNode node_2 = (ComputeNode) slice.getResourceByName(node2);
    Network net = slice.addBroadcastLink(linkName, bw);
    InterfaceNode2Net ifaceNode0 = (InterfaceNode2Net) net.stitch(node_1);
    InterfaceNode2Net ifaceNode1 = (InterfaceNode2Net) net.stitch(node_2);
  }

  public String getNodeDomain(String nodeName) {
    ComputeNode node = (ComputeNode) slice.getResourceByName(nodeName);
    return node.getDomain();
  }

  public void addCoreEdgeRouterPair(String site, String router1, String router2, String linkname, long bw) {
    NodeBaseInfo ninfo = NodeBase.getImageInfo(SliceProperties.OVSVersion);
    String nodeImageShortName = ninfo.imageName;
    String nodeImageURL = ninfo.imageUrl;
    //http://geni-images.renci.org/images/standard/ubuntu/ub1304-ovs-opendaylight-v1.0.0.xml
    String nodeImageHash = ninfo.imageHash;
    String nodeNodeType = "XO Medium";
    String nodePostBootScript = Scripts.getOVSScript();
    String node0 = addComputeNode(router1, nodeImageURL,
      nodeImageHash, nodeImageShortName, nodeNodeType, site,
      nodePostBootScript);
    String node1 = addComputeNode(router2, nodeImageURL,
      nodeImageHash, nodeImageShortName, nodeNodeType, site,
      nodePostBootScript);
    String bronet = addBroadcastLink(linkname, bw);
    stitchNetToNode(bronet, node0);
    stitchNetToNode(bronet, node1);
  }

  public void addOvsRouter(String site, String router1) {
    NodeBaseInfo ninfo = NodeBase.getImageInfo(SliceProperties.OVSVersion);
    String nodeImageShortName = ninfo.imageName;
    String nodeImageURL = ninfo.imageUrl;
    //http://geni-images.renci.org/images/standard/ubuntu/ub1304-ovs-opendaylight-v1.0.0.xml
    String nodeImageHash = ninfo.imageHash;
    String nodeNodeType = "XO Medium";
    String nodePostBootScript = Scripts.getOVSScript();
    addComputeNode(router1, nodeImageURL,
      nodeImageHash, nodeImageShortName, nodeNodeType, site,
      nodePostBootScript);
  }

  public void addDocker(String siteName, String nodeName, String script, String size) {
    NodeBaseInfo ninfo = NodeBase.getImageInfo(NodeBase.UBUNTU16);
    String dockerImageShortName = ninfo.imageName;
    String dockerImageURL = ninfo.imageUrl;
    String dockerImageHash = ninfo.imageHash;
    String dockerNodeType = "XO Medium";
    ComputeNode node0 = this.slice.addComputeNode(nodeName);
    node0.setImage(dockerImageURL, dockerImageHash, dockerImageShortName);
    node0.setNodeType(dockerNodeType);
    node0.setDomain(siteName);
    String dockerScript = Scripts.preBootScripts() + Scripts.installDocker();
    node0.setPostBootScript(dockerScript + script);
  }

  public void addRiakServer(String siteName, String nodeName) {
    addDocker(siteName, nodeName, Scripts.getRiakPreBootScripts(), NodeBase.xoMedium);

  }

  public void addSafeServer(String siteName, String riakIp, String safeDockerImage, String
    safeServerScript) {
    addDocker(siteName, "safe-server", Scripts.getSafeScript_v1(riakIp, safeDockerImage,
      safeServerScript), NodeBase.xoMedium);
  }

  public void addPlexusController(String controllerSite, String name) {
    addDocker(controllerSite, name, Scripts.getPlexusScript(CoreProperties.getPlexusImage()), NodeBase.xoMedium);
  }

  //We always add the bro when we add the edge router
  public String addBro(String broname, String domain) {
    String broN = "Centos 7.4 Bro";
    String broURL =
      "http://geni-images.renci.org/images/standard/centos/centos7.4-bro-v1.0.4/centos7.4-demo.bro-v1.0.4.xml";
    String broHash = "50c973571fc6da95c3f70d0f71c9aea1659ff780";
    String broType = "XO Medium";
    ComputeNode bro = this.slice.addComputeNode(broname);
    bro.setImage(broURL, broHash, broN);
    bro.setDomain(domain);
    bro.setNodeType(broType);
    bro.setPostBootScript(Scripts.getBroScripts());
    return broname;
  }

  public void stitch(String RID, String customerName, String CID, String secret,
                     String newip) {
    logger.debug("mocked slice stitching");
  }

  public void configBroNode(String nodeName, String edgeRouter, String resourceDir, String
    SDNControllerIP, String serverurl, String sshkey) {
    // Bro uses 'eth1"
  }

  public String getDpid(String routerName, String sshkey) {
    String key = String.format("%s_%s", sliceName, routerName);
    if (dpidMap.containsKey(key)) {
      return dpidMap.get(key);
    }
    String dpid = RandomStringUtils.randomNumeric(16).toLowerCase();
    //TODO: return null
    dpidMap.put(key, dpid);
    return dpid;
  }

  public String addOVSRouter(String site, String name) {
    logger.debug(String.format("Adding new OVS router to slice %s on site %s", slice.getName(),
      site));
    NodeBaseInfo ninfo = NodeBase.getImageInfo(SliceProperties.OVSVersion);
    String nodeImageShortName = ninfo.imageName;
    String nodeImageURL = ninfo.imageUrl;
    //http://geni-images.renci.org/images/standard/ubuntu/ub1304-ovs-opendaylight-v1.0.0.xml
    String nodeImageHash = ninfo.imageHash;
    String nodeNodeType = "XO Medium";
    String nodePostBootScript = Scripts.getOVSScript();
    ComputeNode node0 = slice.addComputeNode(name);
    node0.setImage(nodeImageURL, nodeImageHash, nodeImageShortName);
    node0.setNodeType(nodeNodeType);
    node0.setDomain(SiteBase.get(site));
    node0.setPostBootScript(nodePostBootScript);
    return node0.getName();
  }

  public void printNetworkInfo() {
    //getLinks
    for (Network n : slice.getLinks()) {
      logger.debug(n.getLabel() + " " + n.getState());
    }
    //getInterfaces
    for (Interface i : slice.getInterfaces()) {
      InterfaceNode2Net inode2net = (InterfaceNode2Net) i;
      logger.debug("MacAddr: " + inode2net.getMacAddress());
      logger.debug("GUID: " + i.getGUID());
    }
    for (BroadcastNetwork link : slice.getBroadcastLinks()) {
      logger.debug(link.getName());
    }
  }

  public void printSliceInfo() {
    for (Network n : slice.getLinks()) {
      logger.info(n.getLabel() + " " + n.getState());
    }
    //getInterfaces
    for (Interface i : slice.getInterfaces()) {
      InterfaceNode2Net inode2net = (InterfaceNode2Net) i;
      logger.info("MacAddr: " + inode2net.getMacAddress());
      logger.info("GUID: " + i.getGUID());
    }
    for (ComputeNode node : slice.getComputeNodes()) {
      logger.info(node.getName() + node.getManagementIP());
      for (Interface i : node.getInterfaces()) {
        InterfaceNode2Net inode2net = (InterfaceNode2Net) i;
        logger.info("MacAddr: " + inode2net.getMacAddress());
        logger.info("GUID: " + i.getGUID());
      }
    }
  }

  public void deleteResource(String name) {
    slice.getResourceByName(name).delete();
  }

  public String getResourceByName(String name) {
    if (slice.getResourceByName(name) != null) {
      return slice.getResourceByName(name).getName();
    } else {
      return null;
    }
  }

  public Collection<String> getNodeInterfaces(String nodeName) {
    ArrayList<String> res = new ArrayList<>();
    for (Interface ifname : slice.getResourceByName(nodeName).getInterfaces()) {
      res.add(ifname.getName());
    }
    return res;
  }

  public String getNodeOfInterface(String ifName) {
    for (Interface iface : slice.getInterfaces()) {
      if (iface.getName().equals(ifName)) {
        InterfaceNode2Net interfaceNode2Net = (InterfaceNode2Net) iface;
        return interfaceNode2Net.getNode().getName();
      }
    }
    return null;
  }

  public String getLinkOfInterface(String ifName) {
    for (Interface iface : slice.getInterfaces()) {
      if (iface.getName().equals(ifName)) {
        InterfaceNode2Net interfaceNode2Net = (InterfaceNode2Net) iface;
        return interfaceNode2Net.getLink().getName();
      }
    }
    return null;
  }

  public String getMacAddressOfInterface(String ifName) {
    for (Interface iface : slice.getInterfaces()) {
      if (iface.getName().equals(ifName)) {
        InterfaceNode2Net interfaceNode2Net = (InterfaceNode2Net) iface;
        return interfaceNode2Net.getMacAddress();
      }
    }
    return null;
  }

  public Long getBandwidthOfLink(String linkName) {
    Network link = (Network) slice.getResourceByName(linkName);
    return link.getBandwidth();
  }

  public void sleep(int seconds) {
  }

  public void renew(Date newDate) {
  }

  public void renew() {
  }
}
