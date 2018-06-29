package sdx.network;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.HashSet;

public class Router {
  final static Logger logger = LogManager.getLogger(Router.class);
  String routerName = "";
  String dpid = "";
  String managementIP = "";

  HashSet<String> interfaces = new HashSet<String>();
  HashSet<String> customergateways = new HashSet<>();

  public Router(String rid, String switch_id, String ip) {
    routerName = rid;
    dpid = switch_id;
    this.managementIP = ip;
  }

  public Collection<String> getInterfaces(){
    return interfaces;
  }

  public void addInterface(String interfaceIP) {
    interfaces.add(interfaceIP);
  }

  public void addGateway(String gw) {
    logger.debug("Gateway " + gw + " added to " + routerName);
    customergateways.add(gw);
  }

  public boolean hasGateway(String gw) {
    return customergateways.contains(gw);
  }

  public boolean hasIP(String ip) {
    return interfaces.contains(ip);
  }

  public String getDPID() {
    return dpid;
  }

  public String getRouterName() {
    return routerName;
  }

  public String getManagementIP() {
    return this.managementIP;
  }

}
