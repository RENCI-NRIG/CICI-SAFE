package exoplex.demo.multisdxsd;

import com.google.inject.AbstractModule;
import com.google.inject.assistedinject.FactoryProvider;
import exoplex.demo.AbstractTestSetting;
import exoplex.demo.AbstractTestSlice;
import exoplex.demo.multisdx.MultiSdxSlice;
import exoplex.sdx.core.SdxServerBase;
import exoplex.sdx.core.exogeni.ExoSdxServer;
import exoplex.sdx.slice.SliceManagerFactory;
import exoplex.sdx.slice.slicemock.SliceManagerMock;
import safe.Authority;
import safe.multisdx.AuthorityMockMultiSdx;

public class MultiSdxSDMockModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(Authority.class).to(AuthorityMockMultiSdx.class);
    bind(AbstractTestSetting.class).to(MultiSdxSDLargeSetting.class);
    bind(AbstractTestSlice.class).to(MultiSdxSlice.class);
    bind(SdxServerBase.class).to(ExoSdxServer.class);
    bind(SliceManagerFactory.class).toProvider(FactoryProvider.newFactory(SliceManagerFactory
      .class, SliceManagerMock.class));
  }
}
