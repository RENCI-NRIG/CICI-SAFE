package injection;

import com.google.inject.AbstractModule;
import exoplex.demo.AbstractTestSetting;
import exoplex.demo.AbstractTestSlice;
import exoplex.demo.multisdx.MultiSdxSetting;
import exoplex.demo.multisdx.MultiSdxSlice;
import safe.Authority;
import safe.multisdx.AuthorityMockMultiSdx;

public class MultiSdxModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(Authority.class).to(AuthorityMockMultiSdx.class);
    bind(AbstractTestSetting.class).to(MultiSdxSetting.class);
    bind(AbstractTestSlice.class).to(MultiSdxSlice.class);
  }
}
