package exoplex.demo.vfc;

import com.google.inject.AbstractModule;
import com.google.inject.assistedinject.FactoryProvider;
import exoplex.demo.AbstractTestSetting;
import exoplex.demo.AbstractTestSlice;
import exoplex.sdx.slice.SliceManagerFactory;
import exoplex.sdx.slice.exogeni.ExoSliceManager;
import safe.Authority;
import safe.sdx.AuthorityMockSingleSdx;

public class VfcClientModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(Authority.class).to(AuthorityMockSingleSdx.class);
    bind(AbstractTestSetting.class).to(VfcSdxSetting.class);
    bind(AbstractTestSlice.class).to(VfcSdxSlice.class);
    bind(SliceManagerFactory.class).toProvider(FactoryProvider.newFactory(SliceManagerFactory
      .class, ExoSliceManager.class));
  }
}
