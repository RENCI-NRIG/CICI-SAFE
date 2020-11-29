package exoplex.sdx.network;

import com.google.inject.Guice;
import com.google.inject.Injector;
import exoplex.common.utils.ServerOptions;
import exoplex.sdx.core.CoreProperties;
import exoplex.sdx.core.SdxManagerBase;
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

public class IngressFilteringTest extends SingleSliceTestBase {
  static IngressFilteringTest ingressFilteringTest;

  public IngressFilteringTest() {
    super.logger = LogManager.getLogger(IngressFilteringTest.class);
    super.site1 = SiteBase.get("TAMU");
    super.site2 = SiteBase.get("TAMU");
    super.userDir = System.getProperty("user.dir");
    super.sdxSimpleDir = userDir.split("exoplex")[0] + "exoplex/";
    super.arg1 = new String[]{"-c", sdxSimpleDir + "config/ingress/ingress.conf"};
  }

  @BeforeClass
  public static void setUp() throws Exception {
    Injector injector = Guice.createInjector(new QoSModule());

    ingressFilteringTest = injector.getInstance(IngressFilteringTest.class);
    ingressFilteringTest.routingManager = injector.getInstance(AbstractRoutingManager.class);
    ingressFilteringTest.initTest();
  }

  @AfterClass
  public static void cleanUp() {
    //ingressFilteringTest.deleteSlice();
  }

  public void initTest() throws Exception {
    CommandLine cmd = ServerOptions.parseCmd(arg1);
    String configFilePath = cmd.getOptionValue("config");
    this.readConfig(configFilePath);
    ingressFilteringTest.coreProperties.setSdnApp("rest_router");
    //ingressFilteringTest.deleteSlice();
    //createNetwork();
  }

  @Test
  public void testIf() throws Exception {
    ingressFilteringTest.startExoPlex();
    ingressFilteringTest.notifyPrefix("192.168.10.1/24", "192.168.10.2", "CNode0");
    ingressFilteringTest.notifyPrefix("192.168.20.1/24", "192.168.20.2", "CNode1");
    ingressFilteringTest.notifyPrefix("192.168.30.1/24", "192.168.30.2", "CNode2");
    ingressFilteringTest.notifyPrefix("192.168.40.1/24", "192.168.40.2", "CNode3");
    ingressFilteringTest.connectionRequest("192.168.10.1/24", "192.168.30.1/24", 0);
    ingressFilteringTest.connectionRequest("192.168.20.1/24", "192.168.40.1/24", 0);
    ingressFilteringTest.checkConnectivity("CNode0", "192.168.30.2");
    ingressFilteringTest.logFlowTables();
    logger.info("test ends");
  }

  @Override
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

    slice.addBroadcastLink("clink0", 100000000);
    slice.attach("clink0", "c0");
    slice.attach("clink0", "c1");
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

}
