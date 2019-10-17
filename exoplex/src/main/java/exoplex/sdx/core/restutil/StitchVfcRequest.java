package exoplex.sdx.core.restutil;

public class StitchVfcRequest {
  public String vfcsite;
  public String vlan;
  //customer Safe key hash
  public String gateway;
  public String ip;
  public String ckeyhash;
  public String cslice;

  @Override
  public String toString() {
    return String.format("%s %s %s %s %s %s", vfcsite, vlan, gateway, ip, ckeyhash, cslice);
  }
}
