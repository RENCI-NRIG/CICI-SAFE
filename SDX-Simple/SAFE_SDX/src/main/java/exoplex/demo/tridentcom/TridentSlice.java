package exoplex.demo.tridentcom;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import exoplex.client.exogeni.ExogeniClientSlice;
import exoplex.common.utils.ServerOptions;
import exoplex.sdx.safe.SafeManager;
import exoplex.sdx.slice.exogeni.SiteBase;
import exoplex.sdx.slice.exogeni.SliceManager;
import injection.TridentTestModule;
import org.apache.commons.cli.CommandLine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import safe.Authority;
import safe.SafeAuthority;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

public class TridentSlice extends TridentSetting {

  final static Logger logger = LogManager.getLogger(TridentSlice.class);


  @Inject
  public TridentSlice(Provider<Authority> authorityProvider) {
    super(authorityProvider);
  }

  public static void main(String[] args) {
    Injector injector = Guice.createInjector(new TridentTestModule());
    TridentSlice tridentSlice = injector.getProvider(TridentSlice.class).get();
    tridentSlice.run(sdxArgs);
    TridentSlice clientSlice = injector.getProvider(TridentSlice.class).get();
    CommandLine cmd = ServerOptions.parseCmd(clientArgs);
    String configFilePath = cmd.getOptionValue("config");
    clientSlice.readConfig(configFilePath);
    clientSlice.deleteClientSlices();
    clientSlice.createClientSlices(clientSlice.riakIp);
  }

  public static void createSlices(String riakIP) {
    Injector injector = Guice.createInjector(new TridentTestModule());
    TridentSlice tridentSlice = injector.getProvider(TridentSlice.class).get();
    tridentSlice.run(sdxArgs, riakIP);
    TridentSlice clientSlice = injector.getProvider(TridentSlice.class).get();
    CommandLine cmd = ServerOptions.parseCmd(clientArgs);
    String configFilePath = cmd.getOptionValue("config");
    clientSlice.readConfig(configFilePath);
    //clientSlice.deleteClientSlices();
    clientSlice.createClientSlices(riakIP);
  }

  public static void deleteTestSlices() {
    //Delete client slices
    Injector injector = Guice.createInjector(new TridentTestModule());
    TridentSlice clientSlice = injector.getProvider(TridentSlice.class).get();
    CommandLine cmd = ServerOptions.parseCmd(clientArgs);
    String configFilePath = cmd.getOptionValue("config");
    clientSlice.readConfig(configFilePath);
    clientSlice.deleteClientSlices();

    //delete SDX slice
    TridentSlice tridentSlice = injector.getProvider(TridentSlice.class).get();
    tridentSlice.run(sdxDelArgs);
  }

  public void run(String[] args, String myRiakIP) {
    CommandLine cmd = ServerOptions.parseCmd(args);
    String configFilePath = cmd.getOptionValue("config");
    this.readConfig(configFilePath);
    if (myRiakIP != null) {
      riakIp = myRiakIP;
    }
    SliceManager slice = null;
    try {
      slice = createTridentTestSlice();
      slice.reloadSlice();
      checkSdxPrerequisites(slice);
    } catch (Exception e) {
    }
  }

  public void initSafeAuthorization(String safeServerIp) {
    SafeAuthority safeAuthority = new SafeAuthority(safeServer, TridentSetting.sdxName, "sdx",
      TridentSetting.clientSlices,
      TridentSetting.clientKeyMap,
      TridentSetting.clientIpMap);
    safeAuthority.initGeniTrustBase();
  }

  private SliceManager createTridentTestSlice() throws Exception {
    ArrayList<String> sites = TridentSetting.sites;
    SliceManager slice = new SliceManager(TridentSetting.sdxName, pemLocation, keyLocation,
      controllerUrl, sshKey);
    slice.createSlice();
    HashMap<String, String> coreRouterMap = new HashMap<>();
    int i = 0;
    for (String site : sites) {
      String coreRouter = "c" + i;
      String edgeRouter = "e" + i;
      String linkName = "elink" + i;
      i++;
      String siteName = SiteBase.get(site);
      slice.addCoreEdgeRouterPair(siteName, coreRouter, edgeRouter, linkName, bw);
      logger.info(siteName);
      coreRouterMap.put(site, coreRouter);
    }
    //add Links between core routers
    for (int n = 0; n < sites.size(); n++) {
      String linkName = "clink" + n;
      if (n > 0) {
        int p = n / 2;
        String pcore = coreRouterMap.get(sites.get(p));
        String core = coreRouterMap.get(sites.get(n));
        slice.addLink(linkName, pcore, core, bw * 2);
      }
    }
    Random rand = new Random();
    if (plexusInSlice) {
      slice.addPlexusController(SiteBase.get(TridentSetting.sites.get(rand.nextInt(TridentSetting
          .sites.size())
        )),
        "plexuscontroller");
      if (safeEnabled) {
        slice.addSafeServer(SiteBase.get(TridentSetting.sites.get(rand.nextInt(TridentSetting.sites
            .size()))),
          riakIp, SafeManager.getSafeDockerImage(), SafeManager.getSafeServerScript());
      }
    }
    slice.commitAndWait();
    return slice;
  }

  private void createClientSlices(String riakIp) {
    ArrayList<Thread> tlist = new ArrayList<>();
    for (String clientSlice : TridentSetting.clientSlices) {
      Thread t = new Thread() {
        @Override
        public void run() {
          createClientSlice(clientSlice, riakIp);
        }
      };
      tlist.add(t);
    }
    for (Thread t : tlist) {
      t.start();
    }
    try {
      for (Thread t : tlist) {
        t.join();
      }
    } catch (Exception e) {

    }
  }

  private boolean createClientSlice(String clientSlice, String riakIp) {
    ExogeniClientSlice cs = new ExogeniClientSlice(clientArgs);
    int times = 0;
    while (true) {
      try {
        cs.run(clientSlice, TridentSetting.clientIpMap.get(clientSlice),
          SiteBase.get(TridentSetting.clientSiteMap.get(clientSlice)), riakIp);
        break;
      } catch (Exception e) {
        try {
          cs.deleteSlice();
        } catch (Exception ex) {
        }
        logger.warn("%s failed" + clientSlice);
        logger.warn(e.getMessage());
        times++;
        if (times == 5) {
          return false;
        }
      }
    }
    return true;
  }

  private void deleteClientSlices() {
    ExogeniClientSlice cs = new ExogeniClientSlice(clientArgs);
    for (String clientSlice : TridentSetting.clientSlices) {
      deleteSlice(clientSlice);
    }
  }
}
