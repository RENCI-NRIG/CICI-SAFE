package sdx.routing;
import common.slice.*;
import org.apache.commons.cli.CommandLine;
import org.renci.ahab.libndl.resources.request.Interface;
import org.renci.ahab.libndl.resources.request.InterfaceNode2Net;
import sdx.core.SdxManager;
import sdx.core.SliceManager;
import test.Test;

public class TestMpRouting extends SdxManager {
  static String[] arg1 = {"-c", "config/test-mptcp.conf"};
  static String site= SiteBase.get("BBN");

  public static void main(String[] args) throws Exception {
    TestMpRouting mpr = new TestMpRouting();
    mpr.test();
    mpr.initNetwork();
    System.out.println("end");
  }

  public  void test() throws Exception {
    CommandLine cmd = parseCmd(arg1);
    String configfilepath = cmd.getOptionValue("config");
    readConfig(configfilepath);
    getSshContext();
    createNetwork();
  }

  public void initNetwork() throws Exception {
    CommandLine cmd = parseCmd(arg1);
    String configfilepath = cmd.getOptionValue("config");
    readConfig(configfilepath);
    getSshContext();
    plexusName = "plexus";
    readConfig(arg1);
    initializeSdx();
    int i=0;
    for(Interface intf: serverSlice.getInterfaces()){
      i++;
      System.out.println(String.format("=====%s=====", i));
      System.out.println(((InterfaceNode2Net)intf).getMacAddress());
      System.out.println(intf.getName());
    }
    delFlows();
    configRouting();
  }

  public void createNetwork() throws Exception {
    SafeSlice slice = createTestSlice();
    slice.commitAndWait();
    configSdnControllerAddr(slice.getComputeNode("plexus").getManagementIP());
    checkPlexus(slice.getComputeNode("plexus").getManagementIP());
    slice.runCmdSlice(Scripts.getOVSScript(), sshkey, routerPattern, true);
    copyRouterScript(slice);
    configRouters(slice);
  }
  public SafeSlice createTestSlice(){
    SafeSlice slice = SafeSlice.create("test-yyj", pemLocation, keyLocation, controllerUrl, sctx);
    slice.addOVSRouter(site, "CNode0");
    slice.addOVSRouter(site,"CNode1");
    slice.addOVSRouter(site,"CNode2");
    slice.addOVSRouter(site,"c0");
    slice.addOVSRouter(site,"c1");
    slice.addOVSRouter(site,"c2");
    slice.addOVSRouter(site,"c3");
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
    slice.addPlexusController(controllerSite, "plexus");
    return  slice;
  }
}
