package cn.edu.bjtu.foodtime.app;

import android.app.Application;
import cn.edu.bjtu.foodtime.data.local.LocalRepository;
import cn.edu.bjtu.foodtime.domain.repository.FoodtimeRepository;
import cn.edu.bjtu.foodtime.domain.repository.RepositoryProvider;

/** Application composition root. Concrete data sources are wired only in this package. */
public final class FoodtimeApplication extends Application implements RepositoryProvider {
  private FoodtimeRepository repository;

  @Override
  public void onCreate() {
    super.onCreate();
    repository = new LocalRepository(this);
  }

  @Override
  public FoodtimeRepository repository() {
    return repository;
  }
}
