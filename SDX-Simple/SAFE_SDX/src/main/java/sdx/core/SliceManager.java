package sdx.core;

import common.slice.SafeSlice;
import common.slice.Scripts;
import common.slice.SliceCommon;
import common.utils.Exec;
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
import sdx.networkmanager.Link;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @author geni-orca
 */
public class SliceManager extends SliceCommon {
  protected static String routerPattern = "(^(c|e)\\d+)";
  protected static String cRouterPattern = "(^c\\d+)";
  protected static String eRouterPattern = "(^e\\d+)";
  protected static String broPattern = "(bro\\d+_e\\d+)";
  protected static String stitchPortPattern = "(^sp-e\\d+.*)";
  protected static String stosVlanPattern = "(^stitch_e\\d+_\\d+)";
  protected static String linkPattern = "(^(c|e)link\\d+)";
  protected static String cLinkPattern = "(^clink\\d+)";
  protected static String eLinkPattern = "(^elink\\d+)";
  protected static String broLinkPattern = "(^blink_\\d+)";
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

    CommandLine cmd = parseCmd(args);

    logger.debug("cmd " + cmd);

    String configfilepath = cmd.getOptionValue("config");

    readConfig(configfilepath);

    //type=conf.getString("config.type");
    if (cmd.hasOption('d')) type = "delete";

    //SSH context
    getSshContext();

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
      carrier.runCmdSlice( Scripts.getOVSScript(), sshkey, routerPattern, true);
      carrier.copyFile2Slice(scriptsdir + "dpid.sh", "~/dpid.sh", sshkey);
      carrier.copyFile2Slice(scriptsdir + "ifaces.sh", "~/ifaces.sh", sshkey);
      carrier.copyFile2Slice(scriptsdir + "ovsbridge.sh", "~/ovsbridge.sh", sshkey);
      //Make sure that plexus container is running
      SDNControllerIP = carrier.getComputeNode("plexuscontroller").getManagementIP();
      //SDNControllerIP = "152.3.136.36";
      Thread.sleep(10000);
      if (!SDNControllerIP.equals("152.3.136.36") && !checkPlexus(SDNControllerIP)) {
        System.exit(-1);
      }
      logger.debug("Plexus Controller IP: " + SDNControllerIP);
      carrier.runCmdSlice("/bin/bash ~/ovsbridge.sh " + SDNControllerIP + ":6633", sshkey,
          routerPattern, true);

      if (BRO) {
        configBroNodes(carrier, broPattern);
        logger.debug("Set up bro nodes");
      }
      getNetworkInfo(carrier);
    } catch (Exception e) {
      e.printStackTrace();
    }

  }

  public void configFTPService(SafeSlice carrier, String nodePattern, String username, String passwd) {
    carrier.runCmdSlice("/usr/sbin/useradd " + username + "; echo -e \"" + passwd + "\\n" +
        passwd + "\\n\" | passwd " + username, sshkey, nodePattern, true);
    carrier.runCmdSlice("mkdir /home/" + username, sshkey, nodePattern, false);
    carrier.runCmdSlice("/bin/sed -i \"s/pam_service_name=vsftpd/pam_service_name=ftp/\" " +
        "/etc/vsftpd.conf; service vsftpd restart", sshkey, nodePattern, true);
    String resource_dir = conf.getString("config.resourcedir");
    carrier.copyFile2Slice(resource_dir + "bro/evil.txt", "/home/" + username + "/evil.txt",
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


  public void copyRouterScript(SafeSlice s, ComputeNode node) {
    scriptsdir = conf.getString("config.scriptsdir");
    s.copyFile2Node(scriptsdir + "dpid.sh", "~/dpid.sh", sshkey, node.getName());
    s.copyFile2Node(scriptsdir + "ifaces.sh", "~/ifaces.sh", sshkey, node.getName());
    s.copyFile2Node(scriptsdir + "ovsbridge.sh", "~/ovsbridge.sh", sshkey,  node.getName());
    //Make sure that plexus container is running
    logger.debug("Finished copying ovs scripts");
  }

  public void copyRouterScript(SafeSlice s) {
    scriptsdir = conf.getString("config.scriptsdir");
    s.copyFile2Slice(scriptsdir + "dpid.sh", "~/dpid.sh", sshkey, routerPattern);
    s.copyFile2Slice(scriptsdir + "ifaces.sh", "~/ifaces.sh", sshkey, routerPattern);
    s.copyFile2Slice(scriptsdir + "ovsbridge.sh", "~/ovsbridge.sh", sshkey, routerPattern);
    //Make sure that plexus container is running
    logger.debug("Finished copying ovs scripts");
  }


  protected boolean checkPlexus(String SDNControllerIP) {
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
        logger.debug("Failed to start plexus controller, exit - " + result);
        return false;
      }
    }
    return true;
  }


  protected String getBroLinkName(int ip) {
    return "blink_" + ip;
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
        Network bronet = s.addBroadcastLink(getBroLinkName(ip_to_use), bw);
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
      Link link = addLink(s, linkname, node0, node1, bw);
      links.put(linkname, link);
    }
    s.addPlexusController(controllerSite);
    return s;
  }


  private Link addLink(SafeSlice s, String linkname, ComputeNode node0, ComputeNode node1,
                       Long bw) {
    Network net2 = s.addBroadcastLink(linkname, bw);
    InterfaceNode2Net ifaceNode1 = (InterfaceNode2Net) net2.stitch(node0);
    InterfaceNode2Net ifaceNode2 = (InterfaceNode2Net) net2.stitch(node1);
    Link link = new Link();
    link.addNode(node0.getName());
    link.addNode(node1.getName());
    link.setName(linkname);
    return link;
  }
}

