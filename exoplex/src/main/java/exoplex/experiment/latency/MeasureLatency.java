package exoplex.experiment.latency;

import exoplex.common.utils.Exec;
import exoplex.experiment.task.AsyncTask;
import exoplex.experiment.task.TaskInterface;
import exoplex.sdx.slice.SliceProperties;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class MeasureLatency extends AsyncTask implements TaskInterface {

  static Logger logger = LogManager.getLogger(MeasureLatency.class);

  String sshKey;

  String managementIP;

  String targetIP;

  int periodMilliSeconds = 1000;

  int timeSpan = 30;

  int totalTime = -1;

  ArrayList<ImmutablePair<Long, Float>> latencyResults = new ArrayList<>();

  public MeasureLatency(String managementIP, String targetIP, String sshKey) {
    super(UUID.randomUUID(),
      0l,
      TimeUnit.SECONDS,
      new ArrayList<>(),
      new HashMap<>());
    this.managementIP = managementIP;
    this.targetIP = targetIP;
    this.sshKey = sshKey;
  }

  public MeasureLatency(UUID taskId, Long offSetTime, TimeUnit timeUnit, List<UUID> dependencies,
                        HashMap<UUID, AsyncTask> allTasks,
                        String managementIP,
                        String targetIP,
                        String sshKey) {
    super(taskId, offSetTime, timeUnit, dependencies, allTasks);
    this.managementIP = managementIP;
    this.targetIP = targetIP;
    this.sshKey = sshKey;
  }

  public void setPeriod(int periodMilliSeconds) {
    this.periodMilliSeconds = periodMilliSeconds;
  }

  public void setTotalTime(int timeSeconds) {
    this.totalTime = timeSeconds;
  }

  public void runTask() {
    Long currentTime = System.currentTimeMillis();
    if (totalTime < 0) {
      //run until stopped
      String[] res = Exec.sshExec(SliceProperties.userName, managementIP,
        String.format("ping -i %s %s",
        periodMilliSeconds / 1000.0, targetIP), sshKey);
      parseResults(res[0], currentTime);
    } else {
      String[] res = Exec.sshExec(SliceProperties.userName, managementIP,
        String.format("ping -i %s -w %s %s",
        periodMilliSeconds / 1000.0, totalTime, targetIP), sshKey);
      parseResults(res[0], currentTime);
    }
  }

  @Override
  public void stop() {
    Exec.sshExec(SliceProperties.userName, managementIP, "sudo pkill ping", sshKey);
  }

  private void parseResults(String result, Long currentTime) {
    String[] results = result.split("\n");
    Long time = currentTime;
    if (results.length > 1) {
      for (int i = 1; i < results.length; i++, time += periodMilliSeconds) {
        String res = results[i];
        if (res.contains("time=")) {
          try {
            Float rtt = Float.parseFloat(res.split("time=")[1].split(" ")[0]);
            latencyResults.add(new ImmutablePair<Long, Float>(time, rtt));
          } catch (Exception e) {
          }
        }
      }
    }
  }

  public void printResults() {
    for (ImmutablePair<Long, Float> pair : latencyResults) {
      logger.info(String.format("%s  %s ms", pair.getLeft(), pair.getRight()));
    }
  }
}

