package exoplex;

import com.google.inject.Guice;
import com.google.inject.Injector;
import exoplex.common.utils.ServerOptions;
import exoplex.sdx.core.SliceHelper;
import exoplex.sdx.network.RoutingManager;
import exoplex.sdx.safe.SafeManager;
import exoplex.sdx.slice.SliceManager;
import exoplex.sdx.slice.exogeni.SiteBase;
import injection.ExoGeniSliceModule;
import org.apache.commons.cli.CommandLine;

public class DockerServerSlice extends SliceHelper {

  public DockerServerSlice() {
    super(null);
  }

  public static void main(String[] args) throws Exception {
    if (args.length < 1) {
      args = new String[]{"-c", "config/docker.conf"};
    }
    Injector injector = Guice.createInjector(new ExoGeniSliceModule());
    DockerServerSlice slice = injector.getInstance(DockerServerSlice.class);
    slice.run(args);
  }

  public void run(String[] args) {
    if (args.length < 1) {
      args = new String[]{"-c", "config/docker.conf"};
    }
    CommandLine cmd = ServerOptions.parseCmd(args);
    this.readConfig(cmd.getOptionValue("config"));
    try {
      createSafeAndPlexusSlice();
    } catch (Exception e) {

    }
  }

  public void createSafeAndPlexusSlice() throws Exception {
    SliceManager s = sliceManagerFactory.create(sliceName, pemLocation, keyLocation, controllerUrl,
      sshKey);
    s.createSlice();
    s.addSafeServer(SiteBase.get("BBN"), conf.getString("config.riak"), SafeManager
      .getSafeDockerImage(), SafeManager.getSafeServerScript());
    s.addPlexusController(SiteBase.get("BBN"), "plexus");
    s.commitAndWait();
    s.reloadSlice();
    checkSafeServer(s.getManagementIP("safe-server"), riakIp);
    checkPlexus(s, s.getManagementIP("plexus"), RoutingManager.plexusImage);
    System.out.println(String.format("Safe server IP %s", s.getManagementIP("safe-server")));
    System.out.println(String.format("plexus server IP %s", s.getManagementIP("plexus")));
  }
}
