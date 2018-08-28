package riak;

import exoplex.common.slice.SafeSlice;
import exoplex.common.slice.Scripts;
import exoplex.common.slice.SliceCommon;
import exoplex.common.utils.ServerOptions;
import org.renci.ahab.libtransport.SSHAccessToken;
import org.renci.ahab.libtransport.SliceAccessContext;
import org.renci.ahab.libtransport.util.SSHAccessTokenFileFactory;
import org.renci.ahab.libtransport.util.UtilTransportException;

import org.apache.commons.cli.CommandLine;

public class RiakSlice extends SliceCommon{
  public static void main(String[] args) throws  Exception{
    if(args.length<1){
      args = new String[]{"-c", "config/riak.conf"};
    }
    RiakSlice slice = new RiakSlice();
    slice.run(args);
  }

  public void run(String[] args) throws  Exception{
    if(args.length<1){
      args = new String[]{"-c", "config/riak.conf"};
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
    createRiakSlice();
  }

  public void createRiakSlice() throws  Exception{
    SafeSlice s = SafeSlice.create(sliceName, pemLocation, keyLocation, controllerUrl, sctx);
    s.addRiakServer(serverSite, "riak");
    //s.addComputeNode("node0");
    s.commitAndWait();
    s.reloadSlice();
    s.runCmdNode(Scripts.getRiakScripts(), sshkey, "riak", false);
    System.out.println(String.format("Riak IP %s", s.getComputeNode("riak").getManagementIP()));
  }
}
