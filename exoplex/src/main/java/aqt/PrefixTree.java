package aqt;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class PrefixTree {
  static final Logger logger = LogManager.getLogger(PrefixTree.class);

  static final int MAX_NUM = 8;
  final HashSet<Range> cachedObjects;
  //Prefix range of the node
  Range range;
  //if the range is a valid point
  boolean valid;
  boolean empty;
  PrefixTree left;

  PrefixTree right;

  public PrefixTree() {
    this.range = new Range(0, AreaBasedQuadTree.MAX_IP + 1);
    this.valid = false;
    this.left = null;
    this.right = null;
    this.empty = true;
    cachedObjects = new HashSet<>();
  }

  public PrefixTree(long low, long width) {
    this.range = new Range(low, width);
    this.valid = false;
    this.left = null;
    this.right = null;
    this.empty = true;
    cachedObjects = new HashSet<>();
  }

  /**
   * if the sub tree is empty
   *
   * @return
   */
  public boolean isEmpty() {
    return this.empty;
  }

  public void add(Range range) {
    //logger.info(String.format("%s add %s", this.range, range));
    if (this.range.equals(range)) {
      this.valid = true;
      this.empty = false;
      return;
    } else if (!this.range.covers(range)) {
      return;
    }
    this.empty = false;
    if (this.left == null) {
      cachedObjects.add(range);
      if (cachedObjects.size() > MAX_NUM) {
        splitNode();
        for (Range obj : this.cachedObjects) {
          if (this.left.covers(obj)) {
            this.left.add(obj);
          } else {
            this.right.add(obj);
          }
        }
        cachedObjects.clear();
      }
    } else if (this.left.covers(range)) {
      this.left.add(range);
    } else {
      this.right.add(range);
    }
  }

  public void remove(Range range) {
    //logger.info(String.format("%s remove %s", this.range, range));
    if (this.range.equals(range)) {
      this.valid = false;
    } else if (this.left != null) {
      if (this.left.covers(range)) {
        this.left.remove(range);
      } else {
        this.right.remove(range);
      }
    } else {
      this.cachedObjects.remove(range);
    }
    if (!valid && this.cachedObjects.isEmpty()
      && (this.left == null || (this.left.isEmpty() && this.right.isEmpty()))) {
      this.empty = true;
    }
  }

  public List<Range> query(Range query) {
    ArrayList<Range> result = new ArrayList<>();
    if (query.covers(this.range)) {
      if (this.left != null) {
        result.addAll(this.left.query(query));
        result.addAll(this.right.query(query));
      } else {
        result.addAll(this.cachedObjects);
      }
      if (this.valid) {
        result.add(this.range);
      }
      return result;
    }
    if (this.valid) {
      result.add(this.range);
    }
    if (this.left != null) {
      if (this.left.covers(query)) {
        result.addAll(this.left.query(query));
      } else {
        result.addAll(this.right.query(query));
      }
    } else {
      for (Range range : this.cachedObjects) {
        if (range.covers(query) || query.covers(range)) {
          result.add(range);
        }
      }
    }
    return result;
  }

  public boolean covers(Range range) {
    return this.range.covers(range);
  }

  private void splitNode() {
    if (left == null && this.range.getLength() >= 2) {
      long newWidth = this.range.getLength() / 2;
      this.left = new PrefixTree(range.getStart(), newWidth);
      this.right = new PrefixTree(range.getStart() + newWidth, newWidth);
    }
  }
}
