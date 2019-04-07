package exoplex.sdx.slice;

public interface SliceManagerFactory {
  SliceManager create(String sliceName, String pemLocation, String keyLocation, String
    controllerUrl, String sshKey);
}
