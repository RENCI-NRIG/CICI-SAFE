package exoplex.sdx.network;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class SdnReplay {
  public static void replay(String fileName){

    try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
      String line;
      while ((line = br.readLine()) != null) {
        String url = line.replace("\n", "");
        String params = br.readLine();
        params = params.replace("\n", "");
        JSONObject obj = new JSONObject(params);
        RoutingManager.postSdnCmd(url, obj, false);
        try {
          Thread.sleep(500);
        }catch (Exception e){}
      }
    }catch (IOException e){
      e.printStackTrace();
    }
    //((RoutingManager)obj).deleteAllFlows(sdxManager.getSDNController());
  }
}
