package exoplex.demo.singlesdx;

import com.google.inject.Guice;
import exoplex.demo.AbstractTest;
import exoplex.demo.AbstractTestSetting;
import exoplex.demo.AbstractTestSlice;
import exoplex.sdx.core.exogeni.ExoSdxManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class SingleSdxReplayTest extends AbstractTest {
  final static Logger logger = LogManager.getLogger(SingleSdxReplayTest.class);


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
  }

  @After
  @Override
  public void after() throws Exception {
    //super.after();
  }

  @Test
  public void testReplaySdnCmds() throws Exception {
    startSdxServersAndClients(false);
    replaySdnConfiguration("/home/yaoyj11/CICI-SAFE/exoplex/log/sdn.log");
    sleep(1200);
  }

  @Override
  public void startSdxServersAndClients(boolean reset) {
    super.startSdxServersAndClients(reset);
    ExoSdxManager exoSdxManager = (ExoSdxManager) sdxManagerMap.values().iterator().next();
    String safeServerIp = getSafeServerIPfromSdxManager(exoSdxManager);
    setClientSafeServerIp(safeServerIp);
  }
}
