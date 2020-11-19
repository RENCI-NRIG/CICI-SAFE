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

  static PolicyAdvertise defaultPolicy() {
    PolicyAdvertise policyAdvertise = new PolicyAdvertise();
    policyAdvertise.srcPrefix = AdvertiseManager.DEFAULT_PREFIX;
    policyAdvertise.destPrefix = AdvertiseManager.DEFAULT_PREFIX;
    return policyAdvertise;
  }

  @Override
  public boolean equals(Object route) {
    PolicyAdvertise routeAdvertise = (PolicyAdvertise) route;
    if(!routeAdvertise.ownerPID.equals(this.ownerPID) ||
      !routeAdvertise.destPrefix.equals(this.destPrefix)
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
    return true;
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.ownerPID, this.advertiserPID,
      this.srcPrefix, this.destPrefix, String.join(",", this.route));
  }

  @Override
  public String toString() {
    //return toJsonObject().toString();
    return String.format("src: %s dst: %s", srcPrefix, destPrefix);
  }
}
