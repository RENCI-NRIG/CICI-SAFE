package test;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Properties;
import client.exogeniclient.ClientSlice;

public class CreateTestSlice {
  public static void main(String[] args) {
    String[] arg1 = {"-c", "config/test.conf"};
    TestSlice ts = new TestSlice(arg1);
    String[] clientarg1 = {"-c", "client-config/c3-tamu.conf"};
    String[] clientarg2 = {"-c", "client-config/c3-tamu.conf"};
    ClientSlice s1 =  new ClientSlice();
    ClientSlice s2 = new ClientSlice();
    ts.createAndConfigCarrierSlice();
    s1.run(clientarg1);
    s2.run(clientarg2);
    /*
    //Note: seems that we cant create all slices at the same time
    ArrayList<Thread> tlist = new ArrayList<Thread>();
    try {
      //ScpTo.Scp(lfile,"root",mip,rfile,privkey);
      Thread thread0 = new Thread() {
        @Override
        public void run() {
          ts.createAndConfigCarrierSlice();
        }
      };
      thread0.start();
      tlist.add(thread0);
      Thread thread1 = new Thread() {
        @Override
        public void run() {
          s1.run(clientarg1);
        }
      };
      thread1.start();
      tlist.add(thread1);

      Thread thread2 = new Thread() {
        @Override
        public void run() {
          s2.run(clientarg2);
        }
      };
      thread2.start();
      tlist.add(thread2);

    } catch (Exception e) {
      e.printStackTrace();
    }

    try {
      for (Thread t : tlist) {
        t.join();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    */
  }
}
