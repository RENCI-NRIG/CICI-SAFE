package exoplex.sdx.advertise;

import exoplex.sdx.safe.SafeManager;
import org.junit.Before;
import org.junit.Test;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AdvertiseManagerTest {
    AdvertiseManager advertiseManager;

    @Before
    public void before(){
        advertiseManager = new AdvertiseManager("", (SafeManager)new SafeManagerMock());
    }

    @Test
    public void test(){

    }
}
