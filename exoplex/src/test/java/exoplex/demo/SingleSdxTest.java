package exoplex.demo;

import com.google.inject.Guice;
import com.google.inject.Injector;
import injection.SingleSdxModule;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

@Ignore
public class SingleSdxTest extends AbstractTest {
  final static Logger logger = LogManager.getLogger(SingleSdxTest.class);

  public static void main(String[] args) {
    SingleSdxTest singleSdxTest = new SingleSdxTest();
    Injector injector = Guice.createInjector(new SingleSdxModule());
    singleSdxTest.injector = injector;
    singleSdxTest.testSetting = injector.getInstance(AbstractTestSetting.class);
    singleSdxTest.testSlice = injector.getInstance(AbstractTestSlice.class);
    try {
      singleSdxTest.TestSDX();
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
    deleteSliceAfterTest = false;
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
  public void TestSDX() throws Exception {
    startSdxServersAndClients(reset);
    stitchCustomerSlices();
    connectCustomerNetwork();
    checkConnection();
    unStitchCustomerSlices();
    stitchCustomerSlices();
    connectCustomerNetwork();
    checkConnection();
    unStitchCustomerSlices();
  }
}
