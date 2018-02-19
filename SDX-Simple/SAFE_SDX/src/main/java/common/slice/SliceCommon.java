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
  protected ISliceTransportAPIv1 sliceProxy;
  protected SliceAccessContext<SSHAccessToken> sctx;
  protected String safeserver;
  protected String keyhash;
  protected String type;
  private String topodir=null;
  protected String topofile=null;
  protected Config conf;
  protected ArrayList<String> clientSites;
  protected String controllerSite;
  protected List<String> sitelist;
  protected String serverSite;
  protected boolean safeauth = false;
  protected HashMap<String, Link> links = new HashMap<String, Link>();
  protected HashMap<String, ArrayList<String>>computenodes=new HashMap<String,ArrayList<String>>();
  protected ArrayList<StitchPort>stitchports=new ArrayList<>();


  public SliceCommon() {
  }

  public  String getSDNControllerIP(){
    return SDNControllerIP;
  }
  protected CommandLine parseCmd(String[] args) {
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
    CommandLine cmd = null;

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

  protected void readConfig(String configfilepath) {

    File myConfigFile = new File(configfilepath);
    Config fileConfig = ConfigFactory.parseFile(myConfigFile);
    conf = ConfigFactory.load(fileConfig);
    type = conf.getString("config.type");
    sshkey = conf.getString("config.sshkey");
    controllerUrl = conf.getString("config.exogenism");
    pemLocation = conf.getString("config.exogenipem");
    keyLocation = conf.getString("config.exogenipem");
    if (conf.hasPath("config.slicename")) {
      sliceName = conf.getString("config.slicename");
    }
    if (conf.hasPath("config.topodir")) {
      topodir = conf.getString("config.topodir");
      topofile = topodir + sliceName + ".topo";
    }
    if (conf.hasPath("config.safekey")) {
      keyhash = conf.getString("config.safekey");
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
    boolean sliceActive = false;
    while (true) {
      s.refresh();
      sliceActive = true;
      logger.debug("");
      logger.debug("Slice: " + s.getAllResources());
      for (ComputeNode c : s.getComputeNodes()) {
        logger.debug("Resource: " + c.getName() + ", state: " + c.getState());
        if (! c.getState().equals("Active") || c.getManagementIP() == null) sliceActive = false;
      }
      for (Network l : s.getBroadcastLinks()) {
        logger.debug("Resource: " + l.getName() + ", state: " + l.getState());
        if (! l.getState().equals("Active")) sliceActive = false;
      }

      if (sliceActive) break;
      sleep(10);
    }
    logger.debug("Done");
    for (ComputeNode n : s.getComputeNodes()) {
      logger.debug("ComputeNode: " + n.getName() + ", Managment IP =  " + n.getManagementIP());
    }
  }

  protected void waitTillActive(Slice s, int interval) {
    boolean sliceActive = false;
    while (true) {
      s.refresh();
      sliceActive = true;
      logger.debug("");
      logger.debug("Slice: " + s.getAllResources());
      for (ComputeNode c : s.getComputeNodes()) {
        logger.debug("Resource: " + c.getName() + ", state: " + c.getState());
        if (! c.getState().equals("Active") || c.getManagementIP() == null) sliceActive = false;
      }
      for (Network l : s.getBroadcastLinks()) {
        logger.debug("Resource: " + l.getName() + ", state: " + l.getState());
        if (! l.getState().equals("Active")) sliceActive = false;
      }

      if (sliceActive) break;
      sleep(interval);
    }
    logger.debug("Done");
    for (ComputeNode n : s.getComputeNodes()) {
      logger.debug("ComputeNode: " + n.getName() + ", Managment IP =  " + n.getManagementIP());
    }
  }

  protected  void waitTillActive(Slice s, List<String> resources) {
    boolean sliceActive = false;
    while (true) {
      s.refresh();
      sliceActive = true;
      logger.debug("");
      logger.debug("Slice: " + s.getAllResources());
      for (ComputeNode c : s.getComputeNodes()) {
        logger.debug("Resource: " + c.getName() + ", state: " + c.getState());
        if (resources.contains(c.getName())) {
          if (c.getState() != "Active" || c.getManagementIP() == null) sliceActive = false;
        }
      }
      for (Network l : s.getBroadcastLinks()) {
        logger.debug("Resource: " + l.getName() + ", state: " + l.getState());
        if (resources.contains(l.getName())) {
          if (l.getState() != "Active") sliceActive = false;
        }
      }

      if (sliceActive) break;
      sleep(10);
    }
    logger.debug("Done");
    for (ComputeNode n : s.getComputeNodes()) {
      logger.debug("ComputeNode: " + n.getName() + ", Managment IP =  " + n.getManagementIP());
    }
  }

  protected  ArrayList<Link> readLinks(String file) {
    ArrayList<Link>res=new ArrayList<>();
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
    }catch (Exception e){
      e.printStackTrace();
    }
    return res;
  }

  protected  void writeLinks(String file) {
    ArrayList<Link>res=new ArrayList<>();
    try (BufferedWriter br = new BufferedWriter(new FileWriter(file))) {
      Set<String> keyset=links.keySet();
      for(String key:keyset){
        if(!key.contains("stitch") && !key.contains("blink")){
          Link link=links.get(key);

          br.write(link.linkname + " " + link.nodea + " " + link.nodeb + " " + String.valueOf
            (link.capacity)+ "\n");
        }
      }
      br.close();
    }catch (Exception e){
      e.printStackTrace();
    }
  }

  protected  Slice getSlice(ISliceTransportAPIv1 sliceProxy, String sliceName) {
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
      e.printStackTrace();
    }
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

  protected void runCmdSlice(Slice s, String cmd, String privkey, boolean repeat) {
    for (ComputeNode c : s.getComputeNodes()) {
      String mip = c.getManagementIP();
      try {
        logger.debug(mip + " run commands:" + cmd);
        //ScpTo.Scp(lfile,"root",mip,rfile,privkey);
        String res = Exec.sshExec("root", mip, cmd, privkey);
        while (res.startsWith("error") && repeat) {
          sleep(5);
          res = Exec.sshExec("root", mip, cmd, privkey);
        }

      } catch (Exception e) {
        logger.debug("exception when copying config file");
      }
    }
  }

  protected void runCmdSlice(Slice s, String cmd, String privkey, String patn, boolean repeat) {
    Pattern pattern = Pattern.compile(patn);
    for (ComputeNode c : s.getComputeNodes()) {
      Matcher matcher = pattern.matcher(c.getName());
      if (!matcher.find()) {
        continue;
      }
      String mip = c.getManagementIP();
      try {
        logger.debug(mip + " run commands:" + cmd);
        String res = Exec.sshExec("root", mip, cmd, privkey);
        while (res.startsWith("error") && repeat) {
          sleep(5);
          res = Exec.sshExec("root", mip, cmd, privkey);
        }

      } catch (Exception e) {
        logger.debug("exception when copying config file");
      }
    }
  }

  protected void runCmdSlice(Slice s, final String cmd, final String privkey,
                                    final boolean repeat, final boolean parallel) {
    if (parallel) {
      ArrayList<Thread> tlist = new ArrayList<Thread>();
      for (ComputeNode c : s.getComputeNodes()) {
        String name = c.getName();
        final String mip = c.getManagementIP();
        try {
          logger.debug(mip + " run commands:" + cmd);
          //ScpTo.Scp(lfile,"root",mip,rfile,privkey);
          Thread thread = new Thread() {
            @Override
            public void run() {
              try {
                String res = Exec.sshExec("root", mip, cmd, privkey);
                while (res.startsWith("error") && repeat) {
                  sleep(5);
                  res = Exec.sshExec("root", mip, cmd, privkey);
                }
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
    } else {
      runCmdSlice(s, cmd, privkey, repeat);
    }
  }

  protected void runCmdSlice(Slice s, final String cmd, final String privkey, String p, final boolean repeat, final boolean parallel) {
    Pattern pattern = Pattern.compile(p);
    if (parallel) {
      ArrayList<Thread> tlist = new ArrayList<Thread>();
      for (ComputeNode c : s.getComputeNodes()) {
        String name = c.getName();
        Matcher matcher = pattern.matcher(name);
        if (matcher.matches()) {
          final String mip = c.getManagementIP();
          try {
            logger.debug(mip + " run commands:" + cmd);
            //ScpTo.Scp(lfile,"root",mip,rfile,privkey);
            Thread thread = new Thread() {
              @Override
              public void run() {
                try {
                  String res = Exec.sshExec("root", mip, cmd, privkey);
                  while (res.startsWith("error") && repeat) {
                    sleep(5);
                    res = Exec.sshExec("root", mip, cmd, privkey);
                  }
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
      }
      try {
        for (Thread t : tlist) {
          t.join();
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    } else {
      runCmdSlice(s, cmd, privkey, p, repeat);
    }
  }


  protected ISliceTransportAPIv1 getSliceProxy(String pem, String key, String controllerUrl) {
    ISliceTransportAPIv1 sliceProxy = null;
    try {
      //ExoGENI controller context
      ITransportProxyFactory ifac = new XMLRPCProxyFactory();
      logger.debug("Opening certificate " + pem + " and key " + key);
      TransportContext ctx = new PEMTransportContext("", pem, key);
      sliceProxy = ifac.getSliceProxy(ctx, new URL(controllerUrl));

    } catch (Exception e) {
      e.printStackTrace();
      System.err.println("Proxy factory test failed");
      assert (false);
    }

    return sliceProxy;
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
      logger.debug(node.getName() + node.getManagementIP());
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

//	protected final ArrayList<String> domains;
//	static {
//		ArrayList<String> l = new ArrayList<String>();
//
//		for (int i = 0; i < 100; i++){
////			l.add("PSC (Pittsburgh, TX, USA) XO Rack");
////			l.add("UAF (Fairbanks, AK, USA) XO Rack");
//		
////			l.add("UH (Houston, TX USA) XO Rack");
//			l.add("TAMU (College Station, TX, USA) XO Rack");
////		l.add("RENCI (Chapel Hill, NC USA) XO Rack");
////			
////			l.add("SL (Chicago, IL USA) XO Rack");
////			
////			
////			l.add("OSF (Oakland, CA USA) XO Rack");
////			
////		l.add("UMass (UMass Amherst, MA, USA) XO Rack");
//			//l.add("WVN (UCS-B series rack in Morgantown, WV, USA)");
//	//		l.add("UAF (Fairbanks, AK, USA) XO Rack");
////   l.add("UNF (Jacksonville, FL) XO Rack");
////		l.add("UFL (Gainesville, FL USA) XO Rack");
////			l.add("WSU (Detroit, MI, USA) XO Rack");
////			l.add("BBN/GPO (Boston, MA USA) XO Rack");
////			l.add("UvA (Amsterdam, The Netherlands) XO Rack");
//
//		}
//		domains = l;
//	}

}
