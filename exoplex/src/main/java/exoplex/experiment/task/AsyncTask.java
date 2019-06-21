package exoplex.experiment.task;

import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public abstract class AsyncTask {
  protected final UUID taskId;
  protected final List<UUID> dependencies;
  protected Long offSetTime;
  protected TimeUnit timeUnit;
  protected Thread thread;
  protected boolean started = false;
  protected ReentrantLock lock;
  protected boolean ended = false;
  protected HashMap<UUID, AsyncTask> allTasks;

  public AsyncTask(UUID taskId, Long offSetTime, TimeUnit timeUnit, List<UUID> dependencies,
                   HashMap<UUID, AsyncTask> allTasks) {
    this.taskId = taskId;
    this.offSetTime = offSetTime;
    this.timeUnit = timeUnit;
    this.dependencies = dependencies;
    this.allTasks = allTasks;
    this.lock = new ReentrantLock();
  }

  public boolean isStarted() {
    boolean res;
    lock.lock();
    res = started;
    lock.unlock();
    return res;
  }

  public void start() {
    lock.lock();
    if (!started) {
      thread = new Thread() {
        @Override
        public void run() {
          if (offSetTime > 0) {
            try {
              timeUnit.sleep(offSetTime);
            } catch (Exception e) {
            }
          }
          for (UUID taskUUID : dependencies) {
            if (allTasks.containsKey(taskUUID)) {
              AsyncTask parentTask = allTasks.get(taskUUID);
              if (!parentTask.isStarted()) {
                parentTask.start();
              }
              allTasks.get(taskUUID).waitTillEnd();
            }
          }
          runTask();
        }
      };
      thread.start();
      started = true;
    }
    lock.unlock();
  }

  public abstract void runTask();

  public void terminate() {
    if (started && !ended) {
      thread.interrupt();
    }
  }

  public void stop() {
    throw new NotImplementedException();
  }

  public void waitTillEnd() {
    if (ended) {
      return;
    }
    try {
      thread.join();
    } catch (Exception e) {
    } finally {
      ended = true;
    }
  }
}
