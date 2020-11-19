package aqt;

import org.junit.Before;
import org.junit.Test;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

public class AreaBasedQuadTreeTest {
    static final Logger logger = LogManager.getLogger(AreaBasedQuadTreeTest.class);
    AreaBasedQuadTree aqt;
    HashSet<Rectangle> rectangles;
    Rectangle root;

    @Before
    public void before() {
        aqt = new AreaBasedQuadTree();
        rectangles = new HashSet<>();
    }

    @Test
    public void testInsertion() {
        root = new Rectangle(0, 0, 64, 64);
        Rectangle[] splits = verticalSplit(root);
        for (int i = 0; i < 10; i++) {
            if (i % 2 == 0) {
                splits = horizontalSplit(splits[0]);
            } else {
                splits = verticalSplit(splits[0]);
            }
            for (Rectangle rectangle : splits) {
                insertRectangle(rectangle);
            }
        }
        verifyResult();
    }

    @Test
    public void testScale() {
        long width = 2048 * 32;
        root = new Rectangle(0, 0, width, width);
        //aqt = new AreaBasedQuadTree(0, root, null);
        Rectangle[] splits = verticalSplit(root);
        LinkedList<Rectangle> lists = new LinkedList<>();
        lists.addAll(Arrays.asList(splits));
        Random random = new Random();
        long start = System.currentTimeMillis();
        while (lists.size() > 0) {
            Rectangle currentRectangle = lists.poll();
            if (random.nextInt() % 2 == 0) {
                splits = verticalSplit(currentRectangle);
            } else {
                splits = horizontalSplit(currentRectangle);
            }
            if (splits != null) {
                for (Rectangle child : splits) {
                    if (random.nextInt() % 5 != 0) {
                        insertRectangle(child);
                        lists.push(child);
                    }
                }
            }
        }
        long end = System.currentTimeMillis();
        logger.debug(String.format("Inserted %s rectangles in %s ms. " +
                        "Throughput %s/s", this.rectangles.size(), end - start,
                this.rectangles.size() * 1000.0 / (end - start)));
        verifyResult();
        logger.debug(String.format("verified rectangles in %s ms",
                System.currentTimeMillis() - end
        ));
        lists.clear();
        lists.addAll(this.rectangles);
        start = System.currentTimeMillis();
        for (Rectangle rectangle : lists) {
            removeRectangle(rectangle);
        }
        end = System.currentTimeMillis();
        logger.debug(String.format("Removed %s rectangles in %s ms",
                lists.size(),
                end - start));
        verifyResult();
    }

    @Test
    public void testDeletion() {
        testInsertion();
        Collection<Rectangle> rects = new HashSet<>(this.rectangles);
        for (Rectangle rectangle : rects) {
            this.rectangles.remove(rectangle);
            aqt.remove(rectangle);
            verifyResult();
        }
    }

    private void verifyResult() {
        HashSet<Rectangle> result = new HashSet<>(aqt.query(root));
        assert result.size() == rectangles.size();
        assert result.containsAll(rectangles);
    }

    private void insertRectangle(Rectangle rectangle) {
        aqt.insert(rectangle);
        rectangles.add(rectangle);
    }

    private void removeRectangle(Rectangle rectangle) {
        aqt.remove(rectangle);
        rectangles.remove(rectangle);
    }

    private Rectangle[] verticalSplit(Rectangle rectangle) {
        if (rectangle.getW() == 1) {
            return null;
        }
        long newW = rectangle.getW() / 2;
        Rectangle[] result = new Rectangle[2];
        result[0] = new Rectangle(rectangle.getX(), rectangle.getY(), newW, rectangle.getH());
        result[1] = new Rectangle(rectangle.getX() + newW, rectangle.getY(), newW,
                rectangle.getH());
        return result;
    }

    private Rectangle[] horizontalSplit(Rectangle rectangle) {
        if (rectangle.getH() == 1) {
            return null;
        }
        long newH = rectangle.getH() / 2;
        Rectangle[] result = new Rectangle[2];
        result[0] = new Rectangle(rectangle.getX(), rectangle.getY(), rectangle.getW(), newH);
        result[1] = new Rectangle(rectangle.getX(), rectangle.getY() + newH, rectangle.getW(),
                newH);
        return result;
    }
}
