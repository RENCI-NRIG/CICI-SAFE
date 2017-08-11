package safe.sdx.routing;

import java.util.ArrayList;
import java.rmi.*;

public interface RoutingAPI extends java.rmi.Remote
{
  // String sayHello() throws RemoteException;
  String stitchRequest(String slicename, String nodename, String customer_keyhash,String customerName, String ResrvID,String secret) throws RemoteException;
  boolean advertiseRoute(String dest, ArrayList<String> path, String customer_keyhash,String token) throws RemoteException;
}
