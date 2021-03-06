package exoplex.demo.singlesdx;

import exoplex.demo.AbstractTestSetting;

import java.util.Arrays;

public class SingleSdxSetting extends AbstractTestSetting {
  public final String sdxConfigDir = exoplexDir + "config/singlesdx/";
  public String sliceNameSuffix = "test";

  public SingleSdxSetting() {
    clientArgs = new String[]{"-c", exoplexDir +
      "client-config/singlesdx/client" + ".conf"};
    numSdx = 1;

    addSdxSlices();
    addSdxSites();
    addClientSites();
    addClientSlices();
    setClientSdxMap();
    setUserConnectionTagAcls();
    addClientConnectionPairs();
  }

  @Override
  public void addClientConnectionPairs() {
    clientConnectionPairs.add(new Integer[]{0, 2});
    clientConnectionPairs.add(new Integer[]{1, 3});
    //clientConnectionPairs.add(new Integer[]{0, 2});
  }

  @Override
  public void addClientSites() {
    clientSites.add("UFL");
    clientSites.add("UFL");
    clientSites.add("UNF");
    clientSites.add("UNF");
  }

  @Override
  public void addSdxSites() {
    sdxSites.put(sdxSliceNames.get(0), new String[]{"UFL", "UNF"});
  }

  @Override
  public void addSdxSlices() {
    int sdxKeyBase = 100;
    int sdxIpBase = 100;

    for (int i = 0; i < numSdx; i++) {
      String sdxSliceName = String.format("sdx-%s-" + sliceNameSuffix, i + 1);
      sdxConfs.put(sdxSliceName, String.format("%ssdx%s.conf", sdxConfigDir, i + 1));
      String[] sdxArg = new String[]{"-c", sdxConfs.get(sdxSliceName), "-r"};
      String[] sdxNRArg = new String[]{"-c", sdxConfs.get(sdxSliceName)};
      sdxSliceNames.add(sdxSliceName);
      sdxArgs.put(sdxSliceName, sdxArg);
      sdxKeyMap.put(sdxSliceName, String.format("key_p%s", sdxKeyBase + i));
      sdxUrls.put(sdxSliceName, String.format("http://0.0.0.0:888%s/", i));
      sdxIpMap.put(sdxSliceName, String.format("192.168.%s.1/24", sdxIpBase));
      sdxIpBase += 20;
    }
  }

  @Override
  public void addClientSlices() {
    int keyBase = 10;
    int ipBase = 10;
    for (int i = 0; i < clientSites.size(); i++) {
      String clientName = "c" + i + "-" + sliceNameSuffix;
      clientSlices.add(clientName);
      clientKeyMap.put(clientName, "key_p" + (keyBase + i));
      clientSiteMap.put(clientName, clientSites.get(i));
      clientIpMap.put(clientName, "192.168." + ipBase + ".1/24");
      clientSdxMap.put(clientName, sdxSliceNames.get(0));
      ipBase += 10;
    }
  }

  @Override
  public void setClientSdxMap() {
    clientSdxMap.put(clientSlices.get(0), sdxSliceNames.get(0));
    clientSdxMap.put(clientSlices.get(1), sdxSliceNames.get(0));
    clientSdxMap.put(clientSlices.get(2), sdxSliceNames.get(0));
    clientSdxMap.put(clientSlices.get(3), sdxSliceNames.get(0));
    //clientSdxMap.put(clientSlices.get(2), sdxSliceNames.get(0));
  }

  public void setUserConnectionTagAcls() {
    clientTags.put(clientSlices.get(0), Arrays.asList("tag0"));
    clientTags.put(clientSlices.get(2), Arrays.asList("tag0"));
    clientTags.put(clientSlices.get(1), Arrays.asList("tag1"));
    clientTags.put(clientSlices.get(3), Arrays.asList("tag1"));
    //clientTags.put(clientSlices.get(2), Arrays.asList(new String[]{"tag0"}));
  }
}
