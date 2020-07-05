package exoplex.sdx.network;

public class Link {
  private String linkName = "";
  private String ipPrefix = "";
  private String mask = "/24";
  private long capacityAB = 0;
  private long usedBwAB;
  private long capacityBA = 0;
  private long usedBwBA;
  private String interfaceA = null;
  private String nodeA = null;
  private String interfaceB = null;
  private String nodeB = null;

  public Link() {
  }

  //Stitch
  public Link(String linkName, String node) {
    this.linkName = linkName;
    this.nodeA = node;
    interfaceA = NetworkUtil.computeInterfaceName(node, linkName);
  }

  public Link(String linkname, String nodea, String nodeb) {
    this.linkName = linkname;
    if (nodea != null) {
      this.interfaceA = NetworkUtil.computeInterfaceName(nodea, linkname);
      this.nodeA = nodea;
    }
    if (nodeb != null) {
      this.interfaceB = NetworkUtil.computeInterfaceName(nodeb, linkname);
      this.nodeB = nodeb;
    }
  }

  public Link(String linkname, String nodea, String nodeb, long cap) {
    this.linkName = linkname;
    if (nodea != null) {
      this.interfaceA = NetworkUtil.computeInterfaceName(nodea, linkname);
      this.nodeA = nodea;
    }
    if (nodeb != null) {
      this.interfaceB = NetworkUtil.computeInterfaceName(nodeb, linkname);
      this.nodeB = nodeb;
    }
    this.capacityAB = cap;
    this.usedBwAB = 0;
    this.capacityBA = cap;
    this.usedBwBA = 0;
  }

  public String getLinkName() {
    return linkName;
  }

  public String getIpPrefix() {
    return ipPrefix;
  }

  public String getInterfaceA() {
    return interfaceA;
  }

  public String getInterfaceB() {
    return interfaceB;
  }

  public String getPairInterfaceName(String ifName) {
    if (ifName.equals(interfaceA)) {
      return interfaceB;
    } else if (ifName.equals(interfaceB)) {
      return interfaceA;
    }
    return null;
  }

  public String getPairNodeame(String nodeName) {
    if (nodeName.equals(nodeA)) {
      return nodeB;
    } else if (nodeName.equals(interfaceB)) {
      return nodeA;
    }
    return null;
  }

  public String getNodeA() {
    return nodeA;
  }

  public String getNodeB() {
    return nodeB;
  }

  public void addNode(String node) {
    if (nodeA == null) {
      nodeA = node;
      interfaceA = NetworkUtil.computeInterfaceName(nodeA, linkName);
    } else {
      nodeB = node;
      interfaceB = NetworkUtil.computeInterfaceName(nodeB, linkName);
    }
  }

  public void setName(String name) {
    linkName = name;
  }

  public void setIP(String ip) {
    ipPrefix = ip;
  }

  public void setMask(String m) {
    mask = m;
  }

  public String getIP(int i) {
    return ipPrefix + "." + i + mask;
  }

  public boolean match(String a, String b) {
    return (a.equals(nodeA) && b.equals(nodeB) || (a.equals(nodeB) && b.equals(nodeA)));
  }

  public long getAvailableBW() {
    return getAvailableBW(nodeA, nodeB);
  }

  public void useBW(long bw) {
    this.useBW(nodeA, nodeB, bw);
    this.useBW(nodeB, nodeA, bw);
  }

  public void releaseBW(long bw) {
    this.releaseBW(nodeA, nodeB, bw);
    this.releaseBW(nodeB, nodeA, bw);
  }

  public long getAvailableBW(String node1, String node2) {
    try {
      if (node1.equals(nodeA) && node2.equals(nodeB)) {
        return this.capacityAB - usedBwAB;
      } else if (node2.equals(nodeA) && node1.equals(nodeB)) {
        return this.capacityBA - usedBwBA;
      }
    } catch (Exception e) {
      e.printStackTrace();
      return 0;
    }
    return 0;
  }

  public void useBW(String node1, String node2, long bw) {
    try {
      if (node1.equals(nodeA) && node2.equals(nodeB)) {
        usedBwAB -= bw;
      } else if (node2.equals(nodeA) && node1.equals(nodeB)) {
        usedBwBA -= bw;
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void releaseBW(String node1, String node2, long bw) {
    try {
      if (node1.equals(nodeA) && node2.equals(nodeB)) {
        usedBwAB += bw;
      } else if (node2.equals(nodeA) && node1.equals(nodeB)) {
        usedBwBA += bw;
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }


  public boolean equals(Link link) {
    return linkName.equals(link.linkName);
  }

  public String toString() {
    return nodeA + ":" + nodeB + " " + getAvailableBW();
  }

  public boolean hasNode(String nodeName) {
    return nodeName.equals(nodeA) || nodeName.equals(nodeB);
  }

  public long getCapacity() {
    return this.getCapacity(nodeA, nodeB);
  }

  public void setCapacity(long cap) {
    this.setCapacity(nodeA, nodeB, cap);
    this.setCapacity(nodeB, nodeA, cap);
  }

  public long getCapacity(String node1, String node2) {
    try {
      if (node1.equals(nodeA) && node2.equals(nodeB)) {
        return capacityAB;
      } else if (node2.equals(nodeA) && node1.equals(nodeB)) {
        return capacityBA;
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return 0;
  }

  public void setCapacity(String node1, String node2, long cap) {
    try {
      if (node1.equals(nodeA) && node2.equals(nodeB)) {
        capacityAB = cap;
      } else if (node2.equals(nodeA) && node1.equals(nodeB)) {
        capacityBA = cap;
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
