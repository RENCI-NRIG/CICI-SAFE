package sdx.core;

import org.apache.log4j.Logger;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

import sdx.utils.Exec;

import java.io.IOException;
import java.net.URI;


/**
 * Main class.
 *
 */
public class SdxServer {
	  final static Logger logger = Logger.getLogger(Exec.class);	

	
    // Base URI the Grizzly HTTP server will listen on
    /**
     * Starts Grizzly HTTP server exposing JAX-RS resources defined in this application.
     * @return Grizzly HTTP server.
     */
    public static HttpServer startServer(String url) {
        // create a resource config that scans for JAX-RS resources and providers
        // in com.example package
        final ResourceConfig rc = new ResourceConfig().packages("sdx.core");
        // create and start a new instance of grizzly http server
        // exposing the Jersey application at BASE_URI
        return GrizzlyHttpServerFactory.createHttpServer(URI.create(url), rc);
    }

    /**
     * Main method.
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        SdxManager.startSdxServer(args);

        logger.debug("Starting on "+SdxManager.serverurl);
        final HttpServer server = startServer(SdxManager.serverurl);
        logger.debug("Sdx server has started, listening on "+SdxManager.serverurl);
    }
}

