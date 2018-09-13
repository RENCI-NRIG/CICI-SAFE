package safe;

import exoplex.common.utils.SafeUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class AuthorityMock {

  static Logger logger = LogManager.getLogger(AuthorityMock.class);

  static final String bearerRef = "bearerRef";

  static final String subject = "subject";

  static String exampleSafeServer = "129.114.109.53:7777";

  static final String postMakeIPTokenSet = "postMakeIPTokenSet";
  static final String postUserAclEntry = "postUserAclEntry";
  static final String postUserTagAclEntry = "postUserTagAclEntry";
  static final String postCustomerPolicy = "postCustomerPolicy";
  static final String updateTagSet = "updateTagSet";
  static final String postStandardSliceDefaultPrivilegeSet = "postStandardSliceDefaultPrivilegeSet";
  static final String updateSubjectSet = "updateSubjectSet";
  static final String postOwnPrefixPolicy = "postOwnPrefixPolicy";
  static final String postMAEndorsement = "postMAEndorsement";
  static final String postStitchPolicy = "postStitchPolicy";
  static final String postObjectTagSet = "postObjectTagSet";
  static final String postProjectMembership = "postProjectMembership";
  static final String postSAEndorsement = "postSAEndorsement";
  static final String authZByPID = "authZByPID";
  static final String createSlice = "createSlice";
  static final String authorizeStitchByPID = "authorizeStitchByPID";
  static final String authZByUserAttr = "authZByUserAttr";
  static final String postAssignTag = "postAssignTag";
  static final String postCustomerConnectionPolicy = "postCustomerConnectionPolicy";
  static final String postSliceTagAclEntry = "postSliceTagAclEntry";
  static final String postPIEndorsement = "postPIEndorsement";
  static final String postProjectIDAcl = "postProjectIDAcl";
  static final String postCPEndorsement = "postCPEndorsement";
  static final String authZByProjectAttr = "authZByProjectAttr";
  static final String postProjectSet = "postProjectSet";
  static final String postPAEndorsement = "postPAEndorsement";
  static final String authorizeStitchByUID = "authorizeStitchByUID";
  static final String postTagSet = "postTagSet";
  static final String postTagPrivilegePolicy = "postTagPrivilegePolicy";
  static final String createProject = "createProject";
  static final String postAclEntrySet = "postAclEntrySet";
  static final String postUserEndorsement = "postUserEndorsement";
  static final String postMemberSet = "postMemberSet";
  static final String authorizeOwnPrefix = "authorizeOwnPrefix";
  static final String authorizeStitchByProjectAttr = "authorizeStitchByProjectAttr";
  static final String passDelegation = "passDelegation";
  static final String authorizeStitchBySliceAttr = "authorizeStitchBySliceAttr";
  static final String postSliceControl = "postSliceControl";
  static final String postLinkTagSetToProject = "postLinkTagSetToProject";
  static final String postSliceSet = "postSliceSet";
  static final String authorizeStitchByUserAttr = "authorizeStitchByUserAttr";
  static final String createSliver = "createSliver";
  static final String postIdSet = "postIdSet";
  static final String postDlgToken = "postDlgToken";
  static final String postGrantTagPriv = "postGrantTagPriv";
  static final String postUpdateObjectTagSet = "postUpdateObjectTagSet";
  static final String postStandardSliceControlSet = "postStandardSliceControlSet";
  static final String postLinkTagSetToSlice = "postLinkTagSetToSlice";
  static final String postIPAllocate = "postIPAllocate";
  static final String whoami = "whoami";
  static final String postTagAclEntry = "postTagAclEntry";
  static final String postSubjectSet = "postSubjectSet";
  ;

  String safeServer;

  ArrayList<String> principals = new ArrayList<>();

  HashMap<String, String> principalMap = new HashMap<>();

  HashMap<String, String> sliceToken = new HashMap<>();

  HashMap<String, String> sliceKeyMap = new HashMap<>();

  HashMap<String, String> sliceScid = new HashMap<>();

  HashMap<String, String> subjectSet = new HashMap<String, String>();

  HashMap<String, String> sliceIpMap = new HashMap<>();

  ArrayList<String> slices = new ArrayList<>();

  public AuthorityMock(String safeServer) {
    this.safeServer = safeServer;
  }

  public static void main(String[] args) {
    if(args.length==0) {
      LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
      Configuration config = ctx.getConfiguration();
      LoggerConfig loggerConfig = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
      loggerConfig.setLevel(Level.DEBUG);
      ctx.updateLoggers();
      AuthorityMock authorityMock = new AuthorityMock(exampleSafeServer);
      authorityMock.customSetting();
      authorityMock.addPrincipals();
      authorityMock.initPrincipals();
      authorityMock.initializeGeniAuth();
      authorityMock.checkAuthorization();
    }
    else if(args.length==4){
      String userKeyFile = args[0];
      String slice = args[1];
      String ip = args[2];
      String ss = args[3] + ":7777";
      logger.info(String.format("UserKeyFile:%s sliceName:%s IpPrefix:%s SafeServer:%s",
          userKeyFile, slice, ip, ss));
      AuthorityMock mock = new AuthorityMock(ss);
      mock.addPrincipals();
      mock.initPrincipals();
      mock.addUserSlice(userKeyFile, slice, ip);
      mock.checkAuthorization();
    }else {
      logger.info("Usage: userKeyFile sliceName IPPrefix safeServerIP\n");
    }
  }

  public void makeSafePreparation() {
    customSetting();
    addPrincipals();
    initPrincipals();
    initializeGeniAuth();
    checkAuthorization();
  }

  private void customSetting() {
    slices.addAll(Arrays.asList(new String[]{"c1-yaoyj11", "c2-yaoyj11", "c3-yaoyj11",
        "c4-yaoyj11", "c6-yaoyj11"}));
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
  }

  private void initPrincipals() {
    principals.forEach(p -> {
      initIdSetSubjectSet(p);
      principalMap.put(p, SafeUtils.getPrincipalId(safeServer, p));
    });
  }

  private void initIdSetSubjectSet(String key) {
    SafeUtils.postSafeStatements(safeServer, postIdSet, key, new String[]{key});
    String token = SafeUtils.getToken(SafeUtils.postSafeStatements(safeServer, postSubjectSet, key, new
        String[]{}));
    subjectSet.put(key, token);
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
    safePost(postMakeIPTokenSet, "rpkiroot", new String[]{"192.1.1.1/24"});

    for (String slice : slices) {
      String userKeyFile = sliceKeyMap.get(slice);
      String userIP = sliceIpMap.get(slice);
      addUserSlice(userKeyFile, slice, userIP);
    }
    logger.debug("end");
  }

  void addUserSlice(String userKeyFile, String slice, String userIP) {
    slices.add(slice);
    sliceKeyMap.put(slice, userKeyFile);
    //PI delegate to users
    if (!principalMap.containsKey(userKeyFile)) {
      principals.add(userKeyFile);
      initIdSetSubjectSet(userKeyFile);
      principalMap.put(userKeyFile, SafeUtils.getPrincipalId(safeServer, userKeyFile));
    }
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


    String parentPrefix = "192.1.1.1/24";
    String ipToken = safePost(postIPAllocate, "rpkiroot", new String[]{userKey, userIP,
        parentPrefix});
    safePost(postDlgToken, userKeyFile, new String[]{ipToken, userIP});
    authorize(authorizeOwnPrefix, "sdx", new String[]{userKey, userIP});

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
    for (int i = 1; i < slices.size(); i++) {
      String slice = slices.get(i);
      String user = sliceKeyMap.get(slice);
      String userKey = principalMap.get(user);
      String ip = sliceIpMap.get(slice);
      for (int j = i + 1; j < slices.size(); j++) {
        String peerSlice = slices.get(j);
        String peer = sliceKeyMap.get(peerSlice);
        String peerKey = principalMap.get(peer);
        String peerIp = sliceIpMap.get(peerSlice);
        if(!authorize(authZByUserAttr, "sdx", new String[]{userKey, ip, peerKey, peerIp})){
          throw new Exception(String.format("Authorization failed: %s", authZByUserAttr));
        }

      }
    }
  }

  String safePost(String method, String principal) {
    return safePost(method, principal, new Object[]{});
  }

  String safePost(String method, String principal, Object[] others) {
    String p = principalMap.get(principal);
    return SafeUtils.getToken(
        SafeUtils.postSafeStatements(safeServer, method, p, others)
    );
  }

  boolean authorize(String method, String principal, String[] otherValues) {
    String p = principalMap.get(principal);
    return SafeUtils.authorize(safeServer, method, p, otherValues);
  }

  boolean authorize(String method, String principal, String[] otherValues, HashMap<String,
      String> envs) {
    String p = principalMap.get(principal);
    return SafeUtils.authorize(safeServer, method, p, otherValues, envs);
  }

  String simpleEndorseMent(String method, String from, String to) {
    String fp = principalMap.get(from);
    String tp = principalMap.get(to);
    return SafeUtils.getToken(SafeUtils.postSafeStatements(safeServer, method, fp, new
        String[]{tp}));
  }

  String simpleEndorseMent(String method, String from, String to, String scid) {
    String fp = principalMap.get(from);
    String tp = principalMap.get(to);
    String token = SafeUtils.getToken(SafeUtils.postSafeStatements(safeServer, method, fp, new
        String[]{tp}));
    return SafeUtils.getToken(passDelegation(to, token, scid));
  }

  String passDelegation(String principal, String Token, String scid) {
    String p = principalMap.get(principal);
    return SafeUtils.postSafeStatements(safeServer, passDelegation, p, new String[]{Token, scid});
  }

  String simpleDelegation(String method, String from, String to, String scid) {
    String token = simpleEndorseMent(method, from, to, scid);
    return passDelegation(to, token, scid);
  }
}
