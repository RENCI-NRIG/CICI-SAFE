package exoplex.sdx.safe;

import exoplex.common.utils.Exec;
import exoplex.common.utils.SafeUtils;
import exoplex.sdx.advertise.RouteAdvertise;
import exoplex.sdx.core.CoreProperties;
import exoplex.sdx.slice.Scripts;
import exoplex.sdx.slice.SliceProperties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import safe.SdxRoutingSlang;

import java.util.List;

public class SafeManager {
  final static Logger logger = LogManager.getLogger(SafeManager.class);
  private final boolean safeEnabled;
  private String safeServerIp;
  private String safeServer;
  private String safeKeyFile;
  private String sshKey = null;
  private String safeKeyHash = null;

  public SafeManager(String ip, String safeKeyFile, String sshKey, boolean safeEnabled) {
    safeServerIp = ip;
    safeServer = safeServerIp + ":7777";
    this.safeKeyFile = safeKeyFile;
    this.sshKey = sshKey;
    this.safeEnabled = safeEnabled;
    if (safeKeyFile == null) {
      logger.warn("safe key file is null");
    } else {
      try {
        getSafeKeyHash();
      } catch (Exception e) {
      }
    }
    if (!safeEnabled) {
      logger.info("Safe authorization disabled");
    }
  }

  public void setSafeServerIp(String safeServerIp) {
    this.safeServer = safeServerIp + ":7777";
  }

  public String getSafeKeyHash() {
    if (!safeEnabled) {
      return safeKeyFile;
    }
    if (safeKeyHash == null) {
      safeKeyHash = SafeUtils.getPrincipalId(safeServer, safeKeyFile);
    }
    return safeKeyHash;
  }

  public String getPrincipalId(String safeKeyFile) {
    if (!safeEnabled) {
      return safeKeyFile;
    }
    return SafeUtils.getPrincipalId(safeServer, safeKeyFile);
  }

  public boolean authorizeOwnPrefix(String cushash, String cusip) {
    if (!safeEnabled) return true;
    String[] othervalues = new String[2];
    othervalues[0] = cushash;
    othervalues[1] = cusip;
    String message = SafeUtils.postSafeStatements(safeServer, SdxRoutingSlang.authorizeOwnPrefix,
      getSafeKeyHash(),
      othervalues);
    return message == null || !message.contains("Unsatisfied");
  }


  public boolean authorizeConnectivity(String srchash, String srcip, String dsthash, String dstip) {
    if (!safeEnabled) return true;
    String[] othervalues = new String[4];
    othervalues[0] = srchash;
    othervalues[1] = String.format("ipv4\\\"%s\\\"", srcip);
    othervalues[2] = dsthash;
    othervalues[3] = String.format("ipv4\\\"%s\\\"", dstip);
    return SafeUtils.authorize(safeServer, SdxRoutingSlang.authZByUserAttr, getSafeKeyHash(),
      othervalues);
  }

  public void postPathToken(RouteAdvertise advertise) {
    if (!safeEnabled) return;
    if (advertise.srcPrefix == null) {
      String[] params = new String[4];
      params[0] = advertise.safeToken;
      params[1] = advertise.getDestPrefix();
      params[2] = advertise.advertiserPID;
      params[3] = String.valueOf(advertise.route.size());
      post(SdxRoutingSlang.postPathToken, params);
    } else {
      String[] params = new String[5];
      params[0] = advertise.safeToken;
      params[1] = advertise.getSrcPrefix();
      params[2] = advertise.getDestPrefix();
      params[3] = advertise.advertiserPID;
      params[4] = String.valueOf(advertise.route.size());
      post(SdxRoutingSlang.postPathTokenSD, params);
    }
  }

  public String post(String operation, String[] params) {
    if (!safeEnabled) return "safe_disabled";
    String res = null;
    try {
      res = SafeUtils.postSafeStatements(safeServer, operation, getSafeKeyHash(), params);
      return SafeUtils.getToken(res);
    } catch (Exception e) {
      e.printStackTrace();
      logger.warn(String.format("safeserver: %s method: %s principal: %s", safeServer,
        operation, getSafeKeyHash()));
      return res;
    }
  }

  public boolean authorizeBgpAdvertise(RouteAdvertise routeAdvertise) {
    if (!safeEnabled) return true;
    boolean res = false;
    if (routeAdvertise.srcPrefix == null) {
      String[] othervalues = new String[4];
      othervalues[0] = routeAdvertise.ownerPID;
      othervalues[1] = routeAdvertise.getDestPrefix();
      othervalues[2] = routeAdvertise.getFormattedPath();
      othervalues[3] = routeAdvertise.safeToken;
      res = SafeUtils.authorize(safeServer, SdxRoutingSlang.verifyRoute, getSafeKeyHash(),
        othervalues);
    } else {
      String[] othervalues = new String[5];
      othervalues[0] = routeAdvertise.ownerPID;
      othervalues[1] = routeAdvertise.getSrcPrefix();
      othervalues[2] = routeAdvertise.getDestPrefix();
      othervalues[3] = routeAdvertise.getFormattedPath();
      othervalues[4] = routeAdvertise.safeToken;
      res = SafeUtils.authorize(safeServer, SdxRoutingSlang.verifyRouteSD, getSafeKeyHash(),
        othervalues);
    }
    return res;
  }

  public String postASTagAclEntrySD(String tag, String srcIP, String destIP) {
    if (!safeEnabled) return "safeDisabled";
    String[] params = new String[3];
    params[0] = tag;
    params[1] = srcIP;
    params[2] = destIP;
    String res = SafeUtils.postSafeStatements(safeServer, SdxRoutingSlang.postASTagAclEntrySD,
      getSafeKeyHash(), params);
    List<String> tokens = SafeUtils.getTokens(res);
    if (tokens.size() == 1) {
      return tokens.get(0);
    }
    return null;
  }

  public String postSdPolicySet(String srcIP, String destIP) {
    if (!safeEnabled) return "safeDisabled";
    String[] params = new String[2];
    params[0] = srcIP;
    params[1] = destIP;
    String res = SafeUtils.postSafeStatements(safeServer, SdxRoutingSlang.postSdPolicySet,
      getSafeKeyHash(), params);
    List<String> tokens = SafeUtils.getTokens(res);
    if (tokens.size() == 1) {
      return tokens.get(0);
    }
    return null;
  }

  public boolean verifyCompliantPath(String srcPid, String srcIP, String destIP, String
    policyToken, String routeToken, String path) {
    if (!safeEnabled) return true;
    String[] params = new String[6];
    params[0] = srcPid;
    params[1] = srcIP;
    params[2] = destIP;
    params[3] = path;
    params[4] = policyToken;
    params[5] = routeToken;
    return SafeUtils.authorize(safeServer, SdxRoutingSlang.verifyCompliantPath, getSafeKeyHash(),
      params);
  }

  public RouteAdvertise forwardAdvertise(RouteAdvertise routeAdvertise, String targetPid, String
    srcPid) {
    if (routeAdvertise.srcPrefix == null) {
      String[] params = new String[5];
      params[0] = routeAdvertise.getDestPrefix();
      params[1] = routeAdvertise.getFormattedPath();
      params[2] = targetPid;
      params[3] = srcPid;
      params[4] = routeAdvertise.getLength(1);
      String token1 = post(SdxRoutingSlang.postAdvertise, params);
      routeAdvertise.safeToken = token1;
      return routeAdvertise;
    } else {
      String[] params = new String[6];
      params[0] = routeAdvertise.getSrcPrefix();
      params[1] = routeAdvertise.getDestPrefix();
      params[2] = routeAdvertise.getFormattedPath();
      params[3] = targetPid;
      params[4] = srcPid;
      params[5] = routeAdvertise.getLength(1);
      String token1 = post(SdxRoutingSlang.postAdvertiseSD, params);
      routeAdvertise.safeToken = token1;
      return routeAdvertise;

    }
  }

  public boolean authorizeStitchRequest(String customerSafeKeyHash,
                                        String customerSlice
  ) {
    /** Post to remote safesets using apache httpclient */
    if (!safeEnabled) return true;
    String[] othervalues = new String[2];
    othervalues[0] = customerSafeKeyHash;
    String saHash = SafeUtils.getPrincipalId(safeServer, "key_p3");
    String sdxHash = SafeUtils.getPrincipalId(safeServer, this.safeKeyFile);
    othervalues[1] = saHash + ":" + customerSlice;
    boolean res = SafeUtils.authorize(safeServer, SdxRoutingSlang.authorizeStitchByUID, sdxHash, othervalues);
    if (!res) {
      logger.warn("stitchRequest failed");
    }
    return res;
  }

  public boolean verifyAS(String owner, String dstIP, String as, String token) {
    /** Post to remote safesets using apache httpclient */
    if (!safeEnabled) return true;
    String[] othervalues = new String[4];
    othervalues[0] = owner;
    othervalues[1] = dstIP;
    othervalues[2] = as;
    othervalues[3] = token;
    String sdxHash = SafeUtils.getPrincipalId(safeServer, this.safeKeyFile);
    return SafeUtils.authorize(safeServer, SdxRoutingSlang.verifyAS, sdxHash, othervalues);
  }

  public boolean verifyAS(String owner, String srcIP, String dstIP, String as, String token) {
    /** Post to remote safesets using apache httpclient */
    if (!safeEnabled) return true;
    String[] othervalues = new String[5];
    othervalues[0] = owner;
    othervalues[1] = srcIP;
    othervalues[2] = dstIP;
    othervalues[3] = as;
    othervalues[4] = token;
    String sdxHash = SafeUtils.getPrincipalId(safeServer, this.safeKeyFile);
    return SafeUtils.authorize(safeServer, SdxRoutingSlang.verifyASSD, sdxHash, othervalues);
  }

  public boolean authorizeChameleonStitchRequest(String customerSafeKeyHash,
                                                 String stitchPort,
                                                 String vlan
  ) {
    /** Post to remote safesets using apache httpclient */
    if (!safeEnabled) return true;
    String[] othervalues = new String[3];
    othervalues[0] = customerSafeKeyHash;
    othervalues[1] = stitchPort;
    othervalues[2] = vlan;
    String sdxHash = SafeUtils.getPrincipalId(safeServer, "sdx");
    return SafeUtils.authorize(safeServer, "authorizeChameleonStitchByUID", sdxHash, othervalues);
  }

  public void restartSafeServer() {
    Exec.sshExec(SliceProperties.userName, safeServerIp, Scripts.restartSafe_v1(CoreProperties
      .getSafeServerScript()), sshKey);
  }

  public boolean verifySafeInstallation(String riakIp) {
    if (!safeEnabled) return true;
    if (safeServerAlive()) {
      return true;
    }
    while (true) {
      String result = Exec.sshExec(SliceProperties.userName, safeServerIp,
        Scripts.dockerImages(), sshKey)[0];
      if (result.contains(CoreProperties.getSafeDockerImage())) {
        break;
      } else {
        Exec.sshExec(SliceProperties.userName, safeServerIp, Scripts.getSafeScript_v1(riakIp,
          CoreProperties.getSafeDockerImage(),
          CoreProperties.getSafeServerScript()), sshKey);
      }
    }
    while (true) {
      String result = Exec.sshExec(SliceProperties.userName, safeServerIp,
        Scripts.dockerPs(), sshKey)[0];
      if (result.contains("safe")) {
        break;
      } else {
        Exec.sshExec(SliceProperties.userName, safeServerIp, Scripts.getSafeScript_v1(riakIp,
          CoreProperties.getSafeDockerImage(),
          CoreProperties.getSafeServerScript()),
          sshKey);
      }
    }
    Exec.sshExec(SliceProperties.userName, safeServerIp,
      Scripts.restartSafe_v1(CoreProperties.getSafeServerScript()), sshKey);
    while (true) {
      if (safeServerAlive()) {
        break;
      } else {
        try {
          Thread.sleep(10000);
        } catch (Exception e) {
        }
      }
    }
    logger.debug("Safe server alive now");
    return true;
  }

  private boolean safeServerAlive() {
    if (!safeEnabled) return true;
    try {
      SafeUtils.getPrincipalId(safeServer, "sdx");
    } catch (Exception e) {
      logger.debug(String.format("[%s] Safe server not alive yet", safeServerIp));
      return false;
    }
    return true;
  }
}
