/**
 *
 */
package exoplex.client.exogeni;

import exoplex.common.slice.SafeSlice;
import exoplex.common.slice.SliceCommon;
import exoplex.common.utils.Exec;
import exoplex.common.utils.HttpUtil;
import exoplex.common.utils.SafeUtils;
import exoplex.common.utils.ServerOptions;
import exoplex.sdx.bgp.BgpAdvertise;
import exoplex.sdx.core.SliceManager;
import exoplex.sdx.safe.SafeManager;
import org.apache.commons.cli.CommandLine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.renci.ahab.libndl.resources.request.ComputeNode;
import org.renci.ahab.libndl.resources.request.Network;
import org.renci.ahab.libtransport.SSHAccessToken;
import org.renci.ahab.libtransport.SliceAccessContext;
import org.renci.ahab.libtransport.util.ContextTransportException;
import org.renci.ahab.libtransport.util.SSHAccessTokenFileFactory;
import org.renci.ahab.libtransport.util.TransportException;
import org.renci.ahab.libtransport.util.UtilTransportException;
import safe.SdxRoutingSlang;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * @author geni-orca
 */
public class SdxExogeniClient extends SliceCommon{
  final Logger logger = LogManager.getLogger(SdxExogeniClient.class);
  private String logPrefix = "";
  private String ipIprefix;
  private SafeSlice serverSlice = null;
  private boolean safeChecked = false;
  private CommandLine cmd;

  public static void main(String[] args){

    SdxExogeniClient sdxExogeniClient = new SdxExogeniClient(args);
    sdxExogeniClient.run(args);
  }

  public SdxExogeniClient(String sliceName, String IPPrefix, String safeKeyFile, String[] args){
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

    sliceProxy = SafeSlice.getSliceProxy(pemLocation, keyLocation, controllerUrl);

    logPrefix = "[" + sliceName + "] ";

    logger.info(logPrefix + "Client start");
  }

  public void setSafeServer(String safeIP){
    setSafeServerIp(safeIP);
    safeChecked = true;
  }
  public void run(String[] args) {
    try {
      serverSlice = SafeSlice.loadManifestFile(sliceName, pemLocation, keyLocation, controllerUrl);
      if(safeEnabled){
          if(serverSlice.getResourceByName("safe-server")!= null) {
            setSafeServerIp(serverSlice.getComputeNode("safe-server").getManagementIP());
          }else {
            setSafeServerIp(conf.getString("config.safeserver"));
          }
          safeChecked = true;
      }
    }catch (Exception e){
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
            "advertise route: route dest gateway\n\t link site1[RENCI] " +
            "site2[SL] \n" + cmdprefix);
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
      }else if(params[0].equals("unstitch")){
        processUnStitchCmd(params);
      } else {
        processPrefixCmd(params);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  public boolean ping(String nodeName, String ip) {

    try {
      serverSlice = SafeSlice.loadManifestFile(sliceName, pemLocation, keyLocation, controllerUrl);
    } catch (Exception e) {
      logger.warn(e.getMessage());
      if(serverSlice == null) {
        return false;
      }
    }
    ComputeNode node = serverSlice.getComputeNode(nodeName);
    String res[] = Exec.sshExec("root", node.getManagementIP(), "ping  -c 1 " + ip, sshkey);
    logger.debug(res[0]);
    return res[0].contains("1 received");
  }

  public boolean checkConnectivity(String nodeName, String ip) {
    for (int i = 0; i < 5; i++) {
      if (ping(nodeName, ip)) {
        logger.info(String.format("%s connect to %s: Ok", logPrefix, ip));
        return true;
      }else{
        try{
          Thread.sleep(2);
        }catch (Exception e){
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
      if(safeEnabled) {
        if (!safeChecked) {
          if(serverSlice.getResourceByName("safe-server")!=null){
            setSafeServerIp(serverSlice.getComputeNode("safe-server").getManagementIP());
          }else {
            setSafeServerIp(conf.getString("config.safeserver"));
          }
          //sm.verifySafeInstallation(riakIp);
          safeChecked = true;
        }
        safeKeyHash = SafeUtils.getPrincipalId(safeServer, safeKeyFile);
        jsonparams.put("ckeyhash", safeKeyHash);
      }else {
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

  private void advertiseBgp(String peerUrl, BgpAdvertise advertise){
    HttpUtil.postJSON(peerUrl + "sdx/bgp", advertise.toJsonObject());
  }

  private void processPrefixCmd(String[] params) {
    JSONObject paramsobj = new JSONObject();
    paramsobj.put("dest", params[1]);
    paramsobj.put("gateway", params[2]);
    if(safeEnabled) {
      if (!safeChecked) {
        if(serverSlice.getResourceByName("safe-server")!=null){
          setSafeServerIp(serverSlice.getComputeNode("safe-server").getManagementIP());
        }else {
          setSafeServerIp(conf.getString("config.safeserver"));
        }
        safeChecked = true;
      }
      safeKeyHash = SafeUtils.getPrincipalId(safeServer, safeKeyFile);
      paramsobj.put("customer", safeKeyHash);


    }else {
      paramsobj.put("customer", sliceName);
    }
    String res = HttpUtil.postJSON(serverurl + "sdx/notifyprefix", paramsobj);
    JSONObject jsonRes = new JSONObject(res);
    if (!jsonRes.getBoolean("result")) {
      logger.warn(logPrefix + "Prefix not accepted (authorization failed)");
    } else {
      if(safeEnabled) {
        //Make initRoute advertisement
        //dstip, path, targeas, length
        String[] safeparams = new String[4];
        safeparams[0] = String.format("ipv4\\\"%s\\\"", params[1]);
        safeparams[1] = String.format("[%s]", safeKeyHash);
        String sdxSafeKeyHash = jsonRes.getString("safeKeyHash");
        if(sdxSafeKeyHash.equals("")){
          logger.warn("SDX safekeyhash empty");
        }
        safeparams[2] = sdxSafeKeyHash;
        safeparams[3] = String.valueOf(1);
        String token = SafeUtils.getToken(SafeUtils.postSafeStatements(safeServer, SdxRoutingSlang
            .postInitRoute, safeKeyHash, safeparams));
        BgpAdvertise advertise = new BgpAdvertise();
        advertise.safeToken = token;
        advertise.advertiserPID = safeKeyHash;
        advertise.route.add(safeKeyHash);
        advertise.ownerPID = safeKeyHash;
        advertise.prefix = params[1];
        //pass the token when making bgpAdvertise
        advertiseBgp(serverurl,advertise);
        logger.debug("posted initRoute statement");
      }
      logger.info(logPrefix + res);
    }
  }

  public void setServerUrl(String url){
    this.serverurl = url;
  }

  private String processStitchCmd(String[] params) {
    if(serverSlice==null){
      try {
        serverSlice = SafeSlice.loadManifestFile(sliceName, pemLocation, keyLocation, controllerUrl);
      }catch (Exception e){
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
      if(safeEnabled) {
        if(!safeChecked) {
          if(serverSlice.getResourceByName("safe-server")!= null){
            setSafeServerIp(serverSlice.getComputeNode("safe-server").getManagementIP());
          }else {
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
      }else {
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
        Exec.sshExec("root", mip, "echo \"ip route 192.168.1.1/16 " + params[3].split("/")[0] +
          "\"" +
          " >>/etc/quagga/zebra.conf  ", sshkey);
        Exec.sshExec("root", mip, "/etc/init.d/quagga restart", sshkey);
        logger.info(logPrefix + "stitch completed.");
        return ip.split("/")[0];
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  private String processUnStitchCmd(String[] params) {
    if(serverSlice==null){
      try {
        serverSlice = SafeSlice.loadManifestFile(sliceName, pemLocation, keyLocation, controllerUrl);
      }catch (Exception e){
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
      if(safeEnabled) {
        if(!safeChecked) {
          if(serverSlice.getResourceByName("safe-server")!= null){
            setSafeServerIp(serverSlice.getComputeNode("safe-server").getManagementIP());
          }else {
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
      }else{
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

  public void undoStitch(String carrierName, String customerName, String netName, String nodeName) {
    logger.info(logPrefix + "ndllib TestDriver: START");

    //Main Example Code

    SafeSlice s1 = null;
    SafeSlice s2 = null;

    try {
      s1 = SafeSlice.loadManifestFile(carrierName, pemLocation, keyLocation, controllerUrl);
      s2 = SafeSlice.loadManifestFile(customerName, pemLocation, keyLocation, controllerUrl);
    } catch (ContextTransportException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (TransportException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

    Network net1 = (Network) s1.getResourceByName(netName);
    String net1_stitching_GUID = net1.getStitchingGUID();

    ComputeNode node0_s2 = (ComputeNode) s2.getResourceByName(nodeName);
    String node0_s2_stitching_GUID = node0_s2.getStitchingGUID();

    logger.debug("net1_stitching_GUID: " + net1_stitching_GUID);
    logger.debug("node0_s2_stitching_GUID: " + node0_s2_stitching_GUID);
    Long t1 = System.currentTimeMillis();

    try {
      //s1
      //sliceProxy.permitSliceStitch(carrierName, net1_stitching_GUID, "stitchSecret");
      //s2
      sliceProxy.undoSliceStitch(customerName, node0_s2_stitching_GUID, carrierName, net1_stitching_GUID);
    } catch (TransportException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    Long t2 = System.currentTimeMillis();
    logger.info(logPrefix + "Finished UnStitching, time elapsed: " + String.valueOf(t2 - t1) + "\n");
//    try{
//      java.io.BufferedReader stdin = new java.io.BufferedReader(new java.io.InputStreamReader(System.in));
//      String input = new String();
//      input = stdin.readLine();
//      Long t3=System.currentTimeMillis();
//      System.out.println(logPrefix + "Time after stitching: "+String.valueOf(t3-t2)+"\n");
//		}catch (java.io.IOException e) {
//				System.out.println(logPrefix + e);
//		}

  }

  private String getOVSScript(String cip) {
    String script = "apt-get update\n" + "apt-get -y install openvswitch-switch\n apt-get -y install iperf\n /etc/init.d/neuca stop\n";
    // +"ovs-vsctl add-br br0\n"
    // +"ifaces=$(ifconfig |grep 'eth'|grep -v 'eth0'| cut -c 1-8 | sort | uniq -u)\n"
    // //+"interfaces=$(ifconfig |grep 'eth'|grep -v 'eth0'|sed 's/[ \\t].*//;/^$/d');"
    // +"echo \"$ifaces\" >> ~/interfaces.txt\n"
    // +"while read -r line;do\n"
    // +" ifconfig $line 0\n"
    // +"  ovs-vsctl add-port br0 $line\n"
    // +"done <<<\"$ifaces\"\n"
    // +" ovs-vsctl set-controller br0 tcp:"+cip+":6633";
    return script;
  }

  private String getQuaggaScript() {
    return "#!/bin/bash\n"
        // +"mask2cdr()\n{\n"+"local x=${1##*255.}\n"
        // +" set -- 0^^^128^192^224^240^248^252^254^ $(( (${#1} - ${#x})*2 )) ${x%%.*}\n"
        // +" x=${1%%$3*}\n"
        // +"echo $(( $2 + (${#x}/4) ))\n"
        // +"}\n"
        + "ipmask()\n"
        + "{\n"
        + " echo $1/24\n}\n"
        + "apt-get update\n"
        + "apt-get install -y quagga\n"
        + "sed -i -- 's/zebra=no/zebra=yes/g' /etc/quagga/daemons\n"
        + "sed -i -- 's/ospfd=no/ospfd=yes/g' /etc/quagga/daemons\n"
        + "echo \"!zebra configuration file\" >/etc/quagga/zebra.conf\necho \"hostname LogRouter\">>/etc/quagga/zebra.conf\n"
        + "echo \"enable password zebra\">>/etc/quagga/zebra.conf\n"
        + "echo \"!ospfd configuration file\" >/etc/quagga/ospfd.conf\n echo \"hostname ospfd\">>/etc/quagga/ospfd.conf\n echo \"enable password zebra\">>/etc/quagga/ospfd.conf\n  echo \"router ospf\">>/etc/quagga/ospfd.conf\n"
        + "eth=$(ifconfig |grep 'inet addr:'|grep -v 'inet addr:10.' |grep -v '127.0.0.1' |cut -d: -f2|awk '{print $1}')\n"
        + "eth1=$(echo $eth|cut -f 1 -d \" \")\n"
        + "echo \"  router-id $eth1\">>/etc/quagga/ospfd.conf\n"
        + "prefix=$(ifconfig |grep 'inet addr:'|grep -v 'inet addr:10.' |grep -v '127.0.0.1' |cut -d: -f2,4 |awk '{print $1 $2}'| sed 's/Bcast:/\\ /g')\n"
        + "while read -r line;do\n"
        + "  echo \"  network\" $(ipmask $line) area 0 >>/etc/quagga/ospfd.conf\n"
        + "done <<<\"$prefix\"\n"
        + "echo \"log stdout\">>/etc/quagga/ospfd.conf\n"
        + "echo \"1\" > /proc/sys/net/ipv4/ip_forward\n"
        //+"/etc/init.d/quagga restart\napt-get -y install iperf\n"
        + "/etc/init.d/quagga stop\napt-get -y install iperf\n"
        ;
  }

  private boolean postSafeStitchRequest(String keyhash,String customerName,String ReservID,String slicename, String nodename){
    /** Post to remote safesets using apache httpclient */
    String[] othervalues=new String[4];
    othervalues[0]=customerName;
    othervalues[1]=ReservID;
    othervalues[2]=slicename;
    othervalues[3]=nodename;
    String message= SafeUtils.postSafeStatements(safeServer,"postStitchRequest",keyhash,othervalues);
    if(message.contains("fail")){
      return false;
    }
    else
      return true;
  }

}

