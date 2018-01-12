package sdx;

import org.renci.ahab.libndl.Slice;
import sdx.core.SliceManager;
import java.util.ArrayList;
import org.apache.log4j.Logger;
import org.apache.commons.cli.*;
import org.renci.ahab.libndl.resources.request.ComputeNode;
import org.renci.ahab.libndl.resources.request.InterfaceNode2Net;
import org.renci.ahab.libndl.resources.request.Network;
import org.renci.ahab.libtransport.SSHAccessToken;
import org.renci.ahab.libtransport.SliceAccessContext;
import org.renci.ahab.libtransport.util.SSHAccessTokenFileFactory;
import org.renci.ahab.libtransport.util.UtilTransportException;
import org.renci.ahab.libtransport.xmlrpc.XMLRPCTransportException;

import sdx.core.SliceCommon;
import sdx.core.SliceManager;
import sdx.utils.Exec;

public class TestSlice extends SliceManager {
  final static Logger logger = Logger.getLogger(Exec.class);

  public TestSlice() {
  }


  public static void main(String[] args) {

    //TestSlice usage:   ./target/appassembler/bin/SafeSdxTestSlice  ~/.ssl/geni-pruth1.pem ~/.ssl/geni-pruth1.pem "https://geni.renci.org:11443/orca/xmlrpc" pruth.1 stitch
    //TestSlice usage:   ./target/appassembler/bin/SafeSdxTestSlice  ~/.ssl/geni-pruth1.pem ~/.ssl/geni-pruth1.pem "https://geni.renci.org:11443/orca/xmlrpc" name fournodes
    System.out.println("SDX-Simple " + args[0]);

    CommandLine cmd = parseCmd(args);

    logger.debug("cmd " + cmd);

    String configfilepath = cmd.getOptionValue("config");

    readConfig(configfilepath);

    logger.debug("configfilepath " + configfilepath);
    //readConfig(configfilepath);

    //type=conf.getString("config.type");
    if (cmd.hasOption('d')) {
      type = "delete";
    }

    sliceProxy = TestSlice.getSliceProxy(pemLocation, keyLocation, controllerUrl);

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
      Long bw = conf.getLong("config.bw");
      String scriptsdir = conf.getString("config.scriptsdir");
      computeIP(IPPrefix);
      try {
        String carrierName = sliceName;
        Slice carrier = createTestSliceWithBroAndCNode(carrierName, 4, bw);
        //Slice carrier = createCarrierSliceWithCustomerNodes(carrierName, 4, 10, 1000000, 1);
        commitAndWait(carrier);
        carrier.refresh();
        copyFile2Slice(carrier, scriptsdir + "dpid.sh", "~/dpid.sh", sshkey);
        copyFile2Slice(carrier, scriptsdir + "ovsbridge.sh", "~/ovsbridge.sh", sshkey);
        //Make sure that plexus container is running
        //SDNControllerIP = ((ComputeNode) carrier.getResourceByName("plexuscontroller")).getManagementIP();
        SDNControllerIP="152.3.136.36";
        /*
        if (!checkPlexus(SDNControllerIP)) {
          System.exit(-1);
        }*/
        System.out.println("Plexus Controler IP: " + SDNControllerIP);
        runCmdSlice(carrier, "/bin/bash ~/ovsbridge.sh " + SDNControllerIP + ":6633", sshkey, "(c\\d+)",true,true);
        runCmdSlice(carrier, "apt-get -y install quagga", sshkey, "(node\\d+)",true,true);
        runCmdSlice(carrier, "sed -i -- 's/zebra=no/zebra=yes/g' /etc/quagga/daemons", sshkey, "(node\\d+)",true,true);
        runCmdSlice(carrier, "sed -i -- 's/ospfd=no/ospfd=yes/g' /etc/quagga/daemons" , sshkey, "(node\\d+)",true,true);
        runCmdSlice(carrier, "echo \"1\" > /proc/sys/net/ipv4/ip_forward", sshkey, "(node\\d+)",true,true);
        runCmdSlice(carrier, "/etc/init.d/neuca stop\nifconfig eth1 192.168.10.2/24 up" , sshkey, "(node0)",true,true);
        runCmdSlice(carrier, "/etc/init.d/neuca stop\nifconfig eth1 192.168.20.2/24 up" , sshkey, "(node3)",true,true);
        runCmdSlice(carrier, "echo \"ip route 192.168.1.1/16 192.168.10.1\" >>/etc/quagga/zebra.conf", sshkey, "(node0)",true,true);
        runCmdSlice(carrier, "echo \"ip route 192.168.1.1/16 192.168.20.1\" >>/etc/quagga/zebra.conf", sshkey, "(node3)",true,true);
        runCmdSlice(carrier, "/etc/init.d/quagga restart", sshkey, "(node\\d+)",true,true);

        String SAFEServerIP = ((ComputeNode) carrier.getResourceByName("safe-server")).getManagementIP();
        if (!checkSafeServer(SAFEServerIP)) {
          System.exit(-1);
        }
        System.out.println("SAFE Server IP: " + SAFEServerIP);
        //}
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

  public static Slice createBroSlice(String sliceName, int num, int start, long bw, int numstitches) {//,String stitchsubnet="", String slicesubnet="")
    logger.debug("ndllib TestDriver: START");

    Slice s = Slice.create(sliceProxy, sctx, sliceName);

    String nodeImageShortName = "Ubuntu 14.04";
    String nodeImageURL = "http://geni-orca.renci.org/owl/9dfe179d-3736-41bf-8084-f0cd4a520c2f#Ubuntu+14.04";//http://geni-images.renci.org/images/standard/ubuntu/ub1304-ovs-opendaylight-v1.0.0.xml
    String nodeImageHash = "9394ca154aa35eb55e604503ae7943ddaecc6ca5";
    String nodeNodeType = "XO Medium";
    //String nodePostBootScript="apt-get update;apt-get -y  install quagga\n"
    //  +"sed -i -- 's/zebra=no/zebra=yes/g' /etc/quagga/daemons\n"
    //  +"sed -i -- 's/ospfd=no/ospfd=yes/g' /etc/quagga/daemons\n";
    String nodePostBootScript = getOVSScript(SDNControllerIP);
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
      /*
      TODO: add bro
      if(i==1){
        SliceManager.addBro(s,"bro0",node0,50);
      }
      */
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
    addSafeServer(s, riakip);
    addPlexusController(s);
    try {
      s.commit();
    } catch (XMLRPCTransportException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return s;
  }

  public static Slice createTestSliceWithDynamicLink(String sliceName, int num, long bw) {
    String nodeImageShortName = "Ubuntu 14.04";
    String nodeImageURL = "http://geni-orca.renci.org/owl/9dfe179d-3736-41bf-8084-f0cd4a520c2f#Ubuntu+14.04";//http://geni-images.renci.org/images/standard/ubuntu/ub1304-ovs-opendaylight-v1.0.0.xml
    String nodeImageHash = "9394ca154aa35eb55e604503ae7943ddaecc6ca5";
    String nodeNodeType = "XO Medium";
    Slice s = createCarrierSlice(sliceName, num, 1000000);
    //Now add two customer node to c0 and c3
    ComputeNode c0 = (ComputeNode) s.getResourceByName("c0");
    ComputeNode c3 = (ComputeNode) s.getResourceByName("c3");
    ComputeNode cnode0 = s.addComputeNode("node0");
    cnode0.setImage(nodeImageURL, nodeImageHash, nodeImageShortName);
    cnode0.setNodeType(nodeNodeType);
    cnode0.setDomain(clientSites.get(0));
    cnode0.setPostBootScript(getCustomerScript());
    Network net1 = s.addBroadcastLink("stitch_c0_10",bw);
    InterfaceNode2Net ifaceNode0 = (InterfaceNode2Net) net1.stitch(cnode0);
    ifaceNode0.setIpAddress("192.168.10.2");
    ifaceNode0.setNetmask("255.255.255.0");
    net1.stitch(c0);

    ComputeNode cnode1 = s.addComputeNode("node3");
    cnode1.setImage(nodeImageURL, nodeImageHash, nodeImageShortName);
    cnode1.setNodeType(nodeNodeType);
    cnode1.setDomain(clientSites.get(3%clientSites.size()));
    cnode1.setPostBootScript(getCustomerScript());
    Network net2 = s.addBroadcastLink("stitch_c3_20",bw);
    InterfaceNode2Net ifaceNode1 = (InterfaceNode2Net) net2.stitch(cnode1);
    ifaceNode1.setIpAddress("192.168.20.2");
    ifaceNode1.setNetmask("255.255.255.0");
    net2.stitch(c3);
    return s;
  }

  public static Slice createTestSliceWithBroAndCNode(String sliceName, int num, long bw) {
    String nodeImageShortName = "Ubuntu 14.04";
    String nodeImageURL = "http://geni-orca.renci.org/owl/9dfe179d-3736-41bf-8084-f0cd4a520c2f#Ubuntu+14.04";//http://geni-images.renci.org/images/standard/ubuntu/ub1304-ovs-opendaylight-v1.0.0.xml
    String nodeImageHash = "9394ca154aa35eb55e604503ae7943ddaecc6ca5";
    String nodeNodeType = "XO Medium";
    Slice s = createCarrierSlice(sliceName, num, 1000000);
    //Now add two customer node to c0 and c3
    ComputeNode c0 = (ComputeNode) s.getResourceByName("c0");
    ComputeNode c3 = (ComputeNode) s.getResourceByName("c3");
    ComputeNode cnode0 = s.addComputeNode("node0");
    cnode0.setImage(nodeImageURL, nodeImageHash, nodeImageShortName);
    cnode0.setNodeType(nodeNodeType);
    cnode0.setDomain(clientSites.get(0));
    cnode0.setPostBootScript(getCustomerScript());
    Network net1 = s.addBroadcastLink("stitch_c0_10",bw);
    InterfaceNode2Net ifaceNode0 = (InterfaceNode2Net) net1.stitch(cnode0);
    ifaceNode0.setIpAddress("192.168.10.2");
    ifaceNode0.setNetmask("255.255.255.0");
    net1.stitch(c0);

    ComputeNode cnode1 = s.addComputeNode("node3");
    cnode1.setImage(nodeImageURL, nodeImageHash, nodeImageShortName);
    cnode1.setNodeType(nodeNodeType);
    cnode1.setDomain(clientSites.get(3%clientSites.size()));
    cnode1.setPostBootScript(getCustomerScript());
    Network net2 = s.addBroadcastLink("stitch_c3_20",bw);
    InterfaceNode2Net ifaceNode1 = (InterfaceNode2Net) net2.stitch(cnode1);
    ifaceNode1.setIpAddress("192.168.20.2");
    ifaceNode1.setNetmask("255.255.255.0");
    net2.stitch(c3);
    return s;
  }

  public static Slice createCarrierSliceWithCustomerNodes(String sliceName, int num, int start,
                                                          long bw, int numstitches) {//,String stitchsubnet="", String slicesubnet="")
    logger.debug("ndllib TestDriver: START");

    Slice s = Slice.create(sliceProxy, sctx, sliceName);

    String nodeImageShortName = "Ubuntu 14.04";
    String nodeImageURL = "http://geni-orca.renci.org/owl/9dfe179d-3736-41bf-8084-f0cd4a520c2f#Ubuntu+14.04";//http://geni-images.renci.org/images/standard/ubuntu/ub1304-ovs-opendaylight-v1.0.0.xml
    String nodeImageHash = "9394ca154aa35eb55e604503ae7943ddaecc6ca5";
    String nodeNodeType = "XO Medium";
    //String nodePostBootScript="apt-get update;apt-get -y  install quagga\n"
    //  +"sed -i -- 's/zebra=no/zebra=yes/g' /etc/quagga/daemons\n"
    //  +"sed -i -- 's/ospfd=no/ospfd=yes/g' /etc/quagga/daemons\n";
    String nodePostBootScript = getOVSScript(SDNControllerIP);
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
    addSafeServer(s, riakip);
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
