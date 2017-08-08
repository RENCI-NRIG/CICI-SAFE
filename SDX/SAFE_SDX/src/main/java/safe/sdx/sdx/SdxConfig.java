package safe.sdx;
import com.typesafe.config.*;

class SdxConfig {
  public String sshkey;
  public String type;
  public String safekey;
  public String exogenism;
  public String exogenipem;
  public String slicename;
  public String ipprefix;
  public String riakserver;
  public String javasecuritypolicy;
  public String scriptsdir;
 
  public SdxConfig(String configfile){
    System.out.println("config");
    Config conf=ConfigFactory.load(configfile);
    sshkey=conf.getString("config.sshkey");
    type=conf.getString("config.type");
    safekey=conf.getString("config.safekey");
    exogenism=conf.getString("config.exogenism");
    exogenipem=conf.getString("config.exogenipem");
    slicename=conf.getString("config.slicename");
    ipprefix=conf.getString("config.ipprefix");
    javasecuritypolicy=conf.getString("config.javasecuritypolicy");
    scriptsdir=conf.getString("config.scriptsdir");
    if(conf.hasPath("config.riakserver")){
      riakserver=conf.getString("config.riakserver");
    }
    else{
      riakserver="152.3.145.36";
    }
  }
}
