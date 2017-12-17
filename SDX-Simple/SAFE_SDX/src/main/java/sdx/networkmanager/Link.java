package sdx.networkmanager;

import org.apache.log4j.Logger;

import sdx.utils.Exec;

public class Link {
  final static Logger logger = Logger.getLogger(Exec.class);

  public String linkname = "";
  public String nodea = "";
  public String nodeb = "";
  public String ipprefix = "";
  public String mask = "";

  public void addNode(String node) {
    if (nodea == "")
      nodea = node;
    else
      nodeb = node;
  }

  public void setName(String name) {
    linkname = name;
  }

  public void setIP(String ip) {
    ipprefix = ip;
  }

  public void setMask(String m) {
    mask = m;
  }

  public String getIP(int i) {
    return ipprefix + "." + String.valueOf(i) + mask;
  }
}
