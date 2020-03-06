package exoplex.sdx.core.restutil;

public class StitchRequest {
    public String sdxsite;
    //customer Safe key hash
    public String gateway;
    public String ip;
    public String ckeyhash;
    public String cslice;
    public String creservid;
    public String sdxnode;
    public String secret;

    @Override
    public String toString() {
    return String.format("%s %s %s %s %s %s", sdxsite, cslice, creservid, sdxnode, gateway, ip);
    }
}
