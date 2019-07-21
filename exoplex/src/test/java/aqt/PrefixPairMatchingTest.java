package aqt;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.management.ImmutableDescriptor;
import java.util.*;

@Ignore
public class PrefixPairMatchingTest {
    static final Logger logger = LogManager.getLogger(PrefixPairMatchingTest.class);
    AreaBasedQuadTree aqt;
    HashSet<String> prefixes;
    HashSet<String> largePrefixes;
    HashMap<Rectangle, ImmutablePair<String, String>> rectanglePrefixPair;
    HashMap<ImmutablePair<String, String>, Rectangle> prefixPairRectangle;
    ArrayList<ImmutablePair<String, String>> prefixPairs;
    ArrayList<ImmutablePair<String, String>> largePrefixpairs;
    Rectangle root;
    Random random;
    int prefixNum = 4000;
    int prefixPairBase = 2000000;

    @Before
    public void before(){
        prefixes = new HashSet<>();
        largePrefixes = new HashSet<>();
        aqt = new AreaBasedQuadTree();
        random = new Random();
        rectanglePrefixPair = new HashMap<>();
        prefixPairRectangle = new HashMap<>();
        prefixPairs = new ArrayList<>();
        largePrefixpairs = new ArrayList<>();
    }

    @Test
    public void test1(){
        testIPPrefixMatching(prefixNum, prefixPairBase);
    }

    @Test
    public void test2(){
        testIPPrefixMatching(prefixNum, 2*prefixPairBase);
    }

    @Test
    public void test3(){
        testIPPrefixMatching(prefixNum, 4*prefixPairBase);
    }

    @Test
    public void test4(){
        testIPPrefixMatching(prefixNum, 8*prefixPairBase);
    }

    @Test
    public void test5(){
        testIPPrefixMatching(prefixNum, 16*prefixPairBase);
    }

    private void testIPPrefixMatching(int prefixNum, int prefixPairNum){
        random.setSeed(99917);
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
                "Throughput %s/s  unit time %s us", this.prefixPairs.size(),
            end - start,
            this.prefixPairs.size()*1000.0/(end - start),
            (end - start) * 1000.0/this.prefixPairs.size()));

        //query
        start = end;
        ArrayList<Integer> overlappedSize = new ArrayList<>();
        for(ImmutablePair<String, String> pair: largePrefixpairs){
            Rectangle rectangle = this.prefixPairRectangle.get(pair);
            overlappedSize.add(aqt.query(rectangle).size());
        }
        end = System.currentTimeMillis();
        logger.debug(String.format("queried %s prefixes in %s ms. " +
                "Throughput %s/s  unit time %s us", this.largePrefixpairs.size(),
            end - start,
            this.largePrefixpairs.size()*1000.0/(end - start),
            (end - start) * 1000.0 / this.largePrefixpairs.size()));
        start = end;
        int size = this.prefixPairs.size();
        for(ImmutablePair<String, String> pair: prefixPairs){
            Rectangle rectangle = this.prefixPairRectangle.get(pair);
            aqt.remove(rectangle);
        }
        end = System.currentTimeMillis();
        logger.debug(String.format("deleted %s prefixes in %s ms. " +
            "Throughput %s/s   unit time: %s us", size, end - start,
            size*1000.0/(end - start), (end - start) * 1000.0 / size));
        int sum = 0;
        for(Integer s: overlappedSize){
            sum += s;
        }
        logger.info(String.format("Average overlapped size: %s",
            sum * 1.0 / overlappedSize.size()));
    }

    //generate prefixes %8 %12 %16 %20 %24 in ratio: 1:2:4:8:16
    private void generatePrefixes(int num){
        generateSubPrefix(0, 8, 8, 24, 200, num);
        logger.info(String.format("number of prefixes %s", this.prefixes.size()));
    }

    private void generatePrefixPairs(int num){
        ArrayList<String> ps = new ArrayList<>(this.prefixes);
        ArrayList<String> lps = new ArrayList<>(this.prefixes);
        while(prefixPairs.size() < num){
            String prefix1, prefix2;
            if(random.nextInt()%5 != 0){
                int idx1 = random.nextInt(lps.size());
                prefix1 = lps.get(idx1);
            } else{
                int idx1 = random.nextInt(prefixes.size());
                prefix1 = ps.get(idx1);
            }
            int idx2 = random.nextInt(prefixes.size());
            prefix2 = ps.get(idx2);
            Rectangle rectangle = PrefixUtil.prefixPairToRectangle(prefix1,
                prefix2);
            ImmutablePair<String, String> pair = new ImmutablePair<>(prefix1,
                prefix2);
            if(!prefix1.endsWith("/24") || !prefix2.endsWith("/24")){
                largePrefixpairs.add(pair);
            }
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
                largePrefixes.add(prefix);
                generateSubPrefix(base, mask + shift, shift, maxMask, num, maxNum);
            }
        }
    }

}
