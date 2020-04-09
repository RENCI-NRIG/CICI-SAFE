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
  final public static String DEFAULT_PREFIX = "0.0.0.0/0";
  static int topK = 1;
  String myPID;
  SafeManager safeManager;
  AreaBasedQuadTree routeIndex;
  AreaBasedQuadTree outboundPolicyIndex;
  AreaBasedQuadTree matchedIndex;
  ConcurrentHashMap<Rectangle, HashSet<RouteAdvertise>> routeTable;
  ConcurrentHashMap<Rectangle, PolicyAdvertise> outboundPolicyTable;
  ConcurrentHashMap<Rectangle, RouteAdvertise> chosenRoutes;
  ConcurrentHashMap<Rectangle, PolicyAdvertise> advertisedPolicies;
  ConcurrentHashMap<ImmutablePair<PolicyAdvertise,
    RouteAdvertise>, Boolean> checkedPairs;
  HashSet<RouteAdvertise> advertisedRoutes;
  ConcurrentHashMap<Rectangle, ImmutablePair<Rectangle, Rectangle>> matchesMap;
  ConcurrentHashMap<ImmutablePair<String, String>, String> fib;

  public AdvertiseManager(String myPID, SafeManager safeManager) {
    this.myPID = myPID;
    this.safeManager = safeManager;
    routeIndex = new AreaBasedQuadTree();
    outboundPolicyIndex = new AreaBasedQuadTree();
    matchedIndex = new AreaBasedQuadTree();
    routeTable = new ConcurrentHashMap<>();
    outboundPolicyTable = new ConcurrentHashMap<>();
    chosenRoutes = new ConcurrentHashMap<>();
    advertisedPolicies = new ConcurrentHashMap<>();
    checkedPairs = new ConcurrentHashMap();
    advertisedRoutes = new HashSet<>();
    matchesMap = new ConcurrentHashMap();
    Rectangle defaultRect = PrefixUtil.prefixPairToRectangle(DEFAULT_PREFIX,
      DEFAULT_PREFIX);
    outboundPolicyIndex.insert(defaultRect);
    outboundPolicyTable.put(defaultRect, PolicyAdvertise.defaultPolicy());
    fib = new ConcurrentHashMap<>();
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

  /**
   * TODO: for chosen routes, we need to register both policy advertisement
   * and route advertisement. So we can determine the priority of the current
   * policy and the previous one.An example that the current logic doesn't work.
   * Existing route: <src: 1.1.1.0/24, dst: 2.2.2.0/24>
   * Existing policy: <src: 1.1.0.0/16, dst:*>
   * New policy: <src = 1.1.1.0/24, dst * >
   * In this case, the key of chosen route is 1.1.1.0/24, 2.2.2.0/24
   * And the advertised route has src not null,
   * @param policyAdvertise
   * @return
   */
  public synchronized ImmutablePair<List<ForwardInfo>,
    List<AdvertiseBase>> receiveOutboundPolicy(PolicyAdvertise policyAdvertise) {
    ImmutablePair<List<ForwardInfo>, List<AdvertiseBase>> newPair =
      new ImmutablePair<>(new ArrayList<>(), new ArrayList<>());
    List<ForwardInfo> configRoutes = newPair.getLeft();
    List<AdvertiseBase> newAdvertises = newPair.getRight();
    newAdvertises.add(new PolicyAdvertise(policyAdvertise, myPID));
    String srcPrefix = policyAdvertise.srcPrefix;
    String destPrefix = policyAdvertise.destPrefix;
    Rectangle key = PrefixUtil.prefixPairToRectangle(destPrefix, srcPrefix);
    addOutboundPairPolicyTable(key, policyAdvertise);
    outboundPolicyIndex.insert(key);

    ArrayList<Rectangle> matchedKeys = (ArrayList<Rectangle>) routeIndex.query(key);
    Collections.sort(matchedKeys, (o1, o2) -> o2.compareInbound(o1));
    for (Rectangle matchedKey : matchedKeys) {
      Rectangle newKey = key.intersect(matchedKey);
      ImmutablePair<Rectangle, Rectangle> oldPair =
        matchesMap.getOrDefault(newKey, null);
      if(oldPair == null || (key.compareOutbound(oldPair.getLeft()) >= 0)
        && matchedKey.compareInbound(oldPair.getRight()) >= 0) {
        matchedIndex.insert(newKey);
        matchesMap.put(newKey, new ImmutablePair<>(key, matchedKey));
        ArrayList<RouteAdvertise> matchedRoutes =
          new ArrayList<>(routeTable.get(matchedKey));
        Collections.sort(matchedRoutes, (o1, o2) -> o1.length() - o2.length());
        for (RouteAdvertise matchedAdvertise : matchedRoutes) {
          if (isCompliant(policyAdvertise, matchedAdvertise)) {
            chosenRoutes.put(newKey, matchedAdvertise);
            addFib(configRoutes, new ForwardInfo(
              getOverlapPrefix(srcPrefix, matchedAdvertise.srcPrefix),
              getOverlapPrefix(destPrefix, matchedAdvertise.destPrefix),
              matchedAdvertise.advertiserPID
            ));
            if (!advertisedRoutes.contains(matchedAdvertise)) {
              advertisedRoutes.add(matchedAdvertise);
              newAdvertises.add(matchedAdvertise);
            }
            break;
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
  /*
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
   */

  public ImmutablePair<List<ForwardInfo>, RouteAdvertise> receiveAdvertise(RouteAdvertise routeAdvertise) {
    String destPrefix = routeAdvertise.destPrefix;
    String srcPrefix = routeAdvertise.srcPrefix == null? DEFAULT_PREFIX:
      routeAdvertise.srcPrefix;
    List<ForwardInfo> configRoutes = new ArrayList<>();
    Rectangle key = PrefixUtil.prefixPairToRectangle(destPrefix, srcPrefix);
    addToBgpTable(key, routeAdvertise);
    routeIndex.insert(key);
    RouteAdvertise forwardRoute = null;
    //find overlapping policies
    ArrayList<Rectangle> matchedKeys = new ArrayList<>(outboundPolicyIndex.query(key));
    if (matchedKeys.size() > 0) {
      Collections.sort(matchedKeys, (o1, o2) -> o2.compareOutbound(o1));
    }
    for (Rectangle matchedKey : matchedKeys) {
      Rectangle newKey = key.intersect(matchedKey);
      ImmutablePair<Rectangle, Rectangle> oldPair =
        matchesMap.getOrDefault(newKey, null);
      if (oldPair == null || (matchedKey.compareOutbound(oldPair.getLeft()) >= 0)
        && key.compareInbound(oldPair.getRight()) >= 0) {
        matchedIndex.insert(newKey);
        matchesMap.put(newKey, new ImmutablePair<>(matchedKey, key));
        PolicyAdvertise policyAdvertise = outboundPolicyTable.get(matchedKey);
        if (isCompliant(policyAdvertise, routeAdvertise)) {
          if (oldPair == null || key.compareInbound(oldPair.getRight()) > 0
            || !chosenRoutes.containsKey(newKey)
            || routeAdvertise.length() < chosenRoutes.get(newKey).length()) {
            chosenRoutes.put(newKey, routeAdvertise);
            addFib(configRoutes, new ForwardInfo(
              getOverlapPrefix(routeAdvertise.srcPrefix,
                policyAdvertise.srcPrefix),
              getOverlapPrefix(routeAdvertise.destPrefix,
                policyAdvertise.destPrefix),
              routeAdvertise.advertiserPID));
            if (!advertisedRoutes.contains(routeAdvertise)) {
              advertisedRoutes.add(routeAdvertise);
              forwardRoute = routeAdvertise;
            }
          }
        }
      }
    }
    return new ImmutablePair<>(configRoutes, forwardRoute);
  }

  String getOverlapPrefix(String prefix1, String prefix2) {
    if(prefix1 == null) {
      return prefix2;
    }
    if(prefix2 == null) {
      return prefix1;
    }
    int len1 = Integer.valueOf(prefix1.split("/")[1]);
    int len2 = Integer.valueOf(prefix2.split("/")[1]);
    if (len1 < len2) {
      return prefix2;
    } else {
      return prefix1;
    }
  }

  void addAdvertise(List<AdvertiseBase> advertiseList,
                    RouteAdvertise routeAdvertise) {
    if(!advertisedRoutes.contains(routeAdvertise)) {
      advertisedRoutes.add(routeAdvertise);
      advertiseList.add(routeAdvertise);
    }
  }

  void addFib(List<ForwardInfo> forwardInfoList, ForwardInfo forwardInfo) {
    ImmutablePair<String, String> key =
      new ImmutablePair<>(forwardInfo.srcPrefix, forwardInfo.destPrefix);
    if(!fib.containsKey(key) || !fib.get(key).equals(forwardInfo.neighborPid)) {
      forwardInfoList.add(forwardInfo);
      fib.put(key, forwardInfo.neighborPid);
    }
  }

  public String logFib() {
    String res = "";
    for(ImmutablePair<String, String> key: fib.keySet()) {
      res = String.format(String.format("%s src: %s dst: %s nexthop: %s\n",
        res, key.getLeft(), key.getRight(), fib.get(key)));
    }
    return res;
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
    HashSet<RouteAdvertise> advertises = routeTable.computeIfAbsent(key,
      k-> new HashSet<>());
    advertises.add(routeAdvertise);
  }

  private void addOutboundPairPolicyTable(Rectangle key, PolicyAdvertise policyAdvertise) {
    outboundPolicyTable.put(key, policyAdvertise);
  }

  public ArrayList<RouteAdvertise> getAllAdvertises() {
    ArrayList<RouteAdvertise> advertises = new ArrayList<>();
    for (Rectangle key : routeTable.keySet()) {
      advertises.addAll(routeTable.get(key));
    }
    return advertises;
  }

  public ArrayList<PolicyAdvertise> getAllPolicies() {
    ArrayList<PolicyAdvertise> advertises = new ArrayList<>();
    for (Rectangle key : outboundPolicyTable.keySet()) {
      advertises.add(outboundPolicyTable.get(key));
    }
    return advertises;
  }

  public RouteAdvertise getAdvertise(String destPrefix) {
    Rectangle key = PrefixUtil.prefixPairToRectangle(destPrefix, DEFAULT_PREFIX);
    if (routeTable.containsKey(key)) {
      return routeTable.get(key).iterator().next();
    } else {
      return null;
    }
  }

  public RouteAdvertise getAdvertise(String destPrefix, String srcPrefix) {
    Rectangle key = PrefixUtil.prefixPairToRectangle(destPrefix, srcPrefix);
    if (routeTable.containsKey(key)) {
      return routeTable.get(key).iterator().next();
    }
    Rectangle key1 = PrefixUtil.prefixPairToRectangle(destPrefix,
      DEFAULT_PREFIX);
    if (routeTable.containsKey(key1)) {
      return routeTable.get(key1).iterator().next();
    } else {
      return null;
    }
  }
}
