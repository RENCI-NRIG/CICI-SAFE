package exoplex.sdx.routing;
import exoplex.common.slice.*;
import exoplex.experiment.ExperimentBase;
import org.apache.commons.cli.CommandLine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import exoplex.sdx.core.SdxManager;
import exoplex.sdx.network.SdnUtil;

import java.util.HashMap;

public class TestMpRouting extends SdxManager {
  static Logger logger = LogManager.getLogger(TestMpRouting.class);
  static String[] arg1 = {"-c", "config/test-mptcp.conf"};
  static String site= SiteBase.get("BBN");

  public static void main(String[] args) throws Exception {
    TestMpRouting mpr = new TestMpRouting();
    //create the network
    mpr.test();

    mpr.initNetwork();
    mpr.installTestGroup();
    mpr.sendTraffic();
    mpr.getGroupStats();
    System.out.println("end");
  }

  public  void test() throws Exception {
    CommandLine cmd = parseCmd(arg1);
    String configfilepath = cmd.getOptionValue("config");
    readConfig(configfilepath);
    getSshContext();
    createNetwork();
  }

  public void initNetwork() throws Exception {
    CommandLine cmd = parseCmd(arg1);
    String configfilepath = cmd.getOptionValue("config");
    readConfig(configfilepath);
    getSshContext();
    plexusName = "plexus";
    readConfig(arg1);
    initializeSdx();
    delFlows();
    configRouting();
    updateMacAddr();
  }

  public void createNetwork() throws Exception {
    SafeSlice slice = createTestSlice();
    slice.commitAndWait();
    configTestSlice(slice);
    configSdnControllerAddr(slice.getComputeNode("plexus").getManagementIP());
    checkPlexus(slice.getComputeNode("plexus").getManagementIP());
    slice.runCmdSlice(Scripts.getOVSScript(), sshkey, routerPattern, true);
    copyRouterScript(slice);
    configRouters(slice);
  }

  public void configTestSlice(SafeSlice carrier){
    carrier.runCmdSlice("apt-get update;apt-get -y install quagga", sshkey,"(CNode\\d+)", true);
    carrier.runCmdSlice("sed -i -- 's/zebra=no/zebra=yes/g' /etc/quagga/daemons", sshkey, "(CNode\\d+)", true);
    //carrier.runCmdSlice("sed -i -- 's/ospfd=no/ospfd=yes/g' /etc/quagga/daemons", sshkey,
    // "(node\\d+)", true);
    carrier.runCmdSlice("echo \"1\" > /proc/sys/net/ipv4/ip_forward", sshkey, "(CNode\\d+)", true);
    try {
      carrier.runCmdSlice("ifconfig eth1 192.168.10.2/24 up", sshkey, "(CNode0)", true);
      carrier.runCmdSlice("ifconfig eth1 192.168.20.2/24 up", sshkey, "(CNode1)", true);
      carrier.runCmdSlice("ifconfig eth1 192.168.30.2/24 up", sshkey, "(CNode2)", true);
    } catch (Exception e) {
      e.printStackTrace();
    }
    carrier.runCmdSlice("echo \"ip route 192.168.1.1/16 192.168.10.1\" >>/etc/quagga/zebra" +
        ".conf", sshkey, "(CNode0)", true);
    carrier.runCmdSlice("echo \"ip route 192.168.1.1/16 192.168.20.1\" >>/etc/quagga/zebra" +
        ".conf", sshkey, "(CNode1)", true);
    carrier.runCmdSlice("echo \"ip route 192.168.1.1/16 192.168.30.1\" >>/etc/quagga/zebra" +
        ".conf", sshkey, "(CNode2)", true);
    carrier.runCmdSlice("/etc/init.d/quagga restart", sshkey, "(CNode\\d+)", true);
  }

  public SafeSlice createTestSlice(){
    SafeSlice slice = SafeSlice.create("test-yyj", pemLocation, keyLocation, controllerUrl, sctx);
    slice.addComputeNode(site, "CNode0");
    slice.addComputeNode(site,"CNode1");
    slice.addComputeNode(site,"CNode2");
    slice.addOVSRouter(site,"c0");
    slice.addOVSRouter(site,"c1");
    slice.addOVSRouter(site,"c2");
    slice.addOVSRouter(site,"c3");
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
    slice.addPlexusController(controllerSite, "plexus");
    return  slice;
  }

  public void installTestGroup(){
    //===>>c0 =>c1 c2
    HashMap<String, Integer> nbs = new HashMap<>();
    nbs.put("c1", 10);
    nbs.put("c2", 5);
    int groupId = 1;
    SdnUtil.deleteGroup(getSDNController(), getDPID("c0"), groupId);
    routingmanager.setNextHops("c0", getSDNController(), groupId, "192.168.20.0/24", nbs);
    //==> c1->c3 c2->c3
    nbs.clear();
    nbs.put("c3", 5);
    SdnUtil.deleteGroup(getSDNController(), getDPID("c1"), groupId);
    routingmanager.setNextHops("c1", getSDNController(), groupId, "192.168.20.0/24", nbs);
    String res = SdnUtil.getGroupDescStats(getSDNController(), getDPID("c1"), groupId);
    logger.info(res);
    SdnUtil.deleteGroup(getSDNController(), getDPID("c2"), groupId);
    routingmanager.setNextHops("c2", getSDNController(), groupId, "192.168.20.0/24", nbs);
    res = SdnUtil.getGroupDescStats(getSDNController(), getDPID("c2"), groupId);
    logger.info(res);

    ////last hop
    //routingmanager.setOutPort("c3", getSDNController(),
    //    "stitch_c3_20","192.168.20.0/24" );
    //res = SdnUtil.getGroupDescStats(getSDNController(), getDPID("c3"), groupId);
    //logger.info(res);
    routingmanager.singleStepRouting("192.168.20.0/24", "192.168.20.2",
        getDPID("c3"), getSDNController());

    //<<==== c3-> c1 c2
    groupId  = 2;
    nbs.clear();
    nbs.put("c1", 10);
    nbs.put("c2", 5);
    SdnUtil.deleteGroup(getSDNController(), getDPID("c3"), groupId);
    routingmanager.setNextHops("c3", getSDNController(), groupId, "192.168.10.0/24", nbs);
    res = SdnUtil.getGroupDescStats(getSDNController(), getDPID("c3"), groupId);
    logger.info(res);
    //<<===== c1->c0 c2 -> c0
    nbs.clear();
    nbs.put("c0", 5);
    SdnUtil.deleteGroup(getSDNController(), getDPID("c1"), groupId);
    routingmanager.setNextHops("c1", getSDNController(), groupId, "192.168.10.0/24", nbs);
    res = SdnUtil.getGroupDescStats(getSDNController(), getDPID("c1"), groupId);
    logger.info(res);
    SdnUtil.deleteGroup(getSDNController(), getDPID("c2"), groupId);
    routingmanager.setNextHops("c2", getSDNController(), groupId, "192.168.10.0/24", nbs);
    res = SdnUtil.getGroupDescStats(getSDNController(), getDPID("c2"), groupId);
    logger.info(res);
    ///<<<===last hop
    //routingmanager.setOutPort("c0", getSDNController(),
    //    "stitch_c0_10","192.168.10.0/24" );
    routingmanager.singleStepRouting("192.168.10.0/24", "192.168.10.2",
        getDPID("c0"), getSDNController());
    logger.info(res);

    logFlowTables("c0");
    logFlowTables("c1");
    logFlowTables("c2");
    logFlowTables("c3");
  }

  public void sendTraffic(){
    ExperimentBase experiment = new ExperimentBase(this);
    experiment.addClient("CNode0", serverSlice.getComputeNode("CNode0").getManagementIP(),
        "192.168.10.2");
    experiment.addClient("CNode1", serverSlice.getComputeNode("CNode1").getManagementIP(),
        "192.168.20.2");
    experiment.addClient("CNode2", serverSlice.getComputeNode("CNode2").getManagementIP(),
        "192.168.30.2");
    experiment.addUdpFlow("CNode0", "CNode1", "1m");
    experiment.startFlows(10);
    sleep(10);
    experiment.stopFlows();
  }

  public void getGroupStats(){
    logger.info("---------------------");
    String res = SdnUtil.getGroupStats(getSDNController(), getDPID("c0"));
    logger.info(res);
    logger.info("---------------------");
    res = SdnUtil.getGroupStats(getSDNController(), getDPID("c1"));
    logger.info(res);
    logger.info("---------------------");
    res = SdnUtil.getGroupStats(getSDNController(), getDPID("c2"));
    logger.info(res);
    logger.info("---------------------");
    res = SdnUtil.getGroupStats(getSDNController(), getDPID("c3"));
    logger.info(res);
    logger.info("---------------------");
  }
}
