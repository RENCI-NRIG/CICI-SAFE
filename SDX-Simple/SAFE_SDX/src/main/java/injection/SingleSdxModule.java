package injection;

import com.google.inject.AbstractModule;
import exoplex.demo.AbstractTestSetting;
import exoplex.demo.AbstractTestSlice;
import exoplex.demo.singlesdx.SingleSdxSetting;
import exoplex.demo.singlesdx.SingleSdxSlice;
import safe.Authority;
import safe.sdx.AuthorityMockSingleSdx;

public class SingleSdxModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(Authority.class).to(AuthorityMockSingleSdx.class);
    bind(AbstractTestSetting.class).to(SingleSdxSetting.class);
    bind(AbstractTestSlice.class).to(SingleSdxSlice.class);
  }
}
