package cn.edu.bjtu.foodtime.presentation;

import android.app.job.JobParameters;
import android.app.job.JobService;
import cn.edu.bjtu.foodtime.domain.repository.RepositoryException;
import cn.edu.bjtu.foodtime.domain.repository.RepositoryProvider;
import java.util.concurrent.CancellationException;

/** Polls all seven stable-event categories through the platform-independent delivery use case. */
public final class NotificationEventJob extends JobService {
  private volatile JobParameters active;

  @Override public boolean onStartJob(JobParameters parameters) {
    if (!NotificationEventReminders.permitted(this)) return false;
    active = parameters;
    RepositoryProvider provider = (RepositoryProvider) getApplication();
    provider.workExecutor().execute(() -> check(parameters, provider));
    return true;
  }

  private void check(JobParameters parameters, RepositoryProvider provider) {
    boolean retry = false;
    try {
      if (active != parameters) return;
      var repository = provider.repository();
      if (repository.currentUser() == null) repository.restoreSession();
      var user = repository.currentUser();
      if (user == null || !NotificationEventReminders.configured(this, user.id())) return;
      retry = NotificationEventPolling.poll(this, repository, () -> active == parameters);
    } catch (RepositoryException failure) {
      retry = failure.status() == 0 || failure.status() >= 500;
    } catch (CancellationException changed) {
      retry = false;
    } finally {
      if (active == parameters) {
        active = null;
        jobFinished(parameters, retry && NotificationEventReminders.permitted(this));
      }
    }
  }

  @Override public boolean onStopJob(JobParameters parameters) {
    if (active == parameters) active = null;
    return NotificationEventReminders.permitted(this);
  }
}
