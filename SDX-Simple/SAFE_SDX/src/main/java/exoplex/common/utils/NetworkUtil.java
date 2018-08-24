package exoplex.common.utils;

public class NetworkUtil {
  private static  final int RETRY=3;
  public static boolean checkReachability(String ip){
    for(int i=0; i< RETRY; i++) {
      String res = Exec.exec(String.format("ping -c 1 %s", ip));
      if(res.contains("1 packets received") || res.contains("1 received")){
        return true;
      }
    }
    return false;
  }
}
