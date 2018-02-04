package client.exogeniclient;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.apache.http.HttpEntity;
import org.apache.log4j.Logger;
import org.json.JSONObject;
import sdx.utils.Exec;

/**
 * Main class.
 *
 */
public class SdxHttpClient {
	  final static Logger logger = Logger.getLogger(Exec.class);

	// Base URI the Grizzly HTTP server will listen on
  /**
   * Starts Grizzly HTTP server exposing JAX-RS resources defined in this application.
   * @return Grizzly HTTP server.
   */

  public static String httpRequest(String serverurl, JSONObject paramsobj){
    String resobj="{\"result\":\"Failed\"}";
    HttpClient httpClient = HttpClientBuilder.create().build(); //Use this instead
    try {

      HttpPost request = new HttpPost(serverurl);
      StringEntity params =new StringEntity(paramsobj.toString());
      //StringEntity params =new StringEntity("{\"sdxslice\":\"sdx\",\"sdxnode\":\"c0\",\"ckeyhash\":\"keyhash\",\"cslice\":\"alice\",\"creservid\":\"cnode0\",\"secret\":\"20\"} ");
      request.addHeader("content-type", "application/json");
      request.setEntity(params);
      HttpResponse response = httpClient.execute(request);
      //handle response here...
      HttpEntity entity=response.getEntity();
      if(entity !=null) {
        String output = EntityUtils.toString(entity);
        logger.debug(output);
        httpClient.getConnectionManager().shutdown();
        return output;
      }
      else{
        return "http response entity null";
      }

    }catch (Exception ex) {
      ex.printStackTrace();
      return resobj;

    } finally {
      //Deprecated
    }
  }

}

