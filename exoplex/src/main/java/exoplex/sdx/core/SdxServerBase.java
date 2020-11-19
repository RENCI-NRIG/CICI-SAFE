package exoplex.sdx.core;

import org.glassfish.grizzly.http.server.HttpServer;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.net.URI;

public class SdxServerBase {
  public SdxServerBase() {
  }

  public HttpServer startServer(URI uri) {
    return null;
  }

  public SdxManagerBase run(CoreProperties coreProperties) throws
    Exception {
    throw new NotImplementedException();
  }
}
