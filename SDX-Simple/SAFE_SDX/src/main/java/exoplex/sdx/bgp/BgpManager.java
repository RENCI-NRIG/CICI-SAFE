package exoplex.sdx.bgp;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

public class BgpManager {
  String myPID;

  public BgpManager(String myPID){
    this.myPID = myPID;
  }

  ConcurrentHashMap <String, ArrayList<BgpAdvertise>> bgpTable = new ConcurrentHashMap<String,
    ArrayList<BgpAdvertise>>();

  public BgpAdvertise receiveAdvertise(BgpAdvertise bgpAdvertise){
    String prefix = bgpAdvertise.prefix;
    if(bgpTable.containsKey(prefix)){
      //Todo: implement stategy for chosing from multiple advertisements here
      addToBgpTable(bgpAdvertise);
      return null;
    }else {
      addToBgpTable(bgpAdvertise);
      BgpAdvertise propagateAdvertise = new BgpAdvertise(bgpAdvertise, myPID);
      return propagateAdvertise;
    }
  }

  public BgpAdvertise initAdvertise(String userPid, String prefix){
    BgpAdvertise advertise = new BgpAdvertise();
    advertise.route.add(myPID);
    advertise.advertiserPID = myPID;
    advertise.prefix = prefix;
    advertise.ownerPID = userPid;
    addToBgpTable(advertise);
    return advertise;
  }

  public void addToBgpTable(BgpAdvertise bgpAdvertise){
    if(!bgpTable.containsKey(bgpAdvertise.prefix)){
      ArrayList<BgpAdvertise> advertises = new ArrayList<>();
      bgpTable.put(bgpAdvertise.prefix, advertises);
    }
    bgpTable.get(bgpAdvertise.prefix).add(bgpAdvertise);
  }

  public ArrayList<BgpAdvertise> getAllAdvertises(){
    ArrayList<BgpAdvertise> advertises = new ArrayList<>();
    for(String prefix: bgpTable.keySet()){
      advertises.add(bgpTable.get(prefix).get(0));
    }
    return advertises;
  }

  public BgpAdvertise getAdvertise(String prefix){
    if(bgpTable.containsKey(prefix)){
      return bgpTable.get(prefix).get(0);
    }else{
      return null;
    }
  }
}
