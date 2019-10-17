package exoplex.sdx.core.vfc;

import exoplex.sdx.core.RestServiceBase;
import exoplex.sdx.core.restutil.StitchRequest;
import exoplex.sdx.core.restutil.StitchResult;
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

@Path("sdx")
public class VfcRestService extends RestServiceBase {
  final static Logger logger = LogManager.getLogger(VfcRestService.class);

  @POST
  @Path("/stitchvfc")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public StitchResult stitchVfc(@Context UriInfo uriInfo, StitchRequest sr) {
    VfcSdxManager vfcSdxManager = (VfcSdxManager) sdxManagerMap.get(uriInfo.getBaseUri().getPort());
    logger.debug(String.format("%s got sittch request %s", vfcSdxManager.getSliceName(), sr));
    try {
      JSONObject res = vfcSdxManager.stitchRequest(sr.sdxsite, sr.ckeyhash, sr.cslice,
        sr.creservid, sr.secret, sr.sdxnode, sr.gateway, sr.ip);
      return new StitchResult(res);
    } catch (Exception e) {
      e.printStackTrace();
      vfcSdxManager.unlockSlice();
      return new StitchResult();
    }
  }
}
