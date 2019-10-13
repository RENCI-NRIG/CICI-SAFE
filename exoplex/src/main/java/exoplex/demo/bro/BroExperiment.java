package exoplex.demo.bro;

import exoplex.common.utils.Exec;
import exoplex.experiment.ExperimentBase;
import exoplex.sdx.core.exogeni.ExoSdxManager;
import exoplex.sdx.slice.SliceProperties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;

public class BroExperiment extends ExperimentBase {
  final static Logger logger = LogManager.getLogger(BroExperiment.class);
  final ArrayList<String[]> broOut = new ArrayList<String[]>();
  HashMap<String, String[]> clients;
  HashMap<String, BroResult> result;
  String broName = "br0_c0";
  String broIP;
  String routerName = "c0";
  String flowPattern = ".*table=0.*nw_src=.*actions=drop.*";

  public BroExperiment(ExoSdxManager sm) {
    super(sm);
    result = new HashMap<String, BroResult>();
  }

  @Override
  public void clearResult() {
    broOut.clear();
    flowManager.clearResults();
    ftpClientOut.clear();
    pingClientOut.clear();
    routerOut.clear();
    cpuOut.clear();
  }

  public void stopBro() {
    logger.debug("Stop Bro");
    Exec.sshExec(SliceProperties.userName, broIP, "pkill bro", sshkey);
  }

  public void getFileAndEchoTime(int times) {
    for (String[] file : files) {
      final String mip1 = clients.get(file[0])[0];
      final String mip2 = clients.get(file[1])[0];
      final String dip1 = clients.get(file[0])[1];
      final String dip2 = clients.get(file[1])[1];
      tlist.add(new Thread() {
        @Override
        public void run() {
          ftpClientOut.add(Exec.sshExec(SliceProperties.userName, mip1, fetchFileCMD
            (ftpuser, ftppw, dip2, file[2], 1) + getEchoTimeCMD() + fetchFileCMD(ftpuser, ftppw,
            dip2, file[2], times - 1), sshkey));
          flowPattern = ".*table=0.*nw_src=" + dip1 + " actions=drop.*";
        }
      });
      tlist.get(tlist.size() - 1).start();
    }
  }

  public void getFileAndPing() {
    for (String[] file : files) {
      final String mip1 = clients.get(file[0])[0];
      final String mip2 = clients.get(file[1])[0];
      final String dip2 = clients.get(file[1])[1];
      tlist.add(new Thread() {
        @Override
        public void run() {
          pingClientOut.add(Exec.sshExec(SliceProperties.userName, mip1, fetchFileCMD
            (ftpuser, ftppw, dip2, file[2], 1) + "ping -i 0.01 -c 3000 " + dip2, sshkey));
          stopFlows();
          stopBro();
        }
      });
      tlist.get(tlist.size() - 1).start();
    }
  }


  public double measureCPU(int times) {
    //start Bro
    broIP = exoSdxManager.getManagementIP("bro0_c0");
    routerName = "c0";
    tlist.add(new Thread() {
      @Override
      public void run() {
        Exec.sshExec(SliceProperties.userName, broIP, "pkill bro", sshkey);
        broOut.add(Exec.sshExec(SliceProperties.userName, broIP, "/opt/bro/bin/bro -i eth1 " +
          "detect-all-policy.bro", sshkey));
        stopFlows();
      }
    });
    tlist.get(tlist.size() - 1).start();
    sleep(3);
    int minimumSeconds = (int) (times * 1.5 + 30);
    startFlows(300 > minimumSeconds ? 300 : minimumSeconds);
    sleep(15);
    /*
    start cpu measurement
    Roughly it takes about 1 seconds to read cpu percentage once.
     */
    tlist.add(new Thread() {
      @Override
      public void run() {
        cpuOut.add(Exec.sshExec(SliceProperties.userName, broIP,
          String.format("sudo /bin/bash %scpu_percentage.sh %s",
            SliceProperties.homeDir, times),
          sshkey));
        Exec.sshExec(SliceProperties.userName, broIP, "sudo pkill bro", sshkey);
      }
    });
    tlist.get(tlist.size() - 1).start();

    //Then add background traffic

    //wait for all to finish
    try {
      for (Thread t : tlist) {
        t.join();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

    System.out.println("Bro out");
    for (String[] s : broOut) {
      for (String str : s[1].split("\n")) {
        if (str.contains("packets received on interface")) {
          System.out.println(str);
        }
      }
    }

    System.out.println("CPU out");
    double cpuSum = 0;
    int count = 0;
    for (int i = 0; i < 2; i++) {
      for (String[] s : cpuOut) {
        if (i == 0) {
          for (String str : s[0].split("\n")) {
            try {
              double val = Double.valueOf(str);
              cpuSum += val;
              System.out.print(val + ", ");
              count++;
            } catch (Exception e) {
            }
          }
        } else {
          System.err.println(s[1]);
        }
      }
      System.out.println();
    }

    System.out.println("iperf Client report");
    for (int i = 0; i < 2; i++) {
      for (String[] s : flowManager.getClientResults()) {
        if (i == 0) {
          for (String str : s[0].split("\n")) {
            if (str.contains("%)")) {
              System.out.println(str);
            }
          }
        } else {
          System.err.println(s[1]);
        }
      }
    }

    System.out.println("iperf Server report");
    for (int i = 0; i < 2; i++) {
      for (String[] s : flowManager.getServerResults()) {
        if (i == 0) {
          for (String str : s[0].split("\n")) {
            if (str.contains("%)")) {
              System.out.println(str);
            }
          }
        } else {
          System.err.println(s[1]);
        }
      }
    }
    assert times == count;
    System.out.println("Average CPU Usage: " + (cpuSum / times));
    clearResult();
    return cpuSum / times;
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
    broIP = exoSdxManager.getManagementIP(broName);
    tlist.add(new Thread() {
      @Override
      public void run() {
        Exec.sshExec(SliceProperties.userName, broIP, "pkill bro", sshkey);
        broOut.add(Exec.sshExec(SliceProperties.userName, broIP, "/opt/bro/bin/bro -i eth1 " +
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
        cpuOut.add(Exec.sshExec(SliceProperties.userName, broIP,
          String.format("sudo /bin/bash %scpu_percentage.sh %s",
            SliceProperties.homeDir, cpuTimes),
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
    } catch (Exception e) {
      e.printStackTrace();
    }

    System.out.println("Bro out");
    int fileDetected = 0;
    long received = 1;
    long dropped = 0;
    for (String[] s : broOut) {
      for (String str : s[0].split("\n")) {
        if (str.contains("file detected")) {
          fileDetected++;
        }
      }
      for (String str : s[1].split("\n")) {
        if (str.contains("packets received on interface")) {
          String[] parts = str.split(" packets received on interface eth1, ");
          received = Long.valueOf(parts[0].split(" ")[parts[0].split(" ").length - 1]);
          dropped = Long.valueOf(parts[1].split(" ")[0]);
          System.out.println(str);
        }
      }
    }

    System.out.println("CPU out");
    double cpuSum = 0;
    int count = 0;
    for (int i = 0; i < 2; i++) {
      for (String[] s : cpuOut) {
        if (i == 0) {
          for (String str : s[0].split("\n")) {
            try {
              double val = Double.valueOf(str);
              cpuSum += val;
              System.out.print(val + ", ");
              count++;
            } catch (Exception e) {
            }
          }
        } else {
          System.err.println(s[1]);
        }
      }
      System.out.println();
    }

    System.out.println("iperf Client report");
    for (int i = 0; i < 2; i++) {
      for (String[] s : flowManager.getClientResults()) {
        if (i == 0) {
          for (String str : s[0].split("\n")) {
            if (str.contains("%)")) {
              System.out.println(str);
            }
          }
        } else {
          System.err.println(s[1]);
        }
      }
    }

    System.out.println("iperf Server report");
    for (int i = 0; i < 2; i++) {
      for (String[] s : flowManager.getServerResults()) {
        if (i == 0) {
          for (String str : s[0].split("\n")) {
            if (str.contains("%)")) {
              System.out.println(str);
            }
          }
        } else {
          System.err.println(s[1]);
        }
      }
    }
    assert cpuTimes == count;
    double dropRatio = (double) dropped / (double) (received + dropped + 1);
    if (received + dropped < 10) {
      System.out.println("Abnormal bro output");
      logger.debug("Abnormal bro output");
    }
    System.out.println("Average CPU Usage: " + (cpuSum / cpuTimes));
    System.out.println("Detection Rate: " + ((double) fileDetected / (double) fileTimes));
    System.out.println("Drop Rate: " + dropRatio);
    clearResult();
    printSettings();
    return new double[]{cpuSum / cpuTimes, (double) fileDetected / (double) fileTimes, dropRatio};
  }

  public double measureResponseTime(int saturateTime, int fileTimes, int sleepTime, String
    bro, String router) {
    broName = bro;
    broIP = exoSdxManager.getManagementIP(broName);
    routerName = router;
    tlist.add(new Thread() {
      @Override
      public void run() {
        broOut.add(Exec.sshExec(SliceProperties.userName, broIP, "/usr/bin/rm *.log; pkill bro; /opt/bro/bin/bro " +
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
    for (int second = 0; second < sleepTime; second += 5) {
      String flowInstallationTime = exoSdxManager.getFlowInstallationTime(routerName, flowPattern);
      if (flowInstallationTime == null) {
        sleep(5);
      } else {
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
    } catch (Exception e) {
      e.printStackTrace();
    }

    //getFileCompletionTime
    String fileCompletionTime = "0";
    for (String[] s : ftpClientOut) {
      for (String str : s[0].split("\n")) {
        if (str.matches("currentMillis:\\d+")) {
          logger.debug(str);
          fileCompletionTime = str.split(":")[1];
        }
      }
    }
    double responseTime = 0.0;
    if (flowTime == 0.0) {
      responseTime = 100.0 * 1000;
    } else {
      responseTime = flowTime - Long.valueOf(fileCompletionTime);
    }
    System.out.println("Bro out");
    int fileDetected = 0;
    for (String[] s : broOut) {
      logger.debug("Bro:" + s[0]);
      logger.debug("Bro:" + s[1]);
      for (String str : s[0].split("\n")) {
        if (str.contains("file detected")) {
          logger.debug(str);
          fileDetected++;
        }
      }
      for (String str : s[1].split("\n")) {
        if (str.contains("packets received on interface")) {
          System.out.println(str);
        }
      }
    }

    System.out.println("iperf Client report");
    for (int i = 0; i < 2; i++) {
      for (String[] s : flowManager.getClientResults()) {
        if (i == 0) {
          for (String str : s[0].split("\n")) {
            if (str.contains("%)")) {
              System.out.println(str);
            }
          }
        } else {
          System.err.println(s[1]);
        }
      }
    }

    System.out.println("iperf Server report");
    for (int i = 0; i < 2; i++) {
      for (String[] s : flowManager.getServerResults()) {
        if (i == 0) {
          for (String str : s[0].split("\n")) {
            if (str.contains("%)")) {
              System.out.println(str);
            }
          }
        } else {
          System.err.println(s[1]);
        }
      }
    }

    printSettings();
    System.out.println("Response Time " + responseTime + " ms");
    clearResult();
    return responseTime / 1000.0;
  }
}
