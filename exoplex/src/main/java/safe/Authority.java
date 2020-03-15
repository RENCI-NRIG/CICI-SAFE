package safe;

import exoplex.common.utils.SafeUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public abstract class Authority implements SafeLang {
  public static boolean authorizationMade = false;
  public static HashMap<String, String> principalMap = new HashMap<>();
  public static HashMap<String, String> subjectSet = new HashMap<String, String>();
  static Logger logger = LogManager.getLogger(Authority.class);
  public String safeServer;
  public ArrayList<String> principals = new ArrayList<>();
  public ArrayList<Thread> threadList = new ArrayList<>();

  public Authority() {

  }

  public Authority(String safeServer) {
    this.safeServer = safeServer;
  }

  public void setSafeServer(String safeServer) {
    this.safeServer = safeServer;
  }

  abstract public void makeSafePreparation();

  public void initPrincipals() {
    List<Thread> tlist = new ArrayList<>();
    principals.forEach(p -> {
      Thread t = new Thread() {
        @Override
        public void run() {
          initIdSetSubjectSet(p);
          principalMap.put(p, SafeUtils.getPrincipalId(safeServer, p));
        }
      };
      tlist.add(t);
    });
    for(Thread t: tlist) {
      t.start();
    }
    try {
      for (Thread t : tlist) {
        t.join();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void initIdSetSubjectSet(String key) {
    String message = null;
    try {
      SafeUtils.postSafeStatements(safeServer, postIdSet, key, new String[]{key});
      message = SafeUtils.postSafeStatements(safeServer, postSubjectSet, key, new
        String[]{});
      String token = SafeUtils.getToken(message);
      subjectSet.put(key, token);
    } catch (Exception e) {
      e.printStackTrace();
      logger.warn(String.format("safeserver: %s method: postSubjectSet message: %s", safeServer, message));
    }
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

  public void safePostAsync(String method, String principal) {
    Thread t = new Thread() {
      @Override
      public void run() {
        safePost(method, principal);
      }
    };
    threadList.add(t);
    t.start();
  }

  public String safePost(String method, String principal, Object[] others) {
    String p = principalMap.getOrDefault(principal, principal);
    String msg = SafeUtils.postSafeStatements(safeServer, method, p, others);
    return SafeUtils.getToken(msg);
  }

  public void safePostAsync(String method, String principal, Object[] others) {
    Thread t = new Thread() {
      @Override
      public void run() {
        safePost(method, principal, others);
      }
    };
    threadList.add(t);
    t.start();
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
