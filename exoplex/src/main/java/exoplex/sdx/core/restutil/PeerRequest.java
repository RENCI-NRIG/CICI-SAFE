package exoplex.sdx.core.restutil;

import org.json.JSONObject;

public class PeerRequest {
  public String peerUrl;
  public String peerPID;

  public PeerRequest() {
    peerPID = "";
    peerUrl = "";
  }

  public PeerRequest(String json) {
    JSONObject obj = new JSONObject(json);
    peerUrl = obj.getString("peerUrl");
    peerPID = obj.getString("peerPID");
  }

  public JSONObject toJsonObject() {
    JSONObject jsonObject = new JSONObject();
    jsonObject.put("peerUrl", peerUrl);
    jsonObject.put("peerPID", peerPID);
    return jsonObject;
  }

  @Override
  public String toString() {
    JSONObject obj = new JSONObject();
    obj.put("peerUrl", peerUrl);
    obj.put("peerPID", peerPID);
    return obj.toString();
  }
}
