package exoplex.demo.multisdx;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import exoplex.common.utils.Exec;
import exoplex.demo.AbstractTestSetting;
import exoplex.demo.AbstractTestSlice;
import exoplex.sdx.core.SliceHelper;
import injection.MultiSdxModule;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MultiSdxSlice extends AbstractTestSlice {
  final Logger logger = LogManager.getLogger(Exec.class);

  @Inject
  public MultiSdxSlice(Provider<SliceHelper> sliceHelperProvider, AbstractTestSetting testSetting) {
    super(sliceHelperProvider, testSetting);
  }

  public static void main(String[] args) {
    Injector injector = Guice.createInjector(new MultiSdxModule());
    MultiSdxSlice multiSdxSlice = injector.getInstance(MultiSdxSlice.class);
    multiSdxSlice.createSdxSlices(null);
  }
}

