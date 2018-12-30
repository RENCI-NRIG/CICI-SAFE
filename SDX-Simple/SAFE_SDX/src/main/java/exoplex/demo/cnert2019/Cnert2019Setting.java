package exoplex.demo.cnert2019;

import exoplex.sdx.core.SliceManager;

  import java.util.ArrayList;
  import java.util.HashMap;

public class Cnert2019Setting extends SliceManager{
  public static final ArrayList<String> clientSites = new ArrayList<>();
  public static final ArrayList<String> sdxConfs = new ArrayList<>();
  public static final ArrayList<String> sdxSliceNames = new ArrayList<>();
  public static final HashMap<String, String> sdxKeyMap = new HashMap<>();
  public static final HashMap<String, String> sdxIpMap = new HashMap<>();
  public static final HashMap<String, String> sdxUrls = new HashMap<>();
  public static final int numSdx = 2;

  final static String sdxName = "sdx-tri";
  static String userDir = System.getProperty("user.dir");
  static String sdxSimpleDir = userDir.split("SDX-Simple")[0] + "SDX-Simple/";
  public final static String sdxConfigDir = sdxSimpleDir + "config/cnert2019/";
  public final static ArrayList<String[]> sdxArgs = new ArrayList<>();
  public static String[] clientArgs = new String[]{"-c", sdxSimpleDir + "client-config/client" +
    ".conf"};

  static {
    //clientSites.add("RENCI");
    clientSites.add("TAMU");
    clientSites.add("UFL");
    //clientSites.add("UH");
    clientSites.add("UNF");
    //clientSites.add("SL");
    //clientSites.add("GWU");
    //clientSites.add("UMASS");
    //clientSites.add("UNF");
    //clientSites.add("WSU");
  }

  static{
    int sdxKeyBase = 100;
    int sdxIpBase = 100;

    for (int i = 0; i< numSdx; i++){
      sdxConfs.add(String.format("%ssdx%s.conf", sdxConfigDir, i + 1));
      String[] sdxArg = new String[]{"-c", sdxConfs.get(i), "-r"};
      sdxArgs.add(sdxArg);
      String sdxSliceName = String.format("sdx-%s-cnert", i + 1);
      sdxSliceNames.add(sdxSliceName);
      sdxKeyMap.put(sdxSliceName, String.format("key_p%s", sdxKeyBase + i));
      sdxUrls.put(sdxSliceName, String.format("http://127.0.0.1:888%s/", i));
      sdxIpMap.put(sdxSliceName, String.format("192.168.%s.1/24", sdxIpBase));
      sdxIpBase += 20;
    }
  }

  public static final ArrayList<String> clientSlices = new ArrayList<>();

  public static final HashMap<String, String> clientKeyMap = new HashMap<>();

  public static final HashMap<String, String> clientSiteMap = new HashMap<>();

  public static final HashMap<String, String> clientIpMap = new HashMap<>();

  public static final HashMap<String, String> clientSdxMap = new HashMap<>();

  static {
    int keyBase = 10;
    int ipBase = 10;
    for (int i=0; i<clientSites.size(); i++){
      String clientName = "c" + i + "-tri";
      clientSlices.add(clientName);
      clientKeyMap.put(clientName, "key_p" + (keyBase + i));
      clientSiteMap.put(clientName, clientSites.get(i));
      clientIpMap.put(clientName, "192.168." + ipBase + ".1/24");
      clientSdxMap.put(clientName, Cnert2019Setting.sdxSliceNames.get(((i+1)/Cnert2019Setting
        .sdxSliceNames.size())));
      ipBase += 10;
    }
  }
}
