package exoplex.sdx.core.restutil;

public class UndoStitchRequest {
  public String ckeyhash;
  public String cslice;
  public String creservid;

  @Override
  public String toString() {
    return String.format("%s %s %s", ckeyhash, cslice, creservid);
  }
}
