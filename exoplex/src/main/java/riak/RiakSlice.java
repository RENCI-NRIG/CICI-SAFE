package riak;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import exoplex.common.utils.ServerOptions;
import exoplex.sdx.slice.Scripts;
import exoplex.sdx.slice.SliceManager;
import exoplex.sdx.slice.SliceManagerFactory;
import exoplex.sdx.slice.exogeni.SliceCommon;
import exoplex.sdx.slice.exogeni.ExoGeniSliceModule;
import org.apache.commons.cli.CommandLine;

public class RiakSlice extends SliceCommon {
  @Inject
  private SliceManagerFactory sliceManagerFactory;

  public static void main(String[] args) throws Exception {
    if (args.length < 1) {
      args = new String[]{"-c", "config/riak.conf"};
    }
    Injector injector = Guice.createInjector(new ExoGeniSliceModule());
    RiakSlice slice = injector.getInstance(RiakSlice.class);
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
    SliceManager s = sliceManagerFactory.create(sliceName, pemLocation, keyLocation, controllerUrl,
      sshKey);
    s.createSlice();
    s.addRiakServer(serverSite, "riak");
    s.commitAndWait();
    s.loadSlice();
    s.runCmdNode(String.format("echo 'docker rm -f riakserver\n %s' >> start.sh", Scripts
      .getRiakScripts()), "riak", false);
    s.runCmdNode("/bin/bash /root/start.sh", "riak", false);
    String riakIP = s.getManagementIP("riak");
    System.out.println(String.format("Riak IP %s", riakIP));
    return riakIP;
  }

  private String deleteRiakSlice() throws Exception {
    SliceManager s = sliceManagerFactory.create(sliceName, pemLocation, keyLocation, controllerUrl,
      sshKey);
    s.delete();
    return "true";
  }
}
