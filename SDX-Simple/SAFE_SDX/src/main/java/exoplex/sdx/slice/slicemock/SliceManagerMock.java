package exoplex.sdx.slice.slicemock;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.renci.ahab.libndl.resources.common.ModelResource;
import org.renci.ahab.libndl.resources.request.*;
import org.renci.ahab.libtransport.ISliceTransportAPIv1;
import org.renci.ahab.libtransport.util.TransportException;
import org.renci.ahab.libtransport.xmlrpc.XMLRPCTransportException;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;


public class SliceManagerMock implements Serializable{
  private static final long serialVersionUID = 1L;
  final static long DEFAULT_BW = 10000000;
  final static Logger logger = LogManager.getLogger(SliceManagerMock.class);
  private static final int COMMIT_COUNT = 5;
  private static final int INTERVAL = 10;
  private ReentrantLock lock = new ReentrantLock();
  private String pemLocation;
  private String keyLocation;
  private String controllerUrl;
  private String sliceName;
  private String sshKey;
  private HashSet<String> reachableNodes = new HashSet<>();
  private boolean sliceCreated = false;

  public SliceManagerMock(String sliceName, String pemLocation, String keyLocation, String
    controllerUrl, String sshKey) {
    this.sliceName = sliceName;
    this.pemLocation = pemLocation;
    this.keyLocation = keyLocation;
    this.controllerUrl = controllerUrl;
    this.sshKey = sshKey;
  }

  public void createSlice() {
    logger.info(String.format("create %s", sliceName));
    sliceCreated = true;
  }

  public void permitStitch(String secret, String GUID){
    //do nothing
  }

  public String permitStitch(String GUID){
    return "secret";
  }

  public void reloadSlice() throws Exception {
    loadFromFile();
  }

  private void loadFromFile(){
    sliceCreated = true;
  }

  private void saveToFile(){

  }

  public void resetHostNames() {
  }

  public ComputeNode addComputeNode(String name) {
    return null;
  }

  public ComputeNode addComputeNode(){
    return null;
  }

  public ComputeNode addComputeNode(String site, String name) {
  return null;
  }

  public BroadcastNetwork addBroadcastLink(String name, long bandwidth) {
  return null;
  }

  public BroadcastNetwork addBroadcastLink(String name) {
    return this.addBroadcastLink(name, DEFAULT_BW);
  }

  public Interface attach(String nodeName, String linkName, String ip, String netmask) {
    return null;
  }

  public Interface attach(String nodeName, String linkName) {
    return null;
  }

  public RequestResource getResourceByName(String nm) {
    return null;
  }

  public ComputeNode getComputeNode(String nm) {
    return null;
  }

  public Interface stitch(RequestResource r1, RequestResource r2) {
    return null;
  }

  public void unstitch(String stitchLinkName, String customerSlice, String customerGUID) {
  }

  public String getName() {
    return sliceName;
  }

  public void setName(String sliceName) {
    this.sliceName = sliceName;
  }

  public void commit(int count, int sleepInterval) throws XMLRPCTransportException {
  }

  public void commit() throws XMLRPCTransportException {
  }

  public void delete() {
  }

  public String enableSliceStitching(RequestResource r, String secret) {
    return null;
  }

  public Collection<ModelResource> getAllResources() {
    return null;
  }

  public Collection<Interface> getInterfaces() {
    return null;
  }

  public Collection<Network> getLinks() {
    return null;
  }

  public Collection<BroadcastNetwork> getBroadcastLinks() {
    return null;
  }

  public Collection<Node> getNodes() {
    return null;
  }

  public Collection<ComputeNode> getComputeNodes() {
    return null;
  }

  public Collection<StorageNode> getStorageNodes() {
    return null;
  }

  public Collection<StitchPort> getStitchPorts() {
    return null;
  }

  public String getState() {
    return "getState unimplimented";
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

  public void addStitch(StorageNode storageNode, RequestResource r, Interface stitch) {
  }

  public void addStitch(StitchPort stitchPort, RequestResource r, Interface stitch) {
  }

  public void refresh() {
  }

  public void commitSlice() throws TransportException {
    commit();
  }

  public void commitAndWait() throws TransportException, Exception {
    commit();
    waitTillActive();
  }

  public boolean commitAndWait(int interval) throws TransportException, Exception {
    return true;
  }

  public boolean commitAndWait(int interval, List<String> resources) throws TransportException, Exception {
    return true;
  }

  public void waitTillActive() throws Exception {
    waitTillActive(INTERVAL);
  }

  public void waitTillActive(int interval) throws Exception {
  }

  public boolean waitTillActive(int interval, List<String> resources) throws Exception {
    return true;
  }

  public void copyFile2Slice(String lfile, String rfile, String privkey) {
  }

  public void copyFile2Slice(String lfile, String rfile, String privkey,
                             String patn) {
  }

  public void copyFile2Node(String lfile, String rfile, String privkey, String nodeName) {
  }

  private void runCmdSlice(Set<ComputeNode> nodes, String cmd, String sshkey,
                           boolean repeat) {
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
    String mip = getComputeNode(nodeName).getManagementIP();
    return runCmdByIP(cmd, mip, repeat);
  }

  public String runCmdNode(final String cmd, String nodeName) {
    String mip = getComputeNode(nodeName).getManagementIP();
    return runCmdByIP(cmd, mip, false);
  }

  public int getInterfaceNum(String nodeName) {
    String res = runCmdNode("/bin/bash /root/ifaces.sh", nodeName);
    logger.debug(String.format("Interfaces: %s", res));
    int num = res.split("\n").length;
    return num;
  }

  public String runCmdByIP(final String cmd, String mip, boolean repeat) {
    return null;
  }

  public void runCmdSlice(final String cmd, final String sshkey, final String pattern,
                          final boolean repeat) {
  }

  public void addLink(String linkName, String nodeName, long
    bw) {
  }

  public void removeLink(String linkName) {
  }

  public void addLink(String linkName, String ip, String netmask, String nodeName, long
    bw) {
  }

  public void addLink(String linkName, String ip1, String ip2, String netmask, String
    node1, String node2, long bw) {
  }

  public void addLink(String linkName, String
    node1, String node2, long bw) {
  }

  public void addCoreEdgeRouterPair(String site, String router1, String router2, String linkname, long bw) {
  }

  public void addOvsRouter(String site, String router1) {
  }

  public void addDocker(String siteName, String nodeName, String script, String size) {
  }

  public void addRiakServer(String siteName, String nodeName) {

  }

  public void addSafeServer(String siteName, String riakIp, String safeDockerImage, String
    safeServerScript) {
  }

  public void addPlexusController(String controllerSite, String name) {
  }

  //We always add the bro when we add the edge router
  public ComputeNode addBro(String broname, String domain) {
    return null;
  }

  public void stitch(String RID, String customerName, String CID, String secret,
                     String newip) {
  }

  public void configBroNode(String nodeName, String edgeRouter, String resourceDir, String
    SDNControllerIP, String serverurl, String sshkey) {
    // Bro uses 'eth1"
  }

  public String getDpid(String routerName, String sshkey) {
    String[] res = runCmdNode("/bin/bash ~/dpid.sh", routerName, true).split(" ");
    res[1] = res[1].replace("\n", "");
    return res[1];
  }

  public ComputeNode addOVSRouter(String site, String name) {
    return null;
  }

  public void printNetworkInfo() {
  }

  public void printSliceInfo() {
  }
}
