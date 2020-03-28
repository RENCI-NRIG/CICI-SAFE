package exoplex.sdx.advertise;

import aqt.AreaBasedQuadTree;
import aqt.PrefixUtil;
import aqt.Rectangle;
import com.google.inject.internal.cglib.core.$ProcessArrayCallback;
import exoplex.sdx.safe.SafeManager;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.appender.routing.Route;

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
  ConcurrentHashMap<Rectangle, RouteAdvertise> chosenRoutes = new ConcurrentHashMap<>();
  ConcurrentHashMap<Rectangle, PolicyAdvertise> advertisedPolicies = new ConcurrentHashMap<>();
  ConcurrentHashMap<ImmutablePair<PolicyAdvertise,
    RouteAdvertise>, Boolean> checkedPairs = new ConcurrentHashMap();
  HashSet<RouteAdvertise> advertisedRoutes = new HashSet<>();

  public AdvertiseManager(String myPID, SafeManager safeManager) {
    this.myPID = myPID;
    this.safeManager = safeManager;
  }

  public String getMyPID() {
    return myPID;
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

  public synchronized ImmutablePair<List<RouteAdvertise>,
    List<AdvertiseBase>> receiveStPolicy(PolicyAdvertise policyAdvertise) {
    ImmutablePair<List<RouteAdvertise>, List<AdvertiseBase>> newPair =
      new ImmutablePair<>(new ArrayList<>(), new ArrayList<>());
    List<RouteAdvertise> configRoutes = newPair.getLeft();
    List<AdvertiseBase> newAdvertises = newPair.getRight();
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
          if (!chosenRoutes.containsKey(newKey)
            || (chosenRoutes.get(newKey).srcPrefix == null &&
              matchedAdvertise.srcPrefix != null)
            || chosenRoutes.get(newKey).length() > matchedAdvertise.length()
            || !isCompliant(policyAdvertise, chosenRoutes.get(newKey))) {
            chosenRoutes.put(newKey, matchedAdvertise);
            RouteAdvertise configRoute = new RouteAdvertise(matchedAdvertise);
            configRoute.srcPrefix = srcPrefix;
            configRoutes.add(configRoute);
            if(!advertisedRoutes.contains(matchedAdvertise)) {
              newAdvertises.add(matchedAdvertise);
            }
          }
        }
      }
    }
    return newPair;
  }

  //TODO: The logic is about the same as receiveStAdvertise, consider mering
  /**
   * When receiving a route advertisement, two possible actions:
   * 1. install routing flows with both source and destination specified
   * 2. install destination only routing flows.
   * But we only propagate is once.
   * @param routeAdvertise
   * @return
   */
  public ImmutablePair<List<RouteAdvertise>, List<RouteAdvertise>>
    receiveAdvertise(RouteAdvertise routeAdvertise) {
    ImmutablePair<List<RouteAdvertise>, List<RouteAdvertise>> ret =
      new ImmutablePair<>(new ArrayList<>(), new ArrayList<>());
    List<RouteAdvertise> configRoutes = ret.getLeft();
    List<RouteAdvertise> propagateRoutes = ret.getRight();
    Rectangle key = PrefixUtil.prefixPairToRectangle(routeAdvertise.destPrefix, DEFAULT_PREFIX);
    routeIndex.insert(key);
    addToBgpTable(key, routeAdvertise);
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
          RouteAdvertise advertised = chosenRoutes.get(newKey);
          if (advertised == null
            || advertised.length() > routeAdvertise.length()) {
            chosenRoutes.put(newKey, routeAdvertise);
            RouteAdvertise newAdvertise = new RouteAdvertise(routeAdvertise);
            newAdvertise.srcPrefix = policyAdvertise.srcPrefix;
            configRoutes.add(newAdvertise);
            if(!advertisedRoutes.contains(routeAdvertise)) {
              propagateRoutes.add(routeAdvertise);
              advertisedRoutes.add(routeAdvertise);
            }
          }
        }
      }
    }

    if (!chosenRoutes.containsKey(key)
        || chosenRoutes.get(key).length() > routeAdvertise.length()) {
      chosenRoutes.put(key, routeAdvertise);
      configRoutes.add(routeAdvertise);
      if(!advertisedRoutes.contains(routeAdvertise)) {
        propagateRoutes.add(routeAdvertise);
        advertisedRoutes.add(routeAdvertise);
      }
    }
    return ret;
  }

  public RouteAdvertise receiveStAdvertise(RouteAdvertise routeAdvertise) {
    boolean propagate = false;
    String destPrefix = routeAdvertise.destPrefix;
    String srcPrefix = routeAdvertise.srcPrefix;
    Rectangle key = PrefixUtil.prefixPairToRectangle(destPrefix, srcPrefix);
    addToBgpTable(key, routeAdvertise);
    routeIndex.insert(key);
    //find overlapping policies
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
          RouteAdvertise advertised = chosenRoutes.get(newKey);
          if (advertised == null || advertised.srcPrefix == null
            || advertised.length() > routeAdvertise.length()) {
            chosenRoutes.put(newKey, routeAdvertise);
            propagate = true;
          }
        }
      }
    } else {
      if (!chosenRoutes.containsKey(key)
        || chosenRoutes.get(key).length() > routeAdvertise.length()) {
        chosenRoutes.put(key, routeAdvertise);
        propagate = true;
      }
    }
    if(propagate) {
      return routeAdvertise;
    } else {
      return null;
    }
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
    ArrayList<RouteAdvertise> advertises = routeTable.computeIfAbsent(key,
      k-> new ArrayList<>());
    advertises.add(routeAdvertise);
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
