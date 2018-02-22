package safe.exogeni.client;
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

import safe.utils.Exec;
/**

 * @author geni-orca
 *
 */
public class Example extends SliceCommon{
  final static Logger logger = Logger.getLogger(Exec.class);	
	
	
	public Example(){}
	private static int curip=128;
	private static String IPPrefix="192.168.";
	private static String mask="/24";
	private static String riakip="152.3.145.36";
	private static String type;
	private static long bandwidth=100000000;

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
		logger.debug("SDX-Simple " + args[0]);

		CommandLine cmd=parseCmd(args);

		logger.debug("cmd " + cmd);
		
		String configfilepath=cmd.getOptionValue("config");
		
		logger.debug("configfilepath " + configfilepath);
    readConfig(configfilepath);
		
		type=conf.getString("config.type");
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

		if(type.equals("client")){
      IPPrefix=conf.getString("config.ipprefix");
      riakip=conf.getString("config.riakserver");
      if(conf.hasPath("config.bandwidth")){
      	bandwidth=conf.getLong("config.bandwdith");
			}
      computeIP(IPPrefix);
      logger.debug("client start");
      String customerName=sliceName;
      try{
        System.out.println("Using riak server at "+riakip);
				System.out.println("IP prefix "+IPPrefix);
        Slice c1=createCustomerSlice(customerName,2,IPPrefix,curip,1000000000,true);
				try {
					c1.commit();
				} catch (XMLRPCTransportException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
        waitTillActive(c1);
        //copyFile2Slice(c1, "/home/yaoyj11/project/exo-geni/SAFE_SDX/src/main/resources/scripts/configospffornewif.sh","~/configospffornewif.sh","~/.ssh/id_rsa");
        //copyFile2Slice(c1, "/home/yaoyj11/project/exo-geni/SAFE_SDX/src/main/resources/scripts/configospffornewif.sh","~/configospffornewif.sh","~/.ssh/id_rsa");
        //runCmdSlice(c1,"/bin/bash ~/ospfautoconfig.sh","~/.ssh/id_rsa");
        logger.debug("Slice active now");
          
			  String SAFEServerIP=((ComputeNode)c1.getResourceByName("safe-server")).getManagementIP();
			  System.out.println("SAFE Server IP: " + SAFEServerIP);
			
			  System.out.println("CNode0 IP: " + ((ComputeNode)c1.getResourceByName("CNode0")).getManagementIP());
			  System.out.println("CNode1 IP: " + ((ComputeNode)c1.getResourceByName("CNode1")).getManagementIP());
        return;
      }catch (Exception e){
        e.printStackTrace();
      }
		}
		else if (type.equals("delete")){
			Slice s2 = null;
			try{
				logger.debug("deleting slice "+sliceName);
				s2=Slice.loadManifestFile(sliceProxy, sliceName);
				s2.delete();
			}catch (Exception e){
				e.printStackTrace();
			}

		}
		logger.debug("XXXXXXXXXX Done XXXXXXXXXXXXXX");
	}



	public static  Slice createCustomerSlice(String sliceName, int num,String prefix, int start,long bw,boolean network){//=1, String subnet="")
		logger.debug("ndllib TestDriver: START");
		//Main Example Code

		Slice s = Slice.create(sliceProxy, sctx, sliceName);

		String nodeImageShortName="Ubuntu 14.04";
		String nodeImageURL ="http://geni-images.renci.org/images/standard/ubuntu/ub1404-v1.0.4.xml";//http://geni-images.renci.org/images/standard/ubuntu/ub1304-ovs-opendaylight-v1.0.0.xml
		String nodeImageHash ="9394ca154aa35eb55e604503ae7943ddaecc6ca5";
		String nodeNodeType="XO Medium";
		String nodePostBootScript="apt-get update;apt-get -y install quagga iperf\n"
				+"sed -i -- 's/zebra=no/zebra=yes/g' /etc/quagga/daemons\n"
				+"sed -i -- 's/ospfd=no/ospfd=yes/g' /etc/quagga/daemons\n"
				+"echo \"1\" > /proc/sys/net/ipv4/ip_forward\n"
				+"/etc/init.d/neuca stop\n";
		String nodeDomain=routerSite;
		ArrayList<ComputeNode> nodelist=new ArrayList<ComputeNode>();
		ArrayList<Network> netlist=new ArrayList<Network>();
		for(int i=0;i<num;i++){
			ComputeNode node0 = s.addComputeNode("CNode"+String.valueOf(i));
			node0.setImage(nodeImageURL,nodeImageHash,nodeImageShortName);
			node0.setNodeType(nodeNodeType);
			node0.setDomain(routerSite);
			node0.setPostBootScript(nodePostBootScript);
			nodelist.add(node0);
			if(network){
				if(i!=num-1){
					Network net2 = s.addBroadcastLink("clink"+String.valueOf(i),bw);
					InterfaceNode2Net ifaceNode1 = (InterfaceNode2Net) net2.stitch(node0);
					ifaceNode1.setIpAddress(prefix+String.valueOf(start+i)+".1");
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
		}
		//add safe server
		addSafeServer(s,riakip);
		return s;
	}


	private static void addSafeServer(Slice s, String rip){
		String dockerImageShortName="Ubuntu 14.04 Docker";
		String dockerImageURL ="http://geni-images.renci.org/images/standard/docker/ubuntu-14.0.4/ubuntu-14.0.4-docker.xml";//http://geni-images.renci.org/images/standard/ubuntu/ub1304-ovs-opendaylight-v1.0.0.xml
		String dockerImageHash ="b4ef61dbd993c72c5ac10b84650b33301bbf6829";
		String dockerNodeType="XO Medium";
		ComputeNode node0 = s.addComputeNode("safe-server");
		node0.setImage(dockerImageURL,dockerImageHash,dockerImageShortName);
		node0.setNodeType(dockerNodeType);
		node0.setDomain(serverSite);
		node0.setPostBootScript(getSafeScript(rip));
	}

	

	private static String getOVSScript(String cip){
		String script="apt-get update\n"+"apt-get -y install openvswitch-switch\n apt-get -y install iperf\n /etc/init.d/neuca stop\n";
		return script;
	}

	private static String getSafeScript(String riakip){
		String script="apt-get update\n"
				+"docker pull yaoyj11/safeserver\n"
				+"docker run -i -t -d -p 7777:7777 -h safe --name safe yaoyj11/safeserver\n"
				+"docker exec -d safe /bin/bash -c  \"cd /root/safe;export SBT_HOME=/opt/sbt-0.13.12;export SCALA_HOME=/opt/scala-2.11.8;sed -i 's/RIAKSERVER/"+riakip+"/g' safe-server/src/main/resources/application.conf;./sdx.sh\"\n";
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

