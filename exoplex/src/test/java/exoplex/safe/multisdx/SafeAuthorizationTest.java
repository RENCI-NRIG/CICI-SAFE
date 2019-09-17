package exoplex.safe.multisdx;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import exoplex.common.utils.SafeUtils;
import exoplex.demo.AbstractTestSetting;
import exoplex.sdx.advertise.AdvertiseManager;
import exoplex.sdx.advertise.PolicyAdvertise;
import exoplex.sdx.advertise.RouteAdvertise;
import exoplex.sdx.safe.SafeManager;
import exoplex.demo.multisdx.MultiSdxModule;
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
  static String safeServerIP = "152.3.136.36";
  static String safeServer = String.format("%s:7777", safeServerIP);
  static boolean sdpolicytest = false;
  HashMap<String, AdvertiseManager> bgpManagerHashMap = new HashMap<>();
  HashMap<String, SafeManager> safeManagerHashMap = new HashMap<>();
  //static String safeServer = "139.62.242.15:7777";

  @Inject
  public SafeAuthorizationTest(AbstractTestSetting testSetting) {
    super(testSetting);
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
    String[] safeparams = new String[4];

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
    safeparams[3] = String.valueOf(1);
    String token = SafeUtils.getToken(SafeUtils.postSafeStatements(safeServer, SdxRoutingSlang
      .postInitRoute, c0_keyhash, safeparams));

    routeAdvertise.safeToken = token;

    SafeManager safeManagerSdx1 = safeManagerHashMap.get(testSetting.sdxSliceNames.get(0));
    assert safeManagerSdx1.authorizeBgpAdvertise(routeAdvertise);
    safeManagerSdx1.postPathToken(routeAdvertise);

    RouteAdvertise advertise1 = bgpManagerHashMap.get(testSetting.sdxSliceNames.get(0))
      .receiveAdvertise(routeAdvertise);

    advertise1 = safeManagerSdx1.forwardAdvertise(advertise1, getPrincipalId(sliceKeyMap.get
      (testSetting.sdxSliceNames.get(1))), routeAdvertise.advertiserPID);


    SafeManager safeManagerSdx2 = safeManagerHashMap.get(testSetting.sdxSliceNames.get(1));
    assert safeManagerSdx2.authorizeBgpAdvertise(advertise1);

    safeManagerSdx2.postPathToken(advertise1);

    AdvertiseManager advertiseManagerSdx2 = bgpManagerHashMap.get(testSetting.sdxSliceNames.get(1));

    RouteAdvertise advertise2 = advertiseManagerSdx2.receiveAdvertise(advertise1);

    advertise2 = safeManagerSdx2.forwardAdvertise(advertise2, getPrincipalId(sliceKeyMap.get
      (testSetting.sdxSliceNames.get(3))), advertise1.advertiserPID);

    SafeManager safeManagerSdx4 = safeManagerHashMap.get(testSetting.sdxSliceNames.get(3));
    assert safeManagerSdx4.authorizeBgpAdvertise(advertise2);
  }

  void testSDRouteAdvertisements() {
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

    String[] safeparams = new String[5];
    safeparams[0] = routeAdvertise.getSrcPrefix();
    safeparams[1] = routeAdvertise.getDestPrefix();
    safeparams[2] = routeAdvertise.getFormattedPath();
    safeparams[3] = sdx1_keyhash;
    safeparams[4] = String.valueOf(1);
    String token = SafeUtils.getToken(SafeUtils.postSafeStatements(safeServer, SdxRoutingSlang
      .postInitRouteSD, c0_keyhash, safeparams));

    routeAdvertise.safeToken = token;

    SafeManager safeManagerSdx1 = safeManagerHashMap.get(testSetting.sdxSliceNames.get(0));
    assert safeManagerSdx1.authorizeBgpAdvertise(routeAdvertise);
    safeManagerSdx1.postPathToken(routeAdvertise);

    ArrayList<RouteAdvertise> advertises = bgpManagerHashMap.get(testSetting.sdxSliceNames.get
      (0))
      .receiveStAdvertise(routeAdvertise);

    RouteAdvertise advertise1 = advertises.get(0);

    advertise1 = safeManagerSdx1.forwardAdvertise(advertise1, getPrincipalId(sliceKeyMap.get
      (testSetting.sdxSliceNames.get(1))), routeAdvertise.advertiserPID);


    SafeManager safeManagerSdx2 = safeManagerHashMap.get(testSetting.sdxSliceNames.get(1));
    assert safeManagerSdx2.authorizeBgpAdvertise(advertise1);

    safeManagerSdx2.postPathToken(advertise1);

    AdvertiseManager advertiseManagerSdx2 = bgpManagerHashMap.get(testSetting.sdxSliceNames.get(1));

    RouteAdvertise advertise2 = advertiseManagerSdx2.receiveStAdvertise(advertise1).get(0);

    advertise2 = safeManagerSdx2.forwardAdvertise(advertise2, getPrincipalId(sliceKeyMap.get
      (testSetting.sdxSliceNames.get(3))), advertise1.advertiserPID);

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
