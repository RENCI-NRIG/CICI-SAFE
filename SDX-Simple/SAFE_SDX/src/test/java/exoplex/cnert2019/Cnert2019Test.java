package exoplex.cnert2019;
import exoplex.client.exogeni.SdxExogeniClient;
import exoplex.demo.SdxTest;
import exoplex.demo.cnert2019.Cnert2019Slice;
import exoplex.demo.cnert2019.Cnert2019Setting;
import exoplex.sdx.core.SdxManager;
import exoplex.sdx.core.SdxServer;
import org.junit.*;
import riak.RiakSlice;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import safe.AuthorityMock;

import java.util.ArrayList;
import java.util.HashMap;

public class Cnert2019Test {
  final static Logger logger = LogManager.getLogger(SdxTest.class);
  static String userDir = System.getProperty("user.dir");
  static String sdxSimpleDir = userDir.split("SDX-Simple")[0] + "SDX-Simple/";
  static String[] riakArgs = new String[]{"-c", sdxSimpleDir + "config/riak.conf"};
  static String[] riakDelArgs = new String[]{"-c", sdxSimpleDir + "config/riak.conf", "-d"};
  static HashMap<String, SdxManager>  sdxManagerMap = new HashMap<>();
  HashMap<String, SdxExogeniClient> exogeniClients = new HashMap<>();
  static Cnert2019Slice cnert2019Slice = new Cnert2019Slice();

  @BeforeClass
  public static void before() throws Exception{
    System.out.println("before test");
    after();
    //create RiakSlice
    RiakSlice riakSlice = new RiakSlice();
    String riakIP = riakSlice.run(riakArgs);
    //Sdx and client slices
    cnert2019Slice.createSdxSlices(riakIP);
    cnert2019Slice.createClientSlices();

  }

  @AfterClass
  public static void after()throws Exception{
    RiakSlice riakSlice = new RiakSlice();
    riakSlice.run(riakDelArgs);
    cnert2019Slice.deleteSdxSlices();
    cnert2019Slice.deleteSdxSlices();
  }

  @Test
  public void TestSDX() throws Exception{
    ArrayList<Thread> tlist = new ArrayList<>();
    for (String slice : Cnert2019Setting.sdxSliceNames) {
      Thread t = new Thread() {
        @Override
        public void run() {
          try {
            SdxManager sdxManager = SdxServer.run(Cnert2019Setting.sdxArgs.get(slice), Cnert2019Setting.sdxUrls.get
                            (slice), slice);
            sdxManagerMap.put(slice, sdxManager);
          }catch (Exception e){

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

    for(String clientSlice: Cnert2019Setting.clientSlices){
      exogeniClients.put(clientSlice, new SdxExogeniClient(clientSlice,
        Cnert2019Setting.clientIpMap.get(clientSlice),
        Cnert2019Setting.clientKeyMap.get(clientSlice),
        Cnert2019Setting.clientArgs
      ));
    }
    for(String clientSlice: Cnert2019Setting.clientSlices){
      SdxExogeniClient client = exogeniClients.get(clientSlice);
      client.setSafeServer(sdxManagerMap.values().iterator().next().getSafeServerIP());
      client.setServerUrl(Cnert2019Setting.sdxUrls.get(Cnert2019Setting.clientSdxMap.get(clientSlice)));
    }
    //stitch sdx slices

    stitchSdxSlices();

    stitchCustomerSlices();

    connectCustomerNetwork();
  }

  private void stitchSdxSlices(){
      for(Integer[] edge: Cnert2019Setting.sdxNeighbor){
        int i = edge[0];
        int j = edge[1];
        String slice1 = Cnert2019Setting.sdxSliceNames.get(0);
        String slice2 = Cnert2019Setting.sdxSliceNames.get(1);
        sdxManagerMap.get(slice1).adminCmd("stitch", new String[]{Cnert2019Setting.sdxUrls.get(slice2), "e1"});
      }
  }


  private  void stitchCustomerSlices(){
    for(String clientSlice: Cnert2019Setting.clientSlices){
      String clientGateWay = Cnert2019Setting.clientIpMap.get(clientSlice).replace(".1/24", ".2");
      String sdxIP = Cnert2019Setting.clientIpMap.get(clientSlice).replace(".1/24", ".1/24");
      String gw = exogeniClients.get(clientSlice).processCmd(String.format("stitch CNode1 %s %s",
        clientGateWay, sdxIP));
      exogeniClients.get(clientSlice).processCmd(String.format("route %s %s",
        Cnert2019Setting.clientIpMap.get(clientSlice),
        gw));
    }
  }

  private  void unStitchSlices(){
    for(String clientSlice: Cnert2019Setting.clientSlices){
      exogeniClients.get(clientSlice).processCmd("unstitch CNode1");
    }
  }

  private void connectCustomerNetwork(){
    for(Integer[] pair : Cnert2019Setting.customerConnectionPairs){
      int i = pair[0];
      int j = pair[1];
      String client = Cnert2019Setting.clientSlices.get(i);
      String clientIp = Cnert2019Setting.clientIpMap.get(client);
      String peer = Cnert2019Setting.clientSlices.get(j);
      String peerIp = Cnert2019Setting.clientIpMap.get(peer);
      exogeniClients.get(client).processCmd(String.format("link %s %s", clientIp, peerIp));
      if(!exogeniClients.get(client).checkConnectivity("CNode1",
              peerIp.replace(".1/24", ".2"))){
      }
    }
  }
}
