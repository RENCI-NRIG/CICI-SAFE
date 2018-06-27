package sdx.routing;
import common.slice.*;
import sdx.core.SdxManager;
import sdx.core.SliceManager;

public class TestMpRouting extends SdxManager {
  static String[] arg1 = {"-c", "config/sdx.conf"};

  public void main(String[] args) throws Exception {
    test();

  }

  public void test() throws Exception {
    SafeSlice slice = createTestSlice();
    slice.commitAndWait();
    configSdnControllerAddr(slice.getComputeNode("plexus").getManagementIP());
    checkPlexus(slice.getComputeNode("plexus").getManagementIP());
    slice.runCmdSlice(Scripts.getOVSScript(), sshkey, routerPattern, true);
    copyRouterScript(slice);
    configRouters(slice);
  }

  public SafeSlice createTestSlice(){
    parseCmd(arg1);
    getSshContext();
    SafeSlice slice = SafeSlice.create("test-yyj", pemLocation, keyLocation, controllerUrl, sctx);
    slice.addComputeNode("CNode0");
    slice.addComputeNode("CNode1");
    slice.addComputeNode("CNode2");
    slice.addComputeNode("c0");
    slice.addComputeNode("c1");
    slice.addComputeNode("c2");
    slice.addComputeNode("c3");
    slice.addBroadcastLink("stitch_c0_10");
    slice.attach("CNode0", "stitch_c0_10", "192.168.10.2", "255.255.255.0");
    slice.attach("c0", "stitch_c0_10", null, null);
    slice.addBroadcastLink("clink0");
    slice.attach("clink0", "c0");
    slice.attach("clink0", "c1");
    slice.addBroadcastLink("clink1");
    slice.attach("clink1", "c1");
    slice.attach("clink1", "c3");
    slice.addBroadcastLink("clink2");
    slice.attach("clink2", "c0");
    slice.attach("clink2", "c2");
    slice.addBroadcastLink("clink3");
    slice.attach("clink3", "c2");
    slice.attach("clink3", "c3");
    slice.addBroadcastLink("stitch_c3_20");
    slice.attach("stitch_c3_20", "CNode1", "192.168.20.2", "255.255.255.0");
    slice.attach("stitch_c3_20", "c3");
    slice.addBroadcastLink("stitch_c3_30");
    slice.attach("stitch_c3_30", "CNode2", "192.168.30.2", "255.255.255.0");
    slice.attach("stitch_c3_30", "c3");

    //
    slice.addOVSRouter(controllerSite, "plexus");
    return  slice;
  }
}
