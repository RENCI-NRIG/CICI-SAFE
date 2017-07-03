package safe.sdx;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
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
import java.util.HashSet;
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

import java.net.MalformedURLException;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;

/*

 * @author geni-orca
 * @author Yuanjun Yao, yjyao@cs.duke.edu
 * This is the server for carrier slice. It's run by the carrier_slice owner to do the following things
 * 1. Load carriers slice information from exogeniSM, compute the topology
 * 2. Public: Take stitch request from customer slice
 *    Input: Request(carrier_slicename, nodename/sitename,customer_auth_information)
 *    Output: yes or no
 *    Question: Shall carrier slice perform the stitching directly
 * 3. Private: Authorize stitching request:
 *    Call SAFE to authorize the request
 *
 * 4. Private Perform slice stitch
 *    Create a link for stitching
 *
 * 5. Get the connectivity list from SAFE
 *
 * 6. Call SDN controller to install the rules
 */

public class RoutingServer extends UnicastRemoteObject implements RoutingAPI {
  public RoutingServer()throws RemoteException{}
	private static final String RequestResource = null;
	private static String controllerUrl;
  private static String SDNControllerIP;
  private static String SDNController;
  private static String OVSController;
	private static String sliceName;
	private static String pemLocation;
	private static String keyLocation;
  private static String sshkeyLocation;
  private static RoutingManager routingmanager=new RoutingManager();
  private static ISliceTransportAPIv1 sliceProxy;
  private static SliceAccessContext<SSHAccessToken> sctx;
  private static int curip=128;
  private static String IPPrefix="192.168.";
  private static String mask="/24";
  private static HashMap<String, Link> links=new HashMap<String, Link>();
	private static String safeserver;
  private static String server_keyhash;
  private static ArrayList<String[]> advertisements=new ArrayList<String[]>();
  private static ArrayList<Neighbor> neighborASes=new ArrayList<Neighbor>();

	
	public static void main(String [] args){
		//Example usage:   ./target/appassembler/bin/SafeSdxExample  ~/.ssl/geni-pruth1.pem ~/.ssl/geni-pruth1.pem "https://geni.renci.org:11443/orca/xmlrpc" sliceName SDN_controllerIP "~/.ssh/id_rsa" safeserver
    //if(true){
	  //safeserver=args[6];
    //authorizeStitchRequest("weQ8OFpXWhIB1AMzKX2SDJcxT738VdHCcl7mFlvOD24","client","4db2b812-39ac-4333-9417-07adef946e68","bphJZn3RJBnNqoCZk6k9SBD8mwSb054PXbwV7HpE80E","server", "c0");
    //return ;
    //}
    
		System.out.println("Carrier Slice server with Service API: START");
		pemLocation = args[0];
		keyLocation = args[1];
		controllerUrl = args[2]; //"https://geni.renci.org:11443/orca/xmlrpc";
		sliceName = args[3]; //"pruth.sdx.1";
    SDNControllerIP=args[4];
    SDNController=SDNControllerIP+":8080";
    OVSController=SDNControllerIP+":6633";
    sshkeyLocation=args[5];
	  safeserver=args[6];
    server_keyhash=args[7];
    computeIP(args[8]);
    readNeighbors();
		sliceProxy = RoutingServer.getSliceProxy(pemLocation,keyLocation, controllerUrl);		
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
    Slice server_slice = null;
    try {
      server_slice = Slice.loadManifestFile(sliceProxy, sliceName);
    } catch (ContextTransportException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (TransportException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    configRouting(server_slice,OVSController,SDNController);

    try
    {
      RoutingServer obj = new RoutingServer(); 
      // Bind this object instance to the name "HelloServer" 
      Naming.rebind("ServiceServer_"+sliceName, obj); 
    }
    catch (Exception e)
    {
      System.out.println("HelloImpl err: " + e.getMessage()); 
      e.printStackTrace(); 
    }
    String input = new String();  
		try{
//	 			System.out.println(obj.sayHello()); 
      java.io.BufferedReader stdin = new java.io.BufferedReader(new java.io.InputStreamReader(System.in));  
      while(true){
        System.out.print("Enter Commands:stitch clientslicename, server slice anme, client resource name, server resource name\n Or advertise route: route dest serverslicename\n$>");
        input = stdin.readLine();  
        String[] params=input.split(" ");
        System.out.print("continue?[y/n]\n$>"+input);
        input = stdin.readLine();  
        if(input.startsWith("y")){
          try{
            if(params[0].equals("stitch")){
              processStitchCmd(params);
            }else{
              continue;
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

  private static void readNeighbors(){
    try{
      java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader("data/"+sliceName+".nb"));
      String input = br.readLine();  
      while(input!=null){
        String[] items=input.replace("\n","").split(" ");
        String gw=items[0].split(":")[1];
        String edgerouter=items[1].split(":")[1];
        String edgeip=items[2].split(":")[1];
        String keyhash=items[3].split(":")[1];
        String cusname=items[4].split(":")[1];
        neighborASes.add(new Neighbor(keyhash,gw,edgerouter,edgeip,cusname));
        input = br.readLine();
      }
		}catch (java.io.IOException e) {  
				System.out.println(e);   
		}  
    for(Neighbor nb :neighborASes){
      System.out.print(nb.toString());
    }
  }

  private void addEntry_HashList(HashMap<String,ArrayList<String>>  map,String key, String entry){
    if(map.containsKey(key)){
      ArrayList<String> l=map.get(key);
      l.add(entry);
    }
    else{
      ArrayList<String> l=new ArrayList<String>();
      l.add(entry);
      map.put(key,l);
    }
  }

  private ArrayList<String[]> getAllElments_HashList(HashMap<String,ArrayList<String>>  map){
    ArrayList<String[]> res=new ArrayList<String[]>();
    for(String key:map.keySet()){
        for(String ip:map.get(key)){
          String[] pair=new String[2];
          pair[0]=key;
          pair[1]=ip;
          res.add(pair);
        }
    }
    return res;
  }

  private static void computeIP(String prefix){
    System.out.println("Private Prefix for "+sliceName+" :"+prefix);
    String[] ip_mask=prefix.split("/");
    String[] ip_segs=ip_mask[0].split("\\.");
    System.out.println("IP"+ip_mask[0]);
    System.out.println("len"+ip_segs.length);
    curip=Integer.valueOf(ip_segs[2]);
    IPPrefix=ip_segs[0]+"."+ip_segs[1]+".";
  }

  private static void processStitchCmd(String[] params){
    System.out.print("Stitch process");
		sliceProxy = RoutingServer.getSliceProxy(pemLocation,keyLocation, controllerUrl);		
    try{
      RoutingAPI obj = (RoutingAPI) Naming.lookup( "//" + 
          "localhost" + 
          "/ServiceServer_"+params[2]);         //objectname in registry 
      Slice s2 = null;
      try {
        s2 = Slice.loadManifestFile(sliceProxy, params[1]);
      } catch (ContextTransportException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
        System.out.print("context transport exception");
      } catch (TransportException e) {
        // TODO Auto-generated catch block
        System.out.print("transport exception");
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
      postSafeStitchRequest(server_keyhash,params[1],node0_s2_stitching_GUID,params[2],params[4]);
      String res=obj.stitchRequest(params[2],params[4],server_keyhash,params[1],node0_s2_stitching_GUID,secret);
      System.out.println("Got Stitch Information From Server: "+res);
      if(res.equals("")){
        System.out.println("stitch request declined by server");
      } 
      else{
        String[] parts=res.split("_");
        String ip=parts[1];
        System.out.println("set IP address of the stitch interface to "+ip);
        sleep(10);
        Neighbor nb=new Neighbor(parts[2],parts[0],params[3],ip,params[2]);
        neighborASes.add(nb);

        AppendFile("data/"+sliceName+".nb",nb.toString());

        Exec.sshExec("root",node0_s2.getManagementIP(),"/bin/bash ~/ovsbridge.sh "+OVSController,sshkeyLocation);
        routingmanager.replayCmds(routingmanager.getDPID(params[3]));
        Exec.sshExec("root",node0_s2.getManagementIP(),"ifconfig;ovs-vsctl list port",sshkeyLocation);
        Link link=new Link();
        String stitchname="stitch_to_a"+params[2];
        link.setName(stitchname);
        link.addNode(params[3]);
        links.put(stitchname,link);
        routingmanager.newLink(ip, link.nodea, SDNController);
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
  

  public static String  pathStr(ArrayList<String>path){
    String p="["+path.get(0);
    for(int i=1;i<path.size();i++){
      p=p+","+path.get(i);
    }
    p=p+"]";
    return p;
  }
  public String postAdvertiseRoute(String dest, ArrayList<String> path, String prcpl, String recv){
      String []othervalues=new String[5];
      othervalues[0]=dest;
      String p=pathStr(path);
      othervalues[1]=p;
      othervalues[2]=recv;
      othervalues[3]=path.get(1);
      othervalues[4]=String.valueOf(path.size()-1);
      String message=SafePost.postSafeStatements(safeserver,"postAdvertise",prcpl,othervalues);
      if(message==null || message.contains("fail")){
        System.out.print("Failed to post safe statements for routing");
        return null;
      }
      else 
        return SafePost.getToken(message);

  }

  public static String postPathToken(String token, String dest, String src, int len,String prcpl){
      String []othervalues=new String[4];
      othervalues[0]=token;
      othervalues[1]=dest;
      othervalues[2]=src;
      othervalues[3]=String.valueOf(len);
      String message=SafePost.postSafeStatements(safeserver,"postPathToken",prcpl,othervalues);
      if(message==null || message.contains("fail")){
        System.out.print("Failed to post safe statements for routing");
        return null;
      }
      else 
        return SafePost.getToken(message);
  }

  public  boolean advertiseRoute(String dest, ArrayList<String> path, String customer_keyhash,String token) throws RemoteException{
    System.setProperty("java.security.policy","~/project/exo-geni/SAFE_SDX/allow.policy");
    System.out.print("received the route for dest"+dest+ " token:"+token);
    postPathToken(token,dest,customer_keyhash,path.size(),server_keyhash);
    String dstip=dest.substring(6,dest.length()-2);

    // Verify the route advertisement first.
    if(authorizeRoute(server_keyhash,customer_keyhash,path,dest)){
      //avoid cycles
      if(path.contains(server_keyhash))
          return true;
      System.out.print("Consider route for dst"+dest+"  Num neighbors:"+String.valueOf(neighborASes.size()));
      //Make advertisements to all neighbor ASes
      for (Neighbor nb: neighborASes){
        try{
          System.out.print("Consider Neighbor:"+nb.id+" sender hash:"+customer_keyhash);
          if(!nb.id.equals(customer_keyhash)){
            RoutingAPI apiobj=(RoutingAPI) Naming.lookup("//"+"localhost"+"/ServiceServer_"+nb.sliceName);
            path.add(0,server_keyhash);
            //advertise the route
            System.out.print("Readvertise the route for dest"+dest);
            String ntoken=postAdvertiseRoute(dest,path,server_keyhash,nb.id);
            if(ntoken!=null){
              apiobj.advertiseRoute(dest,path,server_keyhash,ntoken);
            }
          }
        }
        catch (Exception e){
          e.printStackTrace();
        }
      }
      //Configure Routing Table within the network
      for (Neighbor nb: neighborASes){
        if (nb.id.equals(customer_keyhash)){
          String edgerouter=nb.edgerouter;
          String gateway= nb.gateway.split("/")[0];
          routingmanager.configurePath(dstip,edgerouter,gateway,SDNController);
          break;
        }
      }
  
    }
    return false;
  }

  private boolean authorizeRoute(String recvhash, String sendhash, ArrayList<String> path, String dstip){
    String[] othervalues=new String[4];
    othervalues[0]=dstip;
    String p=pathStr(path);
    othervalues[1]=p;
    othervalues[2]=sendhash;
    othervalues[3]=String.valueOf(path.size());
    String message=SafePost.postSafeStatements(safeserver,"verifyRoute",recvhash,othervalues);
    if(message ==null ||  message.contains("Unsatisfied")){
      return false;
    }
    else
      return true;
  }

  //The customer slice permit stitching to its node, and give the secret to the service slice. The server add a new link to the targeted node and stitch the link to the node in customer slice.
  public String stitchRequest(String carrierName,String nodeName, String customer_keyhash,String customerName, String ResrvID,String secret) {
    String res="";
    System.out.println("new request for"+carrierName +" and "+nodeName+pemLocation+keyLocation);
    if(authorizeStitchRequest(customer_keyhash,customerName,ResrvID, server_keyhash,carrierName, nodeName)){
      Slice s1 = null;
      ISliceTransportAPIv1 sliceProxy = RoutingServer.getSliceProxy(pemLocation,keyLocation, controllerUrl);
      try {
        s1 = Slice.loadManifestFile(sliceProxy, carrierName);
      } catch (ContextTransportException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } catch (TransportException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      ComputeNode node = (ComputeNode) s1.getResourceByName(nodeName);
      int interfaceNum=routingmanager.getRouter(nodeName).getInterfaceNum();
      String stitchname="stitch_"+nodeName+"_"+curip;
      // Add the network link
      Network net=s1.addBroadcastLink(stitchname);
      InterfaceNode2Net ifaceNode0 = (InterfaceNode2Net) net.stitch(node);
      //ifaceNode0.setIpAddress("192.168.1.1");
      //ifaceNode0.setNetmask("255.255.255.0");
      //this doesn't matter since we set the ip address via ovs
      s1.commit();
      int N=0;
      net=(Network)s1.getResourceByName(stitchname);
      while(net.getState() != "Active" &&N<10){
        try {
          s1 = Slice.loadManifestFile(sliceProxy, carrierName);
        } catch (ContextTransportException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        } catch (TransportException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
        net=(Network)s1.getResourceByName(stitchname);
        for(Network l:s1.getBroadcastLinks()){
          System.out.println("Resource: " + l.getName() + ", state: "  + l.getState());
        }
        System.out.println(((Network)s1.getResourceByName(stitchname)).getState());
        sleep(5);
        N++;
      }
      sleep(10);
      Exec.sshExec("root",node.getManagementIP(),"/bin/bash ~/ovsbridge.sh "+OVSController,sshkeyLocation);
      routingmanager.replayCmds(routingmanager.getDPID(nodeName));
      Exec.sshExec("root",node.getManagementIP(),"ifconfig;ovs-vsctl list port",sshkeyLocation);
      String net1_stitching_GUID = net.getStitchingGUID();
      System.out.println("net1_stitching_GUID: " + net1_stitching_GUID);
      Link link=new Link();
      link.setName(stitchname);
      link.addNode(nodeName);
      link.setIP(IPPrefix+String.valueOf(curip));
      link.setMask(mask);
      links.put(stitchname,link);
      routingmanager.newLink(link.getIP(1), link.nodea, SDNController);
      curip+=1;
      String gw = link.getIP(1);
      String ip=link.getIP(2);
      if(!stitch(customerName,ResrvID,carrierName,net1_stitching_GUID,secret,ip)){
        System.out.println("Stitch Operation Failed");
        return res;
      }
      else{
        res=res+gw+"_"+ip+"_"+server_keyhash;
        //routingmanager.configurePath(ip,nodeName,ip.split("/")[0],SDNController);
        Neighbor nb=new Neighbor(customer_keyhash,ip.split("/")[0],nodeName,gw,customerName);
        AppendFile("data/"+sliceName+".nb",nb.toString());
        neighborASes.add(nb);
        System.out.println(nb.toString());
      }
    }
    return res;
  }

	public static boolean stitch(String customerName, String ReservID,String carrierName, String netID,String secret,String newip){	
		System.out.println("Slice Stitching: Start");
		//Main Example Code
    Long t1 = System.currentTimeMillis();
		try {
			//s2
			Properties p = new Properties();
      System.out.println(newip);
			p.setProperty("ip", newip);
      System.out.println("CustomerName:"+ customerName+" CID:"+ReservID+" secret:"+secret+" SName: "+carrierName+" SID:"+netID);
      sliceProxy = RoutingServer.getSliceProxy(pemLocation,keyLocation, controllerUrl);		
      System.out.println("slice proxy Failed?");
			sliceProxy.performSliceStitch(carrierName, netID,customerName, ReservID, secret, p);
        System.out.println("Stitch Operation Failed?");

		} catch (TransportException e) {
			// TODO Auto-generated catch block
      System.out.println("Caught exception");
			e.printStackTrace();
      return false;
		}
    Long t2 = System.currentTimeMillis();
    System.out.println("Finished Stitching, set ip address of the new interface to "+newip+"  time elapsed: "+String.valueOf(t2-t1)+"\n");
    //Run commands to configure ospf on new interface for cusotmer node
    //Exec.sshExec("root",node0_s2.getManagementIP(),"/bin/bash ~/configospffornewif.sh "+newip,"~/.ssh/id_rsa");
    System.out.println("finished sending reconfiguration command");
    return true;
	}

  public static boolean authorizeStitchRequest(String customer_keyhash,String customerName,String ReservID,String server_keyhash,String slicename, String nodename){
		/* Post to remote safesets using apache httpclient */
    String[] othervalues=new String[5];
    othervalues[0]=customer_keyhash;
    othervalues[1]=customerName;
    othervalues[2]=ReservID;
    othervalues[3]=slicename;
    othervalues[4]=nodename;
    String message=SafePost.postSafeStatements(safeserver,"verifyStitch",server_keyhash,othervalues);
    if(message !=null && message.contains("Unsatisfied")){
      System.out.print("Stitch request unsatisfied");
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
        Exec.sshExec("root",mip,cmd,privkey);

      }catch (Exception e){
        System.out.println("exception when copying config file");
      }
		}
  }

  public static void configRouting(Slice s,String ovscontroller, String httpcontroller){
    System.out.println("Configurating Routing");
    System.setProperty("java.security.policy","~/project/exo-geni/SAFE_SDX/allow.policy");
    try{
      for(ComputeNode node: s.getComputeNodes()){
        String mip= node.getManagementIP();
        System.out.println(node.getName()+" "+mip);
        Exec.sshExec("root",mip,"/bin/bash ~/ovsbridge.sh "+ ovscontroller,"~/.ssh/id_rsa").split(" ");
        String[] result=Exec.sshExec("root",mip,"/bin/bash ~/dpid.sh","~/.ssh/id_rsa").split(" ");
        try{
          System.out.println("Get router info "+result[0]+" "+result[1]);
          routingmanager.newRouter(node.getName(),result[1],Integer.valueOf(result[0]));
        }catch(Exception e){
          e.printStackTrace();
        }
      }
      System.out.println("setting up links)");
      HashSet<Integer> usedip=new HashSet<Integer>();
      for(Interface i: s.getInterfaces()){
        InterfaceNode2Net inode2net=(InterfaceNode2Net)i;
        Link link=links.get(inode2net.getLink().toString());
        if(link==null){
          link=new Link();
          link.setName(inode2net.getLink().toString());
          link.addNode(inode2net.getNode().toString());
          if(link.linkname.contains("stitch")){
            continue;
            //String[] parts=link.linkname.split("_");
            //String ip=parts[2];
            //link.setIP(IPPrefix+ip);
            //link.setMask(mask);
            //usedip.add(Integer.valueOf(parts[2]));
          }
        }
        else{
          link.addNode(inode2net.getNode().toString());
        }
        links.put(inode2net.getLink().toString(),link);
        //System.out.println(inode2net.getNode()+" "+inode2net.getLink());
      }
      //Configure IP addresses for Neighbors
      for (Neighbor nb: neighborASes){
        Link link=new Link();
        link.setName("stitch_to_"+nb.sliceName);
        link.addNode(nb.edgerouter);
        usedip.add(Integer.valueOf(nb.edgeip.split("\\.")[2]));
        routingmanager.newLink(nb.edgeip,nb.edgerouter,httpcontroller);
      }
      Set keyset=links.keySet();
      //System.out.println(keyset);
      for(Object k: keyset){
        Link link=links.get((String)k);
        if(!((String)k).contains("stitch")){
          while(usedip.contains(curip)){
            curip++;
          }
          link.setIP(IPPrefix+String.valueOf(curip));
          link.setMask(mask);
          curip++;
        }
        String param="";
        if(link.nodeb!=""){
          //System.out.println(link.nodea+":"+link.getIP(1)+" "+link.nodeb+":"+link.getIP(2));
          routingmanager.newLink(link.getIP(1), link.nodea, link.getIP(2), link.nodeb, httpcontroller);
        }
        else{
          //System.out.println(link.nodea+" gateway address:"+link.getIP(1));
          routingmanager.newLink(link.getIP(1), link.nodea, httpcontroller);
        }
      }
    }catch(Exception e){
      e.printStackTrace();
    }
  }

  public static void getNetworkInfo(Slice s){
    //getLinks
    for(Network n :s.getLinks()){
      System.out.println(n.getLabel()+" "+n.getState());
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

  public static void  AppendFile(String filepath, String data){
    BufferedWriter bw=null;
    FileWriter fw=null;
    try{
      File file=new File(filepath);
      if (!file.exists()){
        file.createNewFile();
      }
      fw=new FileWriter(file.getAbsoluteFile(),true);
      bw = new BufferedWriter(fw);
      bw.write(data);
      System.out.print("NewNeighbor: "+data);
    }catch (IOException e){
      e.printStackTrace();
    }finally{
      try{
        if(bw!=null)
          bw.close();
        if (fw!=null)
          fw.close();
      }catch (IOException ex){
        ex.printStackTrace();
      }
    }
  }

	public static final ArrayList<String> domains;
	static {
		ArrayList<String> l = new ArrayList<String>();

		for (int i = 0; i < 100; i++){
//			l.add("PSC (Pittsburgh, TX, USA) XO Rack");
//			l.add("UAF (Fairbanks, AK, USA) XO Rack");
		
//			l.add("UH (Houston, TX USA) XO Rack");
//			l.add("TAMU (College Station, TX, USA) XO Rack");
//			l.add("RENCI (Chapel Hill, NC USA) XO Rack");
//			
//			l.add("SL (Chicago, IL USA) XO Rack");
//			
//			
//			l.add("OSF (Oakland, CA USA) XO Rack");
//			
		l.add("UMass (UMass Amherst, MA, USA) XO Rack");
			//l.add("WVN (UCS-B series rack in Morgantown, WV, USA)");
	//		l.add("UAF (Fairbanks, AK, USA) XO Rack");
//    l.add("UNF (Jacksonville, FL) XO Rack");
//		l.add("UFL (Gainesville, FL USA) XO Rack");
//			l.add("WSU (Detroit, MI, USA) XO Rack");
//			l.add("BBN/GPO (Boston, MA USA) XO Rack");
//			l.add("UvA (Amsterdam, The Netherlands) XO Rack");

		}
		domains = l;
	}
}

