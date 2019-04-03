package exoplex.sdx.network;

import com.google.inject.Guice;
import com.google.inject.Injector;
import exoplex.common.utils.ServerOptions;
import exoplex.sdx.core.SdxManager;
import injection.MultiSdxModule;
import org.apache.commons.cli.CommandLine;

import java.io.BufferedReader;
import java.io.FileReader;
import java.lang.reflect.Method;

public class RoutingManagerTest {
  final static String log = "/Users/yaoyj11/SAFE/SDX-Simple/log/routing-manager.log";


  public static void main(String args[]) {
    try {
      Injector injector = Guice.createInjector(new MultiSdxModule());
      Class NetM = Class.forName("exoplex.sdx.network.RoutingManager");
      Object obj = NetM.newInstance();
      SdxManager sdxManager = injector.getInstance(SdxManager.class);
      CommandLine cmd = ServerOptions.parseCmd(new String[]{"-c", "config/sdx.conf"});
      sdxManager.readConfig(cmd.getOptionValue("config"));
      sdxManager.loadSlice();
      sdxManager.initializeSdx();
      Method configRouting = sdxManager.getClass().getDeclaredMethod("configRouting");
      configRouting.setAccessible(true);
      configRouting.invoke(sdxManager);
      sdxManager.delFlows();
      sdxManager.restartPlexus();
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
}
