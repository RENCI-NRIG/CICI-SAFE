package injection;

import com.google.inject.AbstractModule;
import com.google.inject.assistedinject.FactoryProvider;
import exoplex.sdx.slice.SliceManagerFactory;
import exoplex.sdx.slice.exogeni.ExoSliceManager;
import safe.Authority;
import safe.sdx.AuthorityMockSdx;

public class TridentTestModule extends AbstractModule {

  @Override
  protected void configure() {
    bind(Authority.class).to(AuthorityMockSdx.class);
    bind(SliceManagerFactory.class).toProvider(FactoryProvider.newFactory(SliceManagerFactory
      .class, ExoSliceManager.class));
  }
}
