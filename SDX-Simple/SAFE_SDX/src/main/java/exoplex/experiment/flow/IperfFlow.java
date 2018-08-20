package exoplex.experiment.flow;

import exoplex.common.utils.Exec;
import exoplex.experiment.task.AsyncTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class IperfFlow extends AsyncTask{
  String iperfServer;
  String managementIp;
  String serverIp;
  int port;
  int seconds;
  String bw;
  String proto;
  String sshKey;
  ArrayList<String[]> results = new ArrayList<>();
  static String baseCmd = "/usr/bin/iperf";

  public IperfFlow(String clientIp, String serverIp, String sshKey, int port, int seconds, String bw, String proto, String iperfServer){
    super(UUID.randomUUID(),
      0l,
      TimeUnit.SECONDS,
      new ArrayList<>(),
      new HashMap<>());
    this.iperfServer = iperfServer;
    this.managementIp = clientIp;
    this.serverIp = serverIp;
    this.port = port;
    this.sshKey = sshKey;
    this.bw = bw;
    this.seconds = seconds;
    this.proto = proto;
    started = false;
  }

  @Override
  public void runTask() {
    String cmd = baseCmd;
    cmd = cmd + " -c " + serverIp;
    if (proto.equals(IperfServer.UDP)) {
      cmd = cmd + " -u";
    }
    if (seconds > 0) {
      cmd = cmd + " -t " + seconds;
    }
    cmd = cmd + " -b " + bw;
    results.add(Exec.sshExec("root", managementIp, cmd, sshKey));
  }

  public void stop(){
    lock.lock();
    if(started) {
      Exec.sshExec("root", managementIp, "pkill iperf", sshKey);
      started = false;
    }
    lock.unlock();
  }

  public List<String[]> getResults(){
    return results;
  }

  public void clearResults(){
    results.clear();
  }
}
