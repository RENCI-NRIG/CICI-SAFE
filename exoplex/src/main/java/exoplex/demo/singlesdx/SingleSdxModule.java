package exoplex.demo.singlesdx;

import com.google.inject.AbstractModule;
import com.google.inject.assistedinject.FactoryProvider;
import exoplex.demo.AbstractTestSetting;
import exoplex.demo.AbstractTestSlice;
import exoplex.sdx.core.SdxManagerBase;
import exoplex.sdx.core.SdxServerBase;
import exoplex.sdx.core.exogeni.ExoSdxManager;
import exoplex.sdx.core.exogeni.ExoSdxServer;
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
    bind(SdxServerBase.class).to(ExoSdxServer.class);
    bind(SdxManagerBase.class).to(ExoSdxManager.class);
    bind(SliceManagerFactory.class).toProvider(FactoryProvider.newFactory(SliceManagerFactory
      .class, ExoSliceManager.class));
  }
}
