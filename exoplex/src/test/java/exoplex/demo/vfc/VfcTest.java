package exoplex.demo.vfc;

import com.google.inject.Guice;
import com.google.inject.Injector;
import exoplex.client.exogeni.SdxExogeniClient;
import exoplex.demo.AbstractTest;
import exoplex.demo.AbstractTestSetting;
import exoplex.demo.AbstractTestSlice;
import exoplex.demo.singlesdx.SingleSdxModule;
import exoplex.sdx.core.CoreProperties;
import exoplex.sdx.core.SdxManagerBase;
import exoplex.sdx.core.SdxServerBase;
import exoplex.sdx.slice.exogeni.ExoGeniSliceModule;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import riak.RiakSlice;

import java.util.ArrayList;
import java.util.List;

public class VfcTest extends AbstractTest {
  final static Logger logger = LogManager.getLogger(VfcTest.class);
  Injector clientInjector;

  @Override
  public void initTests() {
    clientInjector = Guice.createInjector(new VfcClientModule());
    injector = Guice.createInjector(new VfcSdxModule());
    testSlice = clientInjector.getInstance(AbstractTestSlice.class);
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
    super.after();
  }

  /**
   * single sdx test uses exoplex/config/singlesdx/sdx1.conf
   * Set the sdx key to key_p100 according to the settings
   *
   * @throws Exception
   */
  @Test
  public void testSDX() throws Exception {
    startSdxServersAndClients(reset);
    stitchCustomerSlices();
    connectCustomerNetwork();
    checkConnection(1);
  }

  @Override
  public void startSdxServersAndClients(boolean reset) {
    List<Thread> tlist = new ArrayList<>();
    for (String slice : testSetting.sdxSliceNames) {
      Thread t = new Thread() {
        @Override
        public void run() {
          try {
            SdxServerBase exoSdxServer = injector.getProvider(SdxServerBase.class).get();
            CoreProperties coreProperties = new CoreProperties(testSetting.sdxArgs.get(slice));
            coreProperties.setServerUrl(testSetting.sdxUrls.get(slice));
            coreProperties.setSliceName(slice);
            coreProperties.setBw(testSlice.stitchBw);
            coreProperties.setSafeEnabled(testSetting.safeEnabled);
            if (reset) {
              coreProperties.setReset(true);
            }
            SdxManagerBase sdxManager = exoSdxServer.run(coreProperties);
            sdxManagerMap.put(slice, sdxManager);
          } catch (Exception e) {
            e.printStackTrace();
          }
        }
      };
      tlist.add(t);
    }
    for (Thread t : tlist) {
      t.start();
    }
    try {
      for (Thread t : tlist) {
        t.join();
      }
    } catch (Exception e) {

    }
    startClients();
  }

  @Override
  public void startClients() {
    for (String clientSlice : testSetting.clientSlices) {
      SdxExogeniClient sdxExogeniClient = clientInjector.getProvider(SdxExogeniClient.class).get();
      CoreProperties coreProperties = new CoreProperties(testSetting.clientArgs);
      coreProperties.setIpPrefix(testSetting.clientIpMap.get(clientSlice));
      coreProperties.setSafeKeyFile(testSetting.clientKeyMap.get(clientSlice));
      coreProperties.setSliceName(clientSlice);
      coreProperties.setSafeEnabled(testSetting.safeEnabled);
      coreProperties.setServerUrl(testSetting.sdxUrls.get(testSetting.clientSdxMap.get(clientSlice)));
      sdxExogeniClient.config(coreProperties);
      exogeniClients.put(clientSlice, sdxExogeniClient);
    }
  }

  @Override
  public void stitchCustomerSlices() {
    for (String clientSlice : testSetting.clientSlices) {
      String clientGateWay = testSetting.clientIpMap.get(clientSlice).replace(".1/24", ".2");
      String sdxInterfaceIP = testSetting.clientIpMap.get(clientSlice).replace(".1/24", ".1/24");
      String vfcSite = ((VfcSdxSetting) testSetting).vfcSiteMap.get(clientSlice);
      String vlan = ((VfcSdxSetting) testSetting).vfcVlanMap.get(clientSlice);
      //stitchvfc CNode1 UC 3295 192.168.201.2 192.168.201.1/24
      String gw = exogeniClients.get(clientSlice).processCmd(
        String.format("stitchvfc CNode1 %s %s %s %s", vfcSite, vlan, clientGateWay,
          sdxInterfaceIP));
      exogeniClients.get(clientSlice).processCmd(String.format("route %s %s",
        testSetting.clientIpMap.get(clientSlice),
        clientGateWay));
    }
  }

  @Override
  public void createSlices() throws Exception {
    String riakIP = null;
    if (testSetting.safeEnabled) {
      RiakSlice riakSlice = injector.getInstance(RiakSlice.class);
      riakIP = riakSlice.run(new CoreProperties(testSetting.riakArgs));
    }
    testSlice.createClientSlices(riakIP);
    testSlice.runThreads();
  }

  @Override
  public void deleteSlices() throws Exception {
    RiakSlice riakSlice = injector.getInstance(RiakSlice.class);
    CoreProperties coreProperties = new CoreProperties(testSetting.riakArgs);
    coreProperties.setType("delete");
    riakSlice.run(coreProperties);
    testSlice.deleteClientSlices();
  }
}
