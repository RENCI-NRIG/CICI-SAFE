package exoplex;

import exoplex.common.slice.SliceManager;
import exoplex.common.slice.SiteBase;
import exoplex.common.utils.ServerOptions;
import exoplex.sdx.core.SliceHelper;
import exoplex.sdx.safe.SafeManager;
import org.apache.commons.cli.CommandLine;
import org.renci.ahab.libtransport.SSHAccessToken;
import org.renci.ahab.libtransport.SliceAccessContext;
import org.renci.ahab.libtransport.util.SSHAccessTokenFileFactory;
import org.renci.ahab.libtransport.util.UtilTransportException;

public class DockerServerSlice extends SliceHelper {
  public static void main(String[] args) throws  Exception{
    if(args.length<1){
      args = new String[]{"-c", "config/docker.conf"};
    }
    DockerServerSlice slice = new DockerServerSlice();
    slice.run(args);
  }

  public void run(String[] args){
    if(args.length<1){
      args = new String[]{"-c", "config/docker.conf"};
    }
    CommandLine cmd = ServerOptions.parseCmd(args);
    initializeExoGENIContexts(cmd.getOptionValue("config"));
    //SSH context
    sctx = new SliceAccessContext<>();
    try {
      SSHAccessTokenFileFactory fac;
      fac = new SSHAccessTokenFileFactory("~/.ssh/id_rsa.pub", false);
      SSHAccessToken t = fac.getPopulatedToken();
      sctx.addToken("root", "root", t);
      sctx.addToken("root", t);
    } catch (UtilTransportException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    try {
      createSafeAndPlexusSlice();
    }catch (Exception e){

    }
  }

  public void createSafeAndPlexusSlice() throws  Exception{
    SliceManager s = SliceManager.create(sliceName, pemLocation, keyLocation, controllerUrl, sctx);
    s.addSafeServer(SiteBase.get("BBN"), conf.getString("config.riak"), SafeManager
      .safeDockerImage, SafeManager.safeServerScript);
    s.addPlexusController(SiteBase.get("BBN"),"plexus");
    s.commitAndWait();
    s.reloadSlice();
    checkSafeServer(s.getComputeNode("safe-server").getManagementIP(),riakIp);
    checkPlexus(s.getComputeNode("plexus").getManagementIP());
    System.out.println(String.format("Safe server IP %s", s.getComputeNode("safe-server")
      .getManagementIP()));
    System.out.println(String.format("plexus server IP %s", s.getComputeNode("plexus")
      .getManagementIP()));
  }
}
