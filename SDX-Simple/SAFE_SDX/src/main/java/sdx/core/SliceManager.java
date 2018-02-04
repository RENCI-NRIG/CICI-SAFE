package sdx.core;

import java.util.ArrayList;
import java.util.Date;
import java.text.SimpleDateFormat;

import org.apache.log4j.Logger;
import org.apache.commons.cli.*;

import org.renci.ahab.libndl.Slice;
import org.renci.ahab.libndl.resources.request.ComputeNode;
import org.renci.ahab.libndl.resources.request.InterfaceNode2Net;
import org.renci.ahab.libndl.resources.request.Network;
import org.renci.ahab.libtransport.SSHAccessToken;
import org.renci.ahab.libtransport.SliceAccessContext;
import org.renci.ahab.libtransport.util.SSHAccessTokenFileFactory;
import org.renci.ahab.libtransport.util.UtilTransportException;
import org.renci.ahab.libtransport.xmlrpc.XMLRPCTransportException;

import sdx.utils.Exec;
import sdx.networkmanager.Link;

/**
 * @author geni-orca
 */
public class SliceManager extends SliceCommon {
  final Logger logger = Logger.getLogger(SliceManager.class);

  public SliceManager() {
  }

  protected int curip = 128;
  protected String IPPrefix = "192.168.";
  protected String mask = "/24";
  protected String riakip = "152.3.145.36";
  protected boolean BRO = false;
  //private String type;

  protected void computeIP(String prefix) {
    logger.debug(prefix);
    String[] ip_mask = prefix.split("/");
    String[] ip_segs = ip_mask[0].split("\\.");
    IPPrefix = ip_segs[0] + "." + ip_segs[1] + ".";
    curip = Integer.valueOf(ip_segs[2]);
  }

  public void run(String[] args) {
    System.out.println("SDX-Simple " + args[0]);

    CommandLine cmd = parseCmd(args);

    logger.debug("cmd " + cmd);

    String configfilepath = cmd.getOptionValue("config");

    readConfig(configfilepath);
    int routerNum = 4;
    try {
      routerNum = conf.getInt("config.routernum");
    } catch (Exception e) {
      logger.debug("No router number specified, launching default 4 routers");
    }

    logger.debug("configfilepath " + configfilepath);
    //readConfig(configfilepath);

    //type=conf.getString("config.type");
    if (cmd.hasOption('d')) {
      type = "delete";
    }

    sliceProxy = getSliceProxy(pemLocation, keyLocation, controllerUrl);

    //SSH context
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

    if (type.equals("server")) {
      IPPrefix = conf.getString("config.ipprefix");
      riakip = conf.getString("config.riakserver");
      String scriptsdir = conf.getString("config.scriptsdir");
      computeIP(IPPrefix);
      try {
        String carrierName = sliceName;
        Slice carrier = createCarrierSlice(carrierName, routerNum, 1000000);
        commitAndWait(carrier);
        carrier.refresh();
        copyFile2Slice(carrier, scriptsdir + "dpid.sh", "~/dpid.sh", sshkey);
        copyFile2Slice(carrier, scriptsdir + "ovsbridge.sh", "~/ovsbridge.sh", sshkey);
        //Make sure that plexus container is running
        SDNControllerIP = ((ComputeNode) carrier.getResourceByName("plexuscontroller"))
          .getManagementIP();
        if (!checkPlexus(SDNControllerIP)) {
          System.exit(-1);
        }
        System.out.println("Plexus Controller IP: " + SDNControllerIP);
        runCmdSlice(carrier, "/bin/bash ~/ovsbridge.sh " + SDNControllerIP + ":6633", sshkey,
          "(c\\d+)", true, true);

        String SAFEServerIP =
          ((ComputeNode) carrier.getResourceByName("safe-server")).getManagementIP();
        if (!checkSafeServer(SAFEServerIP)) {
          System.exit(-1);
        }
        System.out.println("SAFE Server IP: " + SAFEServerIP);
        //}
        configBroNodes(carrier);
        System.out.println("Set up bro nodes");
      } catch (Exception e) {
        e.printStackTrace();
      }
    } else if (type.equals("delete")) {
      Slice s2 = null;
      try {
        System.out.println("deleting slice " + sliceName);
        s2 = Slice.loadManifestFile(sliceProxy, sliceName);
        s2.delete();
      } catch (Exception e) {
        e.printStackTrace();
      }

    }
    logger.debug("XXXXXXXXXX Done XXXXXXXXXXXXXX");
  }

  public void configBroNodes(Slice carrier) {
    // Bro uses 'eth1"
    runCmdSlice(carrier, "sed -i 's/eth0/eth1/' /opt/bro/etc/node.cfg", sshkey, "(bro\\d+)" +
      "", true, true);

    String resource_dir = conf.getString("config.resourcedir");
    copyFile2Slice(carrier, resource_dir + "bro/destroy_conn.bro", "/root/destroy_conn" +
      ".bro", sshkey, "(bro\\d+)");
    copyFile2Slice(carrier, resource_dir + "bro/test-all-policy.bro", "/root/test-all-policy" +
        ".bro", sshkey, "(bro\\d+)");
    copyFile2Slice(carrier, resource_dir + "bro/test-all-policy.bro", "/root/detect-file" +
        ".bro", sshkey, "(bro\\d+)");
    copyFile2Slice(carrier, resource_dir + "bro/evil.txt", "/root/evil.txt", sshkey,
      "(bro\\d+)");

    runCmdSlice(carrier, "sed -i 's/bogus_addr/" + SDNControllerIP + "/' destroy_conn.bro",
      sshkey, "(bro\\d+)", true, true);
    for (ComputeNode c : carrier.getComputeNodes()) {
      if (c.getName().contains("bro")) {
        String routername = c.getName().replace("bro", "c");
        ComputeNode router = (ComputeNode) carrier.getResourceByName(routername);
        String mip = router.getManagementIP();
        String dpid = Exec.sshExec("root", mip, "/bin/bash ~/dpid.sh", sshkey).split(" ")[1]
          .replace("\n", "");
        Exec.sshExec("root", c.getManagementIP(), "sed -i 's/bogus_dpid/" + Long.parseLong(dpid, 16) + "/' destroy_conn.bro", sshkey);
      }
    }
  }
  protected void commitAndWait(Slice s) {
    try {
      s.commit();
      waitTillActive(s);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  protected void commitAndWait(Slice s, int interval) {
    try {
      s.commit();
      String timeStamp1 = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date());
      waitTillActive(s,interval);
      String timeStamp2 = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date());
      System.out.println("Time interval: " + timeStamp1 + " " + timeStamp2);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  protected boolean checkSafeServer(String SDNControllerIP) {
    String result = Exec.sshExec("root", SDNControllerIP, "docker ps", sshkey);
    if (result.contains("safe")) {
      logger.debug("safe server has started");
    } else {
      String script = getSafeScript(riakip);
      logger.debug("safe server hasn't started, retrying...");
      Exec.sshExec("root", SDNControllerIP, "docker pull yaoyj11/safeserver", sshkey);
      Exec.sshExec("root", SDNControllerIP, script, sshkey);
      result = Exec.sshExec("root", SDNControllerIP, "docker ps", sshkey);
      if (!result.contains("safe")) {
        logger.debug("Failed to start safe controller, exit");
        System.out.println("Failed to start safe controller, exit");
        System.exit(-1);
        return false;
      }
      logger.debug("safe server has started");
    }
    return true;
  }

  protected boolean checkPlexus(String SDNControllerIP) {
    String result = Exec.sshExec("root", SDNControllerIP, "docker ps", sshkey);
    if (result.contains("plexus")) {
      logger.debug("plexus controller has started");
    } else {
      logger.debug("plexus controller hasn't started, restarting it");
      result = Exec.sshExec("root", SDNControllerIP, "docker images", sshkey);
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
      result = Exec.sshExec("root", SDNControllerIP, "docker ps", sshkey);
      if (result.contains("plexus")) {
        logger.debug("plexus controller has started");
      } else {
        logger.debug("Failed to start plexus controller, exit");
        return false;
      }
    }
    return true;
  }

  //We always add the bro when we add the edge router
  protected ComputeNode addBro(Slice s, String broname, ComputeNode edgerouter, int ip_to_use, long bw) {
    String broN = "Centos 7.4 Bro";
    String broURL =
      "http://geni-images.renci.org/images/standard/centos/centos7.4-bro-v1.0.4/centos7.4-bro-v1.0.4.xml";
    String broHash = "50c973571fc6da95c3f70d0f71c9aea1659ff780";
    String broType = "XO Extra large";
    ComputeNode bro = s.addComputeNode(broname);
    bro.setImage(broURL, broHash, broN);
    bro.setDomain(edgerouter.getDomain());
    bro.setNodeType(broType);
    bro.setPostBootScript("yum install -y tcpdump");

    Network bronet = s.addBroadcastLink("blink_" + ip_to_use, bw);
    InterfaceNode2Net ifaceNode1 = (InterfaceNode2Net) bronet.stitch(edgerouter);
    ifaceNode1.setIpAddress("192.168." + String.valueOf(ip_to_use) + ".1");
    ifaceNode1.setNetmask("255.255.255.0");

    InterfaceNode2Net ifaceNode2 = (InterfaceNode2Net) bronet.stitch(bro);
    ifaceNode2.setIpAddress("192.168." + String.valueOf(ip_to_use) + ".2");
    ifaceNode2.setNetmask("255.255.255.0");
    return bro;
  }

  protected void deleteSlice(String sliceName){
    Slice s2 = null;
    try {
      System.out.println("deleting slice " + sliceName);
      s2 = Slice.loadManifestFile(sliceProxy, sliceName);
      s2.delete();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public Slice createCarrierSlice(String sliceName, int num, long bw) {
    //,String stitchsubnet="", String slicesubnet="")
    logger.debug("ndllib TestDriver: START");

    Slice s = Slice.create(sliceProxy, sctx, sliceName);

    String nodeImageShortName = "Ubuntu 14.04";
    String nodeImageURL = "http://geni-images.renci.org/images/standard/ubuntu/ub1404-v1.0.4.xml";
    //http://geni-images.renci.org/images/standard/ubuntu/ub1304-ovs-opendaylight-v1.0.0.xml
    String nodeImageHash = "9394ca154aa35eb55e604503ae7943ddaecc6ca5";
    String nodeNodeType = "XO Extra large";

    //String nodePostBootScript="apt-get update;apt-get -y  install quagga\n"
    //  +"sed -i -- 's/zebra=no/zebra=yes/g' /etc/quagga/daemons\n"
    //  +"sed -i -- 's/ospfd=no/ospfd=yes/g' /etc/quagga/daemons\n";
    String nodePostBootScript = getOVSScript(SDNControllerIP);
    ArrayList<ComputeNode> nodelist = new ArrayList<ComputeNode>();
    ArrayList<Network> netlist = new ArrayList<Network>();
    ArrayList<Network> stitchlist = new ArrayList<Network>();
    boolean BRO = false;
    if (conf.hasPath("config.bro")) {
      BRO = conf.getBoolean("config.bro");
    }
    for (int i = 0; i < num; i++) {
      ComputeNode node0 = addComputeNode(s, "c" + String.valueOf(i), nodeImageURL,
        nodeImageHash, nodeImageShortName, nodeNodeType, clientSites.get(i % clientSites.size()),
        nodePostBootScript);
      nodelist.add(node0);
      if (BRO && i ==0) {
        addBro(s, "bro" + i, node0, curip++, bw);
      }
    }
    //add Links
    for(int i = 0; i<num-1; i++){
      ComputeNode node0 = nodelist.get(i);
      ComputeNode node1 = nodelist.get(i+1);
      String linkname = "clink" + i;
      Link link = addLink(s, linkname, node0, node1, bw);
      links.put(linkname, link);
    }
    //addSafeServer(s, riakip);
    addPlexusController(s);
    return s;
  }

  private  ComputeNode addComputeNode(Slice s, String name, String nodeImageURL, String
    nodeImageHash, String nodeImageShortName, String nodeNodeType, String site, String
    nodePostBootScript){
    ComputeNode node0 = s.addComputeNode(name);
    node0.setImage(nodeImageURL, nodeImageHash, nodeImageShortName);
    node0.setNodeType(nodeNodeType);
    node0.setDomain(site);
    node0.setPostBootScript(nodePostBootScript);
    return node0;
  }

  private Link addLink(Slice s, String linkname, ComputeNode node0, ComputeNode node1,
                              Long bw){
    Network net2 = s.addBroadcastLink(linkname, bw);
    InterfaceNode2Net ifaceNode1 = (InterfaceNode2Net) net2.stitch(node0);
    InterfaceNode2Net ifaceNode2 = (InterfaceNode2Net) net2.stitch(node1);
    Link link = new Link();
    link.addNode(node0.getName());
    link.addNode(node1.getName());
    link.setName(linkname);
    return link;
  }


  protected void addSafeServer(Slice s, String rip) {
    String dockerImageShortName = "Ubuntu 14.04 Docker";
    String dockerImageURL =
      "http://geni-images.renci.org/images/standard/docker/ubuntu-14.0.4/ubuntu-14.0.4-docker.xml";
    //http://geni-images.renci.org/images/standard/ubuntu/ub1304-ovs-opendaylight-v1.0.0.xml
    String dockerImageHash = "b4ef61dbd993c72c5ac10b84650b33301bbf6829";
    String dockerNodeType = "XO Large";
    ComputeNode node0 = s.addComputeNode("safe-server");
    node0.setImage(dockerImageURL, dockerImageHash, dockerImageShortName);
    node0.setNodeType(dockerNodeType);
    node0.setDomain(serverSite);
    node0.setPostBootScript(getSafeScript(rip));
  }

  protected void addPlexusController(Slice s) {
    String dockerImageShortName = "Ubuntu 14.04 Docker";
    String dockerImageURL =
      "http://geni-images.renci.org/images/standard/docker/ubuntu-14.0.4/ubuntu-14.0.4-docker.xml";
    //http://geni-images.renci.org/images/standard/ubuntu/ub1304-ovs-opendaylight-v1.0.0.xml
    String dockerImageHash = "b4ef61dbd993c72c5ac10b84650b33301bbf6829";
    String dockerNodeType = "XO Large";
    ComputeNode node0 = s.addComputeNode("plexuscontroller");
    node0.setImage(dockerImageURL, dockerImageHash, dockerImageShortName);
    node0.setNodeType(dockerNodeType);
    node0.setDomain(controllerSite);
    node0.setPostBootScript(getPlexusScript());
  }

  protected String getOVSScript(String cip) {
    String script = "apt-get update\n" +
      "apt-get -y install openvswitch-switch\n apt-get -y install iperf\n /etc/init.d/neuca stop\n";
    return script;
  }

  protected String getSafeScript(String riakip) {
    String script = "apt-get update\n"
      + "docker pull yaoyj11/safeserver\n"
      + "docker run -i -t -d -p 7777:7777 -h safe --name safe yaoyj11/safeserver\n"
      + "docker exec -d safe /bin/bash -c  \"cd /root/safe;export SBT_HOME=/opt/sbt-0.13.12;"
      + "export SCALA_HOME=/opt/scala-2.11.8;sed -i 's/RIAKSERVER/" + riakip + "/g' "
      + "safe-server/src/main/resources/application.conf;./sdx.sh\"\n";
    return script;
  }

  protected String getRiakScript() {
    String script = "docker pull yaoyj11/riakimg\n";
    return script;
  }

  protected String getPlexusScript() {
    String script = "apt-get update\n"
      + "docker pull yaoyj11/plexus\n"
      + "docker run -i -t -d -p 8080:8080 -p 6633:6633 -p 3000:3000 -h plexus --name plexus yaoyj11/plexus\n";
    //+"docker exec -d plexus /bin/bash -c  \"cd /root/;./sdx.sh\"\n";
    return script;
  }

  protected String getCustomerScript() {
    String nodePostBootScript="apt-get update;apt-get -y install quagga\n"
      +"sed -i -- 's/zebra=no/zebra=yes/g' /etc/quagga/daemons\n"
      +"sed -i -- 's/ospfd=no/ospfd=yes/g' /etc/quagga/daemons\n"
      +"echo \"1\" > /proc/sys/net/ipv4/ip_forward\n"
      +"/etc/init.d/neuca stop\n";
    return nodePostBootScript;
  }
}

