package exoplex.demo.multisdx;

import exoplex.client.exogeni.SdxExogeniClient;
import exoplex.demo.SdxTest;
import exoplex.sdx.core.SdxManager;
import exoplex.sdx.core.SdxServer;
import exoplex.sdx.network.SdnReplay;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import riak.RiakSlice;
import safe.sdx.AuthorityMockSdx;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MultiSdxTestSD {
  final static Logger logger = LogManager.getLogger(SdxTest.class);
  static String userDir = System.getProperty("user.dir");
  static String sdxSimpleDir = userDir.split("SDX-Simple")[0] + "SDX-Simple/";
  static String[] riakArgs = new String[]{"-c", sdxSimpleDir + "config/riak.conf"};
  static String[] riakDelArgs = new String[]{"-c", sdxSimpleDir + "config/riak.conf", "-d"};
  static HashMap<String, SdxManager> sdxManagerMap = new HashMap<>();
  static MultiSdxSlice multiSdxSlice = new MultiSdxSlice();
  static boolean deleteSlice = true;
  static boolean reset = false;
  HashMap<String, SdxExogeniClient> exogeniClients = new HashMap<>();

  @BeforeClass
  public static void before() throws Exception {
    System.out.println("before test");
    after();
    //create RiakSlice
    RiakSlice riakSlice = new RiakSlice();
    String riakIP = riakSlice.run(riakArgs);
    //Sdx and client slices
    multiSdxSlice.createSdxSlices(riakIP);
    multiSdxSlice.createClientSlices(riakIP);
  }

  @AfterClass
  public static void after() throws Exception {
    if (deleteSlice) {
      RiakSlice riakSlice = new RiakSlice();
      riakSlice.run(riakDelArgs);
      multiSdxSlice.deleteSdxSlices();
      multiSdxSlice.deleteClientSlices();
    }
  }

  public static void main(String[] args) {
    reset = true;
    MultiSdxTestSD multiSdxTest = new MultiSdxTestSD();
    try {
      multiSdxTest.testMultiSdxSD();
      //multiSdxTest.replaySdnConfiguration();
      logFlowTables();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public static void logFlowTables() {
    for (SdxManager sdxManager : sdxManagerMap.values()) {
      Method logFlowTables = null;
      try {
        logFlowTables = sdxManager.getClass().getDeclaredMethod("logFlowTables", null);
      } catch (NoSuchMethodException e) {
        e.printStackTrace();
      }
      logFlowTables.setAccessible(true);
      try {
        logFlowTables.invoke(sdxManager);
      } catch (IllegalAccessException e) {
        e.printStackTrace();
      } catch (InvocationTargetException e) {
        e.printStackTrace();
      }
    }
  }

  public void replaySdnConfiguration() {
    startSdxServersAndClients(reset);
    SdnReplay.replay("/home/yaoyj11/CICI-SAFE/SDX-Simple/log/sdn.log");
    checkConnection();
    logger.info("replay done");

  }

  @Test
  public void testMultiSdxSD() throws Exception {
    startSdxServersAndClients(reset);
    //stitch sdx slices
    Long t0 = System.currentTimeMillis();

    stitchSdxSlices();
    Long t1 = System.currentTimeMillis();

    stitchCustomerSlices();
    Long t2 = System.currentTimeMillis();

    advertiseSDRoutesAndPolicies();

    connectCustomerNetwork();
    Long t3 = System.currentTimeMillis();

    checkConnection();
    Long t4 = System.currentTimeMillis();

    logger.info("test done");
    logger.info(String.format("Time\n stitch sdx: %s s\n stitch customers: %s s\n connection: %s s\n check " +
      "connection: %s s", (t1 - t0) / 1000.0, (t2 - t1) / 1000.0, (t3 - t2) / 1000.0, (t4 - t3) / 1000.0));
  }

  private void startSdxServersAndClients(boolean reset) {
    ArrayList<Thread> tlist = new ArrayList<>();
    for (String slice : MultiSdxSDSetting.sdxSliceNames) {
      Thread t = new Thread() {
        @Override
        public void run() {
          try {
            if (reset) {
              SdxManager sdxManager = SdxServer.run(MultiSdxSDSetting.sdxArgs.get(slice), MultiSdxSDSetting.sdxUrls.get
                (slice), slice);
              sdxManagerMap.put(slice, sdxManager);
            } else {
              SdxManager sdxManager = SdxServer.run(MultiSdxSDSetting.sdxNoResetArgs.get(slice),
                MultiSdxSDSetting.sdxUrls.get
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

    for (String clientSlice : MultiSdxSDSetting.clientSlices) {
      exogeniClients.put(clientSlice, new SdxExogeniClient(clientSlice,
        MultiSdxSDSetting.clientIpMap.get(clientSlice),
        MultiSdxSDSetting.clientKeyMap.get(clientSlice),
        MultiSdxSDSetting.clientArgs
      ));
    }
    for (String clientSlice : MultiSdxSetting.clientSlices) {
      SdxExogeniClient client = exogeniClients.get(clientSlice);
      SdxManager sdxManager = sdxManagerMap.values().iterator().next();
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
      client.setSafeServer(safeServerIp);
      client.setServerUrl(MultiSdxSetting.sdxUrls.get(MultiSdxSetting.clientSdxMap.get(clientSlice)));
    }
  }

  private void stitchSdxSlices() {
    for (Integer[] edge : MultiSdxSDSetting.sdxNeighbor) {
      int i = edge[0];
      int j = edge[1];
      String slice1 = MultiSdxSDSetting.sdxSliceNames.get(i);
      String slice2 = MultiSdxSDSetting.sdxSliceNames.get(j);
      sdxManagerMap.get(slice1).adminCmd("stitch", new String[]{MultiSdxSDSetting.sdxUrls.get(slice2), "e1"});
    }
  }


  private void stitchCustomerSlices() {
    for (String clientSlice : MultiSdxSDSetting.clientSlices) {
      String clientGateWay = MultiSdxSDSetting.clientIpMap.get(clientSlice).replace(".1/24", ".2");
      String sdxIP = MultiSdxSDSetting.clientIpMap.get(clientSlice).replace(".1/24", ".1/24");
      String gw = exogeniClients.get(clientSlice).processCmd(String.format("stitch CNode1 %s %s",
        clientGateWay, sdxIP));
      exogeniClients.get(clientSlice).processCmd(String.format("route %s %s",
        MultiSdxSDSetting.clientIpMap.get(clientSlice),
        gw));
    }
  }

  private void unStitchSlices() {
    for (String clientSlice : MultiSdxSDSetting.clientSlices) {
      exogeniClients.get(clientSlice).processCmd("unstitch CNode1");
    }
  }

  private void advertiseSDRoutesAndPolicies() {
    for (String client : MultiSdxSDSetting.clientSlices) {
      List<ImmutablePair<String, String>> pairAcls = MultiSdxSDSetting.userSDASTagAcls.get
        (client);
      String clientIp = MultiSdxSDSetting.clientIpMap.get(client);
      for (ImmutablePair<String, String> pair : pairAcls) {
        exogeniClients.get(client).processCmd(String.format("bgp %s %s %s", clientIp, pair
          .getLeft(), pair.getRight()));
        exogeniClients.get(client).processCmd(String.format("policy %s %s %s", pair.getLeft(),
          clientIp, pair.getRight()));
      }
    }
    logger.debug("SD routes made");
  }

  private void connectCustomerNetwork() {
    for (Integer[] pair : MultiSdxSDSetting.customerConnectionPairs) {
      int i = pair[0];
      int j = pair[1];
      String client = MultiSdxSDSetting.clientSlices.get(i);
      String clientIp = MultiSdxSDSetting.clientIpMap.get(client);
      String peer = MultiSdxSDSetting.clientSlices.get(j);
      String peerIp = MultiSdxSDSetting.clientIpMap.get(peer);
      exogeniClients.get(client).processCmd(String.format("link %s %s", clientIp, peerIp));
      exogeniClients.get(peer).processCmd(String.format("link %s %s", peerIp, clientIp));
    }
    logger.debug("connection ends");
  }

  private void checkConnection() {
    boolean flag = true;
    for (Integer[] pair : MultiSdxSDSetting.customerConnectionPairs) {
      int i = pair[0];
      int j = pair[1];
      String client = MultiSdxSDSetting.clientSlices.get(i);
      String clientIp = MultiSdxSDSetting.clientIpMap.get(client);
      String peer = MultiSdxSDSetting.clientSlices.get(j);
      String peerIp = MultiSdxSDSetting.clientIpMap.get(peer);
      if (!exogeniClients.get(client).checkConnectivity("CNode1",
        peerIp.replace(".1/24", ".2"))) {
        flag = false;
        deleteSlice = false;
      } else {
        System.out.println(exogeniClients.get(client).traceRoute("CNode1",
          peerIp.replace(".1/24", ".2")));
      }
    }
    deleteSlice = false;

  }
}
