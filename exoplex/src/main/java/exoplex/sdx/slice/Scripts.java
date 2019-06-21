package exoplex.sdx.slice;

public class Scripts {
  public static String getOVSScript() {
    String script = "apt update\n" +
      "apt-get install -y openvswitch-switch\n /etc/init.d/neuca-guest-tools stop\n";
    return script;
  }

  public static String getBasicScripts() {
    return "";
  }

  public static String getPlexusScript(String plexusImage) {
    String script = "apt-get update\n"
      + "docker pull %s\n"
      + "docker run -i -t -d -p 8080:8080 -p 6633:6633 -p 3000:3000 -h plexus --name plexus %s\n";
    script = String.format(script, plexusImage, plexusImage);
    //+"docker exec -d plexus /bin/bash -c  \"cd /root/;./sdx.sh\"\n";
    return script;
  }

  public static String getBroScripts() {
    String script = "yum install -y tcpdump bc htop";
    return script;
  }

  public static String getCustomerScript() {
    String nodePostBootScript = "apt-get update;apt-get -y install quagga iperf\n"
      + "sed -i -- 's/zebra=no/zebra=yes/g' /etc/quagga/daemons\n"
      + "sed -i -- 's/ospfd=no/ospfd=yes/g' /etc/quagga/daemons\n"
      + "echo \"1\" > /proc/sys/net/ipv4/ip_forward\n"
      + "/etc/init.d/neuca stop\n";
    return nodePostBootScript;
  }

  public static String getSafeScript(String riakip) {
    String script = "apt-get update\n"
      + "docker pull yaoyj11/safeserver\n"
      + "docker run -i -t -d -p 7777:7777 -h safe --name safe yaoyj11/safeserver\n"
      + "docker exec -d safe /bin/bash -c  \"cd /root/safe;export SBT_HOME=/opt/sbt-0.13.12;"
      + "export SCALA_HOME=/opt/scala-2.11.8;"
      + "sed -i 's/http:\\/\\/.*:8098/http:\\/\\/" + riakip + ":8098/g' "
      + "safe-server/src/main/resources/application.conf;"
      + "./sdx.sh\"\n";
    return script;
  }

  public static String getSafeScript_v1(String riakip, String safeDockerImg, String
    safeServerScript) {
    String script = String.format("apt-get update\n"
      + "docker pull yaoyj11/%s\n"
      + "docker run -i -t -d -p 7777:7777 -h safe --name safe yaoyj11/%s\n"
      + "docker exed -d safe /usr/bin/git clone -b multisdx https://github" +
        ".com/yaoyj11/safe-multisdx.git\n"
      + "docker exec -d safe /bin/bash -c  \"cd /root/safe;"
      + "sed -i 's/http:\\/\\/.*:8098/http:\\/\\/" + riakip + ":8098/g' "
      + "safe-server/src/main/resources/application.conf;"
      + "cd /root; ./safe-multisdx/%s\"\n", safeDockerImg, safeDockerImg, safeServerScript);
    return script;
  }

  public static String restartSafe_v1(String safeServerScript) {
    return String.format(
      "docker exec -d safe /bin/bash -c  \"cd /root;" +
      "pkill java;" +
      "/usr/bin/git clone -b multisdx https://github.com/yaoyj11/safe-multisdx.git;"
      + "cd /root;./safe-multisdx/%s\"\n",
      safeServerScript);
  }

  public static String getRiakPreBootScripts() {
    return "docker pull yaoyj11/riakimg\n";
  }

  public static String getRiakScripts() {
    return getRiakPreBootScripts() +
      "docker run -i -t  -d -p 2122:2122 -p 8098:8098 -p 8087:8087 -h riakserver --name riakserver yaoyj11/riakimg\n"
      + "docker ps\n"
      + "docker exec -i -t -d riakserver sudo riak start\n"
      + "docker exec -i -t -d  riakserver sudo riak-admin bucket-type activate  safesets\n"
      + "docker exec -i -t  -d riakserver sudo riak-admin bucket-type update safesets '{\"props\":{\"allow_mult\":false}}'\n"
      + "docker exec -it -d riakserver sudo riak ping\n";
  }
}