package exoplex.sdx.advertise;

public class PolicyAdvertise extends AdvertiseBase {
  public PolicyAdvertise() {
    super();
  }

  public PolicyAdvertise(PolicyAdvertise advertise, String myPid) {
    super(advertise, myPid);
    this.advertiserPID = myPid;
  }
}
