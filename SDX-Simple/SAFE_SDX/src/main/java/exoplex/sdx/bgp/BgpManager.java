package exoplex.sdx.bgp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.tuple.ImmutablePair;

public class BgpManager {
  String myPID;
  static int topK = 1;

  public BgpManager(String myPID){
    this.myPID = myPID;
  }

  ConcurrentHashMap <String, ArrayList<RouteAdvertise>> bgpTable = new ConcurrentHashMap<String,
    ArrayList<RouteAdvertise>>();
  ConcurrentHashMap <ImmutablePair<String, String>, ArrayList<RouteAdvertise>> stPairBgpTable = new
    ConcurrentHashMap<>();
  ConcurrentHashMap<ImmutablePair<String, String>, RouteAdvertise> advertisedRoutes = new
    ConcurrentHashMap<>();
  ConcurrentHashMap<ImmutablePair<String, String>, ArrayList<ImmutablePair<RouteAdvertise,
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
    ImmutablePair<String, String> otherKey = new ImmutablePair<>(srcPrefix, destPrefix);
    ArrayList<ImmutablePair<RouteAdvertise, RouteAdvertise>> existingCompliantPairs =
      compliantPairs.getOrDefault(key, new ArrayList<>());
    if(existingCompliantPairs.size() > 0){
      //Do nothing for now
    }else{
      ArrayList<RouteAdvertise> matchedRoutes = stPairBgpTable.getOrDefault(new ImmutablePair<>
        (srcPrefix, destPrefix), new ArrayList<>());
      if(matchedRoutes.size() == 0){
        matchedRoutes.addAll(bgpTable.getOrDefault(srcPrefix, new ArrayList<>()));
      }
      for(RouteAdvertise matchedAdvertise: matchedRoutes){
        String token1 = routeAdvertise.safeToken;
        String token2 = matchedAdvertise.safeToken;
        ArrayList<String> path = new ArrayList<>();
        path.addAll(matchedAdvertise.route);
        Collections.reverse(path);
        path.add(myPID);
        path.addAll(matchedAdvertise.route);
        ArrayList<ImmutablePair<RouteAdvertise, RouteAdvertise>> cpairs = new ArrayList<>();
        if(verifyConjunctPath(routeAdvertise.ownerPID, matchedAdvertise.ownerPID, token1, token2,
          path)){
          cpairs.add(new ImmutablePair<>(routeAdvertise, matchedAdvertise));
        }
        if(cpairs.size() > 0) {
          compliantPairs.put(key, cpairs);
          advertisedRoutes.put(key, routeAdvertise);
          RouteAdvertise propagateAdvertise = new RouteAdvertise(routeAdvertise, myPID);
          newAdvertises.add(propagateAdvertise);

          //get previously advertised route in other direction, if it is not in the  compliant
          // pair, correct the advertisement
          RouteAdvertise otherRoute = advertisedRoutes.get(otherKey);
          boolean compliant = false;
          for(ImmutablePair<RouteAdvertise, RouteAdvertise> pair: cpairs){
            if(pair.getRight().equals(otherRoute)){
              compliant = true;
            }
          }
          if(!compliant){
            RouteAdvertise correctOtherRoute = cpairs.get(0).getRight();
            RouteAdvertise propagateOtherAdvertise = new RouteAdvertise(correctOtherRoute, myPID);
            newAdvertises.add(propagateOtherAdvertise);
          }

        }else{
          if (!advertisedRoutes.containsKey(key)){
            advertisedRoutes.put(key, routeAdvertise);
            RouteAdvertise propagateAdvertise = new RouteAdvertise(routeAdvertise, myPID);
            newAdvertises.add(propagateAdvertise);
          }
        }
      }
    }
    return newAdvertises;
  }

  private boolean verifyConjunctPath(String srcPid, String dstPid, String srcToken, String
    dstToken, ArrayList<String> path){
    return true;
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
