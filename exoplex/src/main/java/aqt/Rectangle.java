package aqt;

import java.util.Objects;

public class Rectangle extends Object{
    private Range x;
    private Range y;

    public Rectangle(long x, long y, long w, long h){
        this.x = new Range(x, w);
        this.y = new Range(y, h);
    }

    public long getX(){
        return this.x.getStart();
    }

    public long getY(){
        return this.y.getStart();
    }

    public long getW(){
        return this.x.getLength();
    }

    public long getH(){
        return this.y.getLength();
    }

    public Range getXSegment(){
        return this.x;
    }

    public Range getYSegment(){
        return this.y;
    }

    public Rectangle intersect(Rectangle rectangle){
        if(this.getX()>= rectangle.getX() + rectangle.getW()
            || this.getY() >= rectangle.getY() + rectangle.getH()
            || this.getX() + this.getW() <= rectangle.getX()
            || this.getY() + this.getH() <= rectangle.getY()){
            return null;
        }
        long x1 = Math.max(this.getX(), rectangle.getX());
        long y1 = Math.max(this.getY(), rectangle.getY());
        long x2 = Math.min(this.getX() + this.getW(), rectangle.getX() + rectangle.getW());
        long y2 = Math.min(this.getY() + this.getH(), rectangle.getY() + rectangle.getH());
        return new Rectangle(x1, y1, x2 - x1, y2 - y1);
    }

    public boolean contains(Rectangle rectangle){
        if(rectangle.getX() < this.getX()
            || rectangle.getY() < this.getY()
            || rectangle.getX() + rectangle.getW() > this.getX() + this.getW()
            || rectangle.getY() + rectangle.getH() > this.getY() + this.getH()
        ){
            return false;
        }
        return true;
    }

    @Override
    public boolean equals(Object rectangle){
        if(!(rectangle instanceof Rectangle)){
            return false;
        }
        Rectangle r = (Rectangle) rectangle;
        if(this.x.getStart() == r.getX() && this.y.getStart() == r.getY()
            && this.x.getLength() == r.getW() && this.y.getLength() == r.getH()){
            return true;
        }else{
            return false;
        }
    }

    @Override
    public int hashCode(){
        return Objects.hash(x.getStart(), y.getStart(), x.getLength(), y.getLength());
    }

    @Override
    public String toString(){
        return String.format("(%s, %s),(%s, %s)", this.getX(),
            this.getX() + this.getW(), this.getY(), this.getY() + this.getH());
    }
}
