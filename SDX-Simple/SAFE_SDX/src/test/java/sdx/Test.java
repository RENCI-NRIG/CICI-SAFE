package sdx;

import sdx.core.*;
import org.apache.log4j.Logger;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.json.JSONObject;

import sdx.utils.Exec;
import sdx.utils.HttpUtil;

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
    testPerFlowQOS();
  }

  private static void testRouting(String[] args){
    SdxManager.startSdxServer(args);
    System.out.println("configured ip addresses in sdx network");
    //notify prefixes for node0 and node1
    SdxManager.notifyPrefix("192.168.10.2/24","192.168.10.2","notused");
    SdxManager.notifyPrefix("192.168.20.2/24","192.168.20.2","notused");
    System.out.println("IP prefix is set up, the two nodes should be able to talk now");
  }

  private static String[] queueCMD(String controller, String dpid){
    String[]res=new String[2];
    res[0]="http://"+controller+":8080/qos/queue/"+dpid;
    res[1]="{\"type\":\"linux-htb\",\"max_rate\":\"1000000\",\"queues\":[{\"min_rate\":\"500000\"},{\"max_rate\":\"100000\"}]}";
//     return "curl -X POST -d '{\"type\":\"linux-htb\",\"max_rate\":\"1000000\",\"queues\":[{\"max_rate\":\"100000\"},{\"min_rate\":\"500000\"}]}' http://"+controller+":8080/qos/queue/"+dpid;
    return res;
  }

  private static String[] qosCMD(String controller, String dpid){
    String[] res=new String[2];
    res[0]="http://"+controller+":8080/qos/rules/"+dpid;
    res[1]="{\"match\":{\"nw_dst\":\"192.168.10.2\",\"nw_proto\":\"TCP\",\"tp_dst\":\"5002\"},\"actions\":{\"queue\":\"1\"}}";
    return res;
  }

  private static String[] qosCMD_1(String controller, String dpid){
    String[] res=new String[2];
    res[0]="http://"+controller+":8080/qos/rules/"+dpid;
    res[1]="{\"match\":{\"nw_dst\":\"192.168.20.2\",\"nw_proto\":\"TCP\",\"tp_dst\":\"5002\"},\"actions\":{\"queue\":\"0\"}}";
    return res;
  }

  private static void testPerFlowQOS(){
    String dpid1=SdxManager.getDPID("c1");
    String dpid2=SdxManager.getDPID("c2");
    String controller=SdxManager.getSDNControllerIP();
    String[] queuecmd=queueCMD(controller,dpid1);
    HttpUtil.postJSON(queuecmd[0], new JSONObject(queuecmd[1]));
    HttpUtil.get(queuecmd[0]);
    queuecmd=queueCMD(controller,dpid2);
    HttpUtil.postJSON(queuecmd[0], new JSONObject(queuecmd[1]));
    HttpUtil.get(queuecmd[0]);
    //String getcmd="curl -X GET http://"+controller+":8080/qos/rules/"+dpid1;
    //String[] qoscmd="curl -X POST -d \'{\"match\":{\"nw_dst\":\"192.168.10.2\",\"nw_proto\":\"TCP\",\"tp_dst\":\"5002\"},\"actions\":{\"queue\":\"0\"}}\' http://"+controller+":8080/qos/rules/"+dpid1;
    String[]qoscmd=qosCMD(controller,dpid1);
    HttpUtil.postJSON(qoscmd[0], new JSONObject(qoscmd[1]));
    HttpUtil.get(qoscmd[0]);
    qoscmd=qosCMD_1(controller,dpid1);
    HttpUtil.postJSON(qoscmd[0], new JSONObject(qoscmd[1]));
    HttpUtil.get(qoscmd[0]);
    qoscmd=qosCMD(controller,dpid2);
    HttpUtil.postJSON(qoscmd[0], new JSONObject(qoscmd[1]));
    HttpUtil.get(qoscmd[0]);
    qoscmd=qosCMD_1(controller,dpid2);
    HttpUtil.postJSON(qoscmd[0], new JSONObject(qoscmd[1]));
    HttpUtil.get(qoscmd[0]);

  }
}
