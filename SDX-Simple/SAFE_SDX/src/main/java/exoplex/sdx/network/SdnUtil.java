package exoplex.sdx.network;

import exoplex.common.utils.HttpUtil;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

public class SdnUtil {
  static final Logger logger = LogManager.getLogger(SdnUtil.class);

  static int cookie = 1026;

  static String queueURL(String controller, String dpid) {
    return "http://" + controller + "/qos/queue/" + dpid;
  }

  static JSONObject queueData(int maxrate, List<Long> queuerate) {
    JSONObject params = new JSONObject();
    params.put("type", "linux-htb");
    params.put("max_rate", String.valueOf(maxrate));
    JSONArray queues = new JSONArray();
    for (Long r : queuerate) {
      JSONObject q = new JSONObject();
      q.put("max_rate", String.valueOf(r));
      queues.put(q);
    }
    params.put("queues", queues);
    logger.debug("queueData" + params.toString());
    return params;
  }

  static String qosRuleURL(String controller, String dpid) {
    return "http://" + controller + "/qos/rules/" + dpid;
  }

  static JSONObject qosRuleData(JSONObject match, int queue_id) {
    JSONObject params = new JSONObject();
    params.put("match", match);
    JSONObject actionjson = new JSONObject();
    actionjson.put("queue", String.valueOf(queue_id));
    params.put("actions", actionjson);
    logger.debug("qosRuleData: " + params.toString());
    return params;
  }

  static String[] mirrorCMD(String controller, String dpid, String source, String dst, String gw) {
    String[] res = new String[3];
    res[0] = "http://" + controller + ":8080/router/" + dpid;
    //res[1] = "{\"source\":\"" + source + "\", \"destination\": \"" + dst + "\", \"mirror\":\"" + gw + "\"}";
    JSONObject params = new JSONObject();
    params.put("mirror", gw);
    if (source != null) {
      params.put("source", source);
    }
    if (dst != null) {
      params.put("destination", dst);
    }
    res[1] = params.toString();
    res[2] = "postJSON";
    return res;
  }

  static String[] addrCMD(String addr, String dpid, String controller) {
    //String cmd="curl -X POST -d {\"address\":\""+addr+"\"} "+controller+"/router/"+dpid;
    String[] res = new String[4];
    res[0] = "http://" + controller + "/router/" + dpid;
    res[1] = "{\"address\":\"" + addr + "\"} ";
    res[2] = "postJSON";
    //res[3] will be replaced with command result
    res[3] = "resultHolder";
    return res;
  }

  static String[] delAddrCMD(String addrId, String dpid, String controller){
    String[] cmd = new String[3];
    cmd[0] = "http://" + controller + "/router/" + dpid;
    cmd[1] = "{\"address_id\":\"" + addrId + "\"}";
    cmd[2] = "delete";
    return cmd;

  }

  static String[] delMirrorCMD(String routeId, String dpid, String controller) {
    String[] cmd = new String[3];
    cmd[0] = "http://" + controller + "/router/" + dpid;
    cmd[1] = "{\"mirror_id\":\"" + routeId + "\"}";
    cmd[2] = "delete";
    return cmd;
  }

  static String[] routingCMD(String dst, String gw, String dpid, String controller) {
    //String cmd="curl -X POST -d {\"destination\":\""+dst+"\",\"gateway\":\""+gw+"\"} "+controller+"/router/"+dpid;
    String[] res = new String[4];
    res[0] = "http://" + controller + "/router/" + dpid;
    JSONObject obj = new JSONObject();
    obj.put("destination", dst);
    obj.put("gateway", gw);
    res[1] = obj.toString();
    res[2] = "postJSON";
    res[3] = "resultHolder";
    return res;
  }

  static String[] routingCMD(String dst, String src, String gw, String dpid, String controller) {
    //String cmd="curl -X POST -d {\"destination\":\""+dst+"\",\"source\":\""+src+"\",\"gateway\":\""+gw+"\"} "+controller+"/router/"+dpid;
    String[] cmd = new String[4];
    cmd[0] = "http://" + controller + "/router/" + dpid;
    cmd[1] = "{\"destination\":\"" + dst + "\",\"source\":\"" + src + "\",\"gateway\":\"" + gw + "\"}";
    cmd[2] = "postJSON";
    cmd[3] = "resultHolder";
    return cmd;
  }

  static String[] delRoutingCMD(String routeId, String dpid, String controller) {
    String[] cmd = new String[3];
    cmd[0] = "http://" + controller + "/router/" + dpid;
    cmd[1] = "{\"route_id\":\"" + routeId + "\"}";
    cmd[2] = "delete";
    return cmd;
  }

  static String[] ovsdbCMD(String mip, String dpid, String controller) {
    //String cmd="curl -X PUT -d \'\"tcp:"+r.getManagementIP()+":6632\"\' "+controller+"/v1.0/conf/switches/"+r.getDPID()+"/ovsdb_addr";
    String[] res = new String[3];
    res[1] = "\"tcp:" + mip + ":6632\"";
    res[0] = "http://" + controller + "/v1.0/conf/switches/" + dpid + "/ovsdb_addr";
    res[2] = "putString";
    return res;
  }

  static Collection<String> getAllSwitches(String controller){
    String url = "http://" + controller + "/stats/switches";
    String res = HttpUtil.get(url);
    String[] dpids = res.replace("[","")
        .replace("]", "")
        .replace("\n", "")
        .replace(" ", "")
        .split(",");
    ArrayList<String> retVal =  new ArrayList<>();
    for(String dpid: dpids){
      try {
        String sdpid = Long.toHexString(Long.valueOf(dpid));
        retVal.add(StringUtils.leftPad(sdpid, 16, '0'));
      }catch (Exception e){}
    }
    return retVal;
  }

  static JSONObject getPortDesc(String controller, String dpid){
    String url = "http://" + controller + "/stats/portdesc/" + Long.parseLong(dpid, 16);
    String res = HttpUtil.get(url);
    try{
      JSONObject obj = new JSONObject(res);
      return obj;
    }catch (Exception e){
      logger.error(e.getMessage());
      logger.error(res);
    }
    return null;
  }

  /*
  enum ofp_group_type {
  OFPGT_ALL = 0, /* All (multicast/broadcast) group.
    OFPGT_SELECT = 1, /* Select group.
    OFPGT_INDIRECT = 2, /* Indirect group.
    OFPGT_FF = 3, /* Fast failover group.
  }
  enum ofp_group {
   Last usable group number.
    OFPG_MAX = 0xffffff00,
    OFPG_ALL = 0xfffffffc,  Represents all groups for group delete commands.
    OFPG_ANY = 0xffffffff  Wildcard group used only for flow stats
  */
  public static String addSelectGroup(String controller, String dpid, int groupId, HashMap<Integer, Integer>ports){
    String url = "http://" + controller + "/stats/groupentry/add";
    JSONObject entity = new JSONObject();
    entity.put("dpid", Long.parseLong(dpid, 16));
    entity.put("type", "SELECT");
    entity.put("group_id", groupId);
    JSONArray buckets = new JSONArray();
    for(int port: ports.keySet()){
      JSONObject bucket = new JSONObject();
      JSONArray actions = new JSONArray();
      JSONObject outAction = new JSONObject();
      outAction.put("type", "OUTPUT");
      outAction.put("port", port);
      actions.put(outAction);
      bucket.put("actions", actions);
      bucket.put("weight", ports.get(port));
      buckets.put(bucket);
    }
    entity.put("buckets", buckets);
    return HttpUtil.postJSON(url, entity);
  }

  public static  String getGroupStats(String controller, String dpid, int groupId){
    String url = "http://" + controller + "/stats/group/" + Long.parseLong(dpid, 16) + "/" + groupId;
    return HttpUtil.get(url);
  }

  public static  String getGroupDescStats(String controller, String dpid, int groupId){
    String url = "http://" + controller + "/stats/groupdesc/" + Long.parseLong(dpid, 16) + "/" + groupId;
    return HttpUtil.get(url);
  }

  public static void deleteGroup(String controller, String dpid, int groupId){
    String url = "http://" + controller + "/stats/groupentry/delete";
    JSONObject entity = new JSONObject();
    entity.put("dpid", Long.parseLong(dpid, 16));
    entity.put("group_id", groupId);
    HttpUtil.postJSON(url, entity);
  }

  public static String setRoutingFlow(String controller, String dpid, String destIP, int groupId){
    String url = "http://" + controller + "/stats/flowentry/add";

    JSONObject entity = new JSONObject();
    entity.put("dpid", Long.parseLong(dpid, 16));
    entity.put("cookie", cookie++);
    entity.put("cookie_mask", 1);
    entity.put("table_id", 0);
    //entity.put("idle_timeout", 30);
   // entity.put("hard_timeout", 30);
    entity.put("priority", 11111);
    entity.put("flags", 1);
    JSONObject match = new JSONObject();
    match.put("nw_dst", destIP);
    match.put("dl_type", 2048);
    entity.put("match", match);
    JSONObject action = new JSONObject();
    action.put("type","GROUP");
    action.put("group_id",groupId);
    JSONArray actions = new JSONArray();
    actions.put(action);
    entity.put("actions", actions);
    return HttpUtil.postJSON(url, entity);
  }

  public static String setRoutingOutputFlow(String controller, String dpid, String destIP, int port){
    String url = "http://" + controller + "/stats/flowentry/add";

    JSONObject entity = new JSONObject();
    entity.put("dpid", Long.parseLong(dpid, 16));
    entity.put("cookie", cookie++);
    entity.put("cookie_mask", 1);
    entity.put("table_id", 0);
    //entity.put("idle_timeout", 300);
    //entity.put("hard_timeout", 300);
    entity.put("priority", 11111);
    entity.put("flags", 1);
    JSONObject match = new JSONObject();
    match.put("nw_dst", destIP);
    match.put("dl_type", 2048);
    entity.put("match", match);
    JSONObject action = new JSONObject();
    action.put("type","OUTPUT");
    action.put("port",port);
    JSONArray actions = new JSONArray();
    actions.put(action);
    entity.put("actions", actions);
    return HttpUtil.postJSON(url, entity);
  }

  public static void deleteAllFlows(String controller, String dpid){
    String url = "http://" + controller + "/stats/flowentry/clear/" + Long.parseLong(dpid, 16);
    HttpUtil.delete(url);
  }

  public static String getAllFlowStats(String controller, String dpid){
    String url = "http://" + controller + "/stats/flow/" + Long.parseLong(dpid, 16);
    return HttpUtil.get(url);
  }

  public static String getGroupStats(String controller, String dpid){
    String url = "http://" + controller + "/stats/group/" + Long.parseLong(dpid, 16);
    return HttpUtil.get(url);
  }
}

