package exoplex.demo.tridentcom;

import com.sun.deploy.util.SessionState;
import com.sun.org.apache.xpath.internal.axes.SelfIteratorNoPredicate;
import exoplex.client.exogeni.ClientSlice;
import exoplex.common.slice.NodeBase;
import exoplex.common.slice.SafeSlice;
import exoplex.common.slice.SiteBase;
import exoplex.sdx.core.SliceManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import safe.SafeAuthority;

public class TridentSlice extends SliceManager{

  final static Logger logger = LogManager.getLogger(TridentSlice.class);

  final static String[] clientArgs = new String[]{"-c", "client-config/client.conf"};

  final static String[] sdxArgs = new String[]{"-c", "config/tri.conf"};

  public TridentSlice(){

  }

  public static void main(String[] args){

    /*
    TridentSlice tridentSlice = new TridentSlice();
    tridentSlice.run(sdxArgs);
    */
    TridentSlice clientSlice = new TridentSlice();
    clientSlice.initializeExoGENIContexts(clientArgs);
    clientSlice.deleteClientSlices();
    clientSlice.createClientSlices();
  }

  @Override
  public void run(String[] args){
    parseConfig(args);
    SafeSlice slice = null;
    try {
      slice = createTridentTestSlice();
      slice.reloadSlice();
      checkPrerequisites(slice);
    }catch (Exception e){
    }
  }

  public void initSafeAuthorization(String safeServerIp){
    SafeAuthority safeAuthority = new SafeAuthority(safeServer, TridentSetting.sdxName, "sdx",
      TridentSetting.clientSlices,
      TridentSetting.clientKeyMap,
      TridentSetting.clientIpMap);
    safeAuthority.initGeniTrustBase();
  }

  private SafeSlice createTridentTestSlice() throws Exception{
    ArrayList<String> sites = TridentSetting.sites;
    SafeSlice slice = SafeSlice.create(TridentSetting.sdxName, pemLocation, keyLocation, controllerUrl,
      sctx);
    HashMap<String, String> coreRouterMap = new HashMap<>();
    int i = 0;
    for(String site: sites){
      String coreRouter = "c" + i;
      String edgeRouter = "e" + i;
      String linkName = "elink" + i;
      i++;
      String siteName = SiteBase.get(site);
      slice.addCoreEdgeRouterPair(siteName, coreRouter, edgeRouter, linkName, bw);
      logger.info(siteName);
      coreRouterMap.put(site, coreRouter);
    }
    //add Links
    for(int n = 0; n< sites.size(); n++){
      String linkName = "clink" + n;
      if(n>0){
        int p = n/2;
        String pcore = coreRouterMap.get(sites.get(p));
        String core = coreRouterMap.get(sites.get(n));
        slice.addLink(linkName, pcore, core, bw * 2);
      }
    }
    Random rand = new Random();
    slice.addPlexusController(SiteBase.get(TridentSetting.sites.get(rand.nextInt(TridentSetting
        .sites.size())
      )),
      "plexuscontroller");
    if(safeEnabled){
      slice.addSafeServer(SiteBase.get(TridentSetting.sites.get(rand.nextInt(TridentSetting.sites
          .size()))),
        riakIp);
    }
    slice.commitAndWait();
    return slice;
  }

  private void createClientSlices(){
    ArrayList<Thread> tlist = new ArrayList<>();
    for(String clientSlice: TridentSetting.clientSlices){
      Thread t = new Thread(){
        @Override
        public void run(){
          createClientSlice(clientSlice);
        }
      };
      tlist.add(t);
    }
    for(Thread t:tlist){
      t.start();
    }
    try {
      for (Thread t : tlist) {
        t.join();
      }
    }catch (Exception e){

    }
  }

  private boolean createClientSlice(String clientSlice){
    ClientSlice cs = new ClientSlice(clientArgs);
    int times=0;
    while (true) {
      try {
        cs.run(clientSlice, TridentSetting.clientIpMap.get(clientSlice),
            SiteBase.get(TridentSetting.clientSiteMap.get(clientSlice)));
        break;
      } catch (Exception e) {
        try {
          deleteSlice(clientSlice);
        }catch (Exception ex){}
        logger.warn("%s failed" + clientSlice);
        logger.warn(e.getMessage());
        times++;
        if(times==5){
          return false;
        }
      }
    }
    return true;
  }

  private void deleteClientSlices(){
    ClientSlice cs  = new ClientSlice(clientArgs);
    for(String clientSlice: TridentSetting.clientSlices) {
      deleteSlice(clientSlice);
    }
  }
}
