package exoplex.experiment.flow;

import exoplex.common.utils.Exec;
import exoplex.experiment.task.AsyncTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class IperfServer extends AsyncTask {
  final static Logger logger = LogManager.getLogger(IperfServer.class);

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

  public IperfServer(String ip, int port, String transportProto, String sshKey) {
    super(UUID.randomUUID(),
      0l,
      TimeUnit.SECONDS,
      new ArrayList<>(),
      new HashMap<>());
    this.ip = ip;
    this.port = port;
    this.transportProto = transportProto;
    this.sshKey = sshKey;
    started = false;
  }

  @Override
  public void runTask() {
    String cmd = baseCmd;
    if (port != 0) {
      cmd = cmd + " -p " + port;
    }
    if (transportProto.equals(UDP)) {
      cmd = cmd + " -u";
    }
    logger.info(cmd);
    String[] res = Exec.sshExec("root", ip, cmd,
      sshKey);
    results.add(res);
    logger.info(res[0]);
    logger.info(res[1]);
  }

  public void stop() {
    lock.lock();
    if (started) {
      Exec.sshExec("root", ip, "pkill iperf",
        sshKey);
      started = false;
    }
    lock.unlock();
  }

  public List<String[]> getResults() {
    return results;
  }

  public void clearResults() {
    results.clear();
  }

  @Override
  public String toString() {
    return String.format("ip %s port %s", this.ip, this.port);
  }
}
