package exoplex.sdx.bgp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

public class RouteAdvertise{
  public String ownerPID;
  public String destPrefix;
  public String srcPrefix;
  public String advertiserPID;
  //use PID to represent AS now
  public String safeToken;
  //use route for destination address based route
  public ArrayList<String> route;

  //<dest, src>-> ArrayList<String>

  public RouteAdvertise(){
    route = new ArrayList<>();
  }

  public RouteAdvertise(RouteAdvertise advertise, String myPid){
    this.ownerPID = advertise.ownerPID;
    this.destPrefix = advertise.destPrefix;
    this.srcPrefix = advertise.srcPrefix;
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

  public Boolean hasSrcPrefix(){
    return srcPrefix != null && !srcPrefix.equals("");
  }

  public String getDestPrefix(){
    return String.format("ipv4\\\"%s\\\"", destPrefix);
  }

  public String getsrcPrefix(){
    if(srcPrefix!= null) {
      return String.format("ipv4\\\"%s\\\"", destPrefix);
    }else{
      return null;
    }
  }

  public String getPath(){
    String path = String.join(",",  route);
    return String.format("[%s]", path);
  }
  public String toString(){
    JSONObject obj = new JSONObject();
    obj.put("ownerPID", ownerPID);
    obj.put("destPrefix", destPrefix);
    obj.put("srcPrefix", srcPrefix);
    obj.put("adbertiserPID", advertiserPID);
    obj.put("safeToken", safeToken);
    obj.put("route", new JSONArray(route));
    return obj.toString();
  }

  public JSONObject toJsonObject(){
    JSONObject obj = new JSONObject();
    obj.put("ownerPID", ownerPID);
    obj.put("destPrefix", destPrefix);
    obj.put("srcPrefix", srcPrefix);
    obj.put("advertiserPID", advertiserPID);
    obj.put("route", new JSONArray(route));
    obj.put("safeToken", safeToken);
    return obj;
  }

  @Override
  public boolean equals(Object routeAdvertise){
    if(! (routeAdvertise instanceof RouteAdvertise)){
      return false;
    }
    if(!this.ownerPID.equals(((RouteAdvertise) routeAdvertise).ownerPID)){
      return false;
    }
    if(!this.destPrefix.equals(((RouteAdvertise) routeAdvertise).destPrefix)){
      return false;
    }
    if(this.srcPrefix!= null && !this.srcPrefix.equals(((RouteAdvertise) routeAdvertise).srcPrefix)){
      return false;
    }
    if(this.srcPrefix == null && ((RouteAdvertise) routeAdvertise).srcPrefix!= null){
      return false;
    }
    if(!this.advertiserPID.equals(((RouteAdvertise) routeAdvertise).advertiserPID)){
      return false;
    }
    if(this.safeToken != null && !this.safeToken.equals(((RouteAdvertise) routeAdvertise).safeToken)){
      return false;
    }
    if(this.route.size() != ((RouteAdvertise) routeAdvertise).route.size()){
      return false;
    }
    for(int i=0; i< route.size(); i++){
      if(this.route.get(i).equals(((RouteAdvertise) routeAdvertise).route.get(i))){
        return  false;
      }
    }
    return true;
  }
}
