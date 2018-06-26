package sdx.bro;

import common.slice.SafeSlice;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.renci.ahab.libtransport.util.TransportException;
import sdx.core.SdxManager;
import sdx.networkmanager.NetworkManager;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

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
    if (flow.bw > capacity - usedCap) {
      return false;
    } else {
      usedCap += flow.bw;
      this.flows.add(flow);
      return true;
    }
  }

  public void removeAllFlows() {
    this.flows.clear();
    usedCap = 0;
  }
}

class Flow {
  public String src;
  public String dst;
  public long bw;
  public String routerName;

  public Flow(String s, String d, long b, String router) {
    this.src = s;
    this.dst = d;
    this.bw = b;
    this.routerName = router;
  }
}


public class BroManager {
  final Logger logger = LogManager.getLogger(BroManager.class);
  private final ReentrantLock ticketLock = new ReentrantLock();
  SafeSlice slice = null;
  NetworkManager networkManager = null;
  SdxManager sdxManager = null;
  private long requiredbw = 0;
  private ArrayList<BroInstance> broInstances = new ArrayList<>();
  private LinkedList<Flow> jobQueue = new LinkedList<Flow>();
  private long tickedBroCapacity = 0;

  public BroManager(SafeSlice slice, NetworkManager networkManager, SdxManager sdxManager) {
    this.slice = slice;
    this.networkManager = networkManager;
    this.sdxManager = sdxManager;
  }

  public void reset() {
    requiredbw = 0;
    for (BroInstance bro : broInstances) {
      bro.removeAllFlows();
    }
  }

  public void addBroInstance(String ip, long cap) {
    broInstances.add(new BroInstance(ip, cap));
  }

  private void deployNewBro(String routerName) throws Exception{
    Thread thread = new Thread() {
      @Override
      public void run() {
        try {
          ticketLock.lock();
          tickedBroCapacity += 500000000;
          ticketLock.unlock();
        } catch (Exception e) {
          e.printStackTrace();
        }
        String ip = null;
        try {
          ip = sdxManager.deployBro(routerName);
        } catch (Exception e) {
          e.printStackTrace();
          try {
            ticketLock.lock();
            tickedBroCapacity -= 500000000;
            ticketLock.unlock();
          } catch (Exception ex) {
            ex.printStackTrace();
          }
        }
        synchronized (broInstances) {
          broInstances.add(new BroInstance(ip, 500000000));
        }
        try {
          ticketLock.lock();
          tickedBroCapacity -= 500000000;
          ticketLock.unlock();
        } catch (Exception e) {
          e.printStackTrace();
        }
        try {
          execJobs();
        }catch (Exception e){
          e.printStackTrace();
        }
      }
    };
    thread.start();
  }

  private void execJobs() throws Exception{
    synchronized (jobQueue) {
      ArrayList<Flow> toRemove = new ArrayList<>();
      for (Flow f : jobQueue) {
        BroInstance bro = getBroInstance(f.bw, f.routerName);
        if (bro != null) {
          String dpid = sdxManager.getDPID(f.routerName);
          String gw = bro.getIP();
          bro.addFlow(f);
          String res = networkManager.setMirror(sdxManager.getSDNController(), dpid, f.src, f.dst, gw);
          res += networkManager.setMirror(sdxManager.getSDNController(), dpid, f.dst, f.src, gw);
          logger.info("job: " + f.src + " " + f.dst + " " + f.bw + ": \n" + res);
          toRemove.add(f);
        }
      }
      for (Flow f : toRemove) {
        jobQueue.remove(f);
      }
    }
    if (broOverloaded()) {
      deployNewBro("e0");
    }
  }

  private BroInstance getBroInstance(long bw, String routerName) {
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
    return broNode;
    /*
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
    */
  }

  private boolean broOverloaded() {
    long sum = 0;
    try {
      ticketLock.lock();
      sum = tickedBroCapacity;
      ticketLock.unlock();
    } catch (Exception e) {
      e.printStackTrace();
    }
    for (BroInstance bro : broInstances) {
      sum += bro.capacity;
    }
    double ratio = (double) requiredbw / (double) sum;
    if (ratio > 0.6) {
      return true;
    } else {
      return false;
    }
  }

  public void setMirrorAsync(String routerName, String source, String dst, long bw) throws  Exception{
    requiredbw += bw;
    synchronized (jobQueue) {
      jobQueue.offer(new Flow(source, dst, bw, routerName));
    }
    execJobs();
  }
}
