package exoplex.sdx.routing;

import exoplex.common.utils.ServerOptions;
import exoplex.sdx.core.SdxManager;
import exoplex.sdx.network.RoutingManager;
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

@Ignore
public class TestVlan extends SdxManager {
  static Logger logger = LogManager.getLogger(TestVlan.class);
  static String site = SiteBase.get("TAMU");
  static String userDir = System.getProperty("user.dir");
  static String sdxSimpleDir = userDir.split("SDX-Simple")[0] + "SDX-Simple/";
  static String[] arg1 = {"-c", sdxSimpleDir + "config/test-vlan.conf"};
  static TestVlan vlan;

  public TestVlan() {
    super(null);
  }

  @BeforeClass
  public static void setUp() throws Exception {
    vlan = new TestVlan();
    //create the slice
    vlan.test();
  }

  @AfterClass
  public static void cleanUp() {
    vlan.deleteSlice();
  }

  public static void main(String[] args) throws Exception {
    vlan.initNetwork();
    Method logFlowTables = vlan.getClass().getDeclaredMethod("logFlowTables");
    logFlowTables.setAccessible(true);
    logFlowTables.invoke(vlan);
    logger.debug("end");
  }

  @Test
  public void testVlan() throws Exception {
    try {
      vlan.initNetwork();
      Method logFlowTables = vlan.getClass().getDeclaredMethod("logFlowTables");
      logFlowTables.setAccessible(true);
      logFlowTables.invoke(vlan);
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
    //configRouting set address and gateways, but not static routing entry
    //delFlows();
    Method configRouting = super.getClass().getDeclaredMethod("configRouting");
    configRouting.setAccessible(true);
    configRouting.invoke(this);
    //delFlows();
    Method updateMacAddr = super.getClass().getDeclaredMethod("updateMacAddr");
    updateMacAddr.setAccessible(true);
    updateMacAddr.invoke(this);
  }

  public void createNetwork() throws Exception {
    serverSlice = createTestSlice();
    serverSlice.commitAndWait();
    configTestSlice(serverSlice);
    if (plexusInSlice) {
      checkPlexus(serverSlice, serverSlice.getManagementIP(plexusName), RoutingManager.plexusImage);
    }
    if (safeInSlice) {
      configSdnControllerAddr(serverSlice.getManagementIP(plexusName));
    } else {
      configSdnControllerAddr(conf.getString("config.plexusserver"));
    }
    serverSlice.runCmdSlice(Scripts.getOVSScript(), sshKey, routerPattern, true);
    copyRouterScript(serverSlice);
    configRouters(serverSlice);
  }

  public void configTestSlice(SliceManager carrier) {
    carrier.runCmdSlice("apt-get update;apt-get -y install quagga", sshKey, "(CNode\\d+)", true);
    carrier.runCmdSlice("sed -i -- 's/zebra=no/zebra=yes/g' /etc/quagga/daemons", sshKey, "(CNode\\d+)", true);
    //carrier.runCmdSlice("sed -i -- 's/ospfd=no/ospfd=yes/g' /etc/quagga/daemons", sshKey,
    // "(node\\d+)", true);
    carrier.runCmdSlice("echo \"1\" > /proc/sys/net/ipv4/ip_forward", sshKey, "(CNode\\d+)", true);
    try {
      carrier.runCmdSlice("ifconfig eth1 192.168.10.2/24 up", sshKey, "(CNode0)", true);
      carrier.runCmdSlice("ifconfig eth1 192.168.20.2/24 up", sshKey, "(CNode1)", true);
    } catch (Exception e) {
      e.printStackTrace();
    }
    carrier.runCmdSlice("echo \"ip route 192.168.1.1/16 192.168.10.1\" >>/etc/quagga/zebra" +
      ".conf", sshKey, "(CNode0)", true);
    carrier.runCmdSlice("echo \"ip route 192.168.1.1/16 192.168.20.1\" >>/etc/quagga/zebra" +
      ".conf", sshKey, "(CNode1)", true);
    carrier.runCmdSlice("/etc/init.d/quagga restart", sshKey, "(CNode\\d+)", true);
  }

  public SliceManager createTestSlice() {
    SliceManager slice = sliceManagerFactory.create(sliceName, pemLocation, keyLocation, controllerUrl,
      sshKey);
    slice.createSlice();
    slice.addComputeNode(site, "CNode0");
    slice.addComputeNode(site, "CNode1");
    slice.addOVSRouter(site, "c0");
    slice.addOVSRouter(site, "c1");
    slice.addBroadcastLink("stitch_c0_10");
    slice.attach("CNode0", "stitch_c0_10", "192.168.10.2", "255.255.255.0");
    slice.attach("c0", "stitch_c0_10", null, null);
    slice.addBroadcastLink("clink0");
    slice.attach("clink0", "c0");
    slice.attach("clink0", "c1");
    slice.addBroadcastLink("stitch_c1_20");
    slice.attach("stitch_c1_20", "CNode1", "192.168.20.2", "255.255.255.0");
    slice.attach("stitch_c1_20", "c1");
    if (plexusInSlice) {
      slice.addPlexusController(controllerSite, plexusName);
    }
    return slice;
  }
}
