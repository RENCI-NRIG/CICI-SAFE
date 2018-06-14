package common.utils;

import com.sun.org.apache.regexp.internal.RE;
import org.ini4j.spi.RegEscapeTool;

public class NetworkUtil {
  private static  final int RETRY=3;
  public static boolean checkReachability(String ip){
    for(int i=0; i< RETRY; i++) {
      String res = Exec.exec(String.format("ping -c 1 %s", ip));
      if(res.contains("1 packets received")){
        return true;
      }
    }
    return false;
  }
}
