package test;
import client.exogeni.SdxExogeniClientManager;
import sdx.core.SdxServer;
import client.exogeni.ClientSlice;

public class TestMain {
  static String[] arg1 = {"-c", "config/cnert-fl.conf", "-n"};
  static String[] clientarg1 = {"-c", "client-config/c1-ufl.conf", "-n"};
  static String[] clientarg2 = {"-c", "client-config/c2-unf.conf", "-n"};
  static String[] clientarg3 = {"-c", "client-config/c3-ufl.conf", "-n"};
  static String[] clientarg4 = {"-c", "client-config/c4-unf.conf", "-n"};

  public static void main(String[] args){
    createTestSlice();
    test();
  }

  public static void test(){
    //rest_router_mirror line 1057 for info about deleting flows. It delete the flows
    //cookie is used to differenciate different flows. Use unique cookie ids accross all tables
    // to manage the flows.

    //use "-n" option in arguments to opt out safe authorization

    //start Sdx Server
    SdxServer.run(arg1);
    SdxExogeniClientManager client1 = new SdxExogeniClientManager(clientarg1);
    SdxExogeniClientManager client2 = new SdxExogeniClientManager(clientarg2);
    SdxExogeniClientManager client3 = new SdxExogeniClientManager(clientarg3);
    SdxExogeniClientManager client4 = new SdxExogeniClientManager(clientarg4);

    //client slice request stitching
    client1.processCmd("stitch CNode0 test-fl c0");
    client2.processCmd("stitch CNode0 test-fl c1");
    client3.processCmd("stitch CNode0 test-fl c0");
    client4.processCmd("stitch CNode0 test-fl c1");
    //TODO new interface not added to bridge

    // client slice advertise their prefix
    client1.processCmd("route 192.168.10.1/24 192.168.129.2");
    client2.processCmd("route 192.168.20.1/24 192.168.130.2");
    client3.processCmd("route 192.168.30.1/24 192.168.131.2");
    client4.processCmd("route 192.168.40.1/24 192.168.132.2");

    //client request for connection between prefixes
    client1.processCmd("link 192.168.10.1/24 192.168.20.1/24");
    client3.processCmd("link 192.168.30.1/24 192.168.40.1/24");
    /*
    SdxServer.sdxManager.removePath("192.168.30.1/24", "192.168.40.1/24");
    client1.processCmd("link 192.168.30.1/24 192.168.40.1/24 10000");
    */

    SdxServer.sdxManager.setMirror(SdxServer.sdxManager.getDPID("c0"), "192.168.30.1/24",
      "192.168.40.1/24", "192.168.100.2");

    SdxServer.sdxManager.setMirror(SdxServer.sdxManager.getDPID("c0"), "192.168.40.1/24",
      "192.168.30.1/24", "192.168.100.2");
    /*
    SdxServer.sdxManager.delMirror(SdxServer.sdxManager.getDPID("c0"), "192.168.30.1/24",
      "192.168.40.1/24");

    SdxServer.sdxManager.delMirror(SdxServer.sdxManager.getDPID("c0"), "192.168.40.1/24",
      "192.168.30.1/24");
    */

    //stop sdx server and exit
    System.exit(0);
  }

  public static void createTestSlice(){
    TestSlice ts = new TestSlice(arg1);
    ClientSlice s1 =  new ClientSlice();
    ClientSlice s2 = new ClientSlice();
    ClientSlice s3 =  new ClientSlice();
    ClientSlice s4 = new ClientSlice();
    ts.run(arg1);
    s1.run(clientarg1);
    s2.run(clientarg2);
    s3.run(clientarg3);
    s4.run(clientarg4);
  }
}
