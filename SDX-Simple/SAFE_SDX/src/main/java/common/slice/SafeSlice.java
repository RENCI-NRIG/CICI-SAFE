package common.slice;

import common.utils.Exec;
import common.utils.NetworkUtil;
import common.utils.ScpTo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.renci.ahab.libndl.Slice;
import org.renci.ahab.libndl.resources.common.ModelResource;
import org.renci.ahab.libndl.resources.request.*;
import org.renci.ahab.libndl.util.IP4Subnet;
import org.renci.ahab.libtransport.*;
import org.renci.ahab.libtransport.util.TransportException;
import org.renci.ahab.libtransport.xmlrpc.XMLRPCProxyFactory;
import org.renci.ahab.libtransport.xmlrpc.XMLRPCTransportException;
import sdx.core.SliceManager;

import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static cern.clhep.Units.s;

public class SafeSlice {
  private static final int COMMIT_COUNT = 5;
  private static final int INTERVAL = 10;
  public static final String VMVersion = "Ubuntu 17.10";
  private ReentrantLock lock = new ReentrantLock();
  final static long DEFAULT_BW = 10000000;
  final static Logger logger = LogManager.getLogger(SafeSlice.class);
  private ISliceTransportAPIv1 sliceProxy;
  private SliceAccessContext<SSHAccessToken> sctx;
  private String pemLocation;
  private String keyLocation;
  private String controllerUrl;
  private String sliceName;
  private Slice slice;
  private HashSet<String> reachableNodes = new HashSet<>();


  public SafeSlice(String pemLocation, String keyLocation, String controllerUrl, SliceAccessContext<SSHAccessToken> sctx) {
    this.pemLocation = pemLocation;
    this.keyLocation = keyLocation;
    this.controllerUrl = controllerUrl;
    this.sctx = sctx;
    this.sliceProxy = getSliceProxy(pemLocation, keyLocation, controllerUrl);
    this.slice = null;
  }

  public SafeSlice(String pemLocation, String keyLocation, String controllerUrl) {
    this.pemLocation = pemLocation;
    this.keyLocation = keyLocation;
    this.controllerUrl = controllerUrl;
    this.sliceProxy = getSliceProxy(pemLocation, keyLocation, controllerUrl);
    this.slice = null;
  }

  public SafeSlice(String sliceName, String pemLocation, String keyLocation, String controllerUrl) {
    this.sliceName = sliceName;
    this.pemLocation = pemLocation;
    this.keyLocation = keyLocation;
    this.controllerUrl = controllerUrl;
    this.sliceProxy = getSliceProxy(pemLocation, keyLocation, controllerUrl);
    this.slice = null;
  }

  public void lockSlice(){
    lock.lock();
  }

  public void unLockSlice(){
    lock.unlock();
  }

  public void abort(){
    try {
      reloadSlice();
      lock.unlock();
    }catch (Exception e){

    }
  }

  public void reloadSlice()throws Exception{
    int i=0;
    sliceProxy = getSliceProxy(pemLocation, keyLocation, controllerUrl);
    do {
      try {
        slice = Slice.loadManifestFile(sliceProxy, sliceName);
        if(slice != null) {
          return;
        }
      } catch (XMLRPCTransportException e) {
        logger.warn(e.getMessage());
        try {
          Thread.sleep((long) (INTERVAL * 1000));
        } catch (InterruptedException var6) {
          Thread.currentThread().interrupt();
        }

      } catch (TransportException ex) {
        logger.warn(ex.getMessage());
        try {
          Thread.sleep((long) (INTERVAL * 1000));
        } catch (InterruptedException var6) {
          Thread.currentThread().interrupt();
        }
      }
      i++;
      slice = null;
    }while (i<COMMIT_COUNT);
    logger.error("failed to reload slice");
    throw  new Exception(String.format("Unable to find %s among active slices", sliceName));

  }

  public static SafeSlice create(String sliceName, String pemLocation, String keyLocation, String controllerUrl, SliceAccessContext<SSHAccessToken>sctx) {
    logger.info(String.format("create %s", sliceName));
    SafeSlice safeSlice = new SafeSlice(pemLocation, keyLocation, controllerUrl, sctx);
    safeSlice.sliceName = sliceName;
    safeSlice.sliceProxy = getSliceProxy(pemLocation, keyLocation, controllerUrl);
    safeSlice.sctx = sctx;
    safeSlice.slice = Slice.create(safeSlice.sliceProxy, sctx, sliceName);
    return safeSlice;
  }

  public static Collection<String> getDomains() {
    return Slice.getDomains();
  }

  public static SafeSlice loadManifestFile(String sliceName, String pemLocation, String keyLocation, String controllerUrl)
      throws org.renci.ahab.libtransport.util.TransportException {
    logger.info(String.format("loadSlice %s", sliceName));
    SafeSlice s = new SafeSlice(pemLocation, keyLocation, controllerUrl);
    s.sliceName = sliceName;
    s.sliceProxy = getSliceProxy(pemLocation, keyLocation, controllerUrl);
    int i=0;
    do {
      try {
        s.slice = Slice.loadManifestFile(s.sliceProxy, sliceName);
        break;
      } catch (XMLRPCTransportException e) {
        logger.warn(e.getMessage());
        try {
          Thread.sleep((long) (INTERVAL * 1000));
        } catch (InterruptedException var6) {
          Thread.currentThread().interrupt();
        }

      } catch (TransportException ex) {
        logger.warn(ex.getMessage());
        try {
          Thread.sleep((long) (INTERVAL * 1000));
        } catch (InterruptedException var6) {
          Thread.currentThread().interrupt();
        }
      }
      i++;
    }while (i<COMMIT_COUNT);
    if(i==COMMIT_COUNT){
      logger.warn("failed to load slice, slice = null");
      s.slice = null;
    }
    return s;
  }

  public ComputeNode addComputeNode(String name) {
    logger.info(String.format("addComputeNode %s", name));
    return this.slice.addComputeNode(name);
  }

  public StorageNode addStorageNode(String name) {
    return this.slice.addStorageNode(name);
  }

  public StitchPort addStitchPort(String name, String label, String port, long bandwidth) {
    logger.info(String.format("addStitchPort %s %s %s %s", name, label, port, bandwidth));
    return this.slice.addStitchPort(name, label, port, bandwidth);
  }

  public BroadcastNetwork addBroadcastLink(String name, long bandwidth) {
    logger.info(String.format("addBroadcastLink %s %s", name, bandwidth));
    return this.slice.addBroadcastLink(name, bandwidth);
  }

  public BroadcastNetwork addBroadcastLink(String name) {
    return this.addBroadcastLink(name, DEFAULT_BW);
  }

  public RequestResource getResourceByName(String nm) {
    return this.slice.getResourceByName(nm);
  }

  public ComputeNode getComputeNode(String nm){
    ComputeNode node =(ComputeNode) this.slice.getResourceByName(nm);
    while( node ==null || node.getState() == null ||node.getManagementIP()==null){
      logger.debug(String.format("getComputeNode %s", nm));
      try {
        reloadSlice();
      }catch (Exception e){

      }
      node =(ComputeNode) this.slice.getResourceByName(nm);
    }
    return node;
  }

  public Interface stitch(RequestResource r1, RequestResource r2) {
    return slice.stitch(r1, r2);
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
    slice.commit(count, sleepInterval);
  }

  public void commit() throws XMLRPCTransportException {
    int i = 0;
    do {
      try {
        slice.commit();
        try {
          lock.unlock();
        }catch (Exception e){
          ;
        }
        return;
      } catch (XMLRPCTransportException var7) {
        logger.debug(var7.getMessage());
        logger.warn("Slice commit failed: sleeping for " + INTERVAL + " seconds. ");
        if (i >= COMMIT_COUNT) {
          throw var7;
        }
      } catch (Exception var8) {
        logger.debug(var8.getMessage());
        logger.warn("Slice commit failed: sleeping for " + INTERVAL + " seconds. ");
      }

      try {
        Thread.sleep((long) (INTERVAL * 1000));
      } catch (InterruptedException var6) {
        Thread.currentThread().interrupt();
      }
      ++i;
    } while (i < COMMIT_COUNT);
    abort();
  }

  public void delete() {
    int i=0;
    do {
      try {
        sliceProxy.deleteSlice(sliceName);
        break;
      } catch (XMLRPCTransportException e) {
        logger.warn(e.getMessage());
        if(e.getMessage().contains("unable to find slice")){
          break;
        }
        try {
          Thread.sleep((long) (INTERVAL * 1000));
        } catch (InterruptedException var6) {
          Thread.currentThread().interrupt();
        }

      } catch (TransportException ex) {
        logger.warn(ex.getMessage());
        try {
          Thread.sleep((long) (INTERVAL * 1000));
        } catch (InterruptedException var6) {
          Thread.currentThread().interrupt();
        }
      }
      i++;
    }while (i<COMMIT_COUNT);
  }

  public String enableSliceStitching(RequestResource r, String secret) {
    return slice.enableSliceStitching(r, secret);
  }

  public Collection<ModelResource> getAllResources() {
    return slice.getAllResources();
  }

  public Collection<Interface> getInterfaces() {
    return slice.getInterfaces();
  }

  public Collection<Network> getLinks() {
    return slice.getLinks();
  }

  public Collection<BroadcastNetwork> getBroadcastLinks() {
    return slice.getBroadcastLinks();
  }

  public Collection<Node> getNodes() {
    return slice.getNodes();
  }

  public Collection<ComputeNode> getComputeNodes() {
    return slice.getComputeNodes();
  }

  public Collection<StorageNode> getStorageNodes() {
    return slice.getStorageNodes();
  }

  public Collection<StitchPort> getStitchPorts() {
    return slice.getStitchPorts();
  }

  public String getState() {
    return "getState unimplimented";
  }

  public void setSliceProxy(ISliceTransportAPIv1 sliceProxy) {
    this.sliceProxy = sliceProxy;
    slice.setSliceProxy(sliceProxy);
  }

  public String getRequest() {
    return slice.getRequest();
  }

  public String getDebugString() {
    return this.slice.getDebugString();
  }

  public String getSliceGraphString() {
    return this.slice.getSliceGraphString();
  }

  public Collection<Interface> getInterfaces(RequestResource requestResource) {
    return null;
  }

  public boolean isNewRequest() {
    return false;
  }

  public void increaseComputeNodeCount(ComputeNode computeNode, int i) {
  }

  public void deleteComputeNode(ComputeNode computeNode, String uri) {
  }

  public void addStitch(ComputeNode computeNode, RequestResource r, Interface stitch) {
  }

  public IP4Subnet setSubnet(String ip, int mask) {
    return slice.setSubnet(ip, mask);
  }

  public void addStitch(StorageNode storageNode, RequestResource r, Interface stitch) {
  }

  public void addStitch(StitchPort stitchPort, RequestResource r, Interface stitch) {
  }

  public IP4Subnet allocateSubnet(int dEFAULT_SIZE) {
    return null;
  }

  public SliceAccessContext<? extends AccessToken> getSliceContext() {
    return slice.getSliceContext();
  }

  public void refresh() {
    slice.refresh();
  }

  public void commitSlice() throws TransportException {
    commit();
  }

  public void commitAndWait() throws TransportException, Exception {
    commit();
    reloadSlice();
    if(slice == null){
      throw new Exception(String.format("Failed to create slice %s", sliceName));
    }
    waitTillActive();
  }

  public boolean commitAndWait(int interval) throws TransportException, Exception {
    commit();
    String timeStamp1 = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date());
    waitTillActive(interval);
    String timeStamp2 = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date());
    logger.debug("Time interval: " + timeStamp1 + " " + timeStamp2);
    return true;
  }

  public boolean commitAndWait(int interval, List<String> resources) throws TransportException, Exception {
    commit();
    String timeStamp1 = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date());
    boolean res = waitTillActive(interval, resources);
    String timeStamp2 = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date());
    logger.debug("Time interval: " + timeStamp1 + " " + timeStamp2);
    return res;
  }

  public void waitTillActive() throws  Exception{
    waitTillActive(INTERVAL);
  }

  public void waitTillActive(int interval) throws  Exception {
    List<String> computeNodes = getComputeNodes().stream().map(c -> c.getName()).collect(Collectors.toList());
    List<String> links = getBroadcastLinks().stream().map(c -> c.getName()).collect(Collectors.toList());
    computeNodes.addAll(links);
    waitTillActive(interval, computeNodes);
  }


  public boolean waitTillActive(int interval, List<String> resources) throws Exception {
    logger.debug("Wait until following resources are active: " + String.join(",", resources));
    boolean sliceActive = false;
    while (true) {
      refresh();
      sliceActive = true;
      logger.debug("SafeSlice: " + getAllResources());
      for (ComputeNode c : getComputeNodes()) {
        logger.debug("Resource: " + c.getName() + ", state: " + c.getState());
        if (resources.contains(c.getName())) {
          if(c.getState().contains("Closed")){
            throw new Exception(String.format("Slice %s closed", sliceName));
          }
          if (!c.getState().equals("Active") || c.getManagementIP() == null) {
            sliceActive = false;
          }else{
            if(!reachableNodes.contains(c.getName()) && !NetworkUtil.checkReachability(c.getManagementIP())){
              logger.warn(String.format("Node %s (%s) unreachable", c.getName(), c.getManagementIP()));
              sliceActive = false;
            }else{
              reachableNodes.add(c.getName());
            }
          }
        }
      }
      for (Network l : getBroadcastLinks()) {
        logger.debug("Resource: " + l.getName() + ", state: " + l.getState());
        if (resources.contains(l.getName())) {
          if(l.getState().contains("Failed") || l.getState().contains("Closed")){
            return false;
          }
          if (!l.getState().equals("Active")) {
            sliceActive = false;
          }
        }
      }
      if (sliceActive) break;
      try {
        Thread.sleep(interval * 1000);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    logger.debug("Done, those  resources are active now: " + String.join(",", resources));
    for (ComputeNode n : getComputeNodes()) {
      logger.debug("ComputeNode: " + n.getName() + ", Managment IP =  " + n.getManagementIP());
    }
    return true;
  }

  public void copyFile2Slice(String lfile, String rfile, String privkey) {
    ArrayList<Thread> tlist = new ArrayList<Thread>();
    for (ComputeNode c : getComputeNodes()) {
      String mip = c.getManagementIP();
      try {
        Thread thread = new Thread() {
          @Override
          public void run() {
            try {
              logger.debug("scp config file to " + mip);
              ScpTo.Scp(lfile, "root", mip, rfile, privkey);
            } catch (Exception e) {
              e.printStackTrace();
            }
          }
        };
        thread.start();
        tlist.add(thread);
      } catch (Exception e) {
        logger.error("exception when copying config file");
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


  public void copyFile2Slice(String lfile, String rfile, String privkey,
                             String patn) {
    Pattern pattern = Pattern.compile(patn);
    ArrayList<Thread> tlist = new ArrayList<Thread>();
    for (ComputeNode c : getComputeNodes()) {
      Matcher matcher = pattern.matcher(c.getName());
      if (!matcher.find()) {
        continue;
      }
      String mip = c.getManagementIP();
      try {
        Thread thread = new Thread() {
          @Override
          public void run() {
            try {
              logger.debug("scp config file to " + mip);
              ScpTo.Scp(lfile, "root", mip, rfile, privkey);
            } catch (Exception e) {
              e.printStackTrace();
            }
          }
        };
        thread.start();
        tlist.add(thread);
      } catch (Exception e) {
        logger.error("exception when copying config file");
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


  public void copyFile2Node(String lfile, String rfile, String privkey, String nodeName) {
    String ip = getComputeNode(nodeName).getManagementIP();
    try {
      logger.debug(String.format("scp file %s to %s", lfile, ip));
      ScpTo.Scp(lfile, "root", ip, rfile, privkey);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void runCmdSlice(Set<ComputeNode> nodes, String cmd, String sshkey,
                           boolean repeat) {
    List<Thread> tlist = new ArrayList<Thread>();

    for (ComputeNode c : nodes) {
      String mip = c.getManagementIP();
      tlist.add(new Thread() {
        @Override
        public void run() {
          try {
            logger.debug(mip + " run commands: " + cmd);
            String res = Exec.sshExec("root", mip, cmd, sshkey)[0];
            while (res.startsWith("error") && repeat) {
              sleep(5);
              res = Exec.sshExec("root", mip, cmd, sshkey)[0];
            }
          } catch (Exception e) {
            logger.warn("exception when running command");
          }
        }
      });
    }

    for (Thread t : tlist)
      t.start();
    for (Thread t : tlist) {
      try {
        t.join();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }

  /**
   *
   * @param mip
   * @param res
   * @param sshkey
   * @return true is there is uninstalled software
   */
  private boolean processCmdRes(String mip, String res, String sshkey){
    if(res.contains("ovs-vsctl: command not found") || res.contains("ovs-ofctl: command not found")){
      String[] result = Exec.sshExec("root", mip, "apt install -y openvswitch-switch", sshkey);
      if(result[0].startsWith("error")){
        return true;
      }else {
        return false;
      }
    }
    return false;
  }

  public String runCmdNode(final String cmd, final String sshkey, String nodeName, boolean repeat){
    String mip = getComputeNode(nodeName).getManagementIP();
    logger.debug(mip + " run commands:" + cmd);
    String res[] = Exec.sshExec("root", mip, cmd, sshkey);
    if(res[1].length() > 0 && processCmdRes(mip, res[1], sshkey)) {
      res[0] = "error";
    }
    while (res[0].startsWith("error") && repeat) {
      try{
        Thread.sleep(2000);
      }catch (Exception e){
      }
      res = Exec.sshExec("root", mip, cmd, sshkey);
      if(res[1].length() > 0 && processCmdRes(mip, res[1], sshkey)) {
        res[0] = "error";
      }
    }
    return res[0];
  }

  public void runCmdSlice(final String cmd, final String sshkey, final String pattern,
                          final boolean repeat) {
    List<Thread> tlist = new ArrayList<Thread>();

    for (ComputeNode c : getComputeNodes()) {
      if (c.getName().matches(pattern)) {
        tlist.add(new Thread() {
          @Override
          public void run() {
            try {
              runCmdNode(cmd, sshkey, c.getName(), repeat);
            } catch (Exception e) {
              logger.warn("exception when running command");
            }
          }
        });
      }
    }
    for (Thread t : tlist)
      t.start();
    for (Thread t : tlist) {
      try {
        t.join();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }

  public void addLink(String linkName, String nodeName, long
      bw) {
    logger.info(String.format("addLink %s %s %s", linkName, nodeName, bw));
    ComputeNode node = (ComputeNode) slice.getResourceByName(nodeName);
    Network net = slice.addBroadcastLink(linkName, bw);
    InterfaceNode2Net ifaceNode0 = (InterfaceNode2Net) net.stitch(node);
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

  public void addCoreEdgeRouterPair(String site, String router1, String router2, String linkname, long bw) {
    NodeBaseInfo ninfo = NodeBase.getImageInfo(VMVersion);
    String nodeImageShortName = ninfo.nisn;
    String nodeImageURL = ninfo.niurl;
    //http://geni-images.renci.org/images/standard/ubuntu/ub1304-ovs-opendaylight-v1.0.0.xml
    String nodeImageHash = ninfo.nihash;
    String nodeNodeType = "XO Extra large";
    String nodePostBootScript = Scripts.getOVSScript();
    ComputeNode node0 = addComputeNode(router1, nodeImageURL,
        nodeImageHash, nodeImageShortName, nodeNodeType, site,
        nodePostBootScript);
    ComputeNode node1 = addComputeNode(router2, nodeImageURL,
        nodeImageHash, nodeImageShortName, nodeNodeType, site,
        nodePostBootScript);
    Network bronet = addBroadcastLink(linkname, bw);
    bronet.stitch(node0);
    bronet.stitch(node1);
  }

  private ComputeNode addComputeNode(
      String name, String nodeImageURL,
      String nodeImageHash, String nodeImageShortName, String nodeNodeType, String site,
      String nodePostBootScript) {
    ComputeNode node0 = slice.addComputeNode(name);
    node0.setImage(nodeImageURL, nodeImageHash, nodeImageShortName);
    node0.setNodeType(nodeNodeType);
    node0.setDomain(site);
    node0.setPostBootScript(nodePostBootScript);
    return node0;
  }

  public void addPlexusController(String controllerSite) {
    String dockerImageShortName = "Ubuntu 14.04 Docker";
    String dockerImageURL =
        "http://geni-images.renci.org/images/standard/docker/ubuntu-14.0.4/ubuntu-14.0.4-docker.xml";
    //http://geni-images.renci.org/images/standard/ubuntu/ub1304-ovs-opendaylight-v1.0.0.xml
    String dockerImageHash = "b4ef61dbd993c72c5ac10b84650b33301bbf6829";
    String dockerNodeType = "XO Large";
    ComputeNode node0 = addComputeNode("plexuscontroller");
    node0.setImage(dockerImageURL, dockerImageHash, dockerImageShortName);
    node0.setNodeType(dockerNodeType);
    node0.setDomain(controllerSite);
    node0.setPostBootScript(Scripts.getPlexusScript());
  }

  //We always add the bro when we add the edge router
  public ComputeNode addBro(String broname, String domain) {
    String broN = "Centos 7.4 Bro";
    String broURL =
        "http://geni-images.renci.org/images/standard/centos/centos7.4-bro-v1.0.4/centos7.4-bro-v1.0.4.xml";
    String broHash = "50c973571fc6da95c3f70d0f71c9aea1659ff780";
    String broType = "XO Medium";
    ComputeNode bro = addComputeNode(broname);
    bro.setImage(broURL, broHash, broN);
    bro.setDomain(domain);
    bro.setNodeType(broType);
    bro.setPostBootScript(Scripts.getBroScripts());

    /*
    Network bronet = s.addBroadcastLink(getBroLinkName(ip_to_use), bw);
    InterfaceNode2Net ifaceNode1 = (InterfaceNode2Net) bronet.stitch(edgerouter);
    ifaceNode1.setIpAddress("192.168." + String.valueOf(ip_to_use) + ".1");
    ifaceNode1.setNetmask("255.255.255.0");
    InterfaceNode2Net ifaceNode2 = (InterfaceNode2Net) bronet.stitch(bro);
    ifaceNode2.setIpAddress("192.168." + String.valueOf(ip_to_use) + ".2");
    ifaceNode2.setNetmask("255.255.255.0");
    */
    return bro;
  }
  public static ISliceTransportAPIv1 getSliceProxy(String pem, String key, String controllerUrl) {
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

  public void stitch(String RID, String customerName, String CID, String secret,
                     String newip) {
    logger.debug("ndllib TestDriver: START");
    //Main Example Code
    Long t1 = System.currentTimeMillis();
    try {
      //s2
      Properties p = new Properties();
      p.setProperty("ip", newip);
      sliceProxy.performSliceStitch(sliceName, RID, customerName, CID, secret, p);
    } catch (TransportException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    Long t2 = System.currentTimeMillis();
    logger.debug("Finished Stitching, set ip address of the new interface to " + newip + "  time elapsed: "
        + String.valueOf(t2 - t1) + "\n");
  }

  public void configBroNode(String nodeName, String edgeRouter, String resourceDir, String SDNControllerIP, String serverurl, String sshkey) {
    // Bro uses 'eth1"
    Exec.sshExec("root", getComputeNode(nodeName).getManagementIP(),"sed -i 's/eth0/eth1/' /opt/bro/etc/node.cfg", sshkey);

    copyFile2Node(resourceDir + "bro/test.bro", "/root/test" +
        ".bro", sshkey, nodeName);
    copyFile2Node(resourceDir + "bro/test-all-policy.bro", "/root/test-all-policy" +
        ".bro", sshkey, nodeName);
    copyFile2Node(resourceDir + "bro/detect.bro", "/root/detect" +
        ".bro", sshkey, nodeName);
    copyFile2Node(resourceDir + "bro/detect-all-policy.bro",
        "/root/detect-all-policy.bro", sshkey, nodeName);
    copyFile2Node(resourceDir + "bro/evil.txt", "/root/evil.txt", sshkey,
        nodeName);
    copyFile2Node(resourceDir + "bro/reporter.py", "/root/reporter.py", sshkey,
        nodeName);
    copyFile2Slice(resourceDir + "bro/cpu_percentage.sh", "/root/cpu_percentage.sh",
        sshkey, nodeName);

    Exec.sshExec("root", getComputeNode(nodeName).getManagementIP(), "sed -i 's/bogus_addr/" + SDNControllerIP + "/' *.bro",
        sshkey);

    String url = serverurl.replace("/", "\\/");
    Exec.sshExec("root", getComputeNode(nodeName).getManagementIP(),"sed -i 's/bogus_addr/" + url + "/g' reporter.py", sshkey);

    String dpid = getDpid(edgeRouter, sshkey)[1];
    Exec.sshExec("root", getComputeNode(nodeName).getManagementIP(), "sed -i 's/bogus_dpid/" + Long.parseLong
        (dpid, 16) + "/' *.bro", sshkey);
    Exec.sshExec("root", getComputeNode(nodeName).getManagementIP(), "broctl deploy&", sshkey);
    Exec.sshExec("root", getComputeNode(nodeName).getManagementIP(),"python reporter & disown", sshkey);
    Exec.sshExec("root", getComputeNode(nodeName).getManagementIP(),"/usr/bin/rm *.log; pkill bro; /usr/bin/screen -d -m /opt/bro/bin/bro " +
        "-i eth1 " + "test-all-policy.bro", sshkey);
  }

  public String[] getDpid(String routerName, String sshkey){
    String[] res = runCmdNode("/bin/bash ~/dpid.sh", sshkey, routerName, true).split(" ");
    res[1] = res[1].replace("\n", "");
    return  res;
  }
}
