package exoplex.sdx.core;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

/**
 * Root resource (exposed at "myresource" path)
 */
@Path("sdx")
public class RestService {
  final static Logger logger = LogManager.getLogger(RestService.class);

  @POST
  @Path("/stitchrequest")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public StitchResult stitchRequest(StitchRequest sr) {
    logger.debug("got sittch request");
    try {
      String[] res = SdxServer.sdxManager.stitchRequest(sr.sdxslice, sr.sdxsite, sr.ckeyhash, sr.cslice,
          sr.creservid, sr.secret, sr.sdxnode);
      return new StitchResult(res[0], res[1]);
    } catch (Exception e) {
      e.printStackTrace();
      return new StitchResult(null, null);
    }
  }

  @POST
  @Path("/connectionrequest")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.TEXT_PLAIN)
  public String connectionRequest(ConnectionRequest sr) {
    logger.debug("got link request between " + sr.self_prefix + " and " + sr.target_prefix);
    try {
      String res = SdxServer.sdxManager.connectionRequest(sr.ckeyhash, sr.self_prefix,
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
  public String stitchChameleon(StitchChameleon sr) {
    logger.debug("got chameleon stitch request: \n" + sr.toString());
    logger.debug(String.format("got chameleon stitch request from %s", sr.ckeyhash));
    String res = SdxServer.sdxManager.stitchChameleon(sr.sdxslice, sr.sdxnode,
        sr.ckeyhash, sr.stitchport, sr.vlan, sr.gateway, sr.ip);
    return res;
  }

  @POST
  @Path("/notifyprefix")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.TEXT_PLAIN)
  public String notifyPrefix(PrefixNotification pn) {
    logger.debug("got notifyprefix");
    String res = SdxServer.sdxManager.notifyPrefix(pn.dest, pn.gateway, pn.customer);
    logger.debug(res);
    return res;
  }

  @POST
  @Path("/broload")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.TEXT_PLAIN)
  public String broload(BroLoad bl)  {
    logger.debug("got broload");
    double load = Double.parseDouble(bl.usage);
    String res = null;
    try {
      res = SdxServer.sdxManager.broload(bl.broip, load);
    } catch (Exception e) {
      e.printStackTrace();
      res = "Failed to get Bro Load";
      logger.warn(res);
    }
    return res;
  }
}

class StitchChameleon {
  public String sdxslice;
  public String sdxnode;
  public String ckeyhash;
  public String stitchport;
  public String vlan;
  public String gateway;
  public String ip;

  public StitchChameleon() {
  }

  public StitchChameleon(String sdxslice, String sdxnode, String ckeyhash, String stitchport, String vlan, String gateway, String ip) {
    this.sdxslice = sdxslice;
    this.sdxnode = sdxnode;
    this.ckeyhash = ckeyhash;
    this.stitchport = stitchport;
    this.vlan = vlan;
    this.gateway = gateway;
    this.ip = ip;
  }

  public String toString() {
    return "{\"sdxslice\": " + sdxslice + ", \"sdxnode\": " + sdxnode + ", \"ckeyhash\":" + ckeyhash + ", \"stitchport\":" + stitchport + ", \"vlan\":" + vlan + "\"gateway\":" + gateway + "}";
  }
}

class StitchRequest {
  public String sdxslice;
  public String sdxsite;
  //customer Safe key hash
  public String ckeyhash;
  public String cslice;
  public String creservid;
  public String sdxnode;
  public String secret;
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

  public StitchResult() {
  }

  public StitchResult(String gw, String ip) {
    this.gateway = gw;
    this.ip = ip;
    if (gateway != null && ip != null)
      result = true;
    else
      result = false;
  }
}

