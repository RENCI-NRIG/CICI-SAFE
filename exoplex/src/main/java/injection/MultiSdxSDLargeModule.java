package injection;

import com.google.inject.AbstractModule;
import com.google.inject.assistedinject.FactoryProvider;
import exoplex.demo.AbstractTestSetting;
import exoplex.demo.AbstractTestSlice;
import exoplex.demo.multisdx.MultiSdxSlice;
import exoplex.demo.multisdxsd.MultiSdxSDLargeSetting;
import exoplex.sdx.slice.SliceManagerFactory;
import exoplex.sdx.slice.exogeni.ExoSliceManager;
import safe.Authority;
import safe.multisdx.AuthorityMockMultiSdx;

public class MultiSdxSDLargeModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(Authority.class).to(AuthorityMockMultiSdx.class);
    bind(AbstractTestSetting.class).to(MultiSdxSDLargeSetting.class);
    bind(AbstractTestSlice.class).to(MultiSdxSlice.class);
    bind(SliceManagerFactory.class).toProvider(FactoryProvider.newFactory(SliceManagerFactory
      .class, ExoSliceManager.class));
  }
}
