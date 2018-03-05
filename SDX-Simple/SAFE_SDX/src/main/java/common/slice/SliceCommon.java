package common.slice;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.File;
import java.io.BufferedWriter;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;
import org.apache.commons.cli.*;
import org.apache.commons.cli.DefaultParser;

import org.renci.ahab.libndl.Slice;
import org.renci.ahab.libndl.resources.request.ComputeNode;
import org.renci.ahab.libndl.resources.request.Interface;
import org.renci.ahab.libndl.resources.request.InterfaceNode2Net;
import org.renci.ahab.libndl.resources.request.Network;
import org.renci.ahab.libndl.resources.request.StitchPort;
import org.renci.ahab.libtransport.ISliceTransportAPIv1;
import org.renci.ahab.libtransport.ITransportProxyFactory;
import org.renci.ahab.libtransport.PEMTransportContext;
import org.renci.ahab.libtransport.SSHAccessToken;
import org.renci.ahab.libtransport.SliceAccessContext;
import org.renci.ahab.libtransport.TransportContext;
import org.renci.ahab.libtransport.util.ContextTransportException;
import org.renci.ahab.libtransport.util.TransportException;
import org.renci.ahab.libtransport.xmlrpc.XMLRPCProxyFactory;

import com.typesafe.config.*;

import common.utils.Exec;
import common.utils.ScpTo;
import sdx.networkmanager.Link;


public abstract class SliceCommon {
  final Logger logger = Logger.getLogger(SliceCommon.class);

  protected final String RequestResource = null;
  protected String controllerUrl;
  protected String SDNControllerIP;
  protected String sliceName;
  protected String pemLocation;
  protected String keyLocation;
  protected String sshkey;
  protected String serverurl;
  protected ISliceTransportAPIv1 sliceProxy;
  protected SliceAccessContext<SSHAccessToken> sctx;
  protected String keyhash;
  protected String type;
  private String topodir=null;
  protected String topofile=null;
  protected Config conf;
  protected ArrayList<String> clientSites;
  protected String controllerSite;
  protected List<String> sitelist;
  protected String serverSite;
  protected HashMap<String, Link> links = new HashMap<String, Link>();
  protected HashMap<String, ArrayList<String>>computenodes=new HashMap<String,ArrayList<String>>();
  protected ArrayList<StitchPort>stitchports=new ArrayList<>();

  public  String getSDNControllerIP() {
    return SDNControllerIP;
  }

  protected CommandLine parseCmd(String[] args) {
    Options options = new Options();
    Option config = new Option("c", "config", true, "configuration file path");
    Option config1 = new Option("d", "delete", false, "delete the slice");
    Option config2 = new Option("e", "exec", true, "command to exec");
    config.setRequired(true);
    config1.setRequired(false);
    config2.setRequired(false);
    options.addOption(config);
    options.addOption(config1);
    options.addOption(config2);
    CommandLineParser parser = new DefaultParser();
    HelpFormatter formatter = new HelpFormatter();
    CommandLine cmd = null;

    try {
      cmd = parser.parse(options, args);
    } catch (ParseException e) {
      System.out.println(e.getMessage());
      formatter.printHelp("utility-name", options);

      System.exit(1);
      return cmd;
    }

    return cmd;
  }

  public String getSliceName(){
    return sliceName;
  }

  protected void readConfig(String configfilepath) {
    File myConfigFile = new File(configfilepath);
    Config fileConfig = ConfigFactory.parseFile(myConfigFile);
    conf = ConfigFactory.load(fileConfig);
    type = conf.getString("config.type");
    if(conf.hasPath("config.exogenism")) {
      controllerUrl = conf.getString("config.exogenism");
    }
    serverurl = conf.getString("config.serverurl");
    if(conf.hasPath("config.exogenipem")) {
      pemLocation = conf.getString("config.exogenipem");
      keyLocation = conf.getString("config.exogenipem");
    }
    if(conf.hasPath("config.sshkey")) {
      sshkey = conf.getString("config.sshkey");
    }
    if (conf.hasPath("config.slicename")) {
      sliceName = conf.getString("config.slicename");
    }
    if (conf.hasPath("config.topodir")) {
      topodir = conf.getString("config.topodir");
      topofile = topodir + sliceName + ".topo";
    }
    if (conf.hasPath("config.serversite")) {
      serverSite = conf.getString("config.serversite");
    }
    if (conf.hasPath("config.controllersite")) {
      controllerSite = conf.getString("config.controllersite");
    }
    if (conf.hasPath("config.clientsites")) {
      String clientSitesStr = conf.getString("config.clientsites");
      clientSites = new ArrayList<String>();
      for (String site : clientSitesStr.split(":")) {
        clientSites.add(site);
      }
    }

  }

  protected void waitTillActive(Slice s) {
    waitTillActive(s, 10);
  }

  protected void waitTillActive(Slice s, int interval) {
    List<String> computeNodes = s.getComputeNodes().stream().map(c -> c.getName()).collect(Collectors.toList());
    List<String> links = s.getBroadcastLinks().stream().map(c -> c.getName()).collect(Collectors.toList());
    computeNodes.addAll(links);
    waitTillActive(s, interval, computeNodes);
  }

  protected  void waitTillActive(Slice s, int interval, List<String> resources) {
    boolean sliceActive = false;
    while (true) {
      s.refresh();
      sliceActive = true;
      logger.debug("\nSlice: " + s.getAllResources());
      for (ComputeNode c : s.getComputeNodes()) {
        logger.debug("Resource: " + c.getName() + ", state: " + c.getState());
        if (resources.contains(c.getName())) {
          if (c.getState() != "Active" || c.getManagementIP() == null) {
            sliceActive = false;
          }
        }
      }
      for (Network l : s.getBroadcastLinks()) {
        logger.debug("Resource: " + l.getName() + ", state: " + l.getState());
        if (resources.contains(l.getName())) {
          if (l.getState() != "Active") {
            sliceActive = false;
          }
        }
      }

      if (sliceActive) break;
      sleep(interval);
    }
    logger.debug("Done");
    for (ComputeNode n : s.getComputeNodes()) {
      logger.debug("ComputeNode: " + n.getName() + ", Managment IP =  " + n.getManagementIP());
    }
  }

  protected  ArrayList<Link> readLinks(String file) {
    ArrayList<Link>res = new ArrayList<>();
    try (BufferedReader br = new BufferedReader(new FileReader(file))) {
      String line;
      while ((line = br.readLine()) != null) {
        // process the line.
        String[] params=line.replace("\n","").split(" ");
        Link link=new Link();
        link.setName(params[0]);
        link.addNode(params[1]);
        link.addNode(params[2]);
        link.setCapacity(Long.parseLong(params[3]));
        res.add(link);
      }
      br.close();
    } catch (Exception e){
      ;
    }
    return res;
  }

  protected boolean isValidLink(String key){
    Link link = links.get(key);
    if(!key.contains("stitch") && !key.contains("blink") && link !=null && link.nodeb != null){
      return true;
    }else{
      return false;
    }
  }

  protected void writeLinks(String file) {
    ArrayList<Link> res = new ArrayList<>();
    try (BufferedWriter br = new BufferedWriter(new FileWriter(file))) {
      Set<String> keyset=links.keySet();
      for (String key : keyset){
        if (isValidLink(key)){
          Link link=links.get(key);

          br.write(link.linkname + " " + link.nodea + " " + link.nodeb + " " + String.valueOf
            (link.capacity)+ "\n");
        }
      }
      br.close();
    } catch (Exception e){
      e.printStackTrace();
    }
  }

  protected Slice getSlice(ISliceTransportAPIv1 sliceProxy, String sliceName) {
    Slice s = null;
    try {
      s = Slice.loadManifestFile(sliceProxy, sliceName);
    } catch (ContextTransportException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (TransportException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return s;
  }

  protected Slice getSlice() {
    Slice s = null;
    try {
      ISliceTransportAPIv1 sliceProxy = getSliceProxy(pemLocation, keyLocation, controllerUrl);
      s = Slice.loadManifestFile(sliceProxy, sliceName);
    } catch (ContextTransportException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (TransportException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return s;
  }

  public void sleep(int sec) {
    try {
      Thread.sleep(sec * 1000);                 //1000 milliseconds is one second.
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
  }

  protected void deleteSlice(String sliceName){
    Slice s2 = null;
    try {
      System.out.println("deleting slice " + sliceName);
      s2 = Slice.loadManifestFile(sliceProxy, sliceName);
      s2.delete();
    } catch (Exception e) {
      System.out.println(e.getMessage());
    }
  }

  public void delete(){
    deleteSlice(sliceName);
  }

  protected void copyFile2Slice(Slice s, String lfile, String rfile, String privkey) {
    ArrayList<Thread> tlist = new ArrayList<Thread>();
    for (ComputeNode c : s.getComputeNodes()) {
      String mip = c.getManagementIP();
      try {
        Thread thread = new Thread() {
          @Override
          public void run() {
            try {
              logger.debug("scp config file to " + mip);
              ScpTo.Scp(lfile, "root", mip, rfile, privkey);
            } catch (Exception e) {
              e.printStackTrace();
            }
          }
        };
        thread.start();
        tlist.add(thread);
      } catch (Exception e) {
        System.out.println("exception when copying config file");
        logger.error("exception when copying config file");
      }
    }
    try {
      for (Thread t : tlist) {
        t.join();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }


  protected void copyFile2Slice(Slice s, String lfile, String rfile, String privkey,
                                       String patn) {
    Pattern pattern = Pattern.compile(patn);
    ArrayList<Thread> tlist = new ArrayList<Thread>();
    for (ComputeNode c : s.getComputeNodes()) {
      Matcher matcher = pattern.matcher(c.getName());
      if (!matcher.find()) {
        continue;
      }
      String mip = c.getManagementIP();
      try {
        Thread thread = new Thread() {
          @Override
          public void run() {
            try {
              logger.debug("scp config file to " + mip);
              ScpTo.Scp(lfile, "root", mip, rfile, privkey);
            } catch (Exception e) {
              e.printStackTrace();
            }
          }
        };
        thread.start();
        tlist.add(thread);
      } catch (Exception e) {
        System.out.println("exception when copying config file");
        logger.error("exception when copying config file");
      }
    }
    try {
      for (Thread t : tlist) {
        t.join();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  protected void runCmdSlice(Set<ComputeNode> nodes, String cmd,
                             boolean repeat, boolean parallel) {
    List<Thread> tlist = new ArrayList<Thread>();

    for (ComputeNode c : nodes) {
      String mip = c.getManagementIP();
      tlist.add(new Thread() {
        @Override
        public void run() {
          try {
            logger.debug(mip + " run commands: " + cmd);
            String res = Exec.sshExec("root", mip, cmd, sshkey)[0];
            while (res.startsWith("error") && repeat) {
              sleep(5);
              res = Exec.sshExec("root", mip, cmd, sshkey)[0];
            }
          } catch (Exception e) {
            System.out.println("exception when running command");
          }
        }
      });
    }

    if (parallel) {
      for (Thread t : tlist)
        t.start();
      for (Thread t : tlist) {
        try {
          t.join();
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    } else {
      for (Thread t : tlist) {
        t.start();
        try {
          t.join();
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    }
  }

  protected void runCmdSlice(Slice s, String cmd, String patn, boolean repeat) {
    Pattern pattern = Pattern.compile(patn);
    runCmdSlice(
      s.getComputeNodes().stream().filter(w -> {
        Matcher matcher = pattern.matcher(w.getName());
        if (!matcher.find()) {
          return false;
        }
        return true;
      }).collect(Collectors.toSet()),
      cmd, repeat, false);
  }

  protected void runCmdSlice(Slice s, final String cmd,
                             final boolean repeat, final boolean parallel) {
    runCmdSlice(s.getComputeNodes().stream().collect(Collectors.toSet()),
                cmd, repeat, parallel);
  }

  protected void runCmdSlice(Slice s, final String cmd,
                             String patn, final boolean repeat, final boolean parallel) {
    Pattern pattern = Pattern.compile(patn);
    runCmdSlice(
      s.getComputeNodes().stream().filter(w -> {
        Matcher matcher = pattern.matcher(w.getName());
        if (!matcher.find()) {
          return false;
        }
        return true;
      }).collect(Collectors.toSet()),
      cmd, repeat, parallel);
  }

  protected ISliceTransportAPIv1 getSliceProxy(String pem, String key, String controllerUrl) {
    ISliceTransportAPIv1 sliceProxy = null;
    try {
      //ExoGENI controller context
      ITransportProxyFactory ifac = new XMLRPCProxyFactory();
      TransportContext ctx = new PEMTransportContext("", pem, key);
      sliceProxy = ifac.getSliceProxy(ctx, new URL(controllerUrl));

    } catch (Exception e) {
      e.printStackTrace();
      assert (false);
    }

    return sliceProxy;
  }

  protected void refreshSliceProxy(){
    sliceProxy = getSliceProxy(pemLocation, keyLocation, controllerUrl);
  }

  protected void getNetworkInfo(Slice s) {
    //getLinks
    for (Network n : s.getLinks()) {
      logger.debug(n.getLabel() + " " + n.getState());
    }
    //getInterfaces
    for (Interface i : s.getInterfaces()) {
      InterfaceNode2Net inode2net = (InterfaceNode2Net) i;
      logger.debug("MacAddr: " + inode2net.getMacAddress());
      logger.debug("GUID: " + i.getGUID());
    }
    for (ComputeNode node : s.getComputeNodes()) {
      System.out.println(node.getName() + node.getManagementIP());
      for (Interface i : node.getInterfaces()) {
        InterfaceNode2Net inode2net = (InterfaceNode2Net) i;
        logger.debug("MacAddr: " + inode2net.getMacAddress());
        logger.debug("GUID: " + i.getGUID());
      }
    }
  }

  protected void printSliceInfo(Slice s) {
    for (Network n : s.getLinks()) {
      System.out.println(n.getLabel() + " " + n.getState());
    }
    //getInterfaces
    for (Interface i : s.getInterfaces()) {
      InterfaceNode2Net inode2net = (InterfaceNode2Net) i;
      System.out.println("MacAddr: " + inode2net.getMacAddress());
      System.out.println("GUID: " + i.getGUID());
    }
    for (ComputeNode node : s.getComputeNodes()) {
      System.out.println(node.getName() + node.getManagementIP());
      for (Interface i : node.getInterfaces()) {
        InterfaceNode2Net inode2net = (InterfaceNode2Net) i;
        System.out.println("MacAddr: " + inode2net.getMacAddress());
        System.out.println("GUID: " + i.getGUID());
      }
    }
  }

  protected boolean patternMatch(String str, String pattern){
    return Pattern.compile(pattern).matcher(str).matches();
  }

  protected String getEchoTimeCMD(){
    return "echo currentMillis:$(/bin/date \"+%s%3N\");";
  }
}
