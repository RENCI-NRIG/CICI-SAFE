package exoplex.sdx.bgp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

public class BgpAdvertise {
  public String ownerPID;
  public String prefix;
  public String advertiserPID;
  //use PID to represent AS now
  public ArrayList<String> route;

  public BgpAdvertise(){
    route = new ArrayList<>();
  }

  public BgpAdvertise(BgpAdvertise advertise, String myPid){
    this.ownerPID = advertise.ownerPID;
    this.prefix = advertise.prefix;
    this.advertiserPID = myPid;
    route = new ArrayList<>();
    route.add(myPid);
    route.addAll(advertise.route);
  }

  public String toString(){
    JSONObject obj = new JSONObject();
    obj.put("ownerPID", ownerPID);
    obj.put("prefix", prefix);
    obj.put(advertiserPID, advertiserPID);
    obj.put("route", new JSONArray(route));
    return obj.toString();
  }

  public JSONObject toJsonObject(){
    JSONObject obj = new JSONObject();
    obj.put("ownerPID", ownerPID);
    obj.put("prefix", prefix);
    obj.put("advertiserPID", advertiserPID);
    obj.put("route", new JSONArray(route));
    return obj;
  }
}
