package exoplex.sdx.core.vfc;

import com.google.inject.Inject;
import exoplex.sdx.core.CoreProperties;
import exoplex.sdx.core.SdxManagerBase;
import exoplex.sdx.core.restutil.NotifyResult;
import exoplex.sdx.network.Link;
import exoplex.sdx.slice.SliceManager;
import exoplex.sdx.slice.vfc.VfcSliceManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import safe.Authority;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VfcSdxManager extends SdxManagerBase {
  final Logger logger = LogManager.getLogger(VfcSdxManager.class);

  static String eRouterPattern = "(net.*)";
  static String stosVlanPattern = "(^stitch_net.*_\\d+.*)";
  static String routerPattern = "(net.*)";

  @Inject
  public VfcSdxManager(Authority authority) {
    super(authority);
  }

  @Override
  public void loadSlice() throws Exception {
    serverSlice = sliceManagerFactory.create(
      coreProperties.getSliceName(),
      coreProperties.getExogeniKey(),
      coreProperties.getExogeniKey(),
      coreProperties.getExogeniSm(),
      coreProperties.getSshKey());
    serverSlice.loadSlice();
  }

  @Override
  public void initializeSdx() throws Exception {
    SDNController = coreProperties.getSdnControllerIp() + ":8080";
    OVSController = coreProperties.getSdnControllerIp() + ":6653";
    loadSdxNetwork(routerPattern, stitchPortPattern, broPattern);
  }

  @Override
  public void startSdxServer(CoreProperties coreProperties) throws Exception {
    this.coreProperties = coreProperties;
    loadSlice();
    initializeSdx();
    configRouting();
  }

  private void loadSdxNetwork(String routerpattern, String stitchportpattern, String
    bropattern) {
    logger.debug("Loading Sdx Network Topology");
    try {
      Pattern pattern = Pattern.compile(routerpattern);
      Pattern stitchpattern = Pattern.compile(stitchportpattern);
      Pattern bropatn = Pattern.compile(bropattern);
      //Nodes: Get all router information
      for (String node : serverSlice.getComputeNodes()) {
        if (pattern.matcher(node).find()) {
          putComputeNode(node);
          if (node.matches(eRouterPattern)) {
            putEdgeRouter(node);
          }
        }
      }
      logger.debug("get links from Slice");
      usedip = new HashSet<Integer>();
      HashSet<String> ifs = new HashSet<String>();
      // get all links, and then
      for (String i : serverSlice.getInterfaces()) {
        routingmanager.updateInterfaceMac(serverSlice.getNodeOfInterface(i),
          serverSlice.getLinkOfInterface(i),
          serverSlice.getMacAddressOfInterface(i)
        );
        if (i.contains("node") || i.contains("bro")) {
          continue;
        }
        logger.debug(i);
        logger.debug("linkname: " + serverSlice.getLinkOfInterface(i) + " bandwidth: " +
          serverSlice.getBandwidthOfLink(serverSlice.getLinkOfInterface(i)));
        if (ifs.contains(i) || !pattern.matcher(serverSlice.getNodeOfInterface(i)).find()) {
          logger.debug("continue");
          continue;
        }
        ifs.add(i);
        Link logLink = links.get(serverSlice.getLinkOfInterface(i));

        if (logLink == null) {
          logLink = new Link();
          logLink.setName(serverSlice.getLinkOfInterface(i));
          logLink.addNode(serverSlice.getNodeOfInterface(i));
          if (logLink.getLinkName().matches(stosVlanPattern) || logLink.getLinkName().matches(
            broLinkPattern)) {
            String[] parts = logLink.getLinkName().split("_");
            String ip = parts[parts.length - 3];
            usedip.add(Integer.valueOf(ip));
            logLink.setIP(IPPrefix + ip);
            logLink.setMask(mask);
          }
        } else {
          logLink.addNode(serverSlice.getNodeOfInterface(i));
        }
        logger.debug(serverSlice.getBandwidthOfLink(serverSlice.getLinkOfInterface(i)));
        links.put(serverSlice.getLinkOfInterface(i), logLink);
        //System.out.println(logPrefix + inode2net.getNode()+" "+inode2net.getLink());
      }
      //Stitchports
      logger.debug("setting up sttichports");
      for (String sp : serverSlice.getStitchPorts()) {
        logger.debug(sp);
        Matcher matcher = stitchpattern.matcher(sp);
        if (!matcher.find()) {
          continue;
        }
        stitchports.add(sp);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
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

    //routingmanager.waitTillAllOvsConnected(SDNController, serverSlice.mocked);

    logger.debug("setting up sttichports");
    HashSet<Integer> usedip = new HashSet<Integer>();
    HashSet<String> ifs = new HashSet<String>();
    for (String sp : stitchports) {
      logger.debug("Setting up stitchport " + sp);
      String[] parts = sp.split("-");
      String ip = parts[2].replace("__", "/").replace("_", ".");
      String nodeName = parts[1];
      String[] ipseg = ip.split("\\.");
      String gw = ipseg[0] + "." + ipseg[1] + "." + ipseg[2] + "." + parts[3];
      routingmanager.newExternalLink(sp, ip, nodeName, gw, SDNController);
    }

    Set<String> keyset = links.keySet();
    //logger.debug(keyset);
    for (String k : keyset) {
      Link logLink = links.get(k);
      logger.debug("Setting up stitch " + logLink.getLinkName());
      if (k.matches(stosVlanPattern) || k.matches(broLinkPattern)) {
        usedip.add(Integer.valueOf(logLink.getIP(1).split("\\.")[2]));
        routingmanager.newExternalLink(logLink.getLinkName(),
          ((VfcSliceManager)serverSlice).getIPOfExternalLink(logLink.getLinkName()),
          logLink.getNodeA(),
          ((VfcSliceManager)serverSlice).getGatewayOfExternalLink(logLink.getLinkName()),
          SDNController);
      }
    }

    //To Emulate dynamic allocation of links, we don't use links whose name does't contain "link"
    for (String k : keyset) {
      Link logLink = links.get(k);
      logger.debug("Setting up logLink " + logLink.getLinkName());
      if (isValidLink(k)) {
        logger.debug("Setting up logLink " + logLink.getLinkName());
        if (logLink.getIpPrefix().equals("")) {
          int ip_to_use = getAvailableIP();
          logLink.setIP(IPPrefix + ip_to_use);
          logLink.setMask(mask);
        }
        //logger.debug(logLink.nodea+":"+logLink.getIP(1)+" "+logLink.nodeb+":"+logLink.getIP(2));
        routingmanager.newInternalLink(logLink.getLinkName(),
          logLink.getIP(1),
          logLink.getNodeA(),
          logLink.getIP(2),
          logLink.getNodeB(),
          SDNController,
          logLink.getCapacity());
      }
    }
    //set ovsdb address
    routingmanager.updateAllPorts(SDNController);
    routingmanager.setOvsdbAddr(SDNController);
  }

  private int getAvailableIP() {
    int ip_to_use = curip;
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
    routingmanager.deleteAllFlows(getSDNController());
  }

  @Override
  public String connectionRequest(String self_prefix, String target_prefix,
    long bandwidth) throws Exception {
    return null;
  }

  @Override
  public NotifyResult notifyPrefix(String dest, String gateway,
                                   String customer_keyhash) {
    return null;
  }
}
