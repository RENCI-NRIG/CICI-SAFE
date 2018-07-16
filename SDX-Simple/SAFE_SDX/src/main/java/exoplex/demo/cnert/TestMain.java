package exoplex.demo.cnert;

import exoplex.client.exogeni.ClientSlice;
import exoplex.client.exogeni.SdxExogeniClient;
import exoplex.client.stitchport.SdxStitchPortClientManager;
import org.apache.commons.cli.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.renci.ahab.libtransport.util.TransportException;
import exoplex.sdx.core.SdxServer;

import java.util.ArrayList;

public class TestMain {
  private static final Logger logger = LogManager.getLogger(TestMain.class.getName());
  /*
  static String state = "fl";
  static String site1 = "ufl";
  static String site2 = "unf";
  */
  //name of exoplex.sdx slice
  static String sdx = "demo";
  //Now only arg1 and clientarg 1->4 are used
  static String[] arg1 = {"-c", "config/sdx.conf"};
  static String[] arg2 = {"-c", "config/sdx.conf", "-r"};
  static String[] clientarg1 = {"-c", "client-config/c1.conf"};
  static String[] clientarg2 = {"-c", "client-config/c2.conf"};
  static String[] clientarg3 = {"-c", "client-config/c3.conf"};
  static String[] clientarg4 = {"-c", "client-config/c4.conf"};
  static String[] clientarg6 = {"-c", "client-config/c6.conf"};
  static String[] clientarg5 = {"-c", "chameleon-config/c1.conf"};
  static boolean stitch = true;

  public static void main(String[] args) throws Exception {
    multiSliceTest(args);
    //emulationTest();
    //testDymanicNetwork();
    //testChameleon();
    //emulationSlice();
  }

  private static CommandLine parseCmd(String[] args) {
    Options options = new Options();
    Option config1 = new Option("s", "slice", false, "run with existing slice");
    Option config2 = new Option("r", "reset", false, "reset SDX slice");
    Option config3 = new Option("d", "delete", false, "delete all slices");
    config1.setRequired(false);
    config2.setRequired(false);
    config3.setRequired(false);
    options.addOption(config1);
    options.addOption(config2);
    options.addOption(config3);
    CommandLineParser parser = new DefaultParser();
    HelpFormatter formatter = new HelpFormatter();
    CommandLine cmd = null;

    try {
      cmd = parser.parse(options, args);
      return cmd;
    } catch (ParseException e) {
      System.out.println(e.getMessage());
      formatter.printHelp("utility-name", options);

      System.exit(1);
    }
    return cmd;
  }

  public static void multiSliceTest(String[] args) throws Exception {
    CommandLine cmd = parseCmd(args);

    if(cmd.hasOption('d')){
      deleteSlice();
      return;
    }
    if (!cmd.hasOption('s')) {
      deleteSlice();
      createTestSliceParrallel();
    }
    if (cmd.hasOption('r')) {
      test(true);
    } else {
      test(false);
    }
  }

  public static void testDymanicNetwork() {
    SdxExogeniClient client6 = new SdxExogeniClient(clientarg6);

    // Client request for connection between prefixes
    if (stitch) {
      client6.processCmd("stitch CNode0 " + sdx);
    }
    client6.processCmd("route 192.168.60.1/24 192.168.136.2");
    client6.processCmd("link 192.168.60.1/24 192.168.40.1/24 1000000");
    //client6.processCmd("link 192.168.60.1/24 192.168.40.1/24");
  }

  public static void test(boolean reset) throws TransportException, Exception {
    /*
    In this function, we create ahab controller for Sdx slice and Client slices.
    We execute command in Client controller to request network stitching to Sdx slice,
    advertise the ip prefix, and request for network connection
     */
    // Start Sdx Server
    //./scripts/sdxserver.sh -c config/sdx.conf
    if (reset) {
      SdxServer.run (arg2);
    } else {
      SdxServer.run(arg1);
    }
    sdx = SdxServer.sdxManager.getSliceName();
    SdxExogeniClient client1 = new SdxExogeniClient(clientarg1);
    SdxExogeniClient client2 = new SdxExogeniClient(clientarg2);
    SdxExogeniClient client3 = new SdxExogeniClient(clientarg3);
    SdxExogeniClient client4 = new SdxExogeniClient(clientarg4);
    SdxExogeniClient client6 = new SdxExogeniClient(clientarg6);

    if (stitch) {
      System.out.println("c1 stitches to SDX");
      client1.processCmd("stitch CNode0 " + sdx + " e0");
      System.out.println("c2 stitches to SDX");
      client2.processCmd("stitch CNode0 " + sdx + " e1");
      System.out.println("c3 stitches to SDX");
      client3.processCmd("stitch CNode0 " + sdx + " e0");
      System.out.println("c4 stitches to SDX");
      client4.processCmd("stitch CNode0 " + sdx + " e1");
    }

    // Client slice advertise their prefix
    client1.processCmd("route 192.168.10.1/24 192.168.132.2");
    client2.processCmd("route 192.168.20.1/24 192.168.133.2");
    client3.processCmd("route 192.168.30.1/24 192.168.134.2");
    client4.processCmd("route 192.168.40.1/24 192.168.135.2");

    // Client request for connection between prefixes
    client1.processCmd("link 192.168.10.1/24 192.168.20.1/24");
    if( !client1.checkConnectivity("CNode1", "192.168.20.2")){
      SdxServer.sdxManager.checkFlowTableForPair("192.168.10.0/24", "192.168.20.0/24",
          "192.168.10.1/24", "192.168.20.1/24");
    }
    client3.processCmd("link 192.168.30.1/24 192.168.40.1/24");

    if( !client3.checkConnectivity("CNode1", "192.168.40.2")){
      SdxServer.sdxManager.checkFlowTableForPair("192.168.30.0/24", "192.168.40.0/24",
      "192.168.30.1/24", "192.168.40.1/24");
    }

    client6.processCmd("stitch CNode0 " + sdx);
    //An IP address will be used when we add new core-edge router pair: 192.168.136.1/24
    client6.processCmd("route 192.168.60.1/24 192.168.137.2");
    client6.processCmd("link 192.168.60.1/24 192.168.10.1/24");
    if(!client6.checkConnectivity("CNode1", "192.168.10.2")){
      SdxServer.sdxManager.checkFlowTableForPair("192.168.10.0/24", "192.168.60.0/24",
      "192.168.10.1/24", "192.168.60.1/24");
    }
    client6.processCmd("link 192.168.60.1/24 192.168.20.1/24");
    if(!client6.checkConnectivity("CNode1", "192.168.20.2")){
      SdxServer.sdxManager.checkFlowTableForPair("192.168.20.0/24", "192.168.60.0/24",
      "192.168.20.1/24", "192.168.60.1/24");
    }
    //SdxServer.sdxManager.logFlowTables();
    /*
    SdxServer.sdxManager.removePath("192.168.30.1/24", "192.168.40.1/24");
    client1.processCmd("link 192.168.30.1/24 192.168.40.1/24 10000");
    */

    //String res = SdxServer.sdxManager.setMirror("c0",
    //  "192.168.30.1/24", "192.168.40.1/24", 400000000);
    //System.out.println(res);

    //res = SdxServer.sdxManager.setMirror("c0", "192.168.10.1/24",
    //  "192.168.20.1/24", 400000000);
    //System.out.println(res);

    // Stop Sdx server and exit
  }

  public static void testChameleon() throws TransportException , Exception{
    SdxServer.run(arg1);
    SdxExogeniClient client1 = new SdxExogeniClient(clientarg1);
    SdxExogeniClient client2 = new SdxExogeniClient(clientarg2);
    SdxExogeniClient client3 = new SdxExogeniClient(clientarg3);
    SdxExogeniClient client4 = new SdxExogeniClient(clientarg4);
    SdxStitchPortClientManager cc = new SdxStitchPortClientManager(clientarg5);
    if (stitch) {
      //client1.processCmd("stitch CNode0 " + sdx + " c0");
      client2.processCmd("stitch CNode0 " + sdx + " c1");
      client3.processCmd("stitch CNode0 " + sdx + " c0");
      client4.processCmd("stitch CNode0 " + sdx + " c1");
      cc.processCmd("stitch http://geni-orca.renci.org/owl/ion" +
          ".rdf#AL2S/Chameleon/Cisco/6509/GigabitEthernet/1/1 3296 " + sdx +
          "c1 10.32.90.206 10.32.90.200/24 ");
    }

    // Client slice advertise their prefix
    client1.processCmd("route 192.168.10.1/24 192.168.130.2");
    client2.processCmd("route 192.168.20.1/24 192.168.131.2");
    client3.processCmd("route 192.168.30.1/24 192.168.132.2");
    client4.processCmd("route 192.168.40.1/24 192.168.133.2");
    cc.processCmd("route 10.32.90.1/24 10.32.90.206");
    cc.processCmd("link 10.32.90.1/24 192.168.20.1/24");

    // Client request for connection between prefixes
    client3.processCmd("link 192.168.30.1/24 192.168.40.1/24");
    client1.processCmd("link 192.168.10.1/24 192.168.20.1/24");
  }

  public static void deleteSlice() {
    TestSlice ts = new TestSlice(arg1);
    ClientSlice s1 = new ClientSlice(clientarg1);
    ClientSlice s2 = new ClientSlice(clientarg2);
    ClientSlice s3 = new ClientSlice(clientarg3);
    ClientSlice s4 = new ClientSlice(clientarg4);
    ClientSlice s6 = new ClientSlice(clientarg6);
    ts.delete();
    s1.delete();
    s2.delete();
    s3.delete();
    s4.delete();
    s6.delete();
  }

  public static void createTestSliceParrallel() throws  Exception{
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

    String[][] args = {clientarg1, clientarg2, clientarg3, clientarg4, clientarg6};
    for (int i = 0; i < 5; i++) {
      final String[] arg = args[i];
      Thread thread2 = new Thread() {
        @Override
        public void run() {
          ClientSlice s1 = new ClientSlice(arg);
          try {
            s1.run();
          }catch (Exception e){
            e.printStackTrace();
            return;
          }
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
    }catch (NullPointerException ex){
      throw ex;
    }
    catch (Exception e) {
      e.printStackTrace();
    }
    System.out.println("Finished create vSDX slice and Client slices");
  }

  public static void emulationTest(String[] args) throws Exception {
    /*
    This function emulates the Sdx slice and customer nodes in the same slice.
     */
    CommandLine cmd = parseCmd(args);
    if (!cmd.hasOption('s')) {
      emulationSlice();
    }
    SdxServer.run(arg1);
    SdxExogeniClient client = new SdxExogeniClient(clientarg4);
    client.processCmd("route 192.168.10.1/24 192.168.10.2");
    client.processCmd("route 192.168.20.1/24 192.168.20.2");
    client.processCmd("route 192.168.30.1/24 192.168.30.2");
    client.processCmd("route 192.168.40.1/24 192.168.40.2");
    client.processCmd("link 192.168.10.1/24 192.168.30.1/24");
    client.processCmd("link 192.168.20.1/24 192.168.40.1/24");

    SdxServer.sdxManager.setMirror("c0", "192.168.10.1/24",
        "192.168.30.1/24");

    if (!cmd.hasOption('s')) {
      try {
        SdxServer.sdxManager.deployBro("c0");
      } catch (TransportException e) {
        e.printStackTrace();
        return;
      }
    }
    SdxServer.sdxManager.setMirror("c0", "192.168.20.1/24",
        "192.168.40.1/24");

    System.exit(0);
  }

  public static void emulationSlice() {
    TestSlice ts = new TestSlice(arg1);
    ts.delete();
    ts.testBroSliceTwoPairs();
  }
}
