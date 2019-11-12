package exoplex.sdx.slice.vfc;

import com.google.inject.AbstractModule;
import com.google.inject.assistedinject.FactoryProvider;
import exoplex.demo.AbstractTestSetting;
import exoplex.demo.singlesdx.SingleSdxSetting;
import exoplex.sdx.core.SdxManagerBase;
import exoplex.sdx.core.vfc.VfcSdxManager;
import exoplex.sdx.slice.SliceManagerFactory;
import safe.Authority;
import safe.sdx.AuthorityMockSingleSdx;

public class VfcModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(AbstractTestSetting.class).to(SingleSdxSetting.class);
    bind(Authority.class).to(AuthorityMockSingleSdx.class);
    bind(SliceManagerFactory.class).toProvider(FactoryProvider.newFactory(SliceManagerFactory
      .class, VfcSliceManager.class));
    bind(SdxManagerBase.class).to(VfcSdxManager.class);
  }
}
