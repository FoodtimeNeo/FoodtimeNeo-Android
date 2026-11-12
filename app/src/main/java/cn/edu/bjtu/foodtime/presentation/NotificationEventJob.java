package cn.edu.bjtu.foodtime.presentation;

import android.app.job.JobParameters;
import android.app.job.JobService;
import cn.edu.bjtu.foodtime.domain.model.Models.NotificationEvent;
import cn.edu.bjtu.foodtime.domain.model.Models.NotificationPreference;
import cn.edu.bjtu.foodtime.domain.model.Models.NotificationPreferences;
import cn.edu.bjtu.foodtime.domain.repository.RepositoryException;
import cn.edu.bjtu.foodtime.domain.repository.RepositoryProvider;
import java.util.HashMap;
import java.util.Map;

/** Polls stable server events; message events remain owned by PrivateMessageReminders. */
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
      NotificationPreferences preferences = repository.notificationPreferences();
      Map<String, Boolean> enabled = new HashMap<>();
      for (NotificationPreference preference : preferences.items())
        enabled.put(preference.category(), preference.enabled());
      long cursor = NotificationEventReminders.cursor(this, user.id());
      var page = repository.notificationEvents(cursor, 50);
      for (NotificationEvent event : page.items()) {
        if (active != parameters) return;
        if ("message".equals(event.category())) continue;
        boolean wanted = Boolean.TRUE.equals(enabled.get(event.category()));
        if (wanted && "pending".equals(event.deliveryStatus())) {
          NotificationEventReminders.notify(this, event);
          try { repository.markNotificationDelivered(event.eventId()); } catch (RepositoryException ignored) { }
        }
      }
      NotificationEventReminders.cursor(this, user.id(), page.nextSeq());
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
    return NotificationEventReminders.permitted(this);
  }
}
