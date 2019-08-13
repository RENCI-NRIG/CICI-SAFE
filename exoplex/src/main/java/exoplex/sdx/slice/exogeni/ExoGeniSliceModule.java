package exoplex.sdx.slice.exogeni;

import com.google.inject.AbstractModule;
import com.google.inject.assistedinject.FactoryProvider;
import exoplex.sdx.slice.SliceManagerFactory;

public class ExoGeniSliceModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(SliceManagerFactory.class).toProvider(FactoryProvider.newFactory(SliceManagerFactory
      .class, ExoSliceManager.class));
  }
}
