package exoplex.common.utils;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SafeUtils {
  final static Logger logger = LogManager.getLogger(SafeUtils.class);

  final static HashMap<String, String> emptyEnvs = new HashMap<>();

  private static String getMessage(String message) {
    Pattern pattern = Pattern.compile("\"message\": \"(.*?)\"}$");
    Matcher matcher = pattern.matcher(message);
    String token = null;
    if (matcher.find()) {
      token = matcher.group(1);
    }
    return token;

  }

  public static String getToken(String message) {
    Pattern pattern = Pattern.compile("\\[\\'(.{43}=?)\\'?");
    Matcher matcher = pattern.matcher(message);
    String token = null;
    if (matcher.find()) {
      token = matcher.group(1);
    }
    logger.debug("token:\"" + token + "\"");
    return token;
  }

  public static List<String> getTokens(String message){
    ArrayList<String> tokens = new ArrayList<String>();
    Pattern pattern = Pattern.compile("\\'(.{43}=?)\\'?");
    Matcher matcher = pattern.matcher(message);
    String token = null;
    while (matcher.find()) {
      token = matcher.group(1);
      tokens.add(token);
    }
    return tokens;
  }


  public static String postSafeStatements(String safeserver, String requestName, String
      principal, Object[] othervalues) {
    return postSafeStatements(safeserver, requestName, principal, emptyEnvs, othervalues);
  }

  private static String getEnvs(HashMap<String, String> envs){
    if(envs.size()==0){
      return "";
    }
    String res = "";
    for(String key : envs.keySet()){
      res = res + "\"" + key + "\":\"" + envs.get(key) + "\",";
    }
    return res;
  }

  public static String postSafeStatements(String safeserver, String requestName, String
      principal, HashMap<String, String> envs, Object[] othervalues) {
    /** Post to remote safesets using apache httpclient */
    String res = null;
    try {
      DefaultHttpClient httpClient = new DefaultHttpClient();
      logger.debug(safeserver + "/" + requestName);
      HttpPost postRequest = new HttpPost("http://" + safeserver + "/" + requestName);
      String params = "{\"principal\":\"PRINCIPAL\",ENVS\"methodParams\":[OTHER]}";
      params = params.replace("ENVS", getEnvs(envs));
      params = params.replace("PRINCIPAL", principal);
      String others = "";
      if (othervalues.length > 0) {
        if(othervalues[0] instanceof String) {
          others = others + "\"" + othervalues[0] + "\"";
        }else if(othervalues[0] instanceof Boolean){
          others = String.valueOf(othervalues[0]);
        }
      }
      for (int i = 1; i < othervalues.length; i++) {
        if(othervalues[i] instanceof String) {
          others = others + ",\"" + othervalues[i] + "\"";
        }else if(othervalues[i] instanceof Boolean){
          others = others + ",\"" + othervalues[i]+"\"";
        }
      }
      params = params.replace("OTHER", others);
      logger.debug(requestName + "  " + params);

      StringEntity input = new StringEntity(params);
      input.setContentType("application/json");
      postRequest.setEntity(input);
      HttpResponse response = httpClient.execute(postRequest);
      if (response.getStatusLine().getStatusCode() != 200) {
        throw new RuntimeException("Failed : HTTP error code : "
            + response.getStatusLine().getStatusCode());
      }

      BufferedReader br = new BufferedReader(
          new InputStreamReader((response.getEntity().getContent())));

      String output;
      String message = "";
      while ((output = br.readLine()) != null) {
        message = message + output;
      }
      res = getMessage(message);
      logger.debug("message:\"" + res + "\"");
      return res;

    } catch (MalformedURLException e) {
      logger.error("malformedURLException when posting safe statement");
    } catch (IOException e) {
      logger.error("http ioexception when posting safe statement");
      e.printStackTrace();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  public static boolean authorize(String safeServer, String requestName, String principal, String[]
      otherValues){
    return authorize(safeServer, requestName, principal, otherValues, emptyEnvs);
  }

  public static boolean authorize(String safeServer, String requestName, String principal, String[]
      otherValues,  HashMap<String, String> envs){
    String message = postSafeStatements(safeServer, requestName, principal, envs, otherValues);
    if(message.contains("Unsatisfied") || message.contains("Failed")){
      return false;
    }
    for(String val: otherValues) {
      if (!message.contains(val)) {
        logger.warn(message);
      }
    }
    return true;
  }

  public static String getPrincipalId(String safeServer, String keyFile){
    String message = SafeUtils.postSafeStatements(safeServer, "whoami", keyFile, new String[]{});
    logger.info(message);
    return message.split(":")[0].replace("{","")
        .replace("'","")
        .replace(" ","");
  }
}
