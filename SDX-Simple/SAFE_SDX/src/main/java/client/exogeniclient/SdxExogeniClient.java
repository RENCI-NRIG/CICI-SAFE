/**
 * 
 */
package client.exogeniclient;

import org.apache.commons.cli.CommandLine;
import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.renci.ahab.libndl.Slice;
import org.renci.ahab.libndl.resources.request.ComputeNode;
import org.renci.ahab.libndl.resources.request.Interface;
import org.renci.ahab.libndl.resources.request.InterfaceNode2Net;
import org.renci.ahab.libndl.resources.request.Network;
import org.renci.ahab.libtransport.SSHAccessToken;
import org.renci.ahab.libtransport.SliceAccessContext;
import org.renci.ahab.libtransport.util.ContextTransportException;
import org.renci.ahab.libtransport.util.SSHAccessTokenFileFactory;
import org.renci.ahab.libtransport.util.TransportException;
import org.renci.ahab.libtransport.util.UtilTransportException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import sdx.core.SliceCommon;
import sdx.utils.Exec;
import sdx.utils.SafePost;

/**

 * @author geni-orca
 *
 */
public class SdxExogeniClient extends SliceCommon {
  final Logger logger = Logger.getLogger(Exec.class);
  private CommandLine cmd;

  public SdxExogeniClient(String[] args){
    //Example usage: ./target/appassembler/bin/SafeSdxClient -f alice.conf
    logger.debug("ndllib TestDriver: START");
    //pemLocation = args[0];
    //keyLocation = args[1];
    //controllerUrl = args[2]; //"https://geni.renci.org:11443/orca/xmlrpc";
    //sliceName = args[3];
    //sshkey=args[6];
    //keyhash=args[7];

    cmd=parseCmd(args);
    String configfilepath=cmd.getOptionValue("config");
    readConfig(configfilepath);
    sdxserver=conf.getString("config.sdxserver");

    sliceProxy = getSliceProxy(pemLocation,keyLocation, controllerUrl);
    //SSH context
    sctx = new SliceAccessContext<>();
    try {
      SSHAccessTokenFileFactory fac;
      fac = new SSHAccessTokenFileFactory(sshkey+".pub", false);
      SSHAccessToken t = fac.getPopulatedToken();
      sctx.addToken("root", "root", t);
      sctx.addToken("root", t);
    } catch (UtilTransportException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

    Slice s2 = null;
    try {
      s2 = Slice.loadManifestFile(sliceProxy, sliceName);
      ComputeNode safe=(ComputeNode)s2.getResourceByName("safe-server");
      safeserver=safe.getManagementIP()+":7777";
    } catch (ContextTransportException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (TransportException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    logger.debug("client start");
    if(cmd.hasOption('n')){
      safeauth=false;
    }
  }
  private String type;
  private String sdxserver;
  private boolean safeauth=true;
	
	public void run(String [] args){
    if(cmd.hasOption('e')){
      String command= cmd.getOptionValue('e');
      processCmd(command);
      return;
    }
     String input = new String();
		try{
//	 			logger.debug(obj.sayHello()); 
      BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
      while(true){
        System.out.print("Enter Commands:stitch client_resource_name  server_slice_name  server_resource_name\n Or advertise route: route dest gateway sdx_slice_name routername,\n$>");
        input = stdin.readLine();  
        System.out.print("continue?[y/n]\n$>"+input);
        input = stdin.readLine();  
        if(input.startsWith("y")){
          processCmd(input);
        }
      }
    }
    catch (Exception e) 
    { 
      logger.debug("HttpClient exception: " + e.getMessage()); 
      e.printStackTrace(); 
    } 
		logger.debug("XXXXXXXXXX Done XXXXXXXXXXXXXX");
	}

	public void processCmd(String command){
    try{
      String[] params=command.split(" ");
      if(params[0].equals("stitch")){
        processStitchCmd(params);
      }else if(params[0].equals("link")){
        processConnectionCmd(params);
      }
      else{
        JSONObject paramsobj=new JSONObject();
        paramsobj.put("dest",params[1]);
        paramsobj.put("gateway",params[2]);
        paramsobj.put("customer", keyhash);
        String res=SdxHttpClient.httpRequest(sdxserver+"sdx/notifyprefix",paramsobj);
        if(res.equals("")){
          logger.debug("Prefix not accepted (authorization failed)");
          System.out.println("Prefix not accepted (authorization failed)");
        }
        else{
          logger.debug(res);
          System.out.println(res);
        }
      }
    }
    catch (Exception e){
      e.printStackTrace();
    }

  }

  private void processConnectionCmd(String[] params){
    try{
      JSONObject jsonparams=new JSONObject();
      String site1=null,site2=null;
      for(String site:sitelist){
        if(site.contains(params[1])){
          site1=site;
        }
        if(site.contains(params[2])){
          site2=site;
        }
        if(site1!=null && site2!=null){
          break;
        }
      }
      if(site1==null || site2==null){
        System.out.println("Cannot find both sites, here is what I found: "+site1+", "+site2+";\n");
        logger.debug("Cannot find both sites, here is what I found: "+site1+", "+site2+";\n");
        return;
      }
      jsonparams.put("site1",site1);
      jsonparams.put("site2",site2);
      String res=SdxHttpClient.httpRequest(sdxserver+"sdx/connectionrequest",jsonparams);
      logger.debug("get connection result from server:\n"+ res);
      System.out.println(res);
    }catch (Exception e){
      e.printStackTrace();
    }
  }

  private void processPrefixCmd(String[] params){
    JSONObject paramsobj=new JSONObject();
    paramsobj.put("dest",params[1]);
    paramsobj.put("gateway",params[2]);
    paramsobj.put("customer", keyhash);
    String res=SdxHttpClient.httpRequest(sdxserver+"sdx/notifyprefix",paramsobj);
    if(res.equals("")){
      logger.debug("Prefix not accepted (authorization failed)");
      System.out.println("Prefix not accepted (authorization failed)");
    }
    else{
      logger.debug(res);
      System.out.println(res);
    }
  }

  private void processStitchCmd(String[] params){
    try{
      Slice s2 =getSlice();
      ComputeNode node0_s2 = (ComputeNode) s2.getResourceByName(params[1]);
      String node0_s2_stitching_GUID = node0_s2.getStitchingGUID();
      String secret="mysecret";
      logger.debug("node0_s2_stitching_GUID: " + node0_s2_stitching_GUID);
      try {
        //s1
        sliceProxy.permitSliceStitch(sliceName,node0_s2_stitching_GUID, secret);
      } catch (TransportException e) {
        // TODO Auto-generated catch block
        logger.debug("Failed to permit stitch");
        e.printStackTrace();
        return;
      }
      //post stitch request to SAFE
      logger.debug("posting stitch request statements to SAFE Sets");
      String sdxsite=node0_s2.getDomain();
      if(safeauth) {
        postSafeStitchRequest(keyhash, sliceName, node0_s2_stitching_GUID, params[2], sdxsite);
      }
      JSONObject jsonparams=new JSONObject();
      jsonparams.put("sdxslice",params[2]);
      jsonparams.put("sdxsite",sdxsite);
      jsonparams.put("ckeyhash",keyhash);
      jsonparams.put("cslice",sliceName);
      jsonparams.put("creservid",node0_s2_stitching_GUID);
      jsonparams.put("secret",secret);
      logger.debug("Sending stitch request to sdx server");
      JSONObject res=new JSONObject(SdxHttpClient.httpRequest(sdxserver+"sdx/stitchrequest",jsonparams));
      logger.debug("Got Stitch Information From Server:\n "+res.toString());
      if(!res.getBoolean("result")){
        logger.debug("stitch request failed");
        System.out.println("stitch request failed");
      }
      else{
        String ip=res.getString("ip");
        logger.debug("set IP address of the stitch interface to "+ip);
        System.out.println("set IP address of the stitch interface to "+ip);
        sleep(15);
        String mip= node0_s2.getManagementIP();
        String result=Exec.sshExec("root",mip,"ifconfig eth2 "+ip,sshkey);
        Exec.sshExec("root",mip,"echo \"ip route 192.168.1.1/16 "+res.getString("gateway").split("/")[0]+"\" >>/etc/quagga/zebra.conf  ",sshkey);
        Exec.sshExec("root",mip,"/etc/init.d/quagga restart",sshkey);
        ComputeNode node1 = (ComputeNode) s2.getResourceByName("CNode1");
        String IPPrefix=conf.getString("config.ipprefix").split("/")[0];
        mip= node1.getManagementIP();
        Exec.sshExec("root",mip,"echo \"ip route 192.168.1.1/16 "+IPPrefix+"\" >>/etc/quagga/zebra.conf  ",sshkey);
        Exec.sshExec("root",mip,"/etc/init.d/quagga restart",sshkey);
        System.out.println("stitch completed.");
      }
    }
    catch (Exception e){
      e.printStackTrace();
    }
  }

  private boolean postSafeStitchRequest(String keyhash,String customerName,String ReservID,String slicename, String nodename){
		/** Post to remote safesets using apache httpclient */
    String[] othervalues=new String[4];
    othervalues[0]=customerName;
    othervalues[1]=ReservID;
    othervalues[2]=slicename;
    othervalues[3]=nodename;
    String message=SafePost.postSafeStatements(safeserver,"postStitchRequest",keyhash,othervalues);
    if(message.contains("fail")){
      return false;
    }
    else
      return true;
  }

  private void configOSPFForNewInterface(ComputeNode c, String newip){
    Exec.sshExec("root",c.getManagementIP(),"/bin/bash ~/configospfforif.sh "+newip,"~/.ssh/id_rsa");
  }

  public void getNetworkInfo(Slice s){
    //getLinks
    for(Network n :s.getLinks()){
      logger.debug(n.getLabel());
    }
    //getInterfaces
    for(Interface i: s.getInterfaces()){
      InterfaceNode2Net inode2net=(InterfaceNode2Net)i;
      logger.debug("MacAddr: "+inode2net.getMacAddress());

      logger.debug("GUID: "+i.getGUID());
    }
    for(ComputeNode node: s.getComputeNodes()){
      logger.debug(node.getName()+node.getManagementIP());
      for(Interface i: node.getInterfaces()){
        InterfaceNode2Net inode2net=(InterfaceNode2Net)i;
        logger.debug("MacAddr: "+inode2net.getMacAddress());
        logger.debug("GUID: "+i.getGUID());
      }
    }
  }
	
	public void undoStitch(String carrierName, String customerName, String netName, String nodeName){
		logger.debug("ndllib TestDriver: START");
		
		//Main Example Code
		
		Slice s1 = null;
		Slice s2 = null;
		
		try {
			s1 = Slice.loadManifestFile(sliceProxy, carrierName);
			s2 = Slice.loadManifestFile(sliceProxy, customerName);
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
    logger.debug("Finished UnStitching, time elapsed: "+String.valueOf(t2-t1)+"\n");
//    try{
//      java.io.BufferedReader stdin = new java.io.BufferedReader(new java.io.InputStreamReader(System.in));  
//      String input = new String();  
//      input = stdin.readLine();  
//      Long t3=System.currentTimeMillis();
//      logger.debug("Time after stitching: "+String.valueOf(t3-t2)+"\n");
//		}catch (java.io.IOException e) {  
//				logger.debug(e);   
//		}  
		
	}

  private String getOVSScript(String cip){
		String script="apt-get update\n"+"apt-get -y install openvswitch-switch\n apt-get -y install iperf\n /etc/init.d/neuca stop\n";
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

  private  String getQuaggaScript(){
    return "#!/bin/bash\n"
     // +"mask2cdr()\n{\n"+"local x=${1##*255.}\n"
     // +" set -- 0^^^128^192^224^240^248^252^254^ $(( (${#1} - ${#x})*2 )) ${x%%.*}\n"
     // +" x=${1%%$3*}\n"
     // +"echo $(( $2 + (${#x}/4) ))\n"
     // +"}\n"
      +"ipmask()\n"
      +"{\n"
      +" echo $1/24\n}\n"
      +"apt-get update\n"
      +"apt-get install -y quagga\n"
      +"sed -i -- 's/zebra=no/zebra=yes/g' /etc/quagga/daemons\n"
      +"sed -i -- 's/ospfd=no/ospfd=yes/g' /etc/quagga/daemons\n"
      +"echo \"!zebra configuration file\" >/etc/quagga/zebra.conf\necho \"hostname Router\">>/etc/quagga/zebra.conf\n"
      +"echo \"enable password zebra\">>/etc/quagga/zebra.conf\n"
      +"echo \"!ospfd configuration file\" >/etc/quagga/ospfd.conf\n echo \"hostname ospfd\">>/etc/quagga/ospfd.conf\n echo \"enable password zebra\">>/etc/quagga/ospfd.conf\n  echo \"router ospf\">>/etc/quagga/ospfd.conf\n"
      +"eth=$(ifconfig |grep 'inet addr:'|grep -v 'inet addr:10.' |grep -v '127.0.0.1' |cut -d: -f2|awk '{print $1}')\n"
      +"eth1=$(echo $eth|cut -f 1 -d \" \")\n"
      +"echo \"  router-id $eth1\">>/etc/quagga/ospfd.conf\n"
      +"prefix=$(ifconfig |grep 'inet addr:'|grep -v 'inet addr:10.' |grep -v '127.0.0.1' |cut -d: -f2,4 |awk '{print $1 $2}'| sed 's/Bcast:/\\ /g')\n"
      +"while read -r line;do\n"
      +"  echo \"  network\" $(ipmask $line) area 0 >>/etc/quagga/ospfd.conf\n"
      +"done <<<\"$prefix\"\n"
      +"echo \"log stdout\">>/etc/quagga/ospfd.conf\n"
      +"echo \"1\" > /proc/sys/net/ipv4/ip_forward\n"
      //+"/etc/init.d/quagga restart\napt-get -y install iperf\n"
      +"/etc/init.d/quagga stop\napt-get -y install iperf\n"
      ;
  }

}

