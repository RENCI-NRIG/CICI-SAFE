package exoplex.sdx.network;

public class Link {
  private String linkName = "";
  private String ipPrefix = "";
  private String mask = "/24";
  private long capacity = 0;
  private String interfaceA = null;
  private String nodeA = null;
  private String interfaceB = null;
  private String nodeB = null;
  private long usedBw;

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
    this.capacity = cap;
    this.usedBw = 0;
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

  public String getInterface(String nodeName) {
    if(interfaceA != null && interfaceA.contains(nodeName)) {
      return interfaceA;
    }
    return interfaceB;
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

  public String getPairNodeName(String nodeName) {
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
    return this.capacity - this.usedBw;
  }

  public void useBW(long bw) {
    this.usedBw += bw;
  }

  public void releaseBW(long bw) {
    this.usedBw -= bw;
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
    return capacity;
  }

  public void setCapacity(long cap) {
    this.capacity = cap;
  }
}
