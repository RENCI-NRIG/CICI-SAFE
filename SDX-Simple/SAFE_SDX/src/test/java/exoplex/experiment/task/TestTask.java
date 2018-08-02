package exoplex.experiment.task;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class TestTask {
  public static void main(String[] args){
    HashMap<UUID, AsyncTask> allTasks= new HashMap<>();
    UUID uuid1 = UUID.randomUUID();
    allTasks.put(uuid1, new TaskOne(uuid1, 0l, TimeUnit.SECONDS, new ArrayList<>(), allTasks,
      "task1"));
    UUID uuid2 = UUID.randomUUID();
    allTasks.put(uuid2, new TaskOne(uuid1, 2l, TimeUnit.SECONDS, new ArrayList<>(), allTasks,
      "task2"));
    ArrayList<UUID> d = new ArrayList<>();
    d.add(uuid1);
    d.add(uuid2);
    UUID uuid3 = UUID.randomUUID();
    allTasks.put(uuid3, new TaskOne(uuid1, 0l, TimeUnit.SECONDS, d, allTasks,
      "task3"));

    UUID uuid4 = UUID.randomUUID();
    allTasks.put(uuid4, new TaskOne(uuid1, 0l, TimeUnit.SECONDS, new ArrayList<>(), allTasks,
      "task4"));
    for(AsyncTask task: allTasks.values()){
      task.start();
    }
    for(AsyncTask task: allTasks.values()){
      task.waitTillEnd();
    }
  }
}
