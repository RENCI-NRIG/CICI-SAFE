package exoplex.client.exogeni;

import exoplex.common.slice.SafeSlice;
import exoplex.sdx.core.SliceManager;
import org.apache.commons.cli.CommandLine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.renci.ahab.libndl.resources.request.ComputeNode;
import org.renci.ahab.libndl.resources.request.InterfaceNode2Net;
import org.renci.ahab.libndl.resources.request.Network;
import org.renci.ahab.libtransport.SSHAccessToken;
import org.renci.ahab.libtransport.SliceAccessContext;
import org.renci.ahab.libtransport.util.SSHAccessTokenFileFactory;
import org.renci.ahab.libtransport.util.TransportException;
import org.renci.ahab.libtransport.util.UtilTransportException;

import java.util.ArrayList;

/**
 * @author geni-orca
 */
public class ClientSlice extends SliceManager {
  static private long bw = 1000000;
  final Logger logger = LogManager.getLogger(ClientSlice.class);
  private String mask = "/24";
  private String type;
  private String routerSite = "";

  public ClientSlice() {
  }


  public ClientSlice(String[] args) {

    logger.debug("SDX-Simple " + args[0]);

    CommandLine cmd = parseCmd(args);

    logger.debug("cmd " + cmd);

    String configfilepath = cmd.getOptionValue("config");

    logger.debug("configfilepath " + configfilepath);
    readConfig(configfilepath);

    type = conf.getString("config.type");
    if (cmd.hasOption('d')) {
      type = "delete";
    }


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

  public void run() throws Exception {
    //Example usage:   ./target/appassembler/bin/SafeSdxExample  ~/.ssl/geni-pruth1.pem ~/.ssl/geni-pruth1.pem "https://geni.renci.org:11443/orca/xmlrpc" pruth.1 stitch
    //Example usage:   ./target/appassembler/bin/SafeSdxExample  ~/.ssl/geni-pruth1.pem ~/.ssl/geni-pruth1.pem "https://geni.renci.org:11443/orca/xmlrpc" name fournodes


    if (type.equals("client")) {
      routerSite = conf.getString("config.routersite");
      IPPrefix = conf.getString("config.ipprefix");
      computeIP(IPPrefix);
      logger.info("Client start");
      String customerName = sliceName;
      SafeSlice c1 = createCustomerSlice(customerName, 2, IPPrefix, curip, bw, true);
      try {
        c1.commitAndWait();
      } catch (Exception e) {
        e.printStackTrace();
        c1 = createCustomerSlice(customerName, 2, IPPrefix, curip, bw, true);
        c1.commitAndWait();
      }
      c1.refresh();
      if(safeEnabled){
        String safeIp = c1.getComputeNode("safe-server").getManagementIP();
        checkSafeServer(safeIp, riakIp);
      }
      //copyFile2Slice(c1, "/home/yaoyj11/project/exo-geni/SAFE_SDX/src/main/resources/scripts/configospffornewif.sh","~/configospffornewif.sh","~/.ssh/id_rsa");
      //copyFile2Slice(c1, "/home/yaoyj11/project/exo-geni/SAFE_SDX/src/main/resources/scripts/configospffornewif.sh","~/configospffornewif.sh","~/.ssh/id_rsa");
      //runCmdSlice(c1,"/bin/bash ~/ospfautoconfig.sh","~/.ssh/id_rsa");
      configFTPService(c1, "(CNode1)", "ftpuser", "ftp");
      logger.info("Slice active now: " + sliceName);
      c1.printNetworkInfo();
      return;
    } else if (type.equals("delete")) {
      SafeSlice s2 = null;
      logger.info("deleting slice " + sliceName);
      s2 = SafeSlice.loadManifestFile(sliceName, pemLocation, keyLocation, controllerUrl);
      s2.delete();
    }
  }

  public SafeSlice createCustomerSlice(String sliceName, int num, String prefix, int start, long bw, boolean network)
      throws TransportException {//=1, String subnet="")
    //Main Example Code

    SafeSlice s = SafeSlice.create(sliceName, pemLocation, keyLocation, controllerUrl, sctx);

    String nodeImageShortName = "Ubuntu 14.04";
    String nodeImageURL = "http://geni-images.renci.org/images/standard/ubuntu/ub1404-v1.0.4.xml";//http://geni-images.renci.org/images/standard/ubuntu/ub1304-ovs-opendaylight-v1.0.0.xml
    String nodeImageHash = "9394ca154aa35eb55e604503ae7943ddaecc6ca5";
    String nodeNodeType = "XO Medium";
    String nodePostBootScript = "apt-get update;apt-get -y install quagga iperf vsftpd\n"
        + "sed -i -- 's/zebra=no/zebra=yes/g' /etc/quagga/daemons\n"
        + "sed -i -- 's/ospfd=no/ospfd=yes/g' /etc/quagga/daemons\n"
        + "echo \"1\" > /proc/sys/net/ipv4/ip_forward\n"
        + "/etc/init.d/neuca stop\n";
    String nodeDomain = routerSite;
    ArrayList<ComputeNode> nodelist = new ArrayList<ComputeNode>();
    ArrayList<Network> netlist = new ArrayList<Network>();
    for (int i = 0; i < num; i++) {
      ComputeNode node0 = s.addComputeNode("CNode" + String.valueOf(i));
      node0.setImage(nodeImageURL, nodeImageHash, nodeImageShortName);
      node0.setNodeType(nodeNodeType);
      node0.setDomain(routerSite);
      node0.setPostBootScript(nodePostBootScript);
      nodelist.add(node0);
      if (network) {
        if (i != num - 1) {
          Network net2 = s.addBroadcastLink("clink" + String.valueOf(i), bw);
          InterfaceNode2Net ifaceNode1 = (InterfaceNode2Net) net2.stitch(node0);
          ifaceNode1.setIpAddress(IPPrefix + String.valueOf(start + i) + ".1");
          ifaceNode1.setNetmask("255.255.255.0");
          netlist.add(net2);
        }
        if (i != 0) {
          Network net = netlist.get(i - 1);
          InterfaceNode2Net ifaceNode1 = (InterfaceNode2Net) net.stitch(node0);
          ifaceNode1.setIpAddress(IPPrefix + String.valueOf(start + i - 1) + ".2");
          ifaceNode1.setNetmask("255.255.255.0");
        }
      }
    }
    if (safeEnabled) {
      s.addSafeServer(serverSite, riakIp);
    }
    return s;
  }
}

