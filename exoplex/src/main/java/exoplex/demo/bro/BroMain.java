package exoplex.demo.bro;

import com.google.inject.Guice;
import com.google.inject.Injector;
import exoplex.demo.multisdx.MultiSdxModule;
import exoplex.sdx.core.CoreProperties;
import exoplex.sdx.core.exogeni.ExoSdxManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class BroMain {
  final static Logger logger = LogManager.getLogger(BroMain.class);
  static ExoSdxManager exoSdxManager;
  static BroExperiment broExp;
  static int flowStart = 0;
  static int flowEnd = 1500;
  static int flowStep = 100;
  static String fileName = "data-1.txt";
  static ArrayList<BroResult> results = new ArrayList<>();
  static String broName = "bro0_c1";
  static String routerName = "c1";
  static String routerNoBro = "c0";
  static String broIP = "192.168.129.2";


  public static void main(String[] args) throws Exception {
    for (int j = 0; j <= (flowEnd - flowStart) / flowStep; j++) {
      results.add(new BroResult());
    }
    Runtime.getRuntime().addShutdownHook(new Thread() {
      public void run() {
        printResult(results);
        saveResult(results, fileName);
      }
    });
    Injector injector = Guice.createInjector(new MultiSdxModule());

    String[] arg1 = {"-c", "config/cnert-renci-sl.conf"};
    exoSdxManager = injector.getInstance(ExoSdxManager.class);
    exoSdxManager.startSdxServer(new CoreProperties(args));
    exoSdxManager.notifyPrefix("192.168.10.1/24", "192.168.10.2", "notused");
    exoSdxManager.notifyPrefix("192.168.20.1/24", "192.168.20.2", "notused");
    exoSdxManager.notifyPrefix("192.168.30.1/24", "192.168.30.2", "notused");
    exoSdxManager.notifyPrefix("192.168.40.1/24", "192.168.40.2", "notused");
    reconfigureSdxNetwork(exoSdxManager);
    broExp = new BroExperiment(exoSdxManager);
    broExp.addClient("node0", exoSdxManager.getManagementIP("node0"), "192.168.10.2");
    broExp.addClient("node1", exoSdxManager.getManagementIP("node1"), "192.168.20.2");
    broExp.addClient("node2", exoSdxManager.getManagementIP("node2"), "192.168.30.2");
    broExp.addClient("node3", exoSdxManager.getManagementIP("node3"), "192.168.40.2");
    int TIMES = 10;
    for (int i = 0; i < TIMES; i++) {
      System.out.println("=============== " + i + " ==============");
      logger.debug("=============== " + i + " ==============");
      /*
      List<Double> cpu = measureCPU();
      for (int j = 0; j < cpu.size(); j++) {
        results.get(j).addCpuUtilization(cpu.get(j));
      }*/

      int flowSeconds = 300;
      if (flowEnd < 500) {
        flowSeconds = 100;
      }
      List<double[]> multi = measureMultiMetrics(300);
      for (int j = 0; j < multi.size(); j++) {
        double[] res = multi.get(j);
        results.get(j).addCpuUtilization(res[0]);
        results.get(j).addDetectionRate(res[1]);
        results.get(j).addPacketDropRatio(res[2]);
      }

      List<Double> rt = measureResponseTime(30);
      for (int j = 0; j < rt.size(); j++) {
        results.get(j).addDetectionTime(rt.get(j));
      }
      printResult(results);
      saveResult(results, fileName);
    }
    return;
  }

  static void reconfigureSdxNetwork(ExoSdxManager exoSdxManager) throws Exception {
    exoSdxManager.delFlows();
    Method configRouting = exoSdxManager.getClass().getDeclaredMethod("configRouting");
    configRouting.setAccessible(true);
    configRouting.invoke(exoSdxManager);
    if (!configFlows(exoSdxManager)) {
      System.out.println("Configure routing and mirror failed, retry");
      logger.debug("Configure routing and mirror failed, retry");
      reconfigureSdxNetwork(exoSdxManager);
    }
  }

  static boolean configFlows(ExoSdxManager exoSdxManager) throws Exception {
    exoSdxManager.connectionRequest("192.168.10.1/24", "192.168.30.1/24", 0);
    exoSdxManager.connectionRequest("192.168.20.1/24", "192.168.40.1/24", 0);
    exoSdxManager.setMirror(routerName, "192.168.10.1/24", "192.168.30.1/24");
    exoSdxManager.setMirror(routerName, "192.168.20.1/24", "192.168.40.1/24");
    String routeFlowPattern = ".*nw_src.*nw_dst.*actions=dec_ttl.*load.*";
    boolean suc = false;
    for (int i = 0; i < 5; i++) {
      if (exoSdxManager.getNumRouteEntries(routerName, routeFlowPattern) == 8 && exoSdxManager
        .getNumRouteEntries(routerNoBro, routeFlowPattern) == 4) {
        suc = true;
        break;
      }
    }
    return suc;
  }

  static List<Double> measureCPU() {
    ArrayList<Double> results = new ArrayList<>();
    for (int flow = flowStart; flow <= flowEnd; flow += flowStep) {
      if (flow < 900) {
        broExp.addUdpFlow("node0", "node2", flow + "M");
        broExp.addFile("node0", "node2", "evil.txt");
        double result = broExp.measureCPU(40);
        results.add(result);
        broExp.clearFlows();
        broExp.clearFiles();
      } else {
        broExp.addUdpFlow("node0", "node2", flow / 2 + "M");
        broExp.addUdpFlow("node1", "node3", flow / 2 + "M");
        broExp.addFile("node0", "node2", "evil.txt");
        double result = broExp.measureCPU(40);
        results.add(result);
        broExp.clearFlows();
        broExp.clearFiles();
      }
    }
    return results;
  }

  static List<double[]> measureMultiMetrics(int flowSeconds) {
    int filetimes = 200;
    int cputimes = 50;
    ArrayList<double[]> results = new ArrayList<>();
    for (int flow = flowStart; flow <= flowEnd; flow += flowStep) {
      if (flow < 900) {
        if (flow > 0) {
          broExp.addUdpFlow("node0", "node2", flow + "M");
        }
        broExp.addFile("node0", "node2", "evil.txt");
        double[] result = broExp.measureMultiMetrics(flowSeconds, filetimes, cputimes, broName,
          routerName);
        results.add(result);
        broExp.clearFlows();
        broExp.clearFiles();
      } else {
        broExp.addUdpFlow("node0", "node2", flow / 2 + "M");
        broExp.addUdpFlow("node1", "node3", flow / 2 + "M");
        broExp.addFile("node0", "node2", "evil.txt");
        double[] result = broExp.measureMultiMetrics(flowSeconds, filetimes, cputimes, broName,
          routerName);
        results.add(result);
        broExp.clearFlows();
        broExp.clearFiles();
      }
    }
    System.out.println("All experiments done: results:\n flow(M) cpu detectionRate dropRate");
    for (int flow = flowStart, i = 0; flow <= flowEnd; flow += flowStep, i++) {
      double[] res = results.get(i);
      System.out.println(flow + " " + res[0] + " " + res[1] + " " + res[2]);
    }
    return results;
  }

  static ArrayList<Double> measureResponseTime(int saturateTime) throws Exception {
    ArrayList<Double> responseTime = new ArrayList<Double>();
    double MaxTime = 40.0;
    for (int flow = flowStart, j = 0; flow <= flowEnd; flow += flowStep, j++) {
      int fileTimes = 1;
      fileTimes = (flow - 500) / 100;
      fileTimes = fileTimes > 1 ? fileTimes : 1;
      int sleepTime = 30;
      if (flow < 900) {
        if (flow > 0) {
          broExp.addUdpFlow("node0", "node2", flow + "M");
        }
        broExp.addFile("node0", "node2", "evil.txt");
        Double time = broExp.measureResponseTime(saturateTime, fileTimes, sleepTime, broName, routerName);
        reconfigureSdxNetwork(exoSdxManager);
        while (time > MaxTime) {
          System.out.println("Bro failed to detect the file, retry");
          time = broExp.measureResponseTime(saturateTime, fileTimes, sleepTime, broName, routerName);
          reconfigureSdxNetwork(exoSdxManager);
        }
        responseTime.add(time);
        broExp.clearFlows();
        broExp.clearFiles();
      } else {
        broExp.addUdpFlow("node0", "node2", flow / 2 + "M");
        broExp.addUdpFlow("node1", "node3", flow / 2 + "M");
        broExp.addFile("node0", "node2", "evil.txt");
        Double time = broExp.measureResponseTime(saturateTime, fileTimes, sleepTime, broName, routerName);
        reconfigureSdxNetwork(exoSdxManager);
        while (time > MaxTime) {
          System.out.println("Bro failed to detect the file, retry");
          time = broExp.measureResponseTime(saturateTime, fileTimes, sleepTime, broName, routerName);
          reconfigureSdxNetwork(exoSdxManager);
        }
        responseTime.add(time);
        broExp.clearFlows();
        broExp.clearFiles();
      }
    }

    for (int flow = flowStart, i = 0; flow <= flowEnd; flow += flowStep, i++) {
      double time = responseTime.get(i);
      System.out.println(flow + " " + time);
    }
    return responseTime;
  }

  public static void printResult(List<BroResult> results) {
    System.out.println("Flow " + BroResult.getHeader());
    logger.debug("Flow " + BroResult.getHeader());
    for (int f = flowStart, i = 0; f <= flowEnd; f += flowStep, i++) {
      BroResult result = results.get(i);
      logger.debug(f + result.toString());
      System.out.println(f + result.toString());
    }
  }

  public static void saveResult(List<BroResult> results, String filename) {
    try (BufferedWriter br = new BufferedWriter(new FileWriter(filename))) {
      br.write("Flow " + BroResult.getHeader() + "\n");
      for (int f = flowStart, i = 0; f <= flowEnd; f += flowStep, i++) {
        BroResult result = results.get(i);
        br.write(f + "M " + result.toString());
        br.write(result.originalData());
      }
      br.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
