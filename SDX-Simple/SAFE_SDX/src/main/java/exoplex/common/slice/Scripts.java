package exoplex.common.slice;

public class Scripts {
  public static String getOVSScript() {
    String script = "apt update\n" +
        "apt-get install -y openvswitch-switch\n /etc/init.d/neuca-guest-tools stop\n";
    return script;
  }

  public static String getBasicScripts(){
    return "";
  }

  public static String getPlexusScript() {
    String script = "apt-get update\n"
        + "docker pull yaoyj11/plexus\n"
        + "docker run -i -t -d -p 8080:8080 -p 6633:6633 -p 3000:3000 -h plexus --name plexus yaoyj11/plexus\n";
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
        + "export SCALA_HOME=/opt/scala-2.11.8;sed -i 's/RIAKSERVER/" + riakip + "/g' "
        + "safe-server/src/main/resources/application.conf;./sdx.sh\"\n";
    return script;
  }

}
