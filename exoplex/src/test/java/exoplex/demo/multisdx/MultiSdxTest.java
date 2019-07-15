package exoplex.demo.multisdx;

import com.google.inject.Guice;
import com.google.inject.Injector;
import exoplex.demo.AbstractTest;
import exoplex.demo.AbstractTestSetting;
import exoplex.demo.AbstractTestSlice;
import exoplex.demo.SdxTest;
import exoplex.sdx.core.SdxManager;
import injection.MultiSdxModule;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;


public class MultiSdxTest extends AbstractTest {
  final static Logger logger = LogManager.getLogger(SdxTest.class);

  public static void main(String[] args) {
    MultiSdxTest multiSdxTest = new MultiSdxTest();
    Injector injector = Guice.createInjector(new MultiSdxModule());
    multiSdxTest.reset = true;
    multiSdxTest.injector = injector;
    multiSdxTest.testSlice = injector.getInstance(AbstractTestSlice.class);
    multiSdxTest.testSetting = injector.getInstance(AbstractTestSetting.class);
    try {
      multiSdxTest.testMultiSdx();
      //multiSdxTest.replaySdnConfiguration("/home/yaoyj11/CICI-SAFE/exoplex/log/sdn.log");
      multiSdxTest.logFlowTables(false);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Override
  public void initTests() {
    injector = Guice.createInjector(new MultiSdxModule());
    testSlice = injector.getInstance(AbstractTestSlice.class);
    testSetting = injector.getInstance(AbstractTestSetting.class);
  }

  @Before
  @Override
  public void before() throws Exception {
    deleteSliceAfterTest = true;
    initTests();
    deleteSlices();
    super.before();
  }

  @After
  @Override
  public void after() throws Exception {
    super.after();
  }

  @Test
  public void testMultiSdx() throws Exception {
    startSdxServersAndClients(reset);
    //stitch sdx slices
    Long t0 = System.currentTimeMillis();
    stitchSdxSlices();
    Long t1 = System.currentTimeMillis();
    stitchCustomerSlices();
    Long t2 = System.currentTimeMillis();
    connectCustomerNetwork();
    Long t3 = System.currentTimeMillis();
    checkConnection();
    Long t4 = System.currentTimeMillis();
    logFlowTables(true);
    logger.info("test done");
    logger.info(String.format("Time\n stitch sdx: %s s\n stitch customers: %s s\n connection: %s s\n check " +
      "connection: %s s", (t1 - t0) / 1000.0, (t2 - t1) / 1000.0, (t3 - t2) / 1000.0, (t4 - t3) / 1000.0));
  }


  @Override
  public void startSdxServersAndClients(boolean reset) {
    super.startSdxServersAndClients(reset);
    SdxManager sdxManager = sdxManagerMap.values().iterator().next();
    String safeServerIp = getSafeServerIPfromSdxManager(sdxManager);
    setClientSafeServerIp(safeServerIp);
  }

  public void stitchSdxSlices() {
    for (Integer[] edge : testSetting.sdxNeighbor) {
      int i = edge[0];
      int j = edge[1];
      String slice1 = testSetting.sdxSliceNames.get(i);
      String slice2 = testSetting.sdxSliceNames.get(j);
      sdxManagerMap.get(slice1).adminCmd("stitch", new String[]{testSetting.sdxUrls.get(slice2), "e1"});
    }
  }
}
