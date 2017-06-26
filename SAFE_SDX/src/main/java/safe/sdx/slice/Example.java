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
  private static String riakip="153.3.145.36";

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

	public static void stitch(String carrierName, String RID, String customerName, String nodeName,String secret,String newip, ServiceAPI obj,String routerName){	
		System.out.println("ndllib TestDriver: START");
		
		//Main Example Code
		
		Slice s2 = null;
		
		try {
			s2 = Slice.loadManifestFile(sliceProxy, customerName);
		} catch (ContextTransportException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (TransportException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		ComputeNode node0_s2 = (ComputeNode) s2.getResourceByName(nodeName);
		String node0_s2_stitching_GUID = node0_s2.getStitchingGUID();
		
		System.out.println("node0_s2_stitching_GUID: " + node0_s2_stitching_GUID);
    Long t1 = System.currentTimeMillis();
			
		try {
			//s2
			Properties p = new Properties();
			p.setProperty("ip", newip);
			sliceProxy.performSliceStitch(customerName, node0_s2_stitching_GUID, carrierName, RID, secret, p);
		} catch (TransportException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    Long t2 = System.currentTimeMillis();
    System.out.println("finished Stitching, set ip address of the new interface to "+newip+"  time elapsed: "+String.valueOf(t2-t1)+"\n");

    //Run commands to configure ospf on new interface for cusotmer node
    //Note: we need to be able to detect the number of the new interfce and configure ip address for this interface.
    sleep(10);
    Exec.sshExec("root",node0_s2.getManagementIP(),"ifconfig eth1  "+newip,"~/.ssh/id_rsa");
    Exec.sshExec("root",node0_s2.getManagementIP(),"ifconfig -a","~/.ssh/id_rsa");
    System.out.println("configured ip for the new interface. Now advertise the route");
    //String ip=newip.replace("/24","");
    //try{
    //  obj.advertiseRoute(newip,ip,routerName);
    //}catch (Exception e){
    //   System.out.println("HelloClient exception: " + e.getMessage()); 
    //   e.printStackTrace(); 
    //}
	}

  public static void dumbBell(String sliceName){
		System.out.println("XXXXXXXXXX Starting first slice XXXXXXXXXXXXXX");
    String carriername=sliceName+"_Carrier";
    String customername=sliceName+"_C";
    ArrayList<Slice> allslices=new ArrayList<Slice>();
    int numclient=4;
		Slice carrier=createCarrierSlice(carriername,2,1,100000000l,numclient);
    waitTillActive(carrier);
    allslices.add(carrier);
    for(int j=0;j<numclient;j++){
      sleep(60);
      System.out.println("XXXXXXXXXX Starting client slice XXXXXXXXXXXXXX"+String.valueOf(j));
      Slice c1=CustomerSlice(customername+String.valueOf(j),2,"192.168.",10+10*j);
      waitTillActive(c1);
      allslices.add(c1);
    }
    for( Slice s:allslices){
      copyFile2Slice(s, "/home/yaoyj11/project/exo-geni/SAFE_SDX/src/main/resources/scripts/ospfautoconfig.sh","~/ospfautoconfig.sh","~/.ssh/id_rsa");
      copyFile2Slice(s, "/home/yaoyj11/project/exo-geni/SAFE_SDX/src/main/resources/scripts/configospffornewif.sh","~/configospffornewif.sh","~/.ssh/id_rsa");
      runCmdSlice(s,"apt-get install -y iperf;/bin/bash ~/ospfautoconfig.sh","~/.ssh/id_rsa");
    }

		System.out.println("XXXXXXXXXX Stitching slices XXXXXXXXXXXXXX");
		//stitchSlices(carriername,customername+"2","stitch1","CNode1","192.168.101.2",24);
    for(int j=0;j<numclient;j++){
      for(int k=0;k<2;k++){
        stitchSlices(carriername,customername+ String.valueOf(j),"stitch"+ String.valueOf(k)+ String.valueOf(j),"CNode"+ String.valueOf(k),"192.168."+ String.valueOf(100+10*k+j)+"."+ String.valueOf(2),24);
      }
    }
  }


  public static void networkStitch(String sliceName){
		System.out.println("XXXXXXXXXX Starting first slice XXXXXXXXXXXXXX");
    String carriername=sliceName+"_Carrier";
    String customername=sliceName+"_C";
		System.out.println("XXXXXXXXXX Starting second slice XXXXXXXXXXXXXX");
		Slice c1=createCustomerSlice(customername+"1",2,"192.168.",30,10000000000l,true);
    waitTillActive(c1);
    copyFile2Slice(c1, "/home/yaoyj11/project/exo-geni/SAFE_SDX/src/main/resources/scripts/ospfautoconfig.sh","~/ospfautoconfig.sh","~/.ssh/id_rsa");
    copyFile2Slice(c1, "/home/yaoyj11/project/exo-geni/SAFE_SDX/src/main/resources/scripts/configospffornewif.sh","~/configospffornewif.sh","~/.ssh/id_rsa");
    runCmdSlice(c1,"/bin/bash ~/ospfautoconfig.sh","~/.ssh/id_rsa");
		System.out.println("XXXXXXXXXX Stitching slices XXXXXXXXXXXXXX");
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

  private static void configOSPFForNewInterface(ComputeNode c, String newip){
    Exec.sshExec("root",c.getManagementIP(),"/bin/bash ~/configospfforif.sh "+newip,"~/.ssh/id_rsa");
  }


  private static void runOSPFConfigScript(ComputeNode c){

  }
  public static void createPriorityNetwork(String sliceName){
		System.out.println("ndllib TestDriver: START");
		//Main Example Code
		
		Slice s = Slice.create(sliceProxy, sctx, sliceName);
		
		String nodeImageShortName="Ubuntu 14.04";
		String nodeImageURL ="http://geni-orca.renci.org/owl/9dfe179d-3736-41bf-8084-f0cd4a520c2f#Ubuntu+14.04";//http://geni-images.renci.org/images/standard/ubuntu/ub1304-ovs-opendaylight-v1.0.0.xml
		String nodeImageHash ="9394ca154aa35eb55e604503ae7943ddaecc6ca5";
		String nodeNodeType="XO Medium";
		String nodePostBootScript="apt-get install iperf";
//String nodePostBootScript="apt-get update;apt-get -y install quagga;apt-get -y install openvswitch-switch; /etc/init.d/neuca stop";
		String nodeDomain=domains.get(0);

		String controllerDomain=domains.get(0);
	    		
	//	s.commit();
		boolean sliceActive = false;
		
    PriorityNetwork net=PriorityNetwork.create(s,sliceName,controllerDomain,100000l);
    for(int i=0;i<7;i++){
      String domain="";
      if(i==0 ||i==1||i==3||i==4)
        domain=domains.get(0);
      else
        domain=domains.get(1);
        
      net.bind("site"+String.valueOf(i),domain);
      if(i==3||i==4||i==5||i==6){
        ComputeNode node = s.addComputeNode("site"+String.valueOf(i)+"_0");
        node.setImage(nodeImageURL,nodeImageHash,nodeImageShortName);
        node.setNodeType(nodeNodeType);
        node.setDomain(domain);
        node.setPostBootScript(nodePostBootScript);
        net.addNode(node,"site"+String.valueOf(i),"192.168.10."+String.valueOf(i+1),"255.255.255.0");
      }
    }
    s.commit();
    waitTillActive(s);
		System.out.println("Done\n start setting priorities, default=1");
    while(true){
			try{  
              System.out.println("Done\n start setting priorities, default=1");
							java.io.BufferedReader stdin = new java.io.BufferedReader(new java.io.InputStreamReader(System.in));  
							String input = new String();  
							input = stdin.readLine();  
							if(input.equals(""))
                break;
              String[] ss=input.split(" ");
              net.QoS_setPriority("site"+ss[0],"site"+ss[1],Integer.valueOf(ss[2]));
              net.QoS_commit();
					}catch (java.io.IOException e) {  
							System.out.println(e);   
					}  

    }
  }


  public static void createSixNodes(){
		System.out.println("ndllib TestDriver: START six nodes");
		
		//Main Example Code
		
		Slice s = Slice.create(sliceProxy, sctx, sliceName);
		
		String nodeImageShortName="Ubuntu 14.04";
		String nodeImageURL ="http://geni-orca.renci.org/owl/9dfe179d-3736-41bf-8084-f0cd4a520c2f#Ubuntu+14.04";//http://geni-images.renci.org/images/standard/ubuntu/ub1304-ovs-opendaylight-v1.0.0.xml
		String nodeImageHash ="9394ca154aa35eb55e604503ae7943ddaecc6ca5";
		String nodeNodeType="XO Medium";
		//String nodePostBootScript="apt-get update;apt-get -y install quagga;apt-get -y install openvswitch-switch; /etc/init.d/neuca stop";
    String quaggascript=getQuaggaScript();
    System.out.println(quaggascript);


    ComputeNode []switches=new ComputeNode[2];
    //addSwitches
		
    for(int i=0;i<switches.length;i++){
      switches[i] = s.addComputeNode("switch"+String.valueOf(i));
      switches[i].setImage(nodeImageURL,nodeImageHash,nodeImageShortName);
      switches[i].setNodeType(nodeNodeType);
      switches[i].setDomain(domains.get(i));
      switches[i].setPostBootScript(quaggascript);
    }
	    Network net1 = s.addBroadcastLink("vlan0",5000000000l);
    for(int i=0;i<switches.length;i++){
	    InterfaceNode2Net ifaceNode0 = (InterfaceNode2Net) net1.stitch(switches[i]);
	    ifaceNode0.setIpAddress("192.168.100."+String.valueOf(i+1));
	    ifaceNode0.setNetmask("255.255.255.0");

        Network net3 = s.addBroadcastLink("vlan"+String.valueOf(i+1),5000000000l);
	    InterfaceNode2Net iface2 = (InterfaceNode2Net) net3.stitch(switches[i]);
	    iface2.setIpAddress("192.168."+String.valueOf(i+1)+".1");
	    iface2.setNetmask("255.255.255.0");
        for(int  j=0;j<4;j++){
          ComputeNode node = s.addComputeNode("Node_"+String.valueOf(i)+"_"+String.valueOf(j));
          node.setImage(nodeImageURL,nodeImageHash,nodeImageShortName);
          node.setNodeType(nodeNodeType);
          node.setDomain(domains.get(i));
          node.setPostBootScript(quaggascript);
          InterfaceNode2Net ifaceNode2 = (InterfaceNode2Net) net3.stitch(node);
          ifaceNode2.setIpAddress("192.168."+String.valueOf(i+1)+"."+String.valueOf(j+2));
          ifaceNode2.setNetmask("255.255.255.0");
        }
    }
		s.commit();
		waitTillActive(s);
  }
	
  public static void createFourNodes(){
		System.out.println("ndllib TestDriver: START");
		//Main Example Code
		
		Slice s = Slice.create(sliceProxy, sctx, sliceName);
		
		String nodeImageShortName="Ubuntu 14.04";
		String nodeImageURL ="http://geni-orca.renci.org/owl/9dfe179d-3736-41bf-8084-f0cd4a520c2f#Ubuntu+14.04";//http://geni-images.renci.org/images/standard/ubuntu/ub1304-ovs-opendaylight-v1.0.0.xml
		String nodeImageHash ="9394ca154aa35eb55e604503ae7943ddaecc6ca5";
		String nodeNodeType="XO Medium";
    String SDNControllerScript="apt-get update;apt-get install ryu-bin";
    String cip=SDNControllerIP;
    
		//String nodePostBootScript0="apt-get update;"+"apt-get -y install quagga;apt-get -y install openvswitch-switch; /etc/init.d/neuca stop;"
    //  +" ifconfig eth1 0; ifconfig eth2 0; ovs-vsctl add-br br0; ovs-vsctl add-port br0 eth1;ovs-vsctl add-port br0 eth2;ovs-vsctl set-controller br0 tcp:"+cip+":6633";
		//String nodePostBootScript1="apt-get update;apt-get -y install quagga;apt-get -y install openvswitch-switch; /etc/init.d/neuca stop;apt-get install iperf";
		//String nodePostBootScript2="apt-get update;"+"apt-get -y install quagga;apt-get -y install openvswitch-switch; /etc/init.d/neuca stop;"
    //  +" ifconfig eth1 0;  ovs-vsctl add-br br0; ovs-vsctl set-controller br0 tcp:"+cip+":6633";
		String nodeDomain=domains.get(2);
    int L=4;
    ComputeNode []nodes=new ComputeNode[L];
    for(int i=0;i<L;i++){
      nodes[i] = s.addComputeNode("node"+String.valueOf(i));
      nodes[i].setImage(nodeImageURL,nodeImageHash,nodeImageShortName);
      nodes[i].setNodeType(nodeNodeType);
      if(i<L/2)
        nodes[i].setDomain(domains.get(0));
      else
        nodes[i].setDomain(domains.get(1));

      if(i<L/2-1){
        nodes[i].setPostBootScript(getOVSScript(SDNControllerIP));
      }
      else if(i>L/2){
        nodes[i].setPostBootScript(getOVSScript(SDNControllerIP));
      }
      else{
        nodes[i].setPostBootScript(getOVSScript(SDNControllerIP));
      }
    }
	    Network net1 = s.addBroadcastLink("link1",500000l);
      String netmask="255.255.255.0";
	    InterfaceNode2Net ifaceNode0 = (InterfaceNode2Net) net1.stitch(nodes[L/2-1]);
	    ifaceNode0.setIpAddress("192.168.2.1");
	    ifaceNode0.setNetmask(netmask);
	    InterfaceNode2Net ifaceNode1 = (InterfaceNode2Net) net1.stitch(nodes[L/2]);
	    ifaceNode1.setIpAddress("192.168.2.2");
	    ifaceNode1.setNetmask(netmask);
	    Network net2 = s.addBroadcastLink("link0",500000l);
	    InterfaceNode2Net ifaceNode3 = (InterfaceNode2Net) net2.stitch(nodes[L/2-1]);
	    ifaceNode3.setIpAddress("192.168.1.1");
	    ifaceNode3.setNetmask(netmask);
      for(int i=0;i<L/2-1;i++){
        InterfaceNode2Net ifaceNode2 = (InterfaceNode2Net) net2.stitch(nodes[i]);
        ifaceNode2.setIpAddress("192.168.1."+String.valueOf(i+2));
        ifaceNode2.setNetmask(netmask);
      }
	    Network net3 = s.addBroadcastLink("link2",500000l);
	    InterfaceNode2Net ifaceNode4 = (InterfaceNode2Net) net3.stitch(nodes[L/2]);
	    ifaceNode4.setIpAddress("192.168.3.1");
	    ifaceNode4.setNetmask(netmask);
      for(int i=L/2+1;i<L;i++){
        InterfaceNode2Net ifaceNode2 = (InterfaceNode2Net) net3.stitch(nodes[i]);
        ifaceNode2.setIpAddress("192.168.3."+String.valueOf(i-L/2+1));
        ifaceNode2.setMacAddress(netmask);
      }
		s.commit();
    waitTillActive(s);

    copyFile2Slice(s,"/home/yaoyj11/project/exo-geni/SAFE_SDX/src/main/resources/scripts/dpid.sh","~/dpid.sh","~/.ssh/id_rsa");
		for(ComputeNode c : s.getComputeNodes()){
      String mip=c.getManagementIP();
      try{
        System.out.println(mip+" run commands:");
        //ScpTo.Scp(lfile,"root",mip,rfile,privkey);
        String routerid=c.toString();
        Exec.sshExec("root",mip,"/bin/bash ~/dpid.sh \""+SDNControllerIP+":8080\" "+routerid,"~/.ssh/id_rsa");
      }catch (Exception e){
        System.out.println("exception when copying config file");
      }
		}
    sleep(10);
		for(ComputeNode c : s.getComputeNodes()){
      for(Interface i: c.getInterfaces()){
        InterfaceNode2Net inode2net=(InterfaceNode2Net)i;
        System.out.println("Interface: link name"+inode2net.getLink());
        System.out.println("Interface: node name"+inode2net.getNode());
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

	public static  Slice CustomerSlice(String sliceName,int num,String prefix, int start){//=1, String subnet="")
		System.out.println("ndllib TestDriver: START");
		//Main Example Code
		
		Slice s = Slice.create(sliceProxy, sctx, sliceName);
		
		String nodeImageShortName="Ubuntu 14.04";
		String nodeImageURL ="http://geni-orca.renci.org/owl/9dfe179d-3736-41bf-8084-f0cd4a520c2f#Ubuntu+14.04";//http://geni-images.renci.org/images/standard/ubuntu/ub1304-ovs-opendaylight-v1.0.0.xml
		String nodeImageHash ="9394ca154aa35eb55e604503ae7943ddaecc6ca5";
		String nodeNodeType="XO Medium";
		String nodePostBootScript="apt-get update;apt-get -y install quagga\n"
      +"sed -i -- 's/zebra=no/zebra=yes/g' /etc/quagga/daemons\n"
      +"sed -i -- 's/ospfd=no/ospfd=yes/g' /etc/quagga/daemons\n";
		String nodeDomain=domains.get(0);
    ArrayList<ComputeNode> nodelist=new ArrayList<ComputeNode>();
    ArrayList<Network> netlist=new ArrayList<Network>();
    for(int i=0;i<num;i++){
		
      ComputeNode node0 = s.addComputeNode("CNode"+String.valueOf(i));
      node0.setImage(nodeImageURL,nodeImageHash,nodeImageShortName);
      node0.setNodeType(nodeNodeType);
      node0.setDomain(domains.get(i));
      node0.setPostBootScript(nodePostBootScript);
      nodelist.add(node0);
	
      //if(i!=num-1){
      //  Network net2 = s.addBroadcastLink("clink"+String.valueOf(i));
      //  InterfaceNode2Net ifaceNode1 = (InterfaceNode2Net) net2.stitch(node0);
      //}
      //if(i!=0){
      //  Network net=netlist.get(i-1);
      //  InterfaceNode2Net ifaceNode1 = (InterfaceNode2Net) net.stitch(node0);
      //  ifaceNode1.setIpAddress("192.168."+String.valueOf(start+i-1)+".2");
      //  ifaceNode1.setNetmask("255.255.255.0");
      //}
    }
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
	
	public static void stitchSlices(String carrierName, String customerName, String netName, String nodeName,String newip,int netmask){	
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
			sliceProxy.permitSliceStitch(carrierName, net1_stitching_GUID, "stitchSecret");
			//s2
			Properties p = new Properties();
			p.setProperty("ip", newip+"/"+String.valueOf(netmask));
			sliceProxy.performSliceStitch(customerName, node0_s2_stitching_GUID, carrierName, net1_stitching_GUID, "stitchSecret", p);
		} catch (TransportException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    Long t2 = System.currentTimeMillis();
    System.out.println("Finished Stitching, time elapsed: "+String.valueOf(t2-t1)+"\n");

    //Run commands to configure ospf on new interface for cusotmer node
    Exec.sshExec("root",node0_s2.getManagementIP(),"/bin/bash ~/configospffornewif.sh "+newip,"~/.ssh/id_rsa");
    System.out.println("finished sending reconfiguration command");
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

  private static String getSafeScript(String riakip){
		String script="apt-get update\n"
      +"docker pull yaoyj11/safeserver\n"
      +"docker run -i -t -d -p 7777:7777 -h safe --name safe yaoyj11/safeserver\n"
      +"docker exec -d safe /bin/bash -c  \"cd /root/safe;export SBT_HOME=/opt/sbt-0.13.12;export SCALA_HOME=/opt/scala-2.11.8;sed -i 's/152.3.145.36:8098/"+riakip+":8098/g' safe-server/src/main/resources/application.conf;./sdx.sh\"\n";
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

