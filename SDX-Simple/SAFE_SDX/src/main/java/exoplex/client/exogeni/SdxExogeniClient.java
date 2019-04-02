/**
 *
 */
package exoplex.client.exogeni;

import exoplex.common.slice.SliceCommon;
import exoplex.common.slice.SliceManager;
import exoplex.common.utils.Exec;
import exoplex.common.utils.HttpUtil;
import exoplex.common.utils.SafeUtils;
import exoplex.common.utils.ServerOptions;
import exoplex.sdx.advertise.PolicyAdvertise;
import exoplex.sdx.advertise.RouteAdvertise;
import exoplex.sdx.safe.SafeManager;
import org.apache.commons.cli.CommandLine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.renci.ahab.libndl.resources.request.ComputeNode;
import org.renci.ahab.libtransport.util.TransportException;
import safe.SdxRoutingSlang;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * @author geni-orca
 */
public class SdxExogeniClient extends SliceCommon {
  final Logger logger = LogManager.getLogger(SdxExogeniClient.class);
  protected SafeManager safeManager = null;
  private String logPrefix = "";
  private String ipIprefix;
  private SliceManager serverSlice = null;
  private boolean safeChecked = false;
  private CommandLine cmd;

  public SdxExogeniClient(String sliceName, String IPPrefix, String safeKeyFile, String[] args) {
    cmd = ServerOptions.parseCmd(args);
    String configFilePath = cmd.getOptionValue("config");
    initializeExoGENIContexts(configFilePath);
    this.ipIprefix = IPPrefix;
    this.safeKeyFile = safeKeyFile;
    this.sliceName = sliceName;
    logPrefix = "[" + sliceName + "] ";
    logger.info(logPrefix + "Client start");
  }

  public SdxExogeniClient(String[] args) {
    //Example usage: ./target/appassembler/bin/SafeSdxClient -f alice.conf
    //pemLocation = args[0];
    //keyLocation = args[1];
    //controllerUrl = args[2]; //"https://geni.renci.org:11443/orca/xmlrpc";
    //sliceName = args[3];
    //sshkey=args[6];
    //keyhash=args[7];

    cmd = ServerOptions.parseCmd(args);
    String configFilePath = cmd.getOptionValue("config");
    initializeExoGENIContexts(configFilePath);

    sliceProxy = SliceManager.getSliceProxy(pemLocation, keyLocation, controllerUrl);

    logPrefix = "[" + sliceName + "] ";

    logger.info(logPrefix + "Client start");
  }

  public static void main(String[] args) {

    SdxExogeniClient sdxExogeniClient = new SdxExogeniClient(args);
    sdxExogeniClient.run(args);
  }

  public void setSafeServer(String safeIP) {
    setSafeServerIp(safeIP);
    safeChecked = true;
  }

  private void loadSlice() throws Exception {
    serverSlice = new SliceManager(sliceName, pemLocation, keyLocation, controllerUrl);
    serverSlice.reloadSlice();
  }

  public void run(String[] args) {
    try {
      loadSlice();
      checkSafe();
    } catch (Exception e) {
      e.printStackTrace();
    }
    if (cmd.hasOption('e')) {
      String command = cmd.getOptionValue('e');
      processCmd(command);
      return;
    }
    String input = new String();
    String cmdprefix = sliceName + "$>";
    try {
//	 			logger.info(logPrefix + obj.sayHello());
      BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
      while (true) {
        System.out.print("Enter Commands:stitch client_resource_name [clientIntfIP] " +
          "[sdxIntfIPw/netmask]\n\t " +
          "unstitch client_resource_name\n\t" +
          "advertise route: route dest gateway\n\t" +
          "link prefix1 prefix2\n" +
          ""
          + cmdprefix);
        input = stdin.readLine();
        System.out.print("continue?[y/n]\n$>" + input);
        input = stdin.readLine();
        if (input.startsWith("y")) {
          processCmd(input);
        }
      }
    } catch (Exception e) {
      logger.error(logPrefix + "HttpClient exception: " + e.getMessage());
      e.printStackTrace();
    }
    logger.info(logPrefix + "XXXXXXXXXX Done XXXXXXXXXXXXXX");
  }

  public String processCmd(String command) {
    try {
      String[] params = command.split(" ");
      if (params[0].equals("stitch")) {
        return processStitchCmd(params);
      } else if (params[0].equals("link")) {
        processConnectionCmd(params);
      } else if (params[0].equals("unstitch")) {
        processUnStitchCmd(params);
      } else if (params[0].equals("route")) {
        processPrefixCmd(params);
      } else if (params[0].equals("bgp")) {
        processBgpCmd(params);
      } else if (params[0].equals("policy")) {
        processPolicyCmd(params);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  public boolean ping(String nodeName, String ip) {
    if (serverSlice == null) {
      try {
        loadSlice();
      } catch (Exception e) {
        logger.warn(e.getMessage());
        if (serverSlice == null) {
          return false;
        }
      }
    }
    ComputeNode node = serverSlice.getComputeNode(nodeName);
    String res[] = Exec.sshExec("root", node.getManagementIP(), "ping  -c 1 " + ip, sshkey);
    logger.debug(res[0]);
    return res[0].contains("1 received");
  }

  public String traceRoute(String nodeName, String ip) {
    if (serverSlice == null) {
      try {
        loadSlice();
      } catch (Exception e) {
        logger.warn(e.getMessage());
        if (serverSlice == null) {
          return null;
        }
      }
    }
    ComputeNode node = serverSlice.getComputeNode(nodeName);
    String res[] = Exec.sshExec("root", node.getManagementIP(), "traceroute " + ip, sshkey);
    logger.debug(res[0]);
    return res[0];
  }

  public boolean checkConnectivity(String nodeName, String ip) {
    for (int i = 0; i < 4; i++) {
      if (ping(nodeName, ip)) {
        logger.info(String.format("%s connect to %s: Ok", logPrefix, ip));
        return true;
      } else {
        try {
          Thread.sleep(2);
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }
    logger.warn(String.format("%s connect to %s: Failed", logPrefix, ip));
    return false;
  }

  private void processConnectionCmd(String[] params) {
    try {
      JSONObject jsonparams = new JSONObject();
      if (safeEnabled) {
        if (!safeChecked) {
          if (serverSlice.getResourceByName("safe-server") != null) {
            setSafeServerIp(serverSlice.getComputeNode("safe-server").getManagementIP());
          } else {
            setSafeServerIp(conf.getString("config.safeserver"));
          }
          //sm.verifySafeInstallation(riakIp);
          safeChecked = true;
        }
        safeKeyHash = SafeUtils.getPrincipalId(safeServer, safeKeyFile);
        jsonparams.put("ckeyhash", safeKeyHash);
      } else {
        jsonparams.put("ckeyhash", sliceName);
      }
      jsonparams.put("self_prefix", params[1]);
      jsonparams.put("target_prefix", params[2]);
      try {
        jsonparams.put("bandwidth", Long.valueOf(params[3]));
      } catch (Exception e) {
      }
      String res = HttpUtil.postJSON(serverurl + "sdx/connectionrequest", jsonparams);
      logger.info(logPrefix + "get connection result from server:\n" + res);
      logger.debug(res);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void advertiseBgp(String peerUrl, RouteAdvertise advertise) {
    HttpUtil.postJSON(peerUrl + "sdx/bgp", advertise.toJsonObject());
  }

  private void advertisePolicy(String peerUrl, PolicyAdvertise advertise) {
    HttpUtil.postJSON(peerUrl + "sdx/policy", advertise.toJsonObject());
  }

  private void processPolicyCmd(String[] params) {
    String tagAuthorityPid = SafeUtils.getPrincipalId(safeServer, "tagauthority");
    String destPrefix = params[1];
    String srcPrefix = params[2];
    String astag = params[3];
    String tag = String.format("%s:%s", tagAuthorityPid, astag);

    PolicyAdvertise policyAdvertise = new PolicyAdvertise();
    policyAdvertise.srcPrefix = srcPrefix;
    policyAdvertise.destPrefix = destPrefix;
    String sdToken = safeManager.postSdPolicySet(tag, policyAdvertise.getSrcPrefix(),
      policyAdvertise.getDestPrefix());
    policyAdvertise.ownerPID = safeKeyHash;
    policyAdvertise.safeToken = sdToken;
    advertisePolicy(serverurl, policyAdvertise);
    logger.debug("client posted SD policy set and made policy advertisement");
  }

  private void processBgpCmd(String[] params) {
    if (safeEnabled) {
      String token = null;
      checkSafe();
      RouteAdvertise advertise = new RouteAdvertise();
      advertise.destPrefix = params[1];
      advertise.srcPrefix = params[2];
      advertise.advertiserPID = safeKeyHash;
      advertise.ownerPID = safeKeyHash;
      advertise.route.add(safeKeyHash);
      if (params.length > 3) {
        //need special tag acl
        // postASTagAclEntrySD
        String[] vars = new String[3];
        String tagAuth = SafeUtils.getPrincipalId(safeServer, "tagauthority");
        String tag = String.format("%s:%s", tagAuth, params[3]);
        String res = safeManager.postSdPolicySet(tag, advertise.getSrcPrefix(), advertise
          .getDestPrefix());
        logger.debug(res);
      }
      //Post SAFE sets
      //postInitRouteSD

      String[] safeparams = new String[5];
      safeparams[0] = advertise.getSrcPrefix();
      safeparams[1] = advertise.getDestPrefix();
      safeparams[2] = advertise.getFormattedPath();
      String sdxPid = HttpUtil.get(serverurl + "sdx/getpid");
      safeparams[3] = sdxPid;
      safeparams[4] = String.valueOf(1);
      logger.debug(String.format("Safe principal Id of sdx server is %s", sdxPid));
      String routeToken = SafeUtils.getToken(SafeUtils.postSafeStatements(safeServer,
        SdxRoutingSlang.postInitRouteSD, safeKeyHash, safeparams));
      advertise.safeToken = routeToken;
      //pass the token when making bgpAdvertise
      advertiseBgp(serverurl, advertise);
      logger.debug(String.format("posted initRouteSD statement for dst %s src %s pair", advertise
        .destPrefix, advertise.srcPrefix));
    }
  }

  private void checkSafe() {
    if (safeEnabled) {
      if (safeInSlice && serverSlice.getResourceByName("safe-server") != null) {
        setSafeServerIp(serverSlice.getComputeNode("safe-server").getManagementIP());
      } else {
        setSafeServerIp(conf.getString("config.safeserver"));
      }
      safeManager = new SafeManager(safeServerIp, safeKeyFile, sshkey);
      safeChecked = true;
      safeKeyHash = SafeUtils.getPrincipalId(safeServer, safeKeyFile);
    }
  }

  private void processPrefixCmd(String[] params) {
    JSONObject paramsobj = new JSONObject();
    paramsobj.put("dest", params[1]);
    paramsobj.put("gateway", params[2]);
    if (safeEnabled) {
      checkSafe();
      paramsobj.put("customer", safeKeyHash);
    } else {
      paramsobj.put("customer", sliceName);
    }
    String res = HttpUtil.postJSON(serverurl + "sdx/notifyprefix", paramsobj);
    JSONObject jsonRes = new JSONObject(res);
    if (!jsonRes.getBoolean("result")) {
      logger.warn(logPrefix + "Prefix not accepted (authorization failed)");
    } else {
      if (safeEnabled) {
        //Make initRoute advertisement
        //dstip, path, targeas, length
        String[] safeparams = new String[4];
        safeparams[0] = String.format("ipv4\\\"%s\\\"", params[1]);
        safeparams[1] = String.format("[%s]", safeKeyHash);
        String sdxSafeKeyHash = jsonRes.getString("safeKeyHash");
        if (sdxSafeKeyHash.equals("")) {
          logger.warn("SDX safekeyhash empty");
        }
        safeparams[2] = sdxSafeKeyHash;
        safeparams[3] = String.valueOf(1);
        String token = SafeUtils.getToken(SafeUtils.postSafeStatements(safeServer, SdxRoutingSlang
          .postInitRoute, safeKeyHash, safeparams));
        RouteAdvertise advertise = new RouteAdvertise();
        advertise.safeToken = token;
        advertise.advertiserPID = safeKeyHash;
        advertise.route.add(safeKeyHash);
        advertise.ownerPID = safeKeyHash;
        advertise.destPrefix = params[1];
        //pass the token when making bgpAdvertise
        advertiseBgp(serverurl, advertise);
        logger.debug("posted initRoute statement");
      }
      logger.info(logPrefix + res);
    }
  }

  public void setServerUrl(String url) {
    this.serverurl = url;
  }

  private String processStitchCmd(String[] params) {
    if (serverSlice == null) {
      try {
        loadSlice();
      } catch (Exception e) {
        logger.error(e.getMessage());
      }
    }
    try {
      ComputeNode node0_s2 = (ComputeNode) serverSlice.getResourceByName(params[1]);
      String node0_s2_stitching_GUID = node0_s2.getStitchingGUID();
      String secret = "mysecret";
      logger.debug("node0_s2_stitching_GUID: " + node0_s2_stitching_GUID);
      try {
        //s1
        sliceProxy.permitSliceStitch(sliceName, node0_s2_stitching_GUID, secret);
      } catch (TransportException e) {
        // TODO Auto-generated catch block
        logger.warn(logPrefix + "Failed to permit stitch");
        e.printStackTrace();
        return null;
      }
      String sdxsite = node0_s2.getDomain();
      //post stitch request to SAFE
      JSONObject jsonparams = new JSONObject();
      jsonparams.put("sdxsite", sdxsite);
      jsonparams.put("cslice", sliceName);
      jsonparams.put("creservid", node0_s2_stitching_GUID);
      jsonparams.put("secret", secret);
      jsonparams.put("gateway", params[2]);
      jsonparams.put("ip", params[3]);
      if (params.length > 4) {
        jsonparams.put("sdxnode", params[4]);
      }
      if (safeEnabled) {
        if (!safeChecked) {
          if (serverSlice.getResourceByName("safe-server") != null) {
            setSafeServerIp(serverSlice.getComputeNode("safe-server").getManagementIP());
          } else {
            setSafeServerIp(conf.getString("config.safeserver"));
          }
          SafeManager sm = new SafeManager(safeServerIp, safeKeyFile, sshkey);
          sm.verifySafeInstallation(riakIp);
          safeChecked = true;
        }
        safeKeyHash = SafeUtils.getPrincipalId(safeServer, safeKeyFile);
        jsonparams.put("ckeyhash", safeKeyHash);
        /*
        postSafeStitchRequest(safeKeyHash, sliceName, node0_s2_stitching_GUID, params[2],
            params[3]);
        */
      } else {
        jsonparams.put("ckeyhash", sliceName);
      }
      logger.debug("Sending stitch request to Sdx server");
      String r = HttpUtil.postJSON(serverurl + "sdx/stitchrequest", jsonparams);
      logger.debug(r);
      JSONObject res = new JSONObject(r);
      logger.info(logPrefix + "Got Stitch Information From Server:\n " + res.toString());
      if (!res.getBoolean("result")) {
        logger.warn(logPrefix + "stitch request failed");
      } else {
        String ip = params[2] + "/" + params[3].split("/")[1];
        logger.info(logPrefix + "set IP address of the stitch interface to " + ip);
        sleep(5);
        String mip = node0_s2.getManagementIP();
        String result = Exec.sshExec("root", mip, "ifconfig eth1 " + ip, sshkey)[0];
        String gateway = params[3].split("/")[0];
        Exec.sshExec("root", mip, "echo \"ip route 192.168.1.1/16 " + gateway +
          "\" >>/etc/quagga/zebra.conf  ", sshkey);
        Exec.sshExec("root", mip, "/etc/init.d/quagga restart", sshkey);
        if (ping(node0_s2.getName(), gateway)) {
          logger.info(String.format("Ping to %s works", gateway));
          logger.info(logPrefix + "stitch completed.");
        } else {
          logger.warn(String.format("Ping to %s doesn't work", gateway));
        }
        return ip.split("/")[0];
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  private String processUnStitchCmd(String[] params) {
    if (serverSlice == null) {
      try {
        loadSlice();
      } catch (Exception e) {
        logger.error(e.getMessage());
      }
    }
    try {
      ComputeNode node0_s2 = (ComputeNode) serverSlice.getResourceByName(params[1]);
      String node0_s2_stitching_GUID = node0_s2.getStitchingGUID();
      logger.debug("node0_s2_stitching_GUID: " + node0_s2_stitching_GUID);
      JSONObject jsonparams = new JSONObject();
      jsonparams.put("cslice", sliceName);
      jsonparams.put("creservid", node0_s2_stitching_GUID);
      if (safeEnabled) {
        if (!safeChecked) {
          if (serverSlice.getResourceByName("safe-server") != null) {
            setSafeServerIp(serverSlice.getComputeNode("safe-server").getManagementIP());
          } else {
            setSafeServerIp(conf.getString("config.safeserver"));
          }
          SafeManager sm = new SafeManager(safeServerIp, safeKeyFile, sshkey);
          sm.verifySafeInstallation(riakIp);
          safeChecked = true;
        }
        safeKeyHash = SafeUtils.getPrincipalId(safeServer, safeKeyFile);
        jsonparams.put("ckeyhash", safeKeyHash);
      } else {
        jsonparams.put("ckeyhash", sliceName);
      }
      logger.debug("Sending unstitch request to Sdx server");
      String r = HttpUtil.postJSON(serverurl + "sdx/undostitch", jsonparams);
      logger.debug(r);
      logger.info(logPrefix + "Unstitch result:\n " + r);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  private void configOSPFForNewInterface(ComputeNode c, String newip) {
    Exec.sshExec("root", c.getManagementIP(), "/bin/bash ~/configospfforif.sh " + newip, "~/.ssh/id_rsa");
  }
}

