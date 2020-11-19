package exoplex;

import com.google.inject.Guice;
import com.google.inject.Injector;
import exoplex.common.utils.ServerOptions;
import exoplex.sdx.core.CoreProperties;
import exoplex.sdx.core.exogeni.SliceHelper;
import exoplex.sdx.slice.SliceManager;
import exoplex.sdx.slice.SliceProperties;
import exoplex.sdx.slice.exogeni.SiteBase;
import exoplex.sdx.slice.exogeni.ExoGeniSliceModule;
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
    SliceManager s = sliceManagerFactory.create(
      coreProperties.getSliceName(),
      coreProperties.getExogeniKey(),
      coreProperties.getExogeniKey(),
      coreProperties.getExogeniSm(),
      coreProperties.getSshKey()
      );
    s.createSlice();
    s.addSafeServer(SiteBase.get("BBN"), coreProperties.getRiakIp(),
      CoreProperties.getSafeDockerImage(), CoreProperties.getSafeServerScript());
    s.addPlexusController(SiteBase.get("BBN"), "plexus");
    s.commitAndWait();
    s.loadSlice();
    checkSafeServer(s.getManagementIP(SliceProperties.SAFESERVER),
      coreProperties.getRiakIp());
    checkPlexus(s, s.getManagementIP("plexus"), CoreProperties.getPlexusImage());
    System.out.println(String.format("Safe server IP %s", s.getManagementIP("safe-server")));
    System.out.println(String.format("plexus server IP %s", s.getManagementIP("plexus")));
  }
}
