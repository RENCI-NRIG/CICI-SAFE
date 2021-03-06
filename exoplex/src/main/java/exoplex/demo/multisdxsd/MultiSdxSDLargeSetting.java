package exoplex.demo.multisdxsd;

import exoplex.demo.AbstractTestSetting;
import org.apache.commons.lang3.tuple.ImmutablePair;

import java.util.Arrays;

public class MultiSdxSDLargeSetting extends AbstractTestSetting {
  public String sdxConfigDir = exoplexDir + "config/multisdx/";

  public MultiSdxSDLargeSetting() {
    setting();
  }

  public void setting() {
    numSdx = 6;
    sliceNameSuffix = "tri";
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

  public void addSdxNeighbors() {
    sdxNeighbor.add(new Integer[]{0, 1});
    sdxNeighbor.add(new Integer[]{1, 3});
    sdxNeighbor.add(new Integer[]{0, 2});
    sdxNeighbor.add(new Integer[]{2, 3});
    sdxNeighbor.add(new Integer[]{2, 4});
    sdxNeighbor.add(new Integer[]{1, 4});
    sdxNeighbor.add(new Integer[]{3, 5});
    sdxNeighbor.add(new Integer[]{4, 5});
  }

  @Override
  public void addClientConnectionPairs() {
    clientConnectionPairs.add(new Integer[]{0, 2});
    clientConnectionPairs.add(new Integer[]{1, 3});
    clientConnectionPairs.add(new Integer[]{0, 3});
    clientConnectionPairs.add(new Integer[]{1, 2});
  }

  @Override
  public void addClientSites() {
    clientSites.add("TAMU");
    clientSites.add("TAMU");
    clientSites.add("TAMU");
    clientSites.add("TAMU");
  }

  @Override
  public void addSdxSites() {
    String[] sites = new String[]{"TAMU", "UH", "UH", "UH", "TAMU"};
    sdxSites.put(sdxSliceNames.get(0), new String[]{sites[0], sites[1]});
    sdxSites.put(sdxSliceNames.get(1), new String[]{sites[1], sites[2]});
    sdxSites.put(sdxSliceNames.get(2), new String[]{sites[1], sites[2]});
    sdxSites.put(sdxSliceNames.get(3), new String[]{sites[2], sites[3]});
    sdxSites.put(sdxSliceNames.get(4), new String[]{sites[2], sites[3]});
    sdxSites.put(sdxSliceNames.get(5), new String[]{sites[3], sites[4]});
  }

  @Override
  public void addSdxSlices() {
    int sdxKeyBase = 100;
    int sdxIpBase = 110;
    for (int i = 0; i < numSdx; i++) {
      String sdxSliceName = String.format("sdx-%s-%s", i + 1, sliceNameSuffix);
      sdxConfs.put(sdxSliceName, String.format("%ssdx%s.conf", sdxConfigDir, i + 1));
      String[] sdxArg = new String[]{"-c", sdxConfs.get(sdxSliceName), "-r"};
      String[] sdxNRArg = new String[]{"-c", sdxConfs.get(sdxSliceName)};
      sdxSliceNames.add(sdxSliceName);
      sdxArgs.put(sdxSliceName, sdxArg);
      sdxKeyMap.put(sdxSliceName, String.format("key_p%s", sdxKeyBase + i));
      sdxUrls.put(sdxSliceName, String.format("http://0.0.0.0:888%s/", i));
      sdxIpMap.put(sdxSliceName, String.format("192.168.%s.1/24", sdxIpBase));
      sdxIpBase += 10;
    }
  }

  @Override
  public void addClientSlices() {
    int keyBase = 10;
    int ipBase = 10;
    for (int i = 0; i < clientSites.size(); i++) {
      String clientName = String.format("c%s-%s", i, sliceNameSuffix);
      clientSlices.add(clientName);
      clientKeyMap.put(clientName, "key_p" + (keyBase + i));
      clientSiteMap.put(clientName, clientSites.get(i));
      clientIpMap.put(clientName, "192.168." + ipBase + ".1/24");
      ipBase += 10;
    }
  }

  @Override
  public void setClientSdxMap() {
    clientSdxMap.put(clientSlices.get(0), sdxSliceNames.get(0));
    clientSdxMap.put(clientSlices.get(1), sdxSliceNames.get(0));
    clientSdxMap.put(clientSlices.get(2), sdxSliceNames.get(5));
    clientSdxMap.put(clientSlices.get(3), sdxSliceNames.get(5));
    clientSdxNode.put(clientSlices.get(0), "e0");
    clientSdxNode.put(clientSlices.get(1), "e0");
    clientSdxNode.put(clientSlices.get(2), "e1");
    clientSdxNode.put(clientSlices.get(3), "e1");
  }

  public void setSdxASTags() {
    sdxASTags.put(sdxSliceNames.get(0), Arrays.asList("astag0", "astag1"));
    sdxASTags.put(sdxSliceNames.get(1), Arrays.asList("astag0"));
    sdxASTags.put(sdxSliceNames.get(2), Arrays.asList("astag1"));
    sdxASTags.put(sdxSliceNames.get(3), Arrays.asList("astag0"));
    sdxASTags.put(sdxSliceNames.get(4), Arrays.asList("astag1"));
    sdxASTags.put(sdxSliceNames.get(5), Arrays.asList("astag0", "astag1"));
  }

  public void setClientASTagAcls() {
    clientASTagAcls.put(clientSlices.get(0), Arrays.asList("astag0"));
    clientASTagAcls.put(clientSlices.get(2), Arrays.asList("astag0"));
    clientASTagAcls.put(clientSlices.get(1), Arrays.asList("astag1"));
    clientASTagAcls.put(clientSlices.get(3), Arrays.asList("astag1"));
  }

  @Override
  public void setUserConnectionTagAcls() {
    clientTags.put(clientSlices.get(0), Arrays.asList("tag0", "tag2"));
    clientTags.put(clientSlices.get(2), Arrays.asList("tag0", "tag3"));
    clientTags.put(clientSlices.get(1), Arrays.asList("tag1", "tag3"));
    clientTags.put(clientSlices.get(3), Arrays.asList("tag1", "tag2"));
  }

  public void setClientASTagAclsForSD() {
    //astag for traffic from pair.left to self.prefix
    clientRouteASTagAcls.put(clientSlices.get(1), Arrays.asList(new ImmutablePair<String, String>("192.168.40.1/24", "astag1")));
    clientPolicyASTagAcls.put(clientSlices.get(1), Arrays.asList(new ImmutablePair<String, String>("192.168.40.1/24", "astag0"),
      new ImmutablePair<String, String>("192.168.40.1/24", "astag1")));
    clientRouteASTagAcls.put(clientSlices.get(3), Arrays.asList(new ImmutablePair<String, String>("192.168.20.1/24", "astag1")));
    clientPolicyASTagAcls.put(clientSlices.get(3), Arrays.asList(new ImmutablePair<String, String>("192.168.20.1/24", "astag0"),
      new ImmutablePair<String, String>("192.168.20.1/24", "astag1")));
  }
}
