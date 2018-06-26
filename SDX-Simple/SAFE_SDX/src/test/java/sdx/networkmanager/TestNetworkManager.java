package sdx.networkmanager;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.FileReader;
import java.lang.reflect.Method;

public class TestNetworkManager {
  final static String log = "/Users/yaoyj11/SAFE/SDX-Simple/log/network-manager.log";


  public static void main(String args[]) {
    try {
      Class NetM = Class.forName("sdx.networkmanager.NetworkManager");
      Object obj = NetM.newInstance();

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

    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
