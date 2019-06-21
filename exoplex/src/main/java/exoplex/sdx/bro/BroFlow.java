package exoplex.sdx.bro;

public class BroFlow {
  public String src;
  public String dst;
  public long bw;
  public String routerName;

  public BroFlow(String s, String d, long b, String router) {
    this.src = s;
    this.dst = d;
    this.bw = b;
    this.routerName = router;
  }
}

