package exoplex.demo.multisdxsd;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import exoplex.demo.AbstractTest;
import exoplex.demo.AbstractTestSetting;
import exoplex.demo.AbstractTestSlice;
import exoplex.experiment.ExperimentBase;
import exoplex.sdx.core.CoreProperties;
import exoplex.sdx.core.SdxManagerBase;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import safe.Authority;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MultiSdxTestSD extends AbstractTest {
  final static Logger logger = LogManager.getLogger(MultiSdxTestSD.class);
  final static AbstractModule module = new MultiSdxCnertModule();
  //final static AbstractModule module = new MultiSdxTridentcomModule();
  //final static AbstractModule module = new MultiSdxSDLargeModule();
  //final static AbstractModule module = new MultiSdxSDModule();
  //final static AbstractModule module = new MultiSdxSDMockModule();

  public static void main(String[] args) {
    MultiSdxTestSD multiSdxTestSD = new MultiSdxTestSD();
    Injector injector = Guice.createInjector(module);
    multiSdxTestSD.reset = true;
    multiSdxTestSD.injector = injector;
    multiSdxTestSD.testSlice = injector.getInstance(AbstractTestSlice.class);
    multiSdxTestSD.testSetting = injector.getInstance(AbstractTestSetting.class);
    CoreProperties.setSafeDockerImage(multiSdxTestSD.testSetting.dockerImage);
    try {
      multiSdxTestSD.testMultiSdxSD();
      //multiSdxTestSD.replaySdnConfiguration("/home/yaoyj11/CICI-SAFE/exoplex/log/sdn.log");
      //multiSdxTestSD.measureBandwidth();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Override
  public void initTests() {
    injector = Guice.createInjector(module);
    testSlice = injector.getInstance(AbstractTestSlice.class);
    testSetting = injector.getInstance(AbstractTestSetting.class);
    CoreProperties.setRouteAdvertise(true);
  }

  @Before
  @Override
  public void before() throws Exception {
    deleteSliceAfterTest = true;
    initTests();
    Authority.authorizationMade = true;
    //deleteSlices();
    //super.before();
  }

  @After
  @Override
  public void after() throws Exception {
    //super.after();
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

    logger.info("Start advertiseing routes and policies");
    advertiseSDRoutesAndPolicies();
    //wait for route to be stablized
    try{
      Thread.sleep(60000);
    }catch (Exception e){

    }

    //connectCustomerNetwork();
    Long t3 = System.currentTimeMillis();


    logger.info("Start checking connections");
    checkConnection(3);
    Long t4 = System.currentTimeMillis();
    //traceRoute();
    Long t5 = System.currentTimeMillis();
    logFlowTables(false);

    logger.info("test done");
    logger.info(String.format("Time\n stitch sdx: %s s\n stitch customers: %s s\n check " +
      "connection: %s s", (t1 - t0) / 1000.0, (t2 - t1) / 1000.0, (t4 - t3) / 1000.0));
  }

  @Test
  /**
   * Cnert 2019 demo
   */
  public void testMultiSdxWithEvents() {
    startSdxServersAndClients(reset);
    //stitch sdx slices
    Long t0 = System.currentTimeMillis();

    stitchSdxSlices();
    Long t1 = System.currentTimeMillis();

    stitchCustomerSlices();
    Long t2 = System.currentTimeMillis();

    try{
      Thread.sleep(10000);
    }catch (Exception e){}
    logger.info("Start advertising routes and policies");
    advertiseSDRoutesAndPolicies();
    try{
      Thread.sleep(60000);
    }catch (Exception e){}
    //wait for route to be stablized
    try{
      //Thread.sleep(60000);
    }catch (Exception e){

    }
    //connectCustomerNetwork();
    Long t3 = System.currentTimeMillis();

    logger.info("Start checking connections");
    checkConnection(3);
    Long t4 = System.currentTimeMillis();
    //traceRoute();
    Long t5 = System.currentTimeMillis();
    logFlowTables(false);

    //connect Client 4
    ((MultiSdxCnertSetting)testSetting).joinLastClient();
    startClient(testSetting.clientSlices.get(3));
    SdxManagerBase exoSdxManager = sdxManagerMap.values().iterator().next();
    String safeServerIp = getSafeServerIPfromSdxManager(exoSdxManager);
    exogeniClients.get(testSetting.clientSlices.get(3)).setSafeServer(safeServerIp);
    stitchCustomerSlice(testSetting.clientSlices.get(3));
    //Advertise routes and policies SD for client 4
    advertise(testSetting.clientSlices.get(3));
    try{
      Thread.sleep(60000);
    }catch (Exception e){

    }
    checkConnection(3);
    logFlowTables(false);

    //connect SDX2 and NSP2
    try {
      String slice1 = testSetting.sdxSliceNames.get(2);
      String slice2 = testSetting.sdxSliceNames.get(5);
      //stitch my e1 to peer e0
      sdxManagerMap.get(slice1).adminCmd("stitch", new String[]{testSetting.sdxUrls.get
        (slice2), "e1", "e0"});
    } catch (Exception e) {
      e.printStackTrace();
    }
    try{
      Thread.sleep(6000000);
    }catch (Exception e){

    }
    checkConnection(3);
    logFlowTables(false);

    logger.info("test done");
    logger.info(String.format("Time\n stitch sdx: %s s\n stitch customers: %s s\n check " +
      "connection: %s s", (t1 - t0) / 1000.0, (t2 - t1) / 1000.0, (t4 - t3) / 1000.0));
  }

  @Override
  public void startSdxServersAndClients(boolean reset) {
    super.startSdxServersAndClients(reset);
    SdxManagerBase exoSdxManager = sdxManagerMap.values().iterator().next();
    String safeServerIp = getSafeServerIPfromSdxManager(exoSdxManager);
    setClientSafeServerIp(safeServerIp);
  }

  private void stitchCustomerSlice(String clientSlice) {
    try {
      String clientGateWay = testSetting.clientIpMap.get(clientSlice).replace(".1/24", ".2");
      String sdxInterfaceIP = testSetting.clientIpMap.get(clientSlice).replace(".1/24", ".1/24");
      String gw;
      if(testSetting.clientSdxNode.containsKey(clientSlice)) {
        gw = exogeniClients.get(clientSlice).processCmd(String.format("stitch CNode1 %s %s " +
          "%s", clientGateWay, sdxInterfaceIP, testSetting.clientSdxNode.get(clientSlice)));
      }else{
        gw = exogeniClients.get(clientSlice).processCmd(String.format("stitch CNode1 %s %s",
          clientGateWay, sdxInterfaceIP));
      }
      exogeniClients.get(clientSlice).processCmd(String.format("route %s %s",
        testSetting.clientIpMap.get(clientSlice),
        gw));
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Override
  public void stitchCustomerSlices() {
    ArrayList<Thread> tlist = new ArrayList<>();
    for (String clientSlice : testSetting.clientSlices) {
      Thread t = new Thread() {
        @Override
        public void run() {
          stitchCustomerSlice(clientSlice);
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

  public void measureBandwidth(){
    startClients();
    ExperimentBase experiment = new ExperimentBase("~/.ssh/id_rsa");
    int port = 5001;
    HashMap<String, Integer> portMap = new HashMap<>();
    for (Integer[] pair : testSetting.clientConnectionPairs) {
      int i = pair[0];
      int j = pair[1];
      String client = testSetting.clientSlices.get(i);
      String clientIp = testSetting.clientIpMap.get(client);
      String clientMIp = exogeniClients.get(client).getManagementIP("CNode1");
      String peer = testSetting.clientSlices.get(j);
      String peerMIp = exogeniClients.get(peer).getManagementIP("CNode1");
      String peerIp = testSetting.clientIpMap.get(peer).replace(".1/24",".2");
      experiment.addClient(client, clientMIp, clientIp);
      experiment.addClient(peer, peerMIp, peerIp);
      int portN = portMap.getOrDefault(peer, port);
      portMap.put(peer, portN);
      experiment.addTcpFlow(client, peer, "0", 10, portN);
      port += 1;
    }
    experiment.startFlows(100);
    experiment.sleep(120);
    experiment.stopFlows();
    experiment.printFlowServerResult();
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

  private void advertise(String client) {
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
      logger.debug("\n\n");
    }
    List<ImmutablePair<String, String>> pairPolicyAcls = testSetting.clientPolicyASTagAcls
      .getOrDefault(client, new ArrayList<>());
    for (ImmutablePair<String, String> pair : pairPolicyAcls) {
      exogeniClients.get(client).processCmd(String.format("policy %s %s %s", pair.getLeft(),
        clientIp, pair.getRight()));
    }
    if (pairPolicyAcls.size() > 0) {
      logger.debug("\n\n");
    }
  }

  private void advertiseSDRoutesAndPolicies() {
    for (String client : testSetting.clientSlices) {
      advertise(client);
    }
    logger.debug("SD routes made");
    deleteSliceAfterTest = true;
  }
}
