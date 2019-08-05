package exoplex.sdx.core.restutil;

import org.json.JSONObject;

public class StitchResult {
  public boolean result;
  public String gateway;
  public String ip;
  public String safeKeyHash;
  public String reservID;
  public String message;

  public StitchResult() {
  }

  public StitchResult(JSONObject res) {
    this.gateway = res.getString("gateway");
    this.ip = res.getString("ip");
    if (res.has("result")) {
      this.result = res.getBoolean("result");
    } else {
      this.result = false;
    }
    if (res.has("safeKeyHash")) {
      this.safeKeyHash = res.getString("safeKeyHash");
    } else {
      this.safeKeyHash = "";
    }
    if (res.has("reservID")) {
      this.reservID = res.getString("reservID");
    } else {
      this.reservID = "";
    }
    if (!gateway.equals("") && !ip.equals("")) {
      this.result = true;
    } else {
      this.result = false;
    }
    this.message = res.getString("message");
  }
}
