package exoplex.demo.cnert2019;

import exoplex.client.exogeni.ExogeniClientSlice;
import exoplex.common.slice.SafeSlice;
import exoplex.common.slice.SiteBase;
import exoplex.common.utils.Exec;
import exoplex.common.utils.ServerOptions;
import exoplex.demo.tridentcom.TridentSetting;
import exoplex.sdx.core.SliceManager;
import org.apache.commons.cli.CommandLine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.renci.ahab.libtransport.SSHAccessToken;
import org.renci.ahab.libtransport.SliceAccessContext;
import org.renci.ahab.libtransport.util.SSHAccessTokenFileFactory;
import org.renci.ahab.libtransport.util.UtilTransportException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Cnert2019Slice {
  final Logger logger = LogManager.getLogger(Exec.class);

  public Cnert2019Slice() {
  }

  public static void main(String[] args){
    Cnert2019Slice cnert2019Slice = new Cnert2019Slice();
    cnert2019Slice.createSdxSlices(null);
  }

  public void createClientSlices() {
    ArrayList<Thread> tlist = new ArrayList<>();
    for (String clientSlice : Cnert2019Setting.clientSlices) {
      Thread t = new Thread() {
        @Override
        public void run() {
          createClientSlice(clientSlice);
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

  private boolean createClientSlice(String clientSlice){
    ExogeniClientSlice cs = new ExogeniClientSlice(Cnert2019Setting.clientArgs);
    int times=0;
    while (true) {
      try {
        cs.run(clientSlice, Cnert2019Setting.clientIpMap.get(clientSlice),
          SiteBase.get(Cnert2019Setting.clientSiteMap.get(clientSlice)));
        break;
      } catch (Exception e) {
        try {
          cs.delete();
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

  public void deleteClientSlices(){
    ExogeniClientSlice cs  = new ExogeniClientSlice(Cnert2019Setting.clientArgs);
    for(String clientSlice: TridentSetting.clientSlices) {
      cs.setSliceName(clientSlice);
      cs.delete();
    }
  }

  public void createSdxSlices(String riakIP){
    ArrayList<Thread> tlist = new ArrayList<>();
    for(int i  = 0; i < Cnert2019Setting.sdxSliceNames.size(); i++){
      final String sliceName = Cnert2019Setting.sdxSliceNames.get(i);
      final String configFile = Cnert2019Setting.sdxConfs.get(i);
      //Set SDX sites here
      final List<String> clientSites = Arrays.asList(Cnert2019Setting.sdxSites.get(i));

      Thread t = new Thread(){
        @Override
        public void run(){
          try {
            createAndConfigSdxSlice(sliceName, configFile, riakIP, clientSites);
          }catch (Exception e){

          }
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
      e.printStackTrace();
    }
  }

  public void deleteSdxSlices(){
    for(int i  = 0; i < Cnert2019Setting.sdxSliceNames.size(); i++) {
      final String sliceName = Cnert2019Setting.sdxSliceNames.get(i);
      final String configFile = Cnert2019Setting.sdxConfs.get(i);
      //Set SDX sites here
      final ArrayList<String> clientSites = null;
      SliceManager sliceManager = new SliceManager();
      sliceManager.initializeExoGENIContexts(configFile);
      sliceManager.setSliceName(sliceName);
      sliceManager.delete();
    }
  }

  private void createAndConfigSdxSlice(String sliceName, String configFile, String riakIP,
    List<String>
    clientSites) throws Exception{
    SliceManager sliceManager = new SliceManager();
    sliceManager.initializeExoGENIContexts(configFile);
    if(riakIP != null) {
      sliceManager.setRiakIP(riakIP);
    }
    if(sliceName != null) {
      sliceManager.setSliceName(sliceName);
    }
    if (clientSites != null){
      sliceManager.setClientSites(clientSites);
    }
    SafeSlice slice = sliceManager.createCarrierSlice(sliceManager.getSliceName(), 2, 100000000);
    slice.commitAndWait();
    slice.reloadSlice();
    sliceManager.checkSdxPrerequisites(slice);
  }
}

