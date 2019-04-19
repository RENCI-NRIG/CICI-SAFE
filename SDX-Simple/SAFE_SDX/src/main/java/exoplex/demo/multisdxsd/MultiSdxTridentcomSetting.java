package exoplex.demo.multisdxsd;

import org.apache.commons.lang3.tuple.ImmutablePair;

import java.util.Arrays;

public class MultiSdxTridentcomSetting extends MultiSdxSDLargeSetting{

  public MultiSdxTridentcomSetting(){
  }

  @Override
  public void setting(){
    numSdx = 6;
    clientArgs = new String[]{"-c", sdxSimpleDir +
      "client-config/multisdx/client" + ".conf"};

    addSdxNeighbors();
    addClientConnectionPairs();
    addClientSites();
    addSdxSlices();
    addSdxSites();
    addClientSlices();
    setClientSdxMap();
    setSdxASTags();
    //setClientASTagAcls();
    setClientASTagAclsForSD();
    setUserConnectionTagAcls();

  }

  @Override
  public void addClientConnectionPairs() {
    clientConnectionPairs.add(new Integer[]{0, 2});
    clientConnectionPairs.add(new Integer[]{1, 3});
    clientConnectionPairs.add(new Integer[]{1, 2});
  }

  @Override
  public void setSdxASTags() {
    sdxASTags.put(sdxSliceNames.get(0), Arrays.asList(new String[]{"tag0", "tag1"}));
    sdxASTags.put(sdxSliceNames.get(1), Arrays.asList(new String[]{"tag0"}));
    sdxASTags.put(sdxSliceNames.get(2), Arrays.asList(new String[]{"tag1"}));
    sdxASTags.put(sdxSliceNames.get(3), Arrays.asList(new String[]{"tag0"}));
    sdxASTags.put(sdxSliceNames.get(4), Arrays.asList(new String[]{"tag1"}));
    sdxASTags.put(sdxSliceNames.get(5), Arrays.asList(new String[]{"tag0", "tag1"}));
  }

  @Override
  public void setClientASTagAcls() {
    clientASTagAcls.put(clientSlices.get(0), Arrays.asList(new String[]{"tag0"}));
    clientASTagAcls.put(clientSlices.get(2), Arrays.asList(new String[]{"tag0", "tag1"}));
    clientASTagAcls.put(clientSlices.get(1), Arrays.asList(new String[]{"tag0", "tag1"}));
    clientASTagAcls.put(clientSlices.get(3), Arrays.asList(new String[]{"tag1"}));
  }

  @Override
  public void setUserConnectionTagAcls() {
    clientTags.put(clientSlices.get(0), Arrays.asList(new String[]{"tag0"}));
    clientTags.put(clientSlices.get(2), Arrays.asList(new String[]{"tag0", "tag1"}));
    clientTags.put(clientSlices.get(1), Arrays.asList(new String[]{"tag0", "tag1"}));
    clientTags.put(clientSlices.get(3), Arrays.asList(new String[]{"tag1"}));
  }

  @Override
  public void setClientASTagAclsForSD() {
    //tag for traffic from pair.left to self.prefix
    clientRouteASTagAcls.put(clientSlices.get(0), Arrays.asList(new ImmutablePair[]{
      new ImmutablePair<String, String>("192.168.30.1/24", "tag0")
    }));
    clientPolicyASTagAcls.put(clientSlices.get(0), Arrays.asList(new ImmutablePair[]{
      new ImmutablePair<String, String>("192.168.30.1/24", "tag0")
    }));
    clientRouteASTagAcls.put(clientSlices.get(2), Arrays.asList(new ImmutablePair[]{
      new ImmutablePair<String, String>("192.168.10.1/24", "tag0")
      , new ImmutablePair<String, String>("192.168.10.1/24", "tag1")
    }));
    clientPolicyASTagAcls.put(clientSlices.get(2), Arrays.asList(new ImmutablePair[]{
      new ImmutablePair<String, String>("192.168.10.1/24", "tag0")
      ,new ImmutablePair<String, String>("192.168.10.1/24", "tag1")
    }));
    clientRouteASTagAcls.put(clientSlices.get(1), Arrays.asList(new ImmutablePair[]{
      new ImmutablePair<String, String>("192.168.40.1/24", "tag0"),
      new ImmutablePair<String, String>("192.168.40.1/24", "tag1")
    }));
    clientPolicyASTagAcls.put(clientSlices.get(1), Arrays.asList(new ImmutablePair[]{
      new ImmutablePair<String, String>("192.168.40.1/24", "tag0"),
      new ImmutablePair<String, String>("192.168.40.1/24", "tag1")
    }));
    clientRouteASTagAcls.put(clientSlices.get(3), Arrays.asList(new ImmutablePair[]{
      new ImmutablePair<String, String>("192.168.20.1/24", "tag1")
    }));
    clientPolicyASTagAcls.put(clientSlices.get(3), Arrays.asList(new ImmutablePair[]{
      new ImmutablePair<String, String>("192.168.20.1/24", "tag1")
    }));


    clientRouteASTagAcls.put(clientSlices.get(1), Arrays.asList(new ImmutablePair[]{
      new ImmutablePair<String, String>("192.168.30.1/24", "tag0"),
      new ImmutablePair<String, String>("192.168.30.1/24", "tag1")
    }));
    clientPolicyASTagAcls.put(clientSlices.get(1), Arrays.asList(new ImmutablePair[]{
      new ImmutablePair<String, String>("192.168.30.1/24", "tag0"),
      new ImmutablePair<String, String>("192.168.30.1/24", "tag1")
    }));
    clientRouteASTagAcls.put(clientSlices.get(2), Arrays.asList(new ImmutablePair[]{
      new ImmutablePair<String, String>("192.168.20.1/24", "tag0")
      , new ImmutablePair<String, String>("192.168.20.1/24", "tag1")
    }));
    clientPolicyASTagAcls.put(clientSlices.get(2), Arrays.asList(new ImmutablePair[]{
      new ImmutablePair<String, String>("192.168.20.1/24", "tag0")
      ,new ImmutablePair<String, String>("192.168.20.1/24", "tag1")
    }));
  }
}
