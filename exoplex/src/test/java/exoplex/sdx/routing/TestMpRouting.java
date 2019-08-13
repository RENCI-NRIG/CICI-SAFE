package exoplex.sdx.routing;

import exoplex.common.utils.ServerOptions;
import exoplex.experiment.ExperimentBase;
import exoplex.sdx.core.SdxManager;
import exoplex.sdx.network.RoutingManager;
import exoplex.sdx.network.SdnUtil;
import exoplex.sdx.slice.Scripts;
import exoplex.sdx.slice.SliceManager;
import exoplex.sdx.slice.exogeni.SiteBase;
import org.apache.commons.cli.CommandLine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.HashMap;

@Ignore
public class TestMpRouting extends SdxManager {
  static Logger logger = LogManager.getLogger(TestMpRouting.class);
  static String site = SiteBase.get("TAMU");
  static String userDir = System.getProperty("user.dir");
  static String sdxSimpleDir = userDir.split("exoplex")[0] + "exoplex/";
  static String[] arg1 = {"-c", sdxSimpleDir + "config/test-mptcp.conf"};
  static TestMpRouting mpr;

  public TestMpRouting() {
    super(null);
  }

  @BeforeClass
  public static void setUp() throws Exception {
    mpr = new TestMpRouting();
    mpr.test();
  }

  @AfterClass
  public static void cleanUp() {
    mpr.deleteSlice();
  }

  public static void main(String[] args) throws Exception {
    mpr.initNetwork();
    mpr.installTestGroup();
    mpr.sendTraffic();
    mpr.getGroupStats();
    mpr.logFlowTables();
    logger.debug("end");
  }

  @Test
  public void testMpRouting() throws Exception {
    try {
      mpr.initNetwork();
      mpr.installTestGroup();
      mpr.sendTraffic();
      mpr.getGroupStats();
      mpr.logFlowTables();
      logger.debug("end");
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void test() throws Exception {
    CommandLine cmd = ServerOptions.parseCmd(arg1);
    String configFilePath = cmd.getOptionValue("config");
    this.readConfig(configFilePath);
    createNetwork();
  }

  public void initNetwork() throws Exception {
    CommandLine cmd = ServerOptions.parseCmd(arg1);
    String configFilePath = cmd.getOptionValue("config");
    this.readConfig(configFilePath);
    plexusName = "plexuscontroller";
    loadSlice();
    initializeSdx();
    delFlows();
    Method configRouting = super.getClass().getDeclaredMethod("configRouting");
    configRouting.setAccessible(true);
    configRouting.invoke(this);
    Method updateMacAddr = super.getClass().getDeclaredMethod("updateMacAddr");
    updateMacAddr.setAccessible(true);
    updateMacAddr.invoke(this);
  }

  public void createNetwork() throws Exception {
    serverSlice = createTestSlice();
    serverSlice.commitAndWait();
    configTestSlice(serverSlice);
    if (coreProperties.isPlexusInSlice()) {
      checkPlexus(serverSlice, serverSlice.getManagementIP(plexusName), RoutingManager.plexusImage);
    }
    if (coreProperties.isSafeInSlice()) {
      configSdnControllerAddr(serverSlice.getManagementIP(plexusName));
    } else {
      configSdnControllerAddr(coreProperties.getSdnControllerIp());
    }
    serverSlice.runCmdSlice(Scripts.getOVSScript(), coreProperties.getSshKey(), routerPattern, true);
    copyRouterScript(serverSlice);
    configRouters(serverSlice);
  }

  public void configTestSlice(SliceManager carrier) {
    carrier.runCmdSlice("apt-get update;apt-get -y install quagga", coreProperties.getSshKey(), "(CNode\\d+)", true);
    carrier.runCmdSlice("sed -i -- 's/zebra=no/zebra=yes/g' /etc/quagga/daemons", coreProperties.getSshKey(), "(CNode\\d+)", true);
    //carrier.runCmdSlice("sed -i -- 's/ospfd=no/ospfd=yes/g' /etc/quagga/daemons", coreProperties.getSshKey(),
    // "(node\\d+)", true);
    carrier.runCmdSlice("echo \"1\" > /proc/sys/net/ipv4/ip_forward", coreProperties.getSshKey(), "(CNode\\d+)", true);
    try {
      carrier.runCmdSlice("ifconfig eth1 192.168.10.2/24 up", coreProperties.getSshKey(), "(CNode0)", true);
      carrier.runCmdSlice("ifconfig eth1 192.168.20.2/24 up", coreProperties.getSshKey(), "(CNode1)", true);
      carrier.runCmdSlice("ifconfig eth1 192.168.30.2/24 up", coreProperties.getSshKey(), "(CNode2)", true);
    } catch (Exception e) {
      e.printStackTrace();
    }
    carrier.runCmdSlice("echo \"ip route 192.168.1.1/16 192.168.10.1\" >>/etc/quagga/zebra" +
      ".conf", coreProperties.getSshKey(), "(CNode0)", true);
    carrier.runCmdSlice("echo \"ip route 192.168.1.1/16 192.168.20.1\" >>/etc/quagga/zebra" +
      ".conf", coreProperties.getSshKey(), "(CNode1)", true);
    carrier.runCmdSlice("echo \"ip route 192.168.1.1/16 192.168.30.1\" >>/etc/quagga/zebra" +
      ".conf", coreProperties.getSshKey(), "(CNode2)", true);
    carrier.runCmdSlice(Scripts.restartQuagga(), coreProperties.getSshKey(), "(CNode\\d+)", true);
  }

  public SliceManager createTestSlice() {
    SliceManager slice = sliceManagerFactory.create(
      coreProperties.getSliceName(),
      coreProperties.getExogeniKey(),
      coreProperties.getExogeniKey(),
      coreProperties.getExogeniSm(),
      coreProperties.getSshKey());
    slice.addComputeNode(site, "CNode0");
    slice.addComputeNode(site, "CNode1");
    slice.addComputeNode(site, "CNode2");
    slice.addOVSRouter(site, "c0");
    slice.addOVSRouter(site, "c1");
    slice.addOVSRouter(site, "c2");
    slice.addOVSRouter(site, "c3");
    slice.addBroadcastLink("stitch_c0_10");
    slice.attach("CNode0", "stitch_c0_10", "192.168.10.2", "255.255.255.0");
    slice.attach("c0", "stitch_c0_10", null, null);
    slice.addBroadcastLink("clink0");
    slice.attach("clink0", "c0");
    slice.attach("clink0", "c1");
    slice.addBroadcastLink("clink1");
    slice.attach("clink1", "c1");
    slice.attach("clink1", "c3");
    slice.addBroadcastLink("clink2");
    slice.attach("clink2", "c0");
    slice.attach("clink2", "c2");
    slice.addBroadcastLink("clink3");
    slice.attach("clink3", "c2");
    slice.attach("clink3", "c3");
    slice.addBroadcastLink("stitch_c3_20");
    slice.attach("stitch_c3_20", "CNode1", "192.168.20.2", "255.255.255.0");
    slice.attach("stitch_c3_20", "c3");
    slice.addBroadcastLink("stitch_c3_30");
    slice.attach("stitch_c3_30", "CNode2", "192.168.30.2", "255.255.255.0");
    slice.attach("stitch_c3_30", "c3");
    if (coreProperties.isPlexusInSlice()) {
      slice.addPlexusController(coreProperties.getSdnSite(), plexusName);
    }
    return slice;
  }

  public void installTestGroup() {
    //===>>c0 =>c1 c2
    HashMap<String, Integer> nbs = new HashMap<>();
    int weight1 = 2;
    int weight2 = 1;
    nbs.put("c1", weight1);
    nbs.put("c2", weight2);
    int groupId = 1;
    SdnUtil.deleteGroup(getSDNController(), getDPID("c0"), groupId);
    routingmanager.setNextHops("c0", getSDNController(), groupId, "192.168.20.0/24", nbs);
    String res = SdnUtil.getGroupDescStats(getSDNController(), getDPID("c0"), groupId);
    logger.info("get group stats");
    logger.info(res);
    //==> c1->c3 c2->c3
    nbs.clear();
    nbs.put("c3", weight2);
    SdnUtil.deleteGroup(getSDNController(), getDPID("c1"), groupId);
    routingmanager.setNextHops("c1", getSDNController(), groupId, "192.168.20.0/24", nbs);
    res = SdnUtil.getGroupDescStats(getSDNController(), getDPID("c1"), groupId);
    logger.info("get group stats");
    logger.info(res);
    SdnUtil.deleteGroup(getSDNController(), getDPID("c2"), groupId);
    routingmanager.setNextHops("c2", getSDNController(), groupId, "192.168.20.0/24", nbs);
    res = SdnUtil.getGroupDescStats(getSDNController(), getDPID("c2"), groupId);
    logger.info("get group stats");
    logger.info(res);

    ////last hop
    //routingmanager.setOutPort("c3", getSDNController(),
    //    "stitch_c3_20","192.168.20.0/24" );
    //res = SdnUtil.getGroupDescStats(getSDNController(), getDPID("c3"), groupId);
    //logger.info(res);
    routingmanager.singleStepRouting("192.168.20.0/24", "192.168.20.2",
      getDPID("c3"), getSDNController());

    //<<==== c3-> c1 c2
    groupId = 2;
    nbs.clear();
    nbs.put("c1", weight1);
    nbs.put("c2", weight2);
    SdnUtil.deleteGroup(getSDNController(), getDPID("c3"), groupId);
    routingmanager.setNextHops("c3", getSDNController(), groupId, "192.168.10.0/24", nbs);
    res = SdnUtil.getGroupDescStats(getSDNController(), getDPID("c3"), groupId);
    logger.info("get group stats");
    logger.info(res);
    //<<===== c1->c0 c2 -> c0
    nbs.clear();
    nbs.put("c0", weight2);
    SdnUtil.deleteGroup(getSDNController(), getDPID("c1"), groupId);
    routingmanager.setNextHops("c1", getSDNController(), groupId, "192.168.10.0/24", nbs);
    res = SdnUtil.getGroupDescStats(getSDNController(), getDPID("c1"), groupId);
    logger.info("get group stats");
    logger.info(res);
    SdnUtil.deleteGroup(getSDNController(), getDPID("c2"), groupId);
    routingmanager.setNextHops("c2", getSDNController(), groupId, "192.168.10.0/24", nbs);
    res = SdnUtil.getGroupDescStats(getSDNController(), getDPID("c2"), groupId);
    logger.info("get group stats");
    logger.info(res);
    ///<<<===last hop
    //routingmanager.setOutPort("c0", getSDNController(),
    //    "stitch_c0_10","192.168.10.0/24" );
    routingmanager.singleStepRouting("192.168.10.0/24", "192.168.10.2",
      getDPID("c0"), getSDNController());
    logger.info(res);
  }

  public void logFlowTables() {
    try {
      Class[] cArg = new Class[1];
      cArg[0] = String.class;
      Method logFlowTables = this.getClass().getDeclaredMethod("logFlowTables", cArg);
      logFlowTables.setAccessible(true);
      logFlowTables.invoke(this, "c0");
      logFlowTables.invoke(this, "c1");
      logFlowTables.invoke(this, "c2");
      logFlowTables.invoke(this, "c3");
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void sendTraffic() {
    ExperimentBase experiment = new ExperimentBase(this);
    experiment.addClient("CNode0", serverSlice.getManagementIP("CNode0"),
      "192.168.10.2");
    experiment.addClient("CNode1", serverSlice.getManagementIP("CNode1"),
      "192.168.20.2");
    experiment.addClient("CNode2", serverSlice.getManagementIP("CNode2"),
      "192.168.30.2");
    experiment.addTcpFlow("CNode0", "CNode1", "1m", 20);
    experiment.addTcpFlow("CNode0", "CNode2", "1m", 20);
    //experiment.addTcpFlow("CNode2", "CNode0", "1m", 20);
    //experiment.addTcpFlow("CNode1", "CNode0", "1m", 20);
    experiment.setLatencyTask("CNode0", "CNode1");
    experiment.startLatencyTask();
    experiment.startFlows(10);
    logger.warn(String.format("start time %s", System.currentTimeMillis() / 1000));
    sleep(15);
    experiment.stopFlows();
    experiment.printFlowServerResult();
    experiment.stopLatencyTask();
    experiment.printLatencyResult();
    logger.warn(String.format("stop time %s", System.currentTimeMillis() / 1000));
  }

  public void getGroupStats() {
    int gid = 1;
    logger.info("---------------------");
    String res = SdnUtil.getGroupStats(getSDNController(), getDPID("c0"), gid);
    logger.info(res);
    logger.info("---------------------");
    res = SdnUtil.getGroupStats(getSDNController(), getDPID("c1"), gid);
    logger.info(res);
    logger.info("---------------------");
    res = SdnUtil.getGroupStats(getSDNController(), getDPID("c2"), gid);
    logger.info(res);
    logger.info("---------------------");
    res = SdnUtil.getGroupStats(getSDNController(), getDPID("c3"), gid);
    logger.info(res);
    logger.info("---------------------");
    gid = 2;
    logger.info("---------------------");
    res = SdnUtil.getGroupStats(getSDNController(), getDPID("c0"), gid);
    logger.info(res);
    logger.info("---------------------");
    res = SdnUtil.getGroupStats(getSDNController(), getDPID("c1"), gid);
    logger.info(res);
    logger.info("---------------------");
    res = SdnUtil.getGroupStats(getSDNController(), getDPID("c2"), gid);
    logger.info(res);
    logger.info("---------------------");
    res = SdnUtil.getGroupStats(getSDNController(), getDPID("c3"), gid);
    logger.info(res);
    logger.info("---------------------");
  }
}
