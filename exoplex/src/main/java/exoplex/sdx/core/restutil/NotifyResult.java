package exoplex.sdx.core.restutil;

import org.json.JSONObject;

public class NotifyResult {
  public boolean result;
  public String safeKeyHash;
  public String message;

  public NotifyResult() {
    this.result = false;
    this.message = "";
    this.safeKeyHash = "";
  }

  public NotifyResult(JSONObject res) {
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
    this.message = res.getString("message");
  }

  public JSONObject toJsonObject() {
    JSONObject json = new JSONObject();
    json.put("result", this.result);
    json.put("safeKeyHash", this.safeKeyHash);
    return json;
  }
}
