package exoplex;

import exoplex.common.utils.ServerOptions;
import exoplex.sdx.core.SliceHelper;
import exoplex.sdx.safe.SafeManager;
import exoplex.sdx.slice.exogeni.SiteBase;
import exoplex.sdx.slice.exogeni.SliceManager;
import org.apache.commons.cli.CommandLine;

public class DockerServerSlice extends SliceHelper {

  public DockerServerSlice() {
    super(null);
  }

  public static void main(String[] args) throws Exception {
    if (args.length < 1) {
      args = new String[]{"-c", "config/docker.conf"};
    }
    DockerServerSlice slice = new DockerServerSlice();
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
    SliceManager s = new SliceManager(sliceName, pemLocation, keyLocation, controllerUrl,
      sshKey);
    s.createSlice();
    s.addSafeServer(SiteBase.get("BBN"), conf.getString("config.riak"), SafeManager
      .getSafeDockerImage(), SafeManager.getSafeServerScript());
    s.addPlexusController(SiteBase.get("BBN"), "plexus");
    s.commitAndWait();
    s.reloadSlice();
    checkSafeServer(s.getComputeNode("safe-server").getManagementIP(), riakIp);
    checkPlexus(s.getComputeNode("plexus").getManagementIP());
    System.out.println(String.format("Safe server IP %s", s.getComputeNode("safe-server")
      .getManagementIP()));
    System.out.println(String.format("plexus server IP %s", s.getComputeNode("plexus")
      .getManagementIP()));
  }
}
