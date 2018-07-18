package exoplex.demo.tridentcom;

import exoplex.sdx.core.SdxManager;

public class TridentTest {

  static SdxManager sdxManager = new SdxManager();

  final static String[] sdxArgs = new String[]{"-c", "config/tri.conf"};

  public static void main(String[] args) throws  Exception{
    sdxManager.startSdxServer(sdxArgs);
  }

}
