package safe.riak;
import org.apache.log4j.Logger;
import org.apache.commons.cli.*;
import org.renci.ahab.libndl.Slice;
import org.renci.ahab.libndl.resources.request.ComputeNode;
import org.renci.ahab.libtransport.SSHAccessToken;
import org.renci.ahab.libtransport.SliceAccessContext;
import org.renci.ahab.libtransport.util.SSHAccessTokenFileFactory;
import org.renci.ahab.libtransport.util.UtilTransportException;
import org.renci.ahab.libtransport.xmlrpc.XMLRPCTransportException;

import safe.utils.Exec;

import java.rmi.RemoteException;
/**

 * @author geni-orca
 *
 */
public class Riak extends SliceCommon{
	final static Logger logger = Logger.getLogger(Riak.class);
	
	public Riak()throws RemoteException{}
	private static int curip=128;
	private static String site;

	private static  void computeIP(String prefix){
		logger.debug(prefix);
		String[] ip_mask=prefix.split("/");
		String[] ip_segs=ip_mask[0].split("\\.");
		curip=Integer.valueOf(ip_segs[2]);
	}

	public static void main(String [] args){
		System.out.println("Starting Riak Server");
		
		//System.exit(0);
		
		CommandLine cmd=parseCmd(args);

		String configfilepath=cmd.getOptionValue("config");
		SliceConfig sdxconfig=readConfig(configfilepath);
		if(cmd.hasOption('d')){
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

		site=sdxconfig.site;
		createRiakSlice(sliceName);
		System.out.println("Done");
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
		
		
		try {
			s.commit();
		} catch (XMLRPCTransportException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
		waitTillActive(s);
		
		ComputeNode riak=(ComputeNode) s.getResourceByName("riak");
		String riakip=riak.getManagementIP();
		Exec.sshExec("root",riakip,"docker run -i -t  -d -p 2122:2122 -p 8098:8098 -p 8087:8087 -h riakserver --name riakserver yaoyj11/riakimg",sshkey);
		Exec.sshExec("root",riakip,"docker ps",sshkey);
		Exec.sshExec("root",riakip,"docker exec -i -t -d riakserver sudo riak start",sshkey);
		Exec.sshExec("root",riakip,"docker exec -i -t -d  riakserver sudo riak-admin bucket-type activate  safesets",sshkey);
		Exec.sshExec("root",riakip,"docker exec -i -t  -d riakserver sudo riak-admin bucket-type update safesets '{\"props\":{\"allow_mult\":false}}'",sshkey);
		Exec.sshExec("root",riakip,"docker exec -it -d riakserver sudo riak ping",sshkey);
		logger.debug("Started riak server at "+riakip);
		System.out.println("Riak Server IP: " + riakip);
		return s;
	}


	private static String getRiakScript(){
		String script="docker pull yaoyj11/riakimg\n";
		return script;
	}

}

