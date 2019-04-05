package injection;

import com.google.inject.AbstractModule;
import exoplex.demo.AbstractTestSetting;
import exoplex.demo.AbstractTestSlice;
import exoplex.demo.multisdx.MultiSdxSDSetting;
import exoplex.demo.multisdx.MultiSdxSlice;
import safe.Authority;
import safe.multisdx.AuthorityMockMultiSdx;

public class MultiSdxSDModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(Authority.class).to(AuthorityMockMultiSdx.class);
    bind(AbstractTestSetting.class).to(MultiSdxSDSetting.class);
    bind(AbstractTestSlice.class).to(MultiSdxSlice.class);
  }
}
