package exoplex.sdx.core;

import exoplex.sdx.advertise.PolicyAdvertise;
import exoplex.sdx.advertise.RouteAdvertise;
import exoplex.sdx.core.restutil.*;
import org.glassfish.grizzly.http.server.HttpServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

@Path("sdx")
public class RestServiceBase {
  final static Logger logger = LogManager.getLogger(RestServiceBase.class);
  private static HashMap<Integer, SdxManagerBase> sdxManagerMap = new HashMap<>();
  private static HashSet<HttpServer> httpServers = new HashSet<>();

  public static void registerHttpServer(HttpServer server) {
    httpServers.add(server);
  }

  public static void shutDownAllHttpServers() {
    for (HttpServer server : httpServers) {
      server.shutdownNow();
    }
  }

  public static void registerSdxManager(Integer port, SdxManagerBase exoSdxManager) {
    sdxManagerMap.put(port, exoSdxManager);
  }

  public static SdxManagerBase getSdxManager(Integer port) {
    return sdxManagerMap.getOrDefault(port, null);
  }

  public static void removeSdxManager(Integer port) {
    sdxManagerMap.remove(port);
  }

  public static Iterable<SdxManagerBase> getAllSdxManagers() {
    return sdxManagerMap.values();
  }

  @POST
  @Path("/admin")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.TEXT_PLAIN)
  public String processAdminCmd(@Context UriInfo uriInfo, AdminCmd cmd) {
    logger.debug(uriInfo.getBaseUri());
    SdxManagerBase exoSdxManager = sdxManagerMap.get(uriInfo.getBaseUri().getPort());
    logger.debug(String.format("%s got stitch request %s", exoSdxManager.getSliceName(), cmd));
    try {
      return exoSdxManager.adminCmd(cmd.operation, cmd.params);
    } catch (Exception e) {
      e.printStackTrace();
      return e.getMessage();
    }
  }

  @POST
  @Path("/flow/packetin")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.TEXT_PLAIN)
  public String processFlowPacketIn(@Context UriInfo uriInfo, Flow packetin) {
    logger.debug(uriInfo.getBaseUri());
    SdxManagerBase exoSdxManager = sdxManagerMap.get(uriInfo.getBaseUri().getPort());
    logger.debug(String.format("%s got packet in %s", exoSdxManager.getSliceName(), packetin));
    try {
      return exoSdxManager.processPacketIn(packetin.src, packetin.dest);
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
    SdxManagerBase exoSdxManager = sdxManagerMap.get(uriInfo.getBaseUri().getPort());
    logger.info(translatePids(String.format("%s got bgp advertisement %s", exoSdxManager.getSliceName(), routeAdvertise)));
    try {
      return exoSdxManager.processBgpAdvertise(routeAdvertise);
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
    SdxManagerBase exoSdxManager = sdxManagerMap.get(uriInfo.getBaseUri().getPort());
    logger.debug(String.format(" %s got bgp pid request", exoSdxManager.getSliceName()));
    try {
      return exoSdxManager.getPid();
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
    SdxManagerBase exoSdxManager = sdxManagerMap.get(uriInfo.getBaseUri().getPort());
    logger.debug(String.format(" %s got flow request", exoSdxManager.getSliceName()));
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
      return exoSdxManager.logFlowTables(patterns, new ArrayList<>());
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
    SdxManagerBase exoSdxManager = sdxManagerMap.get(uriInfo.getBaseUri().getPort());
    logger.info(translatePids(String.format("%s got policy advertisement %s", exoSdxManager.getSliceName(), policyAdvertise)));
    try {
      return exoSdxManager.processPolicyAdvertise(policyAdvertise);
    } catch (Exception e) {
      e.printStackTrace();
      return e.getMessage();
    }
  }

  @POST
  @Path("/connectionrequest")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.TEXT_PLAIN)
  public String connectionRequest(@Context UriInfo uriInfo, ConnectionRequest sr) {
    SdxManagerBase exoSdxManager = sdxManagerMap.get(uriInfo.getBaseUri().getPort());
    logger.debug(String.format("%s got link request between %s and %s", exoSdxManager.getSliceName(), sr.self_prefix, sr.target_prefix));
    try {
      String res = exoSdxManager.connectionRequest(sr.self_prefix, sr.target_prefix, sr.bandwidth);
      return res;
    } catch (Exception e) {
      e.printStackTrace();
      exoSdxManager.unlockSlice();
      return e.getMessage();
    }
  }

  @POST
  @Path("/notifyprefix")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public NotifyResult notifyPrefix(@Context UriInfo uriInfo, PrefixNotification pn) {
    SdxManagerBase exoSdxManager = sdxManagerMap.get(uriInfo.getBaseUri().getPort());
    logger.debug(String.format("%s got notifyprefix %s", exoSdxManager.getSliceName(), pn.toString()));
    NotifyResult res = exoSdxManager.notifyPrefix(pn.dest, pn.gateway, pn.customer);
    return res;
  }

  private String translatePids(String s) {
    for (SdxManagerBase exoSdxManager : sdxManagerMap.values()) {
      s = s.replace(exoSdxManager.getPid(), exoSdxManager.getSliceName());
    }
    return s;
  }
}
