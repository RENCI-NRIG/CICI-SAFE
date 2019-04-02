package safe;

import exoplex.common.utils.SafeUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;

public abstract class AuthorityBase implements SafeLang {
  public static boolean authorizationMade = false;
  public static HashMap<String, String> principalMap = new HashMap<>();
  public static HashMap<String, String> subjectSet = new HashMap<String, String>();
  static Logger logger = LogManager.getLogger(AuthorityBase.class);
  public String safeServer;
  public ArrayList<String> principals = new ArrayList<>();

  public AuthorityBase(String safeServer) {
    this.safeServer = safeServer;
  }

  abstract public void makeSafePreparation();

  public void initPrincipals() {
    principals.forEach(p -> {
      initIdSetSubjectSet(p);
      principalMap.put(p, SafeUtils.getPrincipalId(safeServer, p));
    });
  }

  public void initIdSetSubjectSet(String key) {
    SafeUtils.postSafeStatements(safeServer, postIdSet, key, new String[]{key});
    String token = SafeUtils.getToken(SafeUtils.postSafeStatements(safeServer, postSubjectSet, key, new
      String[]{}));
    subjectSet.put(key, token);
  }

  public String getPrincipalId(String safeKey) {
    if (principalMap.containsKey(safeKey)) {
      return principalMap.get(safeKey);
    } else {
      String key = SafeUtils.getPrincipalId(safeServer, safeKey);
      if (key != null) {
        principalMap.put(safeKey, key);
      }
      return key;
    }
  }

  public String safePost(String method, String principal) {
    return safePost(method, principal, new Object[]{});
  }

  public String safePost(String method, String principal, Object[] others) {
    String p = principalMap.get(principal);
    String msg = SafeUtils.postSafeStatements(safeServer, method, p, others);
    return SafeUtils.getToken(msg);
  }

  public boolean authorize(String method, String principal, String[] otherValues) {
    String p = principalMap.getOrDefault(principal, principal);
    return SafeUtils.authorize(safeServer, method, p, otherValues);
  }

  public boolean authorize(String method, String principal, String[] otherValues, HashMap<String,
    String> envs) {
    String p = principalMap.getOrDefault(principal, principal);
    return SafeUtils.authorize(safeServer, method, p, otherValues, envs);
  }

  public String simpleEndorseMent(String method, String from, String to) {
    String fp = principalMap.getOrDefault(from, from);
    String tp = principalMap.get(to);
    return SafeUtils.getToken(SafeUtils.postSafeStatements(safeServer, method, fp, new
      String[]{tp}));
  }

  public String simpleEndorseMent(String method, String from, String to, String scid) {
    String fp = principalMap.getOrDefault(from, from);
    String tp = principalMap.get(to);
    String token = SafeUtils.getToken(SafeUtils.postSafeStatements(safeServer, method, fp, new
      String[]{tp}));
    return SafeUtils.getToken(passDelegation(to, token, scid));
  }

  public String passDelegation(String principal, String Token, String scid) {
    String p = principalMap.getOrDefault(principal, principal);
    return SafeUtils.postSafeStatements(safeServer, passDelegation, p, new String[]{Token, scid});
  }

  public String simpleDelegation(String method, String from, String to, String scid) {
    String token = simpleEndorseMent(method, from, to, scid);
    return passDelegation(to, token, scid);
  }
}
