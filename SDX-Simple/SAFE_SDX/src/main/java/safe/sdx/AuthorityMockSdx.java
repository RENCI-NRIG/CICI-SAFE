package safe.sdx;

import exoplex.common.utils.SafeUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import safe.AuthorityBase;
import safe.SdxRoutingSlang;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class AuthorityMockSdx extends AuthorityBase implements SdxRoutingSlang {

  static Logger logger = LogManager.getLogger(AuthorityMockSdx.class);

  static String defaultSafeServer = "129.114.108.106:7777";

  HashMap<String, String> sliceToken = new HashMap<>();

  HashMap<String, String> sliceKeyMap = new HashMap<>();

  HashMap<String, String> sliceScid = new HashMap<>();

  HashMap<String, String> sliceIpMap = new HashMap<>();

  ArrayList<String> slices = new ArrayList<>();

  public AuthorityMockSdx(String safeServer) {
    super(safeServer);
  }

  public static void main(String[] args) {
    if(args.length==0) {
      LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
      Configuration config = ctx.getConfiguration();
      LoggerConfig loggerConfig = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
      loggerConfig.setLevel(Level.DEBUG);
      ctx.updateLoggers();
      AuthorityMockSdx authorityMock = new AuthorityMockSdx(defaultSafeServer);
      authorityMock.makeSafePreparation();
    }
    else if(args.length>=4) {
      if(args[0].equals("auth")){
        String userKey = args[1];
        String slice = args[2];
        String ip = args[3];
        String tag = args[4];
        String ss = args[5] + ":7777";
        logger.info(String.format("UserKey:%s sliceName:%s IpPrefix:%s Tag: %s SafeServerIP:%s",
          userKey, slice, ip, tag, ss));
        AuthorityMockSdx mock = new AuthorityMockSdx(ss);
        mock.authorityDelegation(userKey, slice, ip, tag);
      }else if(args[0].equals("update")) {
        String userKey = args[1];
        String method = args[2];
        String token = args[3];
        String name = args[4];
        String ss = args[5] + ":7777";
        AuthorityMockSdx mock = new AuthorityMockSdx(ss);
        mock.updateTokens(userKey, method, token, name);
      }else if(args[0].equals("init")){
        String userKey = args[1];
        String tagAcl = args[2];
        String ss = args[3] + ":7777";
        AuthorityMockSdx mock = new AuthorityMockSdx(ss);
        mock.initUser(userKey, tagAcl);
      }else {
        String userKeyFile = args[0];
        String slice = args[1];
        String ip = args[2];
        String ss = args[3] + ":7777";
        String tag = "tag0";
        logger.info(String.format("UserKeyFile:%s sliceName:%s IpPrefix:%s SafeServerIP:%s Tag: %s",
          userKeyFile, slice, ip, ss, tag));
        AuthorityMockSdx mock = new AuthorityMockSdx(ss);
        mock.addPrincipals();
        mock.initPrincipals();
        mock.addUserSlice(userKeyFile, slice, ip);
        //mock.checkAuthorization();
      }
    }else {
      logger.info("Usage:\n userKeyFile sliceName IPPrefix safeServerIP  ---- default " +
        "delegations\n"
        + "init userKeyFile tag safeServerIP  ---- initialize user, allows connection from " +
        "peer with the tag\n"
        + "auth userKeyHash slicename userIp userTag safeserverIP  ---- authorities make " +
        "delegations to user, sdx allows stitching from user\n"
        + "update userKeyFile method token name  ---- add delegation tokens to related safe sets\n"
      );
    }
  }

  public void authorityDelegation(String userKey, String slice, String userIP, String tag1){
    sliceKeyMap.put(slice, userKey);
    //User membership
    String token = SafeUtils.getToken(SafeUtils.postSafeStatements(safeServer,
      postUserEndorsement, "key_p1", new String[]{userKey}));
    System.out.println(String.format("passDelegation %s %s", token, "User"));
    //PI delegate to users
    HashMap<String, String> envs = new HashMap<>();
    String projectId = principalMap.get("key_p2") + ":project1";
    String pmToken = safePost(postProjectMembership, "key_p4", new String[]{userKey,
      projectId, "true"});
    System.out.println(String.format("passDelegation %s %s", pmToken, projectId));
    envs.clear();

    /*
    The previous part is the common geni trust structure.
    The next is specific to our example

     */
    //create slices.
    String sliceControlRef = safePost(postStandardSliceControlSet, "key_p3");
    String slicePrivRef = safePost(postStandardSliceDefaultPrivilegeSet, "key_p3");
    String sliceId = principalMap.get("key_p3") + ":" + slice;
    sliceScid.put(slice, sliceId);
    sliceToken.put(slice, safePost(postSliceSet, "key_p3",
      new String[]{userKey, sliceId, projectId,
        sliceControlRef,
        slicePrivRef}));
    System.out.println(String.format("passDelegation %s %s", sliceToken.get(slice),
      sliceId));

    //UserAcl
    safePost(postUserAclEntry, "sdx", new String[]{userKey});


    String parentPrefix = "ipv4\\\"192.1.1.1/24\\\"";
    String uip = String.format("ipv4\\\"%s\\\"", userIP);
    String ipToken = safePost(postIPAllocate, "rpkiroot", new String[]{userKey, uip,
      parentPrefix});
    System.out.println(String.format("postDlgToken %s %s", ipToken, uip.replace("\\", "\\\\\\")));

    //Tag delegation
    String tag = SafeUtils.getPrincipalId(safeServer,"tagauthority")+ ":" + tag1;
    safePost(postTagSet, "tagauthority", new String[]{tag});
    String tagToken = safePost(postGrantTagPriv, "tagauthority", new Object[]{userKey, tag, true});
    System.out.println(String.format("updateTagSet %s %s", tagToken, tag));
  }

  public void updateTokens(String userKey, String method, String token, String name){
    System.out.println(safePost(method, userKey, new String[]{token, name}));
  }

  public void initUser(String userKey, String tagAcl){
    //slices.add(slice);
    initIdSetSubjectSet(userKey);
    //User membership
    String tagAuth = SafeUtils.getPrincipalId(safeServer, "tagauthority");
    System.out.println(safePost(postUserTagAclEntry, userKey, new String[]{tagAuth + ":" +
      tagAcl}));
    System.out.println(safePost(postCustomerConnectionPolicy, userKey, new String[]{}));
    System.out.println(safePost(postTagPrivilegePolicy, userKey, new String[]{}));
    System.out.println(safePost(postCustomerPolicy, userKey, new String[]{}));
  }

  public void makeSafePreparation() {
    if(!authorizationMade) {
      customSetting();
      addPrincipals();
      initPrincipals();
      initializeGeniAuth();
      checkAuthorization();
      authorizationMade = true;
    }
  }

  private void customSetting() {
    slices.addAll(Arrays.asList(new String[]{"c0-tri", "c1-tri", "c2-tri",
        "c3-tri", "c4-tri"}));
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
    for (String key: sliceKeyMap.values()){
      principals.add(key);
    }
  }

  private void checkAuthorization() {
    try {
      verifyAuthStitchingByUid();
      verifyAuthZByUserAttr();
    }catch (Exception e){
      logger.error(e.getMessage());
    }
  }


  private void initializeGeniAuth() {
    String token;
    simpleEndorseMent(postMAEndorsement, "geniroot", "key_p1", "MA");
    simpleEndorseMent(postPAEndorsement, "geniroot", "key_p2", "PA");
    simpleEndorseMent(postSAEndorsement, "geniroot", "key_p3", "SA");

    String piCap = simpleEndorseMent(postPIEndorsement, "key_p1", "key_p4", "PI");

    simpleEndorseMent(postUserEndorsement, "key_p1", "sdx", "User");

    HashMap<String, String> envs = new HashMap<>();
    envs.put(subject, principalMap.get("key_p4"));
    envs.put(bearerRef, piCap);
    authorize(createProject, "key_p2", new String[]{}, envs);
    String paMemberSetRef = safePost(postMemberSet, "key_p2");
    String projectId = principalMap.get("key_p2") + ":project1";
    String projectToken = safePost(postProjectSet, "key_p2",
        new String[]{principalMap.get("key_p4"), projectId,
            paMemberSetRef});
    List<String> piProjectTokens = SafeUtils.getTokens(passDelegation("key_p4", projectToken,
        projectId));

    envs.clear();
    //Authorize that PI can create slice
    envs.put(subject, principalMap.get("key_p4"));
    //bearerRef should be subject set, as it contains both project token and MA token
    envs.put(bearerRef, piProjectTokens.get(1));
    assert authorize(createSlice, "key_p3", new String[]{projectId}, envs);
    String sliceControlRef = safePost(postStandardSliceControlSet, "key_p3");
    String slicePrivRef = safePost(postStandardSliceDefaultPrivilegeSet, "key_p3");
    //post authorize policy
    safePost(postStitchPolicy, "sdx");

    //MakeIp Delegation
    safePost(postMakeIPTokenSet, "rpkiroot", new String[]{"ipv4\\\"192.1.1.1/24\\\""});

    for (String slice : slices) {
      String userKeyFile = sliceKeyMap.get(slice);
      String userIP = sliceIpMap.get(slice);
      addUserSlice(userKeyFile, slice, userIP);
    }
    logger.debug("end");
  }


  void addUserSlice(String userKeyFile, String slice, String userIP) {
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
    String userKey = principalMap.get(userKeyFile);
    String projectId = principalMap.get("key_p2") + ":project1";
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
    String sliceId = principalMap.get("key_p3") + ":" + slice;
    sliceScid.put(slice, sliceId);
    sliceToken.put(slice, safePost(postSliceSet, "key_p3",
        new String[]{principalMap.get(sliceKeyMap.get(slice)), sliceId, projectId,
            sliceControlRef,
            slicePrivRef}));
    List<String> sliceTokens = SafeUtils.getTokens(passDelegation(sliceKeyMap.get(slice),
        sliceToken.get(slice), sliceId));

    //UserAcl
    safePost(postUserAclEntry, "sdx", new String[]{principalMap.get(sliceKeyMap.get
        (slice))});


    String parentPrefix = "ipv4\\\"192.1.1.1/24\\\"";
    String uip = String.format("ipv4\\\"%s\\\"", userIP);
    String ipToken = safePost(postIPAllocate, "rpkiroot", new String[]{userKey, uip,
        parentPrefix});
    safePost(postDlgToken, userKeyFile, new String[]{ipToken, uip});
    safePost(updateSubjectSet, userKeyFile, new String[]{ipToken});
    authorize(authorizeOwnPrefix, "sdx", new String[]{userKey, uip});

    //Tag delegation
    String tag = principalMap.get("tagauthority") + ":tag0";
    safePost(postTagSet, "tagauthority", new String[]{tag});
    String tagToken = safePost(postGrantTagPriv, "tagauthority", new Object[]{userKey, tag, true});
    safePost(updateTagSet, userKeyFile, new String[]{tagToken, tag});

    //userTagAcl
    //user post Connect policy
    safePost(postUserTagAclEntry, userKeyFile, new String[]{tag});
    safePost(postCustomerConnectionPolicy, userKeyFile, new String[]{});
    safePost(postTagPrivilegePolicy, userKeyFile, new String[]{});
    safePost(postCustomerPolicy, userKeyFile, new String[]{});
  }

  void verifyAuthStitchingByUid() throws Exception {
    //authorizeStitchByUid
    for (String slice : slices) {
      if(!authorize(authorizeStitchByUID, "sdx",
          new String[]{principalMap.get(sliceKeyMap.get(slice)), sliceScid.get(slice)})){
        throw new Exception(String.format("Authorization failed: %s %s ", authorizeStitchByUID,
            slice));
      }
    }
  }

  void verifyAuthZByUserAttr() throws Exception{
    //authZByUserAttr
    for (int i = 0; i < slices.size(); i++) {
      String slice = slices.get(i);
      String user = sliceKeyMap.get(slice);
      String userKey = principalMap.get(user);
      String ip = String.format("ipv4\\\"%s\\\"", sliceIpMap.get(slice));
      for (int j = i + 1; j < slices.size(); j++) {
        String peerSlice = slices.get(j);
        String peer = sliceKeyMap.get(peerSlice);
        String peerKey = principalMap.get(peer);
        String peerIp =String.format( "ipv4\\\"%s\\\"",sliceIpMap.get(peerSlice));
        if(!authorize(authZByUserAttr, "sdx", new String[]{userKey, ip, peerKey, peerIp})){
          throw new Exception(String.format("Authorization failed: %s", authZByUserAttr));
        }

      }
    }
  }
}
