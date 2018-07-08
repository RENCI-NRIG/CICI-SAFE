package exoplex.client.stitchport;

public class SdxStitchPortClient {
  public static void main(String[] args) {
    SdxStitchPortClientManager client = new SdxStitchPortClientManager(args);
    client.run();
  }
}
