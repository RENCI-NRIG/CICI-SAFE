package exoplex.demo.multisdx;

import exoplex.client.exogeni.ExogeniClientSlice;
import exoplex.common.slice.SliceManager;
import exoplex.common.slice.SiteBase;
import exoplex.common.utils.Exec;
import exoplex.sdx.core.SliceHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MultiSdxSlice {
  final Logger logger = LogManager.getLogger(Exec.class);

  public MultiSdxSlice() {
  }

  public static void main(String[] args){
    MultiSdxSlice multiSdxSlice = new MultiSdxSlice();
    multiSdxSlice.createSdxSlices(null);
  }

  public void createClientSlices() {
    ArrayList<Thread> tlist = new ArrayList<>();
    for (String clientSlice : MultiSdxSetting.clientSlices) {
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
    ExogeniClientSlice cs = new ExogeniClientSlice(MultiSdxSetting.clientArgs);
    int times=0;
    while (true) {
      try {
        cs.run(clientSlice, MultiSdxSetting.clientIpMap.get(clientSlice),
          SiteBase.get(MultiSdxSetting.clientSiteMap.get(clientSlice)));
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
    ExogeniClientSlice cs  = new ExogeniClientSlice(MultiSdxSetting.clientArgs);
    for(String clientSlice: MultiSdxSetting.clientSlices) {
      cs.setSliceName(clientSlice);
      cs.delete();
    }
  }

  public void createSdxSlices(String riakIP){
    ArrayList<Thread> tlist = new ArrayList<>();
    for(int i = 0; i < MultiSdxSetting.sdxSliceNames.size(); i++){
      final String sliceName = MultiSdxSetting.sdxSliceNames.get(i);
      final String configFile = MultiSdxSetting.sdxConfs.get(i);
      //Set SDX sites here
      final List<String> clientSites = Arrays.asList(MultiSdxSetting.sdxSites.get(i));

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
    for(int i = 0; i < MultiSdxSetting.sdxSliceNames.size(); i++) {
      final String sliceName = MultiSdxSetting.sdxSliceNames.get(i);
      final String configFile = MultiSdxSetting.sdxConfs.get(i);
      //Set SDX sites here
      final ArrayList<String> clientSites = null;
      SliceHelper sliceHelper = new SliceHelper();
      sliceHelper.initializeExoGENIContexts(configFile);
      sliceHelper.setSliceName(sliceName);
      sliceHelper.delete();
    }
  }

  private void createAndConfigSdxSlice(String sliceName, String configFile, String riakIP,
    List<String>
    clientSites) throws Exception{
    SliceHelper sliceHelper = new SliceHelper();
    sliceHelper.initializeExoGENIContexts(configFile);
    if(riakIP != null) {
      sliceHelper.setRiakIP(riakIP);
    }
    if(sliceName != null) {
      sliceHelper.setSliceName(sliceName);
    }
    if (clientSites != null){
      sliceHelper.setClientSites(clientSites);
    }
    SliceManager slice = sliceHelper.createCarrierSlice(sliceHelper.getSliceName(), 2, 100000000);
    slice.commitAndWait();
    sliceHelper.resetHostNames(slice);
    sliceHelper.checkSdxPrerequisites(slice);
  }
}

