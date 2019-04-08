package exoplex.demo;

import com.google.inject.Injector;
import exoplex.client.exogeni.SdxExogeniClient;
import exoplex.sdx.core.SdxManager;
import exoplex.sdx.core.SdxServer;
import exoplex.sdx.network.SdnReplay;
import exoplex.sdx.safe.SafeManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import riak.RiakSlice;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;

public abstract class AbstractTest {
  final static Logger logger = LogManager.getLogger(AbstractTest.class);
  public HashMap<String, SdxManager> sdxManagerMap = new HashMap<>();
  public HashMap<String, SdxExogeniClient> exogeniClients = new HashMap<>();
  public Injector injector;
  public boolean deleteSliceAfterTest = true;
  public boolean reset = true;
  public AbstractTestSetting testSetting;
  public AbstractTestSlice testSlice;

  public abstract void initTests();

  public void before() throws Exception {
    initTests();
    SafeManager.setSafeDockerImage(testSetting.dockerImage);
    SafeManager.setSafeServerScript(testSetting.safeServerScript);
    createSlices();
  }

  public void createSlices() throws Exception {
    RiakSlice riakSlice = injector.getInstance(RiakSlice.class);
    String riakIP = riakSlice.run(testSetting.riakArgs);
    testSlice.createSdxSlices(riakIP);
    testSlice.createClientSlices(riakIP);
  }

  public void deleteSlices() throws Exception {
    RiakSlice riakSlice = injector.getInstance(RiakSlice.class);
    riakSlice.run(testSetting.riakDelArgs);
    testSlice.deleteSdxSlices();
    testSlice.deleteClientSlices();
  }

  public void after() throws Exception {
    if (deleteSliceAfterTest) {
      deleteSlices();
    }
  }

  public void startSdxServersAndClients(boolean reset) {
    ArrayList<Thread> tlist = new ArrayList<>();
    for (String slice : testSetting.sdxSliceNames) {
      Thread t = new Thread() {
        @Override
        public void run() {
          try {
            if (reset) {
              SdxServer sdxServer = injector.getProvider(SdxServer.class).get();
              SdxManager sdxManager = sdxServer.run(testSetting.sdxArgs.get(slice),
                testSetting.sdxUrls.get
                  (slice), slice);
              sdxManagerMap.put(slice, sdxManager);
            } else {
              SdxServer sdxServer = injector.getProvider(SdxServer.class).get();
              SdxManager sdxManager = sdxServer.run(testSetting.sdxNoResetArgs.get(slice),
                testSetting.sdxUrls.get
                  (slice), slice);
              sdxManagerMap.put(slice, sdxManager);
            }
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

    for (String clientSlice : testSetting.clientSlices) {
      SdxExogeniClient sdxExogeniClient = injector.getProvider(SdxExogeniClient.class).get();
      sdxExogeniClient.config(clientSlice,
        testSetting.clientIpMap.get(clientSlice),
        testSetting.clientKeyMap.get(clientSlice),
        testSetting.clientArgs
      );
      exogeniClients.put(clientSlice, sdxExogeniClient);
    }
    for (String clientSlice : testSetting.clientSlices) {
      SdxExogeniClient client = exogeniClients.get(clientSlice);
      client.setServerUrl(testSetting.sdxUrls.get(testSetting.clientSdxMap.get(clientSlice)));
    }
  }

  public String getSafeServerIPfromSdxManager(SdxManager sdxManager) {
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
    return safeServerIp;
  }

  public void setClientSafeServerIp(String safeServerIp) {
    for (String clientSlice : testSetting.clientSlices) {
      exogeniClients.get(clientSlice).setSafeServer(safeServerIp);
    }
  }

  public void stitchCustomerSlices() {
    for (String clientSlice : testSetting.clientSlices) {
      String clientGateWay = testSetting.clientIpMap.get(clientSlice).replace(".1/24", ".2");
      String sdxInterfaceIP = testSetting.clientIpMap.get(clientSlice).replace(".1/24", ".1/24");
      String gw = exogeniClients.get(clientSlice).processCmd(String.format("stitch CNode1 %s %s",
        clientGateWay, sdxInterfaceIP));
      exogeniClients.get(clientSlice).processCmd(String.format("route %s %s",
        testSetting.clientIpMap.get(clientSlice),
        gw));
    }
  }

  public void unStitchCustomerSlices() {
    for (String clientSlice : testSetting.clientSlices) {
      exogeniClients.get(clientSlice).processCmd("unstitch CNode1");
    }
  }

  public void connectCustomerNetwork() {
    for (Integer[] pair : testSetting.clientConnectionPairs) {
      int i = pair[0];
      int j = pair[1];
      String client = testSetting.clientSlices.get(i);
      String clientIp = testSetting.clientIpMap.get(client);
      String peer = testSetting.clientSlices.get(j);
      String peerIp = testSetting.clientIpMap.get(peer);
      exogeniClients.get(client).processCmd(String.format("link %s %s", clientIp, peerIp));
      exogeniClients.get(peer).processCmd(String.format("link %s %s", peerIp, clientIp));
    }
    logger.debug("connection ends");
  }

  public boolean checkConnection() {
    return checkConnection(3);
  }

  public boolean checkConnection(int times) {
    logger.debug("checking connections");
    boolean flag = true;
    for (Integer[] pair : testSetting.clientConnectionPairs) {
      int i = pair[0];
      int j = pair[1];
      String client = testSetting.clientSlices.get(i);
      String clientIp = testSetting.clientIpMap.get(client);
      String peer = testSetting.clientSlices.get(j);
      String peerIp = testSetting.clientIpMap.get(peer);
      if (!exogeniClients.get(client).checkConnectivity("CNode1",
        peerIp.replace(".1/24", ".2"), times)) {
        flag = false;
      } else {
        System.out.println(exogeniClients.get(client).traceRoute("CNode1",
          peerIp.replace(".1/24", ".2")));
      }
    }
    return flag;
  }

  public void logFlowTables(boolean showAll) {
    ArrayList<String> patterns = new ArrayList<>();
    if (!showAll) {
      String routeFlowPattern = ".*nw_src=%s.*nw_dst=%s.*actions=dec_ttl.*";
      for (Integer[] pair : testSetting.clientConnectionPairs) {
        String slice1 = testSetting.clientSlices.get(pair[0]);
        String slice2 = testSetting.clientSlices.get(pair[1]);
        String ip1 = testSetting.clientIpMap.get(slice1).replace(".1/24", ".0/24");
        String ip2 = testSetting.clientIpMap.get(slice2).replace(".1/24", ".0/24");
        patterns.add(String.format(routeFlowPattern, ip1, ip2));
        patterns.add(String.format(routeFlowPattern, ip2, ip1));
      }
    }
    for (SdxManager sdxManager : sdxManagerMap.values()) {
      try {
        sdxManager.logFlowTables(patterns);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  public void replaySdnConfiguration(String logFile) {
    startSdxServersAndClients(false);
    SdnReplay.replay(logFile);
    checkConnection();
    logger.info("replay done");
  }
}
