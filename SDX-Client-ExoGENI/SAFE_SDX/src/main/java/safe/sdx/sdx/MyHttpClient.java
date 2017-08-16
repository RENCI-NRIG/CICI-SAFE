package safe.sdx.sdx;
import safe.sdx.utils.SafePost;

import java.net.URI;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.apache.http.client.HttpClient;
import org.json.JSONObject;
/**
 * Main class.
 *
 */
public class MyHttpClient {
  // Base URI the Grizzly HTTP server will listen on
  /**
   * Starts Grizzly HTTP server exposing JAX-RS resources defined in this application.
   * @return Grizzly HTTP server.
   */

  public static JSONObject tryStitch(String serverurl, JSONObject paramsobj){
    JSONObject resobj=new JSONObject();
    resobj.put("result",false);
    HttpClient httpClient = HttpClientBuilder.create().build(); //Use this instead 

    try {

      HttpPost request = new HttpPost(serverurl);
      StringEntity params =new StringEntity(paramsobj.toString());
      //StringEntity params =new StringEntity("{\"sdxslice\":\"sdx\",\"sdxnode\":\"c0\",\"ckeyhash\":\"keyhash\",\"cslice\":\"alice\",\"creservid\":\"cnode0\",\"secret\":\"20\"} ");
      request.addHeader("content-type", "application/json");
      request.setEntity(params);
      HttpResponse response = httpClient.execute(request);
      //handle response here...
      String output=EntityUtils.toString(response.getEntity());
      JSONObject jsonobj=new JSONObject(output);
      httpClient.getConnectionManager().shutdown(); 
      return jsonobj;

    }catch (Exception ex) {
      ex.printStackTrace();
      return resobj;

    } finally {
        //Deprecated
    }
  }

  public static String notifyPrefix(String serverurl, JSONObject paramsobj){
    //JSONObject resobj=new JSONObject();
    //resobj.put("result",false);
    String resobj="";
    HttpClient httpClient = HttpClientBuilder.create().build(); //Use this instead 
    try {

      HttpPost request = new HttpPost(serverurl);
      StringEntity params =new StringEntity(paramsobj.toString());
      //StringEntity params =new StringEntity("{\"sdxslice\":\"sdx\",\"sdxnode\":\"c0\",\"ckeyhash\":\"keyhash\",\"cslice\":\"alice\",\"creservid\":\"cnode0\",\"secret\":\"20\"} ");
      request.addHeader("content-type", "application/json");
      request.setEntity(params);
      HttpResponse response = httpClient.execute(request);
      //handle response here...
      String output=EntityUtils.toString(response.getEntity());
      resobj=output;
      //JSONObject jsonobj=new JSONObject(output);
      //httpClient.getConnectionManager().shutdown(); 
      return resobj;

    }catch (Exception ex) {
      ex.printStackTrace();
      return resobj;

    } finally {
        //Deprecated
    }
  }
}

