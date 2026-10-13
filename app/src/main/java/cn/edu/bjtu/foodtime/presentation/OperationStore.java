package cn.edu.bjtu.foodtime.presentation;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/** In-memory UI operation state survives Activity recreation, never an application process restart. */
final class OperationStore {
  record Outcome(String value,RuntimeException failure) {}
  record Task(long id,MainActivity.Route origin,String action,String imageDraft,
      CompletableFuture<Outcome> result) {}
  private final AtomicLong sequence=new AtomicLong();
  private final ConcurrentMap<Long,Task> tasks=new ConcurrentHashMap<>();
  Task start(Executor executor,MainActivity.Route origin,String action,String imageDraft,Supplier<String> work) {
    Task task=new Task(sequence.incrementAndGet(),origin,action,imageDraft,new CompletableFuture<>());
    tasks.put(task.id(),task);
    executor.execute(() -> {
      try {task.result().complete(new Outcome(work.get(),null));}
      catch(RuntimeException failure) {task.result().complete(new Outcome(null,failure));}
    });
    return task;
  }
  Task find(long id) {return tasks.get(id);}
  void forget(long id) {tasks.remove(id);}
}
