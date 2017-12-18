package sdx.networkmanager;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.apache.http.client.HttpClient;
import org.json.JSONObject;
public class RestSDN {

  final static Logger logger = Logger.getLogger(RestSDN.class);

  public static String httpput(String serverurl, String data){

    HttpClient httpClient = HttpClientBuilder.create().build(); //Use this instead
    try {

      HttpPut request = new HttpPut(serverurl);
      StringEntity params =new StringEntity(data,"UTF-8");
      //StringEntity params =new StringEntity("{\"sdxslice\":\"sdx\",\"sdxnode\":\"c0\",\"ckeyhash\":\"keyhash\",\"cslice\":\"alice\",\"creservid\":\"cnode0\",\"secret\":\"20\"} ");
      request.setEntity(params);
      HttpResponse response = httpClient.execute(request);
      //handle response here...
      String output=EntityUtils.toString(response.getEntity());
      logger.debug(output);
      return output;
    }catch (Exception ex) {
      ex.printStackTrace();
      return "Exception when setting ovsdb addr";
    } finally {
      //Deprecated
    }
  }
}
