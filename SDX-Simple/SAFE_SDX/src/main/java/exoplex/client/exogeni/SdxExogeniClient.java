package exoplex.client.exogeni;

public class SdxExogeniClient {
  public static void main(String[] args) {
    SdxExogeniClientManager client = new SdxExogeniClientManager(args);
    client.run(args);
  }
}
