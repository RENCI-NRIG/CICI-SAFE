package test;
import client.exogeni.ClientSlice;

public class TestMain {
  public static void main(String[] args){
    createTestSlice();
    //test();
  }

  public static void test(){
    String[] args = {"-c", "config/test.conf", "-n"};
    Test t = new Test();
    t.run(args);
  }

  public static void createTestSlice(){
    String[] arg1 = {"-c", "config/test.conf"};
    TestSlice ts = new TestSlice(arg1);
    String[] clientarg1 = {"-c", "client-config/c3-tamu.conf"};
    String[] clientarg2 = {"-c", "client-config/c4-tamu.conf"};
    ClientSlice s1 =  new ClientSlice();
    ClientSlice s2 = new ClientSlice();
    ts.createAndConfigCarrierSlice();
    s1.run(clientarg1);
    s2.run(clientarg2);
  }
}
