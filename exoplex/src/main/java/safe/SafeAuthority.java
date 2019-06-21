package safe;

import exoplex.common.utils.SafeUtils;
import exoplex.demo.multisdx.MultiSdxSetting;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class SafeAuthority implements SdxRoutingSlang, SafeLang {
  static final String bearerRef = "bearerRef";
  static final String subject = "subject";
  static Logger logger = LogManager.getLogger(SafeAuthority.class);
  static String exampleSafeServer = "128.194.6.138:7777";

  String safeServer = "128.194.6.138:7777";

  String sdxSlice;

  String sdxKeyFile;

  HashSet<String> reservedKeys = new HashSet<String>();
  public static boolean authorizationMade = false;

  ArrayList<String> clientSlices = new ArrayList<>();

  ArrayList<String> principals = new ArrayList<>();

  HashMap<String, String> principalMap = new HashMap<>();

  HashMap<String, String> sliceToken = new HashMap<>();

  HashMap<String, String> sliceKeyMap = new HashMap<>();

  HashMap<String, String> sliceScid = new HashMap<>();

  HashMap<String, String> subjectSet = new HashMap<String, String>();

  HashMap<String, String> sliceIpMap = new HashMap<>();

  String[] slices;

  public SafeAuthority(
    String safeServer,
    String sdxSlice,
    String sdxKey,
    List<String> clientSlices,
    HashMap<String, String> clientKeyMap,
    HashMap<String, String> clientIpMap
  ) {
    this.safeServer = safeServer;
    this.sdxSlice = sdxSlice;
    this.sdxKeyFile = sdxKey;
    sliceKeyMap.put(this.sdxSlice, this.sdxKeyFile);
    this.clientSlices.addAll(clientSlices);
    for (String key : clientKeyMap.keySet()) {
      sliceKeyMap.put(key, clientKeyMap.get(key));
    }
    sliceIpMap = clientIpMap;
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

  public void makeCnert2019SafePreparation() {
    if(!authorizationMade) {
      cnert2019Setting();
      addPrincipals();
      initPrincipals();
      initializeCnert2019Auth();
      for(String slice: MultiSdxSetting.clientSlices){
        addCnert2019UserSlice(MultiSdxSetting.clientKeyMap.get(slice), slice, MultiSdxSetting
          .clientIpMap.get(slice));
      }
      for(String slice: MultiSdxSetting.sdxSliceNames){
        addCnert2019UserSlice(MultiSdxSetting.sdxKeyMap.get(slice), slice, MultiSdxSetting
          .sdxIpMap.get(slice));
      }
      //checkAuthorization();
      authorizationMade = true;
    }
  }

  private void cnert2019Setting() {
    slices.addAll(MultiSdxSetting.sdxSliceNames);
    for(String key: MultiSdxSetting.sdxKeyMap.keySet()){
      sliceKeyMap.put(key, MultiSdxSetting.sdxKeyMap.get(key));
      sliceIpMap.put(key, MultiSdxSetting.sdxIpMap.get(key));
    }
    for(String key: MultiSdxSetting.clientSlices){
      sliceKeyMap.put(key, MultiSdxSetting.clientKeyMap.get(key));
      sliceIpMap.put(key, MultiSdxSetting.clientIpMap.get(key));
      slices.add(key);
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

  private void initializeCnert2019Auth(){
    String token;
    simpleEndorseMent(postMAEndorsement, "geniroot", "key_p1", "MA");
    simpleEndorseMent(postPAEndorsement, "geniroot", "key_p2", "PA");
    simpleEndorseMent(postSAEndorsement, "geniroot", "key_p3", "SA");

    String piCap = simpleEndorseMent(postPIEndorsement, "key_p1", "key_p4", "PI");

    for(String key: MultiSdxSetting.sdxKeyMap.values()) {
      simpleEndorseMent(postUserEndorsement, "key_p1", key, "User");
    }

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
    for(String key: MultiSdxSetting.sdxKeyMap.values()) {
      safePost(postStitchPolicy, key);
      safePost(postOwnPrefixPolicy, key);
      safePost(postRoutingPolicy, key);
      safePost(postVerifyASPolicy, key);
    }

    //MakeIp Delegation
    safePost(postMakeIPTokenSet, "rpkiroot", new String[]{"192.1.1.1/24"});

    for (String slice : slices) {
      String userKeyFile = sliceKeyMap.get(slice);
      String userIP = sliceIpMap.get(slice);
      addCnert2019UserSlice(userKeyFile, slice, userIP);
    }
    logger.debug("end");
    for(String sdxslice: MultiSdxSetting.sdxSliceNames) {
      for(String t: MultiSdxSetting.sdxASTags.get(sdxslice)) {
        String tag = principalMap.get("tagauthority") + ":" + t;
        safePost(postTagSet, "tagauthority", new String[]{tag});
        String sdxKeyFile = MultiSdxSetting.sdxKeyMap.get(sdxslice);
        String sdxKey = principalMap.get(sdxKeyFile);
        String tagToken = safePost(postGrantTagPriv, "tagauthority", new Object[]{sdxKey, tag,
          true});
        safePost(updateTagSet, sdxKeyFile, new String[]{tagToken, tag});
      }
    }
    //post user's authorized AS attr acls
    for(String slice: MultiSdxSetting.clientSlices){

    }
  }

  private void initializeGeniAuth() {
    String token;
    reservedKeys.add("geniroot");
    reservedKeys.add("key_p1");
    reservedKeys.add("key_p2");
    reservedKeys.add("key_p3");
    reservedKeys.add("key_p4");
    simpleEndorseMent(postMAEndorsement, "geniroot", "key_p1", "MA");
    simpleEndorseMent(postPAEndorsement, "geniroot", "key_p2", "PA");
    simpleEndorseMent(postSAEndorsement, "geniroot", "key_p3", "SA");

    String piCap = simpleEndorseMent(postPIEndorsement, "key_p1", "key_p4", "PI");

    for (int i = 10; i < 20; i++) {
      simpleEndorseMent(postUserEndorsement, "key_p1", "key_p" + i, "User");
    }
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

    safePost(postMakeIPTokenSet, "rpkiroot", new String[]{"ipv4\\\"192.1.1.1/24\\\""});

    for (String slice : clientSlices) {
      String userKeyFile = sliceKeyMap.get(slice);
      String userIP = sliceIpMap.get(slice);
      addUserSlice(userKeyFile, slice, userIP);
    }
    logger.debug("end");
  }

  /*
  Allow stitching to all SDX slices
   */
  void addCnert2019UserSlice(String userKeyFile, String slice, String userIP) {
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
    for(String sdxKey: MultiSdxSetting.sdxKeyMap.values()) {
      safePost(postUserAclEntry, sdxKey, new String[]{principalMap.get(sliceKeyMap.get
        (slice))});
    }


    String parentPrefix = "192.1.1.1/24";
    String ipToken = safePost(postIPAllocate, "rpkiroot", new String[]{userKey, userIP,
      parentPrefix});
    safePost(postDlgToken, userKeyFile, new String[]{ipToken, userIP});
    safePost(updateSubjectSet, userKeyFile, new String[]{ipToken});
    for(String sdxKey: MultiSdxSetting.sdxKeyMap.values()) {
      authorize(authorizeOwnPrefix, sdxKey, new String[]{userKey, uip});
    }

    //Tag delegation

    //userTagAcl
    //user post Connect policy
    if(MultiSdxSetting.userTags.containsKey(slice)) {
      for(String t: MultiSdxSetting.userTags.get(slice)) {
        String tag = principalMap.get("tagauthority") + ":" + t;
        safePost(postTagSet, "tagauthority", new String[]{tag});
        String tagToken = safePost(postGrantTagPriv, "tagauthority", new Object[]{userKey, tag, true});
        safePost(updateTagSet, userKeyFile, new String[]{tagToken, tag});
        safePost(updateSubjectSet, userKeyFile, new String[]{tagToken});
        safePost(postUserTagAclEntry, userKeyFile, new String[]{tag});
      }
    }

    //post AS tag acls
    if(MultiSdxSetting.userASTagAcls.containsKey(slice)) {
      for(String t: MultiSdxSetting.userASTagAcls.get(slice)) {
        String astag = principalMap.get("tagauthority") + ":" + t;
        safePost(postASTagAclEntry, userKeyFile, new String[]{astag, uip});
      }
    }
    safePost(postCustomerConnectionPolicy, userKeyFile, new String[]{});
    safePost(postTagPrivilegePolicy, userKeyFile, new String[]{});
    safePost(postCustomerPolicy, userKeyFile, new String[]{});
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
      List<String> tokens = SafeUtils.getTokens(passDelegation(sliceKeyMap.get(slice), pmToken,
        projectId));
      envs.clear();
      envs.put(subject, userKey);
      envs.put(bearerRef, tokens.get(1));
      assert authorize(createSlice, "key_p3", new String[]{projectId}, envs);
    }

    /*
    The previous part is the common geni trust structure.
    The next is specific to our example

     */
    //create slices.
    String sliceControlRef = safePost(postStandardSliceControlSet, "key_p3");
    String slicePrivRef = safePost(postStandardSliceDefaultPrivilegeSet, "key_p3");
    for (String slice : clientSlices) {
      String sliceId = principalMap.get("key_p3") + ":" + slice;
      sliceScid.put(slice, sliceId);
      sliceToken.put(slice, safePost(postSliceSet, "key_p3",
        new String[]{principalMap.get(sliceKeyMap.get(slice)), sliceId, projectId,
          sliceControlRef,
          slicePrivRef}));
      SafeUtils.getTokens(passDelegation(sliceKeyMap.get(slice),
        sliceToken.get(slice), sliceId));
    }

    //UserAcl
    for (String slice : clientSlices) {
      safePost(postUserAclEntry, "sdx",
        new String[]{principalMap.get(sliceKeyMap.get(slice))});
    }

    //post authorize policy
    safePost(postStitchPolicy, "sdx");

    //authorizeStitchByUid
    for (String slice : clientSlices) {
      authorize(authorizeStitchByUID, "sdx",
        new String[]{principalMap.get(sliceKeyMap.get(slice)), sliceScid.get(slice)});
    }

    //MakeIp Delegation
    safePost(postMakeIPTokenSet, "rpkiroot", new String[]{"192.1.1.1/24"});
    for (String slice : clientSlices) {
      String ip = sliceIpMap.get(slice);
      String user = sliceKeyMap.get(slice);
      String userKey = principalMap.get(sliceKeyMap.get(slice));
      String ipToken = safePost(postIPAllocate, "rpkiroot", new String[]{userKey, ip,
        "192.1.1.1/24"});
      safePost(postDlgToken, user, new String[]{ipToken, ip});
      assert authorize(authorizeOwnPrefix, "sdx", new String[]{userKey, ip});
    }

    //Tag delegation
    String tag = principalMap.get("tagauthority") + ":tag0";
    safePost(postTagSet, "tagauthority", new String[]{tag});
    for (String slice : clientSlices) {
      String user = sliceKeyMap.get(slice);
      String userKey = principalMap.get(sliceKeyMap.get(slice));
      String tagToken = safePost(postGrantTagPriv, "tagauthority", new String[]{userKey, tag});
      safePost(updateTagSet, user, new String[]{tagToken, tag});
    }

    //userTagAcl
    //user post Connect policy
    for (String slice : clientSlices) {
      String user = sliceKeyMap.get(slice);
      safePost(postUserTagAclEntry, user, new String[]{tag});
      safePost(postCustomerConnectionPolicy, user, new String[]{});
      safePost(postTagPrivilegePolicy, user, new String[]{});
      safePost(postCustomerPolicy, user, new String[]{});
    }


    //authZByUserAttr
    for (int i = 0; i < 2; i++) {
      String slice = clientSlices.get(i);
      String user = sliceKeyMap.get(slice);
      String userKey = principalMap.get(user);
      String ip = sliceIpMap.get(slice);
      for (int j = i + 1; j < 2; j++) {
        String peerSlice = clientSlices.get(j);
        String peer = sliceKeyMap.get(peerSlice);
        String peerKey = principalMap.get(peer);
        String peerIp = sliceIpMap.get(peerSlice);
        assert authorize(authZByUserAttr, "sdx", new String[]{userKey, ip, peerKey, peerIp});
      }
    }
    logger.debug("end");

  }

  private String getPrincipalId(String key) {
    if (principalMap.containsKey(key)) {
      return principalMap.get(key);
    }
    String pid = SafeUtils.getPrincipalId(safeServer, key);
    if (pid != null) {
      principalMap.put(key, pid);
    }
    return pid;
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

    for (int i = 4; i < 20; i++) {
      principals.add("key_p" + i);
    }
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
    for (int i = 1; i < 6; i++) {
      assert authorize(authorizeStitchByUID, "sdx",
        new String[]{principalMap.get(sliceKeyMap.get(slices[i])), sliceScid.get(slices[i])});
    }
    for (int i = 1; i < 6; i++) {
      String slice = slices[i];
      String user = sliceKeyMap.get(slice);
      String userKey = principalMap.get(user);
      String ip = sliceIpMap.get(slice);
      for (int j = i + 1; j < 6; j++) {
        String peerSlice = slices[j];
        String peer = sliceKeyMap.get(peerSlice);
        String peerKey = principalMap.get(peer);
        String peerIp = sliceIpMap.get(peerSlice);
        authorize(authZByUserAttr, "sdx", new String[]{userKey, ip, peerKey, peerIp});
      }
    }
  }


  String safePost(String method, String principal) {
    return safePost(method, principal, new String[]{});
  }

  String safePost(String method, String principal, String[] others) {
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