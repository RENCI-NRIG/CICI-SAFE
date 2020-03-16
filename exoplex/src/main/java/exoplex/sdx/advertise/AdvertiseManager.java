package exoplex.sdx.advertise;

import aqt.AreaBasedQuadTree;
import aqt.PrefixUtil;
import aqt.Rectangle;
import exoplex.sdx.safe.SafeManager;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
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
  ConcurrentHashMap<Rectangle, ArrayList<ImmutablePair<PolicyAdvertise,
    RouteAdvertise>>> compliantPairs = new ConcurrentHashMap<>();

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

  public synchronized ArrayList<AdvertiseBase> receiveStPolicy(PolicyAdvertise policyAdvertise) {
    ArrayList<AdvertiseBase> newAdvertises = new ArrayList<>();
    newAdvertises.add(new PolicyAdvertise(policyAdvertise, myPID));
    String destPrefix = policyAdvertise.destPrefix;
    String srcPrefix = policyAdvertise.srcPrefix;
    Rectangle key = PrefixUtil.prefixPairToRectangle(destPrefix, srcPrefix);
    addToStPairPolicyTable(key, policyAdvertise);
    policyIndex.insert(key);
    ArrayList<ImmutablePair<PolicyAdvertise, RouteAdvertise>> existingCompliantPairs =
      compliantPairs.getOrDefault(key, new ArrayList<>());
    if (existingCompliantPairs.size() > 0) {
      //TODO update for new policies
      //Do nothing for now
    } else {
      Collection<Rectangle> matchedKeys = routeIndex.query(key);
      for (Rectangle matchedKey : matchedKeys) {
        ArrayList<RouteAdvertise> matchedRoutes = routeTable.get(matchedKey);
        ArrayList<ImmutablePair<PolicyAdvertise, RouteAdvertise>> cpairs = new ArrayList<>();
        for (RouteAdvertise matchedAdvertise : matchedRoutes) {
          String token1 = policyAdvertise.safeToken;
          String token2 = matchedAdvertise.safeToken;
          RouteAdvertise newAd = new RouteAdvertise(matchedAdvertise, myPID);
          newAd.route.remove(newAd.route.size() - 1);
          String path = newAd.getFormattedPath();
          if (safeManager.verifyCompliantPath(policyAdvertise.ownerPID, policyAdvertise
            .getSrcPrefix(), policyAdvertise.getDestPrefix(), token1, token2, path)) {
            cpairs.add(new ImmutablePair<>(policyAdvertise, matchedAdvertise));
          }
        }
        if (cpairs.size() > 0) {
          compliantPairs.put(key, cpairs);

          //get previously advertised route in other direction, if it is not in the  compliant
          // pair, correct the advertisement
          boolean compliant = false;
          for (ImmutablePair<PolicyAdvertise, RouteAdvertise> pair : cpairs) {
            ImmutablePair<String, String> k = new ImmutablePair<>(destPrefix, pair.getRight()
              .srcPrefix);
            RouteAdvertise otherRoute = advertisedRoutes.getOrDefault(key, null);
            if (pair.getRight().equals(otherRoute)) {
              compliant = true;
            }
          }
          if (!compliant) {
            Collections.sort(cpairs, new Comparator<ImmutablePair<PolicyAdvertise, RouteAdvertise>>() {
              @Override
              public int compare(ImmutablePair<PolicyAdvertise, RouteAdvertise> o1,
                                 ImmutablePair<PolicyAdvertise, RouteAdvertise> o2) {
                return o1.getRight().route.size() - o2.getRight().route.size();
              }
            });
            RouteAdvertise correctOtherRoute = cpairs.get(0).getRight();
            RouteAdvertise propagateOtherAdvertise = new RouteAdvertise(correctOtherRoute, myPID);
            advertisedRoutes.put(key, correctOtherRoute);
            newAdvertises.add(propagateOtherAdvertise);
          }
        } else {
          if (!advertisedPolicies.containsKey(key)) {
            advertisedPolicies.put(key, policyAdvertise);
            PolicyAdvertise propagateAdvertise = new PolicyAdvertise(policyAdvertise, myPID);
            newAdvertises.add(propagateAdvertise);
          }
        }
      }
    }
    return newAdvertises;
  }

  /*
  Find matching advertisements with the received one and return all the routes that need to be
  updated and propagated.
  If there is already an compliant advertisement, check is the new routeAdvertise is compliant
  and choose to update or not
  Else: Check if there is an matching advertisement in the other direction, verify the
  conjunct path
      If no: advertise any path for the src-dst pair

   TODO: sort out the logic
   */
  public ArrayList<RouteAdvertise> receiveStAdvertise(RouteAdvertise routeAdvertise) {
    ArrayList<RouteAdvertise> newAdvertises = new ArrayList<>();
    String destPrefix = routeAdvertise.destPrefix;
    String srcPrefix = routeAdvertise.srcPrefix;
    Rectangle key = PrefixUtil.prefixPairToRectangle(destPrefix, srcPrefix);
    addToStPairBgpTable(key, routeAdvertise);
    routeIndex.insert(key);
    ArrayList<ImmutablePair<PolicyAdvertise, RouteAdvertise>> existingCompliantPairs =
      compliantPairs.getOrDefault(key, new ArrayList<>());
    ArrayList<ImmutablePair<PolicyAdvertise, RouteAdvertise>> cpairs = new ArrayList<>();
    if (existingCompliantPairs.size() > 0) {
      for (ImmutablePair<PolicyAdvertise, RouteAdvertise> pair :
        existingCompliantPairs) {
        if (routeAdvertise.route.size() < pair.getRight().route.size() &&
          (safeManager.verifyCompliantPath(pair.getLeft(), routeAdvertise))) {
            cpairs.add(new ImmutablePair<>(pair.getLeft(), routeAdvertise));
        }
      }
      if(!cpairs.isEmpty()) {
        compliantPairs.put(key, cpairs);
        RouteAdvertise propagateAdvertise = new RouteAdvertise(routeAdvertise, myPID);
        newAdvertises.add(propagateAdvertise);
      }
    } else {
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
          //don't use path containing self because the tag set for self is not linked yet.
          if (safeManager.verifyCompliantPath(policyAdvertise, routeAdvertise)) {
            cpairs.add(new ImmutablePair<PolicyAdvertise, RouteAdvertise>(policyAdvertise, routeAdvertise));
          }
        }
        if (cpairs.size() > 0) {
          compliantPairs.put(key, cpairs);
          if (!routeAdvertise.equals(advertisedRoutes.getOrDefault(key, new RouteAdvertise()))) {
            advertisedRoutes.put(key, routeAdvertise);
            RouteAdvertise propagateAdvertise = new RouteAdvertise(routeAdvertise, myPID);
            newAdvertises.add(propagateAdvertise);
          }
        }
      } else {
        if (!advertisedRoutes.containsKey(key) || advertisedRoutes.get(key).route.size() > routeAdvertise.route.size()) {
          advertisedRoutes.put(key, routeAdvertise);
          RouteAdvertise propagateAdvertise = new RouteAdvertise(routeAdvertise, myPID);
          newAdvertises.add(propagateAdvertise);
        }
      }
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
