package sdx.networkmanager;

import org.apache.log4j.Logger;

import common.utils.Exec;

public class Link {
  final static Logger logger = Logger.getLogger(Exec.class);

  public String linkname = "";
  public String nodea = null;
  public String nodeb = null;
  public String ipprefix = "";
  public String mask="24";
  public long capacity=0;

  public Link(){}

  public Link(String linkname, String nodea, String nodeb){
    this.linkname = linkname;
    this.nodea = nodea;
    this.nodeb = nodeb;
  }

  public void addNode(String node) {
    if (nodea == null)
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

  public void setCapacity(long cap){
    this.capacity=cap;
  }

  public String getIP(int i) {
    return ipprefix + "." + String.valueOf(i) + mask;
  }

  public boolean match(String a, String b){
    return (nodea.equals(a) && nodeb.equals(b)) || (nodea.equals(b) && nodeb.equals(a));
  }
}

