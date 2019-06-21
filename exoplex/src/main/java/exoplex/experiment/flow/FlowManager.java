package exoplex.experiment.flow;

import exoplex.experiment.task.AsyncTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FlowManager extends AsyncTask {
  final static Logger logger = LogManager.getLogger(FlowManager.class);
  static int DEFAULT_PORT = 5001;
  static int DEFAULT_TIME = 60;
  protected final ArrayList<String[]> iperfClientOut = new ArrayList<>();
  protected final HashMap<String, List<String[]>> iperfServerOut = new HashMap<>();
  HashMap<String, IperfServer> iperfServers = new HashMap<>();
  ArrayList<IperfFlow> flows = new ArrayList<>();
  String sshKey;

  public FlowManager(UUID taskId, Long offSetTime, TimeUnit timeUnit, List<UUID> dependencies,
                     HashMap<UUID, AsyncTask> allTasks,
                     String sshKey) {
    super(taskId, offSetTime, timeUnit, dependencies, allTasks);
    this.sshKey = sshKey;
  }

  public FlowManager(String sshKey) {
    super(UUID.randomUUID(),
      0l,
      TimeUnit.SECONDS,
      new ArrayList<>(),
      new HashMap<>());
    this.sshKey = sshKey;
  }

  public boolean addUdpFlow(String c1, String server, String serverDpIP, String bw, int threads) {
    IperfServer iperfServer = new IperfServer(server, DEFAULT_PORT, IperfServer.UDP, this.sshKey);
    if (iperfServers.containsKey(iperfServer.toString())) {
      if (!iperfServers.get(iperfServer.toString()).transportProto.equals(IperfServer.UDP)) {
        return false;
      }
    }
    iperfServers.put(iperfServer.toString(), iperfServer);
    IperfFlow flow = new IperfFlow(c1, serverDpIP, sshKey, DEFAULT_PORT,
      DEFAULT_TIME, bw, IperfServer.UDP, threads, iperfServer.toString());
    flows.add(flow);
    return true;
  }

  public boolean addTcpFlow(String clientIP, String server, String serverDpIP, String bw,
                            int threads) {
      return addTcpFlow(clientIP, server, serverDpIP, bw, threads, DEFAULT_PORT);
  }

  public boolean addTcpFlow(String clientIP, String server, String serverDpIP, String bw,
                            int threads, int port) {
    logger.info(String.format("Add Tcp Flow: %s %s %s %s", clientIP, server, serverDpIP, threads));
    IperfServer iperfServer = new IperfServer(server, port, IperfServer.TCP, this.sshKey);
    if (iperfServers.containsKey(iperfServer.toString())) {
      if (!iperfServers.get(iperfServer.toString()).transportProto.equals(IperfServer.TCP)) {
        return false;
      }
    }
    iperfServers.put(iperfServer.toString(), iperfServer);
    IperfFlow flow = new IperfFlow(clientIP, serverDpIP, sshKey, port,
        DEFAULT_TIME, bw, IperfServer.TCP, threads, iperfServer.toString());
    flows.add(flow);
    return true;
  }

  public void clearResults() {
    for (IperfServer server : iperfServers.values()) {
      server.clearResults();
    }
    for (IperfFlow flow : flows) {
      flow.clearResults();
    }
  }

  @Override
  public void runTask() {
    startFlows();
  }

  @Override
  public void stop() {
    stopFlows();
  }

  void startFlows() {
    for (IperfServer server : iperfServers.values()) {
      logger.info(String.format("%s start", server.toString()));
      server.start();
    }
    for (IperfFlow flow : flows) {
      logger.info(String.format("%s client start", flow.toString()));
      flow.start();
    }
  }

  public void stopFlows() {
    logger.info("stop flows");
    for (IperfServer server : iperfServers.values()) {
      server.stop();
    }
    for (IperfFlow flow : flows) {
      flow.stop();
    }
  }

  public List<String[]> getClientResults() {
    for (IperfFlow flow : flows) {
      iperfClientOut.addAll(flow.getResults());
    }
    return iperfClientOut;
  }

  public List<String[]> getServerResults() {
    ArrayList<String[]> results = new ArrayList<>();
    for (IperfServer server : iperfServers.values()) {
      iperfServerOut.put(server.toString(), server.getResults());
      results.addAll(server.getResults());
    }
    return results;
  }
}
