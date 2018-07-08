package exoplex.experiment.Flow;

import exoplex.common.utils.Exec;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class IperfFlow {
  String iperfServer;
  String managementIp;
  String serverIp;
  int port;
  int seconds;
  String bw;
  String proto;
  String sshKey;
  ArrayList<String[]> results = new ArrayList<>();
  Thread thread;
  ReentrantLock lock = new ReentrantLock();
  boolean started;
  static String baseCmd = "/usr/bin/iperf";

  public IperfFlow(String clientIp, String serverIp, String sshKey, int port, int seconds, String bw, String proto, String iperfServer){
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

  public void start() {
    lock.lock();
    if (!started) {
      thread = new Thread() {
        @Override
        public void run() {
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
          try{
            lock.lock();
            started = false;
            lock.unlock();
          }catch (Exception e){

          }
        }
      };
      thread.start();
      started = true;
    }
    try {
      lock.unlock();
    }catch (Exception e){

    }
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
