package exoplex.common.slice;

import exoplex.common.utils.Exec;
import exoplex.common.utils.NetworkUtil;
import exoplex.common.utils.PathUtil;
import exoplex.common.utils.ScpTo;
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

import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SliceManager {
  private static final int COMMIT_COUNT = 5;
  private static final int INTERVAL = 10;
  public static final String VMVersion = "Ubuntu 16.04";
  public static final String SafeVMVersion = "Ubuntu 14.04 Docker";
  public static final String CustomerVMVersion = "Ubuntu 14.04";
  private ReentrantLock lock = new ReentrantLock();
  final static long DEFAULT_BW = 10000000;
  final static Logger logger = LogManager.getLogger(SliceManager.class);
  private ISliceTransportAPIv1 sliceProxy;
  private SliceAccessContext<SSHAccessToken> sctx;
  private String pemLocation;
  private String keyLocation;
  private String controllerUrl;
  private String sliceName;
  private Slice slice;
  private HashSet<String> reachableNodes = new HashSet<>();


  public SliceManager(String pemLocation, String keyLocation, String controllerUrl, SliceAccessContext<SSHAccessToken> sctx) {
    this.pemLocation = pemLocation;
    this.keyLocation = keyLocation;
    this.controllerUrl = controllerUrl;
    this.sctx = sctx;
    this.sliceProxy = getSliceProxy(pemLocation, keyLocation, controllerUrl);
    this.slice = null;
  }

  public SliceManager(String pemLocation, String keyLocation, String controllerUrl) {
    this.pemLocation = pemLocation;
    this.keyLocation = keyLocation;
    this.controllerUrl = controllerUrl;
    this.sliceProxy = getSliceProxy(pemLocation, keyLocation, controllerUrl);
    this.slice = null;
  }

  public SliceManager(String sliceName, String pemLocation, String keyLocation, String controllerUrl) {
    this.sliceName = sliceName;
    this.pemLocation = pemLocation;
    this.keyLocation = keyLocation;
    this.controllerUrl = controllerUrl;
    this.sliceProxy = getSliceProxy(pemLocation, keyLocation, controllerUrl);
    this.slice = null;
  }

  public void permitStitch(String secret, String GUID) throws TransportException{
    int times = 0;
    while (times < COMMIT_COUNT) {
      try {
        //s1
        sliceProxy = getSliceProxy(pemLocation, keyLocation, controllerUrl);
        sliceProxy.permitSliceStitch(sliceName, GUID, secret);
        break;
      } catch (TransportException e) {
        // TODO Auto-generated catch block
        logger.warn( "Failed to permit stitch, retry");
        times ++;
        if(times == COMMIT_COUNT){
          throw e;
        }
        try {
          Thread.sleep((long) (INTERVAL * 1000));
        } catch (InterruptedException var6) {
          Thread.currentThread().interrupt();
        }
      }
    }
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
          Thread.sleep((long) (INTERVAL * 1000 * (i + 1)));
        } catch (InterruptedException var6) {
          Thread.currentThread().interrupt();
        }

      } catch (TransportException ex) {
        logger.warn(ex.getMessage());
        try {
          Thread.sleep((long) (INTERVAL * 1000 * (i + 1)));
        } catch (InterruptedException var6) {
          Thread.currentThread().interrupt();
        }
        sliceProxy = getSliceProxy(pemLocation, keyLocation, controllerUrl);
      }
      i++;
      slice = null;
    }while (i<COMMIT_COUNT);
    logger.error("failed to reload slice");
    throw  new Exception(String.format("Unable to find %s among active slices", sliceName));

  }

  public static SliceManager create(String sliceName, String pemLocation, String keyLocation, String controllerUrl, SliceAccessContext<SSHAccessToken>sctx) {
    logger.info(String.format("create %s", sliceName));
    SliceManager sliceManager = new SliceManager(pemLocation, keyLocation, controllerUrl, sctx);
    sliceManager.sliceName = sliceName;
    sliceManager.sliceProxy = getSliceProxy(pemLocation, keyLocation, controllerUrl);
    sliceManager.sctx = sctx;
    sliceManager.slice = Slice.create(sliceManager.sliceProxy, sctx, sliceName);
    return sliceManager;
  }

  public static Collection<String> getDomains() {
    return Slice.getDomains();
  }

  public static SliceManager loadManifestFile(String sliceName, String pemLocation, String keyLocation, String controllerUrl)
      throws org.renci.ahab.libtransport.util.TransportException {
    logger.info(String.format("loadSlice %s", sliceName));
    SliceManager s = new SliceManager(pemLocation, keyLocation, controllerUrl);
    s.sliceName = sliceName;
    s.sliceProxy = getSliceProxy(pemLocation, keyLocation, controllerUrl);
    s.pemLocation = pemLocation;
    s.keyLocation = keyLocation;
    s.controllerUrl = controllerUrl;
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
      s.sliceProxy = getSliceProxy(pemLocation, keyLocation, controllerUrl);
    }while (i<COMMIT_COUNT);
    if(i==COMMIT_COUNT){
      logger.warn("failed to load slice, slice = null");
      s.slice = null;
    }
    return s;
  }

  public void resetHostNames(String sshKey){
    for(ComputeNode node: slice.getComputeNodes()){
      runCmdByIP(String.format("hostnamectl set-hostname %s-%s", sliceName, node.getName()), sshKey,
        node.getManagementIP(), false);
    }
  }

  public ComputeNode addComputeNode(String name) {
    logger.info(String.format("addComputeNode %s", name));
    return this.slice.addComputeNode(name);
  }

  public ComputeNode addComputeNode(
      String name, String nodeImageURL,
      String nodeImageHash, String nodeImageShortName, String nodeNodeType, String site,
      String nodePostBootScript) {
    ComputeNode node0 = this.slice.addComputeNode(name);
    node0.setImage(nodeImageURL, nodeImageHash, nodeImageShortName);
    node0.setNodeType(nodeNodeType);
    node0.setDomain(SiteBase.get(site));
    if(nodePostBootScript != null) {
      node0.setPostBootScript(nodePostBootScript);
    }
    return node0;
  }

  public ComputeNode addComputeNode(String site, String name) {
    logger.debug(String.format("Adding new compute node %s to slice %s", name, slice.getName()));
    NodeBaseInfo ninfo = NodeBase.getImageInfo(CustomerVMVersion);
    String nodeImageShortName = ninfo.nisn;
    String nodeImageURL = ninfo.niurl;
    //http://geni-images.renci.org/images/standard/ubuntu/ub1304-ovs-opendaylight-v1.0.0.xml
    String nodeImageHash = ninfo.nihash;
    String nodeNodeType = "XO Medium";
    String nodePostBootScript = Scripts.getCustomerScript();
    ComputeNode node0 = slice.addComputeNode(name);
    node0.setImage(nodeImageURL, nodeImageHash, nodeImageShortName);
    node0.setNodeType(nodeNodeType);
    node0.setDomain(SiteBase.get(site));
    node0.setPostBootScript(nodePostBootScript);
    return node0;
  }

  public StorageNode addStorageNode(String name, long capacity, String mountpnt) {
    return this.slice.addStorageNode(name, capacity, mountpnt);
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

  public Interface attach(String nodeName, String linkName, String ip, String netmask){
    ComputeNode node = null;
    BroadcastNetwork link = null;
    RequestResource obj;
    if((obj = slice.getResourceByName(nodeName)) instanceof  BroadcastNetwork){
      link = (BroadcastNetwork) obj;
    }else {
      node = (ComputeNode) obj;
    }
    if((obj = slice.getResourceByName(linkName)) instanceof  BroadcastNetwork){
      link = (BroadcastNetwork) obj;
    }else {
      node = (ComputeNode) obj;
    }

    InterfaceNode2Net ifaceNode1 = (InterfaceNode2Net) link.stitch(node);
    if(ip!= null) {
      ifaceNode1.setIpAddress(ip);
      ifaceNode1.setNetmask(netmask);
    }
    return ifaceNode1;
  }

  public Interface attach(String nodeName, String linkName){
    ComputeNode node = null;
    BroadcastNetwork link = null;
    RequestResource obj;
    if((obj = slice.getResourceByName(nodeName)) instanceof  BroadcastNetwork){
      link = (BroadcastNetwork) obj;
    }else {
      node = (ComputeNode) obj;
    }
    if((obj = slice.getResourceByName(linkName)) instanceof  BroadcastNetwork){
      link = (BroadcastNetwork) obj;
    }else {
      node = (ComputeNode) obj;
    }

    InterfaceNode2Net ifaceNode1 = (InterfaceNode2Net) link.stitch(node);
    return ifaceNode1;
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
    reloadSlice();
    while (true) {
      ArrayList<String> activeResources = new ArrayList<>();
      refresh();
      logger.debug("SliceManager: " + getAllResources());
      for (ComputeNode c : getComputeNodes()) {
        logger.debug("[" + sliceName + "] Resource: " + c.getName() + ", state: " + c
                .getState());
        if (resources.contains(c.getName())) {
          if(c.getState().contains("Closed")){
            throw new Exception(String.format("Slice %s closed", sliceName));
          }
          if (!c.getState().equals("Active") || c.getManagementIP() == null) {
          }else{
            if(!reachableNodes.contains(c.getName()) && !NetworkUtil.checkReachability(c.getManagementIP())){
              logger.warn(String.format("Node %s (%s) unreachable", c.getName(), c.getManagementIP()));
            }else{
              activeResources.add(c.getName());
              reachableNodes.add(c.getName());
            }
          }
        }
      }
      for (Network l : getBroadcastLinks()) {
        logger.debug("Resource: " + l.getName() + ", state: " + l.getState());
        if (resources.contains(l.getName())) {
          if(l.getState().contains("Failed") || l.getState().contains("Closed")){
            logger.warn(String.format("link %s failed or closed", l.getName()));
            return false;
          }
          if (l.getState().equals("Active")) {
            activeResources.add(l.getName());
          }
          if (l.getState().equals("Null")) {
            activeResources.add(l.getName());
            logger.warn(String.format("%s state: Null", l.getName()));
          }
        }
      }
      if (activeResources.containsAll(resources)) break;
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
      String[] result = Exec.sshExec("root", mip, "apt-get install -y openvswitch-switch", sshkey);
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
    return runCmdByIP(cmd, sshkey, mip, repeat);
  }

  public String runCmdByIP(final String cmd, final String sshkey, String mip, boolean repeat){
    logger.debug(mip + " run commands:" + cmd);
    String res[] = Exec.sshExec("root", mip, cmd, sshkey);
    while(repeat && (res[0]==null ||res[0].startsWith("error"))){
      logger.debug(res[1]);
      res = Exec.sshExec("root", mip, cmd, sshkey);
      if(res[0].startsWith("error")){
        try{
          Thread.sleep(1000);
        }catch (Exception e){}
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
    String nodeNodeType = "XO Medium";
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

  public void addOvsRouter(String site, String router1) {
    NodeBaseInfo ninfo = NodeBase.getImageInfo(VMVersion);
    String nodeImageShortName = ninfo.nisn;
    String nodeImageURL = ninfo.niurl;
    //http://geni-images.renci.org/images/standard/ubuntu/ub1304-ovs-opendaylight-v1.0.0.xml
    String nodeImageHash = ninfo.nihash;
    String nodeNodeType = "XO Medium";
    String nodePostBootScript = Scripts.getOVSScript();
    ComputeNode node0 = addComputeNode(router1, nodeImageURL,
        nodeImageHash, nodeImageShortName, nodeNodeType, site,
        nodePostBootScript);
  }

  public void addDocker(String siteName, String nodeName, String script, String size){
    NodeBaseInfo ninfo = NodeBase.getImageInfo(NodeBase.Docker);
    String dockerImageShortName = ninfo.nisn;
    String dockerImageURL = ninfo.niurl;
    String dockerImageHash = ninfo.nihash;
    String dockerNodeType = "XO Medium";
    ComputeNode node0 = addComputeNode(nodeName);
    node0.setImage(dockerImageURL, dockerImageHash, dockerImageShortName);
    node0.setNodeType(dockerNodeType);
    node0.setDomain(siteName);
    node0.setPostBootScript(script);
  }

  public void addRiakServer(String siteName, String nodeName){
    addDocker(siteName, nodeName, Scripts.getRiakPreBootScripts(), NodeBase.xoMedium);

  }

  public void addSafeServer(String siteName,  String riakIp, String safeDockerImage, String
    safeServerScript) {
    addDocker(siteName, "safe-server", Scripts.getSafeScript_v1(riakIp, safeDockerImage,
      safeServerScript), NodeBase.xoMedium);
  }

  public void addPlexusController(String controllerSite, String name) {
    addDocker(controllerSite, name, Scripts.getPlexusScript(), NodeBase.xoMedium);
  }

  //We always add the bro when we add the edge router
  public ComputeNode addBro(String broname, String domain) {
    String broN = "Centos 7.4 Bro";
    String broURL =
        "http://geni-images.renci.org/images/standard/centos/centos7.4-bro-v1.0.4/centos7.4-demo.bro-v1.0.4.xml";
    String broHash = "50c973571fc6da95c3f70d0f71c9aea1659ff780";
    String broType = "XO Medium";
    ComputeNode bro = addComputeNode(broname);
    bro.setImage(broURL, broHash, broN);
    bro.setDomain(domain);
    bro.setNodeType(broType);
    bro.setPostBootScript(Scripts.getBroScripts());
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

  public void configBroNode(String nodeName, String edgeRouter, String resourceDir, String
    SDNControllerIP, String serverurl, String sshkey) {
    // Bro uses 'eth1"
    Exec.sshExec("root", getComputeNode(nodeName).getManagementIP(),"sed -i 's/eth0/eth1/' " +
      "/opt/bro/etc/node.cfg", sshkey);

    copyFile2Node(PathUtil.joinFilePath(resourceDir, "bro/test.bro"), "/root/test.bro", sshkey,
      nodeName);
    copyFile2Node(PathUtil.joinFilePath(resourceDir, "bro/test-all-policy.bro"),
      "/root/test-all-policy.bro",
      sshkey, nodeName);
    copyFile2Node(PathUtil.joinFilePath(resourceDir, "bro/detect.bro"), "/root/detect.bro",
      sshkey,
      nodeName);
    copyFile2Node(PathUtil.joinFilePath(resourceDir, "bro/detect-all-policy.bro"),
        "/root/detect-all-policy.bro", sshkey, nodeName);
    copyFile2Node(PathUtil.joinFilePath(resourceDir, "bro/evil.txt"), "/root/evil.txt", sshkey,
        nodeName);
    copyFile2Node(PathUtil.joinFilePath(resourceDir, "bro/reporter.py"), "/root/reporter.py",
      sshkey, nodeName);
    copyFile2Slice(PathUtil.joinFilePath(resourceDir, "bro/cpu_percentage.sh"),
      "/root/cpu_percentage.sh",
        sshkey, nodeName);

    Exec.sshExec("root", getComputeNode(nodeName).getManagementIP(), "sed -i 's/bogus_addr/" +
      SDNControllerIP + "/' *.bro", sshkey);

    String url = serverurl.replace("/", "\\/");
    Exec.sshExec("root", getComputeNode(nodeName).getManagementIP(),"sed -i 's/bogus_addr/" +
      url + "/g' reporter.py", sshkey);

    String dpid = getDpid(edgeRouter, sshkey);
    Exec.sshExec("root", getComputeNode(nodeName).getManagementIP(), "sed -i 's/bogus_dpid/" +
      Long.parseLong(dpid, 16) + "/' *.bro", sshkey);
    Exec.sshExec("root", getComputeNode(nodeName).getManagementIP(), "broctl deploy&", sshkey);
    Exec.sshExec("root", getComputeNode(nodeName).getManagementIP(),"python reporter & disown", sshkey);
    Exec.sshExec("root", getComputeNode(nodeName).getManagementIP(),
        "/usr/bin/rm *.log; pkill bro; /usr/bin/screen -d -m /opt/bro/bin/bro " +
        "-i eth1 " + "test-all-policy.bro", sshkey);
  }

  public String getDpid(String routerName, String sshkey){
    String[] res = runCmdNode("/bin/bash ~/dpid.sh", sshkey, routerName, true).split(" ");
    res[1] = res[1].replace("\n", "");
    return  res[1];
  }

  public ComputeNode addOVSRouter(String site, String name) {
    logger.debug(String.format("Adding new OVS router to slice %s on site %s", slice.getName(),
      site));
    NodeBaseInfo ninfo = NodeBase.getImageInfo(VMVersion);
    String nodeImageShortName = ninfo.nisn;
    String nodeImageURL = ninfo.niurl;
    //http://geni-images.renci.org/images/standard/ubuntu/ub1304-ovs-opendaylight-v1.0.0.xml
    String nodeImageHash = ninfo.nihash;
    String nodeNodeType = "XO Medium";
    String nodePostBootScript = Scripts.getOVSScript();
    ComputeNode node0 = slice.addComputeNode(name);
    node0.setImage(nodeImageURL, nodeImageHash, nodeImageShortName);
    node0.setNodeType(nodeNodeType);
    node0.setDomain(SiteBase.get(site));
    node0.setPostBootScript(nodePostBootScript);
    return node0;
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
    for(BroadcastNetwork link :slice.getBroadcastLinks()){
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

}
