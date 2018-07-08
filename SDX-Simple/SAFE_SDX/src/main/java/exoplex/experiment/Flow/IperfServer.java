package exoplex.experiment.Flow;

import exoplex.common.utils.Exec;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class IperfServer {

  public static String TCP = "tcp";
  public static String UDP = "udp";
  static String baseCmd = "/usr/bin/iperf -s ";
  String ip;
  int port;
  String transportProto;
  ReentrantLock lock = new ReentrantLock();
  boolean started;
  String sshKey;
  ArrayList<String[]> results = new ArrayList<>();
  Thread thread;
  public IperfServer(String ip, int port, String transportProto, String sshKey){
    this.ip = ip;
    this.port = port;
    this.transportProto = transportProto;
    this.sshKey = sshKey;
    started = false;
  }

  public void start(){
    lock.lock();
    if(started){
      lock.unlock();
      return;
    }else {
      thread=
          new Thread() {
            @Override
            public void run() {
              String cmd = baseCmd;
              if(port != 0){
                cmd = cmd + " -p " + port;
              }
              if(transportProto.equals(UDP)){
                cmd = cmd + " -u";
              }
              results.add(Exec.sshExec("root", ip, cmd,
                  sshKey));
            }
          };
      thread.start();
      started = true;
      lock.unlock();
      return;
    }
  }

  public void stop(){
    lock.lock();
    if(started){
      Exec.sshExec("root", ip, "pkill iperf",
          sshKey);
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

  @Override
  public String toString(){
    return String.format("ip %s port", this.ip, this.port);
  }
}
