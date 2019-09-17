package aqt;

import java.util.Objects;

/*
Make sure Range is immutable
 */
public class Range {
  private long start;
  private long length;

  public Range(long x, long w) {
    this.start = x;
    this.length = w;
  }

  public long getStart() {
    return this.start;
  }

  public long getLength() {
    return this.length;
  }

  @Override
  public boolean equals(Object segment) {
    if (!(segment instanceof Range)) {
      return false;
    }
    Range seg = (Range) segment;
    return seg.getStart() == this.start && seg.getLength() == this.length;
  }

  @Override
  public int hashCode() {
    return Objects.hash(start, length);
  }

  @Override
  public String toString() {
    return String.format("(%s, %s)", start, start + length - 1);
  }

  public boolean covers(Range range) {
    return this.start <= range.getStart()
      && this.start + this.length >= range.getStart() + range.getLength();
  }
}
