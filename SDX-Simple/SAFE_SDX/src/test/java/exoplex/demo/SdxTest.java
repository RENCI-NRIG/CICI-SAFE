package exoplex.demo;


import exoplex.client.exogeni.SdxExogeniClient;
import exoplex.demo.tridentcom.TridentSetting;
import exoplex.demo.tridentcom.TridentSlice;
import exoplex.sdx.core.SdxServer;
import junit.framework.Assert;
import org.junit.*;
import riak.RiakSlice;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import safe.AuthorityMock;

import java.util.HashMap;

@Ignore
public class SdxTest {
  final static Logger logger = LogManager.getLogger(SdxTest.class);
  static String userDir = System.getProperty("user.dir");
  static String sdxSimpleDir = userDir.split("SDX-Simple")[0] + "SDX-Simple/";
  static String[] riakArgs = new String[]{"-c", sdxSimpleDir + "config/riak.conf"};
  static String[] riakDelArgs = new String[]{"-c", sdxSimpleDir + "config/riak.conf", "-d"};
  HashMap<String, SdxExogeniClient> exogeniClients = new HashMap<>();

  @BeforeClass
  public static void before() throws Exception{
    System.out.println("before test");
    //create RiakSlice
    RiakSlice riakSlice = new RiakSlice();
    String riakIP = riakSlice.run(riakArgs);
    TridentSlice.createSlices(riakIP);
  }

  @AfterClass
  public static void after()throws Exception{
    RiakSlice riakSlice = new RiakSlice();
    String riakIP = riakSlice.run(riakDelArgs);
    TridentSlice.deleteTestSlices();
    System.out.println("after");
  }

  @Test
  public void TestSDX() throws Exception{
    SdxServer.run(TridentSetting.sdxArgs);
    for(String clientSlice: TridentSetting.clientSlices){
      exogeniClients.put(clientSlice, new SdxExogeniClient(clientSlice,
        TridentSetting.clientIpMap.get(clientSlice),
        TridentSetting.clientKeyMap.get(clientSlice),
        TridentSetting.clientArgs
      ));
    }
    for(SdxExogeniClient client: exogeniClients.values()){
      client.setSafeServer(SdxServer.sdxManager.getSafeServerIP());
    }
    stitchSlices();
    connectCustomerNetwork();
    unStitchSlices();
    stitchSlices();
    connectCustomerNetwork();
    unStitchSlices();
  }

  private  void stitchSlices(){
    for(String clientSlice: TridentSetting.clientSlices){
      AuthorityMock.main(new String[]{TridentSetting.clientKeyMap.get(clientSlice),
        clientSlice,
        TridentSetting.clientIpMap.get(clientSlice),
        SdxServer.sdxManager.getSafeServer().split(":")[0]});

      String gw = exogeniClients.get(clientSlice).processCmd("stitch CNode0");
      exogeniClients.get(clientSlice).processCmd(String.format("route %s %s",
        TridentSetting.clientIpMap.get(clientSlice),
        gw));
    }
  }

  private  void unStitchSlices(){
    for(String clientSlice: TridentSetting.clientSlices){
      exogeniClients.get(clientSlice).processCmd("unstitch CNode0");
    }
  }

  private void connectCustomerNetwork(){
    for(int i=0; i<TridentSetting.clientSlices.size(); i++){
      String client = TridentSetting.clientSlices.get(i);
      String clientIp = TridentSetting.clientIpMap.get(client);
      for(int j = i + 1; j<TridentSetting.clientSlices.size(); j++){
        String peer = TridentSetting.clientSlices.get(j);
        String peerIp = TridentSetting.clientIpMap.get(peer);
        exogeniClients.get(client).processCmd(String.format("link %s %s",
          TridentSetting.clientIpMap.get(client),
          TridentSetting.clientIpMap.get(peer)));

        if(!exogeniClients.get(client).checkConnectivity("CNode1",
          peerIp.replace(".1/24", ".2"))){
          SdxServer.sdxManager.checkFlowTableForPair(clientIp.replace(".1/24", ".0/24"),
            peerIp.replace(".1/24", ".0/24"),
            clientIp, peerIp);
          assert false;
        }
      }
    }
  }
}
