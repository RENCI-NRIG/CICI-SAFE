package exoplex.sdx.core;

import exoplex.common.slice.SafeSlice;
import exoplex.common.slice.Scripts;
import exoplex.common.slice.SliceCommon;
import exoplex.common.utils.Exec;
import exoplex.common.utils.PathUtil;
import exoplex.common.utils.ServerOptions;
import exoplex.sdx.safe.SafeManager;
import org.apache.commons.cli.CommandLine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.renci.ahab.libndl.resources.request.ComputeNode;
import org.renci.ahab.libndl.resources.request.InterfaceNode2Net;
import org.renci.ahab.libndl.resources.request.Network;
import org.renci.ahab.libtransport.SSHAccessToken;
import org.renci.ahab.libtransport.SliceAccessContext;
import org.renci.ahab.libtransport.util.SSHAccessTokenFileFactory;
import org.renci.ahab.libtransport.util.UtilTransportException;
import exoplex.sdx.network.Link;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * @author geni-orca
 */
public class SliceManager extends SliceCommon {
  protected static String routerPattern = "(^(c|e)\\d+)";
  protected static String cRouterPattern = "(^c\\d+)";
  protected static String eRouterPattern = "(^e\\d+)";
  protected static String broPattern = "(bro\\d+_e\\d+)";
  protected static String stitchPortPattern = "(^sp-e\\d+.*)";
  protected static String stosVlanPattern = "(^stitch_(c|e)\\d+_\\d+)";
  protected static String linkPattern = "(^(c|e)link\\d+)";
  protected static String cLinkPattern = "(^clink\\d+)";
  protected static String eLinkPattern = "(^elink\\d+)";
  protected static String broLinkPattern = "(^blink_bro\\d+_e\\d+_\\d+)";
  protected  static String plexusName = "plexuscontroller";
  protected long bw = 1000000000;
  final Logger logger = LogManager.getLogger(SliceManager.class);
  protected int curip = 128;
  protected String IPPrefix = "192.168.";
  protected String mask = "/24";
  protected String scriptsdir;
  protected boolean BRO = false;
  public SliceManager() {
  }

  protected void computeIP(String prefix) {
    logger.debug(prefix);
    String[] ip_mask = prefix.split("/");
    String[] ip_segs = ip_mask[0].split("\\.");
    IPPrefix = ip_segs[0] + "." + ip_segs[1] + ".";
    curip = Integer.valueOf(ip_segs[2]);
  }

  public String getSshKey(){
    return sshkey;
  }

  @Override
  public void readConfig(String configFilePath) {
    super.readConfig(configFilePath);
    if (conf.hasPath("config.bw")) {
      bw = conf.getLong("config.bw");
    }
    if(conf.hasPath("config.ipprefix")) {
      IPPrefix = conf.getString("config.ipprefix");
      computeIP(IPPrefix);
    }
    if(conf.hasPath("config.serverurl")) {
      serverurl = conf.getString("config.serverurl");
    }
  }

  protected void getSshContext(){
    sctx = new SliceAccessContext<>();
    try {
      SSHAccessTokenFileFactory fac;
      fac = new SSHAccessTokenFileFactory("~/.ssh/id_rsa.pub", false);
      SSHAccessToken t = fac.getPopulatedToken();
      sctx.addToken("root", "root", t);
      sctx.addToken("root", t);
    } catch (UtilTransportException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  public void run(String[] args) {
    logger.info("SDX-Simple " + args[0]);

    CommandLine cmd = ServerOptions.parseCmd(args);
    String configFilePath = cmd.getOptionValue("config");
    initializeExoGENIContexts(configFilePath);

    //type=conf.getString("config.type");
    if (cmd.hasOption('d')) type = "delete";

    if (type.equals("server")) {
      long bw = 1000000000;
      if (conf.hasPath("config.bw")) {
        bw = conf.getLong("config.bw");
      }
      createAndConfigCarrierSlice(bw);
    } else if (type.equals("delete")) {
      deleteSlice(sliceName);
    } else {
      logger.debug("Warning: unknown type. Doing nothing.");
    }
    logger.info("XXXXXXXXXX Done XXXXXXXXXXXXXX");
  }


  public void createAndConfigCarrierSlice(long bw) {
    int routerNum = 4;
    try {
      routerNum = conf.getInt("config.routernum");
    } catch (Exception e) {
      logger.error("No router number specified, launching default 4 routers");
    }

    IPPrefix = conf.getString("config.ipprefix");
    String scriptsdir = conf.getString("config.scriptsdir");
    computeIP(IPPrefix);
    try {
      String carrierName = sliceName;
      //Create the slice
      SafeSlice carrier = createCarrierSlice(carrierName, routerNum, bw);
      carrier.commitAndWait();
      try {
        carrier.reloadSlice();
      }catch (Exception e){
        carrier = createCarrierSlice(carrierName, routerNum, bw);
        carrier.commitAndWait();
      }
      logger.info(String.format("Slice %s active now, making configurations", sliceName));
      carrier.runCmdSlice( Scripts.getOVSScript(), sshkey, routerPattern, true);
      carrier.copyFile2Slice(PathUtil.joinFilePath(scriptsdir, "dpid.sh"), "~/dpid.sh", sshkey);
      carrier.copyFile2Slice(PathUtil.joinFilePath(scriptsdir, "ifaces.sh"), "~/ifaces.sh",
        sshkey);
      carrier.copyFile2Slice(PathUtil.joinFilePath(scriptsdir, "ovsbridge.sh"), "~/ovsbridge.sh",
        sshkey);
      //Make sure that plexus container is running
      SDNControllerIP = carrier.getComputeNode(plexusName).getManagementIP();
      if(safeEnabled){
        if(plexusAndSafeInSlice) {
          setSafeServerIp(carrier.getComputeNode("safe-server").getManagementIP());
        }else {
          setSafeServerIp(conf.getString("config.safeserver"));
        }
      }
      //SDNControllerIP = "152.3.136.36";
      Thread.sleep(10000);
      if (!SDNControllerIP.equals("152.3.136.36") && !checkPlexus(SDNControllerIP)) {
        System.exit(-1);
      }
      checkPrerequisites(carrier);

      logger.debug("Plexus Controller IP: " + SDNControllerIP);
      carrier.runCmdSlice("/bin/bash ~/ovsbridge.sh " + SDNControllerIP + ":6633", sshkey,
          routerPattern, true);

      if (BRO) {
        configBroNodes(carrier, broPattern);
        logger.debug("Set up bro nodes");
      }
      carrier.printNetworkInfo();
    } catch (Exception e) {
      e.printStackTrace();
    }

  }

  public void configFTPService(SafeSlice carrier, String nodePattern, String username, String passwd) {
    carrier.runCmdSlice("apt-get install -y vsftpd", sshkey, nodePattern, true);
    carrier.runCmdSlice("/usr/sbin/useradd " + username + "; echo -e \"" + passwd + "\\n" +
        passwd + "\\n\" | passwd " + username, sshkey, nodePattern, true);
    carrier.runCmdSlice("mkdir /home/" + username, sshkey, nodePattern, false);
    carrier.runCmdSlice("/bin/sed -i \"s/pam_service_name=vsftpd/pam_service_name=ftp/\" " +
        "/etc/vsftpd.conf; service vsftpd restart", sshkey, nodePattern, true);
    String resource_dir = conf.getString("config.resourcedir");
    carrier.copyFile2Slice(PathUtil.joinFilePath(resource_dir, "bro/evil.txt"), "/home/" +
        username +
        "/evil" +
        ".txt",
        sshkey, nodePattern);
  }

  public void configBroNodes(SafeSlice carrier, String bropattern) {
    String resource_dir = conf.getString("config.resourcedir");
    List<Thread> tlist = new ArrayList<Thread>();
    for (ComputeNode c : carrier.getComputeNodes()) {
      if (c.getName().matches(bropattern)) {
        tlist.add(new Thread() {
          @Override
          public void run() {
            String routername = c.getName().split("_")[1];
            carrier.configBroNode(c.getName(), routername, resource_dir, SDNControllerIP, serverurl, sshkey);
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

  public void checkPrerequisites(SafeSlice serverSlice){
    //check if openvswitch is installed on all ovs nodes
    logger.debug("Start checking prerequisites");
    ArrayList<Thread> tlist = new ArrayList<>();
    for(ComputeNode node: serverSlice.getComputeNodes()){
      if(node.getName().matches(routerPattern)){
        tlist.add(new Thread(){
          @Override
          public void run(){
            checkOVS(serverSlice, node.getName());
            checkScripts(serverSlice, node.getName());
          }
        });
      }
    }
    //checkPlexus
    tlist.add(new Thread(){
      @Override
      public void run() {
        if(plexusAndSafeInSlice) {
          setSdnControllerIp(serverSlice.getComputeNode(plexusName).getManagementIP());
        }else {
          setSdnControllerIp(conf.getString("config.plexusserver"));
        }
        checkPlexus(SDNControllerIP);
      }
    });
    if(safeEnabled){
      if(plexusAndSafeInSlice) {
        setSafeServerIp(serverSlice.getComputeNode("safe-server").getManagementIP());
      }else{
        setSafeServerIp(conf.getString("config.safeserver"));
      }
      tlist.add(new Thread(){
        @Override
        public void run() {
          checkSafeServer(safeServerIp, riakIp);
          /*
          AuthorityMock mock = new AuthorityMock(safeServerIp + ":7777");
          if(!mock.verifySafePreparation()){
            mock.makeSafePreparation();
          }*/
        }
      });
    }
    tlist.forEach(t->t.start());
    tlist.forEach(t -> {
      try{t.join();
      }catch (Exception e){}
    });
    logger.debug("Finished checking prerequisites");
  }

  /*
  protected  void configSdnControllerIp(SafeSlice serverSlice, String plexusName){
    SDNControllerIP = serverSlice.getComputeNode(plexusName).getManagementIP();
  }

  protected void configSafeServerIp(SafeSlice serverSlice){
    safeServerIp = serverSlice.getComputeNode("safe-server").getManagementIP();
    safeServer = safeServerIp + ":7777";
  }
  */

  public void checkOVS(SafeSlice serverSlice, String nodeName){
    String mip = serverSlice.getComputeNode(nodeName).getManagementIP();
    String res = serverSlice.runCmdNode("ovs-vsctl show", sshkey, nodeName, false);
    if(res.contains("ovs-vsctl: command not found")){
      while(true) {
        String[] result = Exec.sshExec("root", mip, "apt-get install -y openvswitch-switch", sshkey);
        if (!result[0].startsWith("error")) {
          break;
        }
      }
    }
  }

  public void checkScripts(SafeSlice serverSlice, String nodeName){
    String res = serverSlice.runCmdNode("ls", sshkey, nodeName, false);
    if(!res.contains("ovsbridge.sh") || !res.contains("dpid.sh")){
      copyRouterScript(serverSlice, nodeName);
    }
  }

  public void copyRouterScript(SafeSlice s, String node) {
    scriptsdir = conf.getString("config.scriptsdir");
    s.copyFile2Node(PathUtil.joinFilePath(scriptsdir, "dpid.sh"), "~/dpid.sh", sshkey, node);
    s.copyFile2Node(PathUtil.joinFilePath(scriptsdir, "ifaces.sh"), "~/ifaces.sh", sshkey, node);
    s.copyFile2Node(PathUtil.joinFilePath(scriptsdir, "ovsbridge.sh"), "~/ovsbridge.sh", sshkey,
      node);
    //Make sure that plexus container is running
    logger.debug("Finished copying ovs scripts");
  }

  public void copyRouterScript(SafeSlice s) {
    scriptsdir = conf.getString("config.scriptsdir");
    s.copyFile2Slice(PathUtil.joinFilePath(scriptsdir, "dpid.sh"), "~/dpid.sh", sshkey,
      routerPattern);
    s.copyFile2Slice(PathUtil.joinFilePath(scriptsdir, "ifaces.sh"), "~/ifaces.sh", sshkey,
      routerPattern);
    s.copyFile2Slice(PathUtil.joinFilePath(scriptsdir, "ovsbridge.sh"), "~/ovsbridge.sh", sshkey,
      routerPattern);
    //Make sure that plexus container is running
    logger.debug("Finished copying ovs scripts");
  }

  protected boolean checkSafeServer(String safeIP, String riakIp) {
    if(plexusAndSafeInSlice) {
      SafeManager sm = new SafeManager(safeIP, safeKeyFile, sshkey);
      sm.verifySafeInstallation(riakIp);
    }
    return true;
  }

  protected boolean checkPlexus(String SDNControllerIP) {
    if(plexusAndSafeInSlice) {
      String result = Exec.sshExec("root", SDNControllerIP, "docker ps", sshkey)[0];
      if (result.contains("plexus")) {
        logger.debug("plexus controller has started");
      } else {
        logger.debug("plexus controller hasn't started, restarting it");
        result = Exec.sshExec("root", SDNControllerIP, "docker images", sshkey)[0];
        if (result.contains("yaoyj11/plexus")) {
          logger.debug("found plexus image, starting plexus container");
          Exec.sshExec("root", SDNControllerIP, "docker run -i -t -d -p 8080:8080 "
            + " -p 6633:6633 -p 3000:3000 -h plexus --name plexus yaoyj11/plexus", sshkey);
        } else {

          logger.debug("plexus image not found, downloading...");
          Exec.sshExec("root", SDNControllerIP, "docker pull yaoyj11/plexus", sshkey);
          Exec.sshExec("root", SDNControllerIP, "docker run -i -t -d -p 8080:8080 -p"
            + " 6633:6633 -p 3000:3000 -h plexus --name plexus yaoyj11/plexus", sshkey);
        }
        result = Exec.sshExec("root", SDNControllerIP, "docker ps", sshkey)[0];
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

  public SafeSlice createCarrierSlice(String sliceName, int num, long bw) {
    //,String stitchsubnet="", String slicesubnet="")
    logger.debug("ndllib TestDriver: START");
    SafeSlice s = SafeSlice.create(sliceName, pemLocation, keyLocation, controllerUrl, sctx);
    //String nodePostBootScript="apt-get update;apt-get -y  install quagga\n"
    //  +"sed -i -- 's/zebra=no/zebra=yes/g' /etc/quagga/daemons\n"
    //  +"sed -i -- 's/ospfd=no/ospfd=yes/g' /etc/quagga/daemons\n";
    ArrayList<ComputeNode> nodelist = new ArrayList<ComputeNode>();
    ArrayList<Network> netlist = new ArrayList<Network>();
    ArrayList<Network> stitchlist = new ArrayList<Network>();
    if (conf.hasPath("config.bro")) {
      BRO = conf.getBoolean("config.bro");
    }
    for (int i = 0; i < num; i++) {
      s.addCoreEdgeRouterPair(clientSites.get(i % clientSites.size()), "c" + i, "e" + i, "elink" + i, bw);
      nodelist.add((ComputeNode) s.getResourceByName("c" + i));
      if (BRO && i == 0) {
        long brobw = conf.getLong("config.brobw");
        ComputeNode node1 = (ComputeNode) s.getResourceByName("e" + i);
        ComputeNode broNode = s.addBro( "bro0_e" + i, node1.getDomain());
        int ip_to_use = curip++;
        Network bronet = s.addBroadcastLink(getBroLinkName(broNode.getName(),
          ip_to_use),
          bw);
        InterfaceNode2Net ifaceNode1 = (InterfaceNode2Net) bronet.stitch(node1);
        ifaceNode1.setIpAddress("192.168." + String.valueOf(ip_to_use) + ".1");
        ifaceNode1.setNetmask("255.255.255.0");
        InterfaceNode2Net ifaceNode2 = (InterfaceNode2Net) bronet.stitch(broNode);
        ifaceNode2.setIpAddress("192.168." + String.valueOf(ip_to_use) + ".2");
        ifaceNode2.setNetmask("255.255.255.0");
      }
    }
    //add Links
    for (int i = 0; i < num - 1; i++) {
      ComputeNode node0 = nodelist.get(i);
      ComputeNode node1 = nodelist.get(i + 1);
      String linkname = "clink" + i;
      Link logLink = addLink(s, linkname, node0, node1, bw);
      links.put(linkname, logLink);
    }
    if(plexusAndSafeInSlice) {
      if (safeEnabled) {
        s.addSafeServer(serverSite, riakIp);
      }
      s.addPlexusController(controllerSite, plexusName);
    }
    return s;
  }


  private Link addLink(SafeSlice s, String linkname, ComputeNode node0, ComputeNode node1,
                          Long bw) {
    Network net2 = s.addBroadcastLink(linkname, bw);
    InterfaceNode2Net ifaceNode1 = (InterfaceNode2Net) net2.stitch(node0);
    InterfaceNode2Net ifaceNode2 = (InterfaceNode2Net) net2.stitch(node1);
    Link logLink = new Link();
    logLink.addNode(node0.getName());
    logLink.addNode(node1.getName());
    logLink.setName(linkname);
    return logLink;
  }
}

