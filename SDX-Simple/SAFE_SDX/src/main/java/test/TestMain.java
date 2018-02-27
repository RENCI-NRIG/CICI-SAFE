package test;
import java.util.ArrayList;
import client.exogeni.SdxExogeniClientManager;
import sdx.core.SdxServer;
import client.exogeni.ClientSlice;

public class TestMain {
  static String state = "fl";
  static String site1 = "ufl";
  static String site2 = "unf";
  static String sdx = "test-fl";
  static String[] arg1 = {"-c", "config/cnert-"+ state + ".conf"};
  static String[] clientarg1 = {"-c", "client-config/c1-" + site1 + ".conf"};
  static String[] clientarg2 = {"-c", "client-config/c2-"+ site2+".conf"};
  static String[] clientarg3 = {"-c", "client-config/c3-"+site1+".conf"};
  static String[] clientarg4 = {"-c", "client-config/c4-"+site2+".conf"};
  static boolean newSlice = true;
  static boolean stitch = true;

  public static void main(String[] args){
    multiSliceTest();
    //emulationTest();
  }

  public static void emulationTest(){
    if(newSlice) {
      TestSlice ts = new TestSlice(arg1);
      ts.delete();
      ts.testBroSliceTwoPairs();
    }
    SdxServer.run(arg1);
    SdxExogeniClientManager client = new SdxExogeniClientManager(clientarg4);
    client.processCmd("route 192.168.10.1/24 192.168.10.2");
    client.processCmd("route 192.168.20.1/24 192.168.20.2");
    client.processCmd("route 192.168.30.1/24 192.168.30.2");
    client.processCmd("route 192.168.40.1/24 192.168.40.2");
    client.processCmd("link 192.168.10.1/24 192.168.30.1/24");
    client.processCmd("link 192.168.20.1/24 192.168.40.1/24");

    SdxServer.sdxManager.setMirror(SdxServer.sdxManager.getDPID("c0"), "192.168.10.1/24",
      "192.168.30.1/24");

    if(newSlice) {
      SdxServer.sdxManager.deployBro("c0");
    }
    SdxServer.sdxManager.setMirror(SdxServer.sdxManager.getDPID("c0"), "192.168.20.1/24",
     "192.168.40.1/24");

    System.exit(0);
  }

  public static void multiSliceTest(){
    if(newSlice) {
      deleteSlice();
      createTestSliceParrallel();
    }
    test();
  }

  public static void test(){
    // rest_router_mirror line 1057 for info about deleting flows. It delete the flows
    // cookie is used to differenciate different flows. Use unique cookie ids accross all tables
    // to manage the flows.

    // Use "-n" option in arguments to opt out safe authorization

    // Start Sdx Server
    SdxServer.run(arg1);
    SdxExogeniClientManager client1 = new SdxExogeniClientManager(clientarg1);
    SdxExogeniClientManager client2 = new SdxExogeniClientManager(clientarg2);
    SdxExogeniClientManager client3 = new SdxExogeniClientManager(clientarg3);
    SdxExogeniClientManager client4 = new SdxExogeniClientManager(clientarg4);

    if(stitch) {
      client1.processCmd("stitch CNode0 " + sdx + " c0");
      client2.processCmd("stitch CNode0 " + sdx + " c1");
      client3.processCmd("stitch CNode0 " + sdx + " c0");
      client4.processCmd("stitch CNode0 " + sdx + " c1");
    }

    // client slice advertise their prefix
    client1.processCmd("route 192.168.10.1/24 192.168.130.2");
    client2.processCmd("route 192.168.20.1/24 192.168.131.2");
    client3.processCmd("route 192.168.30.1/24 192.168.132.2");
    client4.processCmd("route 192.168.40.1/24 192.168.133.2");

    // Client request for connection between prefixes
    client3.processCmd("link 192.168.30.1/24 192.168.40.1/24");
    client1.processCmd("link 192.168.10.1/24 192.168.20.1/24");
    /*
    SdxServer.sdxManager.removePath("192.168.30.1/24", "192.168.40.1/24");
    client1.processCmd("link 192.168.30.1/24 192.168.40.1/24 10000");
    */

    SdxServer.sdxManager.setMirror(SdxServer.sdxManager.getDPID("c0"), "192.168.30.1/24",
      "192.168.40.1/24", 400000000);

    SdxServer.sdxManager.setMirror(SdxServer.sdxManager.getDPID("c0"), "192.168.10.1/24",
      "192.168.20.1/24", 400000000);

    // Stop Sdx server and exit
    System.exit(0);
  }

  public static void createTestSlice(){
    TestSlice ts = new TestSlice(arg1);
    ClientSlice s1 =  new ClientSlice(clientarg1);
    ClientSlice s2 = new ClientSlice(clientarg2);
    ClientSlice s3 =  new ClientSlice(clientarg3);
    ClientSlice s4 = new ClientSlice(clientarg4);

    ArrayList<Thread> tlist = new ArrayList<Thread>();
    tlist.add(new Thread(() -> ts.run(arg1)));
    tlist.add(new Thread(() -> s1.run()));
    tlist.add(new Thread(() -> s2.run()));
    tlist.add(new Thread(() -> s3.run()));
    tlist.add(new Thread(() -> s4.run()));

    tlist.forEach(w -> {
      try {
        w.join();
      } catch (Exception e) {
        e.printStackTrace();
      }
    });
  }

  public static void deleteSlice(){
    TestSlice ts = new TestSlice(arg1);
    ClientSlice s1 =  new ClientSlice(clientarg1);
    ClientSlice s2 = new ClientSlice(clientarg2);
    ClientSlice s3 =  new ClientSlice(clientarg3);
    ClientSlice s4 = new ClientSlice(clientarg4);
    ts.delete();
    s1.delete();
    s2.delete();
    s3.delete();
    s4.delete();
  }

  public static void createTestSliceParrallel(){
    ArrayList<Thread> tlist = new ArrayList<Thread>();
    Thread thread1 = new Thread() {
      @Override
      public void run() {
        TestSlice ts = new TestSlice(arg1);
        ts.run(arg1);
      }
    };
    thread1.start();
    tlist.add(thread1);
    SdxServer.sdxManager.sleep(10);

    String[][] args = {clientarg1, clientarg2, clientarg3, clientarg4};
    for(int i = 0 ; i< 4; i++) {
      final String[] arg = args[i];
      Thread thread2 = new Thread() {
        @Override
        public void run() {
          ClientSlice s1 = new ClientSlice(arg);
          s1.run();
        }
      };
      thread2.start();
      tlist.add(thread2);
      SdxServer.sdxManager.sleep(10);
    }

    try {
      for (Thread t : tlist) {
        t.join();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
