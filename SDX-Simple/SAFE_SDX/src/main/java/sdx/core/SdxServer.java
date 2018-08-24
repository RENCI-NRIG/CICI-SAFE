package sdx.core;

import org.apache.log4j.Logger;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

import sdx.utils.Exec;

import java.io.IOException;
import java.net.URI;

import com.typesafe.config.*;
import org.apache.commons.cli.*;

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
import java.util.HashSet;
import java.util.Properties;
import java.util.concurrent.locks.ReentrantLock;

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

import java.net.MalformedURLException;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;


/**
 * Main class.
 *
 */
public class SdxServer {
      final static Logger logger = Logger.getLogger(SdxServer.class);

    
    // Base URI the Grizzly HTTP server will listen on
    /**
     * Starts Grizzly HTTP server exposing JAX-RS resources defined in this application.
     * @return Grizzly HTTP server.
     */
    public static HttpServer startServer(String url) {
        // create a resource config that scans for JAX-RS resources and providers
        // in com.example package
        final ResourceConfig rc = new ResourceConfig().packages("sdx.core");
        // create and start a new instance of grizzly http server
        // exposing the Jersey application at BASE_URI
        return GrizzlyHttpServerFactory.createHttpServer(URI.create(url), rc);
    }

    public static CommandLine parseCmd(String[] args){
    Options options = new Options();
    Option config = new Option("c", "config", true, "configuration file path");
    Option config1 = new Option("d", "delete", false, "delete the slice");
    Option config2 = new Option("n", "nosafe", false, "use safe authorization");
    config.setRequired(true);
    config1.setRequired(false);
    config2.setRequired(false);
    options.addOption(config);
    options.addOption(config1);
    options.addOption(config2);
    CommandLineParser parser = new DefaultParser();
    HelpFormatter formatter = new HelpFormatter();
    CommandLine cmd=null;

    try {
        cmd = parser.parse(options, args);
    } catch (ParseException e) {
        logger.debug(e.getMessage());
        formatter.printHelp("utility-name", options);

        System.exit(1);
        return cmd;
    }
    return cmd;
  }

  protected static void readConfig(String configfilepath){

    File myConfigFile = new File(configfilepath);
    Config fileConfig = ConfigFactory.parseFile(myConfigFile);
    conf = ConfigFactory.load(fileConfig);
    type=conf.getString("config.type");
    sshkey=conf.getString("config.sshkey");
    controllerUrl=conf.getString("config.exogenism");
    keyhash=conf.getString("config.safekey");
    pemLocation=conf.getString("config.exogenipem");
    keyLocation=conf.getString("config.exogenipem");
    sliceName=conf.getString("config.slicename");
    serverSite=conf.getString("config.serversite");
    controllerSite=conf.getString("config.controllersite");
    
    String clientSitesStr = conf.getString("config.clientsites");
    clientSites = new ArrayList<String>();
    for(String site : clientSitesStr.split(":")){
        clientSites.add(site);
    }
    
  }



    /**
     * Main method.
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        logger.debug("Carrier Slice server with Service API: START");
        CommandLine cmd=parseCmd(args);

        String IPPrefix;
        String serverurl;
        String safeserver;
        String SDNControllerIP;
        String SDNController;
        String OVSController;

        if(cmd.hasOption('n')){
          safeauth=false;
          System.out.println("Safe disabled, allowing all requests");
        } else{
          safeauth=true;
        }
        String configfilepath=cmd.getOptionValue("config");
        readConfig(configfilepath);
        IPPrefix=conf.getString("config.ipprefix");
        serverurl=conf.getString("config.serverurl");

        //type=sdxconfig.type;
        //computeIP(IPPrefix);
        //System.out.print(pemLocation);
        sliceProxy = getSliceProxy(pemLocation,keyLocation, controllerUrl);
        //SSH context
        sctx = new SliceAccessContext<>();
        try {
            SSHAccessTokenFileFactory fac;
            fac = new SSHAccessTokenFileFactory(sshkey+".pub", false);
            SSHAccessToken t = fac.getPopulatedToken();
            sctx.addToken("root", "root", t);
            sctx.addToken("root", t);
        } catch (UtilTransportException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        Slice keyhashslice = null;
        try {
          keyhashslice = Slice.loadManifestFile(sliceProxy, sliceName);
          ComputeNode safe=(ComputeNode)keyhashslice.getResourceByName("safe-server");
          //System.out.println("safe-server managementIP = " + safe.getManagementIP());
          safeserver=safe.getManagementIP()+":7777";
        } catch (ContextTransportException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        } catch (TransportException e) {
          // TODO Auto-generated catch block
              e.printStackTrace();
        }
        //SDNControllerIP="152.3.136.36";
        SDNControllerIP=((ComputeNode)keyhashslice.getResourceByName("plexuscontroller")).getManagementIP();
        //System.out.println("plexuscontroler managementIP = " + SDNControllerIP);
        SDNController=SDNControllerIP+":8080";
        OVSController=SDNControllerIP+":6633";
        configRouting(keyhashslice,OVSController,SDNController,"(c\\d+)","(sp-c\\d+.*)");

        System.out.println("starting sdx server");

        SdxManager.startSdxServer(IPPrefix, serverurl, safeserver, SDNControllerIP, SDNController, OVSController, safeauth);
        logger.debug("Starting on "+SdxManager.serverurl);
        final HttpServer server = startServer(SdxManager.serverurl);
        logger.debug("Sdx server has started, listening on "+SdxManager.serverurl);
        System.out.println("Sdx server has started, listening on "+SdxManager.serverurl);
    }
}

