package exoplex.demo.multisdx;

import com.google.inject.AbstractModule;
import com.google.inject.assistedinject.FactoryProvider;
import exoplex.demo.AbstractTestSetting;
import exoplex.demo.AbstractTestSlice;
import exoplex.sdx.core.SdxServerBase;
import exoplex.sdx.core.exogeni.ExoSdxServer;
import exoplex.sdx.slice.SliceManagerFactory;
import exoplex.sdx.slice.exogeni.ExoSliceManager;
import safe.Authority;
import safe.multisdx.AuthorityMockMultiSdx;

public class MultiSdxModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(Authority.class).to(AuthorityMockMultiSdx.class);
    bind(AbstractTestSetting.class).to(MultiSdxSetting.class);
    bind(AbstractTestSlice.class).to(MultiSdxSlice.class);
    bind(SdxServerBase.class).to(ExoSdxServer.class);
    bind(SliceManagerFactory.class).toProvider(FactoryProvider.newFactory(SliceManagerFactory
      .class, ExoSliceManager.class));
  }
}
