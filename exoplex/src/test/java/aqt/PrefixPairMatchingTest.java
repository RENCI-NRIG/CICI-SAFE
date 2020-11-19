package aqt;

import exoplex.sdx.advertise.AdvertiseManager;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.jena.atlas.lib.CollectionUtils;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.management.ImmutableDescriptor;
import java.util.*;
import java.util.stream.Collectors;

public class PrefixPairMatchingTest {
    static final Logger logger = LogManager.getLogger(PrefixPairMatchingTest.class);
    AreaBasedQuadTree aqt;
    static HashSet<String> prefixes;
    static HashSet<String>[] prefixesByLength;
    static HashSet<String> largePrefixes;
    HashMap<Rectangle, ImmutablePair<String, String>> rectanglePrefixPair;
    HashMap<ImmutablePair<String, String>, Rectangle> prefixPairRectangle;
    ArrayList<ImmutablePair<String, String>> prefixPairs;
    ArrayList<ImmutablePair<String, String>> queries;
    Rectangle root;
    static Random random;
    static int prefixNum = 10000;
    static int prefixPairBase = 4000000;

    @BeforeClass
    static public void setUp(){
        prefixes = new HashSet<>();
        largePrefixes = new HashSet<>();
        random = new Random();
        random.setSeed(89917);
        //random.setSeed(99917);
        logger.info("generating ip prefixes");
        generatePrefixes(prefixNum);
    }

    @Before
    public void before(){
        rectanglePrefixPair = new HashMap<>();
        prefixPairRectangle = new HashMap<>();
        prefixPairs = new ArrayList<>();
        queries = new ArrayList<>();
    }

    @Test
    public void test() {
        int[] multiplier = new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20};
        for (int m : multiplier) {
            aqt = new AreaBasedQuadTree();
            testIPPrefixMatching(prefixPairBase * m, 10000, 50 - m);
        }
    }

    private void testIPPrefixMatching(int prefixPairNum, int dOnly, int ratio){
        logger.info("generating ip prefix pairs");
        //generatePrefixPairs(prefixPairNum);
        generatePrefixPairs(prefixPairNum, dOnly, ratio, 3);
        Collections.shuffle(prefixPairs);
        Collections.shuffle(queries);
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
        for(ImmutablePair<String, String> pair: queries){
            Rectangle rectangle = this.prefixPairRectangle.get(pair);
            overlappedSize.add(aqt.query(rectangle).size());
        }
        end = System.currentTimeMillis();
        logger.debug(String.format("queried %s prefixes in %s ms. " +
                "Throughput %s/s  unit time %s us", 
	    queries.size(),
            end - start,
            queries.size()*1000.0/(end - start),
            (end - start) * 1000.0 / queries.size()));
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
        long sum = 0;
        int[] ls = new int[200];
        for(Integer s: overlappedSize){
            if(s < 200){
                ls[s]++;
            }
            sum += s;
        }
        logger.info(String.format("Average overlapped size: %s",
            sum * 1.0 / overlappedSize.size()));
        ArrayList<String> lss = new ArrayList<>();
        for(int i: ls){
            lss.add(String.valueOf(i));
        }
        logger.info(String.format("[%s]", String.join(",", lss)));
    }

    //generate prefixes %8 %12 %16 %20 %24 in ratio: 1:2:4:8:16
    static private void generatePrefixes(int num){
        int base = 0;
        int mask = 8;
        int shift = 8;
        int maxMask = 24;
        prefixesByLength = new HashSet[maxMask/shift];
        for(int i = 0; i< maxMask/shift; i++){
            prefixesByLength[i] = new HashSet<>();
        }
        generateSubPrefix(0, 8, 8, 24, 200, num);
        logger.info(String.format("number of prefixes %s", prefixes.size()));
    }

    /**
     *
     * @param totalNum total number of prefixes
     * @param numDestOnly (0.0.0.0/0, destPrefix)
     * @param ratio  number of prefixPairs
     */
    private void generatePrefixPairs(int totalNum, int numDestOnly, int ratio
        , int levels) {
        //generated
        int nd = 0;
        int[] threshold = new int[levels];
        int base = 1;
        threshold[0] = 1;
        for (int i = 1; i < levels; i++) {
            threshold[i] = threshold[i - 1] * (ratio + 1);
        }
        ArrayList<String>[] pls = new ArrayList[levels];
        for (int i = 0; i < levels; i++) {
            pls[i] = new ArrayList<>(prefixesByLength[i]);
        }
        int MAX = threshold[levels - 1];
        long sumLength = 0;
        long numPrefix = 0;
        while (prefixPairs.size() < totalNum) {
            int rand = random.nextInt(MAX);
            int rand2 = random.nextInt(MAX);
            String prefix1 = null;
            String prefix2 = null;
            for (int i = 0; i < levels; i++) {
                if (rand < threshold[i]) {
                    int idx1 = random.nextInt(pls[i].size());
                    prefix1 = pls[i].get(idx1);
                    if (nd < numDestOnly && Integer.valueOf(prefix1.split("/")[1]) > 8) {
                        nd++;
                        ImmutablePair<String, String> pair =
                                new ImmutablePair<>("0.0.0.0/0", prefix1);
                        prefixPairs.add(pair);
                        numPrefix += 2;
                        sumLength += Integer.valueOf(prefix1.split("/")[1]);
                        queries.add(pair);
                        Rectangle rectangle =
                                PrefixUtil.prefixPairToRectangle("0.0.0.0/0",
                                        prefix1);
                        rectanglePrefixPair.put(rectangle, pair);
                        prefixPairRectangle.put(pair, rectangle);
                    }
                    break;
                }
            }
            for (int i = 0; i < levels; i++) {
                if (rand2 < threshold[i]) {
                    int idx2 = random.nextInt(pls[i].size());
                    prefix2 = pls[i].get(idx2);
                    break;
                }
            }
            if (!prefix1.endsWith("/8")
                    || !prefix2.endsWith("/8")) {
                ImmutablePair<String, String> pair = new ImmutablePair<>(prefix1,
                        prefix2);
                prefixPairs.add(pair);
                numPrefix += 2;
                sumLength = sumLength + Integer.valueOf(prefix1.split("/")[1])
                        + Integer.valueOf(prefix2.split("/")[1]);
                queries.add(pair);
                Rectangle rectangle = PrefixUtil.prefixPairToRectangle(prefix1,
                        prefix2);
                rectanglePrefixPair.put(rectangle, pair);
                prefixPairRectangle.put(pair, rectangle);
            }
        }
        logger.info(String.format("number of unique prefix pairs %s\nnumber" +
                        " of prefix pairs %s", rectanglePrefixPair.size(),
                prefixPairs.size()));
        logger.info(String.format("Average prefix lenth for prefix pairs: %s", sumLength * 1.0 / numPrefix));
    }

    private void generatePrefixPairs(int num){
        ArrayList<String> ps = new ArrayList<>(this.prefixes);
        ArrayList<String> lps = new ArrayList<>(this.largePrefixes);
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
                queries.add(pair);
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
    static private void generateSubPrefix(long base, int mask, int shift,
                                     int maxMask,
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
                prefixesByLength[mask/shift - 1].add(prefix);
            }
            if(mask < maxMask){
                largePrefixes.add(prefix);
                generateSubPrefix(base, mask + shift, shift, maxMask, num, maxNum);
            }
        }
    }

}
