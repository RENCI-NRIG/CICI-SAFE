package riak;

import exoplex.common.slice.SliceManager;
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

  public String run(String[] args) throws  Exception{
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
    if(cmd.hasOption('d')){
      return deleteRiakSlice();
    }
    return createRiakSlice();
  }

  public String createRiakSlice() throws  Exception{
    SliceManager s = SliceManager.create(sliceName, pemLocation, keyLocation, controllerUrl, sctx);
    s.addRiakServer(serverSite, "riak");
    s.commitAndWait();
    s.reloadSlice();
    s.runCmdNode(Scripts.getRiakScripts(), sshkey, "riak", false);
    String riakIP = s.getComputeNode("riak").getManagementIP();
    System.out.println(String.format("Riak IP %s", riakIP));
    return  riakIP;
  }

  private String deleteRiakSlice()throws Exception{
    deleteSlice(sliceName);
    return "true";
  }
}
