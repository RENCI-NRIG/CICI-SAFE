package exoplex.sdx.bro;

import com.jcraft.jsch.Session;
import org.renci.ahab.libndl.Slice;
import org.renci.ahab.libndl.resources.request.ComputeNode;
import org.renci.ahab.libndl.resources.request.InterfaceNode2Net;
import org.renci.ahab.libndl.resources.request.Network;
import exoplex.common.slice.NodeBase;

import java.util.*;

public class TestBroSlice extends SliceBase {
  ComputeNode control, flow, cs, c1, c2, c3;
  Network n1, n2, n3, ns;
  InterfaceNode2Net if1f, if1n;
  InterfaceNode2Net if2f, if2n;
  InterfaceNode2Net if3f, if3n;
  InterfaceNode2Net ifsf, ifsn;
  private String resource_dir;
  private Slice thisSlice;
  private Map<String, String> resourceIPs = new HashMap<>();
  private Map<String, Session> sessions = new HashMap<>();
  public TestBroSlice(String configPath) throws SampleSlice.SliceBaseException {
    super(configPath);
    resource_dir = conf.getString("config.resource_dir");
  }
  public TestBroSlice(String configPath, String name) throws SampleSlice.SliceBaseException {
    super(configPath, name);
    resource_dir = conf.getString("config.resource_dir");
  }

  public String sliceName() {
    return "t3";
  }

  protected void createSlice(Slice s) {
    control = NodeBase.makeNode(s, "Ubuntu 17.10", "control");
    flow = NodeBase.makeNode(s, "Ubuntu 17.10 XL", "flow");
    cs = NodeBase.makeNode(s, "Bro", "cs");
    c1 = NodeBase.makeNode(s, "Ubuntu 17.10", "c1");
    c2 = NodeBase.makeNode(s, "Ubuntu 17.10", "c2");
    c3 = NodeBase.makeNode(s, "Ubuntu 17.10", "c3");

    n1 = s.addBroadcastLink("l1", 1000000000);
    n2 = s.addBroadcastLink("l2", 1000000000);
    n3 = s.addBroadcastLink("l3", 1000000000);
    ns = s.addBroadcastLink("ls", 1000000000);

    if1f = (InterfaceNode2Net) n1.stitch(flow);
    if2f = (InterfaceNode2Net) n2.stitch(flow);
    if3f = (InterfaceNode2Net) n3.stitch(flow);
    ifsf = (InterfaceNode2Net) ns.stitch(flow);

    if1n = (InterfaceNode2Net) n1.stitch(c1);
    if2n = (InterfaceNode2Net) n2.stitch(c2);
    if3n = (InterfaceNode2Net) n3.stitch(c3);
    ifsn = (InterfaceNode2Net) ns.stitch(cs);

    if1f.setNetmask("255.255.255.0");
    if2f.setNetmask("255.255.255.0");
    if3f.setNetmask("255.255.255.0");
    ifsf.setNetmask("255.255.255.0");
    if1n.setNetmask("255.255.255.0");
    if2n.setNetmask("255.255.255.0");
    if3n.setNetmask("255.255.255.0");
    ifsn.setNetmask("255.255.255.0");

    if1f.setIpAddress("192.168.10.2");
    if2f.setIpAddress("192.168.20.2");
    if3f.setIpAddress("192.168.30.2");
    ifsf.setIpAddress("192.168.40.2");

    if1n.setIpAddress("192.168.10.1");
    if2n.setIpAddress("192.168.20.1");
    if3n.setIpAddress("192.168.30.1");
    ifsn.setIpAddress("192.168.40.1");
  }

  public void setUpFlow() throws SampleSlice.SliceBaseException {
    Set<Thread> threads = new HashSet<>();
    //skip this
    threads.add(new Thread(() -> {
      try {
        execOnNode(control, "echo 'export DEBIAN_FRONTEND=\"noninteractive\"' >> ~/.bashrc");
        execOnNode(control, "while [ \"`fuser /var/lib/dpkg/lock`\" != \"\" ]; do sleep 1; done"); // Make sure apt-get install is available
        execOnNode(control, "rm -f /var/lib/dpkg/lock\ndpkg --configure -a\napt-get install -y python-pip");
        execOnNode(control, "pip install ryu");
        sftpToNode(control, resource_dir + "scripts/ryu.py");
        sftpToNode(control, resource_dir + "scripts/simple_switch.py");
        sftpToNode(control, resource_dir + "scripts/rest_router.py");
        sftpToNode(control, resource_dir + "scripts/rest_router_mirror.py");
        sftpToNode(control, resource_dir + "scripts/rest_qos.py");
        sftpToNode(control, resource_dir + "scripts/rest_conf_switch.py");
        sftpToNode(control, resource_dir + "scripts/ofctl_rest.py");
        sftpToNode(control, resource_dir + "scripts/simple_switch_13.py");
        sftpToNode(control, resource_dir + "scripts/mirror.sh");
      } catch (SampleSlice.SliceBaseException e) {
        throw new RuntimeException(e);
      }
    }));

    //skip this
    threads.add(new Thread(() -> {
      try {
        execOnNode(flow, "echo 'export DEBIAN_FRONTEND=\"noninteractive\"' >> ~/.bashrc");
        execOnNode(flow, "echo 'export PATH=$PATH:/opt/bro/bin' >> ~/.bashrc");
        execOnNode(flow, "while [ \"`fuser /var/lib/dpkg/lock`\" != \"\" ]; do sleep 1; done"); // Make sure apt-get install is do { ret = execOnNode(flow, "wget -nv http://download.opensuse.org/repositories/network:bro/xUbuntu_17.04/Release.key -O Release.key"); } while (!ret);

        execOnNode(flow, "echo 'deb http://download.opensuse.org/repositories/network:/bro/xUbuntu_17.04/ /' > /etc/apt/sources.list.d/bro.list");
        execOnNode(flow, "wget -nv http://download.opensuse.org/repositories/network:bro/xUbuntu_17.04/Release.key -O Release.key");
        execTillSuccess(flow, "apt-key add -- < Release.key");
        execTillSuccess(flow, "apt update");
        execTillSuccess(flow, "apt-get install -y bro");
        execOnNode(flow, "echo 'export PATH=$PATH:/opt/bro/bin' >> ~/.bashrc");

        execTillSuccess(flow, "apt-get install -y openvswitch-switch");
        execTillSuccess(flow, "apt-get install -y iperf3");
          /*
          execTillSuccess(flow, "apt-get install -y unzip");
          execTillSuccess(flow, "apt-get install -y git cmake make gcc g++ flex bison libpcap-dev libssl-dev python-dev swig zlib1g-dev");
          execOnNode(flow, "wget https://github.com/actor-framework/actor-framework/archive/0.14.0.zip");
          execOnNode(flow, "unzip 0.14.0.zip");
          execOnNode(flow, "cd actor-framework-0.14.0 && ./configure && make && make test && make install");
          execOnNode(flow, "git clone --recursive git://git.bro.org/bro");
          execOnNode(flow, "cd bro && ./configure --prefix=/opt/bro --enable-broker --with-libcaf=/usr/local/lib/libcaf_core.so && make -j 5 && make install");
          execOnNode(flow, "git clone https://github.com/bro/bro-netcontrol");
          execOnNode(flow, "echo 'export PYTHONPATH=/opt/bro/lib/broctl:~/bro-netcontrol' >> ~/.bashrc");
          */
        execOnNode(flow, "ovs-vsctl add-br br0");
        execOnNode(flow, "ovs-vsctl set bridge br0 protocols=OpenFlow13");
        execOnNode(flow, "ovs-vsctl set-fail-mode br0 secure");
        execOnNode(flow, "ovs-vsctl set-controller br0 tcp:" + retrieveIP(control) + ":6633");
        execOnNode(flow, "for i in `ifconfig | grep -B1 \"192.168.*\" | awk '$1!=\"inet\" && $1!=\"--\"' | cut -d: -f1`\n" +
            "do\n" +
            "ovs-vsctl add-port br0 $i\n" +
            "done\n");
      } catch (SampleSlice.SliceBaseException e) {
        throw new RuntimeException(e);
      }
    }));

    for (ComputeNode n : Arrays.asList(c1, c2, c3)) {
      threads.add(new Thread(() -> {
        try {
          execOnNode(n, "echo 'export DEBIAN_FRONTEND=\"noninteractive\"' >> ~/.bashrc");
          execOnNode(n, "while [ \"`fuser /var/lib/dpkg/lock`\" != \"\" ]; do sleep 1; done"); // Make sure apt-get install is available
          execTillSuccess(n, "apt-get install -y iperf3");
          execTillSuccess(n, "apt-get install -y vsftpd");
          sftpToNode(n, resource_dir + "bro/evil.txt");
        } catch (SampleSlice.SliceBaseException e) {
          throw new RuntimeException(e);
        }
      }));
    }

    threads.add(new Thread(() -> {
      try {
        execTillSuccess(cs, "yum install -y iperf");
        execTillSuccess(cs, "yum install -y tcpdump");

        execOnNode(c1, "ip route add 192.168.0.0/16 via 192.168.10.2");
        execOnNode(c2, "ip route add 192.168.0.0/16 via 192.168.20.2");
        execOnNode(c3, "ip route add 192.168.0.0/16 via 192.168.30.2");
        execOnNode(cs, "ip route add 192.168.0.0/16 via 192.168.40.2");
      } catch (SampleSlice.SliceBaseException e) {
        throw new RuntimeException(e);
      }
    }));

    threads.forEach(t -> t.start());
    threads.forEach(t -> {
      try {
        t.join();
      } catch (InterruptedException e) {
      }
    });

    threads.clear();

    String dpid = execOnNode(flow, "ovs-ofctl -O OpenFlow13 show br0 | grep -E '[0-9a-f]{16}' -o", true);

    threads.add(new Thread(() -> {
      try {
        execOnNode(control, "bash mirror.sh&\ndisown -h `jobs -l | grep -E '[0-9]{2,5}' -o`");
        String connected;
        do {
          connected = execOnNode(flow, "ovs-vsctl show", true);
        } while (!connected.contains("is_connected: true"));
        execOnNode(control, "curl -X POST -d '{\"address\": \"192.168.10.2/24\"}' http://127.0.0.1:8080/router/" + dpid);
        execOnNode(control, "curl -X POST -d '{\"address\": \"192.168.20.2/24\"}' http://127.0.0.1:8080/router/" + dpid);
        execOnNode(control, "curl -X POST -d '{\"address\": \"192.168.30.2/24\"}' http://127.0.0.1:8080/router/" + dpid);
        execOnNode(control, "curl -X POST -d '{\"address\": \"192.168.40.2/24\"}' http://127.0.0.1:8080/router/" + dpid);

        execOnNode(control, "curl -X POST -d '{\"source\": \"192.168.10.1/24\", \"destination\": \"192.168.20.1\", \"mirror\": \"192.168.40.1\"}' http://127.0.0.1:8080/router/" + dpid);
        execOnNode(control, "curl -X POST -d '{\"source\": \"192.168.20.1/24\", \"destination\": \"192.168.10.1\", \"mirror\": \"192.168.40.1\"}' http://127.0.0.1:8080/router/" + dpid);
      } catch (SampleSlice.SliceBaseException e) {
        throw new RuntimeException(e);
      }
    }));

    threads.add(new Thread(() -> {
      try {
        String port1 = execOnNode(flow, "ifconfig | grep -B1 \"192.168.10.2\" | awk '$1!=\"inet\" && $1!=\"--\"' | cut -d: -f1", true);
        String port2 = execOnNode(flow, "ifconfig | grep -B1 \"192.168.20.2\" | awk '$1!=\"inet\" && $1!=\"--\"' | cut -d: -f1", true);
        String port3 = execOnNode(flow, "ifconfig | grep -B1 \"192.168.30.2\" | awk '$1!=\"inet\" && $1!=\"--\"' | cut -d: -f1", true);
        String ports = execOnNode(flow, "ifconfig | grep -B1 \"192.168.40.2\" | awk '$1!=\"inet\" && $1!=\"--\"' | cut -d: -f1", true);

        execOnNode(flow, "sed -i 's/eth0/" + ports + "/' /opt/bro/etc/node.cfg"); // This VM uses ports for the server
        execOnNode(cs, "sed -i 's/eth0/eth1/' /opt/bro/etc/node.cfg"); // This VM uses eth1 for the flow
        sftpToNode(cs, resource_dir + "sdnctrl/destroy_conn.bro");
        sftpToNode(cs, resource_dir + "bro/evil.txt");
        String sha1 = execOnNode(cs, "sha1sum evil.txt | cut -d' ' -f1", true);
        execOnNode(cs, "sed -i 's/bogus_dpid/" + Long.parseLong(dpid, 16) + "/' destroy_conn.bro");
        execOnNode(cs, "sed -i 's/bogus_addr/" + retrieveIP(control) + "/' destroy_conn.bro");
        execOnNode(cs, "sed -i 's/bogus_sha1/" + sha1 + "/' destroy_conn.bro");
        // execOnNode(cs, "/opt/bro/bin/broctl deploy");
      } catch (SampleSlice.SliceBaseException e) {
        throw new RuntimeException(e);
      }
    }));

    threads.forEach(t -> t.start());
    threads.forEach(t -> {
      try {
        t.join();
      } catch (InterruptedException e) {
      }
    });

    threads.clear();

    System.out.println("Done!");
  }
}
