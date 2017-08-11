package  safe.sdx.utils;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.MalformedURLException;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;

public class SafePost{
  private static String getMessage(String message){
      Pattern pattern = Pattern.compile("\"message\": \"(.*?)\"");
      Matcher matcher = pattern.matcher(message);
      String token=null;
      if (matcher.find())
      {
        token=matcher.group(1);
      }
      return token;

  }
  public static String getToken(String message){
      Pattern pattern = Pattern.compile("\\[\\'(.{43}?)\\'?");
      Matcher matcher = pattern.matcher(message);
      String token=null;
      if (matcher.find())
      {
        token=matcher.group(1);
      }
      System.out.print("token:\""+token+"\"");
      return token;
  }
  public static String postSafeStatements(String safeserver,String requestName,String principal,String[] othervalues){
		/** Post to remote safesets using apache httpclient */
    String res=null;
		try {
			DefaultHttpClient httpClient = new DefaultHttpClient();
      System.out.println(safeserver+"/"+requestName);
			HttpPost postRequest = new HttpPost("http://"+safeserver+"/"+requestName);
      String params="{\"principal\":\"PRINCIPAL\",\"otherValues\":[OTHER]}";
      params=params.replace("PRINCIPAL",principal);
      String others="";
      System.out.println(othervalues);
      if(othervalues.length>0){
        others=others+"\""+othervalues[0]+"\"";
      }
      for(int i=1;i<othervalues.length;i++){
        others=others+",\""+othervalues[i]+"\"";
      }
      params=params.replace("OTHER",others);
      System.out.print(requestName+"  "+params);

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
      String message="";
			while ((output = br.readLine()) != null) {
        message=message+output;
			}
      res=getMessage(message);
      System.out.print("message:\""+res+"\"");
      return res;
      
			} catch (MalformedURLException e) {
				System.out.println("malformedURLExcepto");
				e.printStackTrace();

			} catch (IOException e) {
        System.out.println("ioexception");
				e.printStackTrace();
		 	} catch (Exception e){
        System.out.println("normal Exception");
        e.printStackTrace();
      }
    return null;
  }
}
