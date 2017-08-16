package safe.sdx.sdx;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

/**
 * Root resource (exposed at "myresource" path)
 */
@Path("sdx")
public class RestService {

    /** 
     * Method handling HTTP GET requests. The returned object will be sent
     * to the client as "text/plain" media type.
     *
     * @return String that will be returned as a text/plain response.
     */
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String getIt() {
        return "Got it!";
    }

    //Test API
    @GET
    @Path("/sr")
    @Produces(MediaType.APPLICATION_JSON)
    public StitchRequest getJson() {
      System.out.println("json get");

        return new StitchRequest("1","2","3","4","5","6");
    }

    //Test API
    @POST
    @Path("/stitchrequest")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public StitchResult stitchRequest(StitchRequest sr){
      System.out.println("got sittch request");
      String[] res=SdxManager.stitchRequest(sr.sdxslice,sr.sdxnode, sr.ckeyhash, sr.cslice, sr.creservid, sr.secret);
      return new StitchResult(res[0],res[1]);
    }
}

class StitchRequest{
  public  String sdxslice;
  public  String sdxnode;
  //customer Safe key hash
  public  String ckeyhash;
  public  String cslice;
  public  String creservid;
  public  String secret;

  public StitchRequest(){}

  public StitchRequest(String sdxslice, String sdxnode,String ckeyhash, String cslice, String creserveid, String secret){
    this.sdxslice=sdxslice;
    this.sdxnode=sdxnode;
    this.ckeyhash=ckeyhash;
    this.cslice=cslice;
    this.creservid=creservid;
    this.secret=secret;
  }
}

class StitchResult{
  public boolean result;
  public String gateway;
  public String ip;
  public StitchResult(){}
  public StitchResult(String gw, String ip){
    this.gateway=gw;
    this.ip=ip;
    if(gateway!=null && ip !=null)
      result=true;
    else
      result=false;
  }
}
