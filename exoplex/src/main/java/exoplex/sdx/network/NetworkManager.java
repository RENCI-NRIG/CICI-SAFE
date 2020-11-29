package exoplex.sdx.network;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import sun.nio.ch.Net;

import java.util.*;

public class NetworkManager {
  final static Logger logger = LogManager.getLogger(NetworkManager.class);

  final static int MAX_RATE = 2000000;
  private HashMap<String, Router> nameRouterMap = new HashMap<>();
  private HashMap<String, Router> dpidRouterMap = new HashMap<>();
  private HashMap<String, Link> linkMap = new HashMap<>();
  private HashMap<String, Interface> interfaceMap = new HashMap<>();
  private HashMap<String, Interface> ipInterfaceMap = new HashMap<>();
  private HashMap<String, String> macInterfaceMap = new HashMap<>();
  private HashMap<String, String> macPortMap = new HashMap<>();
  private HashMap<String, HashSet<String>> dpidMacsMap = new HashMap<>();

  public NetworkManager() {
    logger.debug("initialize network manager");
  }

  public Router getRouter(String routerName) {
    logger.info(String.format("getDPID %s", routerName));
    if (!nameRouterMap.containsKey(routerName)) {
        logger.warn("Router not found: " + routerName);
    }
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

  public void updateInterface(String name, String mac) {
    Interface intf = interfaceMap.computeIfAbsent(name,
      k -> new Interface(name, null, null, mac, macPortMap.getOrDefault(mac, null), null));
    intf.port = macPortMap.getOrDefault(mac, null);
    if (mac != null) {
      macInterfaceMap.put(mac, name);
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
      return linkMap.get(intf.getLinkName()).getPairNodeName(intf.getNodeName());
    }
    return null;
  }

  public void addLink(String linkName, String ipa, String ra, String gw) {
    //TODO gw
    Link link = new Link(linkName, ra);
    String iface = NetworkUtil.computeInterfaceName(ra, linkName);
    Interface intf = interfaceMap.getOrDefault(iface,
      new Interface(iface, null, null, null, null, null));
    intf.setNodeName(ra);
    intf.setLinkName(linkName);
    intf.setIP(ipa);
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
    String ifaceA = NetworkUtil.computeInterfaceName(ra, linkName);
    String ifaceB = NetworkUtil.computeInterfaceName(rb, linkName);
    Interface intf1 = new Interface(ifaceA, ra, linkName, getMacOfInterface(ifaceA),
      getPort(ra, linkName), ipa);
    putInterface(intf1);
    Interface intf2 = new Interface(ifaceB, rb, linkName,
      getMacOfInterface(ifaceB), getPort(rb, linkName), ipb);
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

  public void updatePortData(String dpid, JSONArray ports) {
    HashSet<String> macSet = dpidMacsMap.computeIfAbsent(dpid, k -> new HashSet<>());
    HashSet<String> staleMacs = new HashSet<>(macSet);
    for (int i = 0; i < ports.length(); i++) {
      try {
        JSONObject port = ports.getJSONObject(i);
        String hwAddr = port.getString("hw_addr");
        String portNum;
        portNum = String.valueOf(port.getInt("port_no"));
        if (staleMacs.contains(hwAddr)) {
          staleMacs.remove(hwAddr);
        } else {
          macSet.add(hwAddr);
        }
        String oldVal = macPortMap.put(hwAddr, portNum);
        if (!portNum.equals(oldVal)) {
          logger.debug(String.format("Port number for hw: %s on dpid: %s updated from %s to " +
                  "%s", hwAddr, dpid, oldVal, portNum));
          if (macInterfaceMap.containsKey(hwAddr)) {
            updateInterface(macInterfaceMap.get(hwAddr), hwAddr);
          }
        }
      } catch (Exception e) {
        // when port number is not an Integer, ignore and continue. (LOCAL)
        logger.trace(e.getMessage());
      }
    }
    //remove stale interface
    for (String staleMac: staleMacs) {
        macSet.remove(staleMac);
        macPortMap.remove(staleMac);
        String interfaceName = macInterfaceMap.remove(staleMac);
        interfaceMap.remove(interfaceName);
    }
  }

  public int getPortCount(String dpid) {
    return dpidMacsMap.getOrDefault(dpid, new HashSet<>()).size();
  }

  private String getMacOfInterface(String ifName) {
    Interface intf = interfaceMap.getOrDefault(ifName, null);
    if(intf != null) {
      return intf.macAddr;
    } else {
      return null;
    }
  }

  public String getPort(String routerName, String linkName) {
    try {
      String iface = NetworkUtil.computeInterfaceName(routerName, linkName);
      String mac = getMacOfInterface(iface);
      return macPortMap.getOrDefault(mac, null);
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
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
