package exoplex.client.stitchport;

import exoplex.common.slice.SiteBase;
import exoplex.common.slice.SliceCommon;
import exoplex.common.utils.Exec;
import exoplex.common.utils.HttpUtil;
import exoplex.common.utils.SafeUtils;
import exoplex.common.utils.ServerOptions;
import org.apache.commons.cli.CommandLine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.renci.ahab.libndl.Slice;
import org.renci.ahab.libndl.resources.request.ComputeNode;
import org.renci.ahab.libndl.resources.request.Interface;
import org.renci.ahab.libndl.resources.request.InterfaceNode2Net;
import org.renci.ahab.libndl.resources.request.Network;

/**
 * @author geni-orca
 */
public class SdxStitchPortClient extends SliceCommon {
  final Logger logger = LogManager.getLogger(SdxStitchPortClient.class);
  CommandLine cmd;
  private String type;

  public SdxStitchPortClient(String[] args) {
    cmd = ServerOptions.parseCmd(args);
    String configFilePath = cmd.getOptionValue("config");
    readConfig(configFilePath);
    System.out.println("Client start");
  }

  public static void main(String[] args) {
    SdxStitchPortClient client = new SdxStitchPortClient(args);
    client.run();
  }

  public void run() {
    //Example usage: ./target/appassembler/bin/SafeSdxClient -f alice.conf
    System.out.println("ndllib TestDriver: START");
    //pemLocation = args[0];
    //keyLocation = args[1];
    //controllerUrl = args[2]; //"https://geni.renci.org:11443/orca/xmlrpc";
    //sliceName = args[3];
    //sshkey=args[6];
    //keyhash=args[7];

    if (cmd.hasOption('e')) {
      String command = cmd.getOptionValue('e');
      processCmd(command);
      return;
    }
    String input = new String();
    try {
      java.io.BufferedReader stdin = new java.io.BufferedReader(new java.io.InputStreamReader(System.in));
      while (true) {
        System.out.print("Enter Commands:stitch stitchport vlan gateway ip sdx_site sdx_node \n" +
          " Or advertise route: route dest gateway sdx_slice_name routername,\n$>");
        input = stdin.readLine();
        System.out.print("continue?[y/n]\n$>" + input);

        if (stdin.readLine().startsWith("y")) {
          processCmd(input);
        }
      }
    } catch (Exception e) {
      System.out.println("HttpClient exception: " + e.getMessage());
      e.printStackTrace();
    }
    System.out.println("XXXXXXXXXX Done XXXXXXXXXXXXXX");
  }

  public void processCmd(String command) {
    try {
      logger.debug(command);
      String[] params = command.split(" ");
      if (params[0].equals("stitch")) {
        logger.debug(params.length);
        processStitchCmd(params);
      } else {
        processPrefixCmd(params);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void processPrefixCmd(String[] params){
    System.out.print(params.length);
    JSONObject paramsobj = new JSONObject();
    paramsobj.put("dest", params[1]);
    paramsobj.put("gateway", params[2]);
    setSafeServerIp(conf.getString("config.safeserver"));
    safeKeyHash = SafeUtils.getPrincipalId(safeServer, safeKeyFile);
    paramsobj.put("customer", safeKeyHash);
    String res = HttpUtil.postJSON(serverurl + "sdx/notifyprefix", paramsobj);
    if (res.equals("")) {
      logger.debug("Prefix notifcation failed");
      System.out.println("Prefix notifcation failed");
    } else {
      logger.debug(res);
      System.out.println(res);
    }
  }

  private void processStitchCmd(String[] params) {
    try {
      //post stitch request to SAFE
      logger.debug("posting stitch request statements to SAFE Sets");
      JSONObject jsonparams = new JSONObject();
      jsonparams.put("stitchport", params[1]);
      jsonparams.put("vlan", params[2]);
      jsonparams.put("gateway", params[3]);
      jsonparams.put("ip", params[4]);
      jsonparams.put("sdxsite", SiteBase.get(params[5]));
      try {
        jsonparams.put("sdxnode", params[6]);
      }catch (Exception e){
        jsonparams.put("sdxnode",(String)null);
      }
      if(safeEnabled){
        setSafeServerIp(conf.getString("config.safeserver"));
        safeKeyHash = SafeUtils.getPrincipalId(safeServer, safeKeyFile);
        jsonparams.put("ckeyhash", safeKeyHash);
        postSafeStitchRequest(safeKeyHash,jsonparams.getString("stitchport"),jsonparams.getString("vlan"));
      }
      logger.debug("posted stitch request, requesting to Sdx server");
      String res = HttpUtil.postJSON(serverurl + "sdx/stitchchameleon", jsonparams);
      logger.debug(res);
      System.out.println(res);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void configOSPFForNewInterface(ComputeNode c, String newip) {
    Exec.sshExec("root", c.getManagementIP(), "/bin/bash ~/configospfforif.sh " + newip, "~/.ssh/id_rsa");
  }

  public void getNetworkInfo(Slice s) {
    //getLinks
    for (Network n : s.getLinks()) {
      logger.debug(n.getLabel());
    }
    //getInterfaces
    for (Interface i : s.getInterfaces()) {
      InterfaceNode2Net inode2net = (InterfaceNode2Net) i;
      logger.debug("MacAddr: " + inode2net.getMacAddress());

      logger.debug("GUID: " + i.getGUID());
    }
    for (ComputeNode node : s.getComputeNodes()) {
      logger.debug(node.getName() + node.getManagementIP());
      for (Interface i : node.getInterfaces()) {
        InterfaceNode2Net inode2net = (InterfaceNode2Net) i;
        logger.debug("MacAddr: " + inode2net.getMacAddress());
        logger.debug("GUID: " + i.getGUID());
      }
    }
  }

  private boolean postSafeStitchRequest(String keyhash, String stitchport, String vlan){
    /** Post to remote safesets using apache httpclient */
    String[] othervalues=new String[5];
    othervalues[0]=stitchport;
    othervalues[1]=vlan;
    String message= SafeUtils.postSafeStatements(safeServer,"postChameleonStitchRequest",keyhash,othervalues);
    if(message.contains("fail")){
      return false;
    }
    else
      return true;
  }

  public void undoStitch(String carrierName, String customerName, String netName, String nodeName) {
    System.out.println("Undo stich in chameleon Client not implemented");
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

}

