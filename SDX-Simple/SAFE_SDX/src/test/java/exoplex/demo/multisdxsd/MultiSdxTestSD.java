package exoplex.demo.multisdxsd;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import exoplex.demo.AbstractTest;
import exoplex.demo.AbstractTestSetting;
import exoplex.demo.AbstractTestSlice;
import exoplex.sdx.core.SdxManager;
import exoplex.sdx.safe.SafeManager;
import injection.MultiSdxSDLargeModule;
import injection.MultiSdxSDModule;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class MultiSdxTestSD extends AbstractTest {
  final static Logger logger = LogManager.getLogger(MultiSdxTestSD.class);
  final static AbstractModule module = new MultiSdxSDLargeModule();
  //final static AbstractModule module = new MultiSdxSDMockModule();

  public static void main(String[] args) {
    MultiSdxTestSD multiSdxTestSD = new MultiSdxTestSD();
    Injector injector = Guice.createInjector(module);
    multiSdxTestSD.reset = true;
    multiSdxTestSD.injector = injector;
    multiSdxTestSD.testSlice = injector.getInstance(AbstractTestSlice.class);
    multiSdxTestSD.testSetting = injector.getInstance(AbstractTestSetting.class);
    SafeManager.setSafeDockerImage(multiSdxTestSD.testSetting.dockerImage);
    try {
      multiSdxTestSD.testMultiSdxSD();
      //multiSdxTestSD.replaySdnConfiguration("/home/yaoyj11/CICI-SAFE/SDX-Simple/log/sdn.log");
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Override
  public void initTests() {
    injector = Guice.createInjector(module);
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

    checkConnection(3);
    ping(1000);
    Long t4 = System.currentTimeMillis();

    logFlowTables(false);

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

  @Override
  public void stitchCustomerSlices() {
    ArrayList<Thread> tlist = new ArrayList<>();
    for (String clientSlice : testSetting.clientSlices) {
      Thread t = new Thread() {
        @Override
        public void run() {
          try {
            String clientGateWay = testSetting.clientIpMap.get(clientSlice).replace(".1/24", ".2");
            String sdxInterfaceIP = testSetting.clientIpMap.get(clientSlice).replace(".1/24", ".1/24");
            String gw = exogeniClients.get(clientSlice).processCmd(String.format("stitch CNode1 %s %s",
              clientGateWay, sdxInterfaceIP));
            exogeniClients.get(clientSlice).processCmd(String.format("route %s %s",
              testSetting.clientIpMap.get(clientSlice),
              gw));
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
  }

  public void stitchSdxSlices() {
    ArrayList<Thread> tlist = new ArrayList<>();
    for (Integer[] edge : testSetting.sdxNeighbor) {
      Thread t = new Thread() {
        @Override
        public void run() {
          try {
            int i = edge[0];
            int j = edge[1];
            String slice1 = testSetting.sdxSliceNames.get(i);
            String slice2 = testSetting.sdxSliceNames.get(j);
            //stitch my e1 to peer e0
            sdxManagerMap.get(slice1).adminCmd("stitch", new String[]{testSetting.sdxUrls.get
              (slice2), "e1", "e0"});
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
  }

  /*
  public void stitchSdxSlices() {
    ArrayList<Thread> tlist = new ArrayList<>();
    for (Integer[] edge : testSetting.sdxNeighbor) {
      int i = edge[0];
      int j = edge[1];
      String slice1 = testSetting.sdxSliceNames.get(i);
      String slice2 = testSetting.sdxSliceNames.get(j);
      sdxManagerMap.get(slice1).adminCmd("stitch", new String[]{testSetting.sdxUrls.get(slice2), "e1"});
    }
  }
  */

  private void advertiseSDRoutesAndPolicies() {
    for (String client : testSetting.clientSlices) {
      String clientIp = testSetting.clientIpMap.get(client);
      List<ImmutablePair<String, String>> pairRouteAcls = testSetting.clientRouteASTagAcls.getOrDefault
        (client, new ArrayList<>());
      for (ImmutablePair<String, String> pair : pairRouteAcls) {
        exogeniClients.get(client).processCmd(String.format("acl %s %s %s", clientIp, pair
          .getLeft(), pair.getRight()));
      }
      for (ImmutablePair<String, String> pair : pairRouteAcls) {
        exogeniClients.get(client).processCmd(String.format("bgp %s %s %s", clientIp, pair
          .getLeft(), pair.getRight()));
      }
      if (pairRouteAcls.size() > 0) {
        logger.debug("\n\n\n\n\n\n\n\n");
      }
      List<ImmutablePair<String, String>> pairPolicyAcls = testSetting.clientPolicyASTagAcls
        .getOrDefault(client, new ArrayList<>());
      for (ImmutablePair<String, String> pair : pairPolicyAcls) {
        exogeniClients.get(client).processCmd(String.format("policy %s %s %s", pair.getLeft(),
          clientIp, pair.getRight()));
      }
      if (pairPolicyAcls.size() > 0) {
        logger.debug("\n\n\n\n\n\n\n\n");
      }
    }
    logger.debug("SD routes made");
  }
}
