package bro;

import sdx.core.SdxManager;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import org.apache.log4j.Logger;

public class BroMain {
  final static Logger logger = Logger.getLogger(BroMain.class);
  static SdxManager sdxManager;
  static BroExperiment broExp;
  static int flowStart = 500;
  static int flowEnd = 1000;
  static int flowStep = 100;
  static ArrayList<BroResult> results = new ArrayList<>();

  public static void printResult(List<BroResult> results){
    System.out.println("Flow " + BroResult.getHeader());
    logger.debug("Flow " + BroResult.getHeader());
    for(int f = flowStart, i = 0; f <=flowEnd; f += flowStep, i++){
      BroResult result = results.get(i);
      logger.debug(f + result.toString());
      System.out.println(f + result.toString());
    }
  }

  public static void main(String[] args) {
    for (int j = 0; j <=(flowEnd - flowStart)/flowStep; j++) {
      results.add(new BroResult());
    }
    Runtime.getRuntime().addShutdownHook(new Thread() {
      public void run() {
        printResult(results);
      }
    });

    String[] arg1 = {"-c", "config/cnert-fl2.conf"};
    sdxManager = new SdxManager();
    sdxManager.startSdxServer(arg1);
    sdxManager.notifyPrefix("192.168.10.1/24", "192.168.10.2", "notused");
    sdxManager.notifyPrefix("192.168.20.1/24", "192.168.20.2", "notused");
    sdxManager.notifyPrefix("192.168.30.1/24", "192.168.30.2", "notused");
    sdxManager.notifyPrefix("192.168.40.1/24", "192.168.40.2", "notused");
    configFlows(sdxManager);
    broExp = new BroExperiment(sdxManager);
    broExp.addClient("node0", sdxManager.getManagementIP("node0"), "192.168.10.2");
    broExp.addClient("node1", sdxManager.getManagementIP("node1"), "192.168.20.2");
    broExp.addClient("node2", sdxManager.getManagementIP("node2"), "192.168.30.2");
    broExp.addClient("node3", sdxManager.getManagementIP("node3"), "192.168.40.2");
    int TIMES = 10;
    for (int i = 0; i < TIMES; i++) {
      List<double[]> multi = measureMultiMetrics();
      for (int j = 0; j < multi.size(); j++) {
        double[] res = multi.get(j);
        results.get(j).addCpuUtilization(res[0]);
        results.get(j).addDetectionRate(res[1]);
        results.get(j).addPacketDropRatio(res[2]);
      }
      List<Double> rt = measureResponseTime(30);
      for (int j = 0; j < multi.size(); j++) {
        results.get(j).addDetectionTime(rt.get(j));
      }
      printResult(results);
    }
    return;
  }

  static void reconfigureSdxNetwork(SdxManager sdxManager){
    sdxManager.delFlows();
    sdxManager.configRouting();
    configFlows(sdxManager);
  }

  static void configFlows(SdxManager sdxManager){
    sdxManager.connectionRequest("not used", "192.168.10.1/24", "192.168.30.1/24", 0);
    sdxManager.connectionRequest("not used", "192.168.20.1/24", "192.168.40.1/24", 0);
    sdxManager.setMirror(sdxManager.getDPID("c0"), "192.168.10.1/24", "192.168.30.1/24",
      "192.168.128.2");
    sdxManager.setMirror(sdxManager.getDPID("c0"), "192.168.20.1/24", "192.168.40.1/24",
      "192.168.128.2");
  }

  static void measureCPU(){
    broExp.addFlow("node0", "node2", "300M");
    broExp.measureCPU(20);
    broExp.clearFlows();
    broExp.addFlow("node0", "node2", "400M");
    broExp.measureCPU(20);
    broExp.clearFlows();
    broExp.addFlow("node0", "node2", "500M");
    broExp.measureCPU(20);
  }

  static List<double[]> measureMultiMetrics(){
    ArrayList<double[]> results = new ArrayList<>();
    for(int flow = flowStart; flow <= flowEnd; flow += flowStep){
      if(flow < 900) {
        broExp.addFlow("node0", "node2", flow + "M");
        broExp.addFile("node0", "node2", "evil.txt");
        double[] result = broExp.measureMultiMetrics(300, 200, 40);
        results.add(result);
        broExp.clearFlows();
        broExp.clearFiles();
      }else{
        broExp.addFlow("node0", "node2", flow/2 + "M");
        broExp.addFlow("node1", "node3", flow/2 + "M");
        broExp.addFile("node0", "node2", "evil.txt");
        double[] result = broExp.measureMultiMetrics(300, 200, 40);
        results.add(result);
        broExp.clearFlows();
        broExp.clearFiles();
      }
    }
    System.out.println("All experiments done: results:\n flow(M) cpu detectionRate dropRate");
    for(int flow = flowStart, i = 0; flow <= flowEnd; flow += flowStep, i++) {
      double[] res = results.get(i);
      System.out.println(flow + " " + res[0] + " " + res[1] + " " + res[2]);
    }
    return results;
  }

  static ArrayList<Double> measureResponseTime(int saturateTime){
    ArrayList<Double> responseTime = new ArrayList<Double>();
    double MaxTime = 40.0;
    for(int flow = flowStart, j = 0; flow <= flowEnd; flow += flowStep, j++){
      int fileTimes = 1;
      try{
        fileTimes = (int)(0.5/results.get(j).getDetectionRate().get(0));
        fileTimes = fileTimes > 1? fileTimes:1;
      }catch (Exception e){

      }
      if(flow < 900) {
        broExp.addFlow("node0", "node2", flow + "M");
        broExp.addFile("node0", "node2", "evil.txt");
        Double time = broExp.measureResponseTime(saturateTime, fileTimes);
        reconfigureSdxNetwork(sdxManager);
        while(time > MaxTime){
          System.out.println("bro failed to detect the file, retry");
          time = broExp.measureResponseTime(saturateTime, fileTimes);
          reconfigureSdxNetwork(sdxManager);
        }
        responseTime.add(time);
        broExp.clearFlows();
        broExp.clearFiles();
      }else{
        broExp.addFlow("node0", "node2", flow/2 + "M");
        broExp.addFlow("node1", "node3", flow/2 + "M");
        broExp.addFile("node0", "node2", "evil.txt");
        Double time = broExp.measureResponseTime(saturateTime, fileTimes);
        reconfigureSdxNetwork(sdxManager);
        while(time > MaxTime){
          System.out.println("bro failed to detect the file, retry");
          time = broExp.measureResponseTime(saturateTime, fileTimes);
          reconfigureSdxNetwork(sdxManager);
        }
        responseTime.add(time);
        broExp.clearFlows();
        broExp.clearFiles();
      }
    }

    for(int flow = flowStart, i = 0; flow <= flowEnd; flow += flowStep, i++) {
      double time = responseTime.get(i);
      System.out.println(flow + " " + time);
    }
    return  responseTime;
  }
}
