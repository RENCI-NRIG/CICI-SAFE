package exoplex.cnert2019;
import exoplex.client.exogeni.SdxExogeniClient;
import exoplex.demo.SdxTest;
import exoplex.demo.tridentcom.Cnert2019Setting;
import exoplex.demo.tridentcom.Cnert2019Slice;
import exoplex.sdx.core.SdxManager;
import exoplex.sdx.core.SdxServer;
import junit.framework.Assert;
import org.junit.*;
import riak.RiakSlice;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import safe.AuthorityMock;

import java.util.HashMap;

public class Cnert2019Test {
  final static Logger logger = LogManager.getLogger(SdxTest.class);
  static String userDir = System.getProperty("user.dir");
  static String sdxSimpleDir = userDir.split("SDX-Simple")[0] + "SDX-Simple/";
  static String[] riakArgs = new String[]{"-c", sdxSimpleDir + "config/riak.conf"};
  static String[] riakDelArgs = new String[]{"-c", sdxSimpleDir + "config/riak.conf", "-d"};
  static SdxManager sdxManager;
  HashMap<String, SdxExogeniClient> exogeniClients = new HashMap<>();

  @BeforeClass
  public static void before() throws Exception{
    System.out.println("before test");
    after();
    //create RiakSlice
    RiakSlice riakSlice = new RiakSlice();
    String riakIP = riakSlice.run(riakArgs);
    //Sdx and client slices
    Cnert2019Slice.createSdxSlices(riakIP);

  }

  @AfterClass
  public static void after()throws Exception{
    RiakSlice riakSlice = new RiakSlice();
    String riakIP = riakSlice.run(riakDelArgs);
    Cnert2019Slice.deleteTestSlices();
    System.out.println("after");
  }

  @Test
  public void TestSDX() throws Exception{
    sdxManager = SdxServer.run(Cnert2019Setting.sdxArgs);
    for(String clientSlice: Cnert2019Setting.clientSlices){
      exogeniClients.put(clientSlice, new SdxExogeniClient(clientSlice,
        Cnert2019Setting.clientIpMap.get(clientSlice),
        Cnert2019Setting.clientKeyMap.get(clientSlice),
        Cnert2019Setting.clientArgs
      ));
    }
    for(SdxExogeniClient client: exogeniClients.values()){
      client.setSafeServer(sdxManager.getSafeServerIP());
    }
    stitchSlices();
    connectCustomerNetwork();
    unStitchSlices();
    stitchSlices();
    connectCustomerNetwork();
    unStitchSlices();
  }

  private  void stitchSlices(){
    for(String clientSlice: Cnert2019Setting.clientSlices){
      if(sdxManager.safeEnabled) {
        AuthorityMock.main(new String[]{Cnert2019Setting.clientKeyMap.get(clientSlice),
          clientSlice,
          Cnert2019Setting.clientIpMap.get(clientSlice),
          sdxManager.getSafeServer().split(":")[0]});
      }
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
    for(int i=0; i<Cnert2019Setting.clientSlices.size(); i++){
      String client = Cnert2019Setting.clientSlices.get(i);
      String clientIp = Cnert2019Setting.clientIpMap.get(client);
      for(int j = i + 1; j<Cnert2019Setting.clientSlices.size(); j++){
        String peer = Cnert2019Setting.clientSlices.get(j);
        String peerIp = Cnert2019Setting.clientIpMap.get(peer);
        exogeniClients.get(client).processCmd(String.format("link %s %s",
          Cnert2019Setting.clientIpMap.get(client),
          Cnert2019Setting.clientIpMap.get(peer)));

        if(!exogeniClients.get(client).checkConnectivity("CNode1",
          peerIp.replace(".1/24", ".2"))){
          sdxManager.checkFlowTableForPair(clientIp.replace(".1/24", ".0/24"),
            peerIp.replace(".1/24", ".0/24"),
            clientIp, peerIp);
          assert false;
        }
      }
    }
  }
}
