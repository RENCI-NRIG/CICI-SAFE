package exoplex.sdx.bro;

import exoplex.common.slice.SafeSlice;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import exoplex.sdx.core.SdxManager;
import exoplex.sdx.network.RoutingManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;


public class BroManager {
  final Logger logger = LogManager.getLogger(BroManager.class);
  static final long singleBroCap = 500000000;
  private final ReentrantLock ticketLock = new ReentrantLock();
  SafeSlice slice = null;
  RoutingManager networkManager = null;
  SdxManager sdxManager = null;
  private HashMap<String, Long> requiredbw = new HashMap<>();
  private HashMap<String, ArrayList<BroInstance>> routerBroMap = new HashMap<>();
  private LinkedList<BroFlow> jobQueue = new LinkedList<BroFlow>();
  private HashMap<String, Long> tickedBroCapacity = new HashMap<>();

  public BroManager(SafeSlice slice, RoutingManager networkManager, SdxManager sdxManager) {
    this.slice = slice;
    this.networkManager = networkManager;
    this.sdxManager = sdxManager;
  }

  public void reset() {
    for(String key:requiredbw.keySet()){
      requiredbw.put(key, 0l);
    }
    for(ArrayList<BroInstance> broInstances: routerBroMap.values()) {
      for (BroInstance bro : broInstances) {
        bro.removeAllBroFlows();
      }
    }
  }

  public void addBroInstance(String edgeRouter, String ip, long cap) {
    ArrayList<BroInstance> broInstances = routerBroMap.getOrDefault(edgeRouter, new ArrayList<>());
    broInstances.add(new BroInstance(ip, cap));
    routerBroMap.put(edgeRouter, broInstances);
  }

  private void deployNewBro(String routerName) throws Exception{
    Thread thread = new Thread() {
      @Override
      public void run() {
        try {
          ticketLock.lock();
          tickedBroCapacity.put(routerName, tickedBroCapacity.getOrDefault(routerName, 0l) +
            singleBroCap);
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
            tickedBroCapacity.put(routerName, tickedBroCapacity.getOrDefault(routerName, 0l) -
              singleBroCap);
            ticketLock.unlock();
          } catch (Exception ex) {
            ex.printStackTrace();
          }
        }
        synchronized (routerBroMap) {
          addBroInstance(routerName, ip, singleBroCap);
        }
        try {
          ticketLock.lock();
          tickedBroCapacity.put(routerName, tickedBroCapacity.getOrDefault(routerName, 0l) -
            singleBroCap);
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
      ArrayList<BroFlow> toRemove = new ArrayList<>();
      for (BroFlow f : jobQueue) {
        BroInstance bro = getBroInstance(f.bw, f.routerName);
        if (bro != null) {
          String dpid = sdxManager.getDPID(f.routerName);
          String gw = bro.getIP();
          bro.addBroFlow(f);
          String res = networkManager.setMirror(sdxManager.getSDNController(), dpid, f.src, f.dst, gw);
          res += networkManager.setMirror(sdxManager.getSDNController(), dpid, f.dst, f.src, gw);
          logger.info("job: " + f.src + " " + f.dst + " " + f.bw + ": \n" + res);
          toRemove.add(f);
        }
      }
      for (BroFlow f : toRemove) {
        jobQueue.remove(f);
      }
    }
    for(String router: requiredbw.keySet()) {
      if (broOverloaded(router)) {
        deployNewBro(router);
      }
    }
  }

  private BroInstance getBroInstance(long bw, String routerName) {
    //First fit
    BroInstance broNode = null;
    synchronized (routerBroMap) {
      for (BroInstance bro : routerBroMap.getOrDefault(routerName, new ArrayList<>())) {
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
            broInstances.add(new BroInstance(ip, singleBroCap));
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

  private boolean broOverloaded(String routerName) {
    long sum = 0;
    try {
      ticketLock.lock();
      sum = tickedBroCapacity.getOrDefault(routerName, 0l);
      ticketLock.unlock();
    } catch (Exception e) {
      e.printStackTrace();
    }
    for (BroInstance bro : routerBroMap.getOrDefault(routerName, new ArrayList<>())) {
      sum += bro.capacity;
    }
    double ratio = (double) requiredbw.getOrDefault(routerName, 0l) / (double) sum;
    if (ratio > 0.6) {
      return true;
    } else {
      return false;
    }
  }

  public void setMirrorAsync(String routerName, String source, String dst, long bw) throws  Exception{
    requiredbw.put(routerName, requiredbw.getOrDefault(routerName, 0l));
    synchronized (jobQueue) {
      jobQueue.offer(new BroFlow(source, dst, bw, routerName));
    }
    execJobs();
  }
}
