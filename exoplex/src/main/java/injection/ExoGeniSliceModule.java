package injection;

import com.google.inject.AbstractModule;
import com.google.inject.assistedinject.FactoryProvider;
import exoplex.sdx.slice.SliceManagerFactory;
import exoplex.sdx.slice.exogeni.ExoSliceManager;

public class ExoGeniSliceModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(SliceManagerFactory.class).toProvider(FactoryProvider.newFactory(SliceManagerFactory
      .class, ExoSliceManager.class));
  }
}
