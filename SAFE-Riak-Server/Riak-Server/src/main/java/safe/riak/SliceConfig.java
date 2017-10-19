package safe.riak;
import java.io.File;

import org.apache.log4j.Logger;

import com.typesafe.config.*;

class SliceConfig {
	final static Logger logger = Logger.getLogger(SliceConfig.class);

	
	public String sshkey;
	public String exogenism;
	public String exogenipem;
	public String slicename;
	public String site;
	Config conf;

	public SliceConfig(String configfile){
		logger.debug("config");

		File myConfigFile = new File(configfile);
		Config fileConfig = ConfigFactory.parseFile(myConfigFile);
		conf = ConfigFactory.load(fileConfig);

		sshkey=conf.getString("config.sshkey");
		exogenism=conf.getString("config.exogenism");
		exogenipem=conf.getString("config.exogenipem");
		slicename=conf.getString("config.slicename");
		site=conf.getString("config.site");
	}

	public String get(String name){
		return conf.getString(name);
	}
}
