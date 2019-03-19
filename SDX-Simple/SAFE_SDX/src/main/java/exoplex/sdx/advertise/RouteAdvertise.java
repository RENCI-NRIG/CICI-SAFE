package exoplex.sdx.advertise;

import org.json.JSONObject;

public class RouteAdvertise extends AdvertiseBase{
  public String srcPid;

  public RouteAdvertise(){
    super();
  }

  public RouteAdvertise(RouteAdvertise advertise, String myPid){
    super(advertise, myPid);
    this.srcPid = advertise.advertiserPID;
    this.advertiserPID = myPid;
  }

  @Override
  public JSONObject toJsonObject(){
    JSONObject obj = super.toJsonObject();
    obj.put("srcPid", srcPid);
    return obj;
  }

  @Override
  public String toString() {
    return toJsonObject().toString();
  }
}
