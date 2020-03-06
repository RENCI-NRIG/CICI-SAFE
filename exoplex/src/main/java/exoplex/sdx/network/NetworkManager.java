package exoplex.sdx.network;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

public class NetworkManager {
  final static Logger logger = LogManager.getLogger(NetworkManager.class);

  final static int MAX_RATE = 2000000;
  private HashMap<String, Router> nameRouterMap = new HashMap<>();
  private HashMap<String, Router> dpidRouterMap = new HashMap<>();
  private HashMap<String, Link> linkMap = new HashMap<>();
  private HashMap<String, Interface> interfaceMap = new HashMap<>();
  private HashMap<String, Interface> ipInterfaceMap = new HashMap<>();

  public NetworkManager() {
    logger.debug("initialize network manager");
  }

  public Router getRouter(String routerName) {
    logger.info(String.format("getDPID %s", routerName));
    Router logRouter = nameRouterMap.get(routerName);
    return logRouter;
  }

  public Collection<String> getAllRouters() {
    return nameRouterMap.keySet();
  }

  public Collection<String> getAllDpids() {
    return dpidRouterMap.keySet();
  }

  public Router getRouterByDPID(String dpid) {
    Router router = dpidRouterMap.get(dpid);
    return router;
  }

  public String getRouterByGateway(String gw) {
    for (Router router : nameRouterMap.values()) {
      if (router.hasGateway(gw)) {
        return router.getRouterName();
      }
    }
    return null;
  }

  public void putRouter(Router logRouter) {
    dpidRouterMap.put(logRouter.getDPID(), logRouter);
    nameRouterMap.put(logRouter.getRouterName(), logRouter);
  }

  public void putLink(Link l) {
    linkMap.put(l.getLinkName(), l);
  }

  private void removeLink(String linkName) {
    linkMap.remove(linkName);
  }

  private void putInterface(Interface intf) {
    interfaceMap.put(intf.getName(), intf);
    if (intf.getIp() != null) {
      ipInterfaceMap.put(intf.getIp(), intf);
    }
  }

  private void removeInterface(String linkName, String routerName) {
    String intfName = NetworkUtil.computeInterfaceName(routerName, linkName);
    interfaceMap.remove(intfName);
  }

  public void updateInterface(String name, String port, String mac) {
    Interface intf = interfaceMap.get(name);
    if (intf != null) {
      intf.setMacAddr(mac);
      intf.setPort(port);
      interfaceMap.put(name, intf);
    }
  }

  public String getPairIP(String ip) {
    Interface intf = ipInterfaceMap.get(ip);
    if (intf != null) {
      Interface pairIntf = interfaceMap.get(linkMap.get(intf.getLinkName()).getPairInterfaceName(intf.getName()));
      return pairIntf.getIp();
    }
    return null;
  }

  public String getPairRouter(String ip) {
    Interface intf = ipInterfaceMap.get(ip);
    if (intf != null) {
      return linkMap.get(intf.getLinkName()).getPairNodeame(intf.getNodeName());
    }
    return null;
  }

  public void addLink(String linkName, String ipa, String ra, String gw) {
    //TODO gw
    Link link = new Link(linkName, ra);
    Interface intf = new Interface(ra, linkName, null, null, ipa);
    putLink(link);
    putInterface(intf);
    Router logRouter = getRouter(ra);
    if (logRouter != null) {
      logRouter.addGateway(linkName, gw);
      logRouter.addInterface(intf.getName());
      putRouter(logRouter);
      //logRouters.put(ra,logRouter);
    }
  }

  public void delLink(String linkName, String routerName, String gw) {
    removeLink(linkName);
    removeInterface(linkName, routerName);
    Router logRouter = getRouter(routerName);
    if (logRouter != null) {
      logRouter.delGateway(gw);
      logRouter.delInterface(NetworkUtil.computeInterfaceName(routerName, linkName));
      putRouter(logRouter);
      //logRouters.put(ra,logRouter);
    }
  }

  public void addLink(String linkName, String ipa, String ra, String ipb, String rb, long cap) {
    //logger.debug(ipa+" "+ipb);
    Link link = new Link(linkName, ra, rb, cap);
    putLink(link);
    Interface intf1 = new Interface(ra, linkName, null, null, ipa);
    putInterface(intf1);
    Interface intf2 = new Interface(rb, linkName, null, null, ipb);
    putInterface(intf2);

    Router logRouter = getRouter(ra);
    if (logRouter != null) {
      logRouter.addInterface(intf1.getName());
      putRouter(logRouter);
      //logRouters.put(ra,logRouter);
    }
    Router logRouterb = getRouter(rb);
    if (logRouterb != null) {
      logRouterb.addInterface(intf2.getName());
      putRouter(logRouterb);
      //logRouters.put(ra,logRouter);
    }
  }

  public List<String> getNeighborNodes(String routerName) {
    ArrayList<String> neighbors = new ArrayList<>();
    for (Interface intf : getNeighborInterfaces(routerName)) {
      neighbors.add(intf.getNodeName());
    }
    return neighbors;
  }

  public List<Interface> getNeighborInterfaces(String routerName) {
    ArrayList<Interface> nintf = new ArrayList<>();
    for (String intfName : nameRouterMap.get(routerName).getInterfaces()) {
      Link link = linkMap.get(interfaceMap.get(intfName).getLinkName());
      if (intfName.equals(link.getInterfaceB()) && link.getInterfaceA() != null) {
        nintf.add(interfaceMap.get(link.getInterfaceA()));
      } else if (intfName.equals(link.getInterfaceA()) && link.getInterfaceB() != null) {
        nintf.add(interfaceMap.get(link.getInterfaceB()));
      }
    }
    return nintf;
  }

  public String getPairInterface(String iface) {
    Link link = linkMap.get(interfaceMap.get(iface).getLinkName());
    return iface.equals(link.getInterfaceA()) ? link.getInterfaceB() : link.getInterfaceA();
  }

  public Link getLink(String linkName) {
    return linkMap.get(linkName);
  }

  public Interface getInterface(String interfaceName) {
    return interfaceMap.get(interfaceName);
  }

  public String getInterfaceIP(String interfaceName) {
    return interfaceMap.get(interfaceName).getIp();
  }

  public String getGateWayOfExternalLink(String linkName) {
    return getRouter(getLink(linkName).getNodeA()).getGateWay(linkName);
  }

  public Collection<Router> getRouters() {
    return nameRouterMap.values();
  }

  public Collection<Link> getLinks() {
    return linkMap.values();
  }

  public Collection<String> getInterfaces() {
    return interfaceMap.keySet();
  }
}
