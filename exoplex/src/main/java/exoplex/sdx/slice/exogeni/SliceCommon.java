package exoplex.sdx.slice.exogeni;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import exoplex.sdx.network.Link;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;


public abstract class SliceCommon {
  protected final String RequestResource = null;
  final Logger logger = LogManager.getLogger(SliceCommon.class);
  public String serverurl;
  public boolean safeEnabled = false;
  protected String controllerUrl;
  protected String SDNControllerIP;
  protected String sliceName;
  protected String pemLocation;
  protected String keyLocation;
  protected String sshKey;
  protected String type;
  protected String topofile = null;
  protected Config conf;
  protected List<String> clientSites;
  protected String controllerSite;
  protected List<String> sitelist;
  protected String serverSite;
  protected String safeServer;
  protected String safeServerIp;
  protected String safeKeyFile;
  protected String safeKeyHash = null;
  protected boolean plexusInSlice = false;
  protected boolean safeInSlice = false;
  protected String riakIp = null;

  protected HashMap<String, Link> links = new HashMap<String, Link>();
  protected HashMap<String, ArrayList<String>> computenodes = new HashMap<String, ArrayList<String>>();
  protected ArrayList<String> stitchports = new ArrayList<>();
  private String topodir = null;

  public String getSDNControllerIP() {
    return SDNControllerIP;
  }

  public String getSliceName() {
    return sliceName;
  }

  public void readConfig(String configfilepath) {
    File myConfigFile = new File(configfilepath);
    Config fileConfig = ConfigFactory.parseFile(myConfigFile);
    conf = ConfigFactory.load(fileConfig);
    type = conf.getString("config.type");
    if (conf.hasPath("config.exogenism")) {
      controllerUrl = conf.getString("config.exogenism");
    }
    if (conf.hasPath("config.serverurl")) {
      serverurl = conf.getString("config.serverurl");
    }
    if (conf.hasPath("config.exogenipem")) {
      pemLocation = conf.getString("config.exogenipem");
      keyLocation = conf.getString("config.exogenipem");
    }
    if (conf.hasPath("config.sshkey")) {
      sshKey = conf.getString("config.sshkey");
    }
    if (conf.hasPath("config.slicename")) {
      sliceName = conf.getString("config.slicename");
    }
    if (conf.hasPath("config.topodir")) {
      topodir = conf.getString("config.topodir");
      topofile = topodir + sliceName + ".topo";
    }
    if (conf.hasPath("config.serversite")) {
      serverSite = SiteBase.get(conf.getString("config.serversite"));
    }
    if (conf.hasPath("config.riak")) {
      riakIp = conf.getString("config.riak");
    }
    if (conf.hasPath("config.controllersite")) {
      controllerSite = SiteBase.get(conf.getString("config.controllersite"));
    }
    if (conf.hasPath("config.clientsites")) {
      String clientSitesStr = conf.getString("config.clientsites");
      clientSites = new ArrayList<String>();
      for (String site : clientSitesStr.split(":")) {
        clientSites.add(SiteBase.get(site));
      }
    }
    if (conf.hasPath("config.safe")) {
      safeEnabled = conf.getBoolean("config.safe");
    }
    if (conf.hasPath("config.plexusinslice")) {
      plexusInSlice = conf.getBoolean("config.plexusinslice");
    }
    if (conf.hasPath("config.safeinslice")) {
      safeInSlice = conf.getBoolean("config.safeinslice");
    }
    if(!safeInSlice){
      if (conf.hasPath("config.safeserver")) {
        safeServerIp = conf.getString("config.safeserver");
        setSafeServerIp(safeServerIp);
      }
    }
    if (conf.hasPath("config.safekey")) {
      safeKeyFile = conf.getString("config.safekey");
    }
    if (conf.hasPath("config.sitelist")) {
      sitelist = conf.getStringList("config.sitelist");
    }
  }

  protected void setSdnControllerIp(String sdnControllerIp) {
    SDNControllerIP = sdnControllerIp;
  }

  protected void setSafeServerIp(String safeServerIp) {
    this.safeServerIp = safeServerIp;
    safeServer = safeServerIp + ":7777";
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
      logger.debug(e.getMessage());
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
      logger.debug("Topology not save to file");
    }
  }


  public void sleep(int sec) {
    try {
      Thread.sleep(sec * 1000);                 //1000 milliseconds is one second.
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
  }

  protected boolean patternMatch(String str, String pattern) {
    return Pattern.compile(pattern).matcher(str).matches();
  }
}
