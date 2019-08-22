package exoplex.demo.cnert;

import com.google.inject.Inject;
import exoplex.common.utils.Exec;
import exoplex.common.utils.PathUtil;
import exoplex.sdx.core.CoreProperties;
import exoplex.sdx.core.SliceHelper;
import exoplex.sdx.network.RoutingManager;
import exoplex.sdx.slice.Scripts;
import exoplex.sdx.slice.SliceManager;
import exoplex.sdx.slice.SliceManagerFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.renci.ahab.libtransport.util.TransportException;
import org.renci.ahab.libtransport.xmlrpc.XMLRPCTransportException;
import safe.Authority;

import java.util.ArrayList;

public class CnertTestSlice extends SliceHelper {
  final Logger logger = LogManager.getLogger(Exec.class);

  @Inject
  SliceManagerFactory sliceManagerFactory;

  @Inject
  public CnertTestSlice(Authority authority) {
    super(authority);
  }

  public CnertTestSlice() {
    super(null);
  }


  public void testBroSliceTwoPairs() {
    IPPrefix = coreProperties.getIpPrefix();
    String scriptsdir = coreProperties.getScriptsDir();
    computeIP(IPPrefix);
    try {
      String carrierName = coreProperties.getSliceName();
      int routernum = coreProperties.getClientSites().size();
      long bw = 1500000000;
      SliceManager carrier = createTestSliceWithTwoPairs(carrierName, routernum, bw);
      String resource_dir = coreProperties.getResourceDir();
      //Slice carrier = createCarrierSliceWithCustomerNodes(carrierName, 4, 10, 1000000, 1);
      carrier.commitAndWait();
      carrier.refresh();
      carrier.copyFile2Slice(PathUtil.joinFilePath(scriptsdir, "dpid.sh"), "~/dpid.sh", coreProperties.getSshKey());
      carrier.copyFile2Slice(PathUtil.joinFilePath(scriptsdir, "ovsbridge.sh"), "~/ovsbridge.sh",
        coreProperties.getSshKey());
      //Make sure that plexus container is running
      //SDNControllerIP = "152.3.136.36";
      if (!checkPlexus(carrier, coreProperties.getSdnControllerIp(), CoreProperties.getPlexusImage())) {
        System.exit(-1);
      }
      System.out.println("Plexus Controler IP: " + coreProperties.getSdnControllerIp());
      carrier.runCmdSlice("/bin/bash ~/ovsbridge.sh " + coreProperties.getSdnControllerIp() + ":6633",
        coreProperties.getSshKey(),
        "(^c\\d+)", true);
      carrier.runCmdSlice(Scripts.installQuagga(), "(node\\d+)", coreProperties.getSshKey(), true);
      carrier.runCmdSlice("sed -i -- 's/zebra=no/zebra=yes/g' /etc/quagga/daemons", coreProperties.getSshKey(), "(node\\d+)", true);
      //carrier.runCmdSlice("sed -i -- 's/ospfd=no/ospfd=yes/g' /etc/quagga/daemons", coreProperties.getSshKey(),
      // "(node\\d+)", true);
      carrier.runCmdSlice("echo \"1\" > /proc/sys/net/ipv4/ip_forward", coreProperties.getSshKey(), "(node\\d+)", true);
      try {
        carrier.runCmdSlice("ifconfig eth1 192.168.10.2/24 up", coreProperties.getSshKey(), "(node0)", true);
        carrier.runCmdSlice("ifconfig eth1 192.168.20.2/24 up", coreProperties.getSshKey(), "(node1)", true);
        carrier.runCmdSlice("ifconfig eth1 192.168.30.2/24 up", coreProperties.getSshKey(), "(node2)", true);
        carrier.runCmdSlice("ifconfig eth1 192.168.40.2/24 up", coreProperties.getSshKey(), "(node3)", true);
      } catch (Exception e) {
        e.printStackTrace();
      }
      carrier.runCmdSlice("echo \"ip route 192.168.1.1/16 192.168.10.1\" >>/etc/quagga/zebra" +
        ".conf", coreProperties.getSshKey(), "(node0)", true);
      carrier.runCmdSlice("echo \"ip route 192.168.1.1/16 192.168.20.1\" >>/etc/quagga/zebra" +
        ".conf", coreProperties.getSshKey(), "(node1)", true);
      carrier.runCmdSlice("echo \"ip route 192.168.1.1/16 192.168.30.1\" >>/etc/quagga/zebra" +
        ".conf", coreProperties.getSshKey(), "(node2)", true);
      carrier.runCmdSlice("echo \"ip route 192.168.1.1/16 192.168.40.1\" >>/etc/quagga/zebra" +
        ".conf", coreProperties.getSshKey(), "(node3)", true);
      carrier.runCmdSlice("/etc/init.d/quagga restart", coreProperties.getSshKey(), "(node\\d+)", true);

      if (coreProperties.isBroEnabled()) {
        configBroNodes(carrier, "(bro\\d+_c\\d+)");
      }

      configFTPService(carrier, "(node\\d+)", "ftpuser", "ftp");
      carrier.copyFile2Slice(PathUtil.joinFilePath(resource_dir, "bro/evil.txt"),
        "/home/ftpuser/evil.txt", coreProperties.getSshKey(), "(node\\d+)");
      carrier.copyFile2Slice(PathUtil.joinFilePath(resource_dir, "scripts/getnfiles.sh"),
        "~/getnfiles.sh", coreProperties.getSshKey(), "(node\\d+)");
      carrier.copyFile2Slice(PathUtil.joinFilePath(resource_dir, "scripts/getfiles.sh"),
        "~/getfiles.sh", coreProperties.getSshKey(), "(node\\d+)");
      //}
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void testBroSliceExoGENI() {
    IPPrefix = coreProperties.getIpPrefix();
    computeIP(IPPrefix);
    try {
      String carrierName = coreProperties.getSliceName();
      int routernum = coreProperties.getClientSites().size();
      String resource_dir = coreProperties.getResourceDir();
      ////Slice carrier = createCarrierSliceWithCustomerNodes(carrierName, 4, 10, 1000000, 1);
      //commitAndWait(carrier);
      //carrier.refresh();
      //carrier.copyFile2Slice(scriptsdir + "dpid.sh", "~/dpid.sh", coreProperties.getSshKey());
      //carrier.copyFile2Slice(scriptsdir + "ovsbridge.sh", "~/ovsbridge.sh", coreProperties.getSshKey());
      ////Make sure that plexus container is running
      ////SDNControllerIP = "152.3.136.36";
      ////if (!checkPlexus(SDNControllerIP)) {
      ////  System.exit(-1);
      ////}
      SliceManager carrier = sliceManagerFactory.create(coreProperties.getSliceName(),
        coreProperties.getExogeniKey(),
        coreProperties.getExogeniKey(),
        coreProperties.getExogeniSm(),
        coreProperties.getSshKey());
      carrier.loadSlice();
      System.out.println("Plexus Controler IP: " + coreProperties.getSdnControllerIp());
      carrier.runCmdSlice("/bin/bash ~/ovsbridge.sh " + coreProperties.getSdnControllerIp() + ":6633",
        coreProperties.getSshKey(),
        "(^c\\d+)",
        true);
      carrier.runCmdSlice(Scripts.installQuagga(), coreProperties.getSshKey(), "(node\\d+)", true);
      carrier.runCmdSlice("sed -i -- 's/zebra=no/zebra=yes/g' /etc/quagga/daemons", coreProperties.getSshKey(), "(node\\d+)", true);
      //carrier.runCmdSlice("sed -i -- 's/ospfd=no/ospfd=yes/g' /etc/quagga/daemons", coreProperties.getSshKey(),
      // "(node\\d+)", true);
      carrier.runCmdSlice("echo \"1\" > /proc/sys/net/ipv4/ip_forward", coreProperties.getSshKey(), "(node\\d+)",
        true);
      try {
        carrier.runCmdSlice("ifconfig eth1 192.168.10.2/24 up", coreProperties.getSshKey(), "(node0)", true);
        carrier.runCmdSlice("ifconfig eth1 192.168.20.2/24 up", coreProperties.getSshKey(), "(node" + (routernum - 1)
          + ")", true);
      } catch (Exception e) {
        e.printStackTrace();
      }
      carrier.runCmdSlice("echo \"ip route 192.168.1.1/16 192.168.10.1\" >>/etc/quagga/zebra.conf", coreProperties.getSshKey(), "(node0)", true);
      carrier.runCmdSlice("echo \"ip route 192.168.1.1/16 192.168.20.1\" >>/etc/quagga/zebra.conf", coreProperties.getSshKey(), "(node1)", true);
      carrier.runCmdSlice("/etc/init.d/quagga restart", coreProperties.getSshKey(), "(node\\d+)", true);

      if (coreProperties.isBroEnabled()) {
        configBroNodes(carrier, "(bro\\d+_c\\d+)");
      }

      carrier.runCmdSlice("mkdir /home/ftp", coreProperties.getSshKey(), "(node\\d+)", true);
      carrier.copyFile2Slice(PathUtil.joinFilePath(resource_dir, "bro/evil.txt"),
        "/home/ftp/evil.txt", coreProperties.getSshKey(), "(node\\d+)");
      carrier.copyFile2Slice(PathUtil.joinFilePath(resource_dir, "scripts/getonefile.sh"),
        "~/getonefile.sh", coreProperties.getSshKey(), "(node\\d+)");
      carrier.copyFile2Slice(PathUtil.joinFilePath(resource_dir, "scripts/getfiles.sh"),
        "~/getfiles.sh", coreProperties.getSshKey(), "(node\\d+)");
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void testSliceDynamicLinks() {
    IPPrefix = coreProperties.getIpPrefix();
    String scriptsdir = coreProperties.getScriptsDir();
    computeIP(IPPrefix);
    try {
      String carrierName = coreProperties.getSliceName();
      int routernum = coreProperties.getClientSites().size();
      long bw = 10000000;
      SliceManager carrier = createTestSliceWithDynamicLinks(carrierName, routernum, bw);
      //Slice carrier = createCarrierSliceWithCustomerNodes(carrierName, 4, 10, 1000000, 1);
      carrier.commitAndWait();
      carrier.refresh();
      carrier.copyFile2Slice(PathUtil.joinFilePath(scriptsdir, "dpid.sh"), "~/dpid.sh", coreProperties.getSshKey());
      carrier.copyFile2Slice(PathUtil.joinFilePath(scriptsdir, "ovsbridge.sh"), "~/ovsbridge.sh",
        coreProperties.getSshKey());
      //Make sure that plexus container is running
      coreProperties.setSdnControllerIp(carrier.getManagementIP("plexuscontroller"));
      //SDNControllerIP = "152.3.136.36";
      //if (!checkPlexus(SDNControllerIP)) {
      //  System.exit(-1);
      //}
      System.out.println("Plexus Controler IP: " + coreProperties.getSdnControllerIp());
      carrier.runCmdSlice("/bin/bash ~/ovsbridge.sh " + coreProperties.getSdnControllerIp() + ":6633",
        coreProperties.getSshKey(),
        "(c\\d+)", true);
      carrier.runCmdSlice(Scripts.installQuagga(), coreProperties.getSshKey(), "(node\\d+)", true);
      carrier.runCmdSlice("sed -i -- 's/zebra=no/zebra=yes/g' /etc/quagga/daemons", coreProperties.getSshKey(), "(node\\d+)", true);
      //carrier.runCmdSlice("sed -i -- 's/ospfd=no/ospfd=yes/g' /etc/quagga/daemons", coreProperties.getSshKey(),
      // "(node\\d+)", true);
      carrier.runCmdSlice("echo \"1\" > /proc/sys/net/ipv4/ip_forward", coreProperties.getSshKey(), "(node\\d+)", true);
      try {
        carrier.runCmdSlice("ifconfig eth1 192.168.10.2/24 up", coreProperties.getSshKey(), "(node0)", true);
        carrier.runCmdSlice("echo \"ip route 192.168.1.1/16 192.168.10.1\" >>/etc/quagga/zebra" +
          ".conf", coreProperties.getSshKey(), "(node0)", true);
        carrier.runCmdSlice("echo \"ip route 192.168.1.1/16 192.168.20.1\" >>/etc/quagga/zebra" +
          ".conf", coreProperties.getSshKey(), "(node1)", true);
        carrier.runCmdSlice("echo \"ip route 192.168.1.1/16 192.168.30.1\" >>/etc/quagga/zebra" +
          ".conf", coreProperties.getSshKey(), "(node2)", true);
      } catch (Exception e) {
        e.printStackTrace();
      }
      carrier.runCmdSlice("/etc/init.d/quagga restart", coreProperties.getSshKey(), "(node\\d+)", true);

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void testBroSliceChameleon() {
    IPPrefix = coreProperties.getIpPrefix();
    String scriptsdir = coreProperties.getScriptsDir();
    computeIP(IPPrefix);
    try {
      String carrierName = coreProperties.getSliceName();
      int routernum = coreProperties.getClientSites().size();
      long bw = 10000000;
      SliceManager carrier = createTestSliceWithBroAndChameleon(carrierName, routernum, bw);
      String resource_dir = coreProperties.getResourceDir();
      //Slice carrier = createCarrierSliceWithCustomerNodes(carrierName, 4, 10, 1000000, 1);
      carrier.commitAndWait(1);
      carrier.refresh();
      carrier.copyFile2Slice(PathUtil.joinFilePath(scriptsdir, "dpid.sh"), "~/dpid.sh", coreProperties.getSshKey());
      carrier.copyFile2Slice(PathUtil.joinFilePath(scriptsdir, "ovsbridge.sh"), "~/ovsbridge.sh",
        coreProperties.getSshKey());
      carrier.runCmdSlice("mkdir /home/ftp", coreProperties.getSshKey(), "(node\\d+)", true);
      carrier.copyFile2Slice(PathUtil.joinFilePath(resource_dir, "bro/evil.txt"),
        "/home/ftp/evil.txt", coreProperties.getSshKey(), "(node\\d+)");
      carrier.copyFile2Slice(PathUtil.joinFilePath(resource_dir, "scripts/getonefile.sh"),
        "~/getonefile.sh", coreProperties.getSshKey(), "(node\\d+)");
      carrier.copyFile2Slice(PathUtil.joinFilePath(resource_dir, "scripts/getfiles.sh"),
        "~/getfiles.sh", coreProperties.getSshKey(), "(node\\d+)");
      //Make sure that plexus container is running
      coreProperties.setSdnControllerIp(carrier.getManagementIP("plexuscontroller"));
      //SDNControllerIP = "152.3.136.36";
      //if (!checkPlexus(SDNControllerIP)) {
      //  System.exit(-1);
      //}
      System.out.println("Plexus Controler IP: " + coreProperties.getSdnControllerIp());
      carrier.runCmdSlice("sudo /bin/bash ~/ovsbridge.sh " + coreProperties.getSdnControllerIp() + ":6633",
        coreProperties
          .getSshKey(),
        "(c\\d+)", true);
      carrier.runCmdSlice(Scripts.installQuagga(), coreProperties.getSshKey(), "(node\\d+)", true);
      carrier.runCmdSlice("sed -i -- 's/zebra=no/zebra=yes/g' /etc/quagga/daemons", coreProperties.getSshKey(), "(node\\d+)", true);
      //carrier.runCmdSlice("sed -i -- 's/ospfd=no/ospfd=yes/g' /etc/quagga/daemons", coreProperties.getSshKey(),
      // "(node\\d+)", true);
      carrier.runCmdSlice("echo \"1\" > /proc/sys/net/ipv4/ip_forward", coreProperties.getSshKey(), "(node\\d+)", true);
      try {
        carrier.runCmdSlice("ifconfig eth1 192.168.10.2/24 up", coreProperties.getSshKey(), "(node0)", true);
        carrier.runCmdSlice("echo \"ip route 10.32.90.1/24 192.168.10.1\" >>/etc/quagga/zebra" +
          ".conf", coreProperties.getSshKey(), "(node0)", true);
      } catch (Exception e) {
        e.printStackTrace();
      }
      carrier.runCmdSlice("/etc/init.d/quagga restart", coreProperties.getSshKey(), "(node\\d+)", true);

      if (coreProperties.isBroEnabled()) {
        System.out.println("config bro");
        configBroNodes(carrier, "(bro\\d+_c\\d+)");
      }
      //}
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public SliceManager createTestSliceWithTwoPairs(String sliceName, int num, long bw) {
    String nodeImageShortName = "Ubuntu 14.04";
    String nodeImageURL = "http://geni-orca.renci.org/owl/9dfe179d-3736-41bf-8084-f0cd4a520c2f#Ubuntu+14.04";//http://geni-images.renci.org/images/standard/ubuntu/ub1304-ovs-opendaylight-v1.0.0.xml
    String nodeImageHash = "9394ca154aa35eb55e604503ae7943ddaecc6ca5";
    String nodeNodeType = "XO Extra large";
    SliceManager s = createCarrierSlice(sliceName, num, bw);
    long cnodebw = 1000000000;
    //Now add two customer node to c0 and c3
    String c3 = "c" + (num - 1);

    String scripts = Scripts.installVsftpd() + Scripts.installIperf();

    String nodeName = s.addComputeNode("node0", nodeImageURL, nodeImageHash, nodeImageShortName,
      nodeNodeType, coreProperties.getClientSites().get(0), scripts);
    String netName = s.addBroadcastLink("stitch_c0_10", cnodebw);
    String interfaceName = s.stitchNetToNode(netName, nodeName, "192.168.10.2", "255.255.255.0");
    s.stitchNetToNode(netName, "c0");

    String cnode1 = s.addComputeNode("node1", nodeImageURL, nodeImageHash, nodeImageShortName,
      nodeNodeType, coreProperties.getClientSites().get(0), scripts);
    String net1 = s.addBroadcastLink("stitch_c0_20", cnodebw);
    s.stitchNetToNode(net1, cnode1, "192.168.20.2", "255.255.255.0");
    s.stitchNetToNode(net1, "c0");


    String cnode2 = s.addComputeNode("node2", nodeImageURL, nodeImageHash, nodeImageShortName,
      nodeNodeType, coreProperties.getClientSites().get(1), scripts);
    String net2 = s.addBroadcastLink("stitch_c2_30", cnodebw);
    s.stitchNetToNode(net2, cnode2, "192.168.30.2", "255.255.255.0");
    s.stitchNetToNode(net2, "c" + (num - 1));

    String cnode3 = s.addComputeNode("node3", nodeImageURL, nodeImageHash, nodeImageShortName,
      nodeNodeType, coreProperties.getClientSites().get(1), scripts);
    String net3 = s.addBroadcastLink("stitch_c2_40", cnodebw);
    s.stitchNetToNode(net3, cnode3, "192.168.40.2", "255.255.255.0");
    s.stitchNetToNode(net3, c3);
    return s;
  }

  public SliceManager createTestSliceWithDynamicLinks(String sliceName, int num, long bw) {
    String nodeImageShortName = "Ubuntu 14.04";
    String nodeImageURL = "http://geni-orca.renci.org/owl/9dfe179d-3736-41bf-8084-f0cd4a520c2f#Ubuntu+14.04";//http://geni-images.renci.org/images/standard/ubuntu/ub1304-ovs-opendaylight-v1.0.0.xml
    String nodeImageHash = "9394ca154aa35eb55e604503ae7943ddaecc6ca5";
    String nodeNodeType = "XO Extra large";
    SliceManager s = createCarrierSlice(sliceName, num, bw);
    //Now add two customer node to c0 and c3
    String c0 = "c0";
    String c1 = "c1";
    String c2 = "c2";
    String scripts =
      Scripts.getCustomerScript() + Scripts.installVsftpd() + Scripts.installIperf();

    String nodeName = s.addComputeNode("node0", nodeImageURL, nodeImageHash, nodeImageShortName,
      nodeNodeType, coreProperties.getClientSites().get(0), scripts);
    String netName = s.addBroadcastLink("stitch_c0_10", bw);
    String interfaceName = s.stitchNetToNode(netName, nodeName, "192.168.10.2", "255.255.255.0");
    s.stitchNetToNode(netName, c0);

    String cnode1 = s.addComputeNode("node1", nodeImageURL, nodeImageHash, nodeImageShortName,
      nodeNodeType, coreProperties.getClientSites().get(1), scripts);
    String net1 = s.addBroadcastLink("stitch_c0_20", bw);
    s.stitchNetToNode(net1, cnode1, "192.168.20.2", "255.255.255.0");
    s.stitchNetToNode(net1, c1);

    String cnode2 = s.addComputeNode("node2", nodeImageURL, nodeImageHash, nodeImageShortName,
      nodeNodeType, coreProperties.getClientSites().get(2), scripts);
    String net2 = s.addBroadcastLink("stitch_c2_30", bw);
    s.stitchNetToNode(net2, cnode2, "192.168.30.2", "255.255.255.0");
    s.stitchNetToNode(net2, c2);


    String net3 = s.addBroadcastLink("dl-c0-c2", bw);
    String ifaceNode3 = s.stitchNetToNode(net3, c2);
    s.stitchNetToNode(net3, c0);
    return s;
  }

  public SliceManager createTestSliceWithBroAndCNode(String sliceName, int num, long bw) {
    String nodeImageShortName = "Ubuntu 14.04";
    String nodeImageURL = "http://geni-orca.renci.org/owl/9dfe179d-3736-41bf-8084-f0cd4a520c2f#Ubuntu+14.04";//http://geni-images.renci.org/images/standard/ubuntu/ub1304-ovs-opendaylight-v1.0.0.xml
    String nodeImageHash = "9394ca154aa35eb55e604503ae7943ddaecc6ca5";
    String nodeNodeType = "XO Extra large";
    SliceManager s = createCarrierSlice(sliceName, num, bw);
    //Now add two customer node to c0 and c3
    String scripts = Scripts.getCustomerScript()
      + Scripts.installVsftpd() + Scripts.installIperf();

    String c0 = "c0";
    String c3 = "c" + (num - 1);

    String nodeName = s.addComputeNode("node0", nodeImageURL, nodeImageHash, nodeImageShortName,
      nodeNodeType, coreProperties.getClientSites().get(0), scripts);
    String netName = s.addBroadcastLink("stitch_c0_10", bw);
    s.stitchNetToNode(netName, nodeName, "192.168.10.2", "255.255.255.0");
    s.stitchNetToNode(netName, c0);

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

    String nodeName1 = "node" + (num - 1);
    String stitchName = "stitch_c" + (num - 1) + "_20";
    String site = coreProperties.getClientSites().get((num - 1) % coreProperties.getClientSites()
      .size());
    s.addComputeNode(nodeName1, nodeImageURL, nodeImageHash, nodeImageShortName,
      nodeNodeType, site, scripts);
    s.addBroadcastLink(stitchName, bw);
    s.stitchNetToNode(stitchName, nodeName1, "192.168.20.2", "255.255.255.0");
    s.stitchNetToNode(stitchName, c3);
    return s;
  }

  public SliceManager createTestSliceWithBroAndChameleon(String sliceName, int num, long bw) throws TransportException, Exception {
    String nodeImageShortName = "Ubuntu 14.04";
    String nodeImageURL = "http://geni-orca.renci.org/owl/9dfe179d-3736-41bf-8084-f0cd4a520c2f#Ubuntu+14.04";//http://geni-images.renci.org/images/standard/ubuntu/ub1304-ovs-opendaylight-v1.0.0.xml
    String nodeImageHash = "9394ca154aa35eb55e604503ae7943ddaecc6ca5";
    String nodeNodeType = "XO Extra large";
    SliceManager s = createCarrierSlice(sliceName, num, bw);
    String scripts = Scripts.installVsftpd() + Scripts.installIperf();
    //Now add two customer node to c0 and c3
    String c0 = "c0";

    String nodeName = s.addComputeNode("node0", nodeImageURL, nodeImageHash, nodeImageShortName,
      nodeNodeType, coreProperties.getClientSites().get(0), scripts);
    String netName = s.addBroadcastLink("stitch_c0_10", bw);
    s.stitchNetToNode(netName, nodeName, "192.168.10.2", "255.255.255.0");
    s.stitchNetToNode(netName, c0);


    s.commitAndWait();
    s.loadSlice();
    String c3 = s.getComputeNode("c" + (num - 1));
    String ip = "10.32.90.106/24";
    String gateway = "10.32.90.105";
    String vlan = "3290";
    String stitchport = "http://geni-orca.renci.org/owl/ion.rdf#AL2S/Chameleon/Cisco/6509/GigabitEthernet/1/1";
    String stitchname = "sp-" + c3 + "-" + ip.replace("/", "__").replace(".", "_")
      + '-' + gateway.split("\\.")[3];

    System.out.println("Stitching to Chameleon {" + "stitchname: " + stitchname + " vlan:" + vlan + " stithport: " + stitchport + "}");
    String mysp = s.addStitchPort(stitchname, vlan, stitchport, bw);
    s.stitchSptoNode(mysp, c3);

    return s;
  }

  public SliceManager createCarrierSliceWithCustomerNodes(String sliceName, int num, int start,
                                                          long bw, int numstitches) {//,String stitchsubnet="", String slicesubnet="")
    System.out.println("ndllib TestDriver: START");

    SliceManager s = sliceManagerFactory.create(sliceName,
      coreProperties.getExogeniKey(),
      coreProperties.getExogeniKey(),
      coreProperties.getExogeniSm(),
      coreProperties.getSshKey());

    String nodeImageShortName = "Ubuntu 14.04";
    String nodeImageURL = "http://geni-orca.renci.org/owl/9dfe179d-3736-41bf-8084-f0cd4a520c2f#Ubuntu+14.04";//http://geni-images.renci.org/images/standard/ubuntu/ub1304-ovs-opendaylight-v1.0.0.xml
    String nodeImageHash = "9394ca154aa35eb55e604503ae7943ddaecc6ca5";
    String nodeNodeType = "XO Medium";
    //String nodePostBootScript="apt-get update;apt-get -y  install quagga\n"
    //  +"sed -i -- 's/zebra=no/zebra=yes/g' /etc/quagga/daemons\n"
    //  +"sed -i -- 's/ospfd=no/ospfd=yes/g' /etc/quagga/daemons\n";
    String nodePostBootScript = Scripts.getOVSScript();
    ArrayList<String> nodelist = new ArrayList<>();
    ArrayList<String> netlist = new ArrayList<>();
    ArrayList<String> stitchlist = new ArrayList<>();
    for (int i = 0; i < num; i++) {

      String node0 = s.addComputeNode(((i == 0 || i == (num - 1)) ? "node" : "c") + String
          .valueOf(i), nodeImageURL, nodeImageHash, nodeImageShortName,
        nodeNodeType, coreProperties.getClientSites().get(i % coreProperties.getClientSites().size()),
        nodePostBootScript);

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

        String net2 = s.addBroadcastLink(linkname, bw);
        s.stitchNetToNode(linkname, node0, "192.168." + String.valueOf(start + i) + ".1",
          "255.255.255.0");
        netlist.add(net2);
      }
      if (i != 0) {
        String net = netlist.get(i - 1);
        s.stitchNetToNode(net, node0, "192.168." + String.valueOf(start + i - 1) + ".2",
          "255.255.255.0");
      }
    }
    s.addPlexusController(coreProperties.getSdnSite(), "plexuscontroller");
    try {
      s.commit();
    } catch (XMLRPCTransportException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return s;
  }
}
