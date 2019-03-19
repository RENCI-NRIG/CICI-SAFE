package exoplex.demo.multisdx;

  import exoplex.sdx.core.SliceHelper;
  import org.apache.commons.lang3.tuple.ImmutablePair;

  import java.util.ArrayList;
  import java.util.Arrays;
  import java.util.HashMap;
  import java.util.List;

public class MultiSdxSDSetting extends SliceHelper {
  public static final ArrayList<String> clientSites = new ArrayList<>();
  public static final ArrayList<String> sdxConfs = new ArrayList<>();
  public static final ArrayList<String> sdxSliceNames = new ArrayList<>();
  public static final HashMap<String, String> sdxKeyMap = new HashMap<>();
  public static final HashMap<String, String> sdxIpMap = new HashMap<>();
  public static final HashMap<String, String> sdxUrls = new HashMap<>();
  public static final HashMap<String, List<String>> sdxASTags = new HashMap<>();
  public static final HashMap<String, List<String>> userASTagAcls = new HashMap<>();
  public static final HashMap<String, List<ImmutablePair<String, String>>> userSDASTagAcls = new
    HashMap<>();
  public static final HashMap<String, List<String>> userTags = new HashMap<>();
  public static final int numSdx = 4;

  final static String sdxName = "sdx-tri";
  static String userDir = System.getProperty("user.dir");
  static String sdxSimpleDir = userDir.split("SDX-Simple")[0] + "/SDX-Simple/";
  public final static String sdxConfigDir = sdxSimpleDir + "config/multisdx/";
  public final static HashMap<String, String[]> sdxArgs = new HashMap<>();
  public final static HashMap<String, String[]> sdxNoResetArgs = new HashMap<>();
  public static String[] clientArgs = new String[]{"-c", sdxSimpleDir +
    "client-config/multisdx/client" + ".conf"};
  public static ArrayList<Integer[]> sdxNeighbor = new ArrayList<>();
  public static ArrayList<Integer[]> customerConnectionPairs = new ArrayList<>();

  static {
    sdxNeighbor.add(new Integer[]{0,1});
    sdxNeighbor.add(new Integer[]{1,3});
    sdxNeighbor.add(new Integer[]{0,2});
    sdxNeighbor.add(new Integer[]{2,3});
  }

  static {
    customerConnectionPairs.add(new Integer[]{0,2});
    customerConnectionPairs.add(new Integer[]{1,3});
  }

  static {
    //clientSites.add("RENCI");
    clientSites.add("UH");
    clientSites.add("UH");
    //clientSites.add("UNF");
    clientSites.add("UNF");
    clientSites.add("UNF");
    //clientSites.add("UNF");
    //clientSites.add("UNF");
    //clientSites.add("UNF");
    //clientSites.add("UNF");
    //clientSites.add("UNF");
  }

  public static ArrayList<String[]> sdxSites = new ArrayList<>();
  static {
    sdxSites.add(new String[]{"UH", "SL"});
    sdxSites.add(new String[]{"SL", "UFL"});
    sdxSites.add(new String[]{"SL", "UFL"});
    sdxSites.add(new String[]{"UFL", "UNF"});
  }

  static{
    int sdxKeyBase = 100;
    int sdxIpBase = 100;

    for (int i = 0; i< numSdx; i++){
      sdxConfs.add(String.format("%ssdx%s.conf", sdxConfigDir, i + 1));
      String[] sdxArg = new String[]{"-c", sdxConfs.get(i), "-r"};
      String[] sdxNRArg = new String[]{"-c", sdxConfs.get(i)};
      String sdxSliceName = String.format("sdx-%s-cn", i + 1);
      sdxSliceNames.add(sdxSliceName);
      sdxArgs.put(sdxSliceName, sdxArg);
      sdxNoResetArgs.put(sdxSliceName, sdxNRArg);
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
      String clientName = "c" + i + "-cn";
      clientSlices.add(clientName);
      clientKeyMap.put(clientName, "key_p" + (keyBase + i));
      clientSiteMap.put(clientName, clientSites.get(i));
      clientIpMap.put(clientName, "192.168." + ipBase + ".1/24");
      clientSdxMap.put(clientName, MultiSdxSDSetting.sdxSliceNames.get(((i+1)/ MultiSdxSDSetting
        .sdxSliceNames.size())));
      ipBase += 10;
    }
  }
  static {
    clientSdxMap.put(clientSlices.get(0), sdxSliceNames.get(0));
    clientSdxMap.put(clientSlices.get(1), sdxSliceNames.get(0));
    clientSdxMap.put(clientSlices.get(2), sdxSliceNames.get(3));
    clientSdxMap.put(clientSlices.get(3), sdxSliceNames.get(3));
  }

  static {
    sdxASTags.put(sdxSliceNames.get(0), Arrays.asList(new String[]{"astag0", "astag1"}));
    sdxASTags.put(sdxSliceNames.get(1), Arrays.asList(new String[]{"astag0"}));
    sdxASTags.put(sdxSliceNames.get(2), Arrays.asList(new String[]{"astag1"}));
    sdxASTags.put(sdxSliceNames.get(3), Arrays.asList(new String[]{"astag0", "astag1"}));
  }
  static {
    //userASTagAcls.put(clientSlices.get(0), Arrays.asList(new String[]{"astag0"}));
    //userASTagAcls.put(clientSlices.get(2), Arrays.asList(new String[]{"astag0"}));
    //userASTagAcls.put(clientSlices.get(1), Arrays.asList(new String[]{"astag1"}));
    //userASTagAcls.put(clientSlices.get(3), Arrays.asList(new String[]{"astag1"}));
    userSDASTagAcls.put(clientSlices.get(0), Arrays.asList(new ImmutablePair[]{new
      ImmutablePair<String, String>("192.168.30.1/24", "astag0")}));
    userSDASTagAcls.put(clientSlices.get(2), Arrays.asList(new ImmutablePair[]{new
      ImmutablePair<String, String>("192.168.10.1/24", "astag0")}));
    userSDASTagAcls.put(clientSlices.get(1), Arrays.asList(new ImmutablePair[]{new
      ImmutablePair<String, String>("192.168.40.1/24", "astag1")}));
    userSDASTagAcls.put(clientSlices.get(3), Arrays.asList(new ImmutablePair[]{new
      ImmutablePair<String, String>("192.168.20.1/24", "astag1")}));
  }

  static {
    userTags.put(clientSlices.get(0), Arrays.asList(new String[]{"tag0"}));
    userTags.put(clientSlices.get(2), Arrays.asList(new String[]{"tag0"}));
    userTags.put(clientSlices.get(1), Arrays.asList(new String[]{"tag1"}));
    userTags.put(clientSlices.get(3), Arrays.asList(new String[]{"tag1"}));
  }
}