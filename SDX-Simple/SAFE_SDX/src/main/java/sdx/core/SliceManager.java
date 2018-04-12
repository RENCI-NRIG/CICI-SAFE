package sdx.core;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.ArrayList;
import java.util.Date;
import java.text.SimpleDateFormat;

import common.slice.SliceCommon;
import org.apache.log4j.Logger;
import org.apache.log4j.SimpleLayout;
import org.apache.commons.cli.*;
import org.apache.commons.cli.DefaultParser;

import org.renci.ahab.libndl.LIBNDL;
import org.renci.ahab.libndl.Slice;
import org.renci.ahab.libndl.SliceGraph;
import org.renci.ahab.libndl.extras.PriorityNetwork;
import org.renci.ahab.libndl.resources.common.ModelResource;
import org.renci.ahab.libndl.resources.request.BroadcastNetwork;
import org.renci.ahab.libndl.resources.request.ComputeNode;
import org.renci.ahab.libndl.resources.request.Interface;
import org.renci.ahab.libndl.resources.request.InterfaceNode2Net;
import org.renci.ahab.libndl.resources.request.Network;
import org.renci.ahab.libndl.resources.request.Node;
import org.renci.ahab.libndl.resources.request.StitchPort;
import org.renci.ahab.libndl.resources.request.StorageNode;
import org.renci.ahab.libtransport.ISliceTransportAPIv1;
import org.renci.ahab.libtransport.ITransportProxyFactory;
import org.renci.ahab.libtransport.JKSTransportContext;
import org.renci.ahab.libtransport.PEMTransportContext;
import org.renci.ahab.libtransport.SSHAccessToken;
import org.renci.ahab.libtransport.SliceAccessContext;
import org.renci.ahab.libtransport.TransportContext;
import org.renci.ahab.libtransport.util.ContextTransportException;
import org.renci.ahab.libtransport.util.SSHAccessTokenFileFactory;
import org.renci.ahab.libtransport.util.TransportException;
import org.renci.ahab.libtransport.util.UtilTransportException;

import common.utils.Exec;
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
  protected String scriptsdir;
  protected boolean BRO = false;

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

    //type=conf.getString("config.type");
    if (cmd.hasOption('d')) type = "delete";

    refreshSliceProxy();
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
      long bw = 1000000000;
      if (conf.hasPath("config.bw")){
        bw = conf.getLong("config.bw");
      }
      createAndConfigCarrierSlice(bw);
    } else if (type.equals("delete")) {
      deleteSlice(sliceName);
    } else {
      logger.debug("Warning: unknown type. Doing nothing.");
    }

    System.out.println("XXXXXXXXXX Done XXXXXXXXXXXXXX");
  }


  public void createAndConfigCarrierSlice(long bw) {
    int routerNum = 4;
    try {
      routerNum = conf.getInt("config.routernum");
    } catch (Exception e) {
      System.out.println("No router number specified, launching default 4 routers");
    }

    IPPrefix = conf.getString("config.ipprefix");
    String scriptsdir = conf.getString("config.scriptsdir");
    computeIP(IPPrefix);
    try {
      String carrierName = sliceName;
      //Create the slice
      Slice carrier = createCarrierSlice(carrierName, routerNum, bw);
      commitAndWait(carrier);
      carrier.refresh();
      copyFile2Slice(carrier, scriptsdir + "dpid.sh", "~/dpid.sh", sshkey);
      copyFile2Slice(carrier, scriptsdir + "ovsbridge.sh", "~/ovsbridge.sh", sshkey);
      //Make sure that plexus container is running
      SDNControllerIP = ((ComputeNode) carrier.getResourceByName("plexuscontroller"))
        .getManagementIP();
      //SDNControllerIP = "152.3.136.36";
      Thread.sleep(10000);
      if (!SDNControllerIP.equals("152.3.136.36") && !checkPlexus(SDNControllerIP)) {
        System.exit(-1);
      }
      logger.debug("Plexus Controller IP: " + SDNControllerIP);
      runCmdSlice(carrier, "/bin/bash ~/ovsbridge.sh " + SDNControllerIP + ":6633",
        "(^c\\d+)", true, true);

      if(BRO) {
        configBroNodes(carrier, "(bro\\d+_c\\d+)");
        logger.debug("Set up bro nodes");
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

  }

  public void configFTPService(Slice carrier, String nodePattern, String username, String passwd){
    runCmdSlice(carrier, "/usr/sbin/useradd "+ username + "; echo -e \"" + passwd + "\\n" +
      passwd + "\\n\" | passwd " + username, nodePattern, true, true);
    runCmdSlice(carrier, "mkdir /home/" + username, nodePattern, true,
      true);
    runCmdSlice(carrier, "/bin/sed -i \"s/pam_service_name=vsftpd/pam_service_name=ftp/\" " +
      "/etc/vsftpd.conf; service vsftpd restart", nodePattern, true, true);
    String resource_dir = conf.getString("config.resourcedir");
    copyFile2Slice(carrier, resource_dir + "bro/evil.txt", "/home/"+username + "/evil.txt",
      sshkey, nodePattern);
  }

  public void configBroNodes(Slice carrier, String bropattern) {
    // Bro uses 'eth1"
    runCmdSlice(carrier, "sed -i 's/eth0/eth1/' /opt/bro/etc/node.cfg", bropattern, true, true);

    String resource_dir = conf.getString("config.resourcedir");
    copyFile2Slice(carrier, resource_dir + "bro/test.bro", "/root/test" +
      ".bro", sshkey, bropattern);
    copyFile2Slice(carrier, resource_dir + "bro/test-all-policy.bro", "/root/test-all-policy" +
        ".bro", sshkey, bropattern);
    copyFile2Slice(carrier, resource_dir + "bro/detect.bro", "/root/detect" +
        ".bro", sshkey, bropattern);
    copyFile2Slice(carrier, resource_dir + "bro/detect-all-policy.bro",
      "/root/detect-all-policy.bro", sshkey, bropattern);
    copyFile2Slice(carrier, resource_dir + "bro/evil.txt", "/root/evil.txt", sshkey,
      bropattern);
    copyFile2Slice(carrier, resource_dir + "bro/reporter.py", "/root/reporter.py", sshkey,
      bropattern);
    copyFile2Slice(carrier, resource_dir + "bro/cpu_percentage.sh", "/root/cpu_percentage.sh",
      sshkey,
      bropattern);

    runCmdSlice(carrier, "sed -i 's/bogus_addr/" + SDNControllerIP + "/' *.bro",
       bropattern, true, true);

    String url = serverurl.replace("/", "\\/");
    runCmdSlice(carrier, "sed -i 's/bogus_addr/" + url + "/g' reporter.py",
       bropattern, true, true);

    Pattern pattern = Pattern.compile(bropattern);
    for (ComputeNode c : carrier.getComputeNodes()) {
      if (pattern.matcher(c.getName()).matches()) {
        String routername = c.getName().split("_")[1];
        ComputeNode router = (ComputeNode) carrier.getResourceByName(routername);
        String mip = router.getManagementIP();
        String dpid = Exec.sshExec("root", mip, "/bin/bash ~/dpid.sh", sshkey)[0].split(" ")[1]
          .replace("\n", "");
        Exec.sshExec("root", c.getManagementIP(), "sed -i 's/bogus_dpid/" + Long.parseLong
          (dpid, 16) + "/' *.bro", sshkey);
      }
    }

    runCmdSlice(carrier, "broctl deploy&", bropattern, true, true);
    runCmdSlice(carrier, "python reporter & disown", bropattern, true, true);
    runCmdSlice(carrier, "/usr/bin/rm *.log; pkill bro; /usr/bin/screen -d -m /opt/bro/bin/bro " +
      "-i eth1 " + "test-all-policy.bro", bropattern, true, true);
  }

  public ComputeNode addOVSRouter(Slice s, String site, String name) {
    logger.debug("Adding new OVS router to slice " + s.getName());
    String nodeImageShortName = "Ubuntu 14.04";
    String nodeImageURL = "http://geni-orca.renci.org/owl/9dfe179d-3736-41bf-8084-f0cd4a520c2f#Ubuntu+14.04";//http://geni-images.renci.org/images/standard/ubuntu/ub1304-ovs-opendaylight-v1.0.0.xml
    String nodeImageHash = "9394ca154aa35eb55e604503ae7943ddaecc6ca5";
    String nodeNodeType = "XO Medium";
    String nodePostBootScript = getOVSScript();
    ComputeNode node0 = s.addComputeNode(name);
    node0.setImage(nodeImageURL, nodeImageHash, nodeImageShortName);
    node0.setNodeType(nodeNodeType);
    node0.setDomain(site);
    node0.setPostBootScript(nodePostBootScript);
    return node0;
  }

  public void copyRouterScript(Slice s, ComputeNode node) {
    scriptsdir = conf.getString("config.scriptsdir");
    copyFile2Slice(s, scriptsdir + "dpid.sh", "~/dpid.sh", sshkey, "(" + node.getName() + ")");
    copyFile2Slice(s, scriptsdir + "ovsbridge.sh", "~/ovsbridge.sh", sshkey, "(" + node.getName() + ")");
    //Make sure that plexus container is running
    logger.debug("Finished copying ovs scripts");
  }

  protected void commitAndWait(Slice s) {
    try {
      s.commit();
      waitTillActive(s);
      for (ComputeNode c : s.getComputeNodes()) {
        logger.debug("server " + c.getManagementIP() + " - " + c.getName());
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  protected boolean commitAndWait(Slice s, int interval) {
    try {
      s.commit();
      String timeStamp1 = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date());
      waitTillActive(s,interval);
      String timeStamp2 = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date());
      logger.debug("Time interval: " + timeStamp1 + " " + timeStamp2);
      return true;
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    }
  }

  protected boolean commitAndWait(Slice s, int interval, List<String> resources) {
    try {
      s.commit();
      String timeStamp1 = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date());
      waitTillActive(s,interval, resources);
      String timeStamp2 = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date());
      logger.debug("Time interval: " + timeStamp1 + " " + timeStamp2);
      return true;
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    }
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

  //We always add the bro when we add the edge router
  protected ComputeNode addBro(Slice s, String broname, ComputeNode edgerouter) {
    String broN = "Centos 7.4 Bro";
    String broURL =
      "http://geni-images.renci.org/images/standard/centos/centos7.4-bro-v1.0.4/centos7.4-bro-v1.0.4.xml";
    String broHash = "50c973571fc6da95c3f70d0f71c9aea1659ff780";
    String broType = "XO Medium";
    ComputeNode bro = s.addComputeNode(broname);
    bro.setImage(broURL, broHash, broN);
    bro.setDomain(edgerouter.getDomain());
    bro.setNodeType(broType);
    bro.setPostBootScript(getBroScripts());

    /*
    Network bronet = s.addBroadcastLink(getBroLinkName(ip_to_use), bw);
    InterfaceNode2Net ifaceNode1 = (InterfaceNode2Net) bronet.stitch(edgerouter);
    ifaceNode1.setIpAddress("192.168." + String.valueOf(ip_to_use) + ".1");
    ifaceNode1.setNetmask("255.255.255.0");
    InterfaceNode2Net ifaceNode2 = (InterfaceNode2Net) bronet.stitch(bro);
    ifaceNode2.setIpAddress("192.168." + String.valueOf(ip_to_use) + ".2");
    ifaceNode2.setNetmask("255.255.255.0");
    */
    return bro;
  }

  protected String getBroLinkName(int ip){
    return "blink_"  + ip;
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

  public Slice createCarrierSlice(String sliceName, int num, long bw) {
    //,String stitchsubnet="", String slicesubnet="")
    logger.debug("ndllib TestDriver: START");

    Slice s = Slice.create(sliceProxy, sctx, sliceName);

    NodeBaseInfo ninfo = NodeBase.getImageInfo("Ubuntu 14.04");
    String nodeImageShortName = ninfo.nisn;
    String nodeImageURL = ninfo.niurl;
    //http://geni-images.renci.org/images/standard/ubuntu/ub1304-ovs-opendaylight-v1.0.0.xml
    String nodeImageHash = ninfo.nihash;
    String nodeNodeType = "XO Extra large";

    //String nodePostBootScript="apt-get update;apt-get -y  install quagga\n"
    //  +"sed -i -- 's/zebra=no/zebra=yes/g' /etc/quagga/daemons\n"
    //  +"sed -i -- 's/ospfd=no/ospfd=yes/g' /etc/quagga/daemons\n";
    String nodePostBootScript = getOVSScript();
    ArrayList<ComputeNode> nodelist = new ArrayList<ComputeNode>();
    ArrayList<Network> netlist = new ArrayList<Network>();
    ArrayList<Network> stitchlist = new ArrayList<Network>();
    if (conf.hasPath("config.bro")) {
      BRO = conf.getBoolean("config.bro");
    }
    for (int i = 0; i < num; i++) {
      ComputeNode node0 = addComputeNode(s, "c" + String.valueOf(i), nodeImageURL,
        nodeImageHash, nodeImageShortName, nodeNodeType, clientSites.get(i % clientSites.size()),
        nodePostBootScript);
      nodelist.add(node0);
      if (BRO && i == 0) {
        long brobw = conf.getLong("config.brobw");
        ComputeNode broNode = addBro(s, "bro0_c" + i, node0);
        int ip_to_use = curip++;
        Network bronet = s.addBroadcastLink(getBroLinkName(ip_to_use), bw);
        InterfaceNode2Net ifaceNode1 = (InterfaceNode2Net) bronet.stitch(node0);
        ifaceNode1.setIpAddress("192.168." + String.valueOf(ip_to_use) + ".1");
        ifaceNode1.setNetmask("255.255.255.0");
        InterfaceNode2Net ifaceNode2 = (InterfaceNode2Net) bronet.stitch(broNode);
        ifaceNode2.setIpAddress("192.168." + String.valueOf(ip_to_use) + ".2");
        ifaceNode2.setNetmask("255.255.255.0");
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
    addPlexusController(s);
    return s;
  }

  private ComputeNode addComputeNode(
      Slice s, String name, String nodeImageURL,
      String nodeImageHash, String nodeImageShortName, String nodeNodeType, String site,
      String nodePostBootScript){
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

  protected String getOVSScript() {
    String script = "apt-get update\n" +
      "apt-get -y install openvswitch-switch\n apt-get -y install iperf\n /etc/init.d/neuca stop\n";
    return script;
  }

  protected String getPlexusScript() {
    String script = "apt-get update\n"
      + "docker pull yaoyj11/plexus\n"
      + "docker run -i -t -d -p 8080:8080 -p 6633:6633 -p 3000:3000 -h plexus --name plexus yaoyj11/plexus\n";
    //+"docker exec -d plexus /bin/bash -c  \"cd /root/;./sdx.sh\"\n";
    return script;
  }

  protected String getBroScripts(){
    String script = "yum install -y tcpdump bc htop";
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

