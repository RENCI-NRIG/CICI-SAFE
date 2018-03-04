package bro;

import sdx.core.SdxManager;

import java.util.ArrayList;

public class BroMain {
    static SdxManager sdxManager;
    static BroExperiment broExp;
  public static void main(String[] args){
    String[] arg1 = {"-c", "config/cnert-fl.conf"};
    sdxManager = new SdxManager();
    sdxManager.startSdxServer(arg1);
    sdxManager.notifyPrefix("192.168.10.1/24", "192.168.10.2", "notused");
    sdxManager.notifyPrefix("192.168.20.1/24", "192.168.20.2", "notused");
    sdxManager.notifyPrefix("192.168.30.1/24", "192.168.30.2", "notused");
    sdxManager.notifyPrefix("192.168.40.1/24", "192.168.40.2", "notused");
    sdxManager.connectionRequest("not used", "192.168.10.1/24", "192.168.30.1/24", 0);
    sdxManager.connectionRequest("not used", "192.168.20.1/24", "192.168.40.1/24", 0);
    sdxManager.setMirror(sdxManager.getDPID("c0"), "192.168.10.1/24", "192.168.30.1/24",
      "192.168.128.2");
    sdxManager.setMirror(sdxManager.getDPID("c0"), "192.168.20.1/24", "192.168.40.1/24",
      "192.168.128.2");
    broExp = new BroExperiment(sdxManager);
    broExp.addClient("node0",sdxManager.getManagementIP("node0"), "192.168.10.2");
    broExp.addClient("node1",sdxManager.getManagementIP("node1"), "192.168.20.2");
    broExp.addClient("node2",sdxManager.getManagementIP("node2"), "192.168.30.2");
    broExp.addClient("node3",sdxManager.getManagementIP("node3"), "192.168.40.2");
    //measureMultiMetrics();
    measureResponseTime();
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

  static void measureMultiMetrics(){
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
    System.exit(0);
  }

  static void measureResponseTime(){
    ArrayList<Double> responseTime = new ArrayList<Double>();
    for(int flow = 100; flow <=1500; flow += 100){
      if(flow < 1000) {
        broExp.addFlow("node0", "node2", flow + "M");
        broExp.addFile("node0", "node2", "evil.txt");
        Double time = broExp.measureResponseTime();
        while(time > 40.0){
          System.out.println("bro failed to detect the file, retry");
          time = broExp.measureResponseTime();
        }
        responseTime.add(time);
        broExp.clearFlows();
        broExp.clearFiles();
      }else{
        broExp.addFlow("node0", "node2", flow/2 + "M");
        broExp.addFlow("node1", "node3", flow/2 + "M");
        broExp.addFile("node0", "node2", "evil.txt");
        Double time = broExp.measureResponseTime();
        while(time > 40.0){
          System.out.println("bro failed to detect the file, retry");
          time = broExp.measureResponseTime();
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
  }
}
