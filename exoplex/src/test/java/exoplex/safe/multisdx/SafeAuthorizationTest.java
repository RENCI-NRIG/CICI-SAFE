package exoplex.safe.multisdx;

import com.google.inject.Guice;
import com.google.inject.Injector;
import exoplex.common.utils.SafeUtils;
import exoplex.demo.multisdx.MultiSdxSetting;
import exoplex.sdx.advertise.AdvertiseManager;
import exoplex.sdx.advertise.PolicyAdvertise;
import exoplex.sdx.advertise.RouteAdvertise;
import exoplex.sdx.safe.SafeManager;
import exoplex.demo.multisdx.MultiSdxModule;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import safe.SdxRoutingSlang;
import safe.multisdx.AuthorityMockMultiSdx;

import java.util.ArrayList;
import java.util.HashMap;

@Ignore
public class SafeAuthorizationTest extends AuthorityMockMultiSdx {

  static SafeAuthorizationTest safeAuthorizationTest;
  static boolean sdpolicytest = false;
  HashMap<String, AdvertiseManager> bgpManagerHashMap = new HashMap<>();
  HashMap<String, SafeManager> safeManagerHashMap = new HashMap<>();
  String safeServerIP = "152.3.137.55";
  //static String safeServer = "139.62.242.15:7777";

  public SafeAuthorizationTest() {
    super(new MultiSdxSetting());
    safeServer = String.format("%s:7777", safeServerIP);
  }

  @BeforeClass
  public static void before() {
    Injector injector = Guice.createInjector(new MultiSdxModule());
    safeAuthorizationTest = injector.getInstance(SafeAuthorizationTest.class);
    safeAuthorizationTest.makeSafePreparation();
  }


  @Test
  public void testSafeRouting() {
    safeAuthorizationTest.testOwnPrefix();
    safeAuthorizationTest.initManagers();
    safeAuthorizationTest.testDestinationRouteAdvertisements();
    safeAuthorizationTest.testSDRouteAdvertisements();
  }

  void testDestinationRouteAdvertisements() {
    //c0 to sdx-1
    String[] safeparams = new String[3];

    RouteAdvertise routeAdvertise = new RouteAdvertise();
    String c0_keyhash = getPrincipalId(sliceKeyMap.get(testSetting.clientSlices
      .get(0)));
    String sdx1_keyhash = getPrincipalId(sliceKeyMap.get(testSetting.sdxSliceNames.get(0)));
    routeAdvertise.ownerPID = c0_keyhash;
    routeAdvertise.destPrefix = "192.168.10.1/24";
    routeAdvertise.advertiserPID = c0_keyhash;
    routeAdvertise.route.add(c0_keyhash);

    safeparams[0] = routeAdvertise.getDestPrefix();
    safeparams[1] = routeAdvertise.getFormattedPath();
    safeparams[2] = sdx1_keyhash;
    String token = SafeUtils.getToken(SafeUtils.postSafeStatements(safeServer, SdxRoutingSlang
      .postStartRoute, c0_keyhash, safeparams));

    routeAdvertise.safeToken = token;

    SafeManager safeManagerSdx1 = safeManagerHashMap.get(testSetting.sdxSliceNames.get(0));
    assert safeManagerSdx1.authorizeBgpAdvertise(routeAdvertise);

    RouteAdvertise advertise =
      bgpManagerHashMap.get(testSetting.sdxSliceNames.get(0))
      .receiveAdvertise(routeAdvertise).getRight();

    RouteAdvertise advertise1 = advertise;

    advertise1 = safeManagerSdx1.forwardAdvertise(advertise1, getPrincipalId(sliceKeyMap.get
      (testSetting.sdxSliceNames.get(1))), routeAdvertise.advertiserPID);


    SafeManager safeManagerSdx2 = safeManagerHashMap.get(testSetting.sdxSliceNames.get(1));
    assert safeManagerSdx2.authorizeBgpAdvertise(advertise1);

    AdvertiseManager advertiseManagerSdx2 = bgpManagerHashMap.get(testSetting.sdxSliceNames.get(1));

    RouteAdvertise advertise2 =
      advertiseManagerSdx2.receiveAdvertise(advertise1).getRight();

    advertise2 = safeManagerSdx2.forwardAdvertise(advertise2, getPrincipalId(sliceKeyMap.get
      (testSetting.sdxSliceNames.get(3))), advertise1.advertiserPID);

    SafeManager safeManagerSdx4 = safeManagerHashMap.get(testSetting.sdxSliceNames.get(3));
    assert safeManagerSdx4.authorizeBgpAdvertise(advertise2);
  }

  /**
   * The clientRouteASTagAcls is empty
   */
  void testSDRouteAdvertisements() {
    for(String slice: testSetting.clientSlices) {
      for (ImmutablePair<String, String> pair : testSetting.clientRouteASTagAcls.getOrDefault(slice,
        new ArrayList<>())) {
        String astag = getPrincipalId("tagauthority") + ":" + pair.getRight();
        String srcip = String.format("ipv4\\\"%s\\\"", pair.getLeft());
        String userIP = sliceIpMap.get(slice);
        String uip = String.format("ipv4\\\"%s\\\"", userIP);
        safePost(postASTagAclEntrySD, testSetting.clientKeyMap.get(slice), new String[]{astag, srcip, uip});
      }
    }
    //c0 to sdx-1
    RouteAdvertise routeAdvertise = new RouteAdvertise();
    String c0_keyhash = getPrincipalId(sliceKeyMap.get(testSetting.clientSlices
      .get(0)));
    String sdx1_keyhash = getPrincipalId(sliceKeyMap.get(testSetting.sdxSliceNames.get(0)));
    routeAdvertise.ownerPID = c0_keyhash;
    routeAdvertise.destPrefix = "192.168.10.1/24";
    routeAdvertise.srcPrefix = "192.168.30.1/24";
    routeAdvertise.advertiserPID = c0_keyhash;
    routeAdvertise.route.add(c0_keyhash);

    String[] safeparams = new String[4];
    safeparams[0] = routeAdvertise.getSrcPrefix();
    safeparams[1] = routeAdvertise.getDestPrefix();
    safeparams[2] = routeAdvertise.getFormattedPath();
    safeparams[3] = sdx1_keyhash;
    String token = SafeUtils.getToken(SafeUtils.postSafeStatements(safeServer, SdxRoutingSlang
      .postStartRouteSD, c0_keyhash, safeparams));

    routeAdvertise.safeToken = token;

    SafeManager safeManagerSdx1 = safeManagerHashMap.get(testSetting.sdxSliceNames.get(0));
    assert safeManagerSdx1.authorizeBgpAdvertise(routeAdvertise);

    RouteAdvertise advertise = bgpManagerHashMap.get(testSetting.sdxSliceNames.get
      (0)).receiveAdvertise(routeAdvertise).getRight();
    advertise = new RouteAdvertise(advertise, bgpManagerHashMap.get(testSetting.sdxSliceNames.get
      (0)).getMyPID());

    advertise = safeManagerSdx1.forwardAdvertise(advertise, getPrincipalId(sliceKeyMap.get
      (testSetting.sdxSliceNames.get(1))), routeAdvertise.advertiserPID);


    SafeManager safeManagerSdx2 = safeManagerHashMap.get(testSetting.sdxSliceNames.get(1));
    assert safeManagerSdx2.authorizeBgpAdvertise(advertise);

    AdvertiseManager advertiseManagerSdx2 = bgpManagerHashMap.get(testSetting.sdxSliceNames.get(1));

    RouteAdvertise advertise2 =
      advertiseManagerSdx2.receiveAdvertise(advertise).getRight();

    advertise2 = new RouteAdvertise(advertise2,
      advertiseManagerSdx2.getMyPID());

    advertise2 = safeManagerSdx2.forwardAdvertise(advertise2, getPrincipalId(sliceKeyMap.get
      (testSetting.sdxSliceNames.get(3))), advertise.advertiserPID);

    SafeManager safeManagerSdx4 = safeManagerHashMap.get(testSetting.sdxSliceNames.get(3));
    assert safeManagerSdx4.authorizeBgpAdvertise(advertise2);

    if (sdpolicytest) {
      String slicec0 = testSetting.clientSlices.get(0);
      SafeManager safeManager = safeManagerHashMap.get(slicec0);
      String tagAuthorityPid = getPrincipalId("tagauthority");
      String tag = String.format("%s:%s", tagAuthorityPid, "astag0");
      String aclToken = safeManager.postASTagAclEntrySD(tag, routeAdvertise.getDestPrefix(),
        routeAdvertise.getSrcPrefix());
      String sdToken = safeManager.postSdPolicySet(routeAdvertise.getDestPrefix(),
          routeAdvertise.getSrcPrefix());
      PolicyAdvertise policyAdvertise = new PolicyAdvertise();
      policyAdvertise.srcPrefix = routeAdvertise.destPrefix;
      policyAdvertise.destPrefix = routeAdvertise.srcPrefix;
      policyAdvertise.ownerPID = safeManager.getSafeKeyHash();
      policyAdvertise.route.add(safeManager.getSafeKeyHash());
      policyAdvertise.safeToken = sdToken;
    }
  }


  void initManagers() {
    for (String slice : slices) {
      safeManagerHashMap.put(slice, new SafeManager(safeServerIP, sliceKeyMap.get(slice), "~/" +
        ".ssh/id_rsa", true));
      bgpManagerHashMap.put(slice, new AdvertiseManager(getPrincipalId(sliceKeyMap.get(slice)),
        safeManagerHashMap.get(slice)));
    }
  }

  boolean testOwnPrefix() {
    for (String slice : slices) {
      if (!authorize(authorizeOwnPrefix,
        sliceKeyMap.get(slice),
        new String[]{getPrincipalId(sliceKeyMap.get(slice)),
          String.format("ipv4\\\"%s\\\"", sliceIpMap.get(slice))
        })) {
        assert false;
      }
    }
    return true;
  }
}
