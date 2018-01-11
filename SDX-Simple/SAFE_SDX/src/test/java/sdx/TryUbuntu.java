package sdx;

import org.renci.ahab.libndl.Slice;
import sdx.core.SliceCommon;
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
import sdx.core.TestSlice;

public class TryUbuntu extends  SliceCommon{
  final static Logger logger = Logger.getLogger(Exec.class);
  private static void addUbuntuServer(Slice s) {
    String dockerImageShortName = "ubuntu16.04";
    String dockerImageURL = "http://geni-images.renci.org/images/standard/ubuntu/ub1604-v1.0.4dev.xml";//http://geni-images.renci.org/images/standard/ubuntu/ub1304-ovs-opendaylight-v1.0.0.xml
    String dockerImageHash = "b8e6c544296dce5f91400974326d619d2910967f";
    String dockerNodeType = "XO Medium";
    ComputeNode node0 = s.addComputeNode("ubuntu16");
    node0.setImage(dockerImageURL, dockerImageHash, dockerImageShortName);
    node0.setNodeType(dockerNodeType);
    node0.setDomain("RENCI (Chapel Hill, NC USA) XO Rack");
  }

  private static void addComputeNode(Slice s,String name) {
    String dockerImageShortName = "ubuntu16.04";
    String dockerImageURL = "http://geni-images.renci.org/images/standard/ubuntu/ub1604-v1.0.4dev.xml";//http://geni-images.renci.org/images/standard/ubuntu/ub1304-ovs-opendaylight-v1.0.0.xml
    String dockerImageHash = "b8e6c544296dce5f91400974326d619d2910967f";
    //String dockerImageShortName = "Ubuntu 14.04 Docker";
    //String dockerImageURL = "http://geni-orca.renci.org/owl/5e2190c1-09f1-4c48-8ed6-dacae6b6b435#Ubuntu+14.0.4+Docker";//http://geni-images.renci.org/images/standard/ubuntu/ub1304-ovs-opendaylight-v1.0.0.xml
    //String dockerImageHash = "b4ef61dbd993c72c5ac10b84650b33301bbf6829";
    String dockerNodeType = "XO Medium";
    ComputeNode node0 = s.addComputeNode(name);
    node0.setImage(dockerImageURL, dockerImageHash, dockerImageShortName);
    node0.setNodeType(dockerNodeType);
  }

  private static void addLink(Slice s,String node1, String node2, String name){

    try {
      ComputeNode n1=(ComputeNode)s.getResourceByName(node1);
      ComputeNode n2=(ComputeNode)s.getResourceByName(node2);
      Network net2 = s.addBroadcastLink(name);
      InterfaceNode2Net ifaceNode1 = (InterfaceNode2Net) net2.stitch(n1);
      net2.stitch(n2);
    }catch (Exception e){
      e.printStackTrace();
    }
  }

  public static void main(String[] args) {
    System.out.println("SDX-Simple " + args[0]);

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

    try {
      Slice s = Slice.create(sliceProxy, sctx, "yuanjun-test");
      //addUbuntuServer(s);
      addComputeNode(s,"node1");
      addComputeNode(s,"node2");
      addLink(s,"node1","node2","link0");

      s.commit();
      waitTillActive(s);
      System.out.println("deleting link");
      BroadcastNetwork net=(BroadcastNetwork) s.getResourceByName("link0");
      net.delete();
      s.commit();
      waitTillActive(s);
      s.refresh();
      sleep(20);
      System.out.println("deleting node");
      s=getSlice(sliceProxy,"yuanjun-test");
      ComputeNode node=(ComputeNode) s.getResourceByName("node1");
      node.delete();
      s.commit();
    }catch (Exception e){

      e.printStackTrace();
    }
    //TestSlice usage:   ./target/appassembler/bin/SafeSdxTestSlice  ~/.ssl/geni-pruth1.pem ~/.ssl/geni-pruth1.pem "https://geni.renci.org:11443/orca/xmlrpc" pruth.1 stitch
    //TestSlice usage:   ./target/appassembler/bin/SafeSdxTestSlice  ~/.ssl/geni-pruth1.pem ~/.ssl/geni-pruth1.pem "https://geni.renci.org:11443/orca/xmlrpc" name fournodes
    System.out.println("SDX-Simple " + args[0]);

  }
}
