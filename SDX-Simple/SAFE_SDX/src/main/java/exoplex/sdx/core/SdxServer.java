package exoplex.sdx.core;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.renci.ahab.libtransport.util.TransportException;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;


/**
 * Main class.
 */
public class SdxServer {
  final static Logger logger = LogManager.getLogger(SdxServer.class);
  public static HashMap<Integer, SdxManager> sdxManagerMap = new HashMap<>();


  // Base URI the Grizzly HTTP server will listen on

  /**
   * Starts Grizzly HTTP server exposing JAX-RS resources defined in this application.
   *
   * @return Grizzly HTTP server.
   */
  public static HttpServer startServer(URI uri) {
    // create a resource config that scans for JAX-RS resources and providers
    // in com.example package
    final ResourceConfig rc = new ResourceConfig().packages("exoplex.sdx.core");
    // create and start a new instance of grizzly http server
    // exposing the Jersey application at BASE_URI
    return GrizzlyHttpServerFactory.createHttpServer(uri, rc);
  }

  /**
   * Main method.
   *
   * @param args
   * @throws IOException
   */
  public static void main(String[] args) throws IOException, TransportException, Exception {
    System.out.println("starting exoplex.sdx server");
    SdxManager sdxManager = new SdxManager();
    sdxManager.startSdxServer(args);
    URI uri = URI.create(sdxManager.serverurl);
    sdxManagerMap.put(uri.getPort(), sdxManager);
    logger.debug("Starting on " + sdxManager.serverurl);
    final HttpServer server = startServer(uri);
    logger.debug("Sdx server has started, listening on " + sdxManager.serverurl);
    System.out.println("Sdx server has started, listening on " + sdxManager.serverurl);
  }

  public static SdxManager run(String[] args) throws TransportException , Exception{
    System.out.println("starting exoplex.sdx server");
    SdxManager sdxManager = new SdxManager();
    sdxManager.startSdxServer(args);
    URI uri = URI.create(sdxManager.serverurl);
    sdxManagerMap.put(uri.getPort(), sdxManager);
    logger.debug("Starting on " + sdxManager.serverurl);
    final HttpServer server = startServer(uri);
    logger.debug("Sdx server has started, listening on " + sdxManager.serverurl);
    System.out.println("Sdx server has started, listening on " + sdxManager.serverurl);
    return sdxManager;
  }

  public static SdxManager run(String[] args, String url, String sliceName) throws
    TransportException , Exception{
    System.out.println("starting exoplex.sdx server");
    SdxManager sdxManager = new SdxManager();
    sdxManager.startSdxServer(args, sliceName);
    URI uri = URI.create(url);
    sdxManagerMap.put(uri.getPort(), sdxManager);
    logger.debug("Starting on " + url);
    final HttpServer server = startServer(uri);
    logger.debug("Sdx server has started, listening on " + url);
    System.out.println("Sdx server has started, listening on " + url);
    return sdxManager;
  }
}

