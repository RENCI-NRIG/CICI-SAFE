package sdx.test;

public class CreateTestSlice {
  public static void main(String[] args) {
    String[] arg1 = {"-c", "config/test.conf"};
    TestSlice ts = new TestSlice(arg1);
    ts.createAndConfigCarrierSlice();
  }
}
