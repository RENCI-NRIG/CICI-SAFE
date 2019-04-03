package injection;

import com.google.inject.AbstractModule;
import safe.Authority;
import safe.sdx.AuthorityMockSdx;

public class TridentTestModule extends AbstractModule {

  @Override
  protected void configure() {
    bind(Authority.class).to(AuthorityMockSdx.class);
  }
}
