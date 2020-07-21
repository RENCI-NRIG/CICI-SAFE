package exoplex.sdx.network;

import com.google.inject.Guice;
import com.google.inject.Injector;
import exoplex.common.utils.ServerOptions;
import exoplex.sdx.core.exogeni.ExoSdxManager;
import exoplex.demo.multisdx.MultiSdxModule;
import org.apache.commons.cli.CommandLine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.FileReader;
import java.lang.reflect.Method;

public class RoutingManagerTest {
  final static Logger logger = LogManager.getLogger(RoutingManagerTest.class);
  final static String log = "/Users/yaoyj11/SAFE/exoplex/log/routing-manager.log";


  public static void main(String args[]) {
    try {
      Injector injector = Guice.createInjector(new MultiSdxModule());
      Class NetM = Class.forName("exoplex.sdx.network.RoutingManager");
      Object obj = NetM.newInstance();
      ExoSdxManager exoSdxManager = injector.getInstance(ExoSdxManager.class);
      CommandLine cmd = ServerOptions.parseCmd(new String[]{"-c", "config/sdx.conf"});
      exoSdxManager.readConfig(cmd.getOptionValue("config"));
      exoSdxManager.loadSlice();
      exoSdxManager.initializeSdx();
      Method configRouting = exoSdxManager.getClass().getDeclaredMethod("configRouting");
      configRouting.setAccessible(true);
      configRouting.invoke(exoSdxManager);
      exoSdxManager.delFlows();
      exoSdxManager.restartPlexus();
      try {
        Thread.sleep(10000);
      } catch (Exception e) {

      }

      try (BufferedReader br = new BufferedReader(new FileReader(log))) {
        String line;
        while ((line = br.readLine()) != null) {
          String[] parts = line.replace("\n", "").split(" ");
          Class[] params = new Class[parts.length - 1];
          for (int i = 0; i < params.length; i++) {
            params[i] = String.class;
          }
          Method method = NetM.getDeclaredMethod(parts[0], params);
          String[] p = new String[params.length];
          for (int j = 0; j < params.length; j++) {
            p[j] = parts[j + 1];
          }
          method.invoke(obj, p);
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
      //((RoutingManager)obj).deleteAllFlows(sdxManager.getSDNController());

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Test
  public void testRoutingManager() {
    RoutingManager routingManager = new RoutingManager();
    routingManager.newRouter("c0", "0", null, null);
    routingManager.newRouter("c1", "1", null, null);
    routingManager.newExternalLink("stitch0", "192.168.10.1/24",
      "c0", "192.168.10.2");
    routingManager.newExternalLink("stitch1", "192.168.20.1/24",
      "c1", "192.168.20.2");
    routingManager.newInternalLink("clink0", "192.168.128.1/24", "c0", "192" +
      ".168.128.2/24", "c1", 10);
    routingManager.configurePath("192.168.10.1/24", "c0", "192.168.20.1/24",
     "c1",
      "192.168.10.2", 1);
    routingManager.configurePath("192.168.20.1/24", "c1", "192.168.20.1/24",
      "c0",
      "192.168.20.2", 1);
    logger.info("end");
  }

  @Test
  public void testRoutingManager1() {
    RoutingManager routingManager = new RoutingManager();
    routingManager.newRouter("c0", "0", null, null);
    routingManager.newExternalLink("stitch0", "192.168.10.1/24",
      "c0", "192.168.10.2");
    routingManager.newExternalLink("stitch1", "192.168.20.1/24",
      "c0", "192.168.20.2");
    routingManager.configurePath("192.168.10.1/24", "c0", "192.168.20.1/24",
      "c0",
      "192.168.10.2", 1);
  }

}
