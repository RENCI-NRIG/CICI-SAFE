package exoplex.sdx.advertise;

import java.util.Objects;

public class PolicyAdvertise extends AdvertiseBase {
  public PolicyAdvertise() {
    super();
  }

  public PolicyAdvertise(PolicyAdvertise advertise, String myPid) {
    super(advertise, myPid);
    this.advertiserPID = myPid;
  }

  @Override
  public boolean equals(Object route) {
    RouteAdvertise routeAdvertise = (RouteAdvertise) route;
    if(!routeAdvertise.ownerPID.equals(this.ownerPID) ||
      !routeAdvertise.srcPrefix.equals(this.srcPrefix) ||
      !routeAdvertise.destPrefix.equals(this.destPrefix)
    ) {
      return false;
    }
    if((this.safeToken == null && routeAdvertise.safeToken != null)
      || (this.safeToken != null && routeAdvertise.safeToken == null)) {
      return false;
    }
    if(this.safeToken != null && routeAdvertise.safeToken != null
      && !this.safeToken.equals(routeAdvertise.safeToken)) {
      return false;
    }
    return true;
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.ownerPID, this.advertiserPID,
      this.srcPrefix, this.destPrefix, String.join(",", this.route));
  }
}
