package exoplex.client.exogeni;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import exoplex.common.utils.Exec;
import exoplex.common.utils.ServerOptions;
import exoplex.demo.singlesdx.SingleSdxModule;
import exoplex.sdx.core.CoreProperties;
import exoplex.sdx.core.SliceHelper;
import exoplex.sdx.safe.SafeManager;
import exoplex.sdx.slice.Scripts;
import exoplex.sdx.slice.SliceManager;
import exoplex.sdx.slice.SliceProperties;
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
  final Logger logger = LogManager.getLogger(ExogeniClientSlice.class);

  @Inject
  public ExogeniClientSlice(Authority authority) {
    super(authority);
  }


  public ExogeniClientSlice() {
    super(null);
  }

  public static void main(String[] args) throws Exception {
    Injector injector = Guice.createInjector(new SingleSdxModule());
    ExogeniClientSlice cs = injector.getProvider(ExogeniClientSlice.class).get();
    CoreProperties coreProperties = new CoreProperties(args);
    cs.run(coreProperties);
  }

  public void run(CoreProperties coreProperties) throws Exception {
    this.coreProperties = coreProperties;
    if (coreProperties.getType().equals("client")) {
      computeIP(coreProperties.getIpPrefix());
      logger.info("Client start");
      SliceManager c1 = createCustomerSlice(coreProperties.getSliceName(), 1, IPPrefix, curip,
        coreProperties.getBw(), true);
      try {
        c1.commitAndWait();
      } catch (Exception e) {
        e.printStackTrace();
        c1 = createCustomerSlice(coreProperties.getSliceName(), 1, IPPrefix, curip,
          coreProperties.getBw(), true);
        c1.commitAndWait();
      }
      c1.refresh();
      if (coreProperties.isSafeEnabled() && coreProperties.isSafeInSlice()) {
        String safeIp = c1.getManagementIP("safe-server");
        checkSafeServer(safeIp, coreProperties.getRiakIp());
      }
      checkScripts(c1, "CNode1");
      configQuaggaRouting(c1);
      logger.info("Slice active now: " + coreProperties.getSliceName());
      c1.printNetworkInfo();
      return;
    } else if (coreProperties.getType().equals("delete")) {
      SliceManager s2 = null;
      logger.info("deleting slice " + coreProperties.getSliceName());
      s2 = sliceManagerFactory.create(coreProperties.getSliceName(),
        coreProperties.getExogeniKey(),
        coreProperties.getExogeniKey(),
        coreProperties.getExogeniSm(),
        coreProperties.getSshKey()
      );
      //s2.reloadSlice();
      s2.delete();
    }
  }

  public void configQuaggaRouting(SliceManager c1) {
    c1.runCmdSlice(Scripts.aptUpdate() + Scripts.installQuagga()
        + Scripts.installNetTools()
        + Scripts.installIperf() + Scripts.installTraceRoute(),
      coreProperties.getSshKey(),
      "CNode\\d+",
      true);
    for (String node : c1.getComputeNodes()) {
      Exec.sshExec(SliceProperties.userName, c1.getManagementIP(node),
        Scripts.installQuagga(), coreProperties.getSshKey());
    }
    c1.runCmdSlice(Scripts.enableZebra(),
      coreProperties.getSshKey(),
      "CNode\\d+",
      true);
    String Prefix = coreProperties.getIpPrefix().split("/")[0];
    String mip = c1.getManagementIP("CNode1");
    Exec.sshExec(SliceProperties.userName, mip,
      "sudo bash -c \"echo \"ip route 192.168.1.1/16 " + Prefix +
        "\" >>/etc/quagga/zebra.conf\"", coreProperties.getSshKey());
    Exec.sshExec(SliceProperties.userName, mip, Scripts.enableZebra(), coreProperties.getSshKey());
    String res[] = Exec.sshExec(SliceProperties.userName, mip, "sudo ls " +
        "/etc/quagga",
      coreProperties.getSshKey());
    if (!res[0].contains("zebra.conf")) {
      Exec.sshExec(SliceProperties.userName, mip,
        "sudo bash -c \"echo \"ip route 192.168.1.1/16 " + Prefix + "\"" +
          " >>/etc/quagga/zebra.conf\" ", coreProperties.getSshKey());
      Exec.sshExec(SliceProperties.userName, mip, Scripts.enableZebra(), coreProperties.getSshKey());
    }
    Exec.sshExec(SliceProperties.userName, mip, Scripts.restartQuagga(), coreProperties.getSshKey());
  }

  public void run(String customerName, String ipPrefix, String site, String riakIp) throws
    Exception {
    //Example usage:   ./target/appassembler/bin/SafeSdxExample  ~/.ssl/geni-pruth1.pem ~/.ssl/geni-pruth1.pem "https://geni.renci.org:11443/orca/xmlrpc" pruth.1 stitch
    //Example usage:   ./target/appassembler/bin/SafeSdxExample  ~/.ssl/geni-pruth1.pem ~/.ssl/geni-pruth1.pem "https://geni.renci.org:11443/orca/xmlrpc" name fournodes
    if (coreProperties.getType().equals("client")) {
      coreProperties.setRouterSite(site);
      coreProperties.setIpPrefix(ipPrefix);
      coreProperties.setRiakIp(riakIp);
      computeIP(coreProperties.getIpPrefix());
      logger.info("Client start");
      coreProperties.setSliceName(customerName);
      SliceManager c1 = createCustomerSlice(coreProperties.getSliceName(), 1, IPPrefix, curip,
        coreProperties.getBw(), true);

      c1.commitAndWait();
      c1.refresh();
      if (coreProperties.isSafeEnabled() && coreProperties.isSafeInSlice()) {
        String safeIp = c1.getManagementIP("safe-server");
        checkSafeServer(safeIp, riakIp);
      }
      checkScripts(c1, "CNode1");
      //copyFile2Slice(c1, "/home/yaoyj11/project/exo-geni/SAFE_SDX/src/main/resources/scripts/configospffornewif.sh","~/configospffornewif.sh","~/.ssh/id_rsa");
      //runCmdSlice(c1,"/bin/bash ~/ospfautoconfig.sh","~/.ssh/id_rsa");
      //configFTPService(c1, "(CNode1)", "ftpuser", "ftp");
      configQuaggaRouting(c1);
      resetHostNames(c1);
      logger.info("Slice active now: " + customerName);
      c1.printNetworkInfo();
      return;
    } else if (coreProperties.getType().equals("delete")) {
      SliceManager s2 = null;
      logger.info("deleting slice " + coreProperties.getSliceName());
      s2 = sliceManagerFactory.create(coreProperties.getSliceName(),
        coreProperties.getExogeniKey(),
        coreProperties.getExogeniKey(),
        coreProperties.getExogeniSm(),
        coreProperties.getSshKey()
      );
      //s2.reloadSlice();
      s2.delete();
    }
  }

  public SliceManager createCustomerSlice(String sliceName, int num, String prefix, int start, long bw, boolean network)
    throws TransportException {//=1, String subnet="")
    //Main Example Code
    SliceManager s = sliceManagerFactory.create(coreProperties.getSliceName(),
      coreProperties.getExogeniKey(),
      coreProperties.getExogeniKey(),
      coreProperties.getExogeniSm(),
      coreProperties.getSshKey()
    );

    s.createSlice();

    ArrayList<String> nodelist = new ArrayList<>();
    for (int i = 0; i < num; i++) {
      String node0 = s.addComputeNode(coreProperties.getRouterSite(), "CNode" + String.valueOf(i + 1));
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
    if (coreProperties.isSafeEnabled()) {
      if (coreProperties.isSafeInSlice()) {
        s.addSafeServer(coreProperties.getServerSite(), coreProperties.getRiakIp(),
            CoreProperties.getSafeDockerImage(),
            CoreProperties.getSafeServerScript());
      }
    }
    return s;
  }
}

