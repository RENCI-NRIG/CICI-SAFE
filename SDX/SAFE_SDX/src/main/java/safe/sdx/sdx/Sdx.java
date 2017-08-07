package safe.sdx;
import org.apache.commons.cli.*;
import org.apache.commons.cli.DefaultParser;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import org.renci.ahab.libtransport.ISliceTransportAPIv1;
import org.renci.ahab.libtransport.ITransportProxyFactory;
import org.renci.ahab.libtransport.SliceAccessContext;
import org.renci.ahab.libtransport.SliceAccessContext;
import org.renci.ahab.libtransport.SSHAccessToken;

public class Sdx extends UnicastRemoteObject{
	protected static final String RequestResource = null;
	protected static String controllerUrl;
  protected static String SDNControllerIP;
	protected static String sliceName;
	protected static String pemLocation;
	protected static String keyLocation;
  protected static String sshkey;
  protected static ISliceTransportAPIv1 sliceProxy;
  protected static SliceAccessContext<SSHAccessToken> sctx;
  protected static int curip=128;
	protected static String safeserver;
  protected static String keyhash;
  protected static String javasecuritypolicy;

  public Sdx()throws RemoteException{}

  protected static CommandLine parseCmd(String[] args){
    Options options = new Options();
    Option config = new Option("c", "config", true, "configuration file path");
    config.setRequired(true);
    options.addOption(config);
    CommandLineParser parser = new DefaultParser();
    HelpFormatter formatter = new HelpFormatter();
    CommandLine cmd=null;

    try {
        cmd = parser.parse(options, args);
    } catch (ParseException e) {
        System.out.println(e.getMessage());
        formatter.printHelp("utility-name", options);

        System.exit(1);
        return cmd;
    }
    return cmd;
  }

  protected static SdxConfig readConfig(String configfilepath){
    SdxConfig sdxconfig=new SdxConfig(configfilepath);
    pemLocation = sdxconfig.exogenipem;
		keyLocation = sdxconfig.exogenipem;
		controllerUrl = sdxconfig.exogenism; //"https://geni.renci.org:11443/orca/xmlrpc";
		sliceName = sdxconfig.slicename;
    sshkey=sdxconfig.sshkey;
    keyhash=sdxconfig.safekey;
    javasecuritypolicy=sdxconfig.javasecuritypolicy;
    return sdxconfig;
  }

}
