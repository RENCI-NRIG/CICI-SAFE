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
public class SdxClient {
  public SdxClient()throws RemoteException{}
	private static final String RequestResource = null;
	private static String controllerUrl;
  private static String SDNControllerIP;
	private static String sliceName;
	private static String pemLocation;
	private static String keyLocation;
  private static ISliceTransportAPIv1 sliceProxy;
  private static SliceAccessContext<SSHAccessToken> sctx;
  private static int curip=128;
  private static HashMap<String, Link> links=new HashMap<String, Link>();
  private static String customer_keyhash;
  private static String safeserver=null;
  private static String sshkey;
	
	public static void main(String [] args){
		//Example usage:   ./target/appassembler/bin/SafeSdxExample  ~/.ssl/geni-pruth1.pem ~/.ssl/geni-pruth1.pem "https://geni.renci.org:11443/orca/xmlrpc" pruth.1 stitch
		//Example usage:   ./target/appassembler/bin/SafeSdxExample  ~/.ssl/geni-pruth1.pem ~/.ssl/geni-pruth1.pem "https://geni.renci.org:11443/orca/xmlrpc" name fournodes
		System.out.println("ndllib TestDriver: START");
		pemLocation = args[0];
		keyLocation = args[1];
		controllerUrl = args[2]; //"https://geni.renci.org:11443/orca/xmlrpc";
		sliceName = args[3];
    sshkey=args[6];
    customer_keyhash=args[7];

		sliceProxy = SdxClient.getSliceProxy(pemLocation,keyLocation, controllerUrl);		

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

    if (args[4].equals("client")){
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
      System.out.println("client start");
      String message = "";
      String customerName=sliceName;
      System.setProperty("java.security.policy","~/project/exo-geni/SAFE_SDX/allow1.policy");
      String input = new String();  
		 try{
//					System.out.println(obj.sayHello()); 
       java.io.BufferedReader stdin = new java.io.BufferedReader(new java.io.InputStreamReader(System.in));  
       while(true){
         System.out.print("Enter Commands:stitch clientslicename, server slice anme, client resource name, server resource name\n Or advertise route: route dest  gateway sdx routername,\n$>");
         input = stdin.readLine();  
         String[] params=input.split(" ");
         System.out.print("continue?[y/n]\n$>"+input);
         input = stdin.readLine();  
         if(input.startsWith("y")){
           try{
             if(params[0].equals("stitch")){
               processStitchCmd(params);
             }else{
               ServiceAPI obj = (ServiceAPI) Naming.lookup( "//" + 
                   "localhost" + 
                   "/ServiceServer_"+params[3]);         //objectname in registry 
               obj.notifyPrefix(params[1],params[2],params[4],customer_keyhash);
             }
           }
           catch (Exception e){
             e.printStackTrace();
           }
         }
       }
     }
     catch (Exception e) 
     { 
       System.out.println("HelloClient exception: " + e.getMessage()); 
       e.printStackTrace(); 
     } 
    }
		System.out.println("XXXXXXXXXX Done XXXXXXXXXXXXXX");
	}

  private static void processStitchCmd(String[] params){
    try{
      ServiceAPI obj = (ServiceAPI) Naming.lookup( "//" + 
          "localhost" + 
          "/ServiceServer_"+params[2]);         //objectname in registry 
      Slice s2 = null;
      try {
        s2 = Slice.loadManifestFile(sliceProxy, params[1]);
      } catch (ContextTransportException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } catch (TransportException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      ComputeNode node0_s2 = (ComputeNode) s2.getResourceByName(params[3]);
      String node0_s2_stitching_GUID = node0_s2.getStitchingGUID();
      String secret="mysecret";
      System.out.println("node0_s2_stitching_GUID: " + node0_s2_stitching_GUID);
      try {
        //s1
        sliceProxy.permitSliceStitch(params[1],node0_s2_stitching_GUID, secret);
      } catch (TransportException e) {
        // TODO Auto-generated catch block
        System.out.println("Failed to permit stitch");
        e.printStackTrace();
        return;
      }
      //post stitch request to SAFE
      System.out.println("posting stitch request statements to SAFE Sets");
      postSafeStitchRequest(customer_keyhash,params[1],node0_s2_stitching_GUID,params[2],params[4]);
      String res=obj.stitchRequest(params[2],params[4],customer_keyhash,params[1],node0_s2_stitching_GUID,secret);
      System.out.println("Got Stitch Information From Server: "+res);
      if(res.equals("")){
        System.out.println("stitch request declined by server");
      } 
      else{
        String[] parts=res.split("_");
        String ip=parts[1];
        System.out.println("set IP address of the stitch interface to "+ip);
        sleep(10);
        Exec.sshExec("root",node0_s2.getManagementIP(),"ifconfig eth2 "+ip,sshkey);
      }
    }
    catch (Exception e){
      e.printStackTrace();
    }
  }

  private static boolean postSafeStitchRequest(String customer_keyhash,String customerName,String ReservID,String slicename, String nodename){
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

  private static void configOSPFForNewInterface(ComputeNode c, String newip){
    Exec.sshExec("root",c.getManagementIP(),"/bin/bash ~/configospfforif.sh "+newip,"~/.ssh/id_rsa");
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
	
	public static void undoStitch(String carrierName, String customerName, String netName, String nodeName){	
		System.out.println("ndllib TestDriver: START");
		
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
		
		System.out.println("net1_stitching_GUID: " + net1_stitching_GUID);
		System.out.println("node0_s2_stitching_GUID: " + node0_s2_stitching_GUID);
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
    System.out.println("Finished UnStitching, time elapsed: "+String.valueOf(t2-t1)+"\n");
//    try{
//      java.io.BufferedReader stdin = new java.io.BufferedReader(new java.io.InputStreamReader(System.in));  
//      String input = new String();  
//      input = stdin.readLine();  
//      Long t3=System.currentTimeMillis();
//      System.out.println("Time after stitching: "+String.valueOf(t3-t2)+"\n");
//		}catch (java.io.IOException e) {  
//				System.out.println(e);   
//		}  
		
	}

  private static String getOVSScript(String cip){
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

  private static  String getQuaggaScript(){
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
//			l.add("TAMU (College Station, TX, USA) XO Rack");
			l.add("RENCI (Chapel Hill, NC USA) XO Rack");
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

