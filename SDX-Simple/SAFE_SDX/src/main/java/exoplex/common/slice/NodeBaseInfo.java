package exoplex.common.slice;

public class NodeBaseInfo {
  public String nisn;
  public String niurl;
  public String nihash;
  public String ntype;
  public String domain;

  public NodeBaseInfo(String nisn, String niurl, String nihash, String ntype, String domain) {
    this.nisn = nisn;
    this.niurl = niurl;
    this.nihash = nihash;
    this.ntype = ntype;
    this.domain = domain;
  }
}
