package sdx.network;

public class NetworkUtil {
  public static String computeInterfaceName(String nodeName, String linkName){
    return nodeName + ":" + linkName;
  }
}
