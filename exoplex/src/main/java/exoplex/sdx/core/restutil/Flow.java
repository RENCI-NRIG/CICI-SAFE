package exoplex.sdx.core.restutil;

public class Flow {
  public String src;
  public String dest;

  public Flow() {
  }

  public String toString(){
    return String.format("src: %s dest: %s", src, dest);
  }
}
