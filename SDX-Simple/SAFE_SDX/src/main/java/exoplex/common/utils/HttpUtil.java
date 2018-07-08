package exoplex.common.utils;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.net.URI;

public class HttpUtil {
  final static Logger logger = LogManager.getLogger(HttpUtil.class);

  public static String postJSON(String serverurl, JSONObject paramsobj) {
    logger.debug(serverurl);
    logger.debug(paramsobj);
    JSONObject resobj = new JSONObject();
    resobj.put("result", false);
    resobj.put("info", "Exception when make http request");
    HttpClient httpClient = HttpClientBuilder.create().build(); //Use this instead

    try {

      HttpPost request = new HttpPost(serverurl);
      StringEntity params = new StringEntity(paramsobj.toString());
      request.addHeader("content-type", "application/json");
      request.setEntity(params);
      HttpResponse response = httpClient.execute(request);
      //handle response here...
      if (response.getStatusLine().getStatusCode() != 200) {
        logger.debug(serverurl);
        logger.debug(paramsobj.toString());
      }
      String output = EntityUtils.toString(response.getEntity());
      logger.debug(output);
      return output;

    } catch (Exception ex) {
      ex.printStackTrace();
      return resobj.toString();
    } finally {
      //Deprecated
    }
  }

  public static String putString(String serverurl, String data) {
    CloseableHttpClient httpClient = HttpClientBuilder.create().build();
    try {
      HttpPut request = new HttpPut(serverurl);
      StringEntity params = new StringEntity(data, "UTF-8");
      request.setEntity(params);
      HttpResponse response = httpClient.execute(request);
      //handle response here...
      String output = EntityUtils.toString(response.getEntity());
      logger.debug(output);
      httpClient.close();
      return output;
    } catch (Exception ex) {
      ex.printStackTrace();
      return "Exception when setting ovsdb addr";
    }
  }

  public static String delete(String serverurl, String data) {
    logger.debug("delete: " + serverurl);
    logger.debug(data);
    CloseableHttpClient httpClient = HttpClientBuilder.create().build();
    try {
      HttpDeleteWithBody httpDelete = new HttpDeleteWithBody(serverurl);
      httpDelete.setEntity(new StringEntity(data));
      HttpResponse response = httpClient.execute(httpDelete);
      //handle response here...
      String output = EntityUtils.toString(response.getEntity());
      logger.debug(output);
      httpClient.close();
      return output;
    } catch (Exception e) {
      e.printStackTrace();
      return "Exception when deleting data";
    }
  }

  /*@Input
   * data: a String of json format
   */

  public static String get(String serverurl) {
    CloseableHttpClient httpClient = HttpClientBuilder.create().build();
    try {
      HttpGet request = new HttpGet(serverurl);
      HttpResponse response = httpClient.execute(request);
      //handle response here...
      String output = EntityUtils.toString(response.getEntity());
      logger.debug(output);
      httpClient.close();
      return output;
    } catch (Exception ex) {
      ex.printStackTrace();
      return "Exception when setting ovsdb addr";
    }
  }

  /**
   * Main class.
   */
  static class HttpDeleteWithBody extends HttpEntityEnclosingRequestBase {
    public static final String METHOD_NAME = "DELETE";

    public HttpDeleteWithBody(final String uri) {
      super();
      setURI(URI.create(uri));
    }

    public HttpDeleteWithBody(final URI uri) {
      super();
      setURI(uri);
    }

    public HttpDeleteWithBody() {
      super();
    }

    public String getMethod() {
      return METHOD_NAME;
    }
  }
}
