package exoplex.sdx.network;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;

public class Router {
  final static Logger logger = LogManager.getLogger(Router.class);
  String routerName = "";
  String dpid = "";
  String managementIP = "";
  String domain;
  String controller;

  HashSet<String> interfaces = new HashSet<String>();
  HashMap<String, String> customergateways = new HashMap<>();

  public Router(String rid, String switch_id, String controller, String ip) {
    routerName = rid;
    dpid = switch_id;
    this.controller = controller;
    this.managementIP = ip;
  }

  public String getDomain() {
    return domain;
  }

  public void setDomain(String domain) {
    this.domain = domain;
  }

  public Collection<String> getInterfaces() {
    return interfaces;
  }

  public void addInterface(String interfaceName) {
    interfaces.add(interfaceName);
  }

  public void delInterface(String interfaceName) {
    interfaces.remove(interfaceName);
  }

  public void addGateway(String linkName, String gw) {
    logger.debug("Gateway " + gw + " added to " + routerName);
    customergateways.put(linkName, gw);
  }

  public String getController() {
    return this.controller;
  }

  public void setController(String controller) {
    this.controller = controller;
  }

  public void delGateway(String gw) {
    customergateways.remove(gw);
  }

  public boolean hasGateway(String gw) {
    return customergateways.containsValue(gw);
  }


  public String getGateWay(String linkName) {
    return customergateways.getOrDefault(linkName, null);
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
