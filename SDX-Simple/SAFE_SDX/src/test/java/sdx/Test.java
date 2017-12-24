package sdx;

import sdx.core.*;
import org.apache.log4j.Logger;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.json.JSONObject;

import sdx.utils.Exec;
import sdx.utils.HttpUtil;

import java.io.IOException;
import java.net.URI;

public class Test {
  final static Logger logger = Logger.getLogger(Exec.class);

  public static HttpServer startServer(String url) {
    // create a resource config that scans for JAX-RS resources and providers
    // in com.example package
    final ResourceConfig rc = new ResourceConfig().packages("sdx.core");
    // create and start a new instance of grizzly http server
    // exposing the Jersey application at BASE_URI
    return GrizzlyHttpServerFactory.createHttpServer(URI.create(url), rc);
  }

  public static void main(String[] args) {
    testRouting(args);
    //testPerFlowQOS();
    limitQos();
  }

  private static void testRouting(String[] args) {
    SdxManager.startSdxServer(args);
    System.out.println("configured ip addresses in sdx network");
    //notify prefixes for node0 and node1
    SdxManager.notifyPrefix("192.168.10.2/24", "192.168.10.2", "c0", "notused");
    SdxManager.notifyPrefix("192.168.30.2/24", "192.168.10.2", "c0", "notused");
    SdxManager.notifyPrefix("192.168.20.2/24", "192.168.20.2", "c3", "notused");
    String dpid = SdxManager.getDPID("c0");
    String[] cmd = mirrorCMD(SdxManager.getSDNControllerIP(), dpid, "192.168.20.1/24",
        "192.168.10.2/24", "192.168.101.2");
    System.out.println(cmd[1]);
    String res = HttpUtil.postJSON(cmd[0], new JSONObject(cmd[1]));
    cmd = mirrorCMD(SdxManager.getSDNControllerIP(), dpid, "192.168.10.2/24",
      "192.168.20.1/24", "192.168.101.2");
    System.out.println(cmd[1]);
    System.out.println(Long.parseLong(dpid, 16));
    res = HttpUtil.postJSON(cmd[0], new JSONObject(cmd[1]));
    System.out.println(res);
    System.out.println(cmd[0]);
    System.out.println(cmd[1]);
    System.out.println("IP prefix is set up, the two nodes should be able to talk now");
    /*
    try {
      System.out.println("Now Set up vsftp in node");
      System.in.read();
    }catch (IOException e){
      e.printStackTrace();
    }
    */
  }

  private static String[] mirrorCMD(String controller, String dpid, String source, String dst, String gw) {
    String[] res = new String[2];
    res[0] = "http://" + controller + ":8080/router/" + dpid;
    //res[1] = "{\"source\":\"" + source + "\", \"destination\": \"" + dst + "\", \"mirror\":\"" + gw + "\"}";
    JSONObject params = new JSONObject();
    params.put("mirror", gw);
    if(source != null){
      params.put("source", source);
    }
    if(dst != null){
      params.put("destination", dst);
    }
    res[1] = params.toString();
    return res;
  }

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

  private static void limitQos() {
    String dpid1 = SdxManager.getDPID("c0");
    String controller = SdxManager.getSDNControllerIP();
    String[] queuecmd = queueCMD(controller, dpid1);
    HttpUtil.postJSON(queuecmd[0], new JSONObject(queuecmd[1]));
    HttpUtil.get(queuecmd[0]);
    String[] qoscmd = qosCMD(controller, dpid1);
    HttpUtil.postJSON(qoscmd[0], new JSONObject(qoscmd[1]));
  }

  private static void testPerFlowQOS() {
    String dpid1 = "00000628a4daa642";
    String dpid2 = "00004621982f9b41";
    String controller = "152.54.14.12";
    String[] queuecmd = queueCMD(controller, dpid1);
    HttpUtil.postJSON(queuecmd[0], new JSONObject(queuecmd[1]));
    HttpUtil.get(queuecmd[0]);
    queuecmd = queueCMD(controller, dpid2);
    HttpUtil.postJSON(queuecmd[0], new JSONObject(queuecmd[1]));
    HttpUtil.get(queuecmd[0]);
    String[] qoscmd = qosCMD(controller, dpid1);
    HttpUtil.postJSON(qoscmd[0], new JSONObject(qoscmd[1]));
    qoscmd = qosCMD(controller, dpid2);
    HttpUtil.postJSON(qoscmd[0], new JSONObject(qoscmd[1]));

  }
}
