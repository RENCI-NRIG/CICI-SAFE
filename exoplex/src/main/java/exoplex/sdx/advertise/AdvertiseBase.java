package exoplex.sdx.advertise;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

//TODO: differentiate between policy advertisement and route advertisement
public class AdvertiseBase {
  public String ownerPID;
  public String destPrefix;
  public String srcPrefix;
  public String advertiserPID;
  //use PID to represent AS now
  public String safeToken;
  //use route for destination address based route
  public ArrayList<String> route;

  //<dest, src>-> ArrayList<String>

  public AdvertiseBase() {
    route = new ArrayList<>();
    this.ownerPID = null;
    this.destPrefix = null;
    this.srcPrefix = null;
    this.advertiserPID = null;
    this.safeToken = null;
  }

  public AdvertiseBase(AdvertiseBase advertise, String myPid) {
    this.ownerPID = advertise.ownerPID;
    this.destPrefix = advertise.destPrefix;
    this.srcPrefix = advertise.srcPrefix;
    //Set the safe token to the previous one first, for authorizing neighbor ASes. May update later.
    this.safeToken = advertise.safeToken;
    route = new ArrayList<>();
    route.add(myPid);
    route.addAll(advertise.route);
  }

  public AdvertiseBase(AdvertiseBase advertise) {
    this.ownerPID = advertise.ownerPID;
    this.destPrefix = advertise.destPrefix;
    this.srcPrefix = advertise.srcPrefix;
    //Set the safe token to the previous one first, for authorizing neighbor ASes. May update later.
    this.safeToken = advertise.safeToken;
    route = new ArrayList<>();
    route.addAll(advertise.route);
  }

  public String getLength() {
    return String.valueOf(route.size());
  }

  public String getLength(int i) {
    return String.valueOf(route.size() - i);
  }

  public Boolean hasSrcPrefix() {
    return srcPrefix != null && !srcPrefix.equals("");
  }

  public String getDestPrefix() {
    return String.format("ipv4\\\"%s\\\"", destPrefix);
  }

  public String getSrcPrefix() {
    if (srcPrefix != null) {
      return String.format("ipv4\\\"%s\\\"", srcPrefix);
    } else {
      return null;
    }
  }

  public String getFormattedPath() {
    return getFormattedPath(route);
  }

  public static String getFormattedPath(List<String> routeList) {
    String path = String.join(",", routeList);
    return String.format("[%s]", path);
  }

  public String toString() {
    return toJsonObject().toString();
  }

  public JSONObject toJsonObject() {
    JSONObject obj = new JSONObject();
    obj.put("srcPrefix", srcPrefix);
    obj.put("destPrefix", destPrefix);
    obj.put("ownerPID", ownerPID);
    obj.put("advertiserPID", advertiserPID);
    obj.put("route", new JSONArray(route));
    obj.put("safeToken", safeToken);
    return obj;
  }

  @Override
  public boolean equals(Object routeAdvertise) {
    if (routeAdvertise == null) {
      return false;
    }
    if (!this.ownerPID.equals(((AdvertiseBase) routeAdvertise).ownerPID)) {
      return false;
    }
    if (!this.destPrefix.equals(((AdvertiseBase) routeAdvertise).destPrefix)) {
      return false;
    }
    if (this.srcPrefix != null && !this.srcPrefix.equals(((AdvertiseBase) routeAdvertise).srcPrefix)) {
      return false;
    }
    if (this.srcPrefix == null && ((AdvertiseBase) routeAdvertise).srcPrefix != null) {
      return false;
    }
    if (!this.advertiserPID.equals(((AdvertiseBase) routeAdvertise).advertiserPID)) {
      return false;
    }
    if (this.safeToken != null && !this.safeToken.equals(((AdvertiseBase) routeAdvertise).safeToken)) {
      return false;
    }
    if (this.route.size() != ((AdvertiseBase) routeAdvertise).route.size()) {
      return false;
    }
    for (int i = 0; i < route.size(); i++) {
      if (!this.route.get(i).equals(((AdvertiseBase) routeAdvertise).route.get(i))) {
        return false;
      }
    }
    return true;
  }
}
