package exoplex.sdx.safe;

import exoplex.common.slice.Scripts;
import exoplex.common.utils.Exec;
import exoplex.common.utils.SafeUtils;

import javax.script.ScriptContext;

public class SafeManager {
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

  public boolean verifySafeInstallation(){
    return true
        ;
  }
}
