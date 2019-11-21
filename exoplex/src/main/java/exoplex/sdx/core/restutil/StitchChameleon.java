package exoplex.sdx.core.restutil;

public class StitchChameleon {
  public String sdxsite;
  public String sdxnode;
  public String ckeyhash;
  public String stitchport;
  public String vlan;
  public String gateway;
  public String ip;
  public String creservid;
  public String toString() {
    return "{\"sdxsite\": " + sdxsite + ", \"sdxnode\": " + sdxnode + ", \"ckeyhash\":" + ckeyhash
      + ", \"stitchport\":" + stitchport + ", \"vlan\":" + vlan + ", \"gateway\":" + gateway + ", \"creservid\":" + creservid + "}";
  }
}
