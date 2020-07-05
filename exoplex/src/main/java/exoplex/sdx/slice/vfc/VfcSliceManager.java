package exoplex.sdx.slice.vfc;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import exoplex.sdx.network.Link;
import exoplex.sdx.network.NetworkManager;
import exoplex.sdx.network.Router;
import exoplex.sdx.slice.SliceManager;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.renci.ahab.libtransport.util.TransportException;
import org.renci.ahab.libtransport.xmlrpc.XMLRPCTransportException;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class VfcSliceManager extends SliceManager {

  final static Logger logger = LogManager.getLogger(VfcSliceManager.class);

  NetworkManager networkManager;

  private Map<String, Set<String>> routersMap = new HashMap<>();
  private Map<String, String> linksMap = new HashMap<>();

  @Inject
  public VfcSliceManager(@Assisted("sliceName") String sliceName,
                         @Assisted("pem") String pemLocation,
                         @Assisted("key") String keyLocation,
                         @Assisted("controller") String controllerUrl,
                         @Assisted("ssh") String sshKey) {
    super(sliceName, pemLocation, keyLocation, controllerUrl, sshKey);
    this.mocked = false;
    networkManager = new NetworkManager();
  }

  public void createSlice() {
  }

  public void permitStitch(String secret, String GUID) throws TransportException {
  }

  public boolean revokeStitch(String GUID) throws TransportException {
    return true;
  }

  public String permitStitch(String GUID) throws TransportException {
    return null;
  }

  public void loadSlice() throws Exception {
    networkManager.putRouter(new Router("vfc-1", "0000fe754c80b54d", "152.3" +
      ".137.55:8080", null));
    networkManager.addLink("stitch_vfc0-1_192_168_200_1_24",
      "192.168.200.1/24",
      "vfc-1",
      "192.168.200.15");
    //networkManager.addLink("stitch_net-physnet1_192_168_201_1_24", "192.168.201.1/24", "net" +
    //  "-physnet1", "192.168.201.10");
    //networkManager.addLink("stitch_net-physnet1_192_168_202_1_24", "192.168.202.1/24", "net" +
    //  "-physnet1", "192.168.202.10");
  }

  public void expectOneMoreInterface(String node){

  }

  public void waitForNewInterfaces(String node) {

  }

  public void loadSlice(String topofile) throws Exception {
    JSONParser jsonParser = new JSONParser();
    topofile = topofile.replace("~", System.getProperty("user.home"));
    try (FileReader reader = new FileReader(topofile)) {
      //Read JSON file
      Object vfc = jsonParser.parse(reader);

      JSONArray vfcList = (JSONArray) vfc;

      //Iterate over employee array
      vfcList.forEach( obj -> parseVfcObject( (JSONObject) obj ) );

    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    } catch (ParseException e) {
      e.printStackTrace();
    }
  }

  private void parseVfcObject(JSONObject obj) {
    if (obj.containsKey("router")) {
      obj = (JSONObject) obj.get("router");
      try{
        networkManager.putRouter(
          new Router((String)obj.get("name"),
            (String) obj.get("dpid"),
            (String) obj.get("controller"),
          null));
        Set<String> routerSet = routersMap.computeIfAbsent((String)obj.get("site"),
          k -> new HashSet<>());
        routerSet.add((String) obj.get("name"));
      } catch (Exception e) {
        logger.warn(String.format("unknow router format:\n%s", obj.toString()));
      }
    } else if(obj.containsKey("stitch")) {
      obj = (JSONObject) obj.get("stitch");
      try{
        networkManager.addLink(
          (String) obj.get("name"),
          null,
          (String) obj.get("router"),
          null
        );
        String vlan = (String) obj.get("vlan");
        String site = (String) obj.get("site");
        linksMap.put(site + vlan, (String) obj.get("name"));
      } catch (Exception e) {
        logger.warn(String.format("unknow router format:\n%s", obj.toString()));
      }

    } else if(obj.containsKey("link")) {
      obj = (JSONObject) obj.get("link");
      try {
        networkManager.addLink((String) obj.get("name"),
          null,
          (String) obj.get("node1"),
          null,
          (String) obj.get("node2"),
          (Long) obj.getOrDefault("bw", 10000000000l));
      } catch (Exception e) {
        logger.warn(String.format("unknown link format: \n %s", obj.toString()));
      }
    } else {
      throw new NotImplementedException();
    }
  }

  public String getIPOfExternalLink(String linkName) {
    return networkManager.getInterfaceIP(networkManager.getLink(linkName).getInterfaceA());
  }

  public String getStitchName(String site, String vlan) {
    return linksMap.getOrDefault(site + vlan, null);
  }

  public String getNodeBySite(String site) {
    return routersMap.get(site).iterator().next();
  }

  public String getGatewayOfExternalLink(String linkName) {
    return networkManager.getGateWayOfExternalLink(linkName);
  }

  public void resetHostNames() {
  }

  public String addComputeNode(String name) {
    return name;
  }

  public String addComputeNode(String site, String name) {
    return name;
  }

  public String stitchNetToNode(String netName, String nodeName) {
    return null;
  }

  public String stitchNetToNode(String netName, String nodeName, String ip, String netmask) {
    return null;
  }

  public String addComputeNode(String name, String nodeImageURL, String nodeImageHash,
                               String nodeImageShortName, String nodeNodeType, String site,
                               String nodePostBootScript) {
    return null;
  }

  public String addStitchPort(String name, String label, String port, long bandwidth) {
    return null;
  }

  public void stitchSptoNode(String spName, String nodeName) {
  }

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
    return nm;
  }

  public void unstitch(String stitchLinkName, String customerSlice, String customerGUID) {
  }

  public String getName() {
    return null;
  }

  public void setName(String sliceName) {
  }

  public void commit(int count, int sleepInterval) throws XMLRPCTransportException {
  }

  public void commit() throws XMLRPCTransportException {
  }

  public void delete() {
  }


  public Collection<String> getAllResources() {
    return new ArrayList<>();
  }

  public Collection<String> getInterfaces() {
    return networkManager.getInterfaces();
  }

  public Collection<String> getLinks() {
    List<String> linkNames = new ArrayList<>();
    networkManager.getLinks().forEach(link -> linkNames.add(link.getLinkName()));
    return linkNames;
  }

  public Collection<String> getBroadcastLinks() {
    return new ArrayList<>();
  }

  public Collection<String> getComputeNodes() {
    Collection<String> nodes = new ArrayList<>();
    networkManager.getRouters().forEach(router -> nodes.add(router.getRouterName()));
    return nodes;
  }

  public Link getLink(String linkName) {
    return networkManager.getLink(linkName);
  }

  public Collection<String> getStitchPorts() {
    return new ArrayList<>();
  }

  public void refresh() {
  }

  public void commitSlice() throws TransportException {
  }

  public void commitAndWait() throws Exception {
  }

  public boolean commitAndWait(int interval) throws Exception {
    return true;
  }

  public boolean commitAndWait(int interval, List<String> resources) throws Exception {
    return true;
  }

  public void waitTillActive() throws Exception {
  }

  public void waitTillActive(int interval) throws Exception {
  }

  public boolean waitTillActive(int interval, List<String> resources) throws Exception {
    return true;
  }

  public void copyFile2Slice(String lfile, String rfile, String privkey) {
  }

  public void copyFile2Slice(String lfile, String rfile, String privkey, String patn) {
  }

  public void copyFile2Node(String lfile, String rfile, String privkey, String nodeName) {
  }

  public String runCmdNode(final String cmd, String nodeName, boolean repeat) {
    return "";
  }

  public String runCmdNode(final String cmd, String nodeName) {
    return "";
  }

  public List<ImmutablePair<String, String>> getPhysicalInterfaces(String nodeName) {
    return new ArrayList<>();
  }

  public String runCmdByIP(final String cmd, String mip, boolean repeat) {
    return "";
  }

  public void runCmdSlice(final String cmd, final String sshkey, final String pattern,
                          final boolean repeat) {
  }

  public void addLink(String linkName, String nodeName, long bw) {
  }

  public void removeLink(String linkName) {
  }

  public void addLink(String linkName, String ip, String netmask, String nodeName, long bw) {
  }

  public void addLink(String linkName, String ip1, String ip2, String netmask, String node1,
                      String node2, long bw) {
  }

  public void addLink(String linkName, String node1, String node2, long bw) {
  }

  public String getNodeDomain(String nodeName) {
    for(String site: routersMap.keySet()) {
      if(routersMap.get(site).contains(nodeName)) {
        return site;
      }
    }
    return null;
  }

  public void addCoreEdgeRouterPair(String site, String router1, String router2, String linkname,
                                    long bw) {
  }

  public void addOvsRouter(String site, String router1) {
  }

  public void addDocker(String siteName, String nodeName, String script, String size) {
  }

  public void addRiakServer(String siteName, String nodeName) {
  }

  public void addSafeServer(String siteName, String riakIp, String safeDockerImage,
                            String safeServerScript) {
  }

  public void addPlexusController(String controllerSite, String name) {
  }

  //We always add the bro when we add the edge router
  public String addBro(String broname, String domain) {
    return null;
  }

  public void stitch(String RID, String customerName, String CID, String secret, String newip) {
  }

  public void configBroNode(String nodeName, String edgeRouter, String resourceDir,
                            String SDNControllerIP, String serverurl, String sshkey) {
  }

  public String getDpid(String routerName, String sshkey) {
    return networkManager.getRouter(routerName).getDPID();
  }

  @Override
  public String getController(String routerName) {
    return networkManager.getRouter(routerName).getController();
  }

  public String addOVSRouter(String site, String name) {
    return null;
  }

  public void printNetworkInfo() {
  }

  public void printSliceInfo() {
  }

  public String getState(String resourceName) {
    return null;
  }

  public String getManagementIP(String nodeName) {
    return null;
  }

  public void deleteResource(String name) {
  }

  public void lockSlice() {
  }

  public void unLockSlice() {
  }

  public String getResourceByName(String name) {
    return name;
  }

  public Collection<String> getNodeInterfaces(String nodeName) {
    return new ArrayList<>();
  }

  public String getNodeOfInterface(String ifName) {
    return ifName.split(":")[0];
  }

  public String getLinkOfInterface(String ifName) {
    return ifName.split(":")[1];
  }

  public String getMacAddressOfInterface(String ifName) {
    return null;
  }

  public Long getBandwidthOfLink(String linkName) {
    return null;
  }

  public void sleep(int seconds) {
  }

  public void renew(Date newdate) {
  }

  public void renew() {
  }
}
