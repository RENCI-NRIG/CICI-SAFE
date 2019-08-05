package exoplex.sdx.core.restutil;

/*
Admin command, in routing scenerio, the sdx can work as a client to peer with other sdx networks.
SDX server provides this interface for administrator to issue commands for peering.
 */
public class AdminCmd {
  public String operation;
  public String[] params;

  public String toString() {
    String parameters = String.join(",", params);
    return String.format("{\"operation\":\"%s\", \"params:\":[%s]}", operation, parameters);
  }
}
