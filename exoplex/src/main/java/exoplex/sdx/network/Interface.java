package exoplex.sdx.network;

class Interface {
  String name;
  String nodeName;
  String linkName;
  String macAddr;
  String port;
  String ip;

  public Interface(String ifaceName, String nodeName, String linkName, String macAddr, String port,
                   String ip) {
    this.name = ifaceName;
    this.nodeName = nodeName;
    this.linkName = linkName;
    this.macAddr = macAddr;
    this.ip = ip;
    this.port = port;
  }

  public Interface(String nodeName, String linkName, String macAddr, String port) {
    this.name = NetworkUtil.computeInterfaceName(nodeName, linkName);
    this.nodeName = nodeName;
    this.linkName = linkName;
    this.macAddr = macAddr;
    this.port = port;
  }

  public String getName() {
    return name;
  }

  public String getNodeName() {
    return nodeName;
  }

  public void setNodeName(String nodeName) {
    this.nodeName = nodeName;
  }

  public String getLinkName() {
    return linkName;
  }

  public void setLinkName(String linkName) {
    this.linkName = linkName;
  }

  public String getMacAddr() {
    return macAddr;
  }

  public void setMacAddr(String mac) {
    this.macAddr = mac;
  }

  public String getPort() {
    return port;
  }

  public void setPort(String port) {
    this.port = port;
  }

  public String getIp() {
    return ip;
  }

  public void setIP(String ip) {
    this.ip = ip;
  }
}
