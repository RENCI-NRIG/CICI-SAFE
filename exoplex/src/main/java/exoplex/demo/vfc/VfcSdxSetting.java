package exoplex.demo.vfc;

import exoplex.demo.AbstractTestSetting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class VfcSdxSetting extends AbstractTestSetting {
  public final String sdxConfigDir = exoplexDir + "vfc-config/";
  public String sliceNameSuffix = "test";
  public HashMap<String, String> vfcSiteMap;
  public HashMap<String, String> vfcVlanMap;
  public List<String> sites;
  public List<String> vlans;

  public VfcSdxSetting() {
    clientArgs = new String[]{"-c", exoplexDir +
      "client-config/vfc/client" + ".conf"};
    numSdx = 1;
    vfcSiteMap = new HashMap<>();
    vfcVlanMap = new HashMap<>();
    sites = new ArrayList<>();
    vlans = new ArrayList<>();
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
    clientConnectionPairs.add(new Integer[]{0, 1});
    //clientConnectionPairs.add(new Integer[]{1, 2});
    //clientConnectionPairs.add(new Integer[]{0, 2});
  }

  @Override
  public void addClientSites() {
    clientSites.add("UFL");
    sites.add("UC");
    vlans.add("3297");
    //clientSites.add("UH");
    clientSites.add("UFL");
    sites.add("UC");
    vlans.add("3294");
    //clientSites.add("UFL");
  }

  @Override
  public void addSdxSites() {
    sdxSites.put(sdxSliceNames.get(0), new String[]{"TAMU"});
  }

  @Override
  public void addSdxSlices() {
    int sdxKeyBase = 100;
    int sdxIpBase = 100;

    for (int i = 0; i < numSdx; i++) {
      String sdxSliceName = String.format("sdx-%s-" + sliceNameSuffix, i + 1);
      sdxConfs.put(sdxSliceName, String.format("%svfc%s.conf", sdxConfigDir, i + 1));
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
      vfcSiteMap.put(clientName, sites.get(i));
      vfcVlanMap.put(clientName, vlans.get(i));
      ipBase += 10;
    }
  }

  @Override
  public void setClientSdxMap() {
    clientSdxMap.put(clientSlices.get(0), sdxSliceNames.get(0));
    clientSdxMap.put(clientSlices.get(1), sdxSliceNames.get(0));
    //clientSdxMap.put(clientSlices.get(2), sdxSliceNames.get(0));
  }

  public void setUserConnectionTagAcls() {
    clientTags.put(clientSlices.get(0), Arrays.asList("tag0"));
    clientTags.put(clientSlices.get(1), Arrays.asList("tag0"));
    //clientTags.put(clientSlices.get(2), Arrays.asList(new String[]{"tag0"}));
  }
}
