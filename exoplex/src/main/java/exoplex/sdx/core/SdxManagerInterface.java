package exoplex.sdx.core;

import exoplex.sdx.core.restutil.NotifyResult;

public interface SdxManagerInterface {

  void loadSlice() throws Exception;

  void initializeSdx() throws Exception;

  void startSdxServer(CoreProperties coreProperties) throws Exception;

  void delFlows();

  String connectionRequest(String self_prefix, String target_prefix, long bandwidth) throws Exception;

  NotifyResult notifyPrefix(String dest, String gateway, String customer_keyhash) throws Exception;
}
