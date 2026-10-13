package cn.edu.bjtu.foodtime.app;

import android.app.Application;
import cn.edu.bjtu.foodtime.BuildConfig;
import cn.edu.bjtu.foodtime.data.remote.*;
import java.net.*;
import java.util.concurrent.*;
import cn.edu.bjtu.foodtime.domain.repository.FoodtimeRepository;
import cn.edu.bjtu.foodtime.domain.repository.RepositoryProvider;

/** Application composition root. Concrete data sources are wired only in this package. */
public final class FoodtimeApplication extends Application implements RepositoryProvider {
  private FoodtimeRepository repository;
  private final ExecutorService worker=Executors.newSingleThreadExecutor();

  @Override
  public void onCreate() {
    super.onCreate();
    var cookies=new CookieManager(new EncryptedCookieStore(this,BuildConfig.API_BASE_URL),CookiePolicy.ACCEPT_ORIGINAL_SERVER);
    repository = new RemoteRepository(BuildConfig.API_BASE_URL,new HttpTransport(BuildConfig.API_BASE_URL,cookies));
  }

  @Override
  public FoodtimeRepository repository() {
    return repository;
  }
  @Override public Executor workExecutor() { return worker; }
}
