package sdx;

import com.hp.hpl.jena.tdb.store.Hash;
import sdx.core.*;
import org.apache.log4j.Logger;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.json.JSONObject;

import sdx.utils.Exec;
import sdx.utils.HttpUtil;

import org.renci.ahab.libndl.Slice;
import sdx.core.SliceCommon;

import java.lang.reflect.Array;
import java.util.*;
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
import java.net.URI;
public class TestSiteLink extends SliceCommon{
  final static Logger logger = Logger.getLogger(Exec.class);
  public static void main(String[] args) {

    //TestSlice usage:   ./target/appassembler/bin/SafeSdxTestSlice  ~/.ssl/geni-pruth1.pem ~/.ssl/geni-pruth1.pem "https://geni.renci.org:11443/orca/xmlrpc" pruth.1 stitch
    //TestSlice usage:   ./target/appassembler/bin/SafeSdxTestSlice  ~/.ssl/geni-pruth1.pem ~/.ssl/geni-pruth1.pem "https://geni.renci.org:11443/orca/xmlrpc" name fournodes

    CommandLine cmd = parseCmd(args);

    logger.debug("cmd " + cmd);

    String configfilepath = cmd.getOptionValue("config");

    readConfig(configfilepath);

    logger.debug("configfilepath " + configfilepath);
    //readConfig(configfilepath);

    //type=conf.getString("config.type");
    if (cmd.hasOption('d')) {
      type = "delete";
    }
//    testSiteLink();
   // testLinkDelete();
    testLinkAddition();
  }

  private static void testLinkAddition(){
    sliceProxy = TestSlice.getSliceProxy(pemLocation, keyLocation, controllerUrl);

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
    Slice s = Slice.create(sliceProxy, sctx, sliceName);

    String nodeImageShortName = "Ubuntu 14.04";
    String nodeImageURL = "http://geni-orca.renci.org/owl/9dfe179d-3736-41bf-8084-f0cd4a520c2f#Ubuntu+14.04";//http://geni-images.renci.org/images/standard/ubuntu/ub1304-ovs-opendaylight-v1.0.0.xml
    String nodeImageHash = "9394ca154aa35eb55e604503ae7943ddaecc6ca5";
    String nodeNodeType = "XO Small";
    //String nodePostBootScript="apt-get update;apt-get -y  install quagga\n"
    //  +"sed -i -- 's/zebra=no/zebra=yes/g' /etc/quagga/daemons\n"
    //  +"sed -i -- 's/ospfd=no/ospfd=yes/g' /etc/quagga/daemons\n";
    String site1=null;
    String site2=null;
    for(String site:sitelist) {
      if (site.contains("SL")) {
        site1 = site;
      } else if (site.contains("UFL")) {
        site2 = site;
      }
    }

    try {
      ComputeNode node0 = s.addComputeNode("uh");
      node0.setImage(nodeImageURL, nodeImageHash, nodeImageShortName);
      node0.setNodeType(nodeNodeType);
      node0.setDomain(site1);
      ComputeNode node1 = s.addComputeNode("tamu");
      node1.setImage(nodeImageURL, nodeImageHash, nodeImageShortName);
      node1.setNodeType(nodeNodeType);
      node1.setDomain(site2);
      s.commit();
      waitTillActive(s);
      s=getSlice();
      node0=(ComputeNode)s.getResourceByName("uh") ;
      node1=(ComputeNode)s.getResourceByName("tamu") ;

      Network net2 = s.addBroadcastLink("link0", 1000000l);
      InterfaceNode2Net ifaceNode1 = (InterfaceNode2Net) net2.stitch(node0);
      InterfaceNode2Net ifaceNode2 = (InterfaceNode2Net) net2.stitch(node1);
      s.commit();
      waitTillActive(s);
      Collection<BroadcastNetwork> blinks=s.getBroadcastLinks();
      Collection<Interface> intfs=s.getInterfaces();
      for(Interface inf:intfs){
        System.out.println("interface " +inf.getName() );
      }
      Collection<ModelResource> ress=s.getAllResources();
      for(ModelResource  res:ress){
        System.out.println("resource: "+res.getName());
      }
      System.out.println("Testing adding a link after the slice is active");
      s=getSlice();
      ComputeNode node2 = s.addComputeNode("uh-1");
      node2.setImage(nodeImageURL, nodeImageHash, nodeImageShortName);
      node2.setNodeType(nodeNodeType);
      node2.setDomain(site1);
      s.commit();
      waitTillActive(s);
      blinks=s.getBroadcastLinks();
      intfs=s.getInterfaces();
      for(Interface inf:intfs){
        System.out.println("interface " +inf.getName() );
      }
      ress=s.getAllResources();
      for(ModelResource  res:ress){
        System.out.println("resource: "+res.getName());
      }
    }catch (Exception e){
      e.printStackTrace();
    }

  }


  private static void testLinkDelete(){
    sliceProxy = TestSlice.getSliceProxy(pemLocation, keyLocation, controllerUrl);

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
    Slice s = Slice.create(sliceProxy, sctx, sliceName);

    String nodeImageShortName = "Ubuntu 14.04";
    String nodeImageURL = "http://geni-orca.renci.org/owl/9dfe179d-3736-41bf-8084-f0cd4a520c2f#Ubuntu+14.04";//http://geni-images.renci.org/images/standard/ubuntu/ub1304-ovs-opendaylight-v1.0.0.xml
    String nodeImageHash = "9394ca154aa35eb55e604503ae7943ddaecc6ca5";
    String nodeNodeType = "XO Small";
    //String nodePostBootScript="apt-get update;apt-get -y  install quagga\n"
    //  +"sed -i -- 's/zebra=no/zebra=yes/g' /etc/quagga/daemons\n"
    //  +"sed -i -- 's/ospfd=no/ospfd=yes/g' /etc/quagga/daemons\n";
    String site1=null;
    String site2=null;
    for(String site:sitelist) {
      if (site.contains("RENCI")) {
        site1 = site;
      } else if (site.contains("TAMU")) {
        site2 = site;
      }
    }

    try {
        ComputeNode node0 = s.addComputeNode("renci");
        node0.setImage(nodeImageURL, nodeImageHash, nodeImageShortName);
        node0.setNodeType(nodeNodeType);
        node0.setDomain(site1);
        ComputeNode node1 = s.addComputeNode("tamu");
        node1.setImage(nodeImageURL, nodeImageHash, nodeImageShortName);
        node1.setNodeType(nodeNodeType);
        node1.setDomain(site2);
        ComputeNode node2 = s.addComputeNode("renci-1");
        node2.setImage(nodeImageURL, nodeImageHash, nodeImageShortName);
        node2.setNodeType(nodeNodeType);
        node2.setDomain(site1);
        Network net2 = s.addBroadcastLink("link0", 1000000l);
        InterfaceNode2Net ifaceNode1 = (InterfaceNode2Net) net2.stitch(node0);
        ifaceNode1.setIpAddress("192.168." + String.valueOf(1) + ".1");
        ifaceNode1.setNetmask("255.255.255.0");
        InterfaceNode2Net ifaceNode2 = (InterfaceNode2Net) net2.stitch(node1);
        ifaceNode2.setIpAddress("192.168." + String.valueOf(1) + ".2");
        ifaceNode2.setNetmask("255.255.255.0");
      Network net1= s.addBroadcastLink("link1", 1000000l);
      InterfaceNode2Net ifaceNode3 = (InterfaceNode2Net) net1.stitch(node0);
      ifaceNode3.setIpAddress("192.168." + String.valueOf(2) + ".1");
      ifaceNode3.setNetmask("255.255.255.0");
      InterfaceNode2Net ifaceNode4 = (InterfaceNode2Net) net1.stitch(node2);
      ifaceNode4.setIpAddress("192.168." + String.valueOf(2) + ".2");
      ifaceNode4.setNetmask("255.255.255.0");

        s.commit();
        waitTillActive(s);
      Collection<BroadcastNetwork> blinks=s.getBroadcastLinks();
      Collection<Interface> intfs=s.getInterfaces();
      for(Interface inf:intfs){
        System.out.println("interface " +inf.getName() );
      }
      Collection<ModelResource> ress=s.getAllResources();
      for(ModelResource  res:ress){
        System.out.println("resource: "+res.getName());
      }
      s=getSlice();
      blinks=s.getBroadcastLinks();
      for(BroadcastNetwork link:blinks){
        System.out.println("deleting "+link.getName());
        link.delete();
      }
      s.commit();
      s.refresh();
      waitTillActive(s);

      intfs=s.getInterfaces();
      for(Interface inf:intfs){
        System.out.println("interface " +inf.getName() );
      }
      ress=s.getAllResources();
      for(ModelResource  res:ress){
        System.out.println("resource: "+res.getName());
      }

      waitTillActive(s);
    }catch (Exception e){
      e.printStackTrace();
    }

  }

  private static void testSiteLink(){

    sliceProxy = TestSlice.getSliceProxy(pemLocation, keyLocation, controllerUrl);

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
    Slice s = Slice.create(sliceProxy, sctx, sliceName);

    String nodeImageShortName = "Ubuntu 14.04";
    String nodeImageURL = "http://geni-orca.renci.org/owl/9dfe179d-3736-41bf-8084-f0cd4a520c2f#Ubuntu+14.04";//http://geni-images.renci.org/images/standard/ubuntu/ub1304-ovs-opendaylight-v1.0.0.xml
    String nodeImageHash = "9394ca154aa35eb55e604503ae7943ddaecc6ca5";
    String nodeNodeType = "XO Small";
    //String nodePostBootScript="apt-get update;apt-get -y  install quagga\n"
    //  +"sed -i -- 's/zebra=no/zebra=yes/g' /etc/quagga/daemons\n"
    //  +"sed -i -- 's/ospfd=no/ospfd=yes/g' /etc/quagga/daemons\n";
    ArrayList<ComputeNode> nodelist = new ArrayList<ComputeNode>();
    ArrayList<String>notavailable=new ArrayList<>();
    ArrayList<String>available=new ArrayList<>();
    HashMap<String,String>namesite=new HashMap<String,String>();
    try {
    for(String site:sitelist){
      String name=site.split("\\(")[0].replaceAll("[^A-Za-z\\d]+","");
      namesite.put(name,site);
      namesite.put(site,name);
      System.out.println(site+" "+name);
      ComputeNode node0 = s.addComputeNode(name);
      node0.setImage(nodeImageURL, nodeImageHash, nodeImageShortName);
      node0.setNodeType(nodeNodeType);
      node0.setDomain(site);
      try {
        s.commit();
        s.refresh();
        nodelist.add(node0);
        available.add(site);
        for(ComputeNode c : s.getComputeNodes()) {
          System.out.println("Resource: " + c.getName() + ", state: " + c.getState());
        }
      }catch (Exception e){
        e.printStackTrace();
        notavailable.add(site);
        s.refresh();
      }
    }
    int start=1;
    HashSet<String>connection=new HashSet<String>();
      HashSet<String>uncon=new HashSet<String>();
    waitTillActive(s);
    for(int i=0;i<nodelist.size();i++) {
      for (int j = i + 1; j < nodelist.size(); j++) {
        try {
          ComputeNode node1 = (ComputeNode) s.getResourceByName(nodelist.get(i).getName());
          ComputeNode node2 = (ComputeNode) s.getResourceByName(nodelist.get(j).getName());
          System.out.println(node1.getName() + "-" + node2.getName());
          Network net2 = s.addBroadcastLink(node1.getName() + "-" + node2.getName(), 1000000l);
          InterfaceNode2Net ifaceNode1 = (InterfaceNode2Net) net2.stitch(node1);
          ifaceNode1.setIpAddress("192.168." + String.valueOf(start) + ".1");
          ifaceNode1.setNetmask("255.255.255.0");
          InterfaceNode2Net ifaceNode2 = (InterfaceNode2Net) net2.stitch(node2);
          ifaceNode2.setIpAddress("192.168." + String.valueOf(start) + ".2");
          ifaceNode2.setNetmask("255.255.255.0");
          start = start % 240 + 1;
          try {
            s.commit();
            waitTillActive(s);
            s.refresh();
            for (BroadcastNetwork l : s.getBroadcastLinks()) {
              if (l.getState().equals("Active")) {
                connection.add(l.getName());
                l.delete();
                System.out.println("deleting the active link "+l.getName());
                s.commit();
              } else if (l.getState()=="Failed") {
                uncon.add(l.getName());
              }
              logger.debug("Resource: " + l.getName() + ", state: " + l.getState());

            }
            try {
              s = Slice.loadManifestFile(sliceProxy, sliceName);
            } catch (Exception ex) {
              ex.printStackTrace();
            }
          } catch (Exception e) {
            e.printStackTrace();
          }
        }catch (Exception e){
          e.printStackTrace();
          try {
            s = Slice.loadManifestFile(sliceProxy, sliceName);
          } catch (Exception ex) {
            ex.printStackTrace();
          }
        }
      }
    }
    System.out.println("Available sites");
    for(String site:available){
      System.out.print("\""+site+"\",");
    }
    System.out.print("\nAvailable Connections:\n");
    for(String str:connection){
      System.out.println(str);
    }

      waitTillActive(s);
    }catch (Exception e){
      e.printStackTrace();
    }
  }
}
