package exoplex.experiment.flow;

import exoplex.common.utils.Exec;
import exoplex.experiment.task.AsyncTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import exoplex.sdx.slice.SliceProperties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class IperfFlow extends AsyncTask {
  final static Logger logger = LogManager.getLogger(IperfFlow.class);
  static String baseCmd = "/usr/bin/iperf";
  String iperfServer;
  String managementIp;
  String serverIp;
  int port;
  int seconds;
  String bw;
  String proto;
  String sshKey;
  int threads;
  ArrayList<String[]> results = new ArrayList<>();

  public IperfFlow(String clientIp, String serverIp, String sshKey, int port, int seconds, String
    bw, String proto, int threads, String iperfServer) {
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
    this.threads = threads;
    started = false;
  }

  @Override
  public void runTask() {
    String cmd = baseCmd;
    cmd = cmd + " -c " + serverIp;
    if (proto.equals(IperfServer.UDP)) {
      cmd = cmd + " -u";
      cmd = cmd + " -b " + bw;
    }
    if (seconds > 0) {
      cmd = cmd + " -t " + seconds;
    }
    if (this.threads > 1) {
      cmd = cmd + " -P " + this.threads;
    }
    cmd = cmd + " -p " + this.port;
    logger.info(cmd);
    String[] res = Exec.sshExec(SliceProperties.userName, managementIp, cmd, sshKey);
    logger.info(res[0]);
    logger.info(res[1]);
    results.add(res);
  }

  public void stop() {
    lock.lock();
    if (started) {
      Exec.sshExec(SliceProperties.userName, managementIp, "pkill iperf", sshKey);
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
}
