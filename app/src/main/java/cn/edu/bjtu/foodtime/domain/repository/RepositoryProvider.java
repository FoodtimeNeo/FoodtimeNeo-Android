package cn.edu.bjtu.foodtime.domain.repository;

/** Supplies the repository contract without exposing a concrete data implementation to UI code. */
public interface RepositoryProvider {
  FoodtimeRepository repository();
  java.util.concurrent.Executor workExecutor();
}
