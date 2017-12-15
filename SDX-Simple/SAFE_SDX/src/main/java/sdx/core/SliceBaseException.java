package sdx.core;

public class SliceBaseException extends Exception {
  private static final long serialVersionUID = 1;
  public SliceBaseException(String message) {
    super(message);
  }

  public SliceBaseException(String message, Throwable t) {
    super(message, t);
  }

  public SliceBaseException(Throwable t) {
    super(t);
  }
}
