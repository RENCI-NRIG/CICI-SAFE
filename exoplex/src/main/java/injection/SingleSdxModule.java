package injection;

import com.google.inject.AbstractModule;
import com.google.inject.assistedinject.FactoryProvider;
import exoplex.demo.AbstractTestSetting;
import exoplex.demo.AbstractTestSlice;
import exoplex.demo.singlesdx.SingleSdxSetting;
import exoplex.demo.singlesdx.SingleSdxSlice;
import exoplex.sdx.slice.SliceManagerFactory;
import exoplex.sdx.slice.exogeni.ExoSliceManager;
import safe.Authority;
import safe.sdx.AuthorityMockSingleSdx;

public class SingleSdxModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(Authority.class).to(AuthorityMockSingleSdx.class);
    bind(AbstractTestSetting.class).to(SingleSdxSetting.class);
    bind(AbstractTestSlice.class).to(SingleSdxSlice.class);
    bind(SliceManagerFactory.class).toProvider(FactoryProvider.newFactory(SliceManagerFactory
      .class, ExoSliceManager.class));
  }
}
