package aqt;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.junit.Before;
import org.junit.Test;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashSet;

public class PrefixUtilTest {
    static Logger logger = LogManager.getLogger(PrefixUtilTest.class);

    @Test
    public void testPrefixUtil(){
        prefixToSegment("0.0.0.1/24");
        prefixToSegment("0.0.0.0/24");
        prefixToSegment("0.0.0.0/16");
        prefixToSegment("0.0.0.0/0");
    }

    @Test
    public void testPrefixContain() {
      Range r = PrefixUtil.prefixToRange("192.168.10.2/28");
      Range r1 = PrefixUtil.prefixToRange("192.168.10.2/32");
      assert contains("192.168.10.1/24", "192.168.10.2/32");
    }

    public boolean contains(String prefix1, String prefix2) {
      return PrefixUtil.prefixToRange(prefix1).covers(PrefixUtil.prefixToRange(prefix2));
    }

    @Test
    public void normalizePrefix(){
        normalizePrefix("192.168.1.1/16");
    }

    @Test
    public void testPrefixToRectangle(){
        prefixPairToRectangle("192.168.10.1/24", "192.168.40.1/24");
    }

    @Test
    public void testImmutablePair(){
        HashSet<ImmutablePair<String, String>> set = new HashSet<>();
        set.add(new ImmutablePair<>("1", "2"));
        logger.debug(set.contains(new ImmutablePair<>("1", "2")));
    }

    private void prefixToSegment(String prefix){
        logger.debug(String.format("Prefix: %s Range: %s", prefix, PrefixUtil.prefixToRange(
            prefix)));
    }

    private  void normalizePrefix(String prefix){
        logger.debug(String.format("original: %s after: %s", prefix,
            PrefixUtil.normalizePrefix(prefix)));
    }

    private void prefixPairToRectangle(String p1, String p2){
        logger.debug(String.format("prefix1: %s prefix2: %s rectangle %s",  p1, p2,
            PrefixUtil.prefixPairToRectangle(p1, p2)));
    }
}
