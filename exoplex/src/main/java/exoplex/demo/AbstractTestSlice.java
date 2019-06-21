package exoplex.demo;

import com.google.inject.Inject;
import com.google.inject.Provider;
import exoplex.client.exogeni.ExogeniClientSlice;
import exoplex.common.utils.Exec;
import exoplex.sdx.core.SliceHelper;
import exoplex.sdx.slice.SliceManager;
import exoplex.sdx.slice.exogeni.SiteBase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class AbstractTestSlice {
  public final Provider<SliceHelper> sliceHelperProvider;
  public final Provider<ExogeniClientSlice> exogeniClientSliceProvider;
  public final AbstractTestSetting testSetting;
  final Logger logger = LogManager.getLogger(Exec.class);
  public Long bandwidth = 100000000L;
  public long stitchBw = 400000000l;

  @Inject
  public AbstractTestSlice(Provider<SliceHelper> sliceHelperProvider,
                           Provider<ExogeniClientSlice> exogeniClientSliceProvider,
                           AbstractTestSetting testSetting) {
    this.sliceHelperProvider = sliceHelperProvider;
    this.exogeniClientSliceProvider = exogeniClientSliceProvider;
    this.testSetting = testSetting;
  }

  public void createClientSlices(String riakIp) {
    ArrayList<Thread> tlist = new ArrayList<>();
    for (String clientSlice : testSetting.clientSlices) {
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

  private boolean createClientSlice(String clientSlice, String riakIP) {
    ExogeniClientSlice cs = exogeniClientSliceProvider.get();
    cs.processArgs(testSetting.clientArgs);
    int times = 0;
    while (true) {
      try {
        cs.run(clientSlice, testSetting.clientIpMap.get(clientSlice),
          SiteBase.get(testSetting.clientSiteMap.get(clientSlice)), riakIP);
        break;
      } catch (Exception e) {
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

  public void deleteClientSlices() {
    ExogeniClientSlice cs = exogeniClientSliceProvider.get();
    cs.processArgs(testSetting.clientArgs);
    for (String clientSlice : testSetting.clientSlices) {
      cs.setSliceName(clientSlice);
      cs.deleteSlice();
    }
  }

  public void createSdxSlices(String riakIP) {
    ArrayList<Thread> tlist = new ArrayList<>();
    for (int i = 0; i < testSetting.sdxSliceNames.size(); i++) {
      final String sliceName = testSetting.sdxSliceNames.get(i);
      final String configFile = testSetting.sdxConfs.get(sliceName);
      //Set SDX sites here
      final List<String> clientSites = Arrays.asList(testSetting.sdxSites.get(sliceName));

      Thread t = new Thread() {
        @Override
        public void run() {
          try {
            createAndConfigSdxSlice(sliceName, configFile, riakIP, clientSites);
          } catch (Exception e) {
            e.printStackTrace();
          }
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
      e.printStackTrace();
    }
  }

  public void deleteSdxSlices() {
    for (String sliceName : testSetting.sdxSliceNames) {
      final String configFile = testSetting.sdxConfs.get(sliceName);
      //Set SDX sites here
      SliceHelper sliceHelper = sliceHelperProvider.get();
      sliceHelper.readConfig(configFile);
      sliceHelper.setSliceName(sliceName);
      sliceHelper.deleteSlice();
    }
  }

  private void createAndConfigSdxSlice(String sliceName, String configFile, String riakIP,
                                       List<String>
                                         clientSites) throws Exception {
    SliceHelper sliceHelper = sliceHelperProvider.get();
    sliceHelper.readConfig(configFile);
    if (riakIP != null) {
      sliceHelper.setRiakIP(riakIP);
    }
    if (sliceName != null) {
      sliceHelper.setSliceName(sliceName);
    }
    if (clientSites != null) {
      sliceHelper.setClientSites(clientSites);
    }
    SliceManager slice = sliceHelper.createCarrierSlice(sliceHelper.getSliceName(), clientSites
      .size(), bandwidth);
    slice.commitAndWait();
    sliceHelper.resetHostNames(slice);
    sliceHelper.checkSdxPrerequisites(slice);
  }
}
