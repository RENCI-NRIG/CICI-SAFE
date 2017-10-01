package sdx.core;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Properties;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.SimpleLayout;
import org.apache.commons.cli.*;
import org.apache.commons.cli.DefaultParser;

import org.renci.ahab.libndl.LIBNDL;
import org.renci.ahab.libndl.Slice;
import org.renci.ahab.libndl.SliceGraph;
import org.renci.ahab.libndl.extras.PriorityNetwork;
import org.renci.ahab.libndl.resources.common.ModelResource;
import org.renci.ahab.libndl.resources.request.BroadcastNetwork;
import org.renci.ahab.libndl.resources.request.ComputeNode;
import org.renci.ahab.libndl.resources.request.Interface;
import org.renci.ahab.libndl.resources.request.InterfaceNode2Net;
import org.renci.ahab.libndl.resources.request.Network;
import org.renci.ahab.libndl.resources.request.Node;
import org.renci.ahab.libndl.resources.request.StitchPort;
import org.renci.ahab.libndl.resources.request.StorageNode;
import org.renci.ahab.libtransport.ISliceTransportAPIv1;
import org.renci.ahab.libtransport.ITransportProxyFactory;
import org.renci.ahab.libtransport.JKSTransportContext;
import org.renci.ahab.libtransport.PEMTransportContext;
import org.renci.ahab.libtransport.SSHAccessToken;
import org.renci.ahab.libtransport.SliceAccessContext;
import org.renci.ahab.libtransport.TransportContext;
import org.renci.ahab.libtransport.util.ContextTransportException;
import org.renci.ahab.libtransport.util.SSHAccessTokenFileFactory;
import org.renci.ahab.libtransport.util.TransportException;
import org.renci.ahab.libtransport.util.UtilTransportException;
import org.renci.ahab.libtransport.xmlrpc.XMLRPCProxyFactory;
import org.renci.ahab.libtransport.xmlrpc.XMLRPCTransportException;
import org.renci.ahab.ndllib.transport.OrcaSMXMLRPCProxy;

import sdx.utils.Exec;

import java.rmi.RMISecurityManager;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
/**

 * @author geni-orca
 *
 */
public class Example extends SliceCommon{
  final static Logger logger = Logger.getLogger(Exec.class);	
	
	public Example()throws RemoteException{}
	private static int curip=128;
	private static String IPPrefix="192.168.";
	private static String mask="/24";
	private static String riakip="152.3.145.36";
	//private static String type;

	private static  void computeIP(String prefix){
		logger.debug(prefix);
		String[] ip_mask=prefix.split("/");
		String[] ip_segs=ip_mask[0].split("\\.");
		IPPrefix=ip_segs[0]+"."+ip_segs[1]+".";
		curip=Integer.valueOf(ip_segs[2]);
	}

	public static void main(String [] args){
		
		//Example usage:   ./target/appassembler/bin/SafeSdxExample  ~/.ssl/geni-pruth1.pem ~/.ssl/geni-pruth1.pem "https://geni.renci.org:11443/orca/xmlrpc" pruth.1 stitch
		//Example usage:   ./target/appassembler/bin/SafeSdxExample  ~/.ssl/geni-pruth1.pem ~/.ssl/geni-pruth1.pem "https://geni.renci.org:11443/orca/xmlrpc" name fournodes
		System.out.println("SDX-Simple " + args[0]);

		CommandLine cmd=parseCmd(args);

		logger.debug("cmd " + cmd);
		
		String configfilepath=cmd.getOptionValue("config");
		
		readConfig(configfilepath);
		
		logger.debug("configfilepath " + configfilepath);
    //readConfig(configfilepath);
		
		//type=conf.getString("config.type");
		if(cmd.hasOption('d')){
			type="delete";
		}

		sliceProxy = Example.getSliceProxy(pemLocation,keyLocation, controllerUrl);		

		//SSH context
		sctx = new SliceAccessContext<>();
		try {
			SSHAccessTokenFileFactory fac;
			fac = new SSHAccessTokenFileFactory("~/.ssh/id_rsa.pub", false);
			SSHAccessToken t = fac.getPopulatedToken();			
			sctx.addToken("root", "root", t);
			sctx.addToken("root", t);
		} catch (UtilTransportException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		if(type.equals("server")){
			IPPrefix=conf.getString("config.ipprefix");
			riakip=conf.getString("config.riakserver");
			String scriptsdir=conf.getString("config.scriptsdir");
			computeIP(IPPrefix);
			try{
				String carrierName=sliceName;
				System.setProperty("java.security.policy","~/project/exo-geni/ahabserver/allow.policy");
				Slice carrier=createCarrierSlice(carrierName,4,10,1000000,1);
				carrier.refresh();
				waitTillActive(carrier);
				carrier.refresh();
				copyFile2Slice(carrier, scriptsdir+"dpid.sh","~/dpid.sh",sshkey);
				copyFile2Slice(carrier, scriptsdir+"ovsbridge.sh","~/ovsbridge.sh",sshkey);
        //Make sure that plexus container is running
				SDNControllerIP=((ComputeNode)carrier.getResourceByName("plexuscontroller")).getManagementIP();
        if(!checkPlexus(SDNControllerIP)){
          System.exit(-1);
        }
				System.out.println("Plexus Controler IP: " + SDNControllerIP);
				runCmdSlice(carrier,"/bin/bash ~/ovsbridge.sh "+SDNControllerIP+":6633",sshkey,"(c\\d+)",true,true);
				
				String SAFEServerIP=((ComputeNode)carrier.getResourceByName("safe-server")).getManagementIP();
        if(!checkSafeServer(SAFEServerIP)){
          System.exit(-1);
        }
				System.out.println("SAFE Server IP: " + SAFEServerIP);
				//}
			}catch (Exception e){
				e.printStackTrace();
			}
		}
		else if (type.equals("delete")){
			Slice s2 = null;
			try{
				System.out.println("deleting slice "+sliceName);
				s2=Slice.loadManifestFile(sliceProxy, sliceName);
				s2.delete();
			}catch (Exception e){
				e.printStackTrace();
			}

		}
		logger.debug("XXXXXXXXXX Done XXXXXXXXXXXXXX");
	}

  private static boolean checkSafeServer(String SDNControllerIP){
    String result=Exec.sshExec("root",SDNControllerIP,"docker ps",sshkey);
    if(result.contains("safe")){
      logger.debug("safe server has started");
    }
    else{
      logger.debug("Failed to start safe controller, exit");
      return false;
    }
    return true;
  }
  
  private static boolean checkPlexus(String SDNControllerIP){
    String result=Exec.sshExec("root",SDNControllerIP,"docker ps",sshkey);
    if(result.contains("safeserver")){
      logger.debug("plexus controller has started");
    }
    else{
      logger.debug("plexus controller hasn't started, restarting it");
      result=Exec.sshExec("root",SDNControllerIP,"docker images",sshkey);
      if(result.contains("yaoyj11/plexus")){
        logger.debug("found plexus image, starting plexus container");
        Exec.sshExec("root",SDNControllerIP,"docker run -i -t -d -p 8080:8080 -p 6633:6633 -p 3000:3000 -h plexus --name plexus yaoyj11/plexus",sshkey);
      }else{

        logger.debug("plexus image not found, downloading...");
        Exec.sshExec("root",SDNControllerIP,"docker pull yaoyj11/plexus",sshkey);
        Exec.sshExec("root",SDNControllerIP,"docker run -i -t -d -p 8080:8080 -p 6633:6633 -p 3000:3000 -h plexus --name plexus yaoyj11/plexus",sshkey);
      }
      result=Exec.sshExec("root",SDNControllerIP,"docker ps",sshkey);
      if(result.contains("plexus")){
        logger.debug("plexus controller has started");
      }
      else{
        logger.debug("Failed to start plexus controller, exit");
        return false;
      }
    }
    return true;
  }

	public static Slice createCarrierSlice(String sliceName,int num,int start, long bw,int numstitches){//,String stitchsubnet="", String slicesubnet="")	
		logger.debug("ndllib TestDriver: START");

		Slice s = Slice.create(sliceProxy, sctx, sliceName);

		String nodeImageShortName="Ubuntu 14.04";
		String nodeImageURL ="http://geni-orca.renci.org/owl/9dfe179d-3736-41bf-8084-f0cd4a520c2f#Ubuntu+14.04";//http://geni-images.renci.org/images/standard/ubuntu/ub1304-ovs-opendaylight-v1.0.0.xml
		String nodeImageHash ="9394ca154aa35eb55e604503ae7943ddaecc6ca5";
		String nodeNodeType="XO Medium";
		//String nodePostBootScript="apt-get update;apt-get -y  install quagga\n"
		//  +"sed -i -- 's/zebra=no/zebra=yes/g' /etc/quagga/daemons\n"
		//  +"sed -i -- 's/ospfd=no/ospfd=yes/g' /etc/quagga/daemons\n";
		String nodePostBootScript=getOVSScript(SDNControllerIP);
		ArrayList<ComputeNode> nodelist=new ArrayList<ComputeNode>();
		ArrayList<Network> netlist=new ArrayList<Network>();
		ArrayList<Network> stitchlist=new ArrayList<Network>();
		for(int i=0;i<num;i++){

			ComputeNode node0 = s.addComputeNode("c"+String.valueOf(i));
			node0.setImage(nodeImageURL,nodeImageHash,nodeImageShortName);
			node0.setNodeType(nodeNodeType);
			node0.setDomain(clientSites.get(i));
			node0.setPostBootScript(nodePostBootScript);
			nodelist.add(node0);
			//for(int j=0;j<numstitches;j++){
			//  Network net1 = s.addBroadcastLink("stitch"+String.valueOf(i)+ String.valueOf(j),bw);
			//  InterfaceNode2Net ifaceNode0 = (InterfaceNode2Net) net1.stitch(node0);
			//  ifaceNode0.setIpAddress("192.168."+String.valueOf(100+i*10+j)+".1");
			//  ifaceNode0.setNetmask("255.255.255.0");
			//  stitchlist.add(net1);
			//}
			if(i!=num-1){
				Network net2 = s.addBroadcastLink("clink"+String.valueOf(i),bw);
				InterfaceNode2Net ifaceNode1 = (InterfaceNode2Net) net2.stitch(node0);
				ifaceNode1.setIpAddress("192.168."+String.valueOf(start+i)+".1");
				ifaceNode1.setNetmask("255.255.255.0");
				netlist.add(net2);
			}
			if(i!=0){
				Network net=netlist.get(i-1);
				InterfaceNode2Net ifaceNode1 = (InterfaceNode2Net) net.stitch(node0);
				ifaceNode1.setIpAddress("192.168."+String.valueOf(start+i-1)+".2");
				ifaceNode1.setNetmask("255.255.255.0");
			}
		}
		addSafeServer(s,riakip);
		addPlexusController(s);
		try {
			s.commit();
		} catch (XMLRPCTransportException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return s;
	}



	private static void addSafeServer(Slice s, String rip){
		String dockerImageShortName="Ubuntu 14.04 Docker";
		String dockerImageURL ="http://geni-orca.renci.org/owl/5e2190c1-09f1-4c48-8ed6-dacae6b6b435#Ubuntu+14.0.4+Docker";//http://geni-images.renci.org/images/standard/ubuntu/ub1304-ovs-opendaylight-v1.0.0.xml
		String dockerImageHash ="b4ef61dbd993c72c5ac10b84650b33301bbf6829";
		String dockerNodeType="XO Large";
		ComputeNode node0 = s.addComputeNode("safe-server");
		node0.setImage(dockerImageURL,dockerImageHash,dockerImageShortName);
		node0.setNodeType(dockerNodeType);
		node0.setDomain(serverSite);
		node0.setPostBootScript(getSafeScript(rip));
	}

	private static void addPlexusController(Slice s){
		String dockerImageShortName="Ubuntu 14.04 Docker";
		String dockerImageURL ="http://geni-orca.renci.org/owl/5e2190c1-09f1-4c48-8ed6-dacae6b6b435#Ubuntu+14.0.4+Docker";//http://geni-images.renci.org/images/standard/ubuntu/ub1304-ovs-opendaylight-v1.0.0.xml
		String dockerImageHash ="b4ef61dbd993c72c5ac10b84650b33301bbf6829";
		String dockerNodeType="XO Large";
		ComputeNode node0 = s.addComputeNode("plexuscontroller");
		node0.setImage(dockerImageURL,dockerImageHash,dockerImageShortName);
		node0.setNodeType(dockerNodeType);
		node0.setDomain(controllerSite);
		node0.setPostBootScript(getPlexusScript());
	}

	private static String getOVSScript(String cip){
		String script="apt-get update\n"+"apt-get -y install openvswitch-switch\n apt-get -y install iperf\n /etc/init.d/neuca stop\n";
		return script;
	}

	private static String getSafeScript(String riakip){
		String script="apt-get update\n"
				+"docker pull yaoyj11/safeserver\n"
				+"docker run -i -t -d -p 7777:7777 -h safe --name safe yaoyj11/safeserver\n"
				+"docker exec -d safe /bin/bash -c  \"cd /root/safe;export SBT_HOME=/opt/sbt-0.13.12;export SCALA_HOME=/opt/scala-2.11.8;sed -i 's/128.194.6.136:8098/"+riakip+":8098/g' safe-server/src/main/resources/application.conf;./sdx.sh\"\n";
		return script;
	}

	private static String getRiakScript(){
		String script="docker pull yaoyj11/riakimg\n";
		return script;
	}

	private static String getPlexusScript(){
		String script="apt-get update\n"
				+"docker pull yaoyj11/plexus\n"
				+"docker run -i -t -d -p 8080:8080 -p 6633:6633 -p 3000:3000 -h plexus --name plexus yaoyj11/plexus\n";
		//+"docker exec -d plexus /bin/bash -c  \"cd /root/;./sdx.sh\"\n";
		return script;
	}
}

