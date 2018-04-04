package sdx.core.bro;

import java.util.ArrayList;
import java.util.List;
import org.apache.log4j.Logger;
import org.renci.ahab.libndl.Slice;

import sdx.core.SdxManager;
import sdx.networkmanager.NetworkManager;

class BroInstance {
  long capacity;
  long usedCap;
  String ip;
  ArrayList<Flow> flows;
  public BroInstance(String ip, long cap) {
    this.capacity = cap;
    this.usedCap = 0;
    this.ip = ip;
    this.flows = new ArrayList<>();
  }

  public String getIP() {
    return this.ip;
  }

  public List<Flow> getFlows() {
    return this.flows;
  }

  public long getAvailableCap() {
    return capacity - usedCap;
  }

  public boolean addFlow(Flow flow) {
    if(flow.bw > capacity - usedCap) {
      return false;
    } else {
      usedCap += flow.bw;
      this.flows.add(flow);
      return true;
    }
  }

  public void removeAllFlows(){
    this.flows.clear();
    usedCap = 0;
  }
}

class Flow {
  public String src;
  public String dst;
  public long bw;
  public Flow(String s, String d, long b){
    this.src = s;
    this.dst = d;
    this.bw = b;
  }
}


public class BroManager {
  final Logger logger = Logger.getLogger(BroManager.class);
  Slice slice = null;
  NetworkManager networkManager = null;
  SdxManager sdxManager = null;
  private long requiredbw=0;
  private ArrayList<BroInstance> broInstances = new ArrayList<>();

  public BroManager(Slice slice, NetworkManager networkManager, SdxManager sdxManager){
    this.slice = slice;
    this.networkManager = networkManager;
    this.sdxManager = sdxManager;
  }

  public void reset(){
    requiredbw = 0;
    for (BroInstance bro : broInstances) {
      bro.removeAllFlows();
    }
  }

  public void addBroInstance(String ip, long cap){
    broInstances.add(new BroInstance(ip, cap));
  }


  public BroInstance getBroInstance(long bw, String routerName) {
    //First fit
    BroInstance broNode = null;
    synchronized (broInstances) {
      for (BroInstance bro : broInstances) {
        if (bro.getAvailableCap() > bw) {
          broNode = bro;
          break;
        }
      }
    }
    //checkif necessary to provision new Bro node
    if (broNode == null || broOverloaded()) {
      Thread thread = new Thread() {
        @Override
        public void run() {
          String ip = sdxManager.deployBro(routerName);
          synchronized (broInstances) {
            broInstances.add(new BroInstance(ip, 500000000));
          }
        }
      };
      thread.start();
      if (broNode == null) {
        try {
          System.out.println("No enough capacity in active bro pool, waiting for new bro nodes to" +
            " be deployed");
          thread.join();
          synchronized (broInstances) {
            for (BroInstance bro : broInstances) {
              if (bro.getAvailableCap() > bw) {
                broNode = bro;
                break;
              }
            }
          }
        } catch (Exception e) {
          e.printStackTrace();
        }
        return broNode;
      } else {
        return broNode;
      }
    } else {
      return broNode;
    }
  }

  private boolean broOverloaded() {
    long sum = 0;
    for(BroInstance bro: broInstances){
      sum += bro.capacity;
    }
    double ratio = (double)requiredbw / (double) sum;
    if (ratio > 0.6){
      return true;
    }else{
      return false;
    }
  }

  public String setMirror(String routerName, String source, String dst, long bw){
    requiredbw += bw;
    BroInstance bro = getBroInstance(bw, routerName);
    String dpid = sdxManager.getDPID(routerName);
    String gw = bro.getIP();
    bro.addFlow(new Flow(source, dst, bw));
    String res = networkManager.setMirror(sdxManager.getSDNController(), dpid, source, dst, gw);
    res += networkManager.setMirror(sdxManager.getSDNController(), dpid, dst, source, gw);
    return res;
  }
}
