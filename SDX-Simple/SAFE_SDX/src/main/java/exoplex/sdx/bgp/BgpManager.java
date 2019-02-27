package exoplex.sdx.bgp;

import sun.reflect.misc.ConstructorUtil;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

public class BgpManager {
  String myPID;
  static int topK = 1;

  public BgpManager(String myPID){
    this.myPID = myPID;
  }

  ConcurrentHashMap <String, ArrayList<BgpAdvertise>> bgpTable = new ConcurrentHashMap<String,
    ArrayList<BgpAdvertise>>();
  ConcurrentHashMap <String, ConcurrentHashMap<String, ArrayList<BgpAdvertise>>> stPairBgpTable =
    new ConcurrentHashMap();

  public BgpAdvertise receiveAdvertise(BgpAdvertise bgpAdvertise){
    String destPrefix = bgpAdvertise.destPrefix;
    if(bgpTable.containsKey(destPrefix)){
      //Todo: implement stategy for chosing from multiple advertisements here
      addToBgpTable(bgpAdvertise);
      return null;
    }else {
      addToBgpTable(bgpAdvertise);
      BgpAdvertise propagateAdvertise = new BgpAdvertise(bgpAdvertise, myPID);
      return propagateAdvertise;
    }
  }

  public ArrayList<BgpAdvertise> receiveStAdvertise(BgpAdvertise bgpAdvertise){
    ArrayList<BgpAdvertise> newAdvertises = new ArrayList<>();
    String destPrefix = bgpAdvertise.destPrefix;
    String srcPrefix = bgpAdvertise.srcPrefix;
    addToStPairBgpTable(bgpAdvertise);
    ArrayList<BgpAdvertise> existingAdvertises = stPairBgpTable.get(destPrefix)
      .get(srcPrefix);
    for(int i = 0; i < topK; i++){
      newAdvertises.add(new BgpAdvertise(existingAdvertises.get(i), myPID));
    }
    return newAdvertises;
  }

  public BgpAdvertise initAdvertise(String userPid, String destPrefix){
    BgpAdvertise advertise = new BgpAdvertise();
    advertise.route.add(myPID);
    advertise.advertiserPID = myPID;
    advertise.destPrefix = destPrefix;
    advertise.ownerPID = userPid;
    addToBgpTable(advertise);
    return advertise;
  }

  public void addToBgpTable(BgpAdvertise bgpAdvertise){
    if(!bgpTable.containsKey(bgpAdvertise.destPrefix)){
      ArrayList<BgpAdvertise> advertises = new ArrayList<>();
      bgpTable.put(bgpAdvertise.destPrefix, advertises);
    }
    bgpTable.get(bgpAdvertise.destPrefix).add(bgpAdvertise);
  }

  public void addToStPairBgpTable(BgpAdvertise bgpAdvertise){
    stPairBgpTable.getOrDefault(bgpAdvertise.destPrefix, new ConcurrentHashMap<>())
      .getOrDefault(bgpAdvertise.srcPrefix, new ArrayList<>())
      .add(bgpAdvertise);
  }

  public ArrayList<BgpAdvertise> getAllAdvertises(){
    ArrayList<BgpAdvertise> advertises = new ArrayList<>();
    for(String destPrefix: bgpTable.keySet()){
      advertises.add(bgpTable.get(destPrefix).get(0));
    }
    return advertises;
  }

  public BgpAdvertise getAdvertise(String destPrefix){
    if(bgpTable.containsKey(destPrefix)){
      return bgpTable.get(destPrefix).get(0);
    }else{
      return null;
    }
  }

  public BgpAdvertise getStPairAdvertise(String destPrefix, String srcPrefix){
    ArrayList<BgpAdvertise> advertises =  stPairBgpTable.getOrDefault(destPrefix, new
      ConcurrentHashMap<>())
      .getOrDefault(srcPrefix, new ArrayList<>());
    if(advertises.size() > 0){
      return advertises.get(0);
    }else{
      return null;
    }
  }
}
