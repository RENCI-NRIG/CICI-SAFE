package exoplex.demo.tridentcom;

import com.google.inject.Inject;
import exoplex.sdx.core.SliceHelper;
import safe.Authority;

import java.util.ArrayList;
import java.util.HashMap;

public class TridentSetting extends SliceHelper {
  public static final ArrayList<String> sites = new ArrayList<>();
  public static final ArrayList<String> clientSlices = new ArrayList<>();
  public static final HashMap<String, String> clientKeyMap = new HashMap<>();
  public static final HashMap<String, String> clientSiteMap = new HashMap<>();
  public static final HashMap<String, String> clientIpMap = new HashMap<>();
  final static String sdxName = "sdx-tc";
  static String userDir = System.getProperty("user.dir");
  static String safeDockerImage = "safeserver-v4";
  static String sdxSimpleDir = userDir.split("SDX-Simple")[0] + "SDX-Simple/";
  public final static String sdxConfig = sdxSimpleDir + "config/tri.conf";
  public final static String[] sdxArgs = new String[]{"-c", sdxSimpleDir + "config/tri.conf"};
  public final static String[] sdxDelArgs = new String[]{"-c", sdxSimpleDir + "config/tri.conf",
    "-d"};
  public static String[] clientArgs = new String[]{"-c", sdxSimpleDir + "client-config/client" +
    ".conf"};

  static {
    //sites.add("RENCI");
    sites.add("UNF");
    sites.add("UFL");
    //sites.add("UH");
    sites.add("SL");
    //sites.add("SL");
    //sites.add("UMASS");
    //sites.add("UNF");
    //sites.add("WSU");
  }

  static {
    int keyBase = 5;
    int ipBase = 10;
    for (int i = 0; i < sites.size(); i++) {
      String clientName = "c" + i + "-tri";
      clientSlices.add(clientName);
      clientKeyMap.put(clientName, "key_p" + (keyBase + i));
      clientSiteMap.put(clientName, sites.get(i));
      clientIpMap.put(clientName, "192.168." + ipBase + ".1/24");
      ipBase += 10;
    }
  }

  @Inject
  public TridentSetting(Authority authority) {
    super(authority);
  }
}
