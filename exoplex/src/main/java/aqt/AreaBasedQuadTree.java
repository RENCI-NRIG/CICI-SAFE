package aqt;

import net.jcip.annotations.ThreadSafe;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

/**
 * https://link.springer.com/content/pdf/10.1007/978-0-387-35580-1_4.pdf
 */
@ThreadSafe
public class AreaBasedQuadTree {

  static final Logger logger = LogManager.getLogger(AreaBasedQuadTree.class);

  static final long MAX_IP = 4294967295L;

  // maximum number of objects cached in a node before splitting the node
  static final int MAX_CACHED = 8;

  private AreaBasedQuadTree[] children;

  //Crossing filter set

  private PrefixTree cfsX;

  private PrefixTree cfsY;

  private Set<Rectangle> objects;

  private int level;

  private boolean empty;

  private Rectangle area;

  public AreaBasedQuadTree() {
    this.level = 0;
    this.area = new Rectangle(0, 0, MAX_IP + 1, MAX_IP + 1);
    this.cfsX = new PrefixTree(0, MAX_IP + 1);
    this.cfsY = new PrefixTree(0, MAX_IP + 1);
    this.children = new AreaBasedQuadTree[4];
    this.objects = new HashSet<>();
    this.empty = true;
  }

  public AreaBasedQuadTree(int level, Rectangle area, AreaBasedQuadTree parent) {
    this.level = level;
    this.area = area;
    this.cfsX = new PrefixTree(area.getX(), area.getW());
    this.cfsY = new PrefixTree(area.getY(), area.getH());
    this.objects = new HashSet<>();
    this.children = new AreaBasedQuadTree[4];
    this.empty = true;
  }

  public synchronized boolean isEmpty() {
    return empty;
  }

  public void insert(Rectangle rect) {
    if (!this.area.contains(rect)) {
      logger.debug(String.format("Rectangle %s is fully contained in this node, not added",
        rect));
      return;
    }
    this.empty = false;
    //if the inserted rect is crossing the area in any dimension, add to CFS
    if (rect.getX() == this.area.getX() && rect.getW() == this.area.getW()) {
      cfsY.add(rect.getYSegment());
      return;
    }
    if (rect.getY() == this.area.getY() && rect.getH() == this.area.getH()) {
      cfsX.add(rect.getXSegment());
      return;
    }
    //if not crossing, then the rectangle must be fully contained in one of its children,
    // because the rectangle is a IP prefix pair.
    //check if children are not null
    if (children[0] == null) {
      objects.add(rect);
    }
    //split node
    if (objects.size() > MAX_CACHED) {
      if (children[0] == null) {
        splitNode();
      }
      for (Rectangle r : objects) {
        for (AreaBasedQuadTree child : this.children) {
          if (child.contains(r)) {
            child.insert(r);
            break;
          }
        }
      }
      objects.clear();
    }
  }

  private void checkEmpty() {
    if (this.cfsX.isEmpty() && this.cfsY.isEmpty()
      && this.objects.isEmpty()) {
      if (this.children[0] == null) {
        this.empty = true;
      }
      for (AreaBasedQuadTree child : children) {
        if (!child.isEmpty()) {
          this.empty = false;
          return;
        }
      }
      this.empty = true;
    }
    this.empty = false;
  }

  public void remove(Rectangle rect) {
    //if the inserted rect is crossing the area in any dimension, add to CFS
    if (rect.getX() == this.area.getX() && rect.getW() == this.area.getW()) {
      cfsY.remove(rect.getYSegment());
    } else if (rect.getY() == this.area.getY() && rect.getH() == this.area.getH()) {
      cfsX.remove(rect.getXSegment());
      return;
    } else if (!objects.remove(rect)) {
      if (children[0] == null) {
        return;
      }
      boolean childEmpty = true;
      for (AreaBasedQuadTree child : this.children) {
        if (child.contains(rect)) {
          child.remove(rect);
        }
        if (!child.isEmpty()) {
          childEmpty = false;
        }
      }
      if (childEmpty) {
        clearChildren();
      }
    }
    if (cfsX.isEmpty() && cfsY.isEmpty() && this.objects.isEmpty() && this.children[0] == null) {
      this.empty = true;
    }
  }

  private synchronized void clearChildren() {
    for (int i = 0; i < 4; i++) {
      this.children[i] = null;
    }
  }

  public boolean contains(Rectangle rectangle) {
    return rectangle.getX() >= this.area.getX()
      && rectangle.getY() >= this.area.getY()
      && (rectangle.getX() + rectangle.getW()) <= (this.area.getX() + this.area.getW())
      && (rectangle.getY() + rectangle.getH()) <= (this.area.getY() + this.area.getH());
  }

  public synchronized void splitNode() {
    if (this.area.getW() == 1) {
      System.out.println("Node not splittable");
    }
    long newWidth = area.getW() / 2;
    long x0 = area.getX();
    long y0 = area.getY();
    long x1 = x0 + newWidth;
    long y1 = y0;
    long x2 = x0;
    long y2 = y0 + newWidth;
    long x3 = x0 + newWidth;
    long y3 = y0 + newWidth;
    this.children[0] = new AreaBasedQuadTree(this.level + 1, new Rectangle(x0, y0, newWidth,
      newWidth), this);
    this.children[1] = new AreaBasedQuadTree(this.level + 1, new Rectangle(x1, y1, newWidth,
      newWidth), this);
    this.children[2] = new AreaBasedQuadTree(this.level + 1, new Rectangle(x2, y2, newWidth,
      newWidth), this);
    this.children[3] = new AreaBasedQuadTree(this.level + 1, new Rectangle(x3, y3, newWidth,
      newWidth), this);
  }

  public Rectangle intersect(Rectangle query) {
    return this.area.intersect(query);
  }

  // Return all original rectangles that intersects with the query, not the intersections
  public synchronized Collection<Rectangle> query(Rectangle query) {
    List<Rectangle> result = new ArrayList<>();
    for (Range yseg : this.cfsY.query(query.getYSegment())) {
      result.add(new Rectangle(this.area.getX(), yseg.getStart(),
        this.area.getW(), yseg.getLength()));
    }
    for (Range xseg : this.cfsX.query(query.getXSegment())) {
      result.add(new Rectangle(xseg.getStart(), this.area.getY(),
        xseg.getLength(), this.area.getH()));
    }
    //if the query intersects with the objects, add intersected area to the result
    for (Rectangle rect : this.objects) {
      Rectangle intersection = rect.intersect(query);
      if (intersection != null) {
        result.add(rect);
      }
    }
    //if the query intersects with any children, find within children;
    if (this.children[0] != null) {
      for (AreaBasedQuadTree child : this.children) {
        Rectangle intersection = child.intersect(query);
        if (intersection != null) {
          result.addAll(child.query(intersection));
        }
      }
    }
    return result;
  }
}
