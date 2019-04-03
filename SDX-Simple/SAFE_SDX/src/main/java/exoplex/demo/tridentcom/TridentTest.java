package exoplex.demo.tridentcom;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import exoplex.client.exogeni.SdxExogeniClient;
import exoplex.sdx.core.SdxManager;
import exoplex.sdx.core.SdxServer;
import injection.MultiSdxModule;
import org.apache.commons.cli.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import safe.Authority;
import safe.SafeAuthority;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;

public class TridentTest extends TridentSetting {

  static Logger logger = LogManager.getLogger(TridentTest.class);

  static SdxManager sdxManager;

  static HashMap<String, SdxExogeniClient> exogeniClients = new HashMap<>();

  @Inject
  public TridentTest(Provider<Authority> authorityProvider) {
    super(authorityProvider);
  }

  public static void main(String[] args) throws Exception {
    Injector injector = Guice.createInjector(new MultiSdxModule());
    SdxServer sdxServer = injector.getProvider(SdxServer.class).get();
    sdxManager = sdxServer.run(sdxArgs);
    postSafeCertificates();
    for (String clientSlice : TridentSetting.clientSlices) {
      exogeniClients.put(clientSlice, new SdxExogeniClient(clientSlice,
        TridentSetting.clientIpMap.get(clientSlice),
        TridentSetting.clientKeyMap.get(clientSlice),
        clientArgs
      ));
    }
    stitchSlices();
    connectCustomerNetwork();
  }

  private static CommandLine parseCmd(String[] args) {
    Options options = new Options();
    Option config1 = new Option("s", "slice", false, "run with existing slice");
    Option config2 = new Option("r", "reset", false, "reset SDX slice");
    Option config3 = new Option("d", "delete", false, "delete all slices");
    config1.setRequired(false);
    config2.setRequired(false);
    config3.setRequired(false);
    options.addOption(config1);
    options.addOption(config2);
    options.addOption(config3);
    CommandLineParser parser = new DefaultParser();
    HelpFormatter formatter = new HelpFormatter();
    CommandLine cmd = null;

    try {
      cmd = parser.parse(options, args);
      return cmd;
    } catch (ParseException e) {
      System.out.println(e.getMessage());
      formatter.printHelp("utility-name", options);

      System.exit(1);
    }
    return cmd;
  }

  private static void postSafeCertificates() {
    Method getSafeServer = null;
    try {
      getSafeServer = sdxManager.getClass().getDeclaredMethod("getSafeServer", null);
    } catch (NoSuchMethodException e) {
      e.printStackTrace();
    }
    getSafeServer.setAccessible(true);
    String safeServer = null;
    try {
      safeServer = (String) getSafeServer.invoke(sdxManager);
    } catch (IllegalAccessException e) {
      e.printStackTrace();
    } catch (InvocationTargetException e) {
      e.printStackTrace();
    }
    SafeAuthority safeAuthority = new SafeAuthority(safeServer,
      TridentSetting.sdxName,
      "sdx",
      TridentSetting.clientSlices,
      TridentSetting.clientKeyMap,
      TridentSetting.clientIpMap
    );
    safeAuthority.initGeniTrustBase();
  }

  private static void stitchSlices() {
    for (String clientSlice : TridentSetting.clientSlices) {
      String gw = exogeniClients.get(clientSlice).processCmd("stitch CNode0");

      exogeniClients.get(clientSlice).processCmd(String.format("route %s %s",
        TridentSetting.clientIpMap.get(clientSlice),
        gw));
    }
  }

  private static void connectCustomerNetwork() {
    for (int i = 0; i < TridentSetting.clientSlices.size(); i++) {
      String client = TridentSetting.clientSlices.get(i);
      String clientIp = TridentSetting.clientIpMap.get(client);
      for (int j = i + 1; j < TridentSetting.clientSlices.size(); j++) {
        String peer = TridentSetting.clientSlices.get(j);
        String peerIp = TridentSetting.clientIpMap.get(peer);
        exogeniClients.get(client).processCmd(String.format("link %s %s",
          TridentSetting.clientIpMap.get(client),
          TridentSetting.clientIpMap.get(peer)));

        if (!exogeniClients.get(client).checkConnectivity("CNode1",
          peerIp.replace(".1/24", ".2"))) {
          sdxManager.checkFlowTableForPair(clientIp.replace(".1/24", ".0/24"),
            peerIp.replace(".1/24", ".0/24"),
            clientIp, peerIp);
        }
      }
    }
  }
}
