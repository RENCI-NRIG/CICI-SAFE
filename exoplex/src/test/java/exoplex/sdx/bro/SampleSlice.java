package exoplex.sdx.bro;

import exoplex.sdx.slice.exogeni.NodeBase;
import org.renci.ahab.libndl.Slice;
import org.renci.ahab.libndl.resources.request.ComputeNode;
import org.renci.ahab.libndl.resources.request.InterfaceNode2Net;
import org.renci.ahab.libndl.resources.request.Network;

import java.util.HashSet;
import java.util.Set;


public class SampleSlice extends SliceBase {
  ComputeNode control, flow, end1, end2, bro;
  Network netbro, nete1, nete2;
  InterfaceNode2Net ifFBflow, ifFBbro, ifFE1flow, ifFE1end, ifFE2flow, ifFE2end;

  public SampleSlice(String configPath) throws SliceBaseException {
    super(configPath);
  }

  public SampleSlice(String configPath, String name) throws SliceBaseException {
    super(configPath, name);
  }

  protected void createSlice(Slice s) {
    control = NodeBase.makeNode(s, "Ubuntu 16.04", "control");
    flow = NodeBase.makeNode(s, "Ubuntu 16.04", "flow");
    end1 = NodeBase.makeNode(s, "Ubuntu 16.04", "end1");
    end2 = NodeBase.makeNode(s, "Ubuntu 16.04", "end2");
    bro = NodeBase.makeNode(s, "Bro", "bro");

    netbro = s.addBroadcastLink("clinkFB", 1000000);
    nete1 = s.addBroadcastLink("clinkFE1", 1000000);
    nete2 = s.addBroadcastLink("clinkFE2", 1000000);

    ifFBflow = (InterfaceNode2Net) netbro.stitch(flow);
    ifFBbro = (InterfaceNode2Net) netbro.stitch(bro);
    ifFE1flow = (InterfaceNode2Net) nete1.stitch(flow);
    ifFE1end = (InterfaceNode2Net) nete1.stitch(end1);
    ifFE2flow = (InterfaceNode2Net) nete2.stitch(flow);
    ifFE2end = (InterfaceNode2Net) nete2.stitch(end2);

    ifFBflow.setNetmask("255.255.255.0");
    ifFBbro.setNetmask("255.255.255.0");
    ifFE1flow.setNetmask("255.255.255.0");
    ifFE1end.setNetmask("255.255.255.0");
    ifFE2flow.setNetmask("255.255.255.0");
    ifFE2end.setNetmask("255.255.255.0");

    ifFE1flow.setIpAddress("192.168.1.101");
    ifFE2flow.setIpAddress("192.168.1.102");
    ifFBflow.setIpAddress("192.168.1.103");

    ifFBbro.setIpAddress("192.168.1.3");
    ifFE1end.setIpAddress("192.168.1.1");
    ifFE2end.setIpAddress("192.168.1.2");
  }

  public void setUpFlow() throws SliceBaseException {
    Set<Thread> threads = new HashSet<>();
    threads.add(new Thread(() -> {
      try {
        execOnNode(control, "echo 'export DEBIAN_FRONTEND=\"noninteractive\"' >> ~/.bashrc");
        execOnNode(control, "while [ \"`fuser /var/lib/dpkg/lock`\" != \"\" ]; do sleep 1; done"); // Make sure apt-get install is available
        execOnNode(control, "rm -f /var/lib/dpkg/lock\ndpkg --configure -a\napt-get install -y python-pip");
        execOnNode(control, "pip install ryu");
        sftpToNode(control, "/scripts/ryu.py");
        sftpToNode(control, "/scripts/simple_switch.py");
        sftpToNode(control, "/scripts/rest_router.py");
        //execOnNode(control, "ryu-manager ryu.py &\ndisown -h `jobs -l | grep -E '[0-9]{2,5}' -o`");
      } catch (SliceBaseException e) {
        throw new RuntimeException(e);
      }
    }));

    threads.add(new Thread(() -> {
      try {
        execOnNode(bro, "echo 'export DEBIAN_FRONTEND=\"noninteractive\"' >> ~/.bashrc");
        execOnNode(bro, "sed -i 's/eth0/eth1/' /opt/bro/etc/node.cfg"); // This VM uses eth1
        execOnNode(bro, "/opt/bro/bin/broctl deploy");
      } catch (SliceBaseException e) {
        throw new RuntimeException(e);
      }
    }));

    threads.add(new Thread(() -> {
      try {
        execOnNode(flow, "echo 'export DEBIAN_FRONTEND=\"noninteractive\"' >> ~/.bashrc");
        execOnNode(flow, "echo 'export mainint=`ifconfig | grep -B1 \"10.103.*\" | awk '\\''$1!=\"inet\" && $1!=\"--\"'\\'' | cut -d' ' -f1`' >> ~/.bashrc");
        execOnNode(flow, "echo 'export others=`ifconfig | grep -B1 \"192.168.*\" | awk '\\''$1!=\"inet\" && $1!=\"--\"'\\'' | cut -d' ' -f1`' >> ~/.bashrc");
        execOnNode(flow, "while [ \"`fuser /var/lib/dpkg/lock`\" != \"\" ]; do sleep 1; done"); // Make sure apt-get install is available
        boolean ret;
        do {
          ret = execOnNode(flow, "apt-get install -y openvswitch-switch");
        } while (!ret);
        execOnNode(flow, "ovs-vsctl add-br br0");
        execOnNode(flow, "ovs-vsctl set bridge br0 protocols=OpenFlow13");
        execOnNode(flow, "ovs-vsctl set-fail-mode br0 secure");
        execOnNode(flow, "ovs-vsctl set-controller br0 tcp:" + retrieveIP(control) + ":6633");
        execOnNode(flow, "for i in `ifconfig | grep -B1 \"192.168.*\" | awk '$1!=\"inet\" && $1!=\"--\"' | cut -d' ' -f1`\n" +
          "do\n" +
          "ovs-vsctl add-port br0 $i\n" +
          "done\n");
      } catch (SliceBaseException e) {
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

    String broPort = execOnNode(flow, "ifconfig | grep -B1 \"192.168.1.103\" | awk '$1!=\"inet\" && $1!=\"--\"' | cut -d' ' -f1\n", true);
    String broMac = execOnNode(bro, "ifconfig | grep -A2 eth1 | grep -E '([0-9a-f]{2}:){5}[0-9a-f]{2}' -o", true);
    System.out.println("Bro port is " + broPort + " and mac " + broMac);
    execOnNode(control, "echo \"" + broPort + "\" > broConf\n");
    execOnNode(control, "echo \"" + broMac + "\" >> broConf\n");
    System.out.println("Done!");
  }

  protected String sliceName() {
    return "BroControlNew";
  }

  public static class SliceBaseException extends Exception {
    private static final long serialVersionUID = 1;

    public SliceBaseException(String message) {
      super(message);
    }

    public SliceBaseException(String message, Throwable t) {
      super(message, t);
    }

    public SliceBaseException(Throwable t) {
      super(t);
    }
  }
}

