package cn.edu.bjtu.foodtime.presentation;

import android.app.job.JobParameters;
import android.app.job.JobService;
import cn.edu.bjtu.foodtime.domain.repository.RepositoryProvider;
import cn.edu.bjtu.foodtime.domain.repository.RepositoryException;
import java.util.concurrent.CancellationException;

/** Upgrade bridge for persisted legacy jobs. It never infers new messages from unread totals. */
public final class PrivateMessageReminderJob extends JobService {
  private volatile JobParameters active;

  @Override public boolean onStartJob(JobParameters parameters) {
    if (!PrivateMessageReminders.hasLegacyChoice(this)) return false;
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
      if (user == null || "appeal_only".equals(repository.sessionMode())) return;
      NotificationEventReminders.configure(this, user.id());
      NotificationEventReminders.ensureScheduled(this);
      NotificationPreferenceMigration.prepare(this, repository, user.id());
    } catch (RepositoryException failure) {
      retry = failure.status() == 0 || failure.status() >= 500;
    } catch (CancellationException changed) {
      retry = false;
    } finally {
      if (active == parameters) {
        active = null;
        jobFinished(parameters, retry);
      }
    }
  }

  @Override public boolean onStopJob(JobParameters parameters) {
    if (active == parameters) active = null;
    return PrivateMessageReminders.hasLegacyChoice(this);
  }
}
