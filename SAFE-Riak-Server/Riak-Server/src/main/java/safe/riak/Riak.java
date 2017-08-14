package safe.riak;
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
import org.renci.ahab.ndllib.transport.OrcaSMXMLRPCProxy;

import safe.utils.Exec;

import java.rmi.RMISecurityManager;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
/**

 * @author geni-orca
 *
 */
public class Riak extends SliceCommon{
	final static Logger logger = Logger.getLogger(Riak.class);
	
	public Riak()throws RemoteException{}
	private static int curip=128;
	private static String IPPrefix="192.168.";
	private static String mask="/24";
	//private static HashMap<String, Link> links=new HashMap<String, Link>();
	private static String riakip="152.3.145.36";
	private static String type;
	private static String site;

	private static  void computeIP(String prefix){
		System.out.println(prefix);
		String[] ip_mask=prefix.split("/");
		String[] ip_segs=ip_mask[0].split("\\.");
		IPPrefix=ip_segs[0]+"."+ip_segs[1]+".";
		curip=Integer.valueOf(ip_segs[2]);
	}

	public static void main(String [] args){
		System.out.println("Starting Riak Server");
		logger.info("this is a test message from pruth");
		
		//System.exit(0);
		
		CommandLine cmd=parseCmd(args);

		String configfilepath=cmd.getOptionValue("config");
		SliceConfig sdxconfig=readConfig(configfilepath);
		type=sdxconfig.type;
		if(cmd.hasOption('d')){
			type="delete";
		}

		sliceProxy = Riak.getSliceProxy(pemLocation,keyLocation, controllerUrl);		

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

		if (type.equals("riak")){
			site=sdxconfig.site;
			createRiakSlice(sliceName);


		}
	}

	public static  Slice createRiakSlice(String sliceName){
		Slice s = Slice.create(sliceProxy, sctx, sliceName);
		String dockerImageShortName="Ubuntu 14.04 Docker";
		String dockerImageURL ="http://geni-orca.renci.org/owl/5e2190c1-09f1-4c48-8ed6-dacae6b6b435#Ubuntu+14.0.4+Docker";//http://geni-images.renci.org/images/standard/ubuntu/ub1304-ovs-opendaylight-v1.0.0.xml
		String dockerImageHash ="b4ef61dbd993c72c5ac10b84650b33301bbf6829";
		String dockerNodeType="XO Large";
		
		ComputeNode node0 = s.addComputeNode("riak");
		
		node0.setImage(dockerImageURL,dockerImageHash,dockerImageShortName);
		node0.setNodeType(dockerNodeType);
		node0.setDomain(site);
		node0.setPostBootScript(getRiakScript());
		
		s.commit();
		
		waitTillActive(s);
		
		ComputeNode riak=(ComputeNode) s.getResourceByName("riak");
		String riakip=riak.getManagementIP();
		Exec.sshExec("root",riakip,"docker run -i -t  -d -p 2122:2122 -p 8098:8098 -p 8087:8087 -h riakserver --name riakserver yaoyj11/riakimg",sshkey);
		Exec.sshExec("root",riakip,"docker ps",sshkey);
		Exec.sshExec("root",riakip,"docker exec -i -t -d riakserver sudo riak start",sshkey);
		Exec.sshExec("root",riakip,"docker exec -i -t -d  riakserver sudo riak-admin bucket-type activate  safesets",sshkey);
		Exec.sshExec("root",riakip,"docker exec -i -t  -d riakserver sudo riak-admin bucket-type update safesets '{\"props\":{\"allow_mult\":false}}'",sshkey);
		Exec.sshExec("root",riakip,"docker exec -it -d riakserver sudo riak ping",sshkey);
		System.out.println("Started riak server at "+riakip);
		return s;
	}


	private static String getRiakScript(){
		String script="docker pull yaoyj11/riakimg\n";
		return script;
	}

}

