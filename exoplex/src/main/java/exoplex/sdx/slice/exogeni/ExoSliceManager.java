package exoplex.sdx.slice.exogeni;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.internal.asm.$Type;
import exoplex.common.utils.Exec;
import exoplex.common.utils.NetworkUtil;
import exoplex.common.utils.PathUtil;
import exoplex.common.utils.ScpTo;
import exoplex.sdx.core.CoreProperties;
import exoplex.sdx.core.SdxManagerBase;
import exoplex.sdx.slice.Scripts;
import exoplex.sdx.slice.SliceManager;
import exoplex.sdx.slice.SliceProperties;
import net.jcip.annotations.ThreadSafe;
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

import javax.annotation.Resource;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ThreadSafe
public class ExoSliceManager extends SliceManager {
  final static long DEFAULT_BW = 100000000;
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
  private HashMap<String, AtomicInteger> expectInterfaceNumMap= new HashMap<>();
  private HashMap<String, String> stitchingSecrets = new HashMap<>();

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

  public void permitStitch(String secret, String GUID) throws TransportException {
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
          Thread.sleep(INTERVAL * 1000);
        } catch (InterruptedException var6) {
          Thread.currentThread().interrupt();
        }
      }
    }
  }

  public boolean revokeStitch(String GUID) throws TransportException {
    sliceProxy = getSliceProxy(pemLocation, keyLocation, controllerUrl);
    sliceProxy.revokeSliceStitch(sliceName, GUID);
    stitchingSecrets.remove(GUID);
    return true;
  }

  public String permitStitch(String GUID) throws TransportException {
    if(stitchingSecrets.containsKey(GUID)) {
      return stitchingSecrets.get(GUID);
    }
    int times = 0;
    while (times < COMMIT_COUNT) {
      try {
        //s1
        sliceProxy = getSliceProxy(pemLocation, keyLocation, controllerUrl);
        String secret = RandomStringUtils.randomAlphabetic(10);
        sliceProxy.permitSliceStitch(sliceName, GUID, secret);
        stitchingSecrets.put(GUID, secret);
        return secret;
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
    return null;
  }

  public void lockSlice() {
    logger.debug(String.format("lock slice %s", sliceName));
    lock.lock();
  }

  public void unLockSlice() {
    try {
      logger.debug(String.format("unlock slice %s", sliceName));
      lock.unlock();
    } catch (Exception e) {
      logger.warn("unLockSlice redundant");
    }
  }

  public void abort() {
    try {
      reloadSlice();
      unLockSlice();
    } catch (Exception e) {
    }
  }

  public void loadSlice() throws Exception {
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
          Thread.sleep(INTERVAL * 1000 * (i + 1));
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
    InterfaceNode2Net ifaceNode0 =
      (InterfaceNode2Net) net0.stitch(slice.getResourceByName(nodeName));
    return ifaceNode0.getName();
  }

  public String stitchNetToNode(String netName, String nodeName, String ip, String
    netmask) {
    Network net0 = (Network) slice.getResourceByName(netName);
    InterfaceNode2Net ifaceNode0 =
      (InterfaceNode2Net) net0.stitch(slice.getResourceByName(nodeName));
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
      postBootScriptsMap.put(name, nodePostBootScript);
    }
    return name;
  }

  public String addComputeNode(String site, String name) {
    return addComputeNode(site, name, NodeBase.xoMedium);
  }

  public String addComputeNode(String site, String name, String type) {
    logger.debug(String.format("Adding new %s node %s to slice %s on %s", type,
      name, sliceName, site));
    if (slice == null) {
      createSlice();
    }
    NodeBaseInfo ninfo = NodeBase.getImageInfo(SliceProperties.CustomerVMVersion);
    String nodeImageShortName = ninfo.imageName;
    String nodeImageURL = ninfo.imageUrl;
    //http://geni-images.renci.org/images/standard/ubuntu/ub1304-ovs-opendaylight-v1.0.0.xml
    String nodeImageHash = ninfo.imageHash;
    String nodeNodeType = type;
    String nodePostBootScript = Scripts.getCustomerScript();
    ComputeNode node0 = slice.addComputeNode(name);
    node0.setImage(nodeImageURL, nodeImageHash, nodeImageShortName);
    node0.setNodeType(nodeNodeType);
    node0.setDomain(SiteBase.get(site));
    node0.setPostBootScript(nodePostBootScript);
    postBootScriptsMap.put(name, nodePostBootScript);
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
    return slice.getResourceByName(netName).getStitchingGUID();
  }

  public String getComputeNode(String nm) {
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

  public Interface stitch(RequestResource r1, RequestResource r2) {
    return slice.stitch(r1, r2);
  }

  public void unstitch(String stitchLinkName, String customerSlice,
    String customerGUID) {
      RequestResource resource =  slice.getResourceByName(stitchLinkName);
    if( resource instanceof BroadcastNetwork) {
        BroadcastNetwork net =  (BroadcastNetwork) slice.getResourceByName(stitchLinkName);
        logger.info("Broadcast network");
        String stitchNetReserveId = net.getStitchingGUID();
        try {
            sliceProxy.undoSliceStitch(sliceName, stitchNetReserveId, customerSlice,
                    customerGUID);
        } catch (TransportException e) {
            StringWriter errors = new StringWriter();
            e.printStackTrace(new PrintWriter(errors));
            logger.error(errors);
        }
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
    slice.commit(count, sleepInterval);
  }

  public void commit() throws XMLRPCTransportException {
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
        Thread.sleep(INTERVAL * 1000);
      } catch (InterruptedException var6) {
        Thread.currentThread().interrupt();
      }
      ++i;
    } while (i < COMMIT_COUNT);
    abort();
  }

  public void delete() {
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
          Thread.sleep(INTERVAL * 1000);
        } catch (InterruptedException var6) {
          Thread.currentThread().interrupt();
        }

      } catch (TransportException ex) {
        logger.warn(ex.getMessage());
        try {
          Thread.sleep(INTERVAL * 1000);
        } catch (InterruptedException var6) {
          Thread.currentThread().interrupt();
        }
      }
      i++;
    } while (i < COMMIT_COUNT);
  }

  public String enableSliceStitching(RequestResource r, String secret) {
    return slice.enableSliceStitching(r, secret);
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
    slice.refresh();
  }

  public void commitSlice() throws TransportException {
    commit();
  }

  public void commitAndWait() throws Exception {
    commit();
    reloadSlice();
    if (slice == null) {
      throw new Exception(String.format("Failed to create slice %s", sliceName));
    }
    waitTillActive();
  }

  public boolean commitAndWait(int interval) throws Exception {
    commit();
    String timeStamp1 = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date());
    waitTillActive(interval);
    String timeStamp2 = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date());
    logger.debug("Time interval: " + timeStamp1 + " " + timeStamp2);
    return true;
  }

  public boolean commitAndWait(int interval, List<String> resources) throws Exception {
    commit();
    String timeStamp1 = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date());
    boolean res = waitTillActive(interval, resources);
    String timeStamp2 = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date());
    logger.debug("Time interval: " + timeStamp1 + " " + timeStamp2);
    return res;
  }

  public void waitTillActive() throws Exception {
    waitTillActive(INTERVAL);
  }

  public void waitTillActive(int interval) throws Exception {
    List<String> computeNodes = new ArrayList<>(getComputeNodes());
    List<String> links = new ArrayList<>(getBroadcastLinks());
    computeNodes.addAll(links);
    waitTillActive(interval, computeNodes);
  }

  public String getState(String resourceName) {
    return slice.getResourceByName(resourceName).getState();
  }

  public boolean waitTillActive(int interval, List<String> resources) throws Exception {
    logger.info("Wait until following resources are active: " + String.join(",", resources));
    reloadSlice();
    int times = 0;
    int TOTALTIME = 3600;
    while (times * interval < TOTALTIME) {
      times ++;
      ArrayList<String> activeResources = new ArrayList<>();
      refresh();
      logger.debug("ExoSliceManager: " + getAllResources());
      for (String c : getComputeNodes()) {
        logger.debug(String.format("[%s] Resource: %s , state: %s  site: %s", sliceName, c,
          getState(c), getNodeDomain(c)));
        if (resources.contains(c)) {
          if (getState(c).contains("Closed") || getState(c).contains("Failed")) {
            logger.warn(String.format("Slice %s closed", sliceName));
            return false;
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
    if(times * interval >= TOTALTIME) {
      logger.warn(String.format("Slice not active after %s seconds", 500 * interval));
      return false;
    }
    for (String n : getComputeNodes()) {
      logger.debug("ComputeNode: " + n + ", Managment IP =  " + getManagementIP(n));
      if (postBootScriptsMap.containsKey(n)) {
        runCmdNodeAsync(postBootScriptsMap.remove(n), n, false);
      }
    }
    joinAllThreads();
    logger.info(sliceName + " active, those  resources are active now: " + String.join(",", resources));
    return true;
  }

  public void copyFile2Slice(String lfile, String rfile, String privkey) {
    ArrayList<Thread> tlist = new ArrayList<Thread>();
    for (String c : getComputeNodes()) {
      String mip = getManagementIP(c);
      try {
        Thread thread = new Thread() {
          @Override
          public void run() {
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

  public void copyFile2Slice(String lfile, String rfile, String privkey,
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
          public void run() {
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

  public String getManagementIP(String nodeName) {
    ComputeNode node = (ComputeNode) slice.getResourceByName(nodeName);
    if (node != null) {
      return node.getManagementIP();
    } else {
      try{
        reloadSlice();
        node = (ComputeNode) slice.getResourceByName(nodeName);
        if (node != null) {
          return node.getManagementIP();
        }
      } catch (Exception e) {
      }
      return null;
    }
  }

  public void copyFile2Node(String lfile, String rfile, String privkey,
    String nodeName) {
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
        public void run() {
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
    if (res.contains("ovs-vsctl: command not found")
      || res.contains("ovs-ofctl: command not found")) {
      String[] result = Exec.sshExec(SliceProperties.userName, mip,
        Scripts.installOVS(), sshKey);
      return result[0].startsWith("error");
    }
    if (res.contains("docker: command not found")) {
      this.runCmdByIP(Scripts.installDocker(),  mip, true);
      return true;
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
      return null;
    } else {
      return runCmdByIP(cmd, mip, repeat);
    }
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

  synchronized public void expectOneInterfaceDiff(String nodeName,
                                                  boolean add) {
    AtomicInteger num = expectInterfaceNumMap.computeIfAbsent(nodeName, k -> new AtomicInteger());
    if(num.get() == 0) {
      num.set(getPhysicalInterfaces(nodeName).size());
      logger.debug(String.format("%s %s Number of dataplane interfaces " +
        "before:%s", sliceName, nodeName, num.get()));
    }
    if(add) {
      num.incrementAndGet();
    } else {
      num.decrementAndGet();
    }
  }

  synchronized public void waitForInterfaces(String nodeName) {
    while(getPhysicalInterfaces(nodeName).size() != expectInterfaceNumMap.get(nodeName).get()) {
      sleep(5);
    }
    logger.debug(String.format("%s %s Number of dataplane interfaces after: " +
      "%s", sliceName, nodeName, expectInterfaceNumMap.get(nodeName).get()));
    expectInterfaceNumMap.get(nodeName).set(0);
  }

  public List<ImmutablePair<String, String>> getPhysicalInterfaces(String nodeName) {
    String res = runCmdNode(String.format("sudo /bin/bash %s/ifaces.sh",
      SliceProperties.homeDir),
      nodeName);
    logger.debug(String.format("%s %s Interfaces: %s", sliceName, nodeName, res).replace("\n",
      " "));
    String[] ifaces = res.split("\n");
    ArrayList<ImmutablePair<String, String>> interfaces = new ArrayList<>();
    for (String s : ifaces) {
      String ss = s.replaceAll("\\s+", " ").replace("\n", "");
      if (ss.length() > 1) {
        String[] parts = ss.split(" ");
        interfaces.add(new ImmutablePair<>(parts[0], parts[1]));
      }
    }
    return interfaces;
  }

  public String runCmdByIP(final String cmd, String mip, boolean repeat) {
    logger.debug(String.format("[%s-%s] run commands: %s", sliceName, mip, cmd));
    String[] res = Exec.sshExec(SliceProperties.userName, mip, cmd, sshKey);
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

  synchronized public List<String> addLink(String linkName, String nodeName, long bw) {
    List<String> ifaces = new ArrayList<>();
    logger.info(String.format("addLink %s %s %s", linkName, nodeName, bw));
    ComputeNode node = (ComputeNode) slice.getResourceByName(nodeName);
    Network net = slice.addBroadcastLink(linkName, bw);
    InterfaceNode2Net ifaceNode0 = (InterfaceNode2Net) net.stitch(node);
    ifaces.add(ifaceNode0.getName());
    return ifaces;
  }

  public void removeLink(String linkName) {
    RequestResource resource = slice.getResourceByName(linkName);
    if(resource instanceof BroadcastNetwork) {
        BroadcastNetwork net = (BroadcastNetwork) slice.getResourceByName(linkName);
        logger.info("BroadcastNetwork=" + net);
        net.delete();
    }
    else if( resource instanceof StitchPort) {
        StitchPort stitchPort = (StitchPort) slice.getResourceByName(linkName);
        logger.info("Stitchport=" + stitchPort);
        if(stitchPort != null) {
            logger.info("Deleting stitchport = " + stitchPort);
            stitchPort.delete();
        }
    }
    else {
        logger.info("neither broadcast and stitchport resource=" + resource);
    }
  }

  synchronized public List<String> addLink(String linkName, String ip, String netmask,
                                   String nodeName, long bw) {
    logger.info(String.format("addLink %s %s %s %s %s", linkName, ip, netmask, nodeName, bw));
    List<String> ifaces = new ArrayList<>();
    ComputeNode node = (ComputeNode) slice.getResourceByName(nodeName);
    Network net = slice.addBroadcastLink(linkName, bw);
    InterfaceNode2Net ifaceNode0 = (InterfaceNode2Net) net.stitch(node);
    ifaceNode0.setIpAddress(ip);
    ifaceNode0.setNetmask(netmask);
    ifaces.add(ifaceNode0.getName());
    return ifaces;
  }

  synchronized public List<String> addLink(String linkName, String ip1, String ip2,
                                   String netmask, String node1, String node2, long bw) {
    logger.info(String.format("addLink %s %s %s %s %s %s %s", linkName, ip1, ip2, netmask, node1,
      node2, bw));
    List<String> ifaces = new ArrayList<>();
    ComputeNode node_1 = (ComputeNode) slice.getResourceByName(node1);
    ComputeNode node_2 = (ComputeNode) slice.getResourceByName(node2);
    Network net = slice.addBroadcastLink(linkName, bw);
    InterfaceNode2Net ifaceNode0 = (InterfaceNode2Net) net.stitch(node_1);
    if(ip1 != null) {
      ifaceNode0.setIpAddress(ip1);
      ifaceNode0.setNetmask(netmask);
    }
    InterfaceNode2Net ifaceNode1 = (InterfaceNode2Net) net.stitch(node_2);
    ifaceNode1.setIpAddress(ip2);
    ifaceNode1.setNetmask(netmask);
    ifaces.add(ifaceNode0.getName());
    ifaces.add(ifaceNode1.getName());
    return ifaces;
  }

  synchronized public List<String> addLink(String linkName, String
    node1, String node2, long bw) {
    logger.info(String.format("addLink %s %s %s %s", linkName, node1, node2, bw));
    List<String> ifaces = new ArrayList<>();
    ComputeNode node_1 = (ComputeNode) slice.getResourceByName(node1);
    ComputeNode node_2 = (ComputeNode) slice.getResourceByName(node2);
    Network net = slice.addBroadcastLink(linkName, bw);
    InterfaceNode2Net ifaceNode0 = (InterfaceNode2Net) net.stitch(node_1);
    InterfaceNode2Net ifaceNode1 = (InterfaceNode2Net) net.stitch(node_2);
    ifaces.add(ifaceNode0.getName());
    ifaces.add(ifaceNode1.getName());
    return ifaces;
  }

  public String getNodeDomain(String nodeName) {
    ComputeNode node = (ComputeNode) slice.getResourceByName(nodeName);
    return node.getDomain();
  }

  public void addCoreEdgeRouterPair(String site, String router1,
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

  public void addOvsRouter(String site, String router1) {
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

  public void addDocker(String siteName, String nodeName, String script,
                                     String type) {
    logger.info(String.format("Add docker node %s on %s", nodeName, siteName));
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

  public void addRiakServer(String siteName, String nodeName) {
    addDocker(siteName, nodeName, Scripts.getRiakPreBootScripts(), NodeBase.xoMedium);

  }

  public void addSafeServer(String siteName, String riakIp,
                                         String safeDockerImage, String
                                           safeServerScript) {
    addDocker(siteName, SliceProperties.SAFESERVER, Scripts.getSafeScript_v1(riakIp,
      safeDockerImage,
      safeServerScript), NodeBase.xoLarge);
  }

  public void addPlexusController(String controllerSite, String name) {
    addDocker(controllerSite, name, Scripts.getPlexusScript(CoreProperties.getPlexusImage()),
      NodeBase.xoMedium);
  }

  //We always add the bro when we add the edge router
  public String addBro(String broname, String domain) {
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

  public void stitch(String RID, String customerName, String CID,
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
    logger.debug("Finished Stitching, set ip address of the new interface to "
      + newip + "  time elapsed: "
      + (t2 - t1) + "\n");
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
      String cmdOutput = runCmdNode("sudo /bin/bash ~/dpid.sh", routerName, true);
      String[] res = cmdOutput.split(" ");
      if(res.length > 1) {
          res[1] = res[1].replace("\n", "");
          return res[1];
      }
      return null;
  }

  public String addOVSRouter(String site, String name) {
    logger.debug(String.format("Adding new OVS router to slice %s on site %s", slice.getName(),
      site));
    NodeBaseInfo ninfo = NodeBase.getImageInfo(SliceProperties.OVSVersion);
    String nodeImageShortName = ninfo.imageName;
    String nodeImageURL = ninfo.imageUrl;
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
