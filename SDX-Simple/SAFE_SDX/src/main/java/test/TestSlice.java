package test;

import org.renci.ahab.libndl.Slice;
import sdx.core.SliceManager;

import java.util.ArrayList;
import org.apache.log4j.Logger;
import org.apache.commons.cli.*;
import org.renci.ahab.libndl.resources.request.ComputeNode;
import org.renci.ahab.libndl.resources.request.InterfaceNode2Net;
import org.renci.ahab.libndl.resources.request.Network;
import org.renci.ahab.libndl.resources.request.StitchPort;
import org.renci.ahab.libtransport.SSHAccessToken;
import org.renci.ahab.libtransport.SliceAccessContext;
import org.renci.ahab.libtransport.util.SSHAccessTokenFileFactory;
import org.renci.ahab.libtransport.util.UtilTransportException;
import org.renci.ahab.libtransport.xmlrpc.XMLRPCTransportException;

import common.utils.Exec;

public class TestSlice extends SliceManager {
  final Logger logger = Logger.getLogger(Exec.class);

  public TestSlice(String[] args) {
    CommandLine cmd = parseCmd(args);

    System.out.println("Test Slice running: " + cmd);

    String configfilepath = cmd.getOptionValue("config");

    readConfig(configfilepath);

    System.out.println("configfilepath " + configfilepath);

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
  }

  public void testBroSliceTwoPairs(){
    IPPrefix = conf.getString("config.ipprefix");
    BRO = conf.getBoolean("config.bro");
    String scriptsdir = conf.getString("config.scriptsdir");
    computeIP(IPPrefix);
    try {
      String carrierName = sliceName;
      int routernum = conf.getInt("config.routernum");
      long bw = 1500000000;
      if(conf.hasPath("config.bw")){
        bw = conf.getLong("config.bw");
      }
      Slice carrier = createTestSliceWithTwoPairs(carrierName, routernum, bw);
      String resource_dir = conf.getString("config.resourcedir");
      //Slice carrier = createCarrierSliceWithCustomerNodes(carrierName, 4, 10, 1000000, 1);
      commitAndWait(carrier);
      carrier.refresh();
      copyFile2Slice(carrier, scriptsdir + "dpid.sh", "~/dpid.sh", sshkey);
      copyFile2Slice(carrier, scriptsdir + "ovsbridge.sh", "~/ovsbridge.sh", sshkey);
      //Make sure that plexus container is running
      //SDNControllerIP = "152.3.136.36";
      SDNControllerIP = ((ComputeNode) carrier.getResourceByName("plexuscontroller")).getManagementIP();
      if (!checkPlexus(SDNControllerIP)) {
        System.exit(-1);
      }
      System.out.println("Plexus Controler IP: " + SDNControllerIP);
      runCmdSlice(carrier, "/bin/bash ~/ovsbridge.sh " + SDNControllerIP + ":6633", "(^c\\d+)",
        true, true);
      runCmdSlice(carrier, "apt-get -y install quagga", "(node\\d+)", true, true);
      runCmdSlice(carrier, "sed -i -- 's/zebra=no/zebra=yes/g' /etc/quagga/daemons", "(node\\d+)", true, true);
      //runCmdSlice(carrier, "sed -i -- 's/ospfd=no/ospfd=yes/g' /etc/quagga/daemons", sshkey,
      // "(node\\d+)", true, true);
      runCmdSlice(carrier, "echo \"1\" > /proc/sys/net/ipv4/ip_forward", "(node\\d+)",
        true, true);
      try {
        runCmdSlice(carrier, "ifconfig eth1 192.168.10.2/24 up", "(node0)", true, true);
        runCmdSlice(carrier, "ifconfig eth1 192.168.20.2/24 up", "(node1)", true, true);
        runCmdSlice(carrier, "ifconfig eth1 192.168.30.2/24 up", "(node2)", true, true);
        runCmdSlice(carrier, "ifconfig eth1 192.168.40.2/24 up", "(node3)", true, true);
      }
      catch (Exception e){
        e.printStackTrace();
      }
      runCmdSlice(carrier, "echo \"ip route 192.168.1.1/16 192.168.10.1\" >>/etc/quagga/zebra" +
        ".conf", "(node0)", true, true);
      runCmdSlice(carrier, "echo \"ip route 192.168.1.1/16 192.168.20.1\" >>/etc/quagga/zebra" +
        ".conf", "(node1)", true, true);
      runCmdSlice(carrier, "echo \"ip route 192.168.1.1/16 192.168.30.1\" >>/etc/quagga/zebra" +
        ".conf", "(node2)", true, true);
      runCmdSlice(carrier, "echo \"ip route 192.168.1.1/16 192.168.40.1\" >>/etc/quagga/zebra" +
        ".conf", "(node3)", true, true);
      runCmdSlice(carrier, "/etc/init.d/quagga restart", "(node\\d+)", true, true);

      if(BRO){
        configBroNodes(carrier, "(bro\\d+_c\\d+)");
      }

      runCmdSlice(carrier, "mkdir /home/ftp", "(node\\d+)", true, true);
      copyFile2Slice(carrier, resource_dir + "bro/evil.txt", "/home/ftp/evil.txt",
        sshkey, "(node\\d+)");
      copyFile2Slice(carrier, resource_dir + "scripts/getonefile.sh", "~/getonefile.sh",
        sshkey, "(node\\d+)");
      copyFile2Slice(carrier, resource_dir + "scripts/getnfiles.sh", "~/getnfiles.sh",
        sshkey, "(node\\d+)");
      copyFile2Slice(carrier, resource_dir + "scripts/getfiles.sh", "~/getfiles.sh",
        sshkey, "(node\\d+)");
      //}
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void testBroSliceExoGENI(){
    IPPrefix = conf.getString("config.ipprefix");
    BRO = conf.getBoolean("config.bro");
    String scriptsdir = conf.getString("config.scriptsdir");
    computeIP(IPPrefix);
    try {
      String carrierName = sliceName;
      int routernum = conf.getInt("config.routernum");
      long bw = 10000000;
      if(conf.hasPath("config.bw")){
        bw = conf.getLong("config.bw");
      }
      String resource_dir = conf.getString("config.resourcedir");
      ////Slice carrier = createCarrierSliceWithCustomerNodes(carrierName, 4, 10, 1000000, 1);
      //commitAndWait(carrier);
      //carrier.refresh();
      //copyFile2Slice(carrier, scriptsdir + "dpid.sh", "~/dpid.sh", sshkey);
      //copyFile2Slice(carrier, scriptsdir + "ovsbridge.sh", "~/ovsbridge.sh", sshkey);
      ////Make sure that plexus container is running
      ////SDNControllerIP = "152.3.136.36";
      ////if (!checkPlexus(SDNControllerIP)) {
      ////  System.exit(-1);
      ////}
      Slice carrier = getSlice(sliceProxy,carrierName);
      SDNControllerIP = ((ComputeNode) carrier.getResourceByName("plexuscontroller")).getManagementIP();
      System.out.println("Plexus Controler IP: " + SDNControllerIP);
      runCmdSlice(carrier, "/bin/bash ~/ovsbridge.sh " + SDNControllerIP + ":6633", "(^c\\d+)",
        true, true);
      runCmdSlice(carrier, "apt-get -y install quagga", "(node\\d+)", true, true);
      runCmdSlice(carrier, "sed -i -- 's/zebra=no/zebra=yes/g' /etc/quagga/daemons", "(node\\d+)", true, true);
      //runCmdSlice(carrier, "sed -i -- 's/ospfd=no/ospfd=yes/g' /etc/quagga/daemons", sshkey,
      // "(node\\d+)", true, true);
      runCmdSlice(carrier, "echo \"1\" > /proc/sys/net/ipv4/ip_forward", "(node\\d+)",
        true, true);
      try {
        runCmdSlice(carrier, "ifconfig eth1 192.168.10.2/24 up", "(node0)", true, true);
        runCmdSlice(carrier, "ifconfig eth1 192.168.20.2/24 up", "(node"+(routernum-1)
          +")", true, true);
      }
      catch (Exception e){
        e.printStackTrace();
      }
      runCmdSlice(carrier, "echo \"ip route 192.168.1.1/16 192.168.10.1\" >>/etc/quagga/zebra.conf", "(node0)", true, true);
      runCmdSlice(carrier, "echo \"ip route 192.168.1.1/16 192.168.20.1\" >>/etc/quagga/zebra.conf", "(node1)", true, true);
      runCmdSlice(carrier, "/etc/init.d/quagga restart", "(node\\d+)", true, true);

      if(BRO){
        configBroNodes(carrier, "(bro\\d+_c\\d+)");
      }

      runCmdSlice(carrier, "mkdir /home/ftp", "(node\\d+)", true, true);
      copyFile2Slice(carrier, resource_dir + "bro/evil.txt", "/home/ftp/evil.txt",
        sshkey, "(node\\d+)");
      copyFile2Slice(carrier, resource_dir + "scripts/getonefile.sh", "~/getonefile.sh",
        sshkey, "(node\\d+)");
      copyFile2Slice(carrier, resource_dir + "scripts/getfiles.sh", "~/getfiles.sh",
        sshkey, "(node\\d+)");
      //}
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public  void testSliceDynamicLinks(){
    IPPrefix = conf.getString("config.ipprefix");
    BRO = conf.getBoolean("config.bro");
    String scriptsdir = conf.getString("config.scriptsdir");
    computeIP(IPPrefix);
    try {
      String carrierName = sliceName;
      int routernum = conf.getInt("config.routernum");
      long bw = 10000000;
      if(conf.hasPath("config.bw")){
        bw = conf.getLong("config.bw");
      }
      Slice carrier = createTestSliceWithDynamicLinks(carrierName, routernum, bw);
      String resource_dir = conf.getString("config.resourcedir");
      //Slice carrier = createCarrierSliceWithCustomerNodes(carrierName, 4, 10, 1000000, 1);
      commitAndWait(carrier);
      carrier.refresh();
      copyFile2Slice(carrier, scriptsdir + "dpid.sh", "~/dpid.sh", sshkey);
      copyFile2Slice(carrier, scriptsdir + "ovsbridge.sh", "~/ovsbridge.sh", sshkey);
      //Make sure that plexus container is running
      SDNControllerIP = ((ComputeNode) carrier.getResourceByName("plexuscontroller")).getManagementIP();
      //SDNControllerIP = "152.3.136.36";
      //if (!checkPlexus(SDNControllerIP)) {
      //  System.exit(-1);
      //}
      System.out.println("Plexus Controler IP: " + SDNControllerIP);
      runCmdSlice(carrier, "/bin/bash ~/ovsbridge.sh " + SDNControllerIP + ":6633", "(c\\d+)", true, true);
      runCmdSlice(carrier, "apt-get -y install quagga", "(node\\d+)", true, true);
      runCmdSlice(carrier, "sed -i -- 's/zebra=no/zebra=yes/g' /etc/quagga/daemons", "(node\\d+)", true, true);
      //runCmdSlice(carrier, "sed -i -- 's/ospfd=no/ospfd=yes/g' /etc/quagga/daemons", sshkey,
      // "(node\\d+)", true, true);
      runCmdSlice(carrier, "echo \"1\" > /proc/sys/net/ipv4/ip_forward", "(node\\d+)", true, true);
      try {
        runCmdSlice(carrier, "ifconfig eth1 192.168.10.2/24 up", "(node0)", true, true);
        runCmdSlice(carrier, "echo \"ip route 192.168.1.1/16 192.168.10.1\" >>/etc/quagga/zebra" +
          ".conf", "(node0)", true, true);
        runCmdSlice(carrier, "echo \"ip route 192.168.1.1/16 192.168.20.1\" >>/etc/quagga/zebra" +
          ".conf", "(node1)", true, true);
        runCmdSlice(carrier, "echo \"ip route 192.168.1.1/16 192.168.30.1\" >>/etc/quagga/zebra" +
          ".conf", "(node2)", true, true);
      }
      catch (Exception e){
        e.printStackTrace();
      }
      runCmdSlice(carrier, "/etc/init.d/quagga restart", "(node\\d+)", true, true);

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void testBroSliceChameleon(){
    IPPrefix = conf.getString("config.ipprefix");
    BRO = conf.getBoolean("config.bro");
    String scriptsdir = conf.getString("config.scriptsdir");
    computeIP(IPPrefix);
    try {
      String carrierName = sliceName;
      int routernum = conf.getInt("config.routernum");
      long bw = 10000000;
      if(conf.hasPath("config.bw")){
        bw = conf.getLong("config.bw");
      }
      Slice carrier = createTestSliceWithBroAndChameleon(carrierName, routernum, bw);
      String resource_dir = conf.getString("config.resourcedir");
      //Slice carrier = createCarrierSliceWithCustomerNodes(carrierName, 4, 10, 1000000, 1);
      commitAndWait(carrier,1);
      carrier.refresh();
      copyFile2Slice(carrier, scriptsdir + "dpid.sh", "~/dpid.sh", sshkey);
      copyFile2Slice(carrier, scriptsdir + "ovsbridge.sh", "~/ovsbridge.sh", sshkey);
      runCmdSlice(carrier, "mkdir /home/ftp", "(node\\d+)", true, true);
      copyFile2Slice(carrier, resource_dir + "bro/evil.txt", "/home/ftp/evil.txt",
        sshkey, "(node\\d+)");
      copyFile2Slice(carrier, resource_dir + "scripts/getonefile.sh", "~/getonefile.sh",
        sshkey, "(node\\d+)");
      copyFile2Slice(carrier, resource_dir + "scripts/getfiles.sh", "~/getfiles.sh",
        sshkey, "(node\\d+)");
      //Make sure that plexus container is running
      SDNControllerIP = ((ComputeNode) carrier.getResourceByName("plexuscontroller")).getManagementIP();
      //SDNControllerIP = "152.3.136.36";
      //if (!checkPlexus(SDNControllerIP)) {
      //  System.exit(-1);
      //}
      System.out.println("Plexus Controler IP: " + SDNControllerIP);
      runCmdSlice(carrier, "/bin/bash ~/ovsbridge.sh " + SDNControllerIP + ":6633", "(c\\d+)", true, true);
      runCmdSlice(carrier, "apt-get -y install quagga", "(node\\d+)", true, true);
      runCmdSlice(carrier, "sed -i -- 's/zebra=no/zebra=yes/g' /etc/quagga/daemons", "(node\\d+)", true, true);
      //runCmdSlice(carrier, "sed -i -- 's/ospfd=no/ospfd=yes/g' /etc/quagga/daemons", sshkey,
      // "(node\\d+)", true, true);
      runCmdSlice(carrier, "echo \"1\" > /proc/sys/net/ipv4/ip_forward", "(node\\d+)", true, true);
      try {
        runCmdSlice(carrier, "ifconfig eth1 192.168.10.2/24 up", "(node0)", true, true);
        runCmdSlice(carrier, "echo \"ip route 10.32.90.1/24 192.168.10.1\" >>/etc/quagga/zebra" +
          ".conf", "(node0)", true, true);
      }
      catch (Exception e){
        e.printStackTrace();
      }
      runCmdSlice(carrier, "/etc/init.d/quagga restart", "(node\\d+)", true, true);

      if(BRO){
        System.out.println("config bro");
        configBroNodes(carrier, "(bro\\d+_c\\d+)");
      }
      //}
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public Slice createTestSliceWithTwoPairs(String sliceName, int num, long bw) {
    String nodeImageShortName = "Ubuntu 14.04";
    String nodeImageURL = "http://geni-orca.renci.org/owl/9dfe179d-3736-41bf-8084-f0cd4a520c2f#Ubuntu+14.04";//http://geni-images.renci.org/images/standard/ubuntu/ub1304-ovs-opendaylight-v1.0.0.xml
    String nodeImageHash = "9394ca154aa35eb55e604503ae7943ddaecc6ca5";
    String nodeNodeType = "XO Extra large";
    Slice s = createCarrierSlice(sliceName, num, bw);
    long cnodebw = 1000000000;
    //Now add two customer node to c0 and c3
    ComputeNode c0 = (ComputeNode) s.getResourceByName("c0");
    ComputeNode c3 = (ComputeNode) s.getResourceByName("c" + (num - 1));
    ComputeNode cnode0 = s.addComputeNode("node0");
    cnode0.setImage(nodeImageURL, nodeImageHash, nodeImageShortName);
    cnode0.setNodeType(nodeNodeType);
    cnode0.setDomain(clientSites.get(0));
    String scripts = "apt-get install -y vsftpd iperf\n";
    cnode0.setPostBootScript(getCustomerScript() + scripts);
    Network net0 = s.addBroadcastLink("stitch_c0_10",cnodebw);
    InterfaceNode2Net ifaceNode0 = (InterfaceNode2Net) net0.stitch(cnode0);
    ifaceNode0.setIpAddress("192.168.10.2");
    ifaceNode0.setNetmask("255.255.255.0");
    net0.stitch(c0);

    ComputeNode cnode1 = s.addComputeNode("node1");
    cnode1.setImage(nodeImageURL, nodeImageHash, nodeImageShortName);
    cnode1.setNodeType(nodeNodeType);
    cnode1.setDomain(clientSites.get(0));
    cnode1.setPostBootScript(getCustomerScript() + scripts);
    Network net1 = s.addBroadcastLink("stitch_c0_20",cnodebw);
    InterfaceNode2Net ifaceNode1 = (InterfaceNode2Net) net1.stitch(cnode1);
    ifaceNode1.setIpAddress("192.168.20.2");
    ifaceNode1.setNetmask("255.255.255.0");
    net1.stitch(c0);

    /*
    ComputeNode c1 = (ComputeNode) s.getResourceByName("c1");
    ComputeNode cnode1 = s.addComputeNode("node2");
    cnode1.setImage(nodeImageURL, nodeImageHash, nodeImageShortName);
    cnode1.setNodeType(nodeNodeType);
    cnode1.setDomain(clientSites.get(0));
    cnode1.setPostBootScript(getCustomerScript() + scripts);
    Network net1 = s.addBroadcastLink("stitch_c0_30",bw);
    InterfaceNode2Net ifaceNode1 = (InterfaceNode2Net) net1.stitch(cnode1);
    ifaceNode1.setIpAddress("192.168.30.2");
    ifaceNode1.setNetmask("255.255.255.0");
    net1.stitch(c1);
    */

    ComputeNode cnode2 = s.addComputeNode("node2");
    cnode2.setImage(nodeImageURL, nodeImageHash, nodeImageShortName);
    cnode2.setNodeType(nodeNodeType);
    cnode2.setDomain(clientSites.get(1));
    cnode2.setPostBootScript(getCustomerScript()+scripts);
    Network net2 = s.addBroadcastLink("stitch_c2_30",cnodebw);
    InterfaceNode2Net ifaceNode2 = (InterfaceNode2Net) net2.stitch(cnode2);
    ifaceNode2.setIpAddress("192.168.30.2");
    ifaceNode2.setNetmask("255.255.255.0");
    net2.stitch(c3);

    ComputeNode cnode3 = s.addComputeNode("node3");
    cnode3.setImage(nodeImageURL, nodeImageHash, nodeImageShortName);
    cnode3.setNodeType(nodeNodeType);
    cnode3.setDomain(clientSites.get(1));
    cnode3.setPostBootScript(getCustomerScript()+scripts);
    Network net3 = s.addBroadcastLink("stitch_c2_40",cnodebw);
    InterfaceNode2Net ifaceNode3 = (InterfaceNode2Net) net3.stitch(cnode3);
    ifaceNode3.setIpAddress("192.168.40.2");
    ifaceNode3.setNetmask("255.255.255.0");
    net3.stitch(c3);
    return s;
  }

  public Slice createTestSliceWithDynamicLinks(String sliceName, int num, long bw) {
    String nodeImageShortName = "Ubuntu 14.04";
    String nodeImageURL = "http://geni-orca.renci.org/owl/9dfe179d-3736-41bf-8084-f0cd4a520c2f#Ubuntu+14.04";//http://geni-images.renci.org/images/standard/ubuntu/ub1304-ovs-opendaylight-v1.0.0.xml
    String nodeImageHash = "9394ca154aa35eb55e604503ae7943ddaecc6ca5";
    String nodeNodeType = "XO Extra large";
    Slice s = createCarrierSlice(sliceName, num, bw);
    //Now add two customer node to c0 and c3
    ComputeNode c0 = (ComputeNode) s.getResourceByName("c0");
    ComputeNode c1 = (ComputeNode) s.getResourceByName("c1");
    ComputeNode c2 = (ComputeNode) s.getResourceByName("c2");
    ComputeNode cnode0 = s.addComputeNode("node0");
    cnode0.setImage(nodeImageURL, nodeImageHash, nodeImageShortName);
    cnode0.setNodeType(nodeNodeType);
    cnode0.setDomain(clientSites.get(0));
    String scripts = "apt-get install -y vsftpd iperf\n";
    cnode0.setPostBootScript(getCustomerScript() + scripts);
    Network net0 = s.addBroadcastLink("stitch_c0_10",bw);
    InterfaceNode2Net ifaceNode0 = (InterfaceNode2Net) net0.stitch(cnode0);
    ifaceNode0.setIpAddress("192.168.10.2");
    ifaceNode0.setNetmask("255.255.255.0");
    net0.stitch(c0);

    ComputeNode cnode1 = s.addComputeNode("node1");
    cnode1.setImage(nodeImageURL, nodeImageHash, nodeImageShortName);
    cnode1.setNodeType(nodeNodeType);
    cnode1.setDomain(clientSites.get(1));
    cnode1.setPostBootScript(getCustomerScript() + scripts);
    Network net1 = s.addBroadcastLink("stitch_c1_20",bw);
    InterfaceNode2Net ifaceNode1 = (InterfaceNode2Net) net1.stitch(cnode1);
    ifaceNode1.setIpAddress("192.168.20.2");
    ifaceNode1.setNetmask("255.255.255.0");
    net1.stitch(c1);

    ComputeNode cnode2 = s.addComputeNode("node2");
    cnode2.setImage(nodeImageURL, nodeImageHash, nodeImageShortName);
    cnode2.setNodeType(nodeNodeType);
    cnode2.setDomain(clientSites.get(2));
    cnode2.setPostBootScript(getCustomerScript()+scripts);
    Network net2 = s.addBroadcastLink("stitch_c2_30",bw);
    InterfaceNode2Net ifaceNode2 = (InterfaceNode2Net) net2.stitch(cnode2);
    ifaceNode2.setIpAddress("192.168.30.2");
    ifaceNode2.setNetmask("255.255.255.0");
    net2.stitch(c2);

    Network net3 = s.addBroadcastLink("dl-c0-c2",bw);
    InterfaceNode2Net ifaceNode3 = (InterfaceNode2Net) net3.stitch(c2);
    net3.stitch(c0);
    return s;
  }

  public Slice createTestSliceWithBroAndCNode(String sliceName, int num, long bw) {
    String nodeImageShortName = "Ubuntu 14.04";
    String nodeImageURL = "http://geni-orca.renci.org/owl/9dfe179d-3736-41bf-8084-f0cd4a520c2f#Ubuntu+14.04";//http://geni-images.renci.org/images/standard/ubuntu/ub1304-ovs-opendaylight-v1.0.0.xml
    String nodeImageHash = "9394ca154aa35eb55e604503ae7943ddaecc6ca5";
    String nodeNodeType = "XO Extra large";
    Slice s = createCarrierSlice(sliceName, num, bw);
    //Now add two customer node to c0 and c3
    ComputeNode c0 = (ComputeNode) s.getResourceByName("c0");
    ComputeNode c3 = (ComputeNode) s.getResourceByName("c" + (num - 1));
    ComputeNode cnode0 = s.addComputeNode("node0");
    cnode0.setImage(nodeImageURL, nodeImageHash, nodeImageShortName);
    cnode0.setNodeType(nodeNodeType);
    cnode0.setDomain(clientSites.get(0));
    String scripts = "apt-get install -y vsftpd iperf\n";
    cnode0.setPostBootScript(getCustomerScript() + scripts);
    Network net0 = s.addBroadcastLink("stitch_c0_10",bw);
    InterfaceNode2Net ifaceNode0 = (InterfaceNode2Net) net0.stitch(cnode0);
    ifaceNode0.setIpAddress("192.168.10.2");
    ifaceNode0.setNetmask("255.255.255.0");
    net0.stitch(c0);

    /*
    ComputeNode c1 = (ComputeNode) s.getResourceByName("c1");
    ComputeNode cnode1 = s.addComputeNode("node2");
    cnode1.setImage(nodeImageURL, nodeImageHash, nodeImageShortName);
    cnode1.setNodeType(nodeNodeType);
    cnode1.setDomain(clientSites.get(0));
    cnode1.setPostBootScript(getCustomerScript() + scripts);
    Network net1 = s.addBroadcastLink("stitch_c0_30",bw);
    InterfaceNode2Net ifaceNode1 = (InterfaceNode2Net) net1.stitch(cnode1);
    ifaceNode1.setIpAddress("192.168.30.2");
    ifaceNode1.setNetmask("255.255.255.0");
    net1.stitch(c1);
    */

    ComputeNode cnode2 = s.addComputeNode("node" + (num - 1));
    cnode2.setImage(nodeImageURL, nodeImageHash, nodeImageShortName);
    cnode2.setNodeType(nodeNodeType);
    cnode2.setDomain(clientSites.get((num-1)%clientSites.size()));
    cnode2.setPostBootScript(getCustomerScript()+scripts);
    Network net2 = s.addBroadcastLink("stitch_c" + (num-1) + "_20",bw);
    InterfaceNode2Net ifaceNode2 = (InterfaceNode2Net) net2.stitch(cnode2);
    ifaceNode2.setIpAddress("192.168.20.2");
    ifaceNode2.setNetmask("255.255.255.0");
    net2.stitch(c3);
    return s;
  }

  public Slice createTestSliceWithBroAndChameleon(String sliceName, int num, long bw) {
    String nodeImageShortName = "Ubuntu 14.04";
    String nodeImageURL = "http://geni-orca.renci.org/owl/9dfe179d-3736-41bf-8084-f0cd4a520c2f#Ubuntu+14.04";//http://geni-images.renci.org/images/standard/ubuntu/ub1304-ovs-opendaylight-v1.0.0.xml
    String nodeImageHash = "9394ca154aa35eb55e604503ae7943ddaecc6ca5";
    String nodeNodeType = "XO Extra large";
    Slice s = createCarrierSlice(sliceName, num, bw);
    //Now add two customer node to c0 and c3
    ComputeNode c0 = (ComputeNode) s.getResourceByName("c0");
    ComputeNode cnode0 = s.addComputeNode("node0");
    cnode0.setImage(nodeImageURL, nodeImageHash, nodeImageShortName);
    cnode0.setNodeType(nodeNodeType);
    cnode0.setDomain(clientSites.get(0));
    String scripts = "apt-get install -y vsftpd iperf\n";
    cnode0.setPostBootScript(getCustomerScript() + scripts);
    Network net0 = s.addBroadcastLink("stitch_c0_10",bw);
    InterfaceNode2Net ifaceNode0 = (InterfaceNode2Net) net0.stitch(cnode0);
    ifaceNode0.setIpAddress("192.168.10.2");
    ifaceNode0.setNetmask("255.255.255.0");
    net0.stitch(c0);
    commitAndWait(s);
    s = getSlice(sliceProxy,sliceName);
    ComputeNode c3 = (ComputeNode) s.getResourceByName("c" + (num - 1));
    String ip = "10.32.90.106/24";
    String gateway = "10.32.90.105";
    String vlan = "3290";
    String stitchport = "http://geni-orca.renci.org/owl/ion.rdf#AL2S/Chameleon/Cisco/6509/GigabitEthernet/1/1";
    String stitchname = "sp-" + c3.getName() + "-" + ip.replace("/", "__").replace(".", "_")
      + '-' + gateway.split("\\.")[3];

    System.out.println("Stitching to Chameleon {" + "stitchname: " + stitchname + " vlan:" + vlan + " stithport: " + stitchport + "}");
    StitchPort mysp = s.addStitchPort(stitchname, vlan, stitchport, bw);
    mysp.stitch(c3);

    return s;
  }

  public Slice createCarrierSliceWithCustomerNodes(String sliceName, int num, int start,
                                                          long bw, int numstitches) {//,String stitchsubnet="", String slicesubnet="")
    System.out.println("ndllib TestDriver: START");

    Slice s = Slice.create(sliceProxy, sctx, sliceName);

    String nodeImageShortName = "Ubuntu 14.04";
    String nodeImageURL = "http://geni-orca.renci.org/owl/9dfe179d-3736-41bf-8084-f0cd4a520c2f#Ubuntu+14.04";//http://geni-images.renci.org/images/standard/ubuntu/ub1304-ovs-opendaylight-v1.0.0.xml
    String nodeImageHash = "9394ca154aa35eb55e604503ae7943ddaecc6ca5";
    String nodeNodeType = "XO Medium";
    //String nodePostBootScript="apt-get update;apt-get -y  install quagga\n"
    //  +"sed -i -- 's/zebra=no/zebra=yes/g' /etc/quagga/daemons\n"
    //  +"sed -i -- 's/ospfd=no/ospfd=yes/g' /etc/quagga/daemons\n";
    String nodePostBootScript = getOVSScript();
    ArrayList<ComputeNode> nodelist = new ArrayList<ComputeNode>();
    ArrayList<Network> netlist = new ArrayList<Network>();
    ArrayList<Network> stitchlist = new ArrayList<Network>();
    for (int i = 0; i < num; i++) {

      ComputeNode node0 = s.addComputeNode(((i == 0 || i == (num - 1)) ? "node" : "c") + String.valueOf(i));
      node0.setImage(nodeImageURL, nodeImageHash, nodeImageShortName);
      node0.setNodeType(nodeNodeType);
      node0.setDomain(clientSites.get(i % clientSites.size()));
      node0.setPostBootScript(nodePostBootScript);
      nodelist.add(node0);
      //for(int j=0;j<numstitches;j++){
      //  Network net1 = s.addBroadcastLink("stitch"+String.valueOf(i)+ String.valueOf(j),bw);
      //  InterfaceNode2Net ifaceNode0 = (InterfaceNode2Net) net1.stitch(node0);
      //  ifaceNode0.setIpAddress("192.168."+String.valueOf(100+i*10+j)+".1");
      //  ifaceNode0.setNetmask("255.255.255.0");
      //  stitchlist.add(net1);
      //}
      if (i != num - 1) {
        String linkname = "clink" + String.valueOf(i);
        if (i == 0) {
          linkname = "stitch_c1_10";
        } else if (i == 2) {
          linkname = "stitch_c2_20";
        }
        Network net2 = s.addBroadcastLink(linkname, bw);
        InterfaceNode2Net ifaceNode1 = (InterfaceNode2Net) net2.stitch(node0);
        ifaceNode1.setIpAddress("192.168." + String.valueOf(start + i) + ".1");
        ifaceNode1.setNetmask("255.255.255.0");
        netlist.add(net2);
      }
      if (i != 0) {
        Network net = netlist.get(i - 1);
        InterfaceNode2Net ifaceNode1 = (InterfaceNode2Net) net.stitch(node0);
        ifaceNode1.setIpAddress("192.168." + String.valueOf(start + i - 1) + ".2");
        ifaceNode1.setNetmask("255.255.255.0");
      }
    }
    addPlexusController(s);
    try {
      s.commit();
    } catch (XMLRPCTransportException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return s;
  }
}
