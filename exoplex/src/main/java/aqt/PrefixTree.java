package aqt;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PrefixTree {
    static final Logger logger = LogManager.getLogger(PrefixTree.class);

    //Prefix range of the node
    Range range;

    //if the range is a valid point
    boolean valid;

    boolean empty;

    PrefixTree left;

    PrefixTree right;

    public PrefixTree(){
        this.range = new Range(0, AreaBasedQuadTree.MAX_IP + 1);
        this.valid = false;
        this.left = null;
        this.right = null;
        this.empty = true;
    }

    public PrefixTree(long low, long width){
        this.range = new Range(low, width);
        this.valid = false;
        this.left = null;
        this.right = null;
        this.empty = true;
    }

    /**
     * if the sub tree is empty
     * @return
     */
    public boolean isEmpty(){
        return this.empty;
    }

    public void add(Range range){
        //logger.info(String.format("%s add %s", this.range, range));
        if(this.range.equals(range)){
            this.valid = true;
            this.empty = false;
            return;
        } else if(range.covers(this.range)){
            return;
        }
        this.empty = false;
        splitNode();
        if(this.left.covers(range)){
            this.left.add(range);
        }else{
            this.right.add(range);
        }
    }

    public void remove(Range range){
        //logger.info(String.format("%s remove %s", this.range, range));
        if(this.range.equals(range)){
            this.valid = false;
        }else if(this.left.covers(range)){
            this.left.remove(range);
        }else{
            this.right.remove(range);
        }
        if(!valid && (this.left == null || (this.left.isEmpty() && this.right.isEmpty()))){
            this.empty = true;
            this.left = null;
            this.right = null;
        }
    }

    public List<Range> query (Range query){
        ArrayList<Range> result = new ArrayList<>();
        if(query.covers(this.range)){
            if(this.left != null) {
                result.addAll(this.left.query(query));
                result.addAll(this.right.query(query));
            }
            if(this.valid){
                result.add(this.range);
            }
            return result;
        }
        if(this.valid){
            result.add(this.range);
        }
        if(this.left != null){
            if(this.left.covers(query)){
                result.addAll(this.left.query(query));
            }else{
                result.addAll(this.right.query(query));
            }
        }
        return result;
    }

    public boolean covers(Range range){
        return this.range.covers(range);
    }

    private void splitNode(){
        if(left == null && this.range.getLength() >= 2){
            long newWidth = this.range.getLength() / 2;
            this.left = new PrefixTree(range.getStart(), newWidth);
            this.right = new PrefixTree(range.getStart() + newWidth, newWidth);
        }
    }
}
