package exoplex.sdx.advertise;

import aqt.AreaBasedQuadTree;
import aqt.PrefixUtil;
import aqt.Rectangle;
import exoplex.sdx.safe.SafeManager;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AdvertiseManager {
  final static Logger logger = LogManager.getLogger(AdvertiseManager.class);
  final static String DEFAULT_PREFIX = "0.0.0.0/0";
  static int topK = 1;
  String myPID;
  SafeManager safeManager;
  AreaBasedQuadTree routeIndex = new AreaBasedQuadTree();
  AreaBasedQuadTree policyIndex = new AreaBasedQuadTree();
  ConcurrentHashMap<Rectangle, ArrayList<RouteAdvertise>> routeTable = new ConcurrentHashMap<>();
  ConcurrentHashMap<Rectangle, PolicyAdvertise> policyTable = new ConcurrentHashMap<>();
  ConcurrentHashMap<Rectangle, RouteAdvertise> advertisedRoutes = new ConcurrentHashMap<>();
  ConcurrentHashMap<Rectangle, PolicyAdvertise> advertisedPolicies = new ConcurrentHashMap<>();
  ConcurrentHashMap<ImmutablePair<PolicyAdvertise,
    RouteAdvertise>, Boolean> checkedPairs = new ConcurrentHashMap();

  public AdvertiseManager(String myPID, SafeManager safeManager) {
    this.myPID = myPID;
    this.safeManager = safeManager;
  }

  //TODO: do we need to match for policies here?
  public RouteAdvertise receiveAdvertise(RouteAdvertise routeAdvertise) {
    Rectangle key = PrefixUtil.prefixPairToRectangle(routeAdvertise.destPrefix, DEFAULT_PREFIX);
    routeIndex.insert(key);
    if (routeTable.containsKey(key) &&
      advertisedRoutes.get(key).route.size() <= routeAdvertise.route.size()) {
      //Todo: implement stategy for chosing from multiple advertisements here
      addToBgpTable(key, routeAdvertise);
      return null;
    } else {
      addToBgpTable(key, routeAdvertise);
      advertisedRoutes.put(key, routeAdvertise);
      RouteAdvertise propagateAdvertise = new RouteAdvertise(routeAdvertise, myPID);
      return propagateAdvertise;
    }
  }

  private boolean isCompliant(PolicyAdvertise policyAdvertise, RouteAdvertise
    routeAdvertise) {
    ImmutablePair<PolicyAdvertise, RouteAdvertise> pair =
      new ImmutablePair<>(policyAdvertise, routeAdvertise);
    if(checkedPairs.containsKey(pair)) {
      return checkedPairs.get(pair);
    } else {
      boolean res = safeManager.verifyCompliantPath(policyAdvertise,
        routeAdvertise);
      checkedPairs.put(pair, res);
      return res;
    }
  }

  public synchronized ArrayList<AdvertiseBase> receiveStPolicy(PolicyAdvertise policyAdvertise) {
    ArrayList<AdvertiseBase> newAdvertises = new ArrayList<>();
    newAdvertises.add(new PolicyAdvertise(policyAdvertise, myPID));
    String destPrefix = policyAdvertise.destPrefix;
    String srcPrefix = policyAdvertise.srcPrefix;
    Rectangle key = PrefixUtil.prefixPairToRectangle(destPrefix, srcPrefix);
    addToStPairPolicyTable(key, policyAdvertise);
    policyIndex.insert(key);

    Collection<Rectangle> matchedKeys = routeIndex.query(key);
    for (Rectangle matchedKey : matchedKeys) {
      ArrayList<RouteAdvertise> matchedRoutes = routeTable.get(matchedKey);
      for (RouteAdvertise matchedAdvertise : matchedRoutes) {
        if (isCompliant(policyAdvertise, matchedAdvertise)) {
          Rectangle newKey = key.intersect(matchedKey);
          if (!advertisedRoutes.containsKey(newKey)
            || (advertisedRoutes.get(newKey).srcPrefix == null &&
              matchedAdvertise.srcPrefix != null)
            || advertisedRoutes.get(newKey).length() > matchedAdvertise.length()
            || !isCompliant(policyAdvertise, advertisedRoutes.get(newKey))) {
            newAdvertises.add(new RouteAdvertise(matchedAdvertise, myPID));
            advertisedRoutes.put(newKey, matchedAdvertise);
          }
        }
      }
    }
    return newAdvertises;
  }

  public ArrayList<RouteAdvertise> receiveStAdvertise(RouteAdvertise routeAdvertise) {
    ArrayList<RouteAdvertise> newAdvertises = new ArrayList<>();
    boolean propagate = false;
    String destPrefix = routeAdvertise.destPrefix;
    String srcPrefix = routeAdvertise.srcPrefix;
    Rectangle key = PrefixUtil.prefixPairToRectangle(destPrefix, srcPrefix);
    addToStPairBgpTable(key, routeAdvertise);
    routeIndex.insert(key);
    //find overlapping policies
    ArrayList<ImmutablePair<PolicyAdvertise, RouteAdvertise>> cpairs = new ArrayList<>();
    ArrayList<Rectangle> matchedKeys = new ArrayList<>(policyIndex.query(key));
    if (matchedKeys.size() > 0) {
      Collections.sort(matchedKeys, new Comparator<Rectangle>() {
        @Override
        public int compare(Rectangle o1, Rectangle o2) {
          return (int) (o1.getArea() - o2.getArea());
        }
      });
      //TODO more complex strategy for route policy matching
      for (Rectangle matchedKey : matchedKeys) {
        PolicyAdvertise policyAdvertise = policyTable.get(matchedKey);
        if(isCompliant(policyAdvertise, routeAdvertise)) {
          Rectangle newKey = key.intersect(matchedKey);
          RouteAdvertise advertised = advertisedRoutes.get(newKey);
          if (advertised == null || advertised.srcPrefix == null
            || advertised.length() > routeAdvertise.length()) {
            advertisedRoutes.put(newKey, routeAdvertise);
            propagate = true;
          }
        }
      }
    } else {
      if (!advertisedRoutes.containsKey(key)
        || advertisedRoutes.get(key).length() > routeAdvertise.length()) {
        advertisedRoutes.put(key, routeAdvertise);
        propagate = true;
      }
    }
    if(propagate) {
      RouteAdvertise propagateAdvertise = new RouteAdvertise(routeAdvertise, myPID);
      newAdvertises.add(propagateAdvertise);
    }
    return newAdvertises;
  }

  public RouteAdvertise initAdvertise(String userPid, String destPrefix) {
    RouteAdvertise advertise = new RouteAdvertise();
    advertise.route.add(myPID);
    advertise.advertiserPID = myPID;
    advertise.destPrefix = destPrefix;
    advertise.ownerPID = userPid;
    Rectangle key = PrefixUtil.prefixPairToRectangle(destPrefix, DEFAULT_PREFIX);
    addToBgpTable(key, advertise);
    return advertise;
  }

  private void addToBgpTable(Rectangle key, RouteAdvertise routeAdvertise) {
    if (!routeTable.containsKey(routeAdvertise.destPrefix)) {
      ArrayList<RouteAdvertise> advertises = new ArrayList<>();
      routeTable.put(key, advertises);
    }
    ArrayList<RouteAdvertise> routes = routeTable.get(key);
    synchronized (routes) {
      routes.add(routeAdvertise);
    }
  }

  private void addToStPairBgpTable(Rectangle key, RouteAdvertise routeAdvertise) {
    ArrayList<RouteAdvertise> stLists = routeTable.getOrDefault(key, new ArrayList());
    synchronized (stLists) {
      stLists.add(routeAdvertise);
    }
    routeTable.put(key, stLists);
  }

  private void addToStPairPolicyTable(Rectangle key, PolicyAdvertise policyAdvertise) {
    policyTable.put(key, policyAdvertise);
  }

  public ArrayList<RouteAdvertise> getAllAdvertises() {
    ArrayList<RouteAdvertise> advertises = new ArrayList<>();
    for (Rectangle key : routeTable.keySet()) {
      advertises.add(routeTable.get(key).get(0));
    }
    return advertises;
  }

  public ArrayList<PolicyAdvertise> getAllPolicies() {
    ArrayList<PolicyAdvertise> advertises = new ArrayList<>();
    for (Rectangle key : policyTable.keySet()) {
      advertises.add(policyTable.get(key));
    }
    return advertises;
  }

  public RouteAdvertise getAdvertise(String destPrefix) {
    Rectangle key = PrefixUtil.prefixPairToRectangle(destPrefix, DEFAULT_PREFIX);
    if (routeTable.containsKey(key)) {
      return routeTable.get(key).get(0);
    } else {
      return null;
    }
  }

  public RouteAdvertise getAdvertise(String destPrefix, String srcPrefix) {
    Rectangle key = PrefixUtil.prefixPairToRectangle(destPrefix, srcPrefix);
    if (routeTable.containsKey(key)) {
      return routeTable.get(key).get(0);
    }
    Rectangle key1 = PrefixUtil.prefixPairToRectangle(destPrefix,
      DEFAULT_PREFIX);
    if (routeTable.containsKey(key1)) {
      return routeTable.get(key1).get(0);
    } else {
      return null;
    }
  }

  public RouteAdvertise getStPairAdvertise(String destPrefix, String srcPrefix) {
    Rectangle key = PrefixUtil.prefixPairToRectangle(destPrefix, srcPrefix);
    ArrayList<RouteAdvertise> advertises = routeTable.getOrDefault(key, new ArrayList<>());
    if (advertises.size() > 0) {
      return advertises.get(0);
    } else {
      return null;
    }
  }
}
