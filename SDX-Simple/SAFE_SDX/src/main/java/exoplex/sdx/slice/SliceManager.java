package exoplex.sdx.slice;

import org.renci.ahab.libtransport.util.TransportException;
import org.renci.ahab.libtransport.xmlrpc.XMLRPCTransportException;

import java.util.Collection;
import java.util.List;

public abstract class SliceManager {
  protected String pemLocation;
  protected String keyLocation;
  protected String controllerUrl;
  protected String sliceName;
  protected String sshKey;

  public SliceManager(String sliceName, String pemLocation, String keyLocation, String
    controllerUrl, String sshKey) {
    this.sliceName = sliceName;
    this.pemLocation = pemLocation;
    this.keyLocation = keyLocation;
    this.controllerUrl = controllerUrl;
    this.sshKey = sshKey;
  }


  abstract public void createSlice();

  abstract public void permitStitch(String secret, String GUID) throws TransportException;

  abstract public String permitStitch(String GUID) throws TransportException;

  abstract public void reloadSlice() throws Exception;

  abstract public void resetHostNames();

  abstract public String addComputeNode(String name);

  abstract public String addComputeNode(String site, String name);

  abstract public String stitchNetToNode(String netName, String nodeName);

  abstract public String stitchNetToNode(String netName, String nodeName, String ip, String
    netmask);

  abstract public String addComputeNode(
    String name, String nodeImageURL,
    String nodeImageHash, String nodeImageShortName, String nodeNodeType, String site,
    String nodePostBootScript);

  abstract public String addStitchPort(String name, String label, String port, long bandwidth);

  abstract public void stitchSptoNode(String spName, String nodeName);

  abstract public String addBroadcastLink(String name, long bandwidth);

  abstract public String addBroadcastLink(String name);

  abstract public String attach(String nodeName, String linkName, String ip, String netmask);

  abstract public String attach(String nodeName, String linkName);

  abstract public String getStitchingGUID(String netName);

  abstract public String getComputeNode(String nm);

  abstract public void unstitch(String stitchLinkName, String customerSlice, String customerGUID);

  abstract public String getName();

  abstract public void setName(String sliceName);

  abstract public void commit(int count, int sleepInterval) throws XMLRPCTransportException;

  abstract public void commit() throws XMLRPCTransportException;

  abstract public void delete();


  abstract public Collection<String> getAllResources();

  abstract public Collection<String> getInterfaces();

  abstract public Collection<String> getLinks();

  abstract public Collection<String> getBroadcastLinks();

  abstract public Collection<String> getComputeNodes();

  abstract public Collection<String> getStitchPorts();

  abstract public void refresh();

  abstract public void commitSlice() throws TransportException;

  abstract public void commitAndWait() throws TransportException, Exception;

  abstract public boolean commitAndWait(int interval) throws TransportException, Exception;

  abstract public boolean commitAndWait(int interval, List<String> resources) throws TransportException, Exception;

  abstract public void waitTillActive() throws Exception;

  abstract public void waitTillActive(int interval) throws Exception;

  abstract public boolean waitTillActive(int interval, List<String> resources) throws Exception;

  abstract public void copyFile2Slice(String lfile, String rfile, String privkey);

  abstract public void copyFile2Slice(String lfile, String rfile, String privkey,
                                      String patn);

  abstract public void copyFile2Node(String lfile, String rfile, String privkey, String nodeName);

  abstract public String runCmdNode(final String cmd, String nodeName, boolean repeat);

  abstract public String runCmdNode(final String cmd, String nodeName);

  abstract public int getInterfaceNum(String nodeName);

  abstract public String runCmdByIP(final String cmd, String mip, boolean repeat);

  abstract public void runCmdSlice(final String cmd, final String sshkey, final String pattern,
                                   final boolean repeat);

  abstract public void addLink(String linkName, String nodeName, long bw);

  abstract public void removeLink(String linkName);

  abstract public void addLink(String linkName, String ip, String netmask, String nodeName, long
    bw);

  abstract public void addLink(String linkName, String ip1, String ip2, String netmask, String
    node1, String node2, long bw);

  abstract public void addLink(String linkName, String
    node1, String node2, long bw);

  abstract public String getNodeDomain(String nodeName);

  abstract public void addCoreEdgeRouterPair(String site, String router1, String router2, String
    linkname, long bw);

  abstract public void addOvsRouter(String site, String router1);

  abstract public void addDocker(String siteName, String nodeName, String script, String size);

  abstract public void addRiakServer(String siteName, String nodeName);

  abstract public void addSafeServer(String siteName, String riakIp, String safeDockerImage, String
    safeServerScript);

  abstract public void addPlexusController(String controllerSite, String name);

  //We always add the bro when we add the edge router
  abstract public String addBro(String broname, String domain);

  abstract public void stitch(String RID, String customerName, String CID, String secret,
                              String newip);

  abstract public void configBroNode(String nodeName, String edgeRouter, String resourceDir, String
    SDNControllerIP, String serverurl, String sshkey);

  abstract public String getDpid(String routerName, String sshkey);

  abstract public String addOVSRouter(String site, String name);

  abstract public void printNetworkInfo();

  abstract public void printSliceInfo();

  abstract public String getState(String resourceName);

  abstract public String getManagementIP(String nodeName);

  abstract public void deleteResource(String name);

  abstract public void lockSlice();

  abstract public void unLockSlice();

  abstract public String getResourceByName(String name);

  abstract public Collection<String> getNodeInterfaces(String nodeName);

  abstract public String getNodeOfInterface(String ifName);

  abstract public String getLinkOfInterface(String ifName);

  abstract public String getMacAddressOfInterface(String ifName);

  abstract public Long getBandwidthOfLink(String linkName);
}
