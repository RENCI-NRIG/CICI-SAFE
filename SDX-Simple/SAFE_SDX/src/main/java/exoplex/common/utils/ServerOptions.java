package exoplex.common.utils;

import org.apache.commons.cli.*;

public class ServerOptions {
  public static CommandLine parseCmd(String[] args) {
    Options options = new Options();
    Option config = new Option("c", "config", true, "configuration file path");
    Option config1 = new Option("d", "delete", false, "delete the slice");
    Option config2 = new Option("e", "exec", true, "command to exec");
    Option config3 = new Option("r", "reset", false, "command to exec");
    config.setRequired(true);
    config1.setRequired(false);
    config2.setRequired(false);
    config3.setRequired(false);
    options.addOption(config);
    options.addOption(config1);
    options.addOption(config2);
    options.addOption(config3);
    CommandLineParser parser = new DefaultParser();
    HelpFormatter formatter = new HelpFormatter();
    CommandLine cmd = null;

    try {
      cmd = parser.parse(options, args);
    } catch (ParseException e) {
      formatter.printHelp("utility-name", options);
      System.exit(1);
      return cmd;
    }

    return cmd;
  }
}
