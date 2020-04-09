package exoplex.demo.vfc;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import exoplex.client.exogeni.ExogeniClientSlice;
import exoplex.demo.AbstractTestSetting;
import exoplex.demo.AbstractTestSlice;
import exoplex.demo.singlesdx.SingleSdxModule;
import exoplex.sdx.core.exogeni.SliceHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class VfcSdxSlice extends AbstractTestSlice {
  final Logger logger = LogManager.getLogger(VfcSdxSlice.class);

  @Inject
  public VfcSdxSlice(Provider<SliceHelper> sliceHelperProvider,
                     Provider<ExogeniClientSlice> exogeniClientSliceProvider,
                     AbstractTestSetting testSetting) {
    super(sliceHelperProvider, exogeniClientSliceProvider, testSetting);
  }

  public static void main(String[] args) {
    Injector injector = Guice.createInjector(new SingleSdxModule());
    AbstractTestSlice multiSdxSlice = injector.getInstance(AbstractTestSlice.class);
    multiSdxSlice.createSdxSlices(null);
    multiSdxSlice.runThreads();
  }

}
