package exoplex.demo.singlesdx;

import com.google.inject.Guice;
import com.google.inject.Injector;
import exoplex.demo.AbstractTest;
import exoplex.demo.AbstractTestSetting;
import exoplex.demo.AbstractTestSlice;
import exoplex.sdx.core.exogeni.ExoSdxManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class SingleSdxTest extends AbstractTest {
  final static Logger logger = LogManager.getLogger(SingleSdxTest.class);

  public static void main(String[] args) {
    SingleSdxTest singleSdxTest = new SingleSdxTest();
    Injector injector = Guice.createInjector(new SingleSdxModule());
    singleSdxTest.injector = injector;
    singleSdxTest.testSetting = injector.getInstance(AbstractTestSetting.class);
    singleSdxTest.testSlice = injector.getInstance(AbstractTestSlice.class);
    try {
      singleSdxTest.testSDX();
    } catch (Exception e) {
    }
  }

  @Override
  public void initTests() {
    injector = Guice.createInjector(new SingleSdxModule());
    testSlice = injector.getInstance(AbstractTestSlice.class);
    testSetting = injector.getInstance(AbstractTestSetting.class);
  }

  @Before
  @Override
  public void before() throws Exception {
    deleteSliceAfterTest = true;
    initTests();
    //deleteSlices();
    super.before();
  }

  @After
  @Override
  public void after() throws Exception {
    //super.after();
  }

  /**
   * single sdx test uses exoplex/config/singlesdx/sdx1.conf
   * Set the sdx key to key_p100 according to the settings
    * @throws Exception
   */
  @Test
  public void testSDX() throws Exception {
    startSdxServersAndClients(reset);
    stitchCustomerSlices();
    connectCustomerNetwork();
    checkConnection(1);
    //unStitchCustomerSlices();
    //stitchCustomerSlices();
    //connectCustomerNetwork();
    //checkConnection();
    //unStitchCustomerSlices();
  }

  @Override
  public void startSdxServersAndClients(boolean reset) {
    super.startSdxServersAndClients(reset);
    ExoSdxManager exoSdxManager = sdxManagerMap.values().iterator().next();
    String safeServerIp = getSafeServerIPfromSdxManager(exoSdxManager);
    setClientSafeServerIp(safeServerIp);
  }
}
