package test;
import client.exogeniclient.ClientSlice;
import client.exogeniclient.SdxExogeniClient;
import sdx.core.SdxServer;

public class TestMain {
  public static void main(String[] args){
    //createTestSlice();
    test();
  }

  public static void test(){
    String[] args = {"-c", "config/test.conf", "-n"};
    SdxServer.run(args);
    String[] clientarg1 = {"-c", "client-config/c3-tamu.conf", "-n"};
    String[] clientarg2 = {"-c", "client-config/c4-tamu.conf", "-n"};
    SdxExogeniClient client1 = new SdxExogeniClient(clientarg1);
    SdxExogeniClient client2 = new SdxExogeniClient(clientarg2);
    //NOTE: need to maintain sdx controller, rest_router_mirror doesn't have stplib
    client1.processCmd("stitch CNode0 test-yaoy c0");
    client2.processCmd("stitch CNode0 test-yaoy c1");
    client1.processCmd("route 192.168.30.1/24 192.168.30.1");
    client2.processCmd("route 192.168.40.1/24 192.168.40.1");
    client1.processCmd("link 192.168.30.1/24 192.168.40.1/24 1000000");
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
