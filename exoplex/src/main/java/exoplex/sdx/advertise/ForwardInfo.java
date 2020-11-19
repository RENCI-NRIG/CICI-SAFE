package exoplex.sdx.advertise;

public class ForwardInfo {
  public String srcPrefix;
  public String destPrefix;
  public String neighborPid;

  public ForwardInfo(String srcPrefix, String destprefix, String neighborPid) {
    this.srcPrefix = srcPrefix;
    this.destPrefix = destprefix;
    this.neighborPid = neighborPid;
  }

  @Override
  public String toString() {
    return String.format("src: %s dst: %s neighborPid: %s", srcPrefix,
      destPrefix, neighborPid);
  }
}
