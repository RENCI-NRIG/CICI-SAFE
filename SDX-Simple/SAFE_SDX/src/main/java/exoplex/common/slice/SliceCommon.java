package exoplex.common.slice;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.commons.cli.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.renci.ahab.libndl.resources.request.*;
import org.renci.ahab.libtransport.*;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import exoplex.sdx.network.Link;


public abstract class SliceCommon {
  protected final String RequestResource = null;
  final Logger logger = LogManager.getLogger(SliceCommon.class);
  protected String controllerUrl;
  protected String SDNControllerIP;
  protected String sliceName;
  protected String pemLocation;
  protected String keyLocation;
  protected String sshkey;
  public String serverurl;
  protected ISliceTransportAPIv1 sliceProxy;
  protected SliceAccessContext<SSHAccessToken> sctx;
  protected String type;
  protected String topofile = null;
  protected Config conf;
  protected CommandLine cmd;
  protected ArrayList<String> clientSites;
  protected String controllerSite;
  protected List<String> sitelist;
  protected String serverSite;
  protected String safeServer;
  protected String safeServerIp;
  protected String safeKeyHash;
  protected boolean safeEnabled = false;
  protected String riakIp = null;
  protected HashMap<String, Link> links = new HashMap<String, Link>();
  protected HashMap<String, ArrayList<String>> computenodes = new HashMap<String, ArrayList<String>>();
  protected ArrayList<StitchPort> stitchports = new ArrayList<>();
  private String topodir = null;

  public String getSDNControllerIP() {
    return SDNControllerIP;
  }

  protected CommandLine parseCmd(String[] args) {
    Options options = new Options();
    Option config = new Option("c", "config", true, "configuration file path");
    Option config1 = new Option("d", "delete", false, "delete the slice");
    Option config2 = new Option("e", "exec", true, "command to exec");
    Option config3 = new Option("r", "reset", false, "command to exec");
    config.setRequired(true);
    config1.setRequired(false);
    config2.setRequired(false);
    config3.setRequired(false);
    options.addOption(config);
    options.addOption(config1);
    options.addOption(config2);
    options.addOption(config3);
    CommandLineParser parser = new DefaultParser();
    HelpFormatter formatter = new HelpFormatter();
    CommandLine cmd = null;

    try {
      cmd = parser.parse(options, args);
    } catch (ParseException e) {
      logger.error(e.getMessage());
      formatter.printHelp("utility-name", options);

      System.exit(1);
      return cmd;
    }

    return cmd;
  }

  public String getSliceName() {
    return sliceName;
  }

  protected void readConfig(String configfilepath) {
    File myConfigFile = new File(configfilepath);
    Config fileConfig = ConfigFactory.parseFile(myConfigFile);
    conf = ConfigFactory.load(fileConfig);
    type = conf.getString("config.type");
    if (conf.hasPath("config.exogenism")) {
      controllerUrl = conf.getString("config.exogenism");
    }
    serverurl = conf.getString("config.serverurl");
    if (conf.hasPath("config.exogenipem")) {
      pemLocation = conf.getString("config.exogenipem");
      keyLocation = conf.getString("config.exogenipem");
    }
    if (conf.hasPath("config.sshkey")) {
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
    if(conf.hasPath("conf.riak")){
      riakIp = conf.getString("config.riak");
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
    if(conf.hasPath("config.safe")){
      safeEnabled = conf.getBoolean("config.safe");
    }
    if(conf.hasPath("config.safekey")){
      safeKeyHash = conf.getString("config.clientsites");
    }
  }


  protected ArrayList<Link> readLinks(String file) {
    ArrayList<Link> res = new ArrayList<>();
    try (BufferedReader br = new BufferedReader(new FileReader(file))) {
      String line;
      while ((line = br.readLine()) != null) {
        // process the line.
        String[] params = line.replace("\n", "").split(" ");
        Link logLink = new Link();
        logLink.setName(params[0]);
        logLink.addNode(params[1]);
        logLink.addNode(params[2]);
        logLink.setCapacity(Long.parseLong(params[3]));
        res.add(logLink);
      }
      br.close();
    } catch (Exception e) {
      logger.error(e.getMessage());
    }
    return res;
  }

  protected boolean isValidLink(String key) {
    Link logLink = links.get(key);
    if (!key.contains("stitch") && !key.contains("blink") && logLink != null && logLink.getInterfaceB() != null) {
      return true;
    } else {
      return false;
    }
  }

  protected void writeLinks(String file) {
    ArrayList<Link> res = new ArrayList<>();
    try (BufferedWriter br = new BufferedWriter(new FileWriter(file))) {
      Set<String> keyset = links.keySet();
      for (String key : keyset) {
        if (isValidLink(key)) {
          Link logLink = links.get(key);

          br.write(logLink.getLinkName() + " " + logLink.getNodeA() + " " + logLink.getNodeB() + " " + String.valueOf
              (logLink.getCapacity()) + "\n");
        }
      }
      br.close();
    } catch (Exception e) {
      logger.error("Topology not save to file");
    }
  }


  public void sleep(int sec) {
    try {
      Thread.sleep(sec * 1000);                 //1000 milliseconds is one second.
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
  }

  protected void deleteSlice(String sliceName) {
    logger.info(String.format("deleting slice %s", sliceName));
    SafeSlice s2 = new SafeSlice(sliceName, pemLocation, keyLocation, controllerUrl);
    s2.delete();
  }

  public void delete() {
    deleteSlice(sliceName);
  }

  protected boolean patternMatch(String str, String pattern) {
    return Pattern.compile(pattern).matcher(str).matches();
  }

}
