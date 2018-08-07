package exoplex.experiment;

import exoplex.common.utils.Exec;
import exoplex.experiment.flow.FlowManager;
import exoplex.experiment.latency.MeasureLatency;
import exoplex.sdx.core.SdxManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ExperimentBase {
  private final static Logger logger = LogManager.getLogger(ExperimentBase.class);
  protected static String sshkey = "~/.ssh/id_rsa";
  protected static String ftpuser = "ftpuser";
  protected static String ftppw = "ftp";
  protected final ArrayList<String[]> flows;
  protected final ArrayList<String[]> files;
  protected FlowManager flowManager;
  protected MeasureLatency latencyTask;
  protected final ArrayList<String[]> ftpClientOut = new ArrayList<String[]>();
  protected final ArrayList<String[]> pingClientOut = new ArrayList<String[]>();
  protected final ArrayList<String[]> routerOut = new ArrayList<String[]>();
  protected final ArrayList<String[]> cpuOut = new ArrayList<String[]>();
  protected HashMap<String, String[]> clients;
  protected ArrayList<Thread> tlist;
  protected SdxManager sdxManager;

  public ExperimentBase(SdxManager sm) {
    sdxManager = sm;
    flowManager = new FlowManager(sdxManager.getSshKey());
    clients = new HashMap<String, String[]>();
    flows = new ArrayList<String[]>();
    files = new ArrayList<String[]>();
    tlist = new ArrayList<Thread>();
  }

  public void addClient(String name, String managementIP, String dataplaneIP) {
    clients.put(name, new String[]{managementIP, dataplaneIP});
    Exec.sshExec("root", managementIP, "apt-get install -y iperf", sshkey);
  }

  public void setLatencyTask(String client, String server){
    final String mip1 = clients.get(client)[0];
    final String mip2 = clients.get(server)[0];
    final String dip2 = clients.get(server)[1];
    latencyTask = new MeasureLatency(mip1, dip2, sshkey);
    latencyTask.setPeriod(1000);
    latencyTask.setTotalTime(30);
  }

  public void startLatencyTask(){
    latencyTask.start();
  }

  public void stopLatencyTask(){
    latencyTask.stop();
  }

  public void printLatencyResult(){
    latencyTask.printResults();
  }

  public void addUdpFlow(String client, String server, String bw) {
    final String mip1 = clients.get(client)[0];
    final String mip2 = clients.get(server)[0];
    final String dip2 = clients.get(server)[1];
    flowManager.addUdpFlow(mip1, mip2, dip2, bw);
  }

  public void clearFlows() {
    flows.clear();
  }

  public void addFile(String client, String server, String file) {
    files.add(new String[]{client, server, file});
  }

  public void clearFiles() {
    files.clear();
  }

  public void clearResult() {
    //broOut.clear();
    flowManager.clearResults();
    ftpClientOut.clear();
    pingClientOut.clear();
    routerOut.clear();
    cpuOut.clear();
  }

  public void resetNetwork() {
    sdxManager.reset();
    sdxManager.configRouting();
  }

  //TODO: flow length seconds is not used
  public void startFlows(int seconds) {
    logger.debug("Start flows");
    flowManager.start();
  }

  public void stopFlows() {
    logger.debug("Stop flows");
    flowManager.stop();
  }

  public void printSettings() {
    for (String[] flow : flows) {
      System.out.println(flow[0] + " " + flow[1] + " " + flow[2]);
    }
    for (String[] flow : files) {
      System.out.println(flow[0] + " " + flow[1] + " " + flow[2]);
    }
  }

  public void fetchFiles(int times) {
    for (String[] file : files) {
      final String mip1 = clients.get(file[0])[0];
      final String mip2 = clients.get(file[1])[0];
      final String dip2 = clients.get(file[1])[1];

      tlist.add(new Thread(() -> ftpClientOut.add(Exec.sshExec("root", mip1, fetchFileCMD
          (ftpuser, ftppw, dip2, file[2], times), sshkey))));

      tlist.get(tlist.size() - 1).start();
    }
  }

  public String fetchFileCMD(String ftpuser, String ftppw, String dip, String filename, int times) {
    if (times > 0) {
      return "rm evil.*;/bin/bash getnfiles.sh " + times + " " + ftpuser + " " + ftppw + " " + dip +
          " " + filename + ";";
    } else {
      return "";
    }
  }


  public void  printOut(List<String[]> result) {
    for (String[] s : result) {
      System.out.println(s[0]);
      System.out.println(s[1]);
    }
  }

  protected String getEchoTimeCMD() {
    return "echo currentMillis:$(/bin/date \"+%s%3N\");";
  }

  protected void sleep(int seconds){
    try{
      sleep(seconds * 1000);
    }catch (Exception e){

    }
  }
}
