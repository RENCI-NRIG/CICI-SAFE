package exoplex.demo;

import org.apache.commons.lang3.tuple.ImmutablePair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public abstract class AbstractTestSetting {
  public int numSdx;
  public String sliceNameSuffix = "test";
  public ArrayList<String> sdxSliceNames = new ArrayList<>();
  public HashMap<String, String[]> sdxArgs = new HashMap<>();
  public HashMap<String, String[]> sdxNoResetArgs = new HashMap<>();
  public HashMap<String, String> sdxConfs = new HashMap<>();
  public HashMap<String, String> sdxKeyMap = new HashMap<>();
  public HashMap<String, String> sdxIpMap = new HashMap<>();
  public HashMap<String, String> sdxUrls = new HashMap<>();
  public HashMap<String, List<String>> sdxASTags = new HashMap<>();
  public ArrayList<Integer[]> sdxNeighbor = new ArrayList<>();
  public HashMap<String, String[]> sdxSites = new HashMap<>();
  public ArrayList<String> clientSlices = new ArrayList<>();
  public HashMap<String, String> clientKeyMap = new HashMap<>();
  public HashMap<String, String> clientSiteMap = new HashMap<>();
  public HashMap<String, String> clientIpMap = new HashMap<>();
  public HashMap<String, String> clientSdxMap = new HashMap<>();
  public HashMap<String, String> clientSdxNode = new HashMap<>();
  public ArrayList<String> clientSites = new ArrayList<>();
  public HashMap<String, List<String>> clientASTagAcls = new HashMap<>();
  public HashMap<String, List<ImmutablePair<String, String>>> clientRouteASTagAcls = new
    HashMap<>();
  public HashMap<String, List<ImmutablePair<String, String>>> clientPolicyASTagAcls = new
    HashMap<>();
  public HashMap<String, List<String>> clientTags = new HashMap<>();
  public ArrayList<Integer[]> clientConnectionPairs = new ArrayList<>();
  public String userDir = System.getProperty("user.dir");
  public String sdxSimpleDir = userDir.split("SDX-Simple")[0] + "/SDX-Simple/";
  public String[] clientArgs;
  public String dockerImage = "safeserver-v7";
  public String safeServerScript = "sdx-routing.sh";
  String[] riakArgs = new String[]{"-c", sdxSimpleDir + "config/riak.conf"};
  String[] riakDelArgs = new String[]{"-c", sdxSimpleDir + "config/riak.conf", "-d"};

  public AbstractTestSetting() {
  }


  public void addClientSites() {
  }

  public void addSdxSites() {
  }

  public void addSdxSlices() {
  }

  public void addClientSlices() {
  }

  /*
  Pairs of clients that are assumed to be able to talk.
  We will check the connection between the pairs and run traceroutes between them.
  Note: Traceroute result for some pairs may be "***" because packets in two directions might
  take different paths.
   */
  public void addClientConnectionPairs() {
  }

  /*
  Which SDX serves as the NSP for the client (stitching and SDX)
   */
  public void setClientSdxMap() {
  }

  /*
  Client connection policies that an peer with specified tag (attribute) can talk to him.
  And the tags specified are also delegated to itself by the authorities. (We may also separate it)
   */
  public void setUserConnectionTagAcls() {
  }
}
