package safe.exogeni.client;
import java.io.File;

import org.apache.log4j.Logger;

import com.typesafe.config.*;

import safe.utils.Exec;

class SliceConfig {
	final static Logger logger = Logger.getLogger(SliceConfig.class);	
	
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
 
  public SliceConfig(String configfile){
    logger.debug("config");
    
	File myConfigFile = new File(configfile);
	Config fileConfig = ConfigFactory.parseFile(myConfigFile);
	conf = ConfigFactory.load(fileConfig);

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
