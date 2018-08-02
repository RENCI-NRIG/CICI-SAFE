package exoplex.experiment.task;

public interface TaskInterface {
  void runTask();
  void terminate();
  void start();
  boolean isStarted();
  void waitTillEnd();
}
