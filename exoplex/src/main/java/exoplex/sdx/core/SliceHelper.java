package exoplex.sdx.core;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import exoplex.common.utils.PathUtil;
import exoplex.common.utils.ServerOptions;
import exoplex.demo.singlesdx.SingleSdxModule;
import exoplex.sdx.network.Link;
import exoplex.sdx.network.RoutingManager;
import exoplex.sdx.safe.SafeManager;
import exoplex.sdx.slice.Scripts;
import exoplex.sdx.slice.SliceManager;
import exoplex.sdx.slice.SliceManagerFactory;
import org.apache.commons.cli.CommandLine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Core;
import safe.Authority;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * @author geni-orca
 */
public class SliceHelper {
  protected static String routerPattern = "(^(c|e)\\d+)";
  protected static String cRouterPattern = "(^c\\d+)";
  protected static String eRouterPattern = "(^e\\d+)";
  protected static String broPattern = "(bro\\d+_e\\d+)";
  protected static String stitchPortPattern = "(^sp-e\\d+.*)";
  protected static String stosVlanPattern = "(^stitch_(c|e)\\d+_\\d+.*)";
  protected static String linkPattern = "(^(c|e)link\\d+)";
  protected static String cLinkPattern = "(^clink\\d+)";
  protected static String eLinkPattern = "(^elink\\d+)";
  protected static String broLinkPattern = "(^blink_bro\\d+_e\\d+_\\d+)";
  protected static String plexusName = "plexuscontroller";
  final Logger logger = LogManager.getLogger(SliceHelper.class);
  protected int curip = 128;
  protected String IPPrefix = "192.168.";
  protected String mask = "/24";
  protected Authority authority;
  protected CoreProperties coreProperties;

  protected HashMap<String, Link> links = new HashMap<String, Link>();
  protected HashMap<String, ArrayList<String>> computenodes = new HashMap<String, ArrayList<String>>();
  protected ArrayList<String> stitchports = new ArrayList<>();

  @Inject
  protected SliceManagerFactory sliceManagerFactory;

  @Inject
  public SliceHelper(Authority authority) {
    this.authority = authority;
  }

  public static void main(String[] args) throws Exception {
    Injector injector = Guice.createInjector(new SingleSdxModule());
    SliceHelper sliceHelper = injector.getInstance(SliceHelper.class);
    sliceHelper.run(args);
    System.out.println("Done - complete");
  }

  public String getServerUrl() {
    return coreProperties.getServerUrl();
  }

  public void setServerUrl(String serverUrl) {
    coreProperties.setServerUrl(serverUrl);
  }

  public void setRiakIP(String riakIP) {
    coreProperties.setRiakIp(riakIP);
  }

  public void resetHostNames(SliceManager slice) {
    try {
      slice.resetHostNames();
    } catch (Exception e) {

    }
  }

  public void setBw(long bw) {
    coreProperties.setBw(bw);
  }

  public void processArgs(String[] args) {
    CommandLine cmd = ServerOptions.parseCmd(args);
    String configFilePath = cmd.getOptionValue("config");
    this.readConfig(configFilePath);

    if (cmd.hasOption('d')) {
      coreProperties.setType("delete");
    }
  }

  protected boolean isValidLink(String key) {
    Link logLink = links.get(key);
    if (!key.contains("stitch") && !key.contains("blink") && logLink != null && logLink.getInterfaceB() != null) {
      return true;
    } else {
      return false;
    }
  }

  public void sleep(int sec) {
    try {
      Thread.sleep(sec * 1000);                 //1000 milliseconds is one second.
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
  }

  protected void computeIP(String prefix) {
    String[] ip_mask = prefix.split("/");
    String[] ip_segs = ip_mask[0].split("\\.");
    IPPrefix = ip_segs[0] + "." + ip_segs[1] + ".";
    curip = Integer.valueOf(ip_segs[2]);
  }

  public String getSshKey() {
    return coreProperties.getSshKey();
  }

  public void readConfig(String configFilePath) {
    logger.info("reading from config " + configFilePath);
    coreProperties = new CoreProperties(configFilePath);
    computeIP(coreProperties.getIpPrefix());
  }

  public void run(String[] args) {
    CommandLine cmd = ServerOptions.parseCmd(args);
    String configFilePath = cmd.getOptionValue("config");
    this.readConfig(configFilePath);
    //type=conf.getString("config.type");
    if (cmd.hasOption('d')) {
      coreProperties.setType("delete");
    }

    if (coreProperties.getType().equals("server")) {
      createAndConfigCarrierSlice(coreProperties.getBw());
    } else if (coreProperties.getType().equals("delete")) {
      deleteSlice();
    } else {
      logger.debug("Warning: unknown type. Doing nothing.");
    }
    logger.info("XXXXXXXXXX Done XXXXXXXXXXXXXX");
  }

  public void deleteSlice() {
    SliceManager s = sliceManagerFactory.create(
      coreProperties.getSliceName(),
      coreProperties.getExogeniKey(),
      coreProperties.getExogeniKey(),
      coreProperties.getExogeniSm(),
      coreProperties.getSshKey());
    s.delete();
  }

  public void deleteSlice(String sliceName) {
    SliceManager s = sliceManagerFactory.create(
      sliceName,
      coreProperties.getExogeniKey(),
      coreProperties.getExogeniKey(),
      coreProperties.getExogeniSm(),
      coreProperties.getSshKey());
    s.delete();
  }


  public void createAndConfigCarrierSlice(long bw) {
    int routerNum = coreProperties.getClientSites().size();

    String scriptsdir = coreProperties.getScriptsDir();
    computeIP(coreProperties.getIpPrefix());
    try {
      SliceManager carrier = createCarrierSlice(coreProperties.getSliceName(), routerNum, bw);
      carrier.commitAndWait();
      logger.info(String.format("Slice %s active now, making configurations", coreProperties
        .getSliceName()));
      carrier.runCmdSlice(Scripts.getOVSScript(), coreProperties.getSshKey(), routerPattern, true);
      carrier.copyFile2Slice(PathUtil.joinFilePath(scriptsdir, "dpid.sh"), "~/dpid.sh",
        coreProperties.getSshKey());
      carrier.copyFile2Slice(PathUtil.joinFilePath(scriptsdir, "ifaces.sh"), "~/ifaces.sh",
        coreProperties.getSshKey());
      carrier.copyFile2Slice(PathUtil.joinFilePath(scriptsdir, "ovsbridge.sh"), "~/ovsbridge.sh",
        coreProperties.getSshKey());
      checkSdxPrerequisites(carrier);

      logger.debug("Plexus Controller IP: " + coreProperties.getSdnControllerIp());
      carrier.runCmdSlice("sudo /bin/bash ~/ovsbridge.sh " + coreProperties.getSdnControllerIp() +
          ":6633", coreProperties.getSshKey(),
        routerPattern, true);

      if (coreProperties.isBroEnabled()) {
        configBroNodes(carrier, broPattern);
        logger.debug("Set up bro nodes");
      }
      carrier.printNetworkInfo();
    } catch (Exception e) {
      e.printStackTrace();
    }

  }

  public void configFTPService(SliceManager carrier, String nodePattern, String username, String passwd) {
    carrier.runCmdSlice(Scripts.installVsftpd(), coreProperties.getSshKey(), nodePattern, true);
    carrier.runCmdSlice("sudo /usr/sbin/useradd " + username + "; echo -e \"" + passwd + "\\n" +
      passwd + "\\n\" | passwd " + username, coreProperties.getSshKey(), nodePattern, true);
    carrier.runCmdSlice("mkdir /home/" + username, coreProperties.getSshKey(), nodePattern, false);
    carrier.runCmdSlice("sudo /bin/sed -i \"s/pam_service_name=vsftpd" +
        "/pam_service_name=ftp/\" " +
        "/etc/vsftpd.conf;" +
        "sudo service vsftpd restart", coreProperties.getSshKey(), nodePattern,
      true);
    String resource_dir = coreProperties.getResourceDir();
    carrier.copyFile2Slice(PathUtil.joinFilePath(resource_dir, "bro/evil.txt"), "/home/" +
        username +
        "/evil" +
        ".txt",
      coreProperties.getSshKey(), nodePattern);
  }

  public void configBroNodes(SliceManager carrier, String bropattern) {
    String resource_dir = coreProperties.getResourceDir();
    List<Thread> tlist = new ArrayList<Thread>();
    for (String c : carrier.getComputeNodes()) {
      if (c.matches(bropattern)) {
        tlist.add(new Thread() {
          @Override
          public void run() {
            String routername = c.split("_")[1];
            carrier.configBroNode(c, routername, resource_dir, coreProperties.getSdnControllerIp(),
              coreProperties.getServerUrl(), coreProperties
                .getSshKey
                  ());
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

  public void checkSdxPrerequisites(SliceManager serverSlice) {
    //check if openvswitch is installed on all ovs nodes
    logger.debug("Start checking prerequisites");
    ArrayList<Thread> tlist = new ArrayList<>();
    for (String node : serverSlice.getComputeNodes()) {
      if (node.matches(routerPattern)) {
        tlist.add(new Thread() {
          @Override
          public void run() {
            checkOVS(serverSlice, node);
            checkScripts(serverSlice, node);
          }
        });
      }
    }
    //checkPlexus
    tlist.add(new Thread() {
      @Override
      public void run() {
        if (coreProperties.isPlexusInSlice()) {
          coreProperties.setSdnControllerIp(serverSlice.getManagementIP(plexusName));
        }
        checkPlexus(serverSlice, coreProperties.getSdnControllerIp(), CoreProperties.getPlexusImage());
      }
    });
    if (coreProperties.isSafeEnabled()) {
      if (coreProperties.isSafeInSlice()) {
        coreProperties.setSafeServerIp(serverSlice.getManagementIP("safe-server"));
      }
      tlist.add(new Thread() {
        @Override
        public void run() {
          checkSafeServer(coreProperties.getSafeServerIp(), coreProperties.getRiakIp());
          authority.setSafeServer(coreProperties.getSafeServer());
          authority.makeSafePreparation();
        }
      });
    }
    tlist.forEach(t -> t.start());
    tlist.forEach(t -> {
      try {
        t.join();
      } catch (Exception e) {
      }
    });
    logger.debug("Finished checking prerequisites");
  }

  /*
  protected  void configSdnControllerIp(SliceManager serverSlice, String plexusName){
    SDNControllerIP = serverSlice.getComputeNode(plexusName).getManagementIP();
  }

  protected void configSafeServerIp(SliceManager serverSlice){
    safeServerIp = serverSlice.getComputeNode("safe-server").getManagementIP();
    safeServer = safeServerIp + ":7777";
  }
  */

  public void checkOVS(SliceManager serverSlice, String nodeName) {
    String res = serverSlice.runCmdNode("sudo ovs-vsctl show", nodeName, false);
    if (res.contains("ovs-vsctl: command not found")) {
      while (true) {
        String result = serverSlice.runCmdNode(Scripts.installOVS(), nodeName, true);
        if (!result.startsWith("error")) {
          break;
        }
      }
    }
  }

  public void checkScripts(SliceManager serverSlice, String nodeName) {
    String res = serverSlice.runCmdNode("ls", nodeName, false);
    if (!res.contains("ovsbridge.sh") || !res.contains("dpid.sh") || !res.contains("ifaces.sh")) {
      copyRouterScript(serverSlice, nodeName);
    }
  }

  public void copyRouterScript(SliceManager s, String node) {
    String scriptsdir = coreProperties.getScriptsDir();
    s.copyFile2Node(PathUtil.joinFilePath(scriptsdir, "dpid.sh"), "~/dpid.sh", coreProperties.getSshKey(), node);
    s.copyFile2Node(PathUtil.joinFilePath(scriptsdir, "ifaces.sh"), "~/ifaces.sh", coreProperties.getSshKey(), node);
    s.copyFile2Node(PathUtil.joinFilePath(scriptsdir, "ovsbridge.sh"), "~/ovsbridge.sh", coreProperties.getSshKey(),
      node);
    //Make sure that plexus container is running
    logger.debug("Finished copying ovs scripts");
  }

  public void copyRouterScript(SliceManager s) {
    String scriptsdir = coreProperties.getScriptsDir();
    s.copyFile2Slice(PathUtil.joinFilePath(scriptsdir, "dpid.sh"), "~/dpid.sh", coreProperties.getSshKey(),
      routerPattern);
    s.copyFile2Slice(PathUtil.joinFilePath(scriptsdir, "ifaces.sh"), "~/ifaces.sh", coreProperties.getSshKey(),
      routerPattern);
    s.copyFile2Slice(PathUtil.joinFilePath(scriptsdir, "ovsbridge.sh"), "~/ovsbridge.sh", coreProperties.getSshKey(),
      routerPattern);
    //Make sure that plexus container is running
    logger.debug("Finished copying ovs scripts");
  }

  protected boolean checkSafeServer(String safeIP, String riakIp) {
    logger.info(String.format("checking safe server with image %s", coreProperties.getSafeDockerImage()));
    if (coreProperties.isSafeInSlice()) {
      SafeManager sm = new SafeManager(safeIP, coreProperties.getSafeKeyFile(), coreProperties.getSshKey());
      sm.verifySafeInstallation(riakIp);
    }
    return true;
  }

  protected boolean checkPlexus(SliceManager serverSlice, String SDNControllerIP, String
    plexusImage) {
    if (coreProperties.isPlexusInSlice()) {
      String result = serverSlice.runCmdByIP(Scripts.dockerPs(), SDNControllerIP,
        false);
      if (result.contains("plexus")) {
        logger.debug("plexus controller has started");
      } else {
        logger.debug("plexus controller hasn't started, restarting it");
        result = serverSlice.runCmdByIP("sudo docker images", SDNControllerIP, false);
        if (result.contains(plexusImage)) {
          logger.debug("found plexus image, starting plexus container");
          serverSlice.runCmdByIP(String.format("sudo docker run -i -t -d -p 8080:8080 "
              + " -p 6633:6633 -p 3000:3000 -h plexus --name plexus %s", plexusImage),
            SDNControllerIP, false);
        } else {

          logger.debug("plexus image not found, downloading...");
          serverSlice.runCmdByIP(String.format("sudo docker pull %s", plexusImage),
            SDNControllerIP, false);
          serverSlice.runCmdByIP(String.format("sudo docker run -i -t -d -p 8080:8080 -p"
              + " 6633:6633 -p 3000:3000 -h plexus --name plexus %s", plexusImage), SDNControllerIP,
            false);
        }
        result = serverSlice.runCmdByIP(Scripts.dockerPs(), SDNControllerIP, false);
        if (result.contains("plexus")) {
          logger.debug("plexus controller has started");
        } else {
          logger.error("Failed to start plexus controller, exit - " + result);
          return false;
        }
      }
    }
    return true;
  }

  protected String getBroLinkName(String broName, int ip) {
    return "blink_" + broName + "_" + ip;
  }

  private void clearLinks(String file) {
    try {
      FileOutputStream writer = new FileOutputStream(file);
      writer.write(("").getBytes());
      writer.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public void setClientSites(List<String> clientSites) {
    coreProperties.setClientSites(clientSites);
  }

  public String getSliceName() {
    return coreProperties.getSliceName();
  }

  public void setSliceName(String sliceName) {
    coreProperties.setSliceName(sliceName);
  }

  public SliceManager createCarrierSlice(String sliceName, int num, long bw) {
    //,String stitchsubnet="", String slicesubnet="")
    SliceManager s = sliceManagerFactory.create(sliceName,
      coreProperties.getExogeniKey(),
      coreProperties.getExogeniKey(),
      coreProperties.getExogeniSm(),
      coreProperties.getSshKey());
    s.createSlice();
    //String nodePostBootScript="apt-get update;apt-get -y  install quagga\n"
    //  +"sed -i -- 's/zebra=no/zebra=yes/g' /etc/quagga/daemons\n"
    //  +"sed -i -- 's/ospfd=no/ospfd=yes/g' /etc/quagga/daemons\n";
    ArrayList<String> nodelist = new ArrayList<>();
    ArrayList<String> netlist = new ArrayList<>();
    ArrayList<String> stitchlist = new ArrayList<>();
    for (int i = 0; i < num; i++) {
      s.addOVSRouter(coreProperties.getClientSites().get(i % coreProperties.getClientSites().size()), "e" + i);
      nodelist.add("e" + i);
      if (coreProperties.isBroEnabled() && i == 0) {
        long brobw = coreProperties.getBroBw();
        String node1 = "e" + i;
        String broNode = s.addBro("bro0_e" + i, s.getNodeDomain(node1));
        int ip_to_use = curip++;

        /*
        Network bronet = s.addBroadcastLink(getBroLinkName(broNode.getName(),
          ip_to_use),
          bw);
        InterfaceNode2Net ifaceNode1 = (InterfaceNode2Net) bronet.stitch(node1);
        ifaceNode1.setIpAddress("192.168." + String.valueOf(ip_to_use) + ".1");
        ifaceNode1.setNetmask("255.255.255.0");
        InterfaceNode2Net ifaceNode2 = (InterfaceNode2Net) bronet.stitch(broNode);
        ifaceNode2.setIpAddress("192.168." + String.valueOf(ip_to_use) + ".2");
        ifaceNode2.setNetmask("255.255.255.0");
        */

        String bronet = s.addBroadcastLink(getBroLinkName(broNode,
          ip_to_use),
          bw);
        s.stitchNetToNode(bronet, node1, "192.168." + String.valueOf(ip_to_use) + ".1",
          "255.255.255.0");
        s.stitchNetToNode(bronet, broNode, "192.168." + String.valueOf(ip_to_use) + ".2",
          "255.255.255.0");
      }
    }
    //add Links
    for (int i = 0; i < num - 1; i++) {
      String node0 = nodelist.get(i);
      String node1 = nodelist.get(i + 1);
      String linkname = "clink" + i;
      Link logLink = addLink(s, linkname, node0, node1, bw);
      links.put(linkname, logLink);
    }
    if (coreProperties.isSafeEnabled() && coreProperties.isSafeInSlice()) {
      s.addSafeServer(coreProperties.getServerSite(), coreProperties.getRiakIp(), coreProperties
          .getSafeDockerImage(),
       coreProperties.getSafeServerScript());
    }
    if (coreProperties.isPlexusInSlice()) {
      s.addPlexusController(coreProperties.getSdnSite(), plexusName);
    }
    return s;
  }


  private Link addLink(SliceManager s, String linkname, String node0, String node1,
                       Long bw) {
    String net2 = s.addBroadcastLink(linkname, bw);
    s.stitchNetToNode(net2, node0);
    s.stitchNetToNode(net2, node1);

    Link logLink = new Link();
    logLink.addNode(node0);
    logLink.addNode(node1);
    logLink.setName(linkname);
    return logLink;
  }
}

