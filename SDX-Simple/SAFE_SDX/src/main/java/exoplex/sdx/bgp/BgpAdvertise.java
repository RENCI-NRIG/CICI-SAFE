package exoplex.sdx.bgp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

public class BgpAdvertise {
  public String ownerPID;
  public String prefix;
  public String advertiserPID;
  //use PID to represent AS now
  public String safeToken;
  public ArrayList<String> route;

  public BgpAdvertise(){
    route = new ArrayList<>();
  }

  public BgpAdvertise(BgpAdvertise advertise, String myPid){
    this.ownerPID = advertise.ownerPID;
    this.prefix = advertise.prefix;
    this.advertiserPID = myPid;
    this.safeToken = null;
    route = new ArrayList<>();
    route.add(myPid);
    route.addAll(advertise.route);
  }

  public String getLength(){
    return String.valueOf(route.size());
  }

  public String getLength(int i){
    return String.valueOf(route.size() - i);
  }

  public String getPrefix(){
    return String.format("ipv4\\\"%s\\\"", prefix);
  }

  public String getPath(){
    String path = String.join(",",  route);
    return String.format("[%s]", path);
  }
  public String toString(){
    JSONObject obj = new JSONObject();
    obj.put("ownerPID", ownerPID);
    obj.put("prefix", prefix);
    obj.put("adbertiserPID", advertiserPID);
    obj.put("safeToken", safeToken);
    obj.put("route", new JSONArray(route));
    return obj.toString();
  }

  public JSONObject toJsonObject(){
    JSONObject obj = new JSONObject();
    obj.put("ownerPID", ownerPID);
    obj.put("prefix", prefix);
    obj.put("advertiserPID", advertiserPID);
    obj.put("route", new JSONArray(route));
    obj.put("safeToken", safeToken);
    return obj;
  }
}
