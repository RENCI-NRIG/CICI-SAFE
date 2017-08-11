package safe.sdx.sdx;

public class Link{
  public String linkname="";
  public String nodea="";
  public String nodeb="";
  public String ipprefix="";
  public String mask="";
  public void addNode(String node){
    if(nodea=="")
      nodea=node;
    else
      nodeb=node;
  }

  public void setName(String name){
    linkname=name;
  }

  public void setIP(String ip){
    ipprefix=ip;
  }

  public void setMask(String m){
    mask=m;
  }

  public String  getIP(int i){
    return ipprefix+"."+String.valueOf(i)+mask;
  }
}
