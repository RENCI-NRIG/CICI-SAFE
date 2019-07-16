package exoplex.sdx.core;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import injection.MultiSdxModule;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.renci.ahab.libtransport.util.TransportException;

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

  public static void main(String[] args) throws IOException, TransportException, Exception {
    System.out.println("starting exoplex.sdx server");
    Injector injector = Guice.createInjector(new MultiSdxModule());
    SdxServer sdxServer = injector.getProvider(SdxServer.class).get();
    sdxServer.run(args);
  }

  public HttpServer startServer(URI uri) {
    // create a resource config that scans for JAX-RS resources and providers
    // in com.example package
    final ResourceConfig rc = new ResourceConfig().packages("exoplex.sdx.core");
    // create and start a new instance of grizzly http server
    // exposing the Jersey application at BASE_URI
    return GrizzlyHttpServerFactory.createHttpServer(uri, rc);
  }

  public SdxManager run(String[] args) throws TransportException, Exception {
    System.out.println("starting exoplex.sdx server");
    SdxManager sdxManager = sdxManagerProvider.get();
    sdxManager.startSdxServer(args);
    URI uri = URI.create(sdxManager.serverurl);
    RestService.registerSdxManager(uri.getPort(), sdxManager);
    logger.debug("Starting on " + sdxManager.serverurl);
    final HttpServer server = startServer(uri);
    logger.debug("Sdx server has started, listening on " + sdxManager.serverurl);
    System.out.println("Sdx server has started, listening on " + sdxManager.serverurl);
    return sdxManager;
  }

  public SdxManager run(String[] args, String url, String sliceName) throws
    TransportException, Exception {
    System.out.println("starting exoplex.sdx server");
    SdxManager sdxManager = sdxManagerProvider.get();
    sdxManager.startSdxServer(args, sliceName);
    sdxManager.serverurl = url;
    URI uri = URI.create(url);
    RestService.registerSdxManager(uri.getPort(), sdxManager);
    logger.debug("Starting on " + url);
    final HttpServer server = startServer(uri);
    RestService.registerHttpServer(server);
    logger.debug("Sdx server has started, listening on " + url);
    System.out.println("Sdx server has started, listening on " + url);
    return sdxManager;
  }
}

