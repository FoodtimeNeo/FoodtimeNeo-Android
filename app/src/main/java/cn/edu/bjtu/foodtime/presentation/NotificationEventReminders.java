package cn.edu.bjtu.foodtime.presentation;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import cn.edu.bjtu.foodtime.BuildConfig;
import cn.edu.bjtu.foodtime.R;
import cn.edu.bjtu.foodtime.domain.model.Models.NotificationEvent;

/** Persistent cursor and system-notification delivery for server notification events. */
final class NotificationEventReminders {
  private static final String CHANNEL = "activity-reminders";
  private static final int JOB_ID = 2108;

  private NotificationEventReminders() {}

  private static SharedPreferences settings(Context context) {
    return context.getSharedPreferences("notification-events", Context.MODE_PRIVATE);
  }

  static boolean configured(Context context, String actor) {
    return !actor.isEmpty() && actor.equals(settings(context).getString("actor", ""));
  }

  static boolean permitted(Context context) {
    NotificationManager manager = context.getSystemService(NotificationManager.class);
    return manager != null && manager.areNotificationsEnabled();
  }

  static synchronized void configure(Context context, String actor) {
    settings(context).edit().putString("actor", actor).putString("origin", BuildConfig.API_BASE_URL).apply();
  }

  static synchronized void disable(Context context) {
    settings(context).edit().clear().apply();
    JobScheduler scheduler = context.getSystemService(JobScheduler.class);
    if (scheduler != null) scheduler.cancel(JOB_ID);
    NotificationManager manager = context.getSystemService(NotificationManager.class);
    if (manager != null) manager.cancelAll();
  }

  static boolean ensureScheduled(Context context) {
    JobScheduler scheduler = context.getSystemService(JobScheduler.class);
    if (scheduler == null) return false;
    if (scheduler.getPendingJob(JOB_ID) != null) return true;
    try {
      return scheduler.schedule(new JobInfo.Builder(JOB_ID, new ComponentName(context, NotificationEventJob.class))
          .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
          .setRequiresBatteryNotLow(true)
          .setPersisted(true)
          .setPeriodic(JobInfo.getMinPeriodMillis())
          .build()) == JobScheduler.RESULT_SUCCESS;
    } catch (SecurityException | IllegalStateException rejected) {
      return false;
    }
  }

  static long cursor(Context context, String actor) {
    if (!configured(context, actor)) return 0;
    return settings(context).getLong("cursor", 0);
  }

  static synchronized void cursor(Context context, String actor, long value) {
    if (configured(context, actor)) settings(context).edit().putLong("cursor", value).apply();
  }

  static void notify(Context context, NotificationEvent event) {
    NotificationManager manager = context.getSystemService(NotificationManager.class);
    if (manager == null) return;
    manager.createNotificationChannel(new NotificationChannel(CHANNEL, "互动与处理提醒",
        NotificationManager.IMPORTANCE_DEFAULT));
    Intent open = new Intent(context, MainActivity.class)
        .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    PendingIntent target = PendingIntent.getActivity(context, event.eventId().hashCode(), open,
        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    manager.notify(event.eventId().hashCode(), new Notification.Builder(context, CHANNEL)
        .setSmallIcon(R.drawable.ic_private_message_notification)
        .setContentTitle(categoryTitle(event.category()))
        .setContentText("打开北交干饭吧查看")
        .setVisibility(Notification.VISIBILITY_PRIVATE)
        .setContentIntent(target)
        .setAutoCancel(true)
        .build());
  }

  private static String categoryTitle(String category) {
    return switch (category) {
      case "interaction" -> "有新的互动提醒";
      case "feedback" -> "反馈有新回复";
      case "correction" -> "目录纠错有结果";
      case "report" -> "举报处理有更新";
      case "appeal" -> "申诉复核有结果";
      default -> "饭搭子提醒";
    };
  }
}
