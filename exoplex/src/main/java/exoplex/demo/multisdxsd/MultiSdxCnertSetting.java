package exoplex.demo.multisdxsd;
import org.apache.commons.lang3.tuple.ImmutablePair;

import java.util.Arrays;

public class MultiSdxCnertSetting extends MultiSdxTridentcomSetting{
  int keyBase;
  int ipBase;
  String[] clientNames;

  public MultiSdxCnertSetting() {
  }

  @Override
  public void setting() {
    numSdx = 6;
    keyBase = 10;
    ipBase = 10;
    clientNames = new String[]{"c0-cn", "c1-cn", "c2-cn", "c3-cn"};
    sliceNameSuffix = "tc";
    clientArgs = new String[]{"-c", exoplexDir +
      "client-config/multisdx/client" + ".conf"};

    addSdxNeighbors();
    addClientConnectionPairs();
    addClientSites();
    addSdxSlices();
    addSdxSites();
    addClientSlices();
    setClientSdxMap();
    setSdxASTags();
    setClientASTagAcls();
    setClientASTagAclsForSD();
    setUserConnectionTagAcls();

  }

  @Override
  public void addClientSlices() {
    for (int i = 0; i < clientSites.size() - 1; i++) {
      addClientSlice(i);
    }
  }

  private void addClientSlice(int i) {
    String clientName = clientNames[i];
    clientSlices.add(clientName);
    clientKeyMap.put(clientName, "key_p" + (keyBase + i));
    clientSiteMap.put(clientName, clientSites.get(i));
    clientIpMap.put(clientName, "192.168." + ipBase + ".1/24");
    ipBase += 10;
  }



  @Override
  public void setClientSdxMap() {
    clientSdxMap.put(clientSlices.get(0), sdxSliceNames.get(0));
    clientSdxMap.put(clientSlices.get(1), sdxSliceNames.get(0));
    clientSdxMap.put(clientSlices.get(2), sdxSliceNames.get(5));
    clientSdxNode.put(clientSlices.get(0), "e0");
    clientSdxNode.put(clientSlices.get(1), "e0");
    clientSdxNode.put(clientSlices.get(2), "e1");
    //clientSdxMap.put(clientSlices.get(3), sdxSliceNames.get(5));
    //clientSdxNode.put(clientSlices.get(3), "e1");
  }

  @Override
  public void addClientSites() {
    clientSites.add("TAMU");
    clientSites.add("TAMU");
    clientSites.add("UFL");
    clientSites.add("UFL");
  }

  @Override
  public void addSdxSites() {
    String[] sites = new String[]{"TAMU", "UH", "UH", "UH", "UFL"};
    sdxSites.put(sdxSliceNames.get(0), new String[]{sites[0], sites[1]});
    sdxSites.put(sdxSliceNames.get(1), new String[]{sites[1], sites[2]});
    sdxSites.put(sdxSliceNames.get(2), new String[]{sites[1], sites[2]});
    sdxSites.put(sdxSliceNames.get(3), new String[]{sites[2], sites[3]});
    sdxSites.put(sdxSliceNames.get(4), new String[]{sites[2], sites[3]});
    sdxSites.put(sdxSliceNames.get(5), new String[]{sites[3], sites[4]});
  }

  @Override
  public void addClientConnectionPairs() {
    clientConnectionPairs.add(new Integer[]{0, 2});
    clientConnectionPairs.add(new Integer[]{1, 2});
    //clientConnectionPairs.add(new Integer[]{0, 3});
    //clientConnectionPairs.add(new Integer[]{1, 3});
  }

  @Override
  public void setSdxASTags() {
    sdxASTags.put(sdxSliceNames.get(0), Arrays.asList("tag0", "tag1", "tag2"));
    sdxASTags.put(sdxSliceNames.get(1), Arrays.asList("tag0", "tag2"));
    sdxASTags.put(sdxSliceNames.get(2), Arrays.asList("tag1"));
    sdxASTags.put(sdxSliceNames.get(3), Arrays.asList("tag0"));
    sdxASTags.put(sdxSliceNames.get(4), Arrays.asList("tag1", "tag2"));
    sdxASTags.put(sdxSliceNames.get(5), Arrays.asList("tag0", "tag1", "tag2"));
  }

  @Override
  public void setClientASTagAcls() {
    clientASTagAcls.put(clientSlices.get(0), Arrays.asList("tag0", "tag2"));
    clientASTagAcls.put(clientSlices.get(2), Arrays.asList("tag0", "tag1"));
    clientASTagAcls.put(clientSlices.get(1), Arrays.asList("tag0", "tag1"));
    //clientASTagAcls.put(clientSlices.get(3), Arrays.asList("tag1", "tag2"));
  }

  @Override
  public void setUserConnectionTagAcls() {
    clientTags.put(clientSlices.get(0), Arrays.asList("tag0", "tag2"));
    clientTags.put(clientSlices.get(2), Arrays.asList("tag0", "tag1"));
    clientTags.put(clientSlices.get(1), Arrays.asList("tag0", "tag1"));
    //clientTags.put(clientSlices.get(3), Arrays.asList("tag1", "tag2"));
  }

  @Override
  public void setClientASTagAclsForSD() {
    //tag for inbound traffic
    clientRouteASTagAcls.put(clientSlices.get(0), Arrays.asList(new ImmutablePair<String, String>("192.168.30.1/24", "tag0"),
      new ImmutablePair<String, String>("192.168.40.1/24", "tag2")));
    clientRouteASTagAcls.put(clientSlices.get(1), Arrays.asList(new ImmutablePair<String, String>("192.168.30.1/24", "tag0"),
      new ImmutablePair<String, String>("192.168.40.1/24", "tag1")));
    clientRouteASTagAcls.put(clientSlices.get(2), Arrays.asList(new ImmutablePair<String, String>("192.168.10.1/24", "tag0")
      , new ImmutablePair<String, String>("192.168.20.1/24", "tag1")));
    //policy for outbound traffic
    clientPolicyASTagAcls.put(clientSlices.get(0), Arrays.asList(new ImmutablePair<String, String>("192.168.30.1/24", "tag0"),
      new ImmutablePair<String, String>("192.168.40.1/24", "tag2")));
    clientPolicyASTagAcls.put(clientSlices.get(1), Arrays.asList(new ImmutablePair<String, String>("192.168.30.1/24", "tag0"),
      new ImmutablePair<String, String>("192.168.40.1/24", "tag1")));
    clientPolicyASTagAcls.put(clientSlices.get(2), Arrays.asList(new ImmutablePair<String, String>("192.168.10.1/24", "tag0")
      , new ImmutablePair<String, String>("192.168.20.1/24", "tag1")));
    //clientPolicyASTagAcls.put(clientSlices.get(3), Arrays.asList(new ImmutablePair<String, String>("192.168.20.1/24", "tag1"),
    //  new ImmutablePair<String, String>("192.168.10.1/24", "tag2")));
    //clientRouteASTagAcls.put(clientSlices.get(3), Arrays.asList(new ImmutablePair<String, String>("192.168.20.1/24", "tag1"),
    //  new ImmutablePair<String, String>("192.168.10.1/24", "tag2")));
  }

  public void joinLastClient() {
    addClientSlice(clientSites.size() - 1);
    clientSdxMap.put(clientSlices.get(3), sdxSliceNames.get(5));

    clientSdxNode.put(clientSlices.get(3), "e1");
    clientConnectionPairs.add(new Integer[]{0, 3});
    clientConnectionPairs.add(new Integer[]{1, 3});
    clientASTagAcls.put(clientSlices.get(3), Arrays.asList("tag1", "tag2"));
    clientTags.put(clientSlices.get(3), Arrays.asList("tag1", "tag2"));
    clientPolicyASTagAcls.put(clientSlices.get(3), Arrays.asList(new ImmutablePair<String, String>("192.168.20.1/24", "tag1"),
      new ImmutablePair<String, String>("192.168.10.1/24", "tag2")));
    clientRouteASTagAcls.put(clientSlices.get(3), Arrays.asList(new ImmutablePair<String, String>("192.168.20.1/24", "tag1"),
      new ImmutablePair<String, String>("192.168.10.1/24", "tag2")));
  }
}
