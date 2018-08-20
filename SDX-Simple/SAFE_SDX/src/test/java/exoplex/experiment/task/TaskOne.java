package exoplex.experiment.task;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class TaskOne extends AsyncTask implements TaskInterface{
  String msg;

  public TaskOne(UUID taskId, Long offSetTime, TimeUnit timeUnit, List<UUID> dependencies,
                 HashMap<UUID, AsyncTask> allTasks, String message){
    super(taskId, offSetTime, timeUnit, dependencies, allTasks);
    this.msg = message;
  }

  @Override
  public void runTask(){
    System.out.println(msg);
  }
}
