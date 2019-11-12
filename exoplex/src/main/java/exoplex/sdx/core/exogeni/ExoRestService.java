package exoplex.sdx.core.exogeni;

import exoplex.sdx.core.RestServiceBase;
import exoplex.sdx.core.SdxManagerBase;
import exoplex.sdx.core.restutil.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;

/**
 * Root resource (exposed at "myresource" path)
 * <p>
 * Call with curl:
 * curl -X POST -H "Content-Type: application/json" -d '{"operation":"stitch", "params":["1","2",
 * "3"]}' http://localhost:8880/sdx/admin
 */
@Path("sdx")
public class ExoRestService extends RestServiceBase {
  final static Logger logger = LogManager.getLogger(ExoRestService.class);

  @POST
  @Path("/peer")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public PeerRequest peer(@Context UriInfo uriInfo, PeerRequest peerRequest) {
    logger.debug(uriInfo.getBaseUri());
    SdxManagerBase exoSdxManager = sdxManagerMap.get(uriInfo.getBaseUri().getPort());
    logger.debug(String.format("%s got peer request %s", exoSdxManager.getSliceName(), peerRequest));
    try {
      return exoSdxManager.processPeerRequest(peerRequest);
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
    SdxManagerBase exoSdxManager = sdxManagerMap.get(uriInfo.getBaseUri().getPort());
    logger.debug(String.format("%s got sittch request %s", exoSdxManager.getSliceName(), sr));
    try {
      JSONObject res = exoSdxManager.stitchRequest(sr.sdxsite, sr.ckeyhash, sr.cslice,
        sr.creservid, sr.secret, sr.sdxnode, sr.gateway, sr.ip);
      return new StitchResult(res);
    } catch (Exception e) {
      e.printStackTrace();
      exoSdxManager.unlockSlice();
      return new StitchResult();
    }
  }

  @POST
  @Path("/undostitch")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.TEXT_PLAIN)
  public String undoStitch(@Context UriInfo uriInfo, UndoStitchRequest sr) {
    SdxManagerBase exoSdxManager = sdxManagerMap.get(uriInfo.getBaseUri().getPort());
    logger.debug(String.format("%s got undoStitch request %s ", exoSdxManager.getSliceName(), sr
      .toString()));
    try {
      String res = exoSdxManager.undoStitch(sr.ckeyhash, sr.cslice,
        sr.creservid);
      return res;
    } catch (Exception e) {
      e.printStackTrace();
      exoSdxManager.unlockSlice();
      return String.format("UndoStitch Failed: %s", e.getMessage());
    }
  }

  @POST
  @Path("/stitchchameleon")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.TEXT_PLAIN)
  public String stitchChameleon(@Context UriInfo uriInfo, StitchChameleon sr) {
    logger.debug("got chameleon stitch request: \n" + sr.toString());
    SdxManagerBase exoSdxManager = sdxManagerMap.get(uriInfo.getBaseUri().getPort());
    try {
      String res = exoSdxManager.stitchChameleon(sr.sdxsite, sr.sdxnode,
        sr.ckeyhash, sr.stitchport, sr.vlan, sr.gateway, sr.ip);
      return res;
    } catch (Exception e) {
      e.printStackTrace();
      exoSdxManager.unlockSlice();
      return e.getMessage();
    }
  }

  @POST
  @Path("/broload")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.TEXT_PLAIN)
  public String broload(@Context UriInfo uriInfo, BroLoad bl) {
    logger.debug("got broload");
    ExoSdxManager exoSdxManager = (ExoSdxManager) sdxManagerMap.get(uriInfo.getBaseUri().getPort());
    double load = Double.parseDouble(bl.usage);
    String res = null;
    try {
      res = exoSdxManager.broload(bl.broip, load);
    } catch (Exception e) {
      e.printStackTrace();
      res = "Failed to get Bro Load";
      logger.warn(res);
    } finally {
      exoSdxManager.unlockSlice();
    }
    return res;
  }

}

