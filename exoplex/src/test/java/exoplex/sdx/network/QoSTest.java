package exoplex.sdx.network;

import com.google.inject.Guice;
import com.google.inject.Injector;
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
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Method;

public class QoSTest extends ExoSdxManager {
  static Logger logger = LogManager.getLogger(QoSTest.class);
  static String site1 = SiteBase.get("PSC");
  static String site2 = SiteBase.get("UNF");
  static String userDir = System.getProperty("user.dir");
  static String sdxSimpleDir = userDir.split("exoplex")[0] + "exoplex/";
  static String[] arg1 = {"-c", sdxSimpleDir + "config/qos/qos.conf"};
  static QoSTest qosTest;

  public QoSTest() {
    super(null, null);
  }

  @BeforeClass
  public static void setUp() throws Exception {
    Injector injector = Guice.createInjector(new QoSModule());
    qosTest = injector.getInstance(QoSTest.class);
    qosTest.routingManager = injector.getInstance(AbstractRoutingManager.class);
    qosTest.initTest();
  }

  @AfterClass
  public static void cleanUp() {
    //qosTest.deleteSlice();
  }

  public void initTest() throws Exception {
    CommandLine cmd = ServerOptions.parseCmd(arg1);
    String configFilePath = cmd.getOptionValue("config");
    this.readConfig(configFilePath);
    coreProperties.setSdnApp("rest_mirror");
    createNetwork();
  }

  @Test
  public void testQoS() throws Exception {
    qosTest.startExoPlex();
    qosTest.notifyPrefix("192.168.10.1/24", "192.168.10.2", "CNode0");
    qosTest.notifyPrefix("192.168.20.1/24", "192.168.20.2", "CNode1");
    qosTest.notifyPrefix("192.168.30.1/24", "192.168.30.2", "CNode2");
    qosTest.notifyPrefix("192.168.40.1/24", "192.168.40.2", "CNode3");
    qosTest.connectionRequest("192.168.10.1/24", "192.168.30.1/24", 300000000);
    qosTest.connectionRequest("192.168.20.1/24", "192.168.40.1/24", 100000000);
    logger.info("Now no QoS rule has been installed, the bandwidth are " +
      "limited to the link capacity");
    //promptEnterKey();
    qosTest.setQos("192.168.10.1/24", "192.168.30.1/24", 300000000);
    logger.info("Now QoS rule has been installed to limit bandwith between " +
        "192.168.10.1/24 and 192.168.30.1/24 to 300Mbps");
    //promptEnterKey();
    qosTest.setQos("192.168.20.1/24", "192.168.40.1/24", 100000000);
    logger.info("Now QoS rule has been installed to limit bandwith between " +
      "192.168.20.1/24 and 192.168.40.1/24 to 100Mbps");
    logger.info("test ends");
  }

  @Test
  public void testQoSDynamicLink() throws Exception {
    qosTest.startExoPlex();
    qosTest.notifyPrefix("192.168.10.1/24", "192.168.10.2", "CNode0");
    qosTest.notifyPrefix("192.168.20.1/24", "192.168.20.2", "CNode1");
    qosTest.notifyPrefix("192.168.30.1/24", "192.168.30.2", "CNode2");
    qosTest.notifyPrefix("192.168.40.1/24", "192.168.40.2", "CNode3");
    qosTest.connectionRequest("192.168.10.1/24", "192.168.30.1/24", 100000000);
    qosTest.setQos("192.168.10.1/24", "192.168.30.1/24", 100000000);
    logger.info("Now QoS rule has been installed to limit bandwith between " +
      "192.168.10.1/24 and 192.168.30.1/24 to 300Mbps");
    qosTest.connectionRequest("192.168.20.1/24", "192.168.40.1/24", 300000000);
    qosTest.setQos("192.168.20.1/24", "192.168.40.1/24", 300000000);
    logger.info("Now QoS rule has been installed to limit bandwith between " +
      "192.168.20.1/24 and 192.168.40.1/24 to 100Mbps");
    qosTest.connectionRequest("192.168.10.1/24", "192.168.40.1/24", 100000000);
    qosTest.setQos("192.168.10.1/24", "192.168.40.1/24", 100000000);
    qosTest.connectionRequest("192.168.20.1/24", "192.168.30.1/24", 200000000);
    qosTest.setQos("192.168.20.1/24", "192.168.30.1/24", 200000000);
    logger.info("test ends");
  }

  public void testDeleteLink() throws Exception{
    ExoSliceManager slice = (ExoSliceManager) sliceManagerFactory.create(
      "testsp",
      coreProperties.getExogeniKey(),
      coreProperties.getExogeniKey(),
      coreProperties.getExogeniSm(),
      coreProperties.getSshKey());
   // slice.addComputeNode(site1, "Node0", NodeBase.xoMedium);
   // slice.addComputeNode(site2, "Node1", NodeBase.xoMedium);
   // slice.addLink("clink0", "Node0", "Node1", 10000000);
   // slice.commitAndWait();
    slice.loadSlice();
    slice.deleteResource("clink0");
    slice.commitAndWait();
  }

  @Test
  public void test1() throws Exception {
    qosTest.testDeleteLink();
  }

  public void promptEnterKey(){
    System.out.println("Press " +
      "\"ENTER\" " +
      "to continue...");
    try {
      System.in.read();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public void startExoPlex() throws Exception {
    CommandLine cmd = ServerOptions.parseCmd(arg1);
    String configFilePath = cmd.getOptionValue("config");
    this.readConfig(configFilePath);
    plexusName = "plexuscontroller";
    loadSlice();
    initializeSdx();
    delFlows();
    configRouting();
    updateMacAddr();
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
    try {
      Class[] cArg = new Class[1];
      cArg[0] = String.class;
      Method logFlowTables = this.getClass().getDeclaredMethod("logFlowTables", cArg);
      logFlowTables.setAccessible(true);
      logFlowTables.invoke(this, "c0");
      logFlowTables.invoke(this, "c1");
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

}
