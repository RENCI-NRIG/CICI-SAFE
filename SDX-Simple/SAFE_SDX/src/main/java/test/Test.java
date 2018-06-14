package test;

import common.utils.Exec;
import common.utils.HttpUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.json.JSONObject;
import org.renci.ahab.libtransport.util.TransportException;
import sdx.core.SdxManager;

import java.io.IOException;
import java.net.URI;

public class Test {
  final Logger logger = LogManager.getLogger(Exec.class);
  SdxManager sdxManager = new SdxManager();

  private static String[] queueCMD(String controller, String dpid) {
    String[] res = new String[2];
    res[0] = "http://" + controller + ":8080/qos/queue/" + dpid;
    res[1] = "{\"type\":\"linux-htb\",\"max_rate\":\"1000000000\"," +
        "\"queues\":[{\"max_rate\":\"20000000\"},{\"min_rate\":\"500000\"}]}";
    return res;
  }

  private static String[] qosCMD(String controller, String dpid) {
    String[] res = new String[2];
    res[0] = "http://" + controller + ":8080/qos/rules/" + dpid;
    res[1] = "{\"match\":{\"nw_dst\":\"192.168.20.2\",\"nw_proto\":\"TCP\",\"tp_dst\":\"5002\"}," +
        "\"actions\":{\"queue\":\"0\"}}";
    return res;
  }

  public HttpServer startServer(String url) {
    // create a resource config that scans for JAX-RS resources and providers
    // in com.example package
    final ResourceConfig rc = new ResourceConfig().packages("sdx.core");
    // create and start a new instance of grizzly http server
    // exposing the Jersey application at BASE_URI
    return GrizzlyHttpServerFactory.createHttpServer(URI.create(url), rc);
  }

  public void run(String[] args) throws Exception {
    testRoutingDynamicLink(args);
    //testRoutingChameleon(args);
    //testPerFlowQOS();
    //limitQos();
  }

  private void testRoutingDynamicLink(String[] args) throws Exception {
    sdxManager.startSdxServer(args);
    System.out.println("configured ip addresses in sdx network");
    //notify prefixes for node0 and node1
    sdxManager.notifyPrefix("192.168.10.2/24", "192.168.10.2", "notused");
    sdxManager.notifyPrefix("192.168.20.2/24", "192.168.20.2", "notused");
    sdxManager.connectionRequest("not used", "192.168.20.2/24", "192.168.10.2/24", 500000000);
    sdxManager.notifyPrefix("192.168.30.2/24", "192.168.30.2", "notused");
    sdxManager.connectionRequest("not used", "192.168.20.2/24", "192.168.30.2/24", 500000000);
    sdxManager.connectionRequest("not used", "192.168.30.2/24", "192.168.10.2/24", 500000000);
  }

  private void testRouting(String[] args) throws Exception {
    sdxManager.startSdxServer(args);
    System.out.println("configured ip addresses in sdx network");
    //notify prefixes for node0 and node1
    sdxManager.notifyPrefix("192.168.10.2/24", "192.168.10.2", "notused");
    //sdxManager.notifyPrefix("192.168.30.2/24", "192.168.10.2", "c0", "notused");
    sdxManager.notifyPrefix("192.168.20.2/24", "192.168.20.2", "notused");
    String dpid = sdxManager.getDPID("c0");
    String res = sdxManager.setMirror(dpid, "192.168.20.1/24",
        "192.168.10.2/24");
    String res1 = sdxManager.setMirror(dpid, "192.168.10.2/24",
        "192.168.20.1/24");
    System.out.println(res);

    System.out.println("IP prefix is set up, the two nodes should be able to talk now");
    String dp1 = sdxManager.getDPID("c0");
    String dp2 = sdxManager.getDPID("c1");
    while (true) {
      System.out.println("Press enter to reset");
      try {
        System.in.read();
      } catch (IOException e) {
        e.printStackTrace();
      }
      sdxManager.restartPlexus();
      sdxManager.waitTillAllOvsConnected();
      sdxManager.delFlows();
      System.out.println("all routers connected");
      System.out.println("IP prefix is set up, the two nodes should be able to talk now");
    }
    //sdxManager.printSlice();
    /*
    try {
      System.out.println("Now Set up vsftp in node");
      System.in.read();
    }catch (IOException e){
      e.printStackTrace();
    }
    */
  }

  /*
  private void testRoutingTwoPair(String[] args) {
    //notify 10 and 30 first
    //mirror traffic to 101.2

    //notify20 and 40
    //add another bro node
    //set up routing
    //start sending traffci
    //compare when we mirror all traffic to the same bro node, and when we mirror traffic to
    // different node, and get the cpu utilization overtime.
    sdxManager.startSdxServer(args);
    System.out.println("configured ip addresses in sdx network");
    //notify prefixes for node0 and node1
    sdxManager.notifyPrefix("192.168.10.2/24", "192.168.10.2", "c0", "notused");
    //sdxManager.notifyPrefix("192.168.30.2/24", "192.168.10.2", "c0", "notused");
    sdxManager.notifyPrefix("192.168.20.2/24", "192.168.20.2", "c0", "notused");
    sdxManager.notifyPrefix("192.168.30.2/24", "192.168.30.2", "c1", "notused");
    sdxManager.notifyPrefix("192.168.40.2/24", "192.168.40.2", "c1", "notused");
    String[] addresses = new String[4];
    addresses[0] = "192.168.10.1/24";
    addresses[1] = "192.168.20.1/24";
    addresses[2] = "192.168.30.1/24";
    addresses[3] = "192.168.40.1/24";

    String dpid0 = sdxManager.getDPID("c0");
    String dpid1 = sdxManager.getDPID("c1");
    for (int i = 0; i < 4; i++) {
      for (int j = 0; j < 4; j++) {
        if(i != j){
          if(i == 0 || j == 0) {
            String res = sdxManager.setMirror(sdxManager.getSDNControllerIP(), dpid0, addresses[i],
              addresses[j], "192.168.101.2");
            //String res1 = sdxManager.setMirror(sdxManager.getSDNControllerIP(), dpid, "192.168.20.1/24",
            //  "192.168.10.1/24", "192.168.101.2");
            System.out.println(res);
          }else if( i == 1 || j == 1){
            String res = sdxManager.setMirror(sdxManager.getSDNControllerIP(), dpid0, addresses[i],
              addresses[j], "192.168.101.2");
            //String res1 = sdxManager.setMirror(sdxManager.getSDNControllerIP(), dpid, "192.168.20.1/24",
            //  "192.168.10.1/24", "192.168.101.2");
            System.out.println(res);
          }
        }
      }
    }
    System.out.println(Long.parseLong(dpid0,16));
    System.out.println(Long.parseLong(dpid1,16));
    System.out.println("IP prefix is set up, the two nodes should be able to talk now");
    System.out.println(sdxManager.setMirror(sdxManager.getSDNControllerIP(), dpid0, addresses[0],
      addresses[2], "192.168.103.2"));
    System.out.println(sdxManager.setMirror(sdxManager.getSDNControllerIP(), dpid0, addresses[2],
      addresses[0], "192.168.103.2"));

    while (true) {
      System.out.println("Press enter to reset");
      try {
        System.in.read();
      } catch (IOException e) {
        e.printStackTrace();
      }
      sdxManager.clear();
      sdxManager.delFlows();
      sdxManager.restartPlexus();
      sdxManager.waitTillAllOvsConnected();
      sdxManager.replayCMD(dpid0);
      sdxManager.replayCMD(dpid1);
      sdxManager.notifyPrefix("192.168.10.2/24", "192.168.10.2", "c0", "notused");
      //sdxManager.notifyPrefix("192.168.30.2/24", "192.168.10.2", "c0", "notused");
      sdxManager.notifyPrefix("192.168.20.2/24", "192.168.20.2", "c0", "notused");
      sdxManager.notifyPrefix("192.168.30.2/24", "192.168.30.2", "c1", "notused");
      sdxManager.notifyPrefix("192.168.40.2/24", "192.168.40.2", "c1", "notused");
      ArrayList<String> mirror_ids = new ArrayList<String>();
      for (int i = 0; i < 4; i++) {
        for (int j = 0; j < 4; j++) {
          if(i != j){
            if(i == 0 || j == 0) {
              String res = sdxManager.setMirror(sdxManager.getSDNControllerIP(), dpid0, addresses[i],
                addresses[j], "192.168.101.2");
              //String res1 = sdxManager.setMirror(sdxManager.getSDNControllerIP(), dpid, "192.168.20.1/24",
              //  "192.168.10.1/24", "192.168.101.2");
              System.out.println(res);
            }else if( i == 1 || j == 1){
              String res = sdxManager.setMirror(sdxManager.getSDNControllerIP(), dpid0, addresses[i],
                addresses[j], "192.168.101.2");
              //String res1 = sdxManager.setMirror(sdxManager.getSDNControllerIP(), dpid, "192.168.20.1/24",
              //  "192.168.10.1/24", "192.168.101.2");
              System.out.println(res);
            }
          }
        }
      }
      System.out.println("IP prefix is set up, the two nodes should be able to talk now");
    }
    //for (int i = 0; i < 4; i++) {
    //  for (int j = 0; j < 4; j++) {
    //    if(i != j){
    //      String res = sdxManager.setMirror(sdxManager.getSDNControllerIP(), dpid, addresses[i],
    //        addresses[j], "192.168.101.2");
    //      //String res1 = sdxManager.setMirror(sdxManager.getSDNControllerIP(), dpid, "192.168.20.1/24",
    //      //  "192.168.10.1/24", "192.168.101.2");
    //      System.out.println(res);
    //    }
    //  }
    //}
  }
*/
  private void testRoutingTwoPair(String[] args) throws Exception {
    //notify 10 and 30 first
    //mirror traffic to 101.2

    //notify20 and 40
    //add another bro node
    //set up routing
    //start sending traffci
    //compare when we mirror all traffic to the same bro node, and when we mirror traffic to
    // different node, and get the cpu utilization overtime.
    String[] addresses = new String[4];
    addresses[0] = "192.168.10.1/24";
    addresses[1] = "192.168.20.1/24";
    addresses[2] = "192.168.30.1/24";
    addresses[3] = "192.168.40.1/24";
    sdxManager.startSdxServer(args);
    String dpid0 = sdxManager.getDPID("c0");
    String dpid1 = sdxManager.getDPID("c1");
    System.out.println("configured ip addresses in sdx network");
    //notify prefixes for node0 and node1
    sdxManager.notifyPrefix("192.168.10.2/24", "192.168.10.2", "notused");
    //sdxManager.notifyPrefix("192.168.30.2/24", "192.168.10.2", "c0", "notused");
    sdxManager.notifyPrefix("192.168.30.2/24", "192.168.30.2", "notused");
    String res = sdxManager.setMirror(dpid0, addresses[0],
        addresses[2]);
    System.out.println(res);
    res = sdxManager.setMirror(dpid0, addresses[2],
        addresses[0]);
    System.out.println(res);
    sdxManager.notifyPrefix("192.168.20.2/24", "192.168.20.2", "notused");
    sdxManager.notifyPrefix("192.168.40.2/24", "192.168.40.2", "notused");
    res = sdxManager.setMirror(dpid0, addresses[1],
        addresses[3]);
    System.out.println(res);
    res = sdxManager.setMirror(dpid0, addresses[3],
        addresses[1]);
    System.out.println(res);
  }

  private void testRoutingChameleon(String[] args) throws Exception {
    sdxManager.startSdxServer(args);
    System.out.println("configured ip addresses in sdx network");
    //notify prefixes for node0 and node1
    sdxManager.notifyPrefix("192.168.10.2/24", "192.168.10.2", "notused");
    //sdxManager.notifyPrefix("192.168.30.2/24", "192.168.10.2", "c0", "notused");
    sdxManager.notifyPrefix("10.32.90.105/24", "10.32.90.105", "notused");
    String dpid = sdxManager.getDPID("c0");
    String res = sdxManager.setMirror(dpid, "10.32.90.105/24",
        "192.168.10.2/24");
    String res1 = sdxManager.setMirror(dpid, "192.168.10.2/24",
        "10.32.90.105/24");
    System.out.println(res);

    System.out.println("IP prefix is set up, the two nodes should be able to talk now");
    String dp1 = sdxManager.getDPID("c0");
    String dp2 = sdxManager.getDPID("c1");
    System.out.println(Long.parseLong(dp1, 16));
    while (true) {
      System.out.println("Press enter to reset");
      try {
        System.in.read();
      } catch (IOException e) {
        e.printStackTrace();
      }
      sdxManager.restartPlexus();
      sdxManager.waitTillAllOvsConnected();
      sdxManager.delFlows();
      System.out.println("all routers connected");
    }
  }

  private void limitQos() {
    String dpid1 = sdxManager.getDPID("c0");
    String controller = sdxManager.getSDNControllerIP();
    String[] queuecmd = queueCMD(controller, dpid1);
    HttpUtil.postJSON(queuecmd[0], new JSONObject(queuecmd[1]));
    HttpUtil.get(queuecmd[0]);
    String[] qoscmd = qosCMD(controller, dpid1);
    HttpUtil.postJSON(qoscmd[0], new JSONObject(qoscmd[1]));
  }

  private void testPerFlowQOS() {
    String dpid1 = "00000628a4daa642";
    String dpid2 = "00004621982f9b41";
    String controller = "152.54.14.12";
    String[] queuecmd = queueCMD(controller, dpid1);
    HttpUtil.postJSON(queuecmd[0], new JSONObject(queuecmd[1]));
    HttpUtil.get(queuecmd[0]);
    queuecmd = queueCMD(controller, dpid2);
    HttpUtil.postJSON(queuecmd[0], new JSONObject(queuecmd[1]));
    HttpUtil.get(queuecmd[0]);
    //String getcmd="curl -X GET http://"+controller+":8080/qos/rules/"+dpid1;
    //String[] qoscmd="curl -X POST -d \'{\"match\":{\"nw_dst\":\"192.168.10.2\",\"nw_proto\":\"TCP\",\"tp_dst\":\"5002\"},\"actions\":{\"queue\":\"0\"}}\' http://"+controller+":8080/qos/rules/"+dpid1;
    String[] qoscmd = qosCMD(controller, dpid1);
    String res = HttpUtil.postJSON(qoscmd[0], new JSONObject(qoscmd[1]));
    System.out.println(res.toString());
    System.out.println(HttpUtil.get(qoscmd[0]));
    HttpUtil.postJSON(qoscmd[0], new JSONObject(qoscmd[1]));
    HttpUtil.get(qoscmd[0]);
    /*
    res=HttpUtil.postJSON(qoscmd[0], new JSONObject(qoscmd[1]));
    System.out.println(res.toString());

    String output=HttpUtil.get(qoscmd[0]);
    System.out.println(output);
    qoscmd=qosCMD(controller,dpid2);
    res=HttpUtil.postJSON(qoscmd[0], new JSONObject(qoscmd[1]));
    System.out.println(res.toString());

    output=HttpUtil.get(qoscmd[0]);
    System.out.println(output);
    qoscmd=qosCMD_1(controller,dpid2);

    res=HttpUtil.postJSON(qoscmd[0], new JSONObject(qoscmd[1]));
    System.out.println(res.toString());
    output=HttpUtil.get(qoscmd[0]);
    System.out.println(output);
    */

  }
}
