package exoplex.demo.singlesdx;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import exoplex.common.utils.Exec;
import exoplex.demo.AbstractTestSetting;
import exoplex.demo.AbstractTestSlice;
import exoplex.sdx.core.SliceHelper;
import injection.SingleSdxModule;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SingleSdxSlice extends AbstractTestSlice {
  final Logger logger = LogManager.getLogger(Exec.class);

  @Inject
  public SingleSdxSlice(Provider<SliceHelper> sliceHelperProvider, AbstractTestSetting testSetting) {
    super(sliceHelperProvider, testSetting);
  }

  public static void main(String[] args) {
    Injector injector = Guice.createInjector(new SingleSdxModule());
    AbstractTestSlice multiSdxSlice = injector.getInstance(AbstractTestSlice.class);
    multiSdxSlice.createSdxSlices(null);
  }
}