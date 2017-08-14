package safe.riak;
import java.io.File;

import com.typesafe.config.*;

class SliceConfig {
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
	public String site;
	Config conf;

	public SliceConfig(String configfile){
		System.out.println("config");

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
		site=conf.getString("config.site");
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
