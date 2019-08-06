package exoplex.sdx.slice;

public class Scripts {
  public static String getOVSScript() {
    String script = aptUpdate() + installOVS() + "/etc/init.d/neuca-guest-tools stop\n";
    return script;
  }

  public static String getBasicScripts() {
    return "";
  }

  public static String getPlexusScript(String plexusImage) {
    String script = aptUpdate()
      + "sudo docker pull %s\n"
      + "sudo docker run -i -t -d -p 8080:8080 -p 6633:6633 -p 3000:3000 -h " +
      "plexus --name plexus %s\n";
    script = String.format(script, plexusImage, plexusImage);
    //+"docker exec -d plexus /bin/bash -c  \"cd /root/;./sdx.sh\"\n";
    return script;
  }

  public static String getBroScripts() {
    String script = "sudo yum install -y tcpdump bc htop";
    return script;
  }

  public static String getCustomerScript() {
    String nodePostBootScript =
      preBootScripts()
      + installIperf()
      + installQuagga()
      + "sudo sed -i -- 's/zebra=no/zebra=yes/g' /etc/quagga/daemons\n"
      + "sudo sed -i -- 's/ospfd=no/ospfd=yes/g' /etc/quagga/daemons\n"
      + "sudo bash -c 'echo \"1\" > /proc/sys/net/ipv4/ip_forward'\n"
      + "sudo /etc/init.d/neuca stop\n";
    return nodePostBootScript;
  }

  public static String getSafeScript(String riakip) {
    String script =
      "sudo docker pull yaoyj11/safeserver\n"
      + "sudo docker run -i -t -d -p 7777:7777 -h safe --name safe " +
        "yaoyj11/safeserver\n"
      + "sudo docker exec -d safe /bin/bash -c  \"cd /root/safe;export " +
        "SBT_HOME=/opt/sbt-0.13.12;"
      + "export SCALA_HOME=/opt/scala-2.11.8;"
      + "sed -i 's/http:\\/\\/.*:8098/http:\\/\\/" + riakip + ":8098/g' "
      + "safe-server/src/main/resources/application.conf;"
      + "./sdx.sh\"\n";
    return script;
  }

  public static String getSafeScript_v1(String riakip, String safeDockerImg, String
    safeServerScript) {
    String script = String.format(
      "sudo docker pull yaoyj11/%s\n"
      + "sudo docker run -i -t -d -p 7777:7777 -h safe --name safe yaoyj11/%s\n"
      + "sudo docker exed -d safe /usr/bin/git clone -b multisdx " +
      "https://github" +
        ".com/yaoyj11/safe-multisdx.git\n"
      + "sudo docker exec -d safe /bin/bash -c  \"cd /root/safe;"
      + "sed -i 's/http:\\/\\/.*:8098/http:\\/\\/" + riakip + ":8098/g' "
      + "safe-server/src/main/resources/application.conf;"
      + "cd /root; ./safe-multisdx/%s\"\n", safeDockerImg, safeDockerImg, safeServerScript);
    return script;
  }

  public static String restartSafe_v1(String safeServerScript) {
    return String.format(
      "sudo docker exec -d safe /bin/bash -c  \"cd /root;" +
      "pkill java;" +
      "/usr/bin/git clone -b multisdx https://github.com/yaoyj11/safe-multisdx.git;"
      + "cd /root;./safe-multisdx/%s\"\n",
      safeServerScript);
  }

  public static String getRiakPreBootScripts() {
    return "sudo docker pull yaoyj11/riakimg\n";
  }

  public static String getRiakScripts() {
    return getRiakPreBootScripts() +
      "sudo docker run -i -t  -d -p 2122:2122 -p 8098:8098 -p 8087:8087 -h " +
      "riakserver --name riakserver yaoyj11/riakimg\n"
      + "sudo docker ps\n"
      + "sudo docker exec -i -t -d riakserver sudo riak start\n"
      + "sudo docker exec -i -t -d  riakserver sudo riak-admin bucket-type " +
      "activate  safesets\n"
      + "sudo docker exec -i -t  -d riakserver sudo riak-admin bucket-type " +
      "update safesets '{\"props\":{\"allow_mult\":false}}'\n"
      + "sudo docker exec -it -d riakserver sudo riak ping\n";
  }

  public static String preBootScripts() {
    return stopAptDailyService() + aptUpdate() + installNetTools();
  }

  public static String delayDailyService() {
    /*
    String overrideConf = "[Timer]\n" +
      "OnBootSec=15min\n" +
      "OnUnitActiveSec=1d\n" +
      "AccuracySec=1h\n" +
      "RandomizedDelaySec=30min";
    return String.format("sudo echo \"%s\" > /etc/systemd/system/apt-daily.timer.d/override" +
      ".conf;", overrideConf);
     */
    return "sudo service apt-daily stop;";
  }

  public static String stopAptDailyService() {
    return "systemctl stop apt-daily.service\n" +
      "systemctl kill --kill-who=all apt-daily.service\n" +
      "while ! (systemctl list-units --all apt-daily.service | egrep -q '(dead|failed)')\n" +
      "do\n" +
      "  sleep 1;\n" +
      "done\n";
  }

  public static String installDocker(){
    return "sudo apt-get install -y docker.io;";
  }

  public static String installOVS() {
    return "sudo apt-get install -y openvswitch-switch;";
  }

  public static String aptUpdate() {
    return "sudo apt-get update;";
  }

  public static String installTraceRoute() {
    return "sudo apt-get install -y traceroute;";
  }

  public static String installQuagga() {
    return "sudo apt-get install -y quagga;";
  }

  public static String installIperf() {
    return "sudo apt-get install -y iperf;";
  }

  public static String installVsftpd() {
    return "sudo apt-get install -y vsftpd;";
  }

  public static String dockerImages() {
    return "sudo docker images;";
  }

  public static String dockerPs() {
    return "sudo docker ps;";
  }

  public static String enableZebra() {
    return "sudo bash -c \"echo 'zebra=yes' > /etc/quagga/daemons\";";
  }

  public static String installNetTools() {
    return "sudo apt-get install -y -qq net-tools;";
  }

  public static String restartQuagga() {
    return "sudo service zebra restart;";
  }

  public static String stopQuagga() {
    return "sudo service zebra stop;";
  }
}
