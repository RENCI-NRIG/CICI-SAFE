package exoplex.demo;


import exoplex.demo.tridentcom.TridentSlice;
import junit.framework.Assert;
import org.junit.*;
import riak.RiakSlice;

public class SdxTest {
  static String userDir = System.getProperty("user.dir");
  static String sdxSimpleDir = userDir.split("SDX-Simple")[0] + "SDX-Simple/";
  static String[] riakArgs = new String[]{"-c", sdxSimpleDir + "config/riak.conf"};
  static String[] riakDelArgs = new String[]{"-c", sdxSimpleDir + "config/riak.conf", "-d"};

  @BeforeClass
  public static void before() throws Exception{
    System.out.println("before test");
    //create RiakSlice
    RiakSlice riakSlice = new RiakSlice();
    String riakIP = riakSlice.run(riakArgs);
    TridentSlice.createSlices(riakIP);
  }

  @AfterClass
  public static void after()throws Exception{
    RiakSlice riakSlice = new RiakSlice();
    String riakIP = riakSlice.run(riakDelArgs);
    TridentSlice.deleteTestSlices();
    System.out.println("after");
  }

  @Test
  public void Test1(){
    System.out.println("This is a test1");
  }

  @Test
  public void Test2(){
    System.out.println("This is a test2");
  }

}
