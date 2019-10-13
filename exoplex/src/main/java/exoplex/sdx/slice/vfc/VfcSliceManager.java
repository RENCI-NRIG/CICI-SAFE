package exoplex.sdx.slice.vfc;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import exoplex.sdx.slice.SliceManager;
import org.renci.ahab.libtransport.util.TransportException;
import org.renci.ahab.libtransport.xmlrpc.XMLRPCTransportException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

public class VfcSliceManager extends SliceManager {

  @Inject
  public VfcSliceManager(@Assisted("sliceName") String sliceName,
                         @Assisted("pem") String pemLocation,
                         @Assisted("key") String keyLocation,
                         @Assisted("controller") String controllerUrl,
                         @Assisted("ssh") String sshKey) {
    super(sliceName, pemLocation, keyLocation, controllerUrl, sshKey);
    this.mocked = false;
  }

   public void createSlice() {}

   public void permitStitch(String secret, String GUID) throws TransportException {}

   public String permitStitch(String GUID) throws TransportException {
    return null;
   }

   public void loadSlice() throws Exception {}

   public void resetHostNames() {}

   public String addComputeNode(String name) {
     return name;
   }

   public String addComputeNode(String site, String name) {
     return name;
   }

   public String stitchNetToNode(String netName, String nodeName) {
     return null;
   }

   public String stitchNetToNode(String netName, String nodeName, String ip, String
    netmask) {
     return null;
   }

   public String addComputeNode(
    String name, String nodeImageURL,
    String nodeImageHash, String nodeImageShortName, String nodeNodeType, String site,
    String nodePostBootScript) {
     return null;
   }

   public String addStitchPort(String name, String label, String port, long bandwidth) {
     return null;
   }

   public void stitchSptoNode(String spName, String nodeName) {}

   public String addBroadcastLink(String name, long bandwidth) {
     return null;
   }

   public String addBroadcastLink(String name) {
     return null;
   }

   public String attach(String nodeName, String linkName, String ip, String netmask) {
     return null;
   }

   public String attach(String nodeName, String linkName) {
     return null;
   }

   public String getStitchingGUID(String netName) {
     return null;
   }

   public String getComputeNode(String nm) {
     return null;
   }

   public void unstitch(String stitchLinkName, String customerSlice, String customerGUID) {}

   public String getName() {
     return null;
   }

   public void setName(String sliceName) {}

   public void commit(int count, int sleepInterval) throws XMLRPCTransportException {}

   public void commit() throws XMLRPCTransportException {}

   public void delete() {}


   public Collection<String> getAllResources() {
     return new ArrayList<>();
   }

   public Collection<String> getInterfaces() {
     return new ArrayList<>();
   }

   public Collection<String> getLinks() {
     return new ArrayList<>();
   }

   public Collection<String> getBroadcastLinks() {
     return new ArrayList<>();
   }

   public Collection<String> getComputeNodes() {
     return new ArrayList<>();
   }

   public Collection<String> getStitchPorts() {
     return new ArrayList<>();
   }

   public void refresh() {}

   public void commitSlice() throws TransportException {}

   public void commitAndWait() throws Exception {}

   public boolean commitAndWait(int interval) throws Exception {
     return true;
   }

   public boolean commitAndWait(int interval, List<String> resources) throws Exception {
     return true;
   }

   public void waitTillActive() throws Exception {}

   public void waitTillActive(int interval) throws Exception {}

   public boolean waitTillActive(int interval, List<String> resources) throws Exception {
     return true;
   }

   public void copyFile2Slice(String lfile, String rfile, String privkey) {}

   public void copyFile2Slice(String lfile, String rfile, String privkey,
                                      String patn) {}

   public void copyFile2Node(String lfile, String rfile, String privkey, String nodeName) {}

   public String runCmdNode(final String cmd, String nodeName, boolean repeat) {
     return null;
   }

   public String runCmdNode(final String cmd, String nodeName) {
     return null;
   }

   public List<String> getPhysicalInterfaces(String nodeName) {
     return new ArrayList<>();
   }

   public String runCmdByIP(final String cmd, String mip, boolean repeat) {
     return null;
   }

   public void runCmdSlice(final String cmd, final String sshkey, final String pattern,
                                   final boolean repeat) {}

   public void addLink(String linkName, String nodeName, long bw) {}

   public void removeLink(String linkName) {}

   public void addLink(String linkName, String ip, String netmask, String nodeName, long
    bw) {}

   public void addLink(String linkName, String ip1, String ip2, String netmask, String
    node1, String node2, long bw) {}

   public void addLink(String linkName, String
    node1, String node2, long bw) {}

   public String getNodeDomain(String nodeName) {
     return null;
   }

   public void addCoreEdgeRouterPair(String site, String router1, String router2, String
    linkname, long bw) {}

   public void addOvsRouter(String site, String router1) {}

   public void addDocker(String siteName, String nodeName, String script, String size) {}

   public void addRiakServer(String siteName, String nodeName) {}

   public void addSafeServer(String siteName, String riakIp, String safeDockerImage, String
    safeServerScript) {}

   public void addPlexusController(String controllerSite, String name) {}

  //We always add the bro when we add the edge router
   public String addBro(String broname, String domain) {
     return null;
   }

   public void stitch(String RID, String customerName, String CID, String secret,
                              String newip) {}

   public void configBroNode(String nodeName, String edgeRouter, String resourceDir, String
    SDNControllerIP, String serverurl, String sshkey) {}

   public String getDpid(String routerName, String sshkey) {
     return null;
   }

   public String addOVSRouter(String site, String name) {
     return null;
   }

   public void printNetworkInfo() {}

   public void printSliceInfo() {}

   public String getState(String resourceName) {
     return null;
   }

   public String getManagementIP(String nodeName) {
     return null;
   }

   public void deleteResource(String name) {}

   public void lockSlice() {}

   public void unLockSlice() {}

   public String getResourceByName(String name){
     return name;
   }

   public Collection<String> getNodeInterfaces(String nodeName) {
     return new ArrayList<>();
   }

   public String getNodeOfInterface(String ifName) {
     return null;
   }

   public String getLinkOfInterface(String ifName) {
     return null;
   }

   public String getMacAddressOfInterface(String ifName) {
     return null;
   }

   public Long getBandwidthOfLink(String linkName) {
     return null;
   }

   public void sleep(int seconds) {}

   public void renew(Date newdate) {}

   public void renew() {}
}
