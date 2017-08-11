package safe.sdx.sdx;

import java.util.ArrayList;
import java.rmi.*;

public interface ServiceAPI extends java.rmi.Remote
{
  // String sayHello() throws RemoteException;
  String stitchRequest(String slicename, String nodename, String customer_keyhash,String customerName, String ResrvID,String secret) throws RemoteException;
  void notifyPrefix(String dest, String gateway, String router,String customer_keyhash) throws RemoteException;
}
