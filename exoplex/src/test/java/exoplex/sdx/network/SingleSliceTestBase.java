package exoplex.sdx.network;

import exoplex.common.utils.ServerOptions;
import exoplex.sdx.core.CoreProperties;
import exoplex.sdx.core.exogeni.ExoSdxManager;
import exoplex.sdx.slice.Scripts;
import exoplex.sdx.slice.SliceManager;
import exoplex.sdx.slice.exogeni.ExoSliceManager;
import exoplex.sdx.slice.exogeni.NodeBase;
import exoplex.sdx.slice.exogeni.SiteBase;
import org.apache.commons.cli.CommandLine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;

public class SingleSliceTestBase extends ExoSdxManager {
  Logger logger;
  String site1;
  String site2;
  String userDir;
  String sdxSimpleDir;
  String[] arg1;

  public SingleSliceTestBase() {
    super(null, null);
  }


  public void startExoPlex() throws Exception {
    plexusName = "plexuscontroller";
    loadSlice();
    initializeSdx();
    delFlows();
    configRouting();
  }

  public void createNetwork() throws Exception {
    serverSlice = createTestSlice();
    serverSlice.commitAndWait();
    configTestSlice(serverSlice);
    if (coreProperties.isPlexusInSlice()) {
      checkPlexus(serverSlice, serverSlice.getManagementIP(plexusName), CoreProperties.getPlexusImage());
      configSdnControllerAddr(serverSlice.getManagementIP("plexuscontroller"));
    } else {
      configSdnControllerAddr(coreProperties.getSdnControllerIp());
    }
    serverSlice.runCmdSlice(Scripts.getOVSScript(), coreProperties.getSshKey(), routerPattern, true);
    copyRouterScript(serverSlice);
    configRouters(serverSlice);
  }

  public void configTestSlice(SliceManager carrier) {
    carrier.runCmdSlice("sudo apt-get update;apt-get -y install quagga",
      coreProperties.getSshKey(), "(CNode\\d+)", true);
    carrier.runCmdSlice("sudo sed -i -- 's/zebra=no/zebra=yes/g' " +
      "/etc/quagga/daemons", coreProperties.getSshKey(), "(CNode\\d+)", true);
    //carrier.runCmdSlice("sed -i -- 's/ospfd=no/ospfd=yes/g' /etc/quagga/daemons", coreProperties.getSshKey(),
    // "(node\\d+)", true);
    carrier.runCmdSlice("sudo echo \"1\" > /proc/sys/net/ipv4/ip_forward",
      coreProperties.getSshKey(), "(CNode\\d+)", true);
    try {
      carrier.runCmdSlice("sudo ifconfig ens6 192.168.10.2/24 up",
        coreProperties.getSshKey(), "(CNode0)", true);
      carrier.runCmdSlice("sudo ifconfig ens6 192.168.20.2/24 up",
        coreProperties.getSshKey(), "(CNode1)", true);
      carrier.runCmdSlice("sudo ifconfig ens6 192.168.30.2/24 up",
        coreProperties.getSshKey(), "(CNode2)", true);
      carrier.runCmdSlice("sudo ifconfig ens6 192.168.40.2/24 up",
        coreProperties.getSshKey(), "(CNode3)", true);
    } catch (Exception e) {
      e.printStackTrace();
    }
    carrier.runCmdSlice("sudo echo \"ip route 192.168.1.1/16 192.168.10.1\" " +
      "| sudo tee /etc/quagga/zebra" +
      ".conf", coreProperties.getSshKey(), "(CNode0)", true);
    carrier.runCmdSlice("sudo echo \"ip route 192.168.1.1/16 192.168.20.1\" " +
      "| sudo tee /etc/quagga/zebra" +
      ".conf", coreProperties.getSshKey(), "(CNode1)", true);
    carrier.runCmdSlice("sudo echo \"ip route 192.168.1.1/16 192.168.30.1\" " +
      "| sudo tee /etc/quagga/zebra" +
      ".conf", coreProperties.getSshKey(), "(CNode2)", true);
    carrier.runCmdSlice("sudo echo \"ip route 192.168.1.1/16 192.168.40.1\" " +
      "| sudo tee /etc/quagga/zebra" +
      ".conf", coreProperties.getSshKey(), "(CNode3)", true);
    carrier.runCmdSlice(Scripts.restartQuagga(), coreProperties.getSshKey(), "(CNode\\d+)", true);
  }

  public SliceManager createTestSlice() {
    logger.info(String.format("%s %s %s %s",
      coreProperties.getSliceName(),
      coreProperties.getExogeniKey(),
      coreProperties.getSshKey(),
      coreProperties.getExogeniSm()));
    ExoSliceManager slice = (ExoSliceManager) sliceManagerFactory.create(
      coreProperties.getSliceName(),
      coreProperties.getExogeniKey(),
      coreProperties.getExogeniKey(),
      coreProperties.getExogeniSm(),
      coreProperties.getSshKey());
    slice.addComputeNode(site1, "CNode0", NodeBase.xoMedium);
    slice.addComputeNode(site1, "CNode1", NodeBase.xoMedium);
    slice.addComputeNode(site2, "CNode2", NodeBase.xoMedium);
    slice.addComputeNode(site2, "CNode3", NodeBase.xoMedium);
    slice.addOVSRouter(site1, "c0");
    slice.addOVSRouter(site2, "c1");
    slice.addBroadcastLink("stitch_c0_10", 500000000);
    slice.attach("CNode0", "stitch_c0_10", "192.168.10.2", "255.255.255.0");
    slice.attach("c0", "stitch_c0_10", null, null);

    slice.addBroadcastLink("stitch_c0_20", 500000000);
    slice.attach("CNode1", "stitch_c0_20", "192.168.20.2", "255.255.255.0");
    slice.attach("c0", "stitch_c0_20", null, null);

    //slice.addBroadcastLink("clink0", 100000000);
    //slice.attach("clink0", "c0");
    //slice.attach("clink0", "c1");
    //slice.addBroadcastLink("clink1", 200000000);
    //slice.attach("clink1", "c0");
    //slice.attach("clink1", "c1");

    slice.addBroadcastLink("stitch_c1_30", 500000000);
    slice.attach("CNode2","stitch_c1_30",  "192.168.30.2", "255.255" +
      ".255.0");
    slice.attach("c1", "stitch_c1_30");
    slice.addBroadcastLink("stitch_c1_40", 500000000);
    slice.attach("stitch_c1_40", "CNode3", "192.168.40.2", "255.255.255.0");
    slice.attach("stitch_c1_40", "c1");
    if (coreProperties.isPlexusInSlice()) {
      slice.addPlexusController(coreProperties.getSdnSite(), plexusName);
    }
    return slice;
  }


  public void logFlowTables() {
    super.logFlowTables(new ArrayList<>(), new ArrayList<>());
  }


  public void checkConnectivity(String node1, String ip2) {
    String res = serverSlice.runCmdNode("ping  -c 1 -W 2 " + ip2, node1, false);
    logger.debug(res);
    if(res.contains("1 received")) {
      logger.info(String.format("Ping form %s to %s: Ok.", node1, ip2));
    } else {
      logger.warn(String.format("Ping form %s to %s: Failed.", node1, ip2));
    }
  }

}
