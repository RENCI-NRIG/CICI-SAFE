package safe.sdx.sdx;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

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
  Config conf;
 
  public SdxConfig(String configfile){
    System.out.println("config");
    
    File myConfigFile = new File(configfile);
    Config fileConfig = ConfigFactory.parseFile(myConfigFile);
    conf = ConfigFactory.load(fileConfig);
    
    //conf=ConfigFactory.load(configfile);
    sshkey=conf.getString("config.sshkey");
    type=conf.getString("config.type");
    safekey=conf.getString("config.safekey");
    exogenism=conf.getString("config.exogenism");
    exogenipem=conf.getString("config.exogenipem");
    slicename=conf.getString("config.slicename");
    ipprefix=conf.getString("config.ipprefix");
    javasecuritypolicy=conf.getString("config.javasecuritypolicy");
    if(conf.hasPath("config.riakserver")){
      riakserver=conf.getString("config.riakserver");
    }
    else{
      riakserver="152.3.145.36";
    }
  }

  public String get(String name){
    return conf.getString(name);
  }
}
