package exoplex.sdx.core.exogeni;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import exoplex.demo.singlesdx.SingleSdxModule;
import exoplex.sdx.core.CoreProperties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.moxy.json.MoxyJsonConfig;
import org.glassfish.jersey.moxy.json.MoxyJsonFeature;
import org.glassfish.jersey.server.ResourceConfig;

import javax.ws.rs.ext.ContextResolver;
import java.net.URI;


/**
 * Main class.
 */
public class ExoSdxServer {
  final static Logger logger = LogManager.getLogger(ExoSdxServer.class);
  final Provider<ExoSdxManager> sdxManagerProvider;

  @Inject
  public ExoSdxServer(Provider<ExoSdxManager> sdxManagerProvider) {
    this.sdxManagerProvider = sdxManagerProvider;
  }

  public static void main(String[] args) throws Exception {
    Injector injector = Guice.createInjector(new SingleSdxModule());
    ExoSdxServer exoSdxServer = injector.getProvider(ExoSdxServer.class).get();
    exoSdxServer.run(new CoreProperties(args));
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

  public ExoSdxManager run(CoreProperties coreProperties) throws
    Exception {
    System.out.println("starting exoplex.sdx server");
    ExoSdxManager exoSdxManager = sdxManagerProvider.get();
    exoSdxManager.startSdxServer(coreProperties);
    URI uri = URI.create(coreProperties.getServerUrl());
    RestService.registerSdxManager(uri.getPort(), exoSdxManager);
    logger.debug("Starting on " + coreProperties.getServerUrl());
    final HttpServer server = startServer(uri);
    RestService.registerHttpServer(server);
    logger.debug("Sdx server has started, listening on " + coreProperties.getServerUrl());
    System.out.println("Sdx server has started, listening on " + coreProperties.getServerUrl());
    return exoSdxManager;
  }
}

