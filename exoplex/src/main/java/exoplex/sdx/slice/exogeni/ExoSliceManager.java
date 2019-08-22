package exoplex.sdx.slice.exogeni;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import exoplex.common.utils.Exec;
import exoplex.common.utils.NetworkUtil;
import exoplex.common.utils.PathUtil;
import exoplex.common.utils.ScpTo;
import exoplex.sdx.core.CoreProperties;
import exoplex.sdx.slice.Scripts;
import exoplex.sdx.slice.SliceManager;
import exoplex.sdx.slice.SliceProperties;
import net.jcip.annotations.ThreadSafe;
import org.apache.commons.lang3.RandomStringUtils;
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

import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ThreadSafe
public class ExoSliceManager extends SliceManager {
  final static long DEFAULT_BW = 1000000000;
  final static Logger logger = LogManager.getLogger(ExoSliceManager.class);
  private static final int COMMIT_COUNT = 5;
  private static final int INTERVAL = 10;
  private ReentrantLock lock = new ReentrantLock();
  private ISliceTransportAPIv1 sliceProxy;
  private SliceAccessContext<SSHAccessToken> sctx;
  private Slice slice;
  private HashSet<String> reachableNodes = new HashSet<>();
  private HashMap<String, String> postBootScriptsMap = new HashMap<>();
  private List<Thread> threadList = new ArrayList<>();

  @Inject
  public ExoSliceManager(@Assisted("sliceName") String sliceName,
                         @Assisted("pem") String pemLocation,
                         @Assisted("key") String keyLocation,
                         @Assisted("controller") String controllerUrl,
                         @Assisted("ssh") String sshKey) {
    super(sliceName, pemLocation, keyLocation, controllerUrl, sshKey);
    this.sliceProxy = getSliceProxy(pemLocation, keyLocation, controllerUrl);
    refreshSshContext();
    this.slice = null;
    this.mocked = false;
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

  synchronized public Collection<String> getBroadcastLinks() {
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

  synchronized public void createSlice() {
    logger.info(String.format("create %s", sliceName));
    slice = Slice.create(sliceProxy, sctx, sliceName);
  }

  synchronized public void permitStitch(String secret, String GUID) throws TransportException {
    int times = 0;
    while (times < COMMIT_COUNT) {
      try {
        //s1
        sliceProxy = getSliceProxy(pemLocation, keyLocation, controllerUrl);
        sliceProxy.permitSliceStitch(sliceName, GUID, secret);
        break;
      } catch (TransportException e) {
        // TODO Auto-generated catch blocksynchronized
        logger.warn("Failed to permit stitch, retry");
        times++;
        if (times == COMMIT_COUNT) {
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

  synchronized public String permitStitch(String GUID) throws TransportException {
    int times = 0;
    while (times < COMMIT_COUNT) {
      try {
        //s1
        sliceProxy = getSliceProxy(pemLocation, keyLocation, controllerUrl);
        String secret = RandomStringUtils.randomAlphabetic(10);
        sliceProxy.permitSliceStitch(sliceName, GUID, secret);
        return secret;
      } catch (TransportException e) {
        // TODO Auto-generated catch block
        logger.warn("Failed to permit stitch, retry");
        times++;
        if (times == COMMIT_COUNT) {
          throw e;
        }
        try {
          Thread.sleep((long) (INTERVAL * 1000));
        } catch (InterruptedException var6) {
          Thread.currentThread().interrupt();
        }
      }
    }
    return null;
  }

  synchronized public void lockSlice() {
    logger.debug("lock slice");
    lock.lock();
  }

  synchronized public void unLockSlice() {
    try {
      logger.debug("unlock slice");
      lock.unlock();
    } catch (Exception e) {
      logger.warn("unLockSlice redundant");
    }
  }

  synchronized public void abort() {
    try {
      reloadSlice();
      unLockSlice();
    } catch (Exception e) {
    }
  }

  synchronized public void loadSlice() throws Exception {
    reloadSlice();
    if (slice != null) {
      renew();
    }
  }

  private void reloadSlice() throws Exception {
    int i = 0;
    sliceProxy = getSliceProxy(pemLocation, keyLocation, controllerUrl);
    do {
      try {
        slice = Slice.loadManifestFile(sliceProxy, sliceName);
        if (slice != null) {
          return;
        }
      } catch (Exception e) {
        logger.warn(e.getMessage());
        try {
          Thread.sleep((long) (INTERVAL * 1000 * (i + 1)));
        } catch (InterruptedException var6) {
          Thread.currentThread().interrupt();
        }
      }
      i++;
      sliceProxy = getSliceProxy(pemLocation, keyLocation, controllerUrl);
      refreshSshContext();
      slice = null;
    } while (i < COMMIT_COUNT);
    logger.error("failed to reload slice");
    throw new Exception(String.format("Unable to find %s among active slices", sliceName));
  }

  synchronized public void resetHostNames() {
    for (ComputeNode node : slice.getComputeNodes()) {
      runCmdByIP(String.format("sudo hostnamectl set-hostname %s-%s", sliceName, node.getName()),
        node.getManagementIP(), false);
    }
  }

  synchronized public String addComputeNode(String name) {
    logger.info(String.format("addComputeNode %s", name));
    this.slice.addComputeNode(name);
    return name;
  }

  synchronized public String stitchNetToNode(String netName, String nodeName) {
    Network net0 = (Network) slice.getResourceByName(netName);
    InterfaceNode2Net ifaceNode0 = (InterfaceNode2Net) net0.stitch(slice.getResourceByName(nodeName));
    return ifaceNode0.getName();
  }

  synchronized public String stitchNetToNode(String netName, String nodeName, String ip, String
    netmask) {
    Network net0 = (Network) slice.getResourceByName(netName);
    InterfaceNode2Net ifaceNode0 = (InterfaceNode2Net) net0.stitch(slice.getResourceByName(nodeName));
    ifaceNode0.setIpAddress(ip);
    ifaceNode0.setNetmask(netmask);
    return ifaceNode0.getName();
  }

  synchronized public String addComputeNode(
    String name, String nodeImageURL,
    String nodeImageHash, String nodeImageShortName, String nodeNodeType, String site,
    String nodePostBootScript) {
    ComputeNode node0 = this.slice.addComputeNode(name);
    node0.setImage(nodeImageURL, nodeImageHash, nodeImageShortName);
    node0.setNodeType(nodeNodeType);
    node0.setDomain(SiteBase.get(site));
    if (nodePostBootScript != null) {
      node0.setPostBootScript(nodePostBootScript);
      postBootScriptsMap.put(name, nodePostBootScript);
    }
    return name;
  }

  synchronized public String addComputeNode(String site, String name) {
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
    postBootScriptsMap.put(name, nodePostBootScript);
    return node0.getName();
  }

  synchronized public StorageNode addStorageNode(String name, long capacity, String mountpnt) {
    return this.slice.addStorageNode(name, capacity, mountpnt);
  }

  synchronized public String addStitchPort(String name, String label, String port, long bandwidth) {
    logger.info(String.format("addStitchPort %s %s %s %s", name, label, port, bandwidth));
    return this.slice.addStitchPort(name, label, port, bandwidth).getName();
  }

  synchronized public void stitchSptoNode(String spName, String nodeName) {
    StitchPort sp = (StitchPort) slice.getResourceByName(spName);
    ComputeNode node = (ComputeNode) slice.getResourceByName(nodeName);
    sp.stitch(node);
  }

  synchronized public String addBroadcastLink(String name, long bandwidth) {
    synchronized (this) {
      logger.info(String.format("addBroadcastLink %s %s", name, bandwidth));
      return this.slice.addBroadcastLink(name, bandwidth).getName();
    }
  }

  synchronized public String addBroadcastLink(String name) {
    synchronized (this) {
      return this.addBroadcastLink(name, DEFAULT_BW);
    }
  }

  synchronized public String attach(String nodeName, String linkName, String ip, String netmask) {
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

  synchronized public String attach(String nodeName, String linkName) {
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

  synchronized public String getStitchingGUID(String netName) {
    return slice.getResourceByName(netName).getStitchingGUID();
  }

  synchronized public String getComputeNode(String nm) {
    ComputeNode node = (ComputeNode) this.slice.getResourceByName(nm);
    while (node == null || node.getState() == null || node.getManagementIP() == null) {
      logger.debug(String.format("getComputeNode %s", nm));
      try {
        reloadSlice();
      } catch (Exception e) {

      }
      node = (ComputeNode) this.slice.getResourceByName(nm);
    }
    return node.getName();
  }

  synchronized public Interface stitch(RequestResource r1, RequestResource r2) {
    return slice.stitch(r1, r2);
  }

  synchronized public void unstitch(String stitchLinkName, String customerSlice, String customerGUID) {
    BroadcastNetwork net = (BroadcastNetwork) slice.getResourceByName(stitchLinkName);
    String stitchNetReserveId = net.getStitchingGUID();
    try {
      sliceProxy.undoSliceStitch(sliceName, stitchNetReserveId, customerSlice,
        customerGUID);
    } catch (TransportException e) {
      e.printStackTrace();
    }
  }

  synchronized public String getName() {
    return sliceName;
  }

  synchronized public void setName(String sliceName) {
    this.sliceName = sliceName;
    slice.setName(sliceName);
  }

  synchronized public boolean isNewSlice() {
    return this.slice.isNewSlice();
  }

  synchronized public void commit(int count, int sleepInterval) throws XMLRPCTransportException {
    slice.commit(count, sleepInterval);
  }

  synchronized public void commit() throws XMLRPCTransportException {
    int i = 0;
    do {
      try {
        slice.commit();
        return;
      } catch (XMLRPCTransportException var7) {
        logger.debug(var7.getMessage());
        if (var7.getMessage().contains("duplicate slice urn")) {
          i = COMMIT_COUNT;
        }
        logger.warn("Slice commit failed: sleeping for " + INTERVAL + " seconds. ");
        //if (i >= COMMIT_COUNT) {
        //  throw var7;
        //}
      } catch (Exception var8) {
        logger.debug(var8.getMessage());
        logger.warn("Slice commit failed: sleeping for " + INTERVAL + " seconds. ");
        if (var8.getMessage().contains("duplicate slice urn")) {
          i = COMMIT_COUNT;
        }
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

  synchronized public void delete() {
    logger.debug(String.format("deleting slice %s", sliceName));
    int i = 0;
    do {
      try {
        sliceProxy.deleteSlice(sliceName);
        break;
      } catch (XMLRPCTransportException e) {
        logger.warn(e.getMessage());
        if (e.getMessage().contains("unable to find slice")) {
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
    } while (i < COMMIT_COUNT);
  }

  synchronized public String enableSliceStitching(RequestResource r, String secret) {
    return slice.enableSliceStitching(r, secret);
  }

  synchronized public Collection<String> getAllResources() {
    return maptoNames(slice.getAllResources());
  }

  private Collection<String> maptoNames(Collection<ModelResource> resources) {
    ArrayList<String> res = new ArrayList<>();
    for (ModelResource resource : resources) {
      res.add(resource.getName());
    }
    return res;
  }

  synchronized public Collection<String> getInterfaces() {
    ArrayList<String> res = new ArrayList<>();
    for (Interface intf : slice.getInterfaces()) {
      res.add(intf.getName());
    }
    return res;
  }

  synchronized public Collection<String> getLinks() {
    ArrayList<String> res = new ArrayList<>();
    for (Network net : slice.getLinks()) {
      res.add(net.getName());
    }
    return res;
  }

  synchronized public Collection<String> getComputeNodes() {
    ArrayList<String> res = new ArrayList<>();
    for (ComputeNode node : slice.getComputeNodes()) {
      res.add(node.getName());
    }
    return res;
  }

  synchronized public Collection<String> getStitchPorts() {
    ArrayList<String> res = new ArrayList<>();
    for (StitchPort sp : slice.getStitchPorts()) {
      res.add(sp.getName());
    }
    return res;
  }

  synchronized public void refresh() {
    slice.refresh();
  }

  synchronized public void commitSlice() throws TransportException {
    commit();
  }

  synchronized public void commitAndWait() throws TransportException, Exception {
    commit();
    reloadSlice();
    if (slice == null) {
      throw new Exception(String.format("Failed to create slice %s", sliceName));
    }
    waitTillActive();
  }

  synchronized public boolean commitAndWait(int interval) throws TransportException, Exception {
    commit();
    String timeStamp1 = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date());
    waitTillActive(interval);
    String timeStamp2 = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date());
    logger.debug("Time interval: " + timeStamp1 + " " + timeStamp2);
    return true;
  }

  synchronized public boolean commitAndWait(int interval, List<String> resources) throws TransportException, Exception {
    commit();
    String timeStamp1 = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date());
    boolean res = waitTillActive(interval, resources);
    String timeStamp2 = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date());
    logger.debug("Time interval: " + timeStamp1 + " " + timeStamp2);
    return res;
  }

  synchronized public void waitTillActive() throws Exception {
    waitTillActive(INTERVAL);
  }

  synchronized public void waitTillActive(int interval) throws Exception {
    List<String> computeNodes = new ArrayList<>(getComputeNodes());
    List<String> links = new ArrayList<>(getBroadcastLinks());
    computeNodes.addAll(links);
    waitTillActive(interval, computeNodes);
  }

  synchronized public String getState(String resourceName) {
    return slice.getResourceByName(resourceName).getState();
  }

  synchronized public boolean waitTillActive(int interval, List<String> resources) throws Exception {
    logger.info("Wait until following resources are active: " + String.join(",", resources));
    reloadSlice();
    while (true) {
      ArrayList<String> activeResources = new ArrayList<>();
      refresh();
      logger.debug("ExoSliceManager: " + getAllResources());
      for (String c : getComputeNodes()) {
        logger.debug(String.format("[%s] Resource: %s , state: %s  site: %s", sliceName, c, getState
          (c), getNodeDomain(c)));
        if (resources.contains(c)) {
          if (getState(c).contains("Closed")) {
            throw new Exception(String.format("Slice %s closed", sliceName));
          }
          if (!getState(c).equals("Active") || getManagementIP(c) == null) {
          } else {
            if (!reachableNodes.contains(c) && !NetworkUtil.checkReachability(getManagementIP(c))) {
              logger.warn(String.format("Node %s (%s) of slice %s unreachable", c, getManagementIP
                (c), sliceName));
            } else {
              activeResources.add(c);
              reachableNodes.add(c);
            }
          }
        }
      }
      for (String l : getBroadcastLinks()) {
        logger.debug("Resource: " + l + ", state: " + getState(l));
        if (resources.contains(l)) {
          if (getState(l).contains("Failed") || getState(l).contains("Closed")) {
            logger.warn(String.format("link %s failed or closed", l));
            return false;
          }
          if (getState(l).equals("Active")) {
            activeResources.add(l);
          }
          if (getState(l).equals("Null")) {
            activeResources.add(l);
            logger.warn(String.format("%s state: Null", l));
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
    for (String n : getComputeNodes()) {
      logger.debug("ComputeNode: " + n + ", Managment IP =  " + getManagementIP(n));
      if (postBootScriptsMap.containsKey(n)) {
        runCmdNodeAsync(postBootScriptsMap.remove(n), n, false);
      }
    }
    joinAllThreads();
    logger.info("Done, those  resources are active now: " + String.join(",", resources));
    return true;
  }

  synchronized public void copyFile2Slice(String lfile, String rfile, String privkey) {
    ArrayList<Thread> tlist = new ArrayList<Thread>();
    for (String c : getComputeNodes()) {
      String mip = getManagementIP(c);
      try {
        Thread thread = new Thread() {
          @Override
          synchronized public void run() {
            try {
              logger.debug("scp config file to " + mip);
              ScpTo.Scp(lfile, SliceProperties.userName, mip, rfile, privkey);
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

  synchronized public void copyFile2Slice(String lfile, String rfile, String privkey,
                                          String patn) {
    Pattern pattern = Pattern.compile(patn);
    ArrayList<Thread> tlist = new ArrayList<Thread>();
    for (String c : getComputeNodes()) {
      Matcher matcher = pattern.matcher(c);
      if (!matcher.find()) {
        continue;
      }
      String mip = getManagementIP(c);
      try {
        Thread thread = new Thread() {
          @Override
          synchronized public void run() {
            try {
              logger.debug("scp config file to " + mip);
              ScpTo.Scp(lfile, SliceProperties.userName, mip, rfile, privkey);
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

  synchronized public String getManagementIP(String nodeName) {
    ComputeNode node = (ComputeNode) slice.getResourceByName(nodeName);
    if (node != null) {
      return ((ComputeNode) slice.getResourceByName(nodeName)).getManagementIP();
    } else {
      return null;
    }
  }

  synchronized public void copyFile2Node(String lfile, String rfile, String privkey, String nodeName) {
    String ip = getManagementIP(nodeName);
    try {
      logger.debug(String.format("scp file %s to %s", lfile, ip));
      ScpTo.Scp(lfile, SliceProperties.userName, ip, rfile, privkey);
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
        synchronized public void run() {
          try {
            logger.debug(String.format("[%s-%s-%s] run commands: %s", sliceName, c.getName(), mip,
              cmd));
            runCmdByIP(cmd, mip, repeat);
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
   * @param mip
   * @param res
   * @return true is there is uninstalled software
   */
  private boolean processCmdRes(String mip, String res) {
    if (res == null) {
      return true;
    }
    logger.debug(String.format("%s processing result from %s: %s", sliceName, mip, res));
    if (res.contains("ovs-vsctl: command not found") || res.contains("ovs-ofctl: command not found")) {
      String[] result = Exec.sshExec(SliceProperties.userName, mip,
        Scripts.installOVS(), sshKey);
      if (result[0].startsWith("error")) {
        return true;
      } else {
        return false;
      }
    }
    if (res.contains("docker: command not found")) {
      String[] result = Exec.sshExec(SliceProperties.userName, mip,
        Scripts.installDocker(), sshKey);
      if (result[0].startsWith("error")) {
        return true;
      } else {
        return false;
      }
    }
    if (res.contains("Unable to lock")) {
      Exec.sshExec(SliceProperties.userName, mip, "sudo rm /var/lib/dpkg/lock;dpkg --configure -a",
        sshKey);
    }
    if (res.contains("dpkg was interrupted")) {
      Exec.sshExec(SliceProperties.userName, mip, "sudo dpkg --configure -a",
        sshKey);
    }
    if (res.contains("traceroute: command not found")) {
      Exec.sshExec(SliceProperties.userName, mip, Scripts.installTraceRoute()
        , sshKey);
    }
    if (res.contains("can't read /etc/quagga/daemons: No such file or directory")) {
      Exec.sshExec(SliceProperties.userName, mip, Scripts.enableZebra()
        , sshKey);
    }
    return false;
  }

  public String runCmdNode(final String cmd, String nodeName, boolean repeat) {
    String mip = getManagementIP(nodeName);
    if (mip == null) {
      logger.error(String.format("IP address of %s in slice %s is null", nodeName, sliceName));
    }
    return runCmdByIP(cmd, mip, repeat);
  }

  public String runCmdNode(final String cmd, String nodeName) {
    String mip = getManagementIP(nodeName);
    return runCmdByIP(cmd, mip, false);
  }

  public void runCmdNodeAsync(final String cmd, String nodeName,
                              boolean repeat) {
    String mip = getManagementIP(nodeName);
    Thread t = new Thread() {
      @Override
      public void run() {
        runCmdByIP(cmd, mip, repeat);
      }
    };
    t.start();
    threadList.add(t);
  }

  public void joinAllThreads() {
    for (Thread t : threadList) {
      try {
        t.join();
      } catch (Exception e) {

      }
    }
  }

  public List<String> getPhysicalInterfaces(String nodeName) {
    String res = runCmdNode(String.format("sudo /bin/bash %s/ifaces.sh",
      SliceProperties.homeDir),
      nodeName);
    logger.debug(String.format("%s %s Interfaces: %s", sliceName, nodeName, res).replace("\n",
      " "));
    String[] ifaces = res.replace(" ", "").split("\n");
    ArrayList<String> interfaces = new ArrayList<>();
    for (String s : ifaces) {
      String ss = s.replace(" ", "").replace("\n", "");
      if (ss.length() > 1) {
        interfaces.add(ss);
      }
    }
    return interfaces;
  }

  public String runCmdByIP(final String cmd, String mip, boolean repeat) {
    logger.debug(String.format("[%s-%s] run commands: %s", sliceName, mip, cmd));
    String res[] = Exec.sshExec(SliceProperties.userName, mip, cmd, sshKey);
    if (repeat && (res[0] == null
      || res[0].startsWith("error")
      || res[0].contains("Could not get lock")
      || res[0].contains("command not found"))) {
      logger.debug(res[1]);
      processCmdRes(mip, res[1]);
      res = Exec.sshExec(SliceProperties.userName, mip, cmd, sshKey);
      if (res[0].startsWith("error")) {
        try {
          Thread.sleep(1000);
        } catch (Exception e) {
        }
      }
    }
    return res[0];
  }

  public void runCmdSlice(final String cmd, final String sshkey, final String pattern,
                          final boolean repeat) {
    List<Thread> tlist = new ArrayList<Thread>();

    for (String c : getComputeNodes()) {
      if (c.matches(pattern)) {
        tlist.add(new Thread() {
          @Override
          public void run() {
            try {
              runCmdNode(cmd, c, repeat);
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

  synchronized public void addLink(String linkName, String nodeName, long
    bw) {
    logger.info(String.format("addLink %s %s %s", linkName, nodeName, bw));
    ComputeNode node = (ComputeNode) slice.getResourceByName(nodeName);
    Network net = slice.addBroadcastLink(linkName, bw);
    net.stitch(node);
  }

  synchronized public void removeLink(String linkName) {
    BroadcastNetwork net = (BroadcastNetwork) slice.getResourceByName(linkName);
    net.delete();
  }

  synchronized public void addLink(String linkName, String ip, String netmask,
                                   String nodeName, long
                                     bw) {
    logger.info(String.format("addLink %s %s %s %s %s", linkName, ip, netmask, nodeName, bw));
    ComputeNode node = (ComputeNode) slice.getResourceByName(nodeName);
    Network net = slice.addBroadcastLink(linkName, bw);
    InterfaceNode2Net ifaceNode0 = (InterfaceNode2Net) net.stitch(node);
    ifaceNode0.setIpAddress(ip);
    ifaceNode0.setNetmask(netmask);
  }

  synchronized public void addLink(String linkName, String ip1, String ip2,
                                   String netmask, String
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

  synchronized public void addLink(String linkName, String
    node1, String node2, long bw) {
    logger.info(String.format("addLink %s %s %s %s", linkName, node1, node2, bw));
    ComputeNode node_1 = (ComputeNode) slice.getResourceByName(node1);
    ComputeNode node_2 = (ComputeNode) slice.getResourceByName(node2);
    Network net = slice.addBroadcastLink(linkName, bw);
    net.stitch(node_1);
    net.stitch(node_2);
  }

  synchronized public String getNodeDomain(String nodeName) {
    ComputeNode node = (ComputeNode) slice.getResourceByName(nodeName);
    return node.getDomain();
  }

  synchronized public void addCoreEdgeRouterPair(String site, String router1,
                                                 String router2, String linkname, long bw) {
    NodeBaseInfo ninfo = NodeBase.getImageInfo(SliceProperties.OVSVersion);
    String nodeImageShortName = ninfo.imageName;
    String nodeImageURL = ninfo.imageUrl;
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

  synchronized public void addOvsRouter(String site, String router1) {
    NodeBaseInfo ninfo = NodeBase.getImageInfo(SliceProperties.OVSVersion);
    String nodeImageShortName = ninfo.imageName;
    String nodeImageURL = ninfo.imageUrl;
    String nodeImageHash = ninfo.imageHash;
    String nodeNodeType = "XO Medium";
    String nodePostBootScript = Scripts.getOVSScript();
    addComputeNode(router1, nodeImageURL,
      nodeImageHash, nodeImageShortName, nodeNodeType, site,
      nodePostBootScript);
  }

  synchronized public void addDocker(String siteName, String nodeName, String script,
                                     String type) {
    NodeBaseInfo ninfo = NodeBase.getImageInfo(SliceProperties.DockerVersion);
    String dockerImageShortName = ninfo.imageName;
    String dockerImageURL = ninfo.imageUrl;
    String dockerImageHash = ninfo.imageHash;
    String dockerNodeType = type;
    ComputeNode node0 = this.slice.addComputeNode(nodeName);
    node0.setImage(dockerImageURL, dockerImageHash, dockerImageShortName);
    node0.setNodeType(dockerNodeType);
    node0.setDomain(SiteBase.get(siteName));
    String postBootScript =
      Scripts.preBootScripts() + Scripts.installDocker() + script;
    node0.setPostBootScript(postBootScript);
    postBootScriptsMap.put(nodeName, postBootScript);
  }

  synchronized public void addRiakServer(String siteName, String nodeName) {
    addDocker(siteName, nodeName, Scripts.getRiakPreBootScripts(), NodeBase.xoMedium);

  }

  synchronized public void addSafeServer(String siteName, String riakIp,
                                         String safeDockerImage, String
                                           safeServerScript) {
    addDocker(siteName, "safe-server", Scripts.getSafeScript_v1(riakIp, safeDockerImage,
      safeServerScript), NodeBase.xoLarge);
  }

  synchronized public void addPlexusController(String controllerSite, String name) {
    addDocker(controllerSite, name, Scripts.getPlexusScript(CoreProperties.getPlexusImage()), NodeBase.xoMedium);
  }

  //We always add the bro when we add the edge router
  synchronized public String addBro(String broname, String domain) {
    logger.warn("The old bro image is not supported in ExoGENI, bro might not" +
      " be properly installed in the node");
    String broN = NodeBase.CENTOS_7_6;
    String broURL = NodeBase.getImageInfo(broN).imageUrl;
    String broHash = NodeBase.getImageInfo(broN).imageHash;
    String broType = NodeBase.xoMedium;
    ComputeNode bro = this.slice.addComputeNode(broname);
    bro.setImage(broURL, broHash, broN);
    bro.setDomain(domain);
    bro.setNodeType(broType);
    bro.setPostBootScript(Scripts.getBroScripts());
    postBootScriptsMap.put(broname, Scripts.getBroScripts());
    return broname;
  }

  synchronized public void stitch(String RID, String customerName, String CID,
                                  String secret,
                                  String newip) {
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

  public void configBroNode(String nodeName, String edgeRouter,
                            String resourceDir, String
                              SDNControllerIP, String serverurl, String sshkey) {
    // Bro uses 'eth1"
    Exec.sshExec(SliceProperties.userName, getManagementIP(nodeName), "sudo sed -i " +
      "'s/eth0/eth1/' " +
      "/opt/bro/etc/node.cfg", sshkey);

    copyFile2Node(PathUtil.joinFilePath(resourceDir, "bro/test.bro"),
      String.format("%stest.bro", SliceProperties.homeDir), sshkey,
      nodeName);
    copyFile2Node(PathUtil.joinFilePath(resourceDir, "bro/test-all-policy.bro"),
      String.format("%s/test-all-policy.bro", SliceProperties.homeDir),
      sshkey, nodeName);
    copyFile2Node(PathUtil.joinFilePath(resourceDir, "bro/detect.bro"),
      String.format("%s/detect.bro", SliceProperties.homeDir),
      sshkey,
      nodeName);
    copyFile2Node(PathUtil.joinFilePath(resourceDir, "bro/detect-all-policy.bro"),
      String.format("%s/detect-all-policy.bro", SliceProperties.homeDir), sshkey,
      nodeName);
    copyFile2Node(PathUtil.joinFilePath(resourceDir, "bro/evil.txt"),
      String.format("%sevil.txt", SliceProperties.homeDir), sshkey,
      nodeName);
    copyFile2Node(PathUtil.joinFilePath(resourceDir, "bro/reporter.py"),
      String.format("%s/reporter.py", SliceProperties.homeDir),
      sshkey, nodeName);
    copyFile2Slice(PathUtil.joinFilePath(resourceDir, "bro/cpu_percentage.sh"),
      String.format("%s/cpu_percentage.sh", SliceProperties.homeDir),
      sshkey, nodeName);

    Exec.sshExec(SliceProperties.userName, getManagementIP(nodeName),
      "sudo sed -i 's/bogus_addr/" + SDNControllerIP + "/' *.bro", sshkey);

    String url = serverurl.replace("/", "\\/");
    Exec.sshExec(SliceProperties.userName, getManagementIP(nodeName),
      "sudo sed -i 's/bogus_addr/" +
        url + "/g' reporter.py", sshkey);

    String dpid = getDpid(edgeRouter, sshkey);
    Exec.sshExec(SliceProperties.userName, getManagementIP(nodeName),
      "sudo sed -i 's/bogus_dpid/" +
        Long.parseLong(dpid, 16) + "/' *.bro", sshkey);
    Exec.sshExec(SliceProperties.userName, getManagementIP(nodeName),
      "sudo broctl deploy&", sshkey);
    Exec.sshExec(SliceProperties.userName, getManagementIP(nodeName),
      "sudo python reporter & disown", sshkey);
    Exec.sshExec(SliceProperties.userName, getManagementIP(nodeName),
      "sudo /usr/bin/rm *.log; sudo pkill bro; sudo /usr/bin/screen -d -m " +
        "sudo /opt/bro/bin/bro " +
        "-i eth1 " + "test-all-policy.bro", sshkey);
  }

  public String getDpid(String routerName, String sshkey) {
    String[] res =
      runCmdNode("sudo /bin/bash ~/dpid.sh", routerName, true).split(" ");
    res[1] = res[1].replace("\n", "");
    return res[1];
  }

  synchronized public String addOVSRouter(String site, String name) {
    synchronized (this) {
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
      String postBootScripts = Scripts.preBootScripts() + nodePostBootScript;
      node0.setPostBootScript(postBootScripts);
      postBootScriptsMap.put(name, postBootScripts);
      return node0.getName();
    }
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

  synchronized public void deleteResource(String name) {
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
    for (Interface ifname : ((ComputeNode) slice.getResourceByName(nodeName)).getInterfaces()) {
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
    try {
      Thread.sleep(seconds * 1000);
    } catch (Exception e) {
    }
  }

  public void renew(Date newDate) {
    //try {
    //  slice.renew(newDate);
    //}catch (Exception e){

    //}
  }

  public void renew() {
    //try {
    //  slice.renew(DateUtils.addDays(new Date(), extensionDays));
    //}catch (Exception e){
    //}
  }
}
