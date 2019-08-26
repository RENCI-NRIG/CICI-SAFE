package exoplex.sdx.core;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import exoplex.demo.singlesdx.SingleSdxModule;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.moxy.json.MoxyJsonConfig;
import org.glassfish.jersey.moxy.json.MoxyJsonFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.renci.ahab.libtransport.util.TransportException;

import javax.ws.rs.ext.ContextResolver;
import java.io.IOException;
import java.net.URI;


/**
 * Main class.
 */
public class SdxServer {
  final static Logger logger = LogManager.getLogger(SdxServer.class);
  final Provider<SdxManager> sdxManagerProvider;

  @Inject
  public SdxServer(Provider<SdxManager> sdxManagerProvider) {
    this.sdxManagerProvider = sdxManagerProvider;
  }

  public static void main(String[] args) throws Exception {
    Injector injector = Guice.createInjector(new SingleSdxModule());
    SdxServer sdxServer = injector.getProvider(SdxServer.class).get();
    sdxServer.run(new CoreProperties(args));
  }

  public HttpServer startServer(URI uri) {
      final MoxyJsonConfig moxyJsonConfig = new MoxyJsonConfig();
      final ContextResolver jsonConfigResolver = moxyJsonConfig.resolver();

    // create a resource config that scans for JAX-RS resources and providers
    // in com.example package
    final ResourceConfig rc = new ResourceConfig().packages("exoplex.sdx.core")
            .register(MoxyJsonFeature.class)
            .register(jsonConfigResolver);
    // create and start a new instance of grizzly http server
    // exposing the Jersey application at BASE_URI
    return GrizzlyHttpServerFactory.createHttpServer(uri, rc);
  }

  public SdxManager run(CoreProperties coreProperties) throws
    Exception {
    System.out.println("starting exoplex.sdx server");
    SdxManager sdxManager = sdxManagerProvider.get();
    sdxManager.startSdxServer(coreProperties);
    URI uri = URI.create(coreProperties.getServerUrl());
    RestService.registerSdxManager(uri.getPort(), sdxManager);
    logger.debug("Starting on " + coreProperties.getServerUrl());
    final HttpServer server = startServer(uri);
    RestService.registerHttpServer(server);
    logger.debug("Sdx server has started, listening on " + coreProperties.getServerUrl());
    System.out.println("Sdx server has started, listening on " + coreProperties.getServerUrl());
    return sdxManager;
  }
}

