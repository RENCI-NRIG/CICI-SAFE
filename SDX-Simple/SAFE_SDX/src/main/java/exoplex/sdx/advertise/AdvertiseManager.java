package exoplex.sdx.advertise;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

import exoplex.sdx.safe.SafeManager;
import org.apache.commons.lang3.tuple.ImmutablePair;

public class AdvertiseManager {
  String myPID;
  static int topK = 1;
  SafeManager safeManager;

  public AdvertiseManager(String myPID, SafeManager safeManager){
    this.myPID = myPID;
    this.safeManager = safeManager;
  }

  ConcurrentHashMap <String, ArrayList<RouteAdvertise>> bgpTable = new ConcurrentHashMap<String,
    ArrayList<RouteAdvertise>>();

  ConcurrentHashMap <String, ArrayList<PolicyAdvertise>> policyTable = new ConcurrentHashMap<String,
    ArrayList<PolicyAdvertise>>();

  ConcurrentHashMap <ImmutablePair<String, String>, ArrayList<RouteAdvertise>> stPairBgpTable = new
    ConcurrentHashMap<>();

  ConcurrentHashMap <ImmutablePair<String, String>, ArrayList<PolicyAdvertise>> stPairPolicyTable =
    new ConcurrentHashMap<>();

  ConcurrentHashMap<ImmutablePair<String, String>, RouteAdvertise> advertisedRoutes = new
    ConcurrentHashMap<>();

  ConcurrentHashMap<ImmutablePair<String, String>, PolicyAdvertise> advertisedPolicies = new
    ConcurrentHashMap<>();

  ConcurrentHashMap<ImmutablePair<String, String>, ArrayList<ImmutablePair<PolicyAdvertise,
    RouteAdvertise>>> compliantPairs = new ConcurrentHashMap<>();

  public RouteAdvertise receiveAdvertise(RouteAdvertise routeAdvertise){
    String destPrefix = routeAdvertise.destPrefix;
    if(bgpTable.containsKey(destPrefix)){
      //Todo: implement stategy for chosing from multiple advertisements here
      addToBgpTable(routeAdvertise);
      return null;
    }else {
      addToBgpTable(routeAdvertise);
      ImmutablePair<String, String> key = new ImmutablePair<>(routeAdvertise.destPrefix, null);
      advertisedRoutes.put(key, routeAdvertise);
      RouteAdvertise propagateAdvertise = new RouteAdvertise(routeAdvertise, myPID);
      return propagateAdvertise;
    }
  }

  public ArrayList<AdvertiseBase> receiveStPolicy(PolicyAdvertise policyAdvertise){
    ArrayList<AdvertiseBase> newAdvertises = new ArrayList<>();
    newAdvertises.add(new PolicyAdvertise(policyAdvertise, myPID));
    String destPrefix = policyAdvertise.destPrefix;
    String srcPrefix = policyAdvertise.srcPrefix;
    addToStPairPolicyTable(policyAdvertise);

    ImmutablePair<String, String> otherKey = new ImmutablePair<>(srcPrefix, destPrefix);
    ImmutablePair<String, String> key = new ImmutablePair<>(srcPrefix, destPrefix);
    ArrayList<ImmutablePair<PolicyAdvertise, RouteAdvertise>> existingCompliantPairs =
      compliantPairs.getOrDefault(key, new ArrayList<>());
    if(existingCompliantPairs.size() > 0){
      //TODO update for new policies
      //Do nothing for now
    }else {
      ArrayList<RouteAdvertise> matchedRoutes = stPairBgpTable.getOrDefault(key, new ArrayList<>());
      //NOTE: This doesn't make sense.
      if (matchedRoutes.size() == 0) {
        matchedRoutes.addAll(bgpTable.getOrDefault(srcPrefix, new ArrayList<>()));
      }
      ArrayList<ImmutablePair<PolicyAdvertise, RouteAdvertise>> cpairs = new ArrayList<>();
      //TODO we only need to veirfy if the route is compliant to the other side's policy
      for (RouteAdvertise matchedAdvertise : matchedRoutes) {
        String token1 = policyAdvertise.safeToken;
        String token2 = matchedAdvertise.safeToken;
        String path = (new RouteAdvertise(matchedAdvertise, myPID)).getFormattedPath();
        if (safeManager.verifyCompliantPath(policyAdvertise.ownerPID, policyAdvertise
            .getSrcPrefix(), policyAdvertise.getDestPrefix(), token1, token2, path)) {
          cpairs.add(new ImmutablePair<>(policyAdvertise, matchedAdvertise));
        }
      }
      //If new compliant pairs found for the src-dest flow, propagate the matched route
      if (cpairs.size() > 0) {
        compliantPairs.put(key, cpairs);

        //get previously advertised route in other direction, if it is not in the  compliant
        // pair, correct the advertisement
        RouteAdvertise otherRoute = advertisedRoutes.get(key);
        boolean compliant = false;
        for (ImmutablePair<PolicyAdvertise, RouteAdvertise> pair : cpairs) {
          if (pair.getRight().equals(otherRoute)) {
            compliant = true;
          }
        }
        if (!compliant) {
          RouteAdvertise correctOtherRoute = cpairs.get(0).getRight();
          RouteAdvertise propagateOtherAdvertise = new RouteAdvertise(correctOtherRoute, myPID);
          newAdvertises.add(propagateOtherAdvertise);
        }

      } else {
        //If no compliant route can be found, propagate routing policies
        if (!advertisedPolicies.containsKey(key)) {
          advertisedPolicies.put(key, policyAdvertise);
          PolicyAdvertise propagateAdvertise = new PolicyAdvertise(policyAdvertise, myPID);
          newAdvertises.add(propagateAdvertise);
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
   */
  public ArrayList<RouteAdvertise> receiveStAdvertise(RouteAdvertise routeAdvertise){
    ArrayList<RouteAdvertise> newAdvertises = new ArrayList<>();
    String destPrefix = routeAdvertise.destPrefix;
    String srcPrefix = routeAdvertise.srcPrefix;
    addToStPairBgpTable(routeAdvertise);

    ImmutablePair<String, String> key = new ImmutablePair<>(destPrefix, srcPrefix);
    ArrayList<ImmutablePair<PolicyAdvertise, RouteAdvertise>> existingCompliantPairs =
      compliantPairs.getOrDefault(key, new ArrayList<>());
    if(existingCompliantPairs.size() > 0){
      //Do nothing for now
    }else {
      ArrayList<PolicyAdvertise> matchedPolicies = stPairPolicyTable.getOrDefault(key,
        new ArrayList<>());
      //NOTE: This doesn't make sense.
      if (matchedPolicies.size() == 0) {
        matchedPolicies.addAll(policyTable.getOrDefault(srcPrefix, new ArrayList<>()));
      }
      ArrayList<ImmutablePair<PolicyAdvertise, RouteAdvertise>> cpairs = new ArrayList<>();
      //TODO we only need to veirfy if the route is compliant to the other side's policy
      for (PolicyAdvertise policyAdvertise : matchedPolicies) {
        String token2 = policyAdvertise.safeToken;
        RouteAdvertise newAd = new RouteAdvertise(routeAdvertise, myPID);
        String path = newAd.getFormattedPath();
        //don't use path containing self because the tag set for self is not linked yet.
        if (safeManager.verifyCompliantPath(routeAdvertise.ownerPID, routeAdvertise.srcPrefix,
          routeAdvertise.destPrefix, token2, routeAdvertise.safeToken,
          routeAdvertise.getFormattedPath())) {
          cpairs.add(new ImmutablePair<PolicyAdvertise, RouteAdvertise>(policyAdvertise,
            routeAdvertise));
        }
      }
      if (cpairs.size() > 0) {
        compliantPairs.put(key, cpairs);
        advertisedRoutes.put(key, routeAdvertise);
        RouteAdvertise propagateAdvertise = new RouteAdvertise(routeAdvertise, myPID);
        newAdvertises.add(propagateAdvertise);

      } else {
        if (!advertisedRoutes.containsKey(key)) {
          advertisedRoutes.put(key, routeAdvertise);
          RouteAdvertise propagateAdvertise = new RouteAdvertise(routeAdvertise, myPID);
          newAdvertises.add(propagateAdvertise);
        }
      }
    }
    return newAdvertises;
  }


  public RouteAdvertise initAdvertise(String userPid, String destPrefix){
    RouteAdvertise advertise = new RouteAdvertise();
    advertise.route.add(myPID);
    advertise.advertiserPID = myPID;
    advertise.destPrefix = destPrefix;
    advertise.ownerPID = userPid;
    addToBgpTable(advertise);
    return advertise;
  }

  public void addToBgpTable(RouteAdvertise routeAdvertise){
    if(!bgpTable.containsKey(routeAdvertise.destPrefix)){
      ArrayList<RouteAdvertise> advertises = new ArrayList<>();
      bgpTable.put(routeAdvertise.destPrefix, advertises);
    }
    bgpTable.get(routeAdvertise.destPrefix).add(routeAdvertise);
  }

  public void addToStPairBgpTable(RouteAdvertise routeAdvertise){
    ImmutablePair<String, String> key = new ImmutablePair<>(routeAdvertise.destPrefix,
      routeAdvertise.srcPrefix);
    ArrayList<RouteAdvertise> stLists = stPairBgpTable.getOrDefault(key, new ArrayList());
    stLists.add(routeAdvertise);
    stPairBgpTable.put(key, stLists);
  }

  public void addToStPairPolicyTable(PolicyAdvertise policyAdvertise){
    ImmutablePair<String, String> key = new ImmutablePair<>(policyAdvertise.destPrefix,
      policyAdvertise.srcPrefix);
    ArrayList<PolicyAdvertise> stLists = stPairPolicyTable.getOrDefault(key, new ArrayList());
    stLists.add(policyAdvertise);
    stPairPolicyTable.put(key, stLists);
  }

  public ArrayList<RouteAdvertise> getAllAdvertises(){
    ArrayList<RouteAdvertise> advertises = new ArrayList<>();
    for(String destPrefix: bgpTable.keySet()){
      advertises.add(bgpTable.get(destPrefix).get(0));
    }
    return advertises;
  }

  public RouteAdvertise getAdvertise(String destPrefix){
    if(bgpTable.containsKey(destPrefix)){
      return bgpTable.get(destPrefix).get(0);
    }else{
      return null;
    }
  }

  public RouteAdvertise getAdvertise(String destPrefix, String srcPrefix){
    ImmutablePair<String, String> key = new ImmutablePair<String, String>(destPrefix, srcPrefix);
    if(stPairBgpTable.containsKey(key)){
      return stPairBgpTable.get(key).get(0);
    }
    if(bgpTable.containsKey(destPrefix)){
      return bgpTable.get(destPrefix).get(0);
    }else{
      return null;
    }
  }

  public RouteAdvertise getStPairAdvertise(String destPrefix, String srcPrefix){
    ArrayList<RouteAdvertise> advertises =  stPairBgpTable.getOrDefault(new ImmutablePair<>
      (destPrefix, srcPrefix), new ArrayList<>());
    if(advertises.size() > 0){
      return advertises.get(0);
    }else{
      return null;
    }
  }
}