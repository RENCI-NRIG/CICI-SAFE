package exoplex.demo;

import com.google.inject.Inject;
import com.google.inject.Provider;
import exoplex.client.exogeni.ExogeniClientSlice;
import exoplex.common.utils.Exec;
import exoplex.sdx.core.CoreProperties;
import exoplex.sdx.core.exogeni.SliceHelper;
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
  private List<Thread> threadList = new ArrayList<>();

  @Inject
  public AbstractTestSlice(Provider<SliceHelper> sliceHelperProvider,
                           Provider<ExogeniClientSlice> exogeniClientSliceProvider,
                           AbstractTestSetting testSetting) {
    this.sliceHelperProvider = sliceHelperProvider;
    this.exogeniClientSliceProvider = exogeniClientSliceProvider;
    this.testSetting = testSetting;
  }

  public void createClientSlices(String riakIp) {
    for (String clientSlice : testSetting.clientSlices) {
      Thread t = new Thread() {
        @Override
        public void run() {
          createClientSlice(clientSlice, riakIp);
        }
      };
      threadList.add(t);
    }
  }

  private boolean createClientSlice(String clientSlice, String riakIP) {
    ExogeniClientSlice cs = exogeniClientSliceProvider.get();
    CoreProperties coreProperties = new CoreProperties(testSetting.clientArgs);
    coreProperties.setIpPrefix(testSetting.clientIpMap.get(clientSlice));
    coreProperties.setRouterSite(SiteBase.get(testSetting.clientSiteMap.get(clientSlice)));
    coreProperties.setRiakIp(riakIP);
    coreProperties.setSliceName(clientSlice);
    coreProperties.setSafeEnabled(testSetting.safeEnabled);
    int times = 0;
    while (true) {
      try {
        cs.run(coreProperties);
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
    CoreProperties coreProperties = new CoreProperties(testSetting.clientArgs);
    coreProperties.setType("delete");
    for (String clientSlice : testSetting.clientSlices) {
      coreProperties.setSliceName(clientSlice);
      try {
        cs.run(coreProperties);
      } catch (Exception e) {

      }
    }
  }

  public void createSdxSlices(String riakIP) {
    for (int i = 0; i < testSetting.sdxSliceNames.size(); i++) {
      final String sliceName = testSetting.sdxSliceNames.get(i);
      final String configFile = testSetting.sdxConfs.get(sliceName);
      //Set SDX sites here
      final List<String> clientSites = Arrays.asList(testSetting.sdxSites.get(sliceName));
      CoreProperties coreProperties = new CoreProperties(configFile);
      coreProperties.setSliceName(sliceName);
      coreProperties.setRiakIp(riakIP);
      coreProperties.setClientSites(clientSites);
      coreProperties.setSafeEnabled(testSetting.safeEnabled);

      Thread t = new Thread() {
        @Override
        public void run() {
          try {
            createAndConfigSdxSlice(coreProperties);
          } catch (Exception e) {
            e.printStackTrace();
          }
        }
      };
      threadList.add(t);
    }
  }

  public void runThreads() {
    for (Thread t : threadList) {
      t.start();
    }
    for (Thread t : threadList) {
      try {
        t.join();
      } catch (Exception e) {
        logger.warn(e.getMessage());
      }
    }
    threadList.clear();
  }

  public void deleteSdxSlices() {
    for (String sliceName : testSetting.sdxSliceNames) {
      final String configFile = testSetting.sdxConfs.get(sliceName);
      //Set SDX sites here
      SliceHelper sliceHelper = sliceHelperProvider.get();
      CoreProperties coreProperties = new CoreProperties(configFile);
      coreProperties.setSliceName(sliceName);
      coreProperties.setType("delete");
      try {
        sliceHelper.run(coreProperties);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  private void createAndConfigSdxSlice(CoreProperties coreProperties) throws Exception {
    SliceHelper sliceHelper = sliceHelperProvider.get();
    SliceManager slice = sliceHelper.createCarrierSlice(coreProperties);
    slice.commitAndWait();
    sliceHelper.resetHostNames(slice);
    sliceHelper.checkSdxPrerequisites(slice);
  }
}
