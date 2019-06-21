package exoplex.demo;

import com.google.inject.Guice;
import com.google.inject.Injector;
import exoplex.client.exogeni.SdxExogeniClient;
import exoplex.demo.tridentcom.TridentSetting;
import exoplex.demo.tridentcom.TridentSlice;
import exoplex.sdx.core.SdxManager;
import exoplex.sdx.core.SdxServer;
import exoplex.sdx.safe.SafeManager;
import injection.TridentTestModule;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import riak.RiakSlice;
import safe.sdx.AuthorityMockSdx;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;

@Ignore
public class SdxTest {
  final static Logger logger = LogManager.getLogger(SdxTest.class);
  static String userDir = System.getProperty("user.dir");
  static String sdxSimpleDir = userDir.split("exoplex")[0] + "exoplex/";
  static String[] riakArgs = new String[]{"-c", sdxSimpleDir + "config/riak.conf"};
  static String[] riakDelArgs = new String[]{"-c", sdxSimpleDir + "config/riak.conf", "-d"};
  static SdxManager sdxManager;
  HashMap<String, SdxExogeniClient> exogeniClients = new HashMap<>();
  Injector injector = Guice.createInjector(new TridentTestModule());
  TridentSlice tridentSlice = injector.getInstance(TridentSlice.class);
  boolean deleteSlice = true;

  public static void main(String[] args) {
    Injector injector = Guice.createInjector(new TridentTestModule());
    SdxTest sdxTest = new SdxTest();
    try {
      sdxTest.TestSDX();
    } catch (Exception e) {
    }
  }

  @Before
  public void before() throws Exception {
    SafeManager.setSafeDockerImage("safeserver-v7");
    SafeManager.setSafeServerScript("prdn.sh");
    System.out.println("before test");
    after();
    deleteSlice = false;
    //create RiakSlice
    RiakSlice riakSlice = new RiakSlice();
    String riakIP = riakSlice.run(riakArgs);
    //Sdx and client slices
    TridentSlice.createSlices(riakIP);
  }

  @After
  public void after() throws Exception {
    if (deleteSlice) {
      RiakSlice riakSlice = new RiakSlice();
      String riakIP = riakSlice.run(riakDelArgs);
      TridentSlice.deleteTestSlices();
      System.out.println("after");
    }
  }

  @Test
  public void TestSDX() throws Exception {
    SdxServer sdxServer = injector.getProvider(SdxServer.class).get();
    sdxManager = sdxServer.run(TridentSetting.sdxArgs);
    for (String clientSlice : TridentSetting.clientSlices) {
      exogeniClients.put(clientSlice, new SdxExogeniClient(clientSlice,
        TridentSetting.clientIpMap.get(clientSlice),
        TridentSetting.clientKeyMap.get(clientSlice),
        TridentSetting.clientArgs
      ));
    }
    for (SdxExogeniClient client : exogeniClients.values()) {
      Method getSafeServerIP = sdxManager.getClass().getDeclaredMethod("getSafeServerIP", null);
      getSafeServerIP.setAccessible(true);
      String safeIP = (String) getSafeServerIP.invoke(sdxManager);
      client.setSafeServer(safeIP);
    }
    stitchSlices();
    connectCustomerNetwork();
    unStitchSlices();
    stitchSlices();
    connectCustomerNetwork();
    unStitchSlices();
  }

  private void stitchSlices() {
    for (String clientSlice : TridentSetting.clientSlices) {
      if (sdxManager.safeEnabled) {
        Method getSafeServerIP = null;
        try {
          getSafeServerIP = sdxManager.getClass().getDeclaredMethod("getSafeServerIP", null);
        } catch (NoSuchMethodException e) {
          e.printStackTrace();
        }
        getSafeServerIP.setAccessible(true);
        String safeServerIp = null;
        try {
          safeServerIp = (String) getSafeServerIP.invoke(sdxManager);
        } catch (IllegalAccessException e) {
          e.printStackTrace();
        } catch (InvocationTargetException e) {
          e.printStackTrace();
        }
        AuthorityMockSdx.main(new String[]{TridentSetting.clientKeyMap.get(clientSlice),
          clientSlice,
          TridentSetting.clientIpMap.get(clientSlice),
          safeServerIp});
      }
      String clientGateWay = TridentSetting.clientIpMap.get(clientSlice).replace(".1/24", ".2");
      String sdxIP = TridentSetting.clientIpMap.get(clientSlice).replace(".1/24", ".1/24");
      String gw = exogeniClients.get(clientSlice).processCmd(String.format("stitch CNode1 %s %s",
        clientGateWay, sdxIP));
      exogeniClients.get(clientSlice).processCmd(String.format("route %s %s",
        TridentSetting.clientIpMap.get(clientSlice),
        gw));
    }
  }

  private void unStitchSlices() {
    for (String clientSlice : TridentSetting.clientSlices) {
      exogeniClients.get(clientSlice).processCmd("unstitch CNode1");
    }
  }

  private void connectCustomerNetwork() {
    for (int i = 0; i < TridentSetting.clientSlices.size(); i++) {
      String client = TridentSetting.clientSlices.get(i);
      String clientIp = TridentSetting.clientIpMap.get(client);
      for (int j = i + 1; j < TridentSetting.clientSlices.size(); j++) {
        String peer = TridentSetting.clientSlices.get(j);
        String peerIp = TridentSetting.clientIpMap.get(peer);
        exogeniClients.get(client).processCmd(String.format("link %s %s",
          TridentSetting.clientIpMap.get(client),
          TridentSetting.clientIpMap.get(peer)));
        exogeniClients.get(peer).processCmd(String.format("link %s %s",
          TridentSetting.clientIpMap.get(peer),
          TridentSetting.clientIpMap.get(client)));

        if (!exogeniClients.get(client).checkConnectivity("CNode1",
          peerIp.replace(".1/24", ".2"), 3)) {
          sdxManager.checkFlowTableForPair(clientIp.replace(".1/24", ".0/24"),
            peerIp.replace(".1/24", ".0/24"),
            clientIp, peerIp);
          deleteSlice = false;
          assert false;
        }
      }
    }
  }
}
