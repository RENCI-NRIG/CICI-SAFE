package aqt;

import org.junit.Before;
import org.junit.Test;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PrefixUtilTest {
    static Logger logger = LogManager.getLogger(PrefixUtilTest.class);

    @Test
    public void testPrefixUtil(){
        prefixToSegment("0.0.0.1/24");
        prefixToSegment("0.0.0.0/24");
        prefixToSegment("0.0.0.0/16");
    }

    @Test
    public void normalizePrefix(){
        normalizePrefix("192.168.1.1/16");
    }

    private void prefixToSegment(String prefix){
        logger.debug(String.format("Prefix: %s Range: %s", prefix, PrefixUtil.prefixToRange(
            prefix)));
    }

    private  void normalizePrefix(String prefix){
        logger.debug(String.format("original: %s after: %s", prefix,
            PrefixUtil.normalizePrefix(prefix)));
    }
}
