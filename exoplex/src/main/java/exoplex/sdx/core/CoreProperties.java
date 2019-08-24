package exoplex.sdx.core;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import exoplex.common.utils.ServerOptions;
import exoplex.sdx.slice.exogeni.SiteBase;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


public class CoreProperties {
  static Logger logger = LogManager.getLogger(CoreProperties.class);

  private static String plexusImage = "yaoyj11/plexus-v3";
  private static String safeDockerImage = "safeserver-v8";
  private static String safeServerScript = "sdx-routing.sh";

  private boolean broEnabled = false;

  //site of sdn controller
  private String sdnSite = null;

  //exogeni key
  private String exogeniKey = null;

  //if sdn controller is in slice
  private boolean plexusInSlice = false;

  private String riakIp = null;
  private boolean safeEnabled = false;
  private String safeKeyFile = null;
  private String safeKeyHash = null;

  //ip address of safe server
  private String safeServerIp = null;

  //server url of SDX
  private String serverUrl = null;

  //site of safe server
  private String serverSite = null;
  private String sliceName = null;
  private String sshKey = null;
  private List<String> clientSites = null;
  private boolean safeInSlice = false;
  private String sdnControllerIp = null;
  private String type = null;
  private String resourceDir = null;
  private String scriptsDir = null;
  private String publicUrl = null;
  private String ipPrefix = null;
  private String exogeniSm = null;
  private long bw = 100000000;
  private long broBw = 100000000;
  private String routerSite = null;
  private boolean reset = false;
  private String command = null;

  public CoreProperties() {
  }

  public CoreProperties(String configFilePath) {
    this.readConfig(configFilePath);
  }

  public CoreProperties(JSONObject jsonObject) {

  }

  public CoreProperties(String[] args) {
    CommandLine cmd = ServerOptions.parseCmd(args);
    this.readConfig(cmd.getOptionValue("config"));
    if (cmd.hasOption('r')) {
      this.setReset(true);
    }
    if (cmd.hasOption('d')) {
      this.setType("delete");
    }
    if (cmd.hasOption('e')) {
      this.setCommand(cmd.getOptionValue('e'));
    }
  }

  public static String getSafeDockerImage() {
    return safeDockerImage;
  }

  public static void setSafeDockerImage(String name) {
    safeDockerImage = name;
  }

  public static String getSafeServerScript() {
    return safeServerScript;
  }

  public static void setSafeServerScript(String name) {
    safeServerScript = name;
  }

  public static String getPlexusImage() {
    return plexusImage;
  }

  private void readConfig(String configFilePath) {
    logger.info(String.format("Loading configuration from %s", configFilePath));
    File myConfigFile = new File(configFilePath);
    Config fileConfig = ConfigFactory.parseFile(myConfigFile);
    Config conf = ConfigFactory.load(fileConfig);
    if (conf.hasPath("config.bro")) {
      setBroEnabled(conf.getBoolean("config.bro"));
    }
    if (conf.hasPath("config.bro")) {
      setBroBw(conf.getLong("config.brobw"));
    }
    if (conf.hasPath("config.serverurl")) {
      setServerUrl(conf.getString("config.serverurl"));
    }
    if (conf.hasPath("config.exogenism")) {
      setExogeniSm(conf.getString("config.exogenism"));
    }
    if (conf.hasPath("config.publicurl")) {
      setPublicUrl(conf.getString("config.publicurl"));
    }
    if (conf.hasPath("config.exogenipem")) {
      setExogeniKey(conf.getString("config.exogenipem"));
    }
    if (conf.hasPath("config.sshkey")) {
      sshKey = conf.getString("config.sshkey");
    }
    if (conf.hasPath("config.slicename")) {
      setSliceName(conf.getString("config.slicename"));
    }
    if (conf.hasPath("config.serversite")) {
      setServerSite(SiteBase.get(conf.getString("config.serversite")));
    }
    if (conf.hasPath("config.riak")) {
      setRiakIp(conf.getString("config.riak"));
    }
    if (conf.hasPath("config.controllersite")) {
      setSdnSite(SiteBase.get(conf.getString("config.controllersite")));
    }
    if (conf.hasPath("config.clientsites")) {
      String clientSitesStr = conf.getString("config.clientsites");
      clientSites = new ArrayList<String>();
      for (String site : clientSitesStr.split(":")) {
        clientSites.add(SiteBase.get(site));
      }
    }
    if (conf.hasPath("config.safe")) {
      setSafeEnabled(conf.getBoolean("config.safe"));
    }
    if (conf.hasPath("config.plexusinslice")) {
      setPlexusInSlice(conf.getBoolean("config.plexusinslice"));
    }
    if (!isPlexusInSlice()) {
      if (conf.hasPath("config.plexusserver")) {
        setSdnControllerIp(conf.getString("config.plexusserver"));
      }
    }
    if (conf.hasPath("config.safeinslice")) {
      setSafeInSlice(conf.getBoolean("config.safeinslice"));
    }
    if (!isSafeInSlice()) {
      if (conf.hasPath("config.safeserver")) {
        setSafeServerIp(conf.getString("config.safeserver"));
      }
    }
    if (conf.hasPath("config.safekey")) {
      setSafeKeyFile(conf.getString("config.safekey"));
    }
    if (conf.hasPath("config.type")) {
      setType(conf.getString("config.type"));
    }
    if (conf.hasPath("config.ipprefix")) {
      setIpPrefix(conf.getString("config.ipprefix"));
    }
    if (conf.hasPath("config.bw")) {
      setBw(conf.getLong("config.bw"));
    }
    if (conf.hasPath("config.scriptsdir")) {
      setScriptsDir(conf.getString("config.scriptsdir"));
    }
    if (conf.hasPath("config.resourcedir")) {
      setResourceDir(conf.getString("config.resourcedir"));
    }
    if (conf.hasPath("config.routersite")) {
      setRouterSite(conf.getString("config.routersite"));
    }
    logger.debug(this.toString());
  }

  public boolean isPlexusInSlice() {
    return plexusInSlice;
  }

  public void setPlexusInSlice(boolean plexusInSlice) {
    this.plexusInSlice = plexusInSlice;
  }

  public String getType() {
    return this.type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public boolean isSafeInSlice() {
    return this.safeInSlice;
  }

  public void setSafeInSlice(boolean safeInSlice) {
    this.safeInSlice = safeInSlice;
  }

  public String getSshKey() {
    return this.sshKey;
  }

  public void setSshKey(String sshKey) {
    this.sshKey = sshKey;
    logger.debug(String.format("%s: %s", "config.sshkey", sshKey));
  }

  public String getExogeniKey() {
    return this.exogeniKey;
  }

  public void setExogeniKey(String exogeniKey) {
    this.exogeniKey = exogeniKey;
    logger.debug(String.format("%s: %s", "config.exogenipem", exogeniKey));
  }

  public String getRiakIp() {
    return this.riakIp;
  }

  public void setRiakIp(String riakIp) {
    this.riakIp = riakIp;
    logger.debug(String.format("riakIp: %s", riakIp));
  }

  public boolean isSafeEnabled() {
    return this.safeEnabled;
  }

  public void setSafeEnabled(boolean safeEnabled) {
    this.safeEnabled = safeEnabled;
  }

  public String getSafeKeyFile() {
    return this.safeKeyFile;
  }

  public void setSafeKeyFile(String safeKeyFile) {
    this.safeKeyFile = safeKeyFile;
  }

  public String getSafeKeyHash() {
    return this.safeKeyHash;
  }

  public void setSafeKeyHash(String safeKeyHash) {
    this.safeKeyHash = safeKeyHash;
  }

  public String getSliceName() {
    return sliceName;
  }

  public void setSliceName(String sliceName) {
    this.sliceName = sliceName;
    logger.debug(String.format("%s: %s", "config.slicename", sliceName));
  }

  public String getSdnControllerIp() {
    return this.sdnControllerIp;
  }

  public void setSdnControllerIp(String sdnControllerIp) {
    this.sdnControllerIp = sdnControllerIp;
  }

  public String getSafeServerIp() {
    return safeServerIp;
  }

  public void setSafeServerIp(String safeServerIp) {
    this.safeServerIp = safeServerIp;
    logger.debug(String.format("%s: %s", "config.safeserver", safeServerIp));
  }

  public String getSafeServer() {
    return safeServerIp + ":7777";
  }

  public String getServerUrl() {
    return this.serverUrl;
  }

  public void setServerUrl(String serverUrl) {
    this.serverUrl = serverUrl;
    logger.debug(String.format("%s: %s", "config.serverurl", serverUrl));
  }

  public String getSdnSite() {
    return this.sdnSite;
  }

  public void setSdnSite(String sdnSite) {
    this.sdnSite = sdnSite;
  }

  public String getServerSite() {
    return this.serverSite;
  }

  public void setServerSite(String serverSite) {
    this.serverSite = serverSite;
    logger.debug(String.format("serverSite: %s", serverSite));
  }

  public String getResourceDir() {
    return this.resourceDir;
  }

  public void setResourceDir(String resourceDir) {
    this.resourceDir = resourceDir;
  }

  public String getPublicUrl() {
    if (this.publicUrl != null) {
      return this.publicUrl;
    } else {
      logger.warn(String.format("config.publicurl not found in configuration file, using %s" +
        " instead\n Sdn controller might not be able to notify new packet to " +
        "sdx", serverUrl));
      return this.serverUrl;
    }
  }

  public void setPublicUrl(String publicUrl) {
    this.publicUrl = publicUrl;
  }

  public long getBw() {
    return this.bw;
  }

  public void setBw(long bw) {
    this.bw = bw;
  }

  public long getBroBw() {
    return this.broBw;
  }

  public void setBroBw(long broBw) {
    this.broBw = broBw;
  }

  public String getIpPrefix() {
    return ipPrefix;
  }

  public void setIpPrefix(String ipPrefix) {
    this.ipPrefix = ipPrefix;
  }

  public List<String> getClientSites() {
    return this.clientSites;
  }

  public void setClientSites(Collection<String> sites) {
    clientSites = new ArrayList<String>();
    for (String site : sites) {
      clientSites.add(SiteBase.get(site));
    }
  }

  public String getScriptsDir() {
    return this.scriptsDir;
  }

  public void setScriptsDir(String scriptsDir) {
    this.scriptsDir = scriptsDir;
  }

  public boolean isBroEnabled() {
    return broEnabled;
  }

  public void setBroEnabled(boolean broEnabled) {
    this.broEnabled = broEnabled;
  }

  public String getExogeniSm() {
    return this.exogeniSm;
  }

  public void setExogeniSm(String exogeniSm) {
    this.exogeniSm = exogeniSm;
  }

  public String getRouterSite() {
    return this.routerSite;
  }

  public void setRouterSite(String routerSite) {
    this.routerSite = SiteBase.get(routerSite);
  }

  public boolean getReset() {
    return this.reset;
  }

  public void setReset(boolean reset) {
    this.reset = reset;
  }

  public String getCommand() {
    return this.command;
  }

  public void setCommand(String cmd) {
    this.command = cmd;
  }

  @Override
  public String toString() {
    return ToStringBuilder.reflectionToString(this);
  }
}

