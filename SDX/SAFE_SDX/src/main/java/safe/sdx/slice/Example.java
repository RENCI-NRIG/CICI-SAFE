/**
 * 
 */
package safe.sdx;


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

import java.rmi.RMISecurityManager;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
/**

 * @author geni-orca
 *
 */
public class Example {
  public Example()throws RemoteException{}
	private static final String RequestResource = null;
	private static String controllerUrl;
  private static String SDNControllerIP;
	private static String sliceName;
	private static String pemLocation;
	private static String keyLocation;
  private static ISliceTransportAPIv1 sliceProxy;
  private static SliceAccessContext<SSHAccessToken> sctx;
  private static int curip=128;
  private static String IPPrefix="192.168.";
  private static String mask="/24";
  private static HashMap<String, Link> links=new HashMap<String, Link>();
  private static String customer_keyhash;
  private static String safeserver;
  private static String sshkey;
  private static String riakip="152.3.145.36";

  private static  void computeIP(String prefix){
    String[] ip_mask=prefix.split("/");
    String[] ip_segs=ip_mask[0].split("\\.");
    IPPrefix=ip_segs[0]+"."+ip_segs[1]+".";
    curip=Integer.valueOf(ip_segs[2]);
  }
	
	public static void main(String [] args){
		//Example usage:   ./target/appassembler/bin/SafeSdxExample  ~/.ssl/geni-pruth1.pem ~/.ssl/geni-pruth1.pem "https://geni.renci.org:11443/orca/xmlrpc" pruth.1 stitch
		//Example usage:   ./target/appassembler/bin/SafeSdxExample  ~/.ssl/geni-pruth1.pem ~/.ssl/geni-pruth1.pem "https://geni.renci.org:11443/orca/xmlrpc" name fournodes
		System.out.println("ndllib TestDriver: START");
		pemLocation = args[0];
		keyLocation = args[1];
		controllerUrl = args[2]; //"https://geni.renci.org:11443/orca/xmlrpc";
		sliceName = args[3]; //"pruth.sdx.1";

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

    if(args[4].equals("server")){
      SDNControllerIP=args[6];
      if(args.length<9){
        System.out.print("Using default riak server at 152.3.145.36:8098");
      }else{
        riakip=args[8];
      }
      try{
        if(args[5].equals("true")){
          String carrierName=sliceName;
          System.setProperty("java.security.policy","~/project/exo-geni/ahabserver/allow.policy");
          Slice carrier=createCarrierSlice(carrierName,4,10,1000000,1);
          waitTillActive(carrier);
          sshkey=args[7];
          copyFile2Slice(carrier, "/home/yaoyj11/project/exo-geni/SAFE_SDX/src/main/resources/scripts/dpid.sh","~/dpid.sh",sshkey);
          copyFile2Slice(carrier, "/home/yaoyj11/project/exo-geni/SAFE_SDX/src/main/resources/scripts/ovsbridge.sh","~/ovsbridge.sh",sshkey);
          runCmdSlice(carrier,"/bin/bash ~/ovsbridge.sh "+SDNControllerIP+":6633",sshkey);
        }
      }catch (Exception e){
        e.printStackTrace();
      }
    }
    else if (args[4].equals("delete")){
      Slice s2 = null;
      try{
        s2=Slice.loadManifestFile(sliceProxy, args[3]);
        s2.delete();
      }catch (Exception e){
        e.printStackTrace();
      }

    }
    else if (args[4].equals("client")){
      System.out.println("client start");
      String message = "blank";
      String customerName=sliceName;
      try{
        if(args[5].equals("true")){
          try{
            computeIP(args[6]);
          }catch(Exception e){
            e.printStackTrace();
          }
          if(args.length<8){
            System.out.print("Using default riak server at 152.3.145.36:8098");
          }else{
            riakip=args[7];
            System.out.print("Using riak server at "+riakip);
          }
          Slice c1=createCustomerSlice(customerName,2,IPPrefix,curip,1000000,true);
          waitTillActive(c1);
          //copyFile2Slice(c1, "/home/yaoyj11/project/exo-geni/SAFE_SDX/src/main/resources/scripts/configospffornewif.sh","~/configospffornewif.sh","~/.ssh/id_rsa");
          //copyFile2Slice(c1, "/home/yaoyj11/project/exo-geni/SAFE_SDX/src/main/resources/scripts/configospffornewif.sh","~/configospffornewif.sh","~/.ssh/id_rsa");
          //runCmdSlice(c1,"/bin/bash ~/ospfautoconfig.sh","~/.ssh/id_rsa");
          return;
        }
      }catch (Exception e){
        e.printStackTrace();
      }
    }
		System.out.println("XXXXXXXXXX Done XXXXXXXXXXXXXX");
	}

  private static boolean postStitchRequest(String customer_keyhash,String customerName,String ReservID,String slicename, String nodename){
		/** Post to remote safesets using apache httpclient */
    String[] othervalues=new String[4];
    othervalues[0]=customerName;
    othervalues[1]=ReservID;
    othervalues[2]=slicename;
    othervalues[3]=nodename;
    String message=SafePost.postSafeStatements(safeserver,"postStitchRequest",customer_keyhash,othervalues);
    if(message.contains("fail")){
      return false;
    }
    else
      return true;
  }


  private static void copyFile2Slice(Slice s, String lfile, String rfile,String privkey){
		for(ComputeNode c : s.getComputeNodes()){
      String mip=c.getManagementIP();
      try{
        System.out.println("scp config file to "+mip);
        ScpTo.Scp(lfile,"root",mip,rfile,privkey);
        //Exec.sshExec("yaoyj11","152.3.136.145","/bin/bash "+rfile,privkey);

      }catch (Exception e){
        System.out.println("exception when copying config file");
      }
		}
  }

  private static void runCmdSlice(Slice s, String cmd, String privkey){
		for(ComputeNode c : s.getComputeNodes()){
      String mip=c.getManagementIP();
      try{
        System.out.println(mip+" run commands:"+cmd);
        //ScpTo.Scp(lfile,"root",mip,rfile,privkey);
        String res=Exec.sshExec("root",mip,cmd,privkey);
        while(res.startsWith("error")){
          sleep(5);
          res=Exec.sshExec("root",mip,cmd,privkey);
        }

      }catch (Exception e){
        System.out.println("exception when copying config file");
      }
		}
  }

  public static void getNetworkInfo(Slice s){
    //getLinks
    for(Network n :s.getLinks()){
      System.out.println(n.getLabel());
    }
    //getInterfaces
    for(Interface i: s.getInterfaces()){
      InterfaceNode2Net inode2net=(InterfaceNode2Net)i;
      System.out.println("MacAddr: "+inode2net.getMacAddress());

      System.out.println("GUID: "+i.getGUID());
    }
    for(ComputeNode node: s.getComputeNodes()){
      System.out.println(node.getName()+node.getManagementIP());
      for(Interface i: node.getInterfaces()){
        InterfaceNode2Net inode2net=(InterfaceNode2Net)i;
        System.out.println("MacAddr: "+inode2net.getMacAddress());
        System.out.println("GUID: "+i.getGUID());
      }
    }
  }
	
	public static Slice createCarrierSlice(String sliceName,int num,int start, long bw,int numstitches){//,String stitchsubnet="", String slicesubnet="")	
		System.out.println("ndllib TestDriver: START");
		
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
      node0.setDomain(domains.get(i));
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
    s.commit();
    return s;
	}
	
	public static  Slice createCustomerSlice(String sliceName, int num,String prefix, int start,long bw,boolean network){//=1, String subnet="")
		System.out.println("ndllib TestDriver: START");
		//Main Example Code
		
		Slice s = Slice.create(sliceProxy, sctx, sliceName);
		
		String nodeImageShortName="Ubuntu 14.04";
		String nodeImageURL ="http://geni-orca.renci.org/owl/9dfe179d-3736-41bf-8084-f0cd4a520c2f#Ubuntu+14.04";//http://geni-images.renci.org/images/standard/ubuntu/ub1304-ovs-opendaylight-v1.0.0.xml
		String nodeImageHash ="9394ca154aa35eb55e604503ae7943ddaecc6ca5";
		String nodeNodeType="XO Medium";
		String nodePostBootScript="apt-get update;apt-get -y install quagga\n"
      +"sed -i -- 's/zebra=no/zebra=yes/g' /etc/quagga/daemons\n"
      +"sed -i -- 's/ospfd=no/ospfd=yes/g' /etc/quagga/daemons\n"
      +"echo \"1\" > /proc/sys/net/ipv4/ip_forward\n"
      +"/etc/init.d/neuca stop\n";
		String nodeDomain=domains.get(0);
    ArrayList<ComputeNode> nodelist=new ArrayList<ComputeNode>();
    ArrayList<Network> netlist=new ArrayList<Network>();
    for(int i=0;i<num;i++){
      ComputeNode node0 = s.addComputeNode("CNode"+String.valueOf(i));
      node0.setImage(nodeImageURL,nodeImageHash,nodeImageShortName);
      node0.setNodeType(nodeNodeType);
      node0.setDomain(domains.get(0));
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
		s.commit();
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
    node0.setDomain(domains.get(0));
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
      +"docker exec -d safe /bin/bash -c  \"cd /root/safe;export SBT_HOME=/opt/sbt-0.13.12;export SCALA_HOME=/opt/scala-2.11.8;sed -i 's/152.3.145.36:8098/"+riakip+":8098/g' safe-server/src/main/resources/application.conf;./sdx.sh\"\n";
    return script;
  }

	public static Slice getSlice(ISliceTransportAPIv1 sliceProxy, String sliceName){
		Slice s = null;
		try {
			s = Slice.loadManifestFile(sliceProxy, sliceName);
		} catch (ContextTransportException e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
		} catch (TransportException e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
		}
		return s;
	}

	public static void sleep(int sec){
		try {
			Thread.sleep(sec*1000);                 //1000 milliseconds is one second.
		} catch(InterruptedException ex) {  
			Thread.currentThread().interrupt();
		}
	}


	public static ISliceTransportAPIv1 getSliceProxy(String pem, String key, String controllerUrl){

		ISliceTransportAPIv1 sliceProxy = null;
		try{
			//ExoGENI controller context
			ITransportProxyFactory ifac = new XMLRPCProxyFactory();
			System.out.println("Opening certificate " + pem + " and key " + key);
			TransportContext ctx = new PEMTransportContext("", pem, key);
			sliceProxy = ifac.getSliceProxy(ctx, new URL(controllerUrl));

		} catch  (Exception e){
			e.printStackTrace();
			System.err.println("Proxy factory test failed");
			assert(false);
		}

		return sliceProxy;
	}



	public static final ArrayList<String> domains;
	static {
		ArrayList<String> l = new ArrayList<String>();

		for (int i = 0; i < 100; i++){
//			l.add("PSC (Pittsburgh, TX, USA) XO Rack");
//			l.add("UAF (Fairbanks, AK, USA) XO Rack");
		
//			l.add("UH (Houston, TX USA) XO Rack");
			l.add("TAMU (College Station, TX, USA) XO Rack");
//			l.add("RENCI (Chapel Hill, NC USA) XO Rack");
//			
//			l.add("SL (Chicago, IL USA) XO Rack");
//			
//			
//			l.add("OSF (Oakland, CA USA) XO Rack");
//			
//		l.add("UMass (UMass Amherst, MA, USA) XO Rack");
			//l.add("WVN (UCS-B series rack in Morgantown, WV, USA)");
	//		l.add("UAF (Fairbanks, AK, USA) XO Rack");
//   l.add("UNF (Jacksonville, FL) XO Rack");
//		l.add("UFL (Gainesville, FL USA) XO Rack");
//			l.add("WSU (Detroit, MI, USA) XO Rack");
//			l.add("BBN/GPO (Boston, MA USA) XO Rack");
//			l.add("UvA (Amsterdam, The Netherlands) XO Rack");

		}
		domains = l;
	}

  public static void waitTillActive(Slice s){
		boolean sliceActive = false;
		while (true){		
			s.refresh();
			sliceActive = true;
			System.out.println("");
			System.out.println("Slice: " + s.getAllResources());
			for(ComputeNode c : s.getComputeNodes()){
				System.out.println("Resource: " + c.getName() + ", state: "  + c.getState());
				if(c.getState() != "Active") sliceActive = false;
			}
			for(Network l: s.getBroadcastLinks()){
				System.out.println("Resource: " + l.getName() + ", state: "  + l.getState());
				if(l.getState() != "Active") sliceActive = false;
			}
		 	
		 	if(sliceActive) break;
		 	sleep(10);
		}
		System.out.println("Done");
		for(ComputeNode n : s.getComputeNodes()){
			System.out.println("ComputeNode: " + n.getName() + ", Managment IP =  " + n.getManagementIP());
		}
  }
}

