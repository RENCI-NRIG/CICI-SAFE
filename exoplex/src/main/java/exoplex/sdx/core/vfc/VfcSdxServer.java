package exoplex.sdx.core.vfc;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import exoplex.sdx.core.CoreProperties;
import exoplex.sdx.core.SdxManagerBase;
import exoplex.sdx.core.SdxServerBase;
import exoplex.sdx.slice.vfc.VfcModule;
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
public class VfcSdxServer extends SdxServerBase {
  final static Logger logger = LogManager.getLogger(VfcSdxServer.class);
  VfcSdxManager vfcSdxManager;

  @Inject
  public VfcSdxServer() {
    VfcModule vfcModule = new VfcModule();
    Injector injector = Guice.createInjector(vfcModule);
    vfcSdxManager = (VfcSdxManager) injector.getInstance(SdxManagerBase.class);
  }

  public static void main(String[] args) throws Exception {
    VfcSdxServer vfcSdxServer = new VfcSdxServer();
    vfcSdxServer.run(new CoreProperties(args));
  }

  @Override
  public HttpServer startServer(URI uri) {
    final MoxyJsonConfig moxyJsonConfig = new MoxyJsonConfig();
    final ContextResolver jsonConfigResolver = moxyJsonConfig.resolver();

    // create a resource config that scans for JAX-RS resources and providers
    // in com.example package
    final ResourceConfig rc = new ResourceConfig().packages("exoplex.sdx.core.vfc")
      .register(MoxyJsonFeature.class)
      .register(jsonConfigResolver);
    // create and start a new instance of grizzly http server
    // exposing the Jersey application at BASE_URI
    return GrizzlyHttpServerFactory.createHttpServer(uri, rc);
  }

  @Override
  public SdxManagerBase run(CoreProperties coreProperties) throws
    Exception {
    System.out.println("starting exoplex.sdx server");
    vfcSdxManager.startSdxServer(coreProperties);
    URI uri = URI.create(coreProperties.getServerUrl());
    VfcRestService.registerSdxManager(uri.getPort(), vfcSdxManager);
    logger.debug("Starting on " + coreProperties.getServerUrl());
    final HttpServer server = startServer(uri);
    VfcRestService.registerHttpServer(server);
    logger.debug("Sdx server has started, listening on " + coreProperties.getServerUrl());
    System.out.println("Sdx server has started, listening on " + coreProperties.getServerUrl());
    return vfcSdxManager;
  }
}
