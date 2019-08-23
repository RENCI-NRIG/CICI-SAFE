/**
 *
 */
package exoplex.client.exogeni;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import exoplex.common.utils.HttpUtil;
import exoplex.common.utils.ServerOptions;
import exoplex.demo.singlesdx.SingleSdxModule;
import exoplex.sdx.advertise.PolicyAdvertise;
import exoplex.sdx.advertise.RouteAdvertise;
import exoplex.sdx.core.CoreProperties;
import exoplex.sdx.safe.SafeManager;
import exoplex.sdx.slice.Scripts;
import exoplex.sdx.slice.SliceManager;
import exoplex.sdx.slice.SliceManagerFactory;
import exoplex.sdx.slice.SliceProperties;
import org.apache.commons.cli.CommandLine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import safe.SdxRoutingSlang;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;

/**
 * @author geni-orca
 */
public class SdxExogeniClient {
  final Logger logger = LogManager.getLogger(SdxExogeniClient.class);
  protected SafeManager safeManager = null;
  private String logPrefix = "";
  private SliceManager serverSlice = null;
  private boolean safeChecked = false;
  private CommandLine cmd;
  private CoreProperties coreProperties;

  @Inject
  private SliceManagerFactory sliceManagerFactory;

  public SdxExogeniClient() {
  }

  public SdxExogeniClient(String sliceName, String IPPrefix, String safeKeyFile, String[] args) {
    cmd = ServerOptions.parseCmd(args);
    String configFilePath = cmd.getOptionValue("config");
    coreProperties = new CoreProperties(configFilePath);
    coreProperties.setIpPrefix(IPPrefix);
    coreProperties.setSafeKeyFile(safeKeyFile);
    logPrefix = "[" + coreProperties.getSliceName() + "] ";
    logger.info(logPrefix + "Client start");
  }

  public SdxExogeniClient(String[] args) {
    //Example usage: ./target/appassembler/bin/SafeSdxClient -f alice.conf
    //pemLocation = args[0];
    //keyLocation = args[1];
    //controllerUrl = args[2]; //"https://geni.renci.org:11443/orca/xmlrpc";
    //sliceName = args[3];
    //sshKey=args[6];
    //keyhash=args[7];

    cmd = ServerOptions.parseCmd(args);
    String configFilePath = cmd.getOptionValue("config");
    coreProperties = new CoreProperties(configFilePath);

    logPrefix = "[" + coreProperties.getSliceName() + "] ";

    logger.info(logPrefix + "Client start");
  }

  public static void main(String[] args) {

    Injector injector = Guice.createInjector(new SingleSdxModule());
    SdxExogeniClient sdxExogeniClient = injector.getInstance(SdxExogeniClient.class);
    sdxExogeniClient.run(new CoreProperties(args));
  }

  public String getManagementIP(String nodeName) {
    if (serverSlice == null) {
      try {
        loadSlice();
      } catch (Exception e) {
        logger.error(e.getMessage());
      }
    }
    return serverSlice.getManagementIP(nodeName);
  }

  public void config(CoreProperties coreProperties) {
    this.coreProperties = coreProperties;
    logPrefix = "[" + coreProperties.getSliceName() + "] ";
    logger.info(logPrefix + "Client start");
  }

  public void setSafeServer(String safeIP) {
    coreProperties.setSafeServerIp(safeIP);
    if (safeManager == null) {
      safeManager = new SafeManager(coreProperties.getSafeServerIp(),
        coreProperties.getSafeKeyFile(), coreProperties.getSshKey(), true);
    } else {
      safeManager.setSafeServerIp(coreProperties.getSafeServerIp());
    }
    safeChecked = true;
  }

  private void loadSlice() throws Exception {
    serverSlice = sliceManagerFactory.create(
      coreProperties.getSliceName(),
      coreProperties.getExogeniKey(),
      coreProperties.getExogeniKey(),
      coreProperties.getExogeniSm(),
      coreProperties.getSshKey()
    );
    serverSlice.loadSlice();
  }

  public void run(CoreProperties coreProperties) {
    this.coreProperties = coreProperties;
    logPrefix = String.format("[%s]", this.coreProperties.getSliceName());
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
    String input;
    String cmdprefix = coreProperties.getSliceName() + "$>";
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
    checkSafe();
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
      } else if (params[0].equals("acl")) {
        processAclCmd(params);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  /*
  Be cafeful when using async methods
   */
  public void processCmdAsync(String command) {
    Thread thread = new Thread() {
      @Override
      public void run() {
        processCmd(command);
      }
    };
    thread.start();
  }

  public boolean ping(String nodeName, String ip) {
    if (serverSlice == null) {
      try {
        loadSlice();
      } catch (Exception e) {
        e.printStackTrace();
        logger.warn(e.getMessage());
        if (serverSlice == null) {
          return false;
        }
      }
    }
    String node = serverSlice.getComputeNode(nodeName);
    String res = serverSlice.runCmdNode("ping  -c 1 -W 2 " + ip, node, false);
    logger.debug(res);
    return res.contains("1 received");
  }

  public boolean ping(String nodeName, String ip, int num) {
    if (serverSlice == null) {
      try {
        loadSlice();
      } catch (Exception e) {
        e.printStackTrace();
        logger.warn(e.getMessage());
        if (serverSlice == null) {
          return false;
        }
      }
    }
    String node = serverSlice.getComputeNode(nodeName);
    String res = serverSlice.runCmdNode(String.format("ping -i 0.01 -c %s %s", num, ip), node,
      false);
    logger.debug(res);
    return res.contains("received");
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
    String node = serverSlice.getComputeNode(nodeName);
    String res = serverSlice.runCmdNode("traceroute -N 1 " + ip, node, true);
    logger.debug(res);
    return res;
  }

  public boolean checkConnectivity(String nodeName, String ip, int times) {
    for (int i = 0; i < times; i++) {
      if (ping(nodeName, ip)) {
        logger.info(String.format("%s connect to %s: Ok", logPrefix, ip));
        return true;
      } else {
      }
    }
    logger.warn(String.format("%s connect to %s: Failed", logPrefix, ip));
    return false;
  }

  private void processConnectionCmd(String[] params) {
    try {
      JSONObject jsonparams = new JSONObject();
      jsonparams.put("self_prefix", params[1]);
      jsonparams.put("target_prefix", params[2]);
      try {
        jsonparams.put("bandwidth", Long.valueOf(params[3]));
      } catch (Exception e) {
        jsonparams.put("bandwidth", 0l);
      }
      String res = HttpUtil.postJSON(coreProperties.getServerUrl() + "sdx/connectionrequest", jsonparams);
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

  private void advertiseBgpAsync(String peerUrl, RouteAdvertise advertise) {
    Thread thread = new Thread() {
      @Override
      public void run() {
        HttpUtil.postJSON(peerUrl + "sdx/bgp", advertise.toJsonObject());
      }
    };
    thread.start();
  }

  private void advertisePolicyAsync(String peerUrl, PolicyAdvertise advertise) {
    Thread thread = new Thread() {
      @Override
      public void run() {
        HttpUtil.postJSON(peerUrl + "sdx/policy", advertise.toJsonObject());
      }
    };
    thread.start();
  }

  private void processPolicyCmd(String[] params) {
    String tagAuthorityPid = safeManager.getPrincipalId("tagauthority");
    String destPrefix = params[1];
    String srcPrefix = params[2];
    String astag = params[3];
    String tag = String.format("%s:%s", tagAuthorityPid, astag);

    PolicyAdvertise policyAdvertise = new PolicyAdvertise();
    policyAdvertise.srcPrefix = srcPrefix;
    policyAdvertise.destPrefix = destPrefix;
    String sdToken = safeManager.postASTagAclEntrySD(tag, policyAdvertise.getSrcPrefix(),
      policyAdvertise.getDestPrefix());
    String sdSetToken = safeManager.postSdPolicySet(policyAdvertise.getSrcPrefix(),
      policyAdvertise.getDestPrefix());
    policyAdvertise.ownerPID = safeManager.getSafeKeyHash();
    policyAdvertise.safeToken = sdSetToken;
    advertisePolicyAsync(coreProperties.getServerUrl(), policyAdvertise);
    logger.debug("client posted SD policy set and made policy advertisement");
  }

  private void processAclCmd(String[] params) {
    String token = null;
    RouteAdvertise advertise = new RouteAdvertise();
    advertise.destPrefix = params[1];
    advertise.srcPrefix = params[2];
    advertise.advertiserPID = safeManager.getSafeKeyHash();
    advertise.ownerPID = safeManager.getSafeKeyHash();
    advertise.route.add(safeManager.getSafeKeyHash());
    if (params.length > 3) {
      //need special tag acl
      // postASTagAclEntrySD
      String[] vars = new String[3];
      String tagAuth = safeManager.getPrincipalId("tagauthority");
      String tag = String.format("%s:%s", tagAuth, params[3]);
      String res = safeManager.postASTagAclEntrySD(tag, advertise.getSrcPrefix(), advertise
        .getDestPrefix());
      logger.debug(res);
      res = safeManager.postSdPolicySet(advertise.getSrcPrefix(), advertise
        .getDestPrefix());
      logger.debug(res);
    }
    //Post SAFE sets
    //postInitRouteSD
  }

  private void processBgpCmd(String[] params) {
    RouteAdvertise advertise = new RouteAdvertise();
    advertise.destPrefix = params[1];
    advertise.srcPrefix = params[2];
    advertise.advertiserPID = safeManager.getSafeKeyHash();
    advertise.ownerPID = safeManager.getSafeKeyHash();
    advertise.route.add(safeManager.getSafeKeyHash());
    if (params.length > 3) {
      //need special tag acl
      // postASTagAclEntrySD
      String[] vars = new String[3];
      String tagAuth = safeManager.getPrincipalId("tagauthority");
      String tag = String.format("%s:%s", tagAuth, params[3]);
      String res = safeManager.postASTagAclEntrySD(tag, advertise.getSrcPrefix(), advertise
        .getDestPrefix());
      logger.debug(res);
      res = safeManager.postSdPolicySet(advertise.getSrcPrefix(), advertise
        .getDestPrefix());
      logger.debug(res);
    }
    //Post SAFE sets
    //postInitRouteSD

    String[] safeparams = new String[5];
    safeparams[0] = advertise.getSrcPrefix();
    safeparams[1] = advertise.getDestPrefix();
    safeparams[2] = advertise.getFormattedPath();
    String sdxPid = HttpUtil.get(coreProperties.getServerUrl() + "sdx/getpid");
    safeparams[3] = sdxPid;
    safeparams[4] = String.valueOf(1);
    logger.debug(String.format("Safe principal Id of sdx server is %s", sdxPid));
    String routeToken = safeManager.post(SdxRoutingSlang.postInitRouteSD, safeparams);
    advertise.safeToken = routeToken;
    //pass the token when making bgpAdvertise
    advertiseBgpAsync(coreProperties.getServerUrl(), advertise);
    logger.debug(String.format("posted initRouteSD statement for dst %s src %s pair", advertise
      .destPrefix, advertise.srcPrefix));
  }

  private void checkSafe() {
    if (coreProperties.isSafeEnabled() && !safeChecked) {
      if (coreProperties.isSafeInSlice()
        && serverSlice.getResourceByName(SliceProperties.SAFESERVER) != null) {
        coreProperties.setSafeServerIp(serverSlice.getManagementIP(SliceProperties.SAFESERVER));
      }
      safeManager = new SafeManager(coreProperties.getSafeServerIp(), coreProperties.getSafeKeyFile(),
        coreProperties.getSshKey(), true);
      safeChecked = true;
    } else if(!coreProperties.isSafeEnabled()) {
      safeManager = new SafeManager(coreProperties.getSafeServerIp(), coreProperties.getSafeKeyFile(),
        coreProperties.getSshKey(), false);
    }
  }

  private void processPrefixCmd(String[] params) {
    JSONObject paramsobj = new JSONObject();
    paramsobj.put("dest", params[1]);
    paramsobj.put("gateway", params[2]);
    if (coreProperties.isSafeEnabled()) {
      checkSafe();
      paramsobj.put("customer", safeManager.getSafeKeyHash());
    } else {
      paramsobj.put("customer", coreProperties.getSliceName());
    }
    String res = HttpUtil.postJSON(coreProperties.getServerUrl() + "sdx/notifyprefix", paramsobj);
    JSONObject jsonRes = new JSONObject(res);
    if (!jsonRes.getBoolean("result")) {
      logger.warn(logPrefix + "Prefix not accepted (authorization failed)");
    } else {
      if (coreProperties.isSafeEnabled()) {
        //Make initRoute advertisement
        //dstip, path, targeas, length
        String[] safeparams = new String[4];
        safeparams[0] = String.format("ipv4\\\"%s\\\"", params[1]);
        safeparams[1] = String.format("[%s]", safeManager.getSafeKeyHash());
        String sdxSafeKeyHash = jsonRes.getString("safeKeyHash");
        if (sdxSafeKeyHash.equals("")) {
          logger.warn("SDX safekeyhash empty");
        }
        safeparams[2] = sdxSafeKeyHash;
        safeparams[3] = String.valueOf(1);
        String token = safeManager.post(SdxRoutingSlang.postInitRoute, safeparams);
        RouteAdvertise advertise = new RouteAdvertise();
        advertise.safeToken = token;
        advertise.advertiserPID = safeManager.getSafeKeyHash();
        advertise.route.add(safeManager.getSafeKeyHash());
        advertise.ownerPID = safeManager.getSafeKeyHash();
        advertise.destPrefix = params[1];
        //pass the token when making bgpAdvertise
        advertiseBgp(coreProperties.getServerUrl(), advertise);
        logger.debug("posted initRoute statement");
      }
      logger.info(logPrefix + res);
    }
  }

  public void setServerUrl(String url) {
    coreProperties.setServerUrl(url);
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
      String nodeName = serverSlice.getResourceByName(params[1]);
      String nodeStitchingGUID = serverSlice.getStitchingGUID(nodeName);
      String secret = serverSlice.permitStitch(nodeStitchingGUID);
      logger.debug("nodeStitchingGUID: " + nodeStitchingGUID);
      String sdxsite = serverSlice.getNodeDomain(nodeName);
      //post stitch request to SAFE
      JSONObject jsonparams = new JSONObject();
      jsonparams.put("sdxsite", sdxsite);
      jsonparams.put("cslice", coreProperties.getSliceName());
      jsonparams.put("creservid", nodeStitchingGUID);
      jsonparams.put("secret", secret);
      jsonparams.put("gateway", params[2]);
      jsonparams.put("ip", params[3]);
      if (params.length > 4) {
        jsonparams.put("sdxnode", params[4]);
      }
      if (coreProperties.isSafeEnabled()) {
        checkSafe();
        jsonparams.put("ckeyhash", safeManager.getSafeKeyHash());
      } else {
        jsonparams.put("ckeyhash", coreProperties.getSliceName());
      }
      int interfaceNum = serverSlice.getPhysicalInterfaces(nodeName).size();
      logger.debug(String.format(logPrefix + "Number of dataplane interfaces before " +
        "stitching: %s", interfaceNum));
      logger.debug(logPrefix + "Sending stitch request to Sdx server");
      String r = HttpUtil.postJSON(coreProperties.getServerUrl() + "sdx/stitchrequest", jsonparams);
      logger.debug(r);
      JSONObject res = new JSONObject(r);
      logger.info(logPrefix + "Got Stitch Information From Server:\n " + res.toString());
      if (!res.getBoolean("result")) {
        logger.warn(logPrefix + "stitch request failed");
      } else {
        String ip = params[2] + "/" + params[3].split("/")[1];
        logger.info(logPrefix + "set IP address of the stitch interface to " + ip);
        List<String> interfaces = serverSlice.getPhysicalInterfaces(nodeName);
        while (interfaces.size() <= interfaceNum) {
          sleep(5);
          interfaces = serverSlice.getPhysicalInterfaces(nodeName);
          logger.debug(String.format(logPrefix + "Number of dataplane " +
            "interfaces: %s", interfaces.size()));
        }
        String newInterface = interfaces.get(interfaces.size() - 1);

        String mip = serverSlice.getManagementIP(nodeName);
        String result = serverSlice.runCmdNode(String.format("sudo ifconfig " +
            "%s %s", newInterface, ip),
          nodeName, false);
        String gateway = params[3].split("/")[0];
        serverSlice.runCmdNode("sudo bash -c 'echo \"ip route 192.168.1.1/16 " + gateway +
          "\" >>/etc/quagga/zebra.conf'", nodeName, false);
        serverSlice.runCmdNode(Scripts.restartQuagga(), nodeName,
          false);
        if (ping(nodeName, gateway)) {
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

  void sleep(int sec) {
    try {
      Thread.sleep(sec * 1000);
    } catch (Exception e) {

    }
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
      String node0_s2 = serverSlice.getResourceByName(params[1]);
      String node0_s2_stitching_GUID = serverSlice.getStitchingGUID(node0_s2);
      logger.debug("node0_s2_stitching_GUID: " + node0_s2_stitching_GUID);
      JSONObject jsonparams = new JSONObject();
      jsonparams.put("cslice", coreProperties.getSliceName());
      jsonparams.put("creservid", node0_s2_stitching_GUID);
      if (coreProperties.isSafeEnabled()) {
        checkSafe();
        jsonparams.put("ckeyhash", safeManager.getSafeKeyHash());
      } else {
        jsonparams.put("ckeyhash", coreProperties.getSliceName());
      }
      logger.debug("Sending unstitch request to Sdx server");
      String r = HttpUtil.postJSON(coreProperties.getServerUrl() + "sdx/undostitch", jsonparams);
      logger.debug(r);
      logger.info(logPrefix + "Unstitch result:\n " + r);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  private void configOSPFForNewInterface(String c, String newip) {
    serverSlice.runCmdNode("sudo /bin/bash ~/configospfforif.sh " + newip, c,
      false);
  }
}

