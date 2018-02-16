package test;
import client.exogeni.SdxExogeniClientManager;
import sdx.core.SdxServer;
import client.exogeni.ClientSlice;

public class TestMain {
  public static void main(String[] args){
    //createTestSlice();
    test();
  }

  public static void test(){
    //rest_router_mirror line 1057 for info about deleting flows. It delete the flows
    //cookie is used to differenciate different flows. Use unique cookie ids accross all tables
    // to manage the flows.
    String[] args = {"-c", "config/test.conf", "-n"};
    SdxServer.run(args);
    String[] clientarg1 = {"-c", "client-config/c3-tamu.conf", "-n"};
    String[] clientarg2 = {"-c", "client-config/c4-tamu.conf", "-n"};
    SdxExogeniClientManager client1 = new SdxExogeniClientManager(clientarg1);
    SdxExogeniClientManager client2 = new SdxExogeniClientManager(clientarg2);
    client1.processCmd("stitch CNode0 test-yaoy c0");
    client2.processCmd("stitch CNode0 test-yaoy c1");
    client1.processCmd("route 192.168.30.1/24 192.168.129.2 c0");
    client2.processCmd("route 192.168.40.1/24 192.168.130.2 c1");
    //SdxServer.sdxManager.removePath("192.168.30.1/24", "192.168.40.1/24");
    //client1.processCmd("link 192.168.30.1/24 192.168.40.1/24 10000");
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
    System.exit(0);
  }

  public static void createTestSlice(){
    String[] arg1 = {"-c", "config/test.conf"};
    TestSlice ts = new TestSlice(arg1);
    String[] clientarg1 = {"-c", "client-config/c3-tamu.conf"};
    String[] clientarg2 = {"-c", "client-config/c4-tamu.conf"};
    ClientSlice s1 =  new ClientSlice();
    ClientSlice s2 = new ClientSlice();
    ts.createAndConfigCarrierSlice();
    s1.run(clientarg1);
    s2.run(clientarg2);
  }
}
