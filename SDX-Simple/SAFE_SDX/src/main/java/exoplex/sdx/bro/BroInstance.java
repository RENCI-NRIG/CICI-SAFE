package exoplex.sdx.bro;

import java.util.ArrayList;
import java.util.List;

public class BroInstance {
  long capacity;
  long usedCap;
  String ip;
  ArrayList<BroFlow> flows;

  public BroInstance(String ip, long cap) {
    this.capacity = cap;
    this.usedCap = 0;
    this.ip = ip;
    this.flows = new ArrayList<>();
  }

  public String getIP() {
    return this.ip;
  }

  public List<BroFlow> getBroFlows() {
    return this.flows;
  }

  public long getAvailableCap() {
    return capacity - usedCap;
  }

  public boolean addBroFlow(BroFlow flow) {
    if (flow.bw > capacity - usedCap) {
      return false;
    } else {
      usedCap += flow.bw;
      this.flows.add(flow);
      return true;
    }
  }

  public void removeAllBroFlows() {
    this.flows.clear();
    usedCap = 0;
  }
}

