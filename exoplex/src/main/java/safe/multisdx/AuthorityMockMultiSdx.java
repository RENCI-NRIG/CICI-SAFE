package safe.multisdx;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import exoplex.common.utils.SafeUtils;
import exoplex.demo.AbstractTestSetting;
import exoplex.demo.multisdxsd.MultiSdxSDModule;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import safe.Authority;
import safe.SdxRoutingSlang;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class AuthorityMockMultiSdx extends Authority implements SdxRoutingSlang {

  static Logger logger = LogManager.getLogger(AuthorityMockMultiSdx.class);

  static String defaultSafeServer = "128.194.6.133:7777";

  public HashMap<String, String> sliceToken = new HashMap<>();

  public HashMap<String, String> sliceKeyMap = new HashMap<>();

  public HashMap<String, String> sliceScid = new HashMap<>();

  public HashMap<String, String> sliceIpMap = new HashMap<>();

  public ArrayList<String> slices = new ArrayList<>();

  public AbstractTestSetting testSetting;
  private ReentrantLock lock = new ReentrantLock();

  public AuthorityMockMultiSdx(String safeServer) {
    super(safeServer);
  }

  @Inject
  public AuthorityMockMultiSdx(AbstractTestSetting testSetting) {
    this.testSetting = testSetting;
  }

  public static void main(String[] args) {
    if (args.length == 0) {
      LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
      Configuration config = ctx.getConfiguration();
      LoggerConfig loggerConfig = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
      loggerConfig.setLevel(Level.DEBUG);
      ctx.updateLoggers();
      Injector injector = Guice.createInjector(new MultiSdxSDModule());
      Authority authorityMock = injector.getInstance(Authority.class);
      authorityMock.setSafeServer("128.194.6.133:7777");
      authorityMock.makeSafePreparation();
    } else if (args.length == 4) {
      String userKeyFile = args[0];
      String slice = args[1];
      String ip = args[2];
      String ss = args[3] + ":7777";
      logger.info(String.format("UserKeyFile:%s sliceName:%s IpPrefix:%s SafeServer:%s",
        userKeyFile, slice, ip, ss));
      AuthorityMockMultiSdx mock = new AuthorityMockMultiSdx(ss);
      mock.addPrincipals();
      mock.initPrincipals();
      mock.addMultiSdxUserSlice(userKeyFile, slice, ip);
      //mock.checkAuthorization();
    } else {
      logger.info("Usage: userKeyFile sliceName IPPrefix safeServerIP\n");
    }
  }

  public void setSafeServer(String safeServer) {
    this.safeServer = safeServer;
  }

  public void makeSafePreparation() {
    lock.lock();
    if (!authorizationMade) {
      authorizationMade = true;
      multiSdxSetting();
      addPrincipals();
      initPrincipals();
      initializeMultiSdxAuth();
      for (String slice : testSetting.clientSlices) {
        addMultiSdxUserSlice(testSetting.clientKeyMap.get(slice), slice, testSetting
          .clientIpMap.get(slice));
      }
      for (String slice : testSetting.sdxSliceNames) {
        addMultiSdxUserSlice(testSetting.sdxKeyMap.get(slice), slice, testSetting
          .sdxIpMap.get(slice));
      }
      checkAuthorization();
      logger.debug("safe preparation made");
    }
    lock.unlock();
  }

  private void multiSdxSetting() {
    slices.addAll(testSetting.sdxSliceNames);
    for (String key : testSetting.sdxKeyMap.keySet()) {
      sliceKeyMap.put(key, testSetting.sdxKeyMap.get(key));
      sliceIpMap.put(key, testSetting.sdxIpMap.get(key));
    }
    for (String key : testSetting.clientSlices) {
      sliceKeyMap.put(key, testSetting.clientKeyMap.get(key));
      sliceIpMap.put(key, testSetting.clientIpMap.get(key));
      slices.add(key);
    }
  }

  private void customSetting() {
    slices.addAll(Arrays.asList("c0-tri", "c1-tri", "c2-tri",
      "c3-tri", "c4-tri"));
    sliceKeyMap.put(slices.get(0), "key_p5");
    sliceKeyMap.put(slices.get(1), "key_p6");
    sliceKeyMap.put(slices.get(2), "key_p7");
    sliceKeyMap.put(slices.get(3), "key_p8");
    sliceKeyMap.put(slices.get(4), "key_p9");

    sliceIpMap.put(slices.get(0), "192.168.10.1/24");
    sliceIpMap.put(slices.get(1), "192.168.20.1/24");
    sliceIpMap.put(slices.get(2), "192.168.30.1/24");
    sliceIpMap.put(slices.get(3), "192.168.40.1/24");
    sliceIpMap.put(slices.get(4), "192.168.60.1/24");
  }

  private void addPrincipals() {
    principals.add("sdx");
    principals.add("tagauthority");
    principals.add("geniroot");
    principals.add("rpkiroot");
    //MA
    principals.add("key_p1");
    //PA
    principals.add("key_p2");
    //SA
    principals.add("key_p3");
    //PI
    principals.add("key_p4");
    for (String key : sliceKeyMap.values()) {
      principals.add(key);
    }
  }

  private void checkAuthorization() {
    try {
      verifyAuthStitchingByUid();
      //verifyAuthZByUserAttr();
    } catch (Exception e) {
      logger.error(e.getMessage());
    }
  }

  private void initializeMultiSdxAuth() {
    String token;
    simpleEndorseMent(postMAEndorsement, "geniroot", "key_p1", "MA");
    simpleEndorseMent(postPAEndorsement, "geniroot", "key_p2", "PA");
    simpleEndorseMent(postSAEndorsement, "geniroot", "key_p3", "SA");

    String piCap = simpleEndorseMent(postPIEndorsement, "key_p1", "key_p4", "PI");

    for (String key : testSetting.sdxKeyMap.values()) {
      simpleEndorseMent(postUserEndorsement, "key_p1", key, "User");
    }

    HashMap<String, String> envs = new HashMap<>();
    envs.put(subject, getPrincipalId("key_p4"));
    envs.put(bearerRef, piCap);
    authorize(createProject, "key_p2", new String[]{}, envs);
    String paMemberSetRef = safePost(postMemberSet, "key_p2");
    String projectId = getPrincipalId("key_p2") + ":project1";
    String projectToken = safePost(postProjectSet, "key_p2",
      new String[]{getPrincipalId("key_p4"), projectId,
        paMemberSetRef});
    List<String> piProjectTokens = SafeUtils.getTokens(passDelegation("key_p4", projectToken,
      projectId));

    envs.clear();
    //Authorize that PI can create slice
    envs.put(subject, getPrincipalId("key_p4"));
    //bearerRef should be subject set, as it contains both project token and MA token
    envs.put(bearerRef, piProjectTokens.get(1));
    assert authorize(createSlice, "key_p3", new String[]{projectId}, envs);
    String sliceControlRef = safePost(postStandardSliceControlSet, "key_p3");
    String slicePrivRef = safePost(postStandardSliceDefaultPrivilegeSet, "key_p3");
    //post authorize policy
    for (String key : testSetting.sdxKeyMap.values()) {
      safePost(postStitchPolicy, key);
      safePost(postOwnPrefixPolicy, key);
      safePost(postRoutingPolicy, key);
      safePost(postVerifyASPolicy, key);
    }


    //MakeIp Delegation
    safePost(postMakeIPTokenSet, "rpkiroot", new String[]{"ipv4\\\"192.1.1.1/24\\\""});

    for (String slice : slices) {
      String userKeyFile = sliceKeyMap.get(slice);
      String userIP = sliceIpMap.get(slice);
      addMultiSdxUserSlice(userKeyFile, slice, userIP);
    }

    //Tag Delegation to SDXes
    logger.debug("end");
    for (String sdxslice : testSetting.sdxSliceNames) {
      for (String t : testSetting.sdxASTags.get(sdxslice)) {
        String tag = getPrincipalId("tagauthority") + ":" + t;
        safePost(postTagSet, "tagauthority", new String[]{tag});
        String sdxKeyFile = testSetting.sdxKeyMap.get(sdxslice);
        String sdxKey = getPrincipalId(sdxKeyFile);
        String tagToken = safePost(postGrantTagPriv, "tagauthority", new Object[]{sdxKey, tag,
          true});
        safePost(updateTagSet, sdxKeyFile, new String[]{tagToken, tag});
      }
    }
    //post user's authorized AS attr acls
    for (String slice : testSetting.clientSlices) {

    }
  }

  /*
  Allow stitching to all SDX slices
   */
  void addMultiSdxUserSlice(String userKeyFile, String slice, String userIP) {
    //slices.add(slice);
    sliceKeyMap.put(slice, userKeyFile);
    if (!principalMap.containsKey(userKeyFile)) {
      principals.add(userKeyFile);
      initIdSetSubjectSet(userKeyFile);
      principalMap.put(userKeyFile, SafeUtils.getPrincipalId(safeServer, userKeyFile));
    }
    //User membership
    simpleEndorseMent(postUserEndorsement, "key_p1", userKeyFile, "User");
    //PI delegate to users
    HashMap<String, String> envs = new HashMap<>();
    String userKey = getPrincipalId(userKeyFile);
    String projectId = getPrincipalId("key_p2") + ":project1";
    String pmToken = safePost(postProjectMembership, "key_p4", new String[]{userKey,
      projectId, "true"});
    List<String> tokens = SafeUtils.getTokens(passDelegation(userKeyFile, pmToken,
      projectId));
    envs.clear();
    envs.put(subject, userKey);
    envs.put(bearerRef, tokens.get(1));
    assert authorize(createSlice, "key_p3", new String[]{projectId}, envs);

    /*
    The previous part is the common geni trust structure.
    The next is specific to our example

     */
    //create slices.
    String sliceControlRef = safePost(postStandardSliceControlSet, "key_p3");
    String slicePrivRef = safePost(postStandardSliceDefaultPrivilegeSet, "key_p3");
    String sliceId = getPrincipalId("key_p3") + ":" + slice;
    sliceScid.put(slice, sliceId);
    sliceToken.put(slice, safePost(postSliceSet, "key_p3",
      new String[]{getPrincipalId(sliceKeyMap.get(slice)), sliceId, projectId,
        sliceControlRef,
        slicePrivRef}));
    List<String> sliceTokens = SafeUtils.getTokens(passDelegation(sliceKeyMap.get(slice),
      sliceToken.get(slice), sliceId));

    //UserAcl
    for (String sdxKey : testSetting.sdxKeyMap.values()) {
      safePost(postUserAclEntry, sdxKey, new String[]{getPrincipalId(sliceKeyMap.get
        (slice))});
    }


    String parentPrefix = "ipv4\\\"192.1.1.1/24\\\"";
    String uip = String.format("ipv4\\\"%s\\\"", userIP);
    String ipToken = safePost(postIPAllocate, "rpkiroot", new String[]{userKey, uip,
      parentPrefix});
    safePost(postDlgToken, userKeyFile, new String[]{ipToken, uip});
    safePost(updateSubjectSet, userKeyFile, new String[]{ipToken});
    for (String sdxKey : testSetting.sdxKeyMap.values()) {
      authorize(authorizeOwnPrefix, sdxKey, new String[]{userKey, uip});
    }

    //Tag delegation

    //userTagAcl
    //user post Connect policy
    if (testSetting.clientTags.containsKey(slice)) {
      for (String t : testSetting.clientTags.get(slice)) {
        String tag = getPrincipalId("tagauthority") + ":" + t;
        safePost(postTagSet, "tagauthority", new String[]{tag});
        String tagToken = safePost(postGrantTagPriv, "tagauthority", new Object[]{userKey, tag, true});
        safePost(updateTagSet, userKeyFile, new String[]{tagToken, tag});
        safePost(updateSubjectSet, userKeyFile, new String[]{tagToken});
        safePost(postUserTagAclEntry, userKeyFile, new String[]{tag});
      }
    }

    //post AS tag acls
    if (testSetting.clientASTagAcls.containsKey(slice)) {
      for (String t : testSetting.clientASTagAcls.get(slice)) {
        String astag = getPrincipalId("tagauthority") + ":" + t;
        safePost(postASTagAclEntry, userKeyFile, new String[]{astag, uip});
      }
      /*
      for (ImmutablePair<String, String> pair : testSetting.clientRouteASTagAcls.getOrDefault(slice,
        new ArrayList<>())) {
        String astag = getPrincipalId("tagauthority") + ":" + pair.getRight();
        String srcip = String.format("ipv4\\\"%s\\\"", pair.getLeft());
        safePost(postASTagAclEntrySD, userKeyFile, new String[]{astag, srcip, uip});
      }*/
    }
    safePost(postCustomerConnectionPolicy, userKeyFile, new String[]{});
    safePost(postTagPrivilegePolicy, userKeyFile, new String[]{});
    safePost(postCustomerPolicy, userKeyFile, new String[]{});
    safePost(postAuthZASPolicy, userKeyFile, new String[]{});
  }

  void verifyAuthStitchingByUid() throws Exception {
    //authorizeStitchByUid
    for (String slice : testSetting.sdxSliceNames) {
      for (String sdx : testSetting.sdxSliceNames)
        if (!authorize(authorizeStitchByUID, sliceKeyMap.get(sdx),
          new String[]{getPrincipalId(sliceKeyMap.get(slice)), sliceScid.get(slice)})) {
          throw new Exception(String.format("Authorization failed: %s %s ", authorizeStitchByUID,
            slice));
        }
    }
  }

  void verifyAuthZByUserAttr() throws Exception {
    //authZByUserAttr
    for (int i = 1; i < slices.size(); i++) {
      String slice = slices.get(i);
      String user = sliceKeyMap.get(slice);
      String userKey = getPrincipalId(user);
      String ip = sliceIpMap.get(slice);
      for (int j = i + 1; j < slices.size(); j++) {
        String peerSlice = slices.get(j);
        String peer = sliceKeyMap.get(peerSlice);
        String peerKey = getPrincipalId(peer);
        String peerIp = String.format("ipv4\\\"%s\\\"", sliceIpMap.get(peerSlice));
        if (!authorize(authZByUserAttr, "sdx", new String[]{userKey, ip, peerKey, peerIp})) {
          throw new Exception(String.format("Authorization failed: %s", authZByUserAttr));
        }
      }
    }
  }
}
