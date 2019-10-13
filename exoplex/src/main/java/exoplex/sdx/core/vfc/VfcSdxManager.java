package exoplex.sdx.core.vfc;

import com.google.inject.Inject;
import exoplex.sdx.core.CoreProperties;
import exoplex.sdx.core.SdxManagerBase;
import exoplex.sdx.core.restutil.NotifyResult;
import safe.Authority;

public class VfcSdxManager extends SdxManagerBase {

  @Inject
  public VfcSdxManager(Authority authority) {
    super(authority);
  }

  @Override
  public void loadSlice() throws Exception {
    serverSlice = sliceManagerFactory.create(
      coreProperties.getSliceName(),
      coreProperties.getExogeniKey(),
      coreProperties.getExogeniKey(),
      coreProperties.getExogeniSm(),
      coreProperties.getSshKey());
  }

  @Override
  public void initializeSdx() throws Exception {

  }

  @Override
  public void startSdxServer(CoreProperties coreProperties) throws Exception {
    this.coreProperties = coreProperties;
    loadSlice();
    initializeSdx();
  }

  @Override
  public void delFlows() {
    routingmanager.deleteAllFlows(getSDNController());
  }

  @Override
  public String connectionRequest(String self_prefix, String target_prefix,
    long bandwidth) throws Exception {
    return null;
  }

  @Override
  public NotifyResult notifyPrefix(String dest, String gateway,
                                   String customer_keyhash) {
    return null;
  }
}
