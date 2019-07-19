package aqt;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.junit.Before;
import org.junit.Test;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.management.ImmutableDescriptor;
import java.util.*;


public class PrefixPairMatchingTest {
    static final Logger logger = LogManager.getLogger(PrefixPairMatchingTest.class);
    AreaBasedQuadTree aqt;
    HashSet<String> prefixes;
    HashMap<Rectangle, ImmutablePair<String, String>> rectanglePrefixPair;
    HashMap<ImmutablePair<String, String>, Rectangle> prefixPairRectangle;
    ArrayList<ImmutablePair<String, String>> prefixPairs;
    Rectangle root;
    Random random;

    @Before
    public void before(){
        prefixes = new HashSet<>();
        aqt = new AreaBasedQuadTree();
        random = new Random();
        rectanglePrefixPair = new HashMap<>();
        prefixPairRectangle = new HashMap<>();
        prefixPairs = new ArrayList<>();
    }

    @Test
    public void testIPPrefixMatching(){
        random.setSeed(99917);
        int prefixNum = 5000;
        int prefixPairNum = 5000000;
        logger.info("generating ip prefixes");
        generatePrefixes(prefixNum);
        logger.info("generating ip prefix pairs");
        generatePrefixPairs(prefixPairNum);
        //insertPrefixes
        logger.info("start bench mark");
        long start = System.currentTimeMillis();
        for(ImmutablePair<String, String> pair: prefixPairs){
            Rectangle rectangle = this.prefixPairRectangle.get(pair);
            aqt.insert(rectangle);
        }
        long end = System.currentTimeMillis();
        logger.debug(String.format("Inserted %s prefixes in %s ms. " +
                "Throughput %s/s", this.prefixPairs.size(), end - start,
            this.prefixPairs.size()*1000.0/(end - start)));

        //query
        start = end;
        for(ImmutablePair<String, String> pair: prefixPairs){
            Rectangle rectangle = this.prefixPairRectangle.get(pair);
            aqt.query(rectangle);
        }
        end = System.currentTimeMillis();
        logger.debug(String.format("queried %s prefixes in %s ms. " +
                "Throughput %s/s", this.prefixPairs.size(), end - start,
            this.prefixPairs.size()*1000.0/(end - start)));
        start = end;
        int size = this.prefixPairs.size();
        for(ImmutablePair<String, String> pair: prefixPairs){
            Rectangle rectangle = this.prefixPairRectangle.get(pair);
            aqt.remove(rectangle);
        }
        end = System.currentTimeMillis();
        logger.debug(String.format("queried %s prefixes in %s ms. " +
            "Throughput %s/s", size, end - start,
            size*1000.0/(end - start)));
    }

    //generate prefixes %8 %12 %16 %20 %24 in ratio: 1:2:4:8:16
    private void generatePrefixes(int num){
        generateSubPrefix(0, 4, 4, 24, 10, num);
        logger.info(String.format("number of prefixes %s", this.prefixes.size()));
    }

    private void generatePrefixPairs(int num){
        ArrayList<String> ps = new ArrayList<>(this.prefixes);
        while(prefixPairs.size() < num){
            int idx1 = random.nextInt(prefixes.size());
            int idx2 = random.nextInt(prefixes.size());
            String prefix1 = ps.get(idx1);
            String prefix2 = ps.get(idx2);
            Rectangle rectangle = PrefixUtil.prefixPairToRectangle(prefix1,
                prefix2);
            ImmutablePair<String, String> pair = new ImmutablePair<>(prefix1,
                prefix2);
            rectanglePrefixPair.put(rectangle, pair);
            prefixPairs.add(pair);
            prefixPairRectangle.put(pair, rectangle);
        }
        logger.info(String.format("number of unique prefix pairs %s\nnumber" +
            " of prefix pairs %s", rectanglePrefixPair.size(),
            prefixPairs.size()));
    }

    /**
     * Generate prefixes recursively
     * @param base
     * @param mask
     * @param shift
     * @param maxMask
     * @param num
     */
    private void generateSubPrefix(long base, int mask, int shift, int maxMask,
        int num, int maxNum){
        int range = 1 << shift;
        for(int i = 0; i< num; i++){
            if(prefixes.size() == maxNum){
                return;
            }
            long r = random.nextInt(range);
            base = base + r << (32 - mask);
            String prefix = String.format("%s/%s",
                PrefixUtil.longToAddr(base), mask);
            if(mask >= 8) {
                prefixes.add(prefix);
            }
            if(mask < maxMask){
                generateSubPrefix(base, mask + shift, shift, maxMask, num, maxNum);
            }
        }
    }

}
