package riak;

import exoplex.common.utils.ServerOptions;
import exoplex.sdx.slice.Scripts;
import exoplex.sdx.slice.exogeni.SliceCommon;
import exoplex.sdx.slice.exogeni.SliceManager;
import org.apache.commons.cli.CommandLine;

public class RiakSlice extends SliceCommon {
  public static void main(String[] args) throws Exception {
    if (args.length < 1) {
      args = new String[]{"-c", "config/riak.conf"};
    }
    RiakSlice slice = new RiakSlice();
    slice.run(args);
  }

  public String run(String[] args) throws Exception {
    if (args.length < 1) {
      args = new String[]{"-c", "config/riak.conf"};
    }
    CommandLine cmd = ServerOptions.parseCmd(args);
    readConfig(cmd.getOptionValue("config"));
    //SSH context
    if (cmd.hasOption('d')) {
      return deleteRiakSlice();
    }
    return createRiakSlice();
  }

  public String createRiakSlice() throws Exception {
    SliceManager s = new SliceManager(sliceName, pemLocation, keyLocation, controllerUrl,
      sshKey);
    s.createSlice();
    s.addRiakServer(serverSite, "riak");
    s.commitAndWait();
    s.reloadSlice();
    s.runCmdNode(Scripts.getRiakScripts(), "riak", false);
    s.runCmdNode(String.format("echo 'docker rm -f riakserver\n %s' >> start.sh", Scripts
      .getRiakScripts()), "riak", false);
    String riakIP = s.getManagementIP("riak");
    System.out.println(String.format("Riak IP %s", riakIP));
    return riakIP;
  }

  private String deleteRiakSlice() throws Exception {
    SliceManager s = new SliceManager(sliceName, pemLocation, keyLocation, controllerUrl,
      sshKey);
    s.delete();
    return "true";
  }
}
