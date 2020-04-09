package safe;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import exoplex.sdx.core.CoreProperties;
import exoplex.sdx.slice.Scripts;
import exoplex.sdx.slice.SliceManager;
import exoplex.sdx.slice.SliceManagerFactory;
import exoplex.sdx.slice.exogeni.ExoGeniSliceModule;

public class RiakSlice {
  @Inject
  private SliceManagerFactory sliceManagerFactory;

  private CoreProperties coreProperties;

  public static void main(String[] args) throws Exception {
    if (args.length < 1) {
      args = new String[]{"-c", "config/riak.conf"};
    }
    Injector injector = Guice.createInjector(new ExoGeniSliceModule());
    RiakSlice slice = injector.getInstance(RiakSlice.class);
    slice.run(new CoreProperties(args));
  }

  public String run(CoreProperties coreProperties) throws Exception {
    this.coreProperties = coreProperties;
    //SSH context
    if (coreProperties.getType() == "delete") {
      return deleteRiakSlice();
    }
    return createRiakSlice();
  }

  public String createRiakSlice() throws Exception {
    SliceManager s = sliceManagerFactory.create(
      coreProperties.getSliceName(),
      coreProperties.getExogeniKey(),
      coreProperties.getExogeniKey(),
      coreProperties.getExogeniSm(),
      coreProperties.getSshKey()
    );
    s.createSlice();
    s.addRiakServer(coreProperties.getServerSite(), "riak");
    s.commitAndWait();
    s.loadSlice();
    s.runCmdNode(String.format("echo 'sudo docker rm -f riakserver\n %s' >> " +
      "start.sh", Scripts
      .getRiakScripts()), "riak", false);
    s.runCmdNode("sudo /bin/bash start.sh", "riak", false);
    String riakIP = s.getManagementIP("riak");
    System.out.println(String.format("Riak IP %s", riakIP));
    return riakIP;
  }

  private String deleteRiakSlice() throws Exception {
    SliceManager s = sliceManagerFactory.create(
      coreProperties.getSliceName(),
      coreProperties.getExogeniKey(),
      coreProperties.getExogeniKey(),
      coreProperties.getExogeniSm(),
      coreProperties.getSshKey()
    );
    s.delete();
    return "true";
  }
}
