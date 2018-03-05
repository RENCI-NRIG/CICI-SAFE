package bro;

import cern.colt.list.adapter.DoubleListAdapter;

import java.util.ArrayList;
import java.util.List;

public class BroResult {
  ArrayList<Double> detectionTime;
  ArrayList<Double> cpuUtilization;
  ArrayList<Double> detectionRate;
  ArrayList<Double> packetDropRatio;

  public BroResult(){
    detectionRate = new ArrayList<Double>();
    detectionTime = new ArrayList<Double>();
    cpuUtilization = new ArrayList<Double>();
    packetDropRatio = new ArrayList<Double>();
  }

  public void addDetectionTime(Double time){
    detectionTime.add(time);
  }

  public List<Double> getDetectionTime(){
    return detectionTime;
  }

  public void addDetectionRate(Double rate){
    detectionRate.add(rate);
  }

  public List<Double> getDetectionRate(){
    return detectionRate;
  }

  public void addCpuUtilization(Double cpu){
    cpuUtilization.add(cpu);
  }

  public List<Double> getCpuUtilization(){
    return cpuUtilization;
  }

  public void addPacketDropRatio(Double ratio){
    packetDropRatio.add(ratio);
  }

  public List<Double> getPacketDropRatio(){
    return packetDropRatio;
  }

  @Override
  public String toString() {
    try {
      double dtime = 0.0;
      for (Double t : detectionTime) {
        dtime += t;
      }
      dtime = dtime / detectionTime.size();

      double cpu = 0.0;
      for (Double t : cpuUtilization) {
        cpu += t;
      }
      cpu = cpu / cpuUtilization.size();

      double detectrate = 0.0;
      for (Double t : detectionRate) {
        detectrate += t;
      }
      detectrate = detectrate / detectionRate.size();

      double droprate = 0.0;
      for (Double t : packetDropRatio) {
        droprate += t;
      }
      droprate = droprate / packetDropRatio.size();
      return String.format("%7.3f %7.3f %7.3f" +
        "%7.3f", dtime, cpu, detectrate, droprate);
    }catch (Exception e){

    }
    return String.format("null null null null");
  }

  public static String getHeader(){
    return "DetectionTime  CPU  DetectionRate  DropRatio";
  }
}
