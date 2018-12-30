package exoplex.sdx.core;

import exoplex.sdx.bgp.BgpAdvertise;
import org.apache.jena.atlas.json.JSON;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;

/**
 * Root resource (exposed at "myresource" path)
 *
 * Call with curl:
 * curl -X POST -H "Content-Type: application/json" -d '{"operation":"stitch", "params":["1","2",
 * "3"]}' http://localhost:8880/sdx/admin
 */
@Path("sdx")
public class RestService {
  final static Logger logger = LogManager.getLogger(RestService.class);

  @POST
  @Path("/admin")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.TEXT_PLAIN)
  public String processAdminCmd(@Context UriInfo uriInfo, AdminCmd cmd) {
    logger.debug(uriInfo.getBaseUri());
    logger.debug(String.format("got sittch request %s", cmd));
    SdxManager sdxManager = SdxServer.sdxManagerMap.get(uriInfo.getBaseUri().getPort());
    try {
      return sdxManager.adminCmd(cmd.operation, cmd.params);
    } catch (Exception e) {
      e.printStackTrace();
      return e.getMessage();
    }
  }

  @POST
  @Path("/bgp")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.TEXT_PLAIN)
  public String receiveBgpAdvertise(@Context UriInfo uriInfo, BgpAdvertise bgpAdvertise) {
    logger.debug(uriInfo.getBaseUri());
    logger.debug(String.format("got bgp advertisement %s", bgpAdvertise));
    SdxManager sdxManager = SdxServer.sdxManagerMap.get(uriInfo.getBaseUri().getPort());
    try {
      return sdxManager.processBgpAdvertise(bgpAdvertise);
    } catch (Exception e) {
      e.printStackTrace();
      return e.getMessage();
    }
  }

  @POST
  @Path("/peer")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public PeerRequest peer(@Context UriInfo uriInfo, PeerRequest peerRequest) {
    logger.debug(uriInfo.getBaseUri());
    logger.debug(String.format("got peer request %s", peerRequest));
    SdxManager sdxManager = SdxServer.sdxManagerMap.get(uriInfo.getBaseUri().getPort());
    try {
      return sdxManager.processPeerRequest(peerRequest);
    } catch (Exception e) {
      e.printStackTrace();
      return new PeerRequest();
    }
  }

  @POST
  @Path("/stitchrequest")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public StitchResult stitchRequest(@Context UriInfo uriInfo, StitchRequest sr) {
    logger.debug(String.format("got sittch request %s", sr));
    SdxManager sdxManager = SdxServer.sdxManagerMap.get(uriInfo.getBaseUri().getPort());
    try {
      JSONObject res = sdxManager.stitchRequest(sr.sdxsite, sr.ckeyhash, sr.cslice,
          sr.creservid, sr.secret, sr.sdxnode, sr.gateway, sr.ip);
      return new StitchResult(res);
    } catch (Exception e) {
      e.printStackTrace();
      return new StitchResult();
    }
  }

  @POST
  @Path("/undostitch")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.TEXT_PLAIN)
  public String undoStitch(@Context UriInfo uriInfo, UndoStitchRequest sr) {
    logger.debug("got undoStitch request ");
    SdxManager sdxManager = SdxServer.sdxManagerMap.get(uriInfo.getBaseUri().getPort());
    try {
      String res = sdxManager.undoStitch(sr.ckeyhash, sr.cslice,
          sr.creservid);
      return res;
    } catch (Exception e) {
      e.printStackTrace();
      return String.format("UndoStitch Failed: %s", e.getMessage());
    }
  }

  @POST
  @Path("/connectionrequest")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.TEXT_PLAIN)
  public String connectionRequest(@Context UriInfo uriInfo, ConnectionRequest sr) {
    logger.debug("got link request between " + sr.self_prefix + " and " + sr.target_prefix);
    SdxManager sdxManager = SdxServer.sdxManagerMap.get(uriInfo.getBaseUri().getPort());
    try {
      String res = sdxManager.connectionRequest(sr.ckeyhash, sr.self_prefix,
          sr.target_prefix, sr.bandwidth);
      return res;
    } catch (Exception e) {
      e.printStackTrace();
      return e.getMessage();
    }
  }

  @POST
  @Path("/stitchchameleon")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.TEXT_PLAIN)
  public String stitchChameleon(@Context UriInfo uriInfo, StitchChameleon sr) {
    logger.debug("got chameleon stitch request: \n" + sr.toString());
    SdxManager sdxManager = SdxServer.sdxManagerMap.get(uriInfo.getBaseUri().getPort());
    String res = sdxManager.stitchChameleon(sr.sdxsite, sr.sdxnode,
        sr.ckeyhash, sr.stitchport, sr.vlan, sr.gateway, sr.ip);
    return res;
  }

  @POST
  @Path("/notifyprefix")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public NotifyResult notifyPrefix(@Context UriInfo uriInfo, PrefixNotification pn) {
    logger.debug("got notifyprefix");
    SdxManager sdxManager = SdxServer.sdxManagerMap.get(uriInfo.getBaseUri().getPort());
    NotifyResult res = sdxManager.notifyPrefix(pn.dest, pn.gateway, pn.customer);
    return res;
  }

  @POST
  @Path("/broload")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.TEXT_PLAIN)
  public String broload(@Context UriInfo uriInfo, BroLoad bl)  {
    logger.debug("got broload");
    SdxManager sdxManager = SdxServer.sdxManagerMap.get(uriInfo.getBaseUri().getPort());
    double load = Double.parseDouble(bl.usage);
    String res = null;
    try {
      res = sdxManager.broload(bl.broip, load);
    } catch (Exception e) {
      e.printStackTrace();
      res = "Failed to get Bro Load";
      logger.warn(res);
    }
    return res;
  }
}

/*
Admin command, in routing scenerio, the sdx can work as a client to peer with other sdx networks.
SDX server provides this interface for administrator to issue commands for peering.
 */
class AdminCmd{
  public String operation;
  public String[] params;

  public String toString(){
    String parameters= String.join(",", params);
    return String.format("{\"operation\":\"%s\", \"params:\":[%s]}",operation, parameters);
  }
}

class StitchChameleon {
  public String sdxsite;
  public String sdxnode;
  public String ckeyhash;
  public String stitchport;
  public String vlan;
  public String gateway;
  public String ip;

  public String toString() {
    return "{\"sdxsite\": " + sdxsite + ", \"sdxnode\": " + sdxnode + ", \"ckeyhash\":" + ckeyhash
      + ", \"stitchport\":" + stitchport + ", \"vlan\":" + vlan + "\"gateway\":" + gateway + "}";
  }
}

class StitchRequest {
  public String sdxsite;
  //customer Safe key hash
  public String gateway;
  public String ip;
  public String ckeyhash;
  public String cslice;
  public String creservid;
  public String sdxnode;
  public String secret;

  @Override
  public String toString(){
    return String.format("%s %s %s %s %s %s", sdxsite, cslice, creservid, sdxnode, gateway, ip);
  }
}

class UndoStitchRequest {
  public String ckeyhash;
  public String cslice;
  public String creservid;
  @Override
  public String toString(){
    return String.format("%s%s %s", ckeyhash, cslice, creservid);
  }
}

class PeerRequest {
  public String peerUrl;
  public String peerPID;

  public PeerRequest(){
    peerPID = "";
    peerUrl = "";
  }

  public PeerRequest(String json){
    JSONObject obj = new JSONObject(json);
    peerUrl = obj.getString("peerUrl");
    peerPID = obj.getString("peerPID");
  }

  public JSONObject toJsonObject(){
    JSONObject jsonObject = new JSONObject();
    jsonObject.put("peerUrl", peerUrl);
    jsonObject.put("peerPID", peerPID);
    return jsonObject;
  }

  @Override
  public String toString(){
    JSONObject obj = new JSONObject();
    obj.put("peerUrl", peerUrl);
    obj.put("peerPID", peerPID);
    return obj.toString();
  }
}

class ConnectionRequest {
  public String ckeyhash;
  public String self_prefix;
  public String target_prefix;
  public long bandwidth;
}

class PrefixNotification {
  public String dest;
  public String gateway;
  public String customer;
}

class BroLoad {
  public String broip;
  public String usage;

  public String toString() {
    return "{\"broip\": " + broip + ", \"usage\": " + usage + "}";
  }
}

class StitchResult {
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
    }else{
      this.result = false;
    }
    if(res.has("safeKeyHash")){
      this.safeKeyHash = res.getString("safeKeyHash");
    }else{
      this.safeKeyHash = "";
    }
    if(res.has("reservID")){
      this.reservID = res.getString("reservID");
    }else{
      this.reservID = "";
    }
    if (!gateway.equals("") && !ip.equals(""))
      result = true;
    else
      result = false;
    this.message = res.getString("message");
  }
}


class NotifyResult {
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
    }else{
      this.result = false;
    }
    if(res.has("safeKeyHash")){
      this.safeKeyHash = res.getString("safeKeyHash");
    }else{
      this.safeKeyHash = "";
    }
    this.message = res.getString("message");
  }

  public JSONObject toJsonObject(){
    JSONObject json = new JSONObject();
    json.put("result", this.result);
    json.put("safeKeyHash", this.safeKeyHash);
    return json;
  }
}
