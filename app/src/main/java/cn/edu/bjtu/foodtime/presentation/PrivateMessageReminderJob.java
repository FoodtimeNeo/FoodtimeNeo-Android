package cn.edu.bjtu.foodtime.presentation;

import android.app.job.JobParameters;
import android.app.job.JobService;
import cn.edu.bjtu.foodtime.domain.repository.RepositoryProvider;
import cn.edu.bjtu.foodtime.domain.repository.RepositoryException;

public final class PrivateMessageReminderJob extends JobService {
  private volatile JobParameters active;

  @Override public boolean onStartJob(JobParameters parameters) {
    if (!PrivateMessageReminders.shouldCheck(this)) return false;
    active = parameters;
    RepositoryProvider provider = (RepositoryProvider) getApplication();
    provider.workExecutor().execute(() -> check(parameters, provider));
    return true;
  }

  private void check(JobParameters parameters, RepositoryProvider provider) {
    boolean retry = false;
    try {
      if (active != parameters || !PrivateMessageReminders.shouldCheck(this)) return;
      var repository = provider.repository();
      if (repository.currentUser() == null) repository.restoreSession();
      var user = repository.currentUser();
      if (user == null || !PrivateMessageReminders.configured(this, user.id())) return;
      long unread = repository.refreshDirectUnread();
      if (active == parameters) PrivateMessageReminders.update(this, user.id(), unread);
    } catch (RepositoryException failure) {
      retry = failure.status() == 0 || failure.status() >= 500;
    } finally {
      if (active == parameters) {
        active = null;
        jobFinished(parameters, retry);
      }
    }
  }

  @Override public boolean onStopJob(JobParameters parameters) {
    if (active == parameters) active = null;
    return PrivateMessageReminders.shouldCheck(this);
  }
}
