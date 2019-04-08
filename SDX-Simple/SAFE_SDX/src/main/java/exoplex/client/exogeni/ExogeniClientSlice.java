package exoplex.client.exogeni;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import exoplex.common.utils.Exec;
import exoplex.common.utils.ServerOptions;
import exoplex.sdx.core.SliceHelper;
import exoplex.sdx.safe.SafeManager;
import exoplex.sdx.slice.SliceManager;
import exoplex.sdx.slice.exogeni.SiteBase;
import injection.ExoGeniSliceModule;
import org.apache.commons.cli.CommandLine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.renci.ahab.libtransport.util.TransportException;
import safe.Authority;

import java.util.ArrayList;

/**
 * @author geni-orca
 */
public class ExogeniClientSlice extends SliceHelper {
  static private long bw = 1000000;
  final Logger logger = LogManager.getLogger(ExogeniClientSlice.class);
  private String mask = "/24";
  private String type;
  private String subnet;
  private String routerSite = "";

  @Inject
  public ExogeniClientSlice(Authority authority) {
    super(authority);
  }


  public ExogeniClientSlice() {
    super(null);
  }

  public static void main(String[] args) throws Exception {
    Injector injector = Guice.createInjector(new ExoGeniSliceModule());
    ExogeniClientSlice cs = injector.getProvider(ExogeniClientSlice.class).get();
    cs.run();
  }

  public void processArgs(String[] args) {

    logger.debug("SDX-Simple " + args[0]);

    CommandLine cmd = ServerOptions.parseCmd(args);
    String configFilePath = cmd.getOptionValue("config");

    this.readConfig(configFilePath);

    type = conf.getString("config.type");
    if (cmd.hasOption('d')) {
      type = "delete";
    }
  }

  public void run() throws Exception {
    //Example usage:   ./target/appassembler/bin/SafeSdxExample  ~/.ssl/geni-pruth1.pem ~/.ssl/geni-pruth1.pem "https://geni.renci.org:11443/orca/xmlrpc" pruth.1 stitch
    //Example usage:   ./target/appassembler/bin/SafeSdxExample  ~/.ssl/geni-pruth1.pem ~/.ssl/geni-pruth1.pem "https://geni.renci.org:11443/orca/xmlrpc" name fournodes


    if (type.equals("client")) {
      routerSite = SiteBase.get(conf.getString("config.routersite"));
      subnet = conf.getString("config.ipprefix");
      computeIP(subnet);
      logger.info("Client start");
      String customerName = sliceName;
      SliceManager c1 = createCustomerSlice(customerName, 1, IPPrefix, curip, bw, true);
      try {
        c1.commitAndWait();
      } catch (Exception e) {
        e.printStackTrace();
        c1 = createCustomerSlice(customerName, 1, IPPrefix, curip, bw, true);
        c1.commitAndWait();
      }
      c1.refresh();
      if (safeEnabled && safeInSlice) {
        String safeIp = c1.getManagementIP("safe-server");
        checkSafeServer(safeIp, riakIp);
      }
      //copyFile2Slice(c1, "/home/yaoyj11/project/exo-geni/SAFE_SDX/src/main/resources/scripts/configospffornewif.sh","~/configospffornewif.sh","~/.ssh/id_rsa");
      //copyFile2Slice(c1, "/home/yaoyj11/project/exo-geni/SAFE_SDX/src/main/resources/scripts/configospffornewif.sh","~/configospffornewif.sh","~/.ssh/id_rsa");
      //runCmdSlice(c1,"/bin/bash ~/ospfautoconfig.sh","~/.ssh/id_rsa");
      configFTPService(c1, "(CNode1)", "ftpuser", "ftp");
      configQuaggaRouting(c1);
      logger.info("Slice active now: " + sliceName);
      c1.printNetworkInfo();
      return;
    } else if (type.equals("delete")) {
      SliceManager s2 = null;
      logger.info("deleting slice " + sliceName);
      s2 = sliceManagerFactory.create(sliceName, pemLocation, keyLocation, controllerUrl, sshKey);
      //s2.reloadSlice();
      s2.delete();
    }
  }

  public void configQuaggaRouting(SliceManager c1) {
    c1.runCmdSlice("apt-get update; apt-get install -y quagga traceroute iperf", sshKey,
      "CNode\\d+",
      true);
    for (String node : c1.getComputeNodes()) {
      String res[] = Exec.sshExec("root", c1.getManagementIP(node), "ls /etc/init.d", sshKey);
      while (!res[0].contains("quagga")) {
        res = Exec.sshExec("root", c1.getManagementIP(node), "apt-get install -y quagga", sshKey);
      }
    }
    c1.runCmdSlice("sed -i -- 's/zebra=no/zebra=yes/g' /etc/quagga/daemons", sshKey, "CNode\\d+",
      true);
    String Prefix = subnet.split("/")[0];
    String mip = c1.getManagementIP("CNode1");
    Exec.sshExec("root", mip, "echo \"ip route 192.168.1.1/16 " + Prefix + "\" >>/etc/quagga/zebra.conf  ", sshKey);
    Exec.sshExec("root", mip, "sed -i -- 's/zebra=no/zebra=yes/g' /etc/quagga/daemons\n", sshKey);
    String res[] = Exec.sshExec("root", mip, "ls /etc/quagga", sshKey);
    while (!res[0].contains("zebra.conf") || !res[0].contains("zebra.conf")) {
      c1.runCmdSlice("apt-get update; apt-get install -y quagga iperf", sshKey, "CNode\\d+",
        true);
      res = Exec.sshExec("root", mip, "ls /etc/quagga", sshKey);
      Exec.sshExec("root", mip, "echo \"ip route 192.168.1.1/16 " + Prefix + "\" >>/etc/quagga/zebra.conf  ", sshKey);
      Exec.sshExec("root", mip, "sed -i -- 's/zebra=no/zebra=yes/g' /etc/quagga/daemons\n", sshKey);
    }
    Exec.sshExec("root", mip, "/etc/init.d/quagga restart", sshKey);
  }

  public void run(String customerName, String ipPrefix, String site, String riakIp) throws
    Exception {
    //Example usage:   ./target/appassembler/bin/SafeSdxExample  ~/.ssl/geni-pruth1.pem ~/.ssl/geni-pruth1.pem "https://geni.renci.org:11443/orca/xmlrpc" pruth.1 stitch
    //Example usage:   ./target/appassembler/bin/SafeSdxExample  ~/.ssl/geni-pruth1.pem ~/.ssl/geni-pruth1.pem "https://geni.renci.org:11443/orca/xmlrpc" name fournodes
    if (type.equals("client")) {
      routerSite = site;
      subnet = ipPrefix;
      this.riakIp = riakIp;
      computeIP(subnet);
      logger.info("Client start");
      sliceName = customerName;
      SliceManager c1 = createCustomerSlice(sliceName, 1, IPPrefix, curip, bw, true);

      c1.commitAndWait();
      c1.refresh();
      if (safeEnabled && safeInSlice) {
        String safeIp = c1.getManagementIP("safe-server");
        checkSafeServer(safeIp, riakIp);
      }
      //copyFile2Slice(c1, "/home/yaoyj11/project/exo-geni/SAFE_SDX/src/main/resources/scripts/configospffornewif.sh","~/configospffornewif.sh","~/.ssh/id_rsa");
      //copyFile2Slice(c1, "/home/yaoyj11/project/exo-geni/SAFE_SDX/src/main/resources/scripts/configospffornewif.sh","~/configospffornewif.sh","~/.ssh/id_rsa");
      //runCmdSlice(c1,"/bin/bash ~/ospfautoconfig.sh","~/.ssh/id_rsa");
      //configFTPService(c1, "(CNode1)", "ftpuser", "ftp");
      configQuaggaRouting(c1);
      resetHostNames(c1);
      logger.info("Slice active now: " + customerName);
      c1.printNetworkInfo();
      return;
    } else if (type.equals("delete")) {
      SliceManager s2 = null;
      logger.info("deleting slice " + sliceName);
      s2 = sliceManagerFactory.create(sliceName, pemLocation, keyLocation, controllerUrl, sshKey);
      //s2.reloadSlice();
      s2.delete();
    }
  }

  public SliceManager createCustomerSlice(String sliceName, int num, String prefix, int start, long bw, boolean network)
    throws TransportException {//=1, String subnet="")
    //Main Example Code

    SliceManager s = sliceManagerFactory.create(sliceName, pemLocation, keyLocation, controllerUrl,
      sshKey);
    s.createSlice();

    ArrayList<String> nodelist = new ArrayList<>();
    for (int i = 0; i < num; i++) {
      String node0 = s.addComputeNode(routerSite, "CNode" + String.valueOf(i + 1));
      nodelist.add(node0);
    }
    if (network) {
      for (int i = 0; i < nodelist.size() - 1; i++) {
        s.addLink("clink" + (i + 1),
          IPPrefix + (start + i) + ".1",
          IPPrefix + (start + i) + ".2",
          "255.255.255.0",
          nodelist.get(i),
          nodelist.get(i + 1),
          bw
        );
      }
    }
    if (safeEnabled) {
      if (safeInSlice) {
        s.addSafeServer(serverSite, riakIp, SafeManager.getSafeDockerImage(), SafeManager
          .getSafeServerScript());
      }
    }
    return s;
  }
}

