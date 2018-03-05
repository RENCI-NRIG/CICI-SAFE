package bro;

import sdx.core.SdxManager;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

public class BroMain {
    static SdxManager sdxManager;
    static BroExperiment broExp;

  public static void main(String[] args){
    String[] arg1 = {"-c", "config/cnert-fl2.conf"};
    sdxManager = new SdxManager();
    sdxManager.startSdxServer(arg1);
    sdxManager.notifyPrefix("192.168.10.1/24", "192.168.10.2", "notused");
    sdxManager.notifyPrefix("192.168.20.1/24", "192.168.20.2", "notused");
    sdxManager.notifyPrefix("192.168.30.1/24", "192.168.30.2", "notused");
    sdxManager.notifyPrefix("192.168.40.1/24", "192.168.40.2", "notused");
    configFlows(sdxManager);
    broExp = new BroExperiment(sdxManager);
    broExp.addClient("node0",sdxManager.getManagementIP("node0"), "192.168.10.2");
    broExp.addClient("node1",sdxManager.getManagementIP("node1"), "192.168.20.2");
    broExp.addClient("node2",sdxManager.getManagementIP("node2"), "192.168.30.2");
    broExp.addClient("node3",sdxManager.getManagementIP("node3"), "192.168.40.2");
    int TIMES = 10;
    ArrayList<BroResult> results= null;
    for(int i=0; i<TIMES; i++) {
      List<double[]> multi = measureMultiMetrics();
      if(results == null){
        results = new ArrayList<>();
        for(int j =0; j < multi.size(); j++){
          results.add(new BroResult());
        }
      }
      for(int j = 0; j<multi.size(); j++){
        double[] res = multi.get(j);
        results.get(j).addCpuUtilization(res[0]);
        results.get(j).addDetectionRate(res[1]);
        results.get(j).addPacketDropRatio(res[2]);
      }
      List<Double> rt = measureResponseTime(30);
      for(int j = 0; j<multi.size(); j++){
        results.get(j).addDetectionTime(rt.get(j));
      }
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
    for(int flow = 100; flow <=1500; flow += 100){
      if(flow < 1000) {
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
    for(int flow = 100, i = 0; flow <=1500; flow += 100, i++) {
      double[] res = results.get(i);
      System.out.println(flow + " " + res[0] + " " + res[1] + " " + res[2]);
    }
    return results;
  }

  static ArrayList<Double> measureResponseTime(int saturateTime){
    ArrayList<Double> responseTime = new ArrayList<Double>();
    double MaxTime = 40.0;
    for(int flow = 100; flow <=1500; flow += 100){
      if(flow < 1000) {
        broExp.addFlow("node0", "node2", flow + "M");
        broExp.addFile("node0", "node2", "evil.txt");
        Double time = broExp.measureResponseTime(saturateTime);
        reconfigureSdxNetwork(sdxManager);
        while(time > MaxTime){
          System.out.println("bro failed to detect the file, retry");
          time = broExp.measureResponseTime(saturateTime);
          reconfigureSdxNetwork(sdxManager);
        }
        responseTime.add(time);
        broExp.clearFlows();
        broExp.clearFiles();
      }else{
        broExp.addFlow("node0", "node2", flow/2 + "M");
        broExp.addFlow("node1", "node3", flow/2 + "M");
        broExp.addFile("node0", "node2", "evil.txt");
        Double time = broExp.measureResponseTime(saturateTime);
        reconfigureSdxNetwork(sdxManager);
        while(time > MaxTime){
          System.out.println("bro failed to detect the file, retry");
          time = broExp.measureResponseTime(saturateTime);
          reconfigureSdxNetwork(sdxManager);
        }
        responseTime.add(time);
        broExp.clearFlows();
        broExp.clearFiles();
      }
    }

    for(int flow = 100, i = 0; flow <=1500; flow += 100, i++) {
      double time = responseTime.get(i);
      System.out.println(flow + " " + time);
    }
    return  responseTime;
  }
}
