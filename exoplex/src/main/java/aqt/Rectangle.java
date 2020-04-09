package aqt;

import java.util.Objects;

/**
 * Make sure Rectangle is immutable
 */
public class Rectangle extends Object {
  private Range x;
  private Range y;

  public Rectangle(long x, long y, long w, long h) {
    this.x = new Range(x, w);
    this.y = new Range(y, h);
  }

  public Rectangle(Range range1, Range range2) {
    this.x = range1;
    this.y = range2;
  }

  public long getArea() {
    return this.x.getLength() * this.y.getLength();
  }

  public long getX() {
    return this.x.getStart();
  }

  public long getY() {
    return this.y.getStart();
  }

  public long getW() {
    return this.x.getLength();
  }

  public long getH() {
    return this.y.getLength();
  }

  public Range getXSegment() {
    return this.x;
  }

  public Range getYSegment() {
    return this.y;
  }

  public Rectangle intersect(Rectangle rectangle) {
    if (this.getX() >= rectangle.getX() + rectangle.getW()
      || this.getY() >= rectangle.getY() + rectangle.getH()
      || this.getX() + this.getW() <= rectangle.getX()
      || this.getY() + this.getH() <= rectangle.getY()) {
      return null;
    }
    long x1 = Math.max(this.getX(), rectangle.getX());
    long y1 = Math.max(this.getY(), rectangle.getY());
    long x2 = Math.min(this.getX() + this.getW(), rectangle.getX() + rectangle.getW());
    long y2 = Math.min(this.getY() + this.getH(), rectangle.getY() + rectangle.getH());
    return new Rectangle(x1, y1, x2 - x1, y2 - y1);
  }

  public boolean contains(Rectangle rectangle) {
    return rectangle.getX() >= this.getX()
      && rectangle.getY() >= this.getY()
      && rectangle.getX() + rectangle.getW() <= this.getX() + this.getW()
      && rectangle.getY() + rectangle.getH() <= this.getY() + this.getH();
  }

  /**
   * If this has higher priority, return 1
   * @param o1
   * @return
   */
  public int compareInbound(Rectangle o1) {
    if(this.getW() > o1.getW()) {
      return -1;
    } else if (this.getW() < o1.getW()) {
      return 1;
    } else {
      if(this.getH() > o1.getH()) {
        return -1;
      } else if (this.getH() < o1.getH()) {
        return 1;
      } else {
        return 0;
      }
    }
  }

  public int compareOutbound(Rectangle o1) {
    if(this.getH() > o1.getH()) {
      return -1;
    } else if (this.getH() < o1.getH()) {
      return 1;
    } else {
      if(this.getW() > o1.getW()) {
        return -1;
      } else if (this.getW() < o1.getW()) {
        return 1;
      } else {
        return 0;
      }
    }
  }

  @Override
  public boolean equals(Object rectangle) {
    if (!(rectangle instanceof Rectangle)) {
      return false;
    }
    Rectangle r = (Rectangle) rectangle;
    return this.x.getStart() == r.getX() && this.y.getStart() == r.getY()
      && this.x.getLength() == r.getW() && this.y.getLength() == r.getH();
  }

  @Override
  public int hashCode() {
    return Objects.hash(x.getStart(), y.getStart(), x.getLength(), y.getLength());
  }

  @Override
  public String toString() {
    return String.format("(%s, %s),(%s, %s)", this.getX(),
      this.getX() + this.getW(), this.getY(), this.getY() + this.getH());
  }
}
