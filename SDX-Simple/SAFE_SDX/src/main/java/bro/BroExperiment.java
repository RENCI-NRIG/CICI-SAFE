package bro;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import common.slice.SliceCommon;
import common.utils.Exec;
import org.apache.log4j.Logger;
import org.apache.log4j.SimpleLayout;
import org.renci.ahab.libndl.resources.request.InterfaceNode2Net;
import sdx.core.SdxManager;

public class BroExperiment extends SliceCommon {
  final static Logger logger = Logger.getLogger(BroExperiment.class);
  HashMap<String, String[]> clients;
  HashMap<String, BroResult> result;
  ArrayList<Thread> tlist;
  final ArrayList<String[]> flows;
  final ArrayList<String[]> files;
  SdxManager sdxManager;
  private static String sshkey = "~/.ssh/id_rsa";
  private static String ftpuser = "ftpuser";
  private static String ftppw = "ftp";

  final ArrayList<String[]> broOut = new ArrayList<String[]>();
  final ArrayList<String[]> iperfClientOut = new ArrayList<String[]>();
  final ArrayList<String[]> iperfServerOut = new ArrayList<String[]>();
  final ArrayList<String[]> ftpClientOut = new ArrayList<String[]>();
  final ArrayList<String[]> pingClientOut = new ArrayList<String[]>();
  final ArrayList<String[]> routerOut = new ArrayList<String[]>();
  final ArrayList<String[]> cpuOut = new ArrayList<String[]>();
  String broName = "br0_c0";
  String broIP;
  String routerName = "c0";
  String flowPattern = ".*table=0.*nw_src=.*actions=drop.*";

  public BroExperiment(SdxManager sm){
    sdxManager = sm;
    result = new HashMap<String, BroResult>();
    clients = new HashMap<String, String[]>();
    flows = new ArrayList<String[]>();
    files = new ArrayList<String[]>();
    tlist = new ArrayList<Thread>();
  }

  public void addClient(String name, String managementIP, String dataplaneIP){
    clients.put(name, new String[]{managementIP, dataplaneIP});
  }

  public void addFlow(String c1, String c2, String bw){
    flows.add(new String[]{c1, c2, bw});
  }

  public void clearFlows(){
    flows.clear();
  }

  public void addFile(String client, String server, String file){
    files.add(new String[]{client, server, file});
  }

  public void clearFiles(){
    files.clear();
  }

  public void clearResult(){
    broOut.clear();
    iperfClientOut.clear();
    iperfServerOut.clear();
    ftpClientOut.clear();
    pingClientOut.clear();
    routerOut.clear();
    cpuOut.clear();
  }

  public void resetNetwork(){
    sdxManager.reset();
    sdxManager.configRouting();
  }

  public void startFlows(int seconds){
    logger.debug("Start sending background flows");
    for(String[] flow: flows){
      final String mip1 = clients.get(flow[0])[0];
      final String mip2 = clients.get(flow[1])[0];
      final String dip2 = clients.get(flow[1])[1];
      final String bw = flow[2];
      tlist.add(new Thread() {
                  @Override
                  public void run() {
                    iperfServerOut.add(Exec.sshExec("root", mip2, "pkill iperf; /usr/bin/iperf " +
                        "-s -u ",
                      sshkey));
                  }
                });
      tlist.get(tlist.size() - 1).start();
      sleep(2);
      tlist.add(new Thread() {
        @Override
        public void run() {
          iperfClientOut.add(Exec.sshExec("root", mip1,"pkill iperf; /usr/bin/iperf " +
            "-c " + dip2 + " -u -t " + seconds + " -b " + bw, sshkey));
        }
      });
      tlist.get(tlist.size() - 1).start();
    }
  }

  public void stopFlows(){
    logger.debug("stop flows");
    for(String[] flow: flows){
      final String mip1 = clients.get(flow[0])[0];
      final String mip2 = clients.get(flow[1])[0];
      final String dip2 = clients.get(flow[1])[1];
      final String bw = flow[2];
      Exec.sshExec("root", mip1,"pkill iperf", sshkey);
      Exec.sshExec("root", mip2,"pkill iperf", sshkey);
    }
  }

  public void stopBro(){
    logger.debug("Stop Bro");
    Exec.sshExec("root", broIP, "pkill bro", sshkey);
  }

  public void printSettings(){
    for(String[] flow: flows) {
      System.out.println(flow[0] + " " + flow[1] + " " + flow[2]);
    }
    for(String[] flow: files) {
      System.out.println(flow[0] + " " + flow[1] + " " + flow[2]);
    }
  }

  public void fetchFiles(int times){
    for(String[] file: files){
      final String mip1 = clients.get(file[0])[0];
      final String mip2 = clients.get(file[1])[0];
      final String dip2 = clients.get(file[1])[1];

      tlist.add(new Thread(() -> ftpClientOut.add(Exec.sshExec("root", mip1, fetchFileCMD
          (ftpuser, ftppw, dip2, file[2], times), sshkey))));

      tlist.get(tlist.size() - 1).start();
    }
  }

  private String fetchFileCMD(String ftpuser, String ftppw, String dip, String filename, int times){
    if(times >0) {
      return "rm evil.*;/bin/bash getnfiles.sh " + times + " " + ftpuser + " " + ftppw + " " + dip +
        " " + filename + ";";
    }else{
      return "";
    }
  }

  public void getFileAndEchoTime(int times){
    for(String[] file: files){
      final String mip1 = clients.get(file[0])[0];
      final String mip2 = clients.get(file[1])[0];
      final String dip1 = clients.get(file[0])[1];
      final String dip2 = clients.get(file[1])[1];
      tlist.add(new Thread() {
        @Override
        public void run() {
          ftpClientOut.add(Exec.sshExec("root", mip1, fetchFileCMD
            (ftpuser, ftppw, dip2, file[2], 1) + getEchoTimeCMD() + fetchFileCMD(ftpuser, ftppw,
            dip2, file[2], times -1 ), sshkey));
          flowPattern = ".*table=0.*nw_src="+ dip1 + " actions=drop.*";
        }
      });
      tlist.get(tlist.size() - 1).start();
    }
  }

  public void getFileAndPing(){
    for(String[] file: files){
      final String mip1 = clients.get(file[0])[0];
      final String mip2 = clients.get(file[1])[0];
      final String dip2 = clients.get(file[1])[1];
      tlist.add(new Thread() {
        @Override
        public void run() {
          pingClientOut.add(Exec.sshExec("root", mip1, fetchFileCMD
            (ftpuser, ftppw, dip2, file[2], 1) + "ping -i 0.01 -c 3000 " + dip2, sshkey));
          stopFlows();
          stopBro();
        }
      });
      tlist.get(tlist.size() - 1).start();
    }
  }


  public double measureCPU(int times){
    //start Bro
    broIP = sdxManager.getManagementIP("bro0_c0");
    routerName = "c0";
    tlist.add(new Thread() {
                @Override
                public void run() {
                  Exec.sshExec("root", broIP, "pkill bro", sshkey);
                  broOut.add(Exec.sshExec("root", broIP, "/opt/bro/bin/bro -i eth1 " +
                    "detect-all-policy.bro", sshkey));
                  stopFlows();
                }
              });
    tlist.get(tlist.size() - 1).start();
    sleep(3);
    int minimumSeconds = (int)(times * 1.5 + 30);
    startFlows(300 > minimumSeconds? 300:minimumSeconds);
    sleep(15);
    /*
    start cpu measurement
    Roughly it takes about 1 seconds to read cpu percentage once.
     */
    tlist.add(new Thread() {
                @Override
                public void run() {
                  cpuOut.add(Exec.sshExec("root", broIP, "/bin/bash /root/cpu_percentage.sh " +
                      times,
                    sshkey));
                  Exec.sshExec("root", broIP, "pkill bro", sshkey);
                }
              });
    tlist.get(tlist.size() - 1).start();

    //Then add background traffic

    //wait for all to finish
    try {
      for (Thread t : tlist) {
        t.join();
      }
    }catch (Exception e){
      e.printStackTrace();
    }

    System.out.println("Bro out");
    for (String[] s : broOut) {
      for(String str: s[1].split("\n")) {
        if (str.contains("packets received on interface")) {
          System.out.println(str);
        }
      }
    }

    System.out.println("CPU out");
    double cpuSum = 0;
    int count = 0;
    for(int i=0;i<2;i++) {
      for (String[] s : cpuOut) {
        if(i==0) {
          for(String str: s[0].split("\n")){
            try{
              double val = Double.valueOf(str);
              cpuSum += val;
              System.out.print(val + ", ");
              count ++;
            }catch (Exception e){
            }
          }
        }else {
          System.err.println(s[1]);
        }
      }
      System.out.println("");
    }

    System.out.println("iperf Client report");
    for(int i=0;i<2;i++) {
      for (String[] s : iperfClientOut) {
        if(i==0) {
          for(String str: s[0].split("\n")) {
            if (str.contains("%)")) {
              System.out.println(str);
            }
          }
        }else {
          System.err.println(s[1]);
        }
      }
    }

    System.out.println("iperf Server report");
    for(int i=0;i<2;i++) {
      for (String[] s : iperfServerOut) {
        if(i==0) {
          for(String str: s[0].split("\n")) {
            if (str.contains("%)")) {
              System.out.println(str);
            }
          }
        }else {
          System.err.println(s[1]);
        }
      }
    }
    assert times == count;
    System.out.println("Average CPU Usage: " + (cpuSum/times));
    clearResult();
    return cpuSum/times;
  }

  public double[] measureMultiMetrics(int flowSeconds, int fileTimes, int cpuTimes, String bro,
                                      String router) {
    /*
    Function to measure detection rate and packet drop ratio
    @Params
        flowSeconds: length of background traffic
        fileTimes: times of file transmission
     */
    broName = bro;
    routerName = router;
    broIP = sdxManager.getManagementIP(broName);
    tlist.add(new Thread() {
      @Override
      public void run() {
        Exec.sshExec("root", broIP, "pkill bro", sshkey);
        broOut.add(Exec.sshExec("root", broIP, "/opt/bro/bin/bro -i eth1 " +
          "detect-all-policy.bro", sshkey));
        stopFlows();
      }
    });
    tlist.get(tlist.size() - 1).start();
    sleep(3);
    startFlows(flowSeconds);
    sleep(30);
    //start getting files
    fetchFiles(fileTimes);


    /*
    start cpu measurement
    Roughly it takes about 1 seconds to read cpu percentage once.
     */
    tlist.add(new Thread() {
      @Override
      public void run() {
        cpuOut.add(Exec.sshExec("root", broIP, "/bin/bash /root/cpu_percentage.sh " +
            cpuTimes,
          sshkey));
      }
    });
    tlist.get(tlist.size() - 1).start();

    //Wait and stop all processes
    System.out.println("Waiting for experiment to be finished");
    sleep(flowSeconds);
    stopFlows();
    stopBro();

    //wait for all to finish
    try {
      for (Thread t : tlist) {
        t.join();
      }
    }catch (Exception e){
      e.printStackTrace();
    }

    System.out.println("Bro out");
    int fileDetected = 0;
    long received = 1;
    long dropped = 0;
    for (String[] s : broOut) {
      for(String str: s[0].split("\n")) {
        if (str.contains("file detected")) {
          fileDetected ++;
        }
      }
      for(String str: s[1].split("\n")) {
        if (str.contains("packets received on interface")) {
          String[] parts = str.split(" packets received on interface eth1, ");
          received = Long.valueOf(parts[0].split(" ")[parts[0].split(" ").length -1]);
          dropped = Long.valueOf(parts[1].split(" ")[0]);
          System.out.println(str);
        }
      }
    }

    System.out.println("CPU out");
    double cpuSum = 0;
    int count = 0;
    for(int i=0;i<2;i++) {
      for (String[] s : cpuOut) {
        if(i==0) {
          for(String str: s[0].split("\n")){
            try{
              double val = Double.valueOf(str);
              cpuSum += val;
              System.out.print(val + ", ");
              count ++;
            }catch (Exception e){
            }
          }
        }else {
          System.err.println(s[1]);
        }
      }
      System.out.println("");
    }

    System.out.println("iperf Client report");
    for(int i=0;i<2;i++) {
      for (String[] s : iperfClientOut) {
        if(i==0) {
          for(String str: s[0].split("\n")) {
            if (str.contains("%)")) {
              System.out.println(str);
            }
          }
        }else {
          System.err.println(s[1]);
        }
      }
    }

    System.out.println("iperf Server report");
    for(int i=0;i<2;i++) {
      for (String[] s : iperfServerOut) {
        if(i==0) {
          for(String str: s[0].split("\n")) {
            if (str.contains("%)")) {
              System.out.println(str);
            }
          }
        }else {
          System.err.println(s[1]);
        }
      }
    }
    assert cpuTimes == count;
    double dropRatio = (double)dropped/(double)(received + dropped + 1);
    if(received + dropped < 10){
      System.out.println("Abnormal bro output");
      logger.debug("Abnormal bro output");
    }
    System.out.println("Average CPU Usage: " + (cpuSum/cpuTimes));
    System.out.println("Detection Rate: " + ((double)fileDetected/(double)fileTimes));
    System.out.println("Drop Rate: " + dropRatio);
    clearResult();
    printSettings();
    return new double[]{cpuSum/cpuTimes, (double)fileDetected/(double)fileTimes, dropRatio};
  }

  void printOut(List<String[]>result){
    for (String[] s : result) {
      System.out.println(s[0]);
      System.out.println(s[1]);
    }
  }

  public double measureResponseTime(int saturateTime, int fileTimes, int sleepTime, String
    bro, String router){
    broName = bro;
    broIP = sdxManager.getManagementIP(broName);
    routerName = router;
    tlist.add(new Thread() {
      @Override
      public void run() {
        broOut.add(Exec.sshExec("root", broIP, "/usr/bin/rm *.log; pkill bro; /opt/bro/bin/bro " +
          "-i eth1 test-all-policy.bro", sshkey));
        stopFlows();
      }
    });
    tlist.get(tlist.size() - 1).start();
    sleep(3);
    startFlows(300);
    sleep(saturateTime);
    /*
    start cpu measurement
    Roughly it takes about 1 seconds to read cpu percentage once.
     */
    //fetch one file and start ping, after 30 seconds, terminate bro and stop all flows
    getFileAndEchoTime(fileTimes);
    long flowTime = 0;
    for(int second = 0; second <sleepTime; second += 5){
      String flowInstallationTime = sdxManager.getFlowInstallationTime(routerName, flowPattern);
      if(flowInstallationTime == null){
        sleep(5);
      }else{
        flowTime = Long.valueOf(flowInstallationTime);
        break;
      }
    }
    stopFlows();
    stopBro();

    //wait for all to finish
    try {
      for (Thread t : tlist) {
        t.join();
      }
    }catch (Exception e){
      e.printStackTrace();
    }

    //getFileCompletionTime
    String fileCompletionTime = "0";
    for(String[] s:ftpClientOut){
      for(String str: s[0].split("\n")){
        if(str.matches("currentMillis:\\d+")){
          logger.debug(str);
          fileCompletionTime = str.split(":")[1];
        }
      }
    }
    double responseTime = 0.0;
    if(flowTime == 0.0){
      responseTime = 100.0* 1000;
    }
    else{
      responseTime = flowTime - Long.valueOf(fileCompletionTime);
    }
    System.out.println("Bro out");
    int fileDetected = 0;
    for (String[] s : broOut) {
      logger.debug("Bro:" + s[0]);
      logger.debug("Bro:" + s[1]);
      for(String str: s[0].split("\n")) {
        if (str.contains("file detected")) {
          logger.debug(str);
          fileDetected ++;
        }
      }
      for(String str: s[1].split("\n")) {
        if (str.contains("packets received on interface")) {
          System.out.println(str);
        }
      }
    }

    System.out.println("iperf Client report");
    for(int i=0;i<2;i++) {
      for (String[] s : iperfClientOut) {
        if(i==0) {
          for(String str: s[0].split("\n")) {
            if (str.contains("%)")) {
              System.out.println(str);
            }
          }
        }else {
          System.err.println(s[1]);
        }
      }
    }

    System.out.println("iperf Server report");
    for(int i=0;i<2;i++) {
      for (String[] s : iperfServerOut) {
        if(i==0) {
          for(String str: s[0].split("\n")) {
            if (str.contains("%)")) {
              System.out.println(str);
            }
          }
        }else {
          System.err.println(s[1]);
        }
      }
    }

    printSettings();
    System.out.println("Response Time " + responseTime + " ms");
    clearResult();
    return responseTime/1000.0;
  }
}
