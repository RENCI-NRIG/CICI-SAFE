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
}
