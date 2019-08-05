package exoplex.sdx.core;

import exoplex.sdx.advertise.PolicyAdvertise;
import exoplex.sdx.advertise.RouteAdvertise;
import exoplex.sdx.core.restutil.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.grizzly.http.server.HttpServer;
import org.json.JSONObject;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Root resource (exposed at "myresource" path)
 * <p>
 * Call with curl:
 * curl -X POST -H "Content-Type: application/json" -d '{"operation":"stitch", "params":["1","2",
 * "3"]}' http://localhost:8880/sdx/admin
 */
@Path("sdx")
public class RestService {
  final static Logger logger = LogManager.getLogger(RestService.class);
  private static HashMap<Integer, SdxManager> sdxManagerMap = new HashMap<>();
  private  static HashSet<HttpServer> httpServers = new HashSet<>();

  public static void registerHttpServer(HttpServer server){
    httpServers.add(server);
  }

  public static void shutDownAllHttpServers(){
    for(HttpServer server: httpServers){
      server.shutdownNow();
    }
  }

  public static void registerSdxManager(Integer port, SdxManager sdxManager) {
    sdxManagerMap.put(port, sdxManager);
  }

  public static SdxManager getSdxManager(Integer port) {
    return sdxManagerMap.getOrDefault(port, null);
  }

  public static void removeSdxManager(Integer port) {
    if (sdxManagerMap.containsKey(port)) {
      sdxManagerMap.remove(port);
    }
  }

  public static Iterable<SdxManager> getAllSdxManagers() {
    return sdxManagerMap.values();
  }

  @POST
  @Path("/admin")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.TEXT_PLAIN)
  public String processAdminCmd(@Context UriInfo uriInfo, AdminCmd cmd) {
    logger.debug(uriInfo.getBaseUri());
    SdxManager sdxManager = sdxManagerMap.get(uriInfo.getBaseUri().getPort());
    logger.debug(String.format("%s got stitch request %s",
      sdxManager.getSliceName(), cmd));
    try {
      return sdxManager.adminCmd(cmd.operation, cmd.params);
    } catch (Exception e) {
      e.printStackTrace();
      return e.getMessage();
    }
  }

  @POST
  @Path("/flow/packetin")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.TEXT_PLAIN)
  public String processFlowPacketIn(@Context UriInfo uriInfo,
                                    Flow packetin) {
    logger.debug(uriInfo.getBaseUri());
    SdxManager sdxManager = sdxManagerMap.get(uriInfo.getBaseUri().getPort());
    logger.debug(String.format("%s got packet in %s", sdxManager.getSliceName(),
        packetin));
    try {
      return sdxManager.processPacketIn(packetin.src, packetin.dest);
    } catch (Exception e) {
      e.printStackTrace();
      return e.getMessage();
    }
  }

  @POST
  @Path("/bgp")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.TEXT_PLAIN)
  public String receiveBgpAdvertise(@Context UriInfo uriInfo, RouteAdvertise routeAdvertise) {
    logger.debug(uriInfo.getBaseUri());
    SdxManager sdxManager = sdxManagerMap.get(uriInfo.getBaseUri().getPort());
    logger.info(translatePids(String.format("%s got bgp advertisement %s", sdxManager
      .getSliceName(), routeAdvertise)));
    try {
      return sdxManager.processBgpAdvertise(routeAdvertise);
    } catch (Exception e) {
      e.printStackTrace();
      return e.getMessage();
    }
  }

  @GET
  @Path("/getpid")
  @Produces(MediaType.TEXT_PLAIN)
  public String getPid(@Context UriInfo uriInfo) {
    logger.debug(uriInfo.getBaseUri());
    SdxManager sdxManager = sdxManagerMap.get(uriInfo.getBaseUri().getPort());
    logger.debug(String.format(" %s got bgp pid request", sdxManager.getSliceName()));
    try {
      return sdxManager.getPid();
    } catch (Exception e) {
      e.printStackTrace();
      return e.getMessage();
    }
  }

  @POST
  @Path("/flow")
  @Produces(MediaType.TEXT_PLAIN)
  public String getFLow(@Context UriInfo uriInfo, Flow flow) {
    logger.debug(uriInfo.getBaseUri());
    SdxManager sdxManager = sdxManagerMap.get(uriInfo.getBaseUri().getPort());
    logger.debug(String.format(" %s got flow request", sdxManager.getSliceName()));
    ArrayList<String> patterns = new ArrayList<>();
    String pattern = ".*";
    if (flow.src != null) {
      pattern = pattern + String.format("nw_src=.*%s.*", flow.src);
    }
    if (flow.dest != null) {
      pattern = pattern + String.format("nw_dst=.*%s.*", flow.dest);
    }
    patterns.add(pattern);
    try {
      return sdxManager.logFlowTables(patterns, new ArrayList<>());
    } catch (Exception e) {
      e.printStackTrace();
      return e.getMessage();
    }
  }

  @POST
  @Path("/policy")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.TEXT_PLAIN)
  public String receivePolicyAdvertise(@Context UriInfo uriInfo, PolicyAdvertise policyAdvertise) {
    logger.debug(uriInfo.getBaseUri());
    SdxManager sdxManager = sdxManagerMap.get(uriInfo.getBaseUri().getPort());
    logger.info(translatePids(String.format("%s got policy advertisement %s", sdxManager
      .getSliceName(), policyAdvertise)));
    try {
      return sdxManager.processPolicyAdvertise(policyAdvertise);
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
    SdxManager sdxManager = sdxManagerMap.get(uriInfo.getBaseUri().getPort());
    logger.debug(String.format("%s got peer request %s", sdxManager.getSliceName(), peerRequest));
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
    SdxManager sdxManager = sdxManagerMap.get(uriInfo.getBaseUri().getPort());
    logger.debug(String.format("%s got sittch request %s", sdxManager.getSliceName(), sr));
    try {
      JSONObject res = sdxManager.stitchRequest(sr.sdxsite, sr.ckeyhash, sr.cslice,
        sr.creservid, sr.secret, sr.sdxnode, sr.gateway, sr.ip);
      return new StitchResult(res);
    } catch (Exception e) {
      e.printStackTrace();
      sdxManager.unlockSlice();
      return new StitchResult();
    }
  }

  @POST
  @Path("/undostitch")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.TEXT_PLAIN)
  public String undoStitch(@Context UriInfo uriInfo, UndoStitchRequest sr) {
    SdxManager sdxManager = sdxManagerMap.get(uriInfo.getBaseUri().getPort());
    logger.debug(String.format("%s got undoStitch request %s ", sdxManager.getSliceName(), sr
      .toString()));
    try {
      String res = sdxManager.undoStitch(sr.ckeyhash, sr.cslice,
        sr.creservid);
      return res;
    } catch (Exception e) {
      e.printStackTrace();
      sdxManager.unlockSlice();
      return String.format("UndoStitch Failed: %s", e.getMessage());
    }
  }

  @POST
  @Path("/connectionrequest")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.TEXT_PLAIN)
  public String connectionRequest(@Context UriInfo uriInfo, ConnectionRequest sr) {
    SdxManager sdxManager = sdxManagerMap.get(uriInfo.getBaseUri().getPort());
    logger.debug(String.format("%s got link request between %s and %s", sdxManager.getSliceName()
      , sr.self_prefix, sr.target_prefix));
    try {
      String res = sdxManager.connectionRequest(sr.self_prefix,
        sr.target_prefix, sr.bandwidth);
      return res;
    } catch (Exception e) {
      e.printStackTrace();
      sdxManager.unlockSlice();
      return e.getMessage();
    }
  }

  @POST
  @Path("/stitchchameleon")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.TEXT_PLAIN)
  public String stitchChameleon(@Context UriInfo uriInfo, StitchChameleon sr) {
    logger.debug("got chameleon stitch request: \n" + sr.toString());
    SdxManager sdxManager = sdxManagerMap.get(uriInfo.getBaseUri().getPort());
    try {
      String res = sdxManager.stitchChameleon(sr.sdxsite, sr.sdxnode,
          sr.ckeyhash, sr.stitchport, sr.vlan, sr.gateway, sr.ip);
      return res;
    } catch (Exception e){
      e.printStackTrace();
      sdxManager.unlockSlice();
      return e.getMessage();
    }
  }

  @POST
  @Path("/notifyprefix")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public NotifyResult notifyPrefix(@Context UriInfo uriInfo, PrefixNotification pn) {
    SdxManager sdxManager = sdxManagerMap.get(uriInfo.getBaseUri().getPort());
    logger.debug(String.format("%s got notifyprefix %s", sdxManager.getSliceName(), pn.toString()));
    NotifyResult res = sdxManager.notifyPrefix(pn.dest, pn.gateway, pn.customer);
    return res;
  }

  @POST
  @Path("/broload")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.TEXT_PLAIN)
  public String broload(@Context UriInfo uriInfo, BroLoad bl) {
    logger.debug("got broload");
    SdxManager sdxManager = sdxManagerMap.get(uriInfo.getBaseUri().getPort());
    double load = Double.parseDouble(bl.usage);
    String res = null;
    try {
      res = sdxManager.broload(bl.broip, load);
    } catch (Exception e) {
      e.printStackTrace();
      res = "Failed to get Bro Load";
      logger.warn(res);
    } finally {
      sdxManager.unlockSlice();
    }
    return res;
  }

  private String translatePids(String s) {
    for (SdxManager sdxManager : sdxManagerMap.values()) {
      s = s.replace(sdxManager.getPid(), sdxManager.getSliceName());
    }
    return s;
  }
}

