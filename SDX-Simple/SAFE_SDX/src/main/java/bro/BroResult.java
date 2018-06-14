package bro;

import java.util.ArrayList;
import java.util.List;

public class BroResult {
  ArrayList<Double> detectionTime;
  ArrayList<Double> cpuUtilization;
  ArrayList<Double> detectionRate;
  ArrayList<Double> packetDropRatio;

  public BroResult() {
    detectionRate = new ArrayList<Double>();
    detectionTime = new ArrayList<Double>();
    cpuUtilization = new ArrayList<Double>();
    packetDropRatio = new ArrayList<Double>();
  }

  public static String getHeader() {
    return "DetectionTime(stdev)  CPU(stdev)  DetectionRate(stdev)  DropRatio(stdev)";
  }

  public void addDetectionTime(Double time) {
    detectionTime.add(time);
  }

  public List<Double> getDetectionTime() {
    return detectionTime;
  }

  public void addDetectionRate(Double rate) {
    detectionRate.add(rate);
  }

  public List<Double> getDetectionRate() {
    return detectionRate;
  }

  public void addCpuUtilization(Double cpu) {
    cpuUtilization.add(cpu);
  }

  public List<Double> getCpuUtilization() {
    return cpuUtilization;
  }

  public void addPacketDropRatio(Double ratio) {
    packetDropRatio.add(ratio);
  }

  public List<Double> getPacketDropRatio() {
    return packetDropRatio;
  }

  public String originalData() {
    String res = "ResponseTime:";
    for (Double t : detectionTime) {
      res = res + " " + t;
    }
    res += "\n DetectionRate:";
    for (Double r : detectionRate) {
      res = res + " " + r;
    }
    res += "\n PacketDropRatio:";
    for (Double r : packetDropRatio) {
      res = res + " " + r;
    }
    res += "\n CPUUtilization:";
    for (Double r : cpuUtilization) {
      res = res + " " + r;
    }
    res += "\n";
    return res;
  }

  public Double[] getStats() {
    Double[] res = {0.0, 0.0, 0.0, 0.0};
    res[0] = getStdev(detectionTime);
    res[1] = getStdev(detectionRate);
    res[2] = getStdev(packetDropRatio);
    res[3] = getStdev(cpuUtilization);
    return res;
  }

  private double getStdev(List<Double> records) {
    if (records.size() == 0) {
      return Double.NaN;
    }
    double val = 0.0;
    for (double t : records) {
      val += t;
    }
    double mean = val / records.size();
    double sqerr = 0.0;
    for (double t : records) {
      sqerr += (t - mean) * (t - mean);
    }
    return Math.sqrt(sqerr / records.size());
  }

  private double getMean(List<Double> records) {
    if (records.size() == 0) {
      return Double.NaN;
    }
    double val = 0.0;
    for (double t : records) {
      val += t;
    }
    double mean = val / records.size();
    return mean;
  }

  @Override
  public String toString() {
    return String.format("%7.3f(%.2f) %7.3f(%.2f) %7.3f(%.4f) %7.3f(%.4f)", getMean
            (detectionTime), getStdev(detectionTime), getMean(cpuUtilization), getStdev(cpuUtilization)
        , getMean(detectionRate), getStdev(detectionRate), getMean(packetDropRatio), getStdev
            (packetDropRatio));
  }
}
