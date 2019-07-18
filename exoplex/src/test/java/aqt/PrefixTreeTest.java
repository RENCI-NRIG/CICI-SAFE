package aqt;

import org.junit.Before;
import org.junit.Test;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

public class PrefixTreeTest {
    static final Logger logger = LogManager.getLogger(PrefixTreeTest.class);
    PrefixTree prefixTree;
    HashSet<Range> ranges;
    Range root;

    @Before
    public void before(){

        prefixTree = new PrefixTree(0, 8);
        ranges = new HashSet<>();
    }

    @Test
    public void testInsertion(){
        root = new Range(0, 8);
        Range[] splits = split(root);
        for(int i = 0; i<2; i++){
            splits = split(splits[0]);
            for(Range rectangle: splits){
                insertRange(rectangle);
            }
        }
        verifyResult();
    }

    @Test
    public void testScale(){
        long start = System.currentTimeMillis();
        long width = 2048*8;
        root = new Range(0, width);
        prefixTree = new PrefixTree(0, width);
        Range[] splits = split(root);
        LinkedList<Range> lists = new LinkedList<>();
        lists.addAll(Arrays.asList(splits));
        Random random = new Random();
        while (lists.size() > 0){
            Range currentRange = lists.poll();
                splits = split(currentRange);
            if(splits != null){
                for(Range child: splits){
                    if(random.nextInt()%5 != 0) {
                        insertRange(child);
                        lists.push(child);
                    }
                }
            }
        }
        long end = System.currentTimeMillis();
        logger.debug(String.format("Inserted %s ranges in %s ms", this.ranges.size(),
            end - start));
        verifyResult();
        logger.debug(String.format("verified ranges in %s ms",
            System.currentTimeMillis() - end
        ));
    }

    @Test
    public void testDeletion(){
        testInsertion();
        Collection<Range> rects = new HashSet<>(this.ranges);
        for(Range rectangle: rects){
            this.ranges.remove(rectangle);
            prefixTree.remove(rectangle);
            verifyResult();
        }
    }

    private void verifyResult(){
        HashSet<Range> result = new HashSet<>(prefixTree.query(root));
        assert result.size() == ranges.size();
        assert result.containsAll(ranges);
    }

    private void insertRange(Range range){
        prefixTree.add(range);
        ranges.add(range);
    }

    private Range[] split(Range range){
        if(range.getLength() == 1){
            return null;
        }
        long newW = range.getLength()/2;
        Range[] result = new Range[2];
        result[0] = new Range(range.getStart(), newW);
        result[1] = new Range(range.getStart() + newW, newW);
        return result;
    }
}
