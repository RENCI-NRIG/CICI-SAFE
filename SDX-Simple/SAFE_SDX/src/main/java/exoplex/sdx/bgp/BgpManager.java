package exoplex.sdx.bgp;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

public class BgpManager {
  String myPID;
  static int topK = 1;

  public BgpManager(String myPID){
    this.myPID = myPID;
  }

  ConcurrentHashMap <String, ArrayList<RouteAdvertise>> bgpTable = new ConcurrentHashMap<String,
    ArrayList<RouteAdvertise>>();
  ConcurrentHashMap <String, ConcurrentHashMap<String, ArrayList<RouteAdvertise>>> stPairBgpTable =
    new ConcurrentHashMap();

  public RouteAdvertise receiveAdvertise(RouteAdvertise routeAdvertise){
    String destPrefix = routeAdvertise.destPrefix;
    if(bgpTable.containsKey(destPrefix)){
      //Todo: implement stategy for chosing from multiple advertisements here
      addToBgpTable(routeAdvertise);
      return null;
    }else {
      addToBgpTable(routeAdvertise);
      RouteAdvertise propagateAdvertise = new RouteAdvertise(routeAdvertise, myPID);
      return propagateAdvertise;
    }
  }

  public ArrayList<RouteAdvertise> receiveStAdvertise(RouteAdvertise routeAdvertise){
    ArrayList<RouteAdvertise> newAdvertises = new ArrayList<>();
    String destPrefix = routeAdvertise.destPrefix;
    String srcPrefix = routeAdvertise.srcPrefix;
    addToStPairBgpTable(routeAdvertise);
    ArrayList<RouteAdvertise> existingAdvertises = stPairBgpTable.get(destPrefix)
      .get(srcPrefix);
    for(int i = 0; i < topK; i++){
      newAdvertises.add(new RouteAdvertise(existingAdvertises.get(i), myPID));
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
    stPairBgpTable.getOrDefault(routeAdvertise.destPrefix, new ConcurrentHashMap<>())
      .getOrDefault(routeAdvertise.srcPrefix, new ArrayList<>())
      .add(routeAdvertise);
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
    ArrayList<RouteAdvertise> advertises =  stPairBgpTable.getOrDefault(destPrefix, new
      ConcurrentHashMap<>())
      .getOrDefault(srcPrefix, new ArrayList<>());
    if(advertises.size() > 0){
      return advertises.get(0);
    }else{
      return null;
    }
  }
}
