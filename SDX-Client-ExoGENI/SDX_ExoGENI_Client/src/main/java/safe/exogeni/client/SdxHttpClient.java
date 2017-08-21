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
public class SdxHttpClient {
    // Base URI the Grizzly HTTP server will listen on
    public static final String BASE_URI = "http://152.3.136.36:8080/";
    /**
     * Starts Grizzly HTTP server exposing JAX-RS resources defined in this application.
     * @return Grizzly HTTP server.
     */
public static void main(String[] args) {
try {

		DefaultHttpClient httpClient = new DefaultHttpClient();
		HttpGet getRequest = new HttpGet(
			"http://152.3.136.36:8080/sdx/sr");
		//getRequest.addHeader("accept", "application/json");

		HttpResponse response = httpClient.execute(getRequest);

		if (response.getStatusLine().getStatusCode() != 200) {
			throw new RuntimeException("Failed : HTTP error code : "
			   + response.getStatusLine().getStatusCode());
		}
		
		String output=EntityUtils.toString(response.getEntity());
		JSONObject jsonobj=new JSONObject(output);
		System.out.println(jsonobj);

		//BufferedReader br = new BufferedReader(
    //                     new InputStreamReader((response.getEntity().getContent())));

		//String output;
		//System.out.println("Output from Server .... \n");
		//while ((output = br.readLine()) != null) {
		//	System.out.println(output);
		//}

		httpClient.getConnectionManager().shutdown();

	  } catch (ClientProtocolException e) {

		e.printStackTrace();

	  } catch (IOException e) {

		e.printStackTrace();
	  }

		HttpClient httpClient = HttpClientBuilder.create().build(); //Use this instead 

		try {
      System.out.println("hah");

			HttpPost request = new HttpPost("http://152.3.136.36:8080/sdx/stitchrequest");
			StringEntity params =new StringEntity("{\"sdxslice\":\"sdx\",\"sdxnode\":\"c0\",\"ckeyhash\":\"keyhash\",\"cslice\":\"alice\",\"creservid\":\"cnode0\",\"secret\":\"20\"} ");
			request.addHeader("content-type", "application/json");
			request.setEntity(params);
			HttpResponse response = httpClient.execute(request);
		BufferedReader br = new BufferedReader(
                         new InputStreamReader((response.getEntity().getContent())));

		String output1;
		System.out.println("Output from Server .... \n");
		while ((output1 = br.readLine()) != null) {
			System.out.println(output1);
		}

			//handle response here...
		
			String output=EntityUtils.toString(response.getEntity());
			JSONObject jsonobj=new JSONObject(output);
			System.out.println(jsonobj);

		}catch (Exception ex) {

				//handle exception here

		} finally {
				//Deprecated
				//httpClient.getConnectionManager().shutdown(); 
		}

	}

}

