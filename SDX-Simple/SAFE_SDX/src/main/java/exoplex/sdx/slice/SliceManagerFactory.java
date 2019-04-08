package exoplex.sdx.slice;

import com.google.inject.assistedinject.Assisted;

public interface SliceManagerFactory {
  SliceManager create(
    @Assisted("sliceName") String sliceName,
    @Assisted("pem") String pemLocation,
    @Assisted("key") String keyLocation,
    @Assisted("controller") String controllerUrl,
    @Assisted("ssh") String sshKey);
}
