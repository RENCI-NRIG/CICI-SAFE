package exoplex.sdx.safe;

import exoplex.common.slice.Scripts;
import exoplex.common.utils.Exec;
import exoplex.common.utils.SafeUtils;

import exoplex.sdx.bgp.BgpAdvertise;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import safe.SdxRoutingSlang;

public class SafeManager {
  final static Logger logger = LogManager.getLogger(SafeManager.class);
  private String safeServerIp;
  private String safeServer;
  private String safeKeyFile;
  private String sshKey=null;
  private String safeKeyHash= null;
  //was v4
  public final static String safeDockerImage = "safeserver-v7";
  //was prdn.sh
  public final static String safeServerScript = "sdx-routing.sh";

  public SafeManager(String ip, String safeKeyFile, String sshKey){
    safeServerIp = ip;
    safeServer = safeServerIp + ":7777";
    this.safeKeyFile = safeKeyFile;
    this.sshKey = sshKey;
  }

  public String getSafeKeyHash(){
    if(safeKeyHash == null){
      safeKeyHash = SafeUtils.getPrincipalId(safeServer, safeKeyFile);
    }
    return safeKeyHash;
  }

  public boolean authorizePrefix(String cushash, String cusip){
    String[] othervalues=new String[2];
    othervalues[0]=cushash;
    othervalues[1]=cusip;
    String message= SafeUtils.postSafeStatements(safeServer,"ownPrefix",
      getSafeKeyHash(),
      othervalues);
    if(message !=null && message.contains("Unsatisfied")){
      return false;
    }
    else
      return true;
  }


  public boolean authorizeConnectivity(String srchash, String srcip, String dsthash, String dstip){
    String[] othervalues=new String[4];
    othervalues[0]=srchash;
    othervalues[1]= String.format("ipv4\\\"%s\\\"", srcip);
    othervalues[2]=dsthash;
    othervalues[3]= String.format("ipv4\\\"%s\\\"", dstip);
    return SafeUtils.authorize(safeServer, "authZByUserAttr", getSafeKeyHash(),
      othervalues);
  }

  public void postPathToken(BgpAdvertise advertise){
    String[] params = new String[4];
    params[0] = advertise.safeToken;
    params[1] = advertise.getDestPrefix();
    params[2] = advertise.advertiserPID;
    params[3] = String.valueOf(advertise.route.size());
    post(SdxRoutingSlang.postPathToken, params);
  }

  public boolean authorizeStitchRequest(String customer_slice,
                                        String customerName,
                                        String ReservID,
                                        String keyhash,
                                        String slicename,
                                        String nodename
  ){
    /** Post to remote safesets using apache httpclient */
    String[] othervalues=new String[5];
    othervalues[0]=customer_slice;
    othervalues[1]=customerName;
    othervalues[2]=ReservID;
    othervalues[3]=slicename;
    othervalues[4]=nodename;
    String message= SafeUtils.postSafeStatements(safeServer,"verifyStitch",
      getSafeKeyHash(),
      othervalues);
    if(message ==null || message.contains("Unsatisfied")){
      return false;
    }
    else
      return true;
  }

  public String post(String operation, String[] params){
    String res = SafeUtils.postSafeStatements(safeServer, operation, getSafeKeyHash(), params);
    return SafeUtils.getToken(res);
  }

  public boolean authorizeBgpAdvertise(BgpAdvertise bgpAdvertise){
    String[] othervalues=new String[4];
    othervalues[0] = bgpAdvertise.ownerPID;
    othervalues[1] = bgpAdvertise.getDestPrefix();
    othervalues[2] = bgpAdvertise.getPath();
    othervalues[3] = bgpAdvertise.safeToken;
    return SafeUtils.authorize(safeServer, SdxRoutingSlang.verifyRoute, getSafeKeyHash(), othervalues);
  }

  public boolean authorizeStitchRequest(String customerSafeKeyHash,
                                        String customerSlice
  ){
    /** Post to remote safesets using apache httpclient */
    String[] othervalues=new String[2];
    othervalues[0]=customerSafeKeyHash;
    String saHash = SafeUtils.getPrincipalId(safeServer, "key_p3");
    String sdxHash = SafeUtils.getPrincipalId(safeServer, this.safeKeyFile);
    othervalues[1]=saHash + ":" + customerSlice;
    return SafeUtils.authorize(safeServer, "authorizeStitchByUID", sdxHash, othervalues);
  }

  public boolean verifyAS(String owner, String dstIP, String as, String token)
  {
    /** Post to remote safesets using apache httpclient */
    String[] othervalues=new String[4];
    othervalues[0]= owner;
    othervalues[1] = dstIP;
    othervalues[2] = as;
    othervalues[3] = token;
    String sdxHash = SafeUtils.getPrincipalId(safeServer, this.safeKeyFile);
    return SafeUtils.authorize(safeServer, "verifyAS", sdxHash, othervalues);
  }

  public boolean authorizeChameleonStitchRequest(String customerSafeKeyHash,
    String stitchPort,
    String vlan
  ){
    /** Post to remote safesets using apache httpclient */
    String[] othervalues=new String[3];
    othervalues[0]=customerSafeKeyHash;
    othervalues[1]=stitchPort;
    othervalues[2]=vlan;
    String sdxHash = SafeUtils.getPrincipalId(safeServer, "sdx");
    return SafeUtils.authorize(safeServer, "authorizeChameleonStitchByUID", sdxHash, othervalues);
  }

  public void restartSafeServer(){
    Exec.sshExec("root", safeServerIp, Scripts.restartSafe_v1(safeServerScript),sshKey);
  }

  public void deploySafeScripts(){

  }

  public boolean verifySafeInstallation(String riakIp){
    if(safeServerAlive()){
      return true;
    }
    while(true) {
      String result = Exec.sshExec("root", safeServerIp, "docker images", sshKey)[0];
      if(result.contains(safeDockerImage)){
        break;
      }else{
        Exec.sshExec("root", safeServerIp, Scripts.getSafeScript_v1(riakIp, safeDockerImage,
          safeServerScript), sshKey);
      }
    }
    while(true){
      String result = Exec.sshExec("root", safeServerIp, "docker ps", sshKey)[0];
      if(result.contains("safe")){
        break;
      }else{
        Exec.sshExec("root", safeServerIp, Scripts.getSafeScript_v1(riakIp, safeDockerImage,
          safeServerScript),
          sshKey);
      }
    }
    Exec.sshExec("root", safeServerIp, Scripts.restartSafe_v1(safeServerScript), sshKey);
    while (true){
      if(safeServerAlive()){
        break;
      }else{
        try{
          Thread.sleep(10000);
        }catch (Exception e){
        }
      }
    }
    return true;
  }

  private boolean safeServerAlive(){
    try{
      SafeUtils.getPrincipalId(safeServer, "sdx");
    }catch (Exception e){
      logger.debug("Safe server not alive yet");
      return false;
    }
    return true;
  }
}
