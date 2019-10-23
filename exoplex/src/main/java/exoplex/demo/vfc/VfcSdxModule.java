package exoplex.demo.vfc;

import com.google.inject.AbstractModule;
import com.google.inject.assistedinject.FactoryProvider;
import exoplex.demo.AbstractTestSetting;
import exoplex.demo.AbstractTestSlice;
import exoplex.sdx.core.SdxServerBase;
import exoplex.sdx.core.vfc.VfcSdxServer;
import exoplex.sdx.slice.SliceManagerFactory;
import exoplex.sdx.slice.vfc.VfcSliceManager;
import safe.Authority;
import safe.sdx.AuthorityMockSingleSdx;

public class VfcSdxModule extends AbstractModule {

  @Override
  protected void configure() {
    bind(Authority.class).to(AuthorityMockSingleSdx.class);
    bind(AbstractTestSetting.class).to(VfcSdxSetting.class);
    bind(AbstractTestSlice.class).to(VfcSdxSlice.class);
    bind(SdxServerBase.class).to(VfcSdxServer.class);
    bind(SliceManagerFactory.class).toProvider(FactoryProvider.newFactory(SliceManagerFactory
      .class, VfcSliceManager.class));
  }
}
