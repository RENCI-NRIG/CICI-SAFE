package exoplex.demo.multisdx;
import exoplex.client.exogeni.SdxExogeniClient;
import exoplex.demo.SdxTest;
import exoplex.sdx.core.SdxManager;
import exoplex.sdx.core.SdxServer;
import exoplex.sdx.network.SdnReplay;
import org.apache.commons.io.filefilter.FalseFileFilter;
import org.junit.*;
import riak.RiakSlice;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import safe.sdx.AuthorityMockSdx;

import javax.xml.stream.FactoryConfigurationError;
import java.util.ArrayList;
import java.util.HashMap;

public class MultiSdxTest {
  final static Logger logger = LogManager.getLogger(SdxTest.class);
  static String userDir = System.getProperty("user.dir");
  static String sdxSimpleDir = userDir.split("SDX-Simple")[0] + "SDX-Simple/";
  static String[] riakArgs = new String[]{"-c", sdxSimpleDir + "config/riak.conf"};
  static String[] riakDelArgs = new String[]{"-c", sdxSimpleDir + "config/riak.conf", "-d"};
  static HashMap<String, SdxManager>  sdxManagerMap = new HashMap<>();
  HashMap<String, SdxExogeniClient> exogeniClients = new HashMap<>();
  static MultiSdxSlice multiSdxSlice = new MultiSdxSlice();
  static boolean deleteSlice = true;

  @BeforeClass
  public static void before() throws Exception {
    System.out.println("before test");
    after();
    //create RiakSlice
    RiakSlice riakSlice = new RiakSlice();
    String riakIP = riakSlice.run(riakArgs);
    //Sdx and client slices
    multiSdxSlice.createSdxSlices(riakIP);
    multiSdxSlice.createClientSlices();
  }

  @AfterClass
  public static void after()throws Exception{
    if(deleteSlice) {
      RiakSlice riakSlice = new RiakSlice();
      riakSlice.run(riakDelArgs);
      multiSdxSlice.deleteSdxSlices();
      multiSdxSlice.deleteClientSlices();
    }
    Thread.sleep(200000);
  }

  public static void main(String[] args){
    MultiSdxTest multiSdxTest = new MultiSdxTest();
    try {
      multiSdxTest.testMultiSdx();
      //multiSdxTest.replaySdnConfiguration();
      logFlowTables();
    }catch (Exception e){
      e.printStackTrace();
    }
  }

  public static void logFlowTables(){
    for(SdxManager sdxManager: sdxManagerMap.values()){
      sdxManager.logFlowTables();
    }
  }

  public void replaySdnConfiguration(){
    AuthorityMockSdx.authorizationMade = true;
    ArrayList<Thread> tlist = new ArrayList<>();
    for (String slice : MultiSdxSetting.sdxSliceNames) {
      Thread t = new Thread() {
        @Override
        public void run() {
          try {
            SdxManager sdxManager = SdxServer.run(MultiSdxSetting.sdxNoResetArgs.get(slice),
              MultiSdxSetting.sdxUrls.get
              (slice), slice);
            sdxManager.delFlows();
            sdxManager.restartPlexus();
            Thread.sleep(5000);
            sdxManager.waitTillAllOvsConnected();
            sdxManagerMap.put(slice, sdxManager);
          }catch (Exception e){
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
      e.printStackTrace();
    }

    for(String clientSlice: MultiSdxSetting.clientSlices){
      exogeniClients.put(clientSlice, new SdxExogeniClient(clientSlice,
        MultiSdxSetting.clientIpMap.get(clientSlice),
        MultiSdxSetting.clientKeyMap.get(clientSlice),
        MultiSdxSetting.clientArgs
      ));
    }
    for(String clientSlice: MultiSdxSetting.clientSlices){
      SdxExogeniClient client = exogeniClients.get(clientSlice);
      client.setSafeServer(sdxManagerMap.values().iterator().next().getSafeServerIP());
      client.setServerUrl(MultiSdxSetting.sdxUrls.get(MultiSdxSetting.clientSdxMap.get(clientSlice)));
    }
    SdnReplay.replay("/home/yaoyj11/CICI-SAFE/SDX-Simple/log/sdn.log");
    checkConnection();
    logger.info("replay done");

  }

  @Test
  public void testMultiSdx() throws Exception{
    ArrayList<Thread> tlist = new ArrayList<>();
    for (String slice : MultiSdxSetting.sdxSliceNames) {
      Thread t = new Thread() {
        @Override
        public void run() {
          try {
            SdxManager sdxManager = SdxServer.run(MultiSdxSetting.sdxArgs.get(slice), MultiSdxSetting.sdxUrls.get
                            (slice), slice);
            sdxManagerMap.put(slice, sdxManager);
          }catch (Exception e){
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

    for(String clientSlice: MultiSdxSetting.clientSlices){
      exogeniClients.put(clientSlice, new SdxExogeniClient(clientSlice,
        MultiSdxSetting.clientIpMap.get(clientSlice),
        MultiSdxSetting.clientKeyMap.get(clientSlice),
        MultiSdxSetting.clientArgs
      ));
    }
    for(String clientSlice: MultiSdxSetting.clientSlices){
      SdxExogeniClient client = exogeniClients.get(clientSlice);
      client.setSafeServer(sdxManagerMap.values().iterator().next().getSafeServerIP());
      client.setServerUrl(MultiSdxSetting.sdxUrls.get(MultiSdxSetting.clientSdxMap.get(clientSlice)));
    }
    //stitch sdx slices
    Long t0 = System.currentTimeMillis();

    stitchSdxSlices();
    Long t1 = System.currentTimeMillis();

    stitchCustomerSlices();
    Long t2 = System.currentTimeMillis();

    connectCustomerNetwork();
    Long t3= System.currentTimeMillis();

    checkConnection();
    Long t4 = System.currentTimeMillis();

    logger.info("test done");
    logger.info(String.format("Time\n stitch sdx: %s s\n stitch customers: %s s\n connection: %s s\n check " +
      "connection: %s s", (t1 - t0)/1000.0, (t2 - t1)/1000.0, (t3 - t2)/1000.0, (t4-t3)/1000.0));
  }

  private void stitchSdxSlices(){
      for(Integer[] edge: MultiSdxSetting.sdxNeighbor){
        int i = edge[0];
        int j = edge[1];
        String slice1 = MultiSdxSetting.sdxSliceNames.get(i);
        String slice2 = MultiSdxSetting.sdxSliceNames.get(j);
        sdxManagerMap.get(slice1).adminCmd("stitch", new String[]{MultiSdxSetting.sdxUrls.get(slice2), "e1"});
      }
  }


  private  void stitchCustomerSlices(){
    for(String clientSlice: MultiSdxSetting.clientSlices){
      String clientGateWay = MultiSdxSetting.clientIpMap.get(clientSlice).replace(".1/24", ".2");
      String sdxIP = MultiSdxSetting.clientIpMap.get(clientSlice).replace(".1/24", ".1/24");
      String gw = exogeniClients.get(clientSlice).processCmd(String.format("stitch CNode1 %s %s",
        clientGateWay, sdxIP));
      exogeniClients.get(clientSlice).processCmd(String.format("route %s %s",
        MultiSdxSetting.clientIpMap.get(clientSlice),
        gw));
    }
  }

  private  void unStitchSlices(){
    for(String clientSlice: MultiSdxSetting.clientSlices){
      exogeniClients.get(clientSlice).processCmd("unstitch CNode1");
    }
  }

  private void connectCustomerNetwork(){
    for(Integer[] pair : MultiSdxSetting.customerConnectionPairs){
      int i = pair[0];
      int j = pair[1];
      String client = MultiSdxSetting.clientSlices.get(i);
      String clientIp = MultiSdxSetting.clientIpMap.get(client);
      String peer = MultiSdxSetting.clientSlices.get(j);
      String peerIp = MultiSdxSetting.clientIpMap.get(peer);
      exogeniClients.get(client).processCmd(String.format("link %s %s", clientIp, peerIp));
      exogeniClients.get(peer).processCmd(String.format("link %s %s", peerIp, clientIp));
    }
    logger.debug("connection ends");
  }

  private void checkConnection(){
    boolean flag = true;
    for(Integer[] pair : MultiSdxSetting.customerConnectionPairs){
      int i = pair[0];
      int j = pair[1];
      String client = MultiSdxSetting.clientSlices.get(i);
      String clientIp = MultiSdxSetting.clientIpMap.get(client);
      String peer = MultiSdxSetting.clientSlices.get(j);
      String peerIp = MultiSdxSetting.clientIpMap.get(peer);
      if(!exogeniClients.get(client).checkConnectivity("CNode1",
        peerIp.replace(".1/24", ".2"))){
        flag = false;
        deleteSlice = false;
      }else{
        System.out.println(exogeniClients.get(client).traceRoute("CNode1",
          peerIp.replace(".1/24", ".2")));
      }
    }
    deleteSlice = false;

  }
}
