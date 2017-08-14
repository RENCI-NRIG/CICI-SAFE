package safe.riak;
import java.io.File;

import com.typesafe.config.*;

class SliceConfig {
	public String sshkey;
	public String exogenism;
	public String exogenipem;
	public String slicename;
	public String site;
	Config conf;

	public SliceConfig(String configfile){
		System.out.println("config");

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
