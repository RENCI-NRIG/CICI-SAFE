package exoplex.sdx.advertise;

import org.json.JSONObject;

import java.util.Objects;

public class RouteAdvertise extends AdvertiseBase {
  public String srcPid;

  public RouteAdvertise() {
    super();
  }

  public RouteAdvertise(RouteAdvertise advertise, String myPid) {
    super(advertise, myPid);
    this.srcPid = advertise.advertiserPID;
    this.advertiserPID = myPid;
  }

  public int length() {
    return route.size();
  }

  @Override
  public JSONObject toJsonObject() {
    JSONObject obj = super.toJsonObject();
    obj.put("srcPid", srcPid);
    return obj;
  }

  @Override
  public String toString() {
    //return toJsonObject().toString();
    return String.format("src: %s dst: %s route: [%s]", srcPrefix, destPrefix,
      String.join(",", route));
  }

  @Override
  public boolean equals(Object route) {
    RouteAdvertise routeAdvertise = (RouteAdvertise) route;
    if(!routeAdvertise.ownerPID.equals(this.ownerPID) ||
      !routeAdvertise.srcPid.equals(this.srcPid) ||
      !routeAdvertise.destPrefix.equals(this.destPrefix) ||
      !routeAdvertise.advertiserPID.equals(this.advertiserPID)
    ) {
      return false;
    }
    if((this.safeToken == null && routeAdvertise.safeToken != null)
      || (this.safeToken != null && routeAdvertise.safeToken == null)) {
      return false;
    }
    if((this.srcPrefix == null && routeAdvertise.srcPrefix != null)
      || (this.srcPrefix != null && routeAdvertise.srcPrefix == null)) {
      return false;
    }
    if(this.safeToken != null && routeAdvertise.safeToken != null
      && !this.safeToken.equals(routeAdvertise.safeToken)) {
      return false;
    }
    if(this.route.size() != routeAdvertise.route.size()) {
      return false;
    }
    for(int i = 0; i < this.route.size(); i ++) {
      if(!this.route.get(i).equals(routeAdvertise.route.get(i))) {
        return false;
      }
    }
    return true;
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.ownerPID, this.srcPid, this.advertiserPID,
      this.srcPrefix, this.destPrefix, String.join(",", this.route));
  }
}
