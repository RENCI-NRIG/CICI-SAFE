package exoplex.sdx.safe;

import exoplex.common.slice.Scripts;
import exoplex.common.utils.Exec;
import exoplex.common.utils.SafeUtils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import sun.awt.windows.ThemeReader;

public class SafeManager {
  final static Logger logger = LogManager.getLogger(SafeManager.class);
  private String safeServerIp;
  private String safeServer;
  private String safeKeyFile;
  private String sshKey=null;
  private String safeKeyHash= null;

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

  public void restartSafeServer(){
    Exec.sshExec("root", safeServerIp, Scripts.restartSafe_v1(),sshKey);
  }

  public void deploySafeScripts(){

  }

  public boolean verifySafeInstallation(String riakIp){
    if(safeServerAlice()){
      return true;
    }
    while(true) {
      String result = Exec.sshExec("root", safeServerIp, "docker images", sshKey)[0];
      if(result.contains("safeserver")){
        break;
      }else{
        Exec.sshExec("root", safeServerIp, Scripts.getSafeScript_v1(riakIp), sshKey);
      }
    }
    while(true){
      String result = Exec.sshExec("root", safeServerIp, "docker ps", sshKey)[0];
      if(result.contains("safe")){
        break;
      }else{
        Exec.sshExec("root", safeServerIp, Scripts.getSafeScript_v1(riakIp), sshKey);
      }
    }
    Exec.sshExec("root", safeServerIp, Scripts.restartSafe_v1(), sshKey);
    while (true){
      if(safeServerAlice()){
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

  private boolean safeServerAlice(){
    try{
      SafeUtils.getPrincipalId(safeServer, "sdx");
    }catch (Exception e){
      logger.debug("Safe server not alive yet");
      return false;
    }
    return true;
  }
}
