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
import cn.edu.bjtu.foodtime.R;

final class PrivateMessageReminders {
  static final String OPEN_INBOX = "cn.edu.bjtu.foodtime.OPEN_PRIVATE_MESSAGE_INBOX";
  private static final String CHANNEL = "private-messages";
  private static final int ID = 2107;

  private PrivateMessageReminders() {}

  private static SharedPreferences settings(Context context) {
    return context.getSharedPreferences("private-message-reminders", Context.MODE_PRIVATE);
  }

  static boolean configured(Context context, String actor) {
    return !actor.isEmpty() && actor.equals(settings(context).getString("actor", ""));
  }

  static boolean permitted(Context context) {
    return context.getSystemService(NotificationManager.class).areNotificationsEnabled();
  }

  static synchronized boolean enable(Context context, String actor, long unread) {
    if (!permitted(context)) return false;
    settings(context).edit().putString("actor", actor).putLong("unread", unread).apply();
    if (ensureScheduled(context)) return true;
    disable(context);
    return false;
  }

  static boolean ensureScheduled(Context context) {
    JobScheduler scheduler = context.getSystemService(JobScheduler.class);
    if (scheduler.getPendingJob(ID) != null) return true;
    try {
      return scheduler.schedule(new JobInfo.Builder(ID, new ComponentName(context, PrivateMessageReminderJob.class))
          .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
          .setRequiresBatteryNotLow(true)
          .setPersisted(true)
          .setPeriodic(JobInfo.getMinPeriodMillis())
          .build()) == JobScheduler.RESULT_SUCCESS;
    } catch (SecurityException | IllegalStateException rejected) {
      return false;
    }
  }

  static synchronized void disable(Context context) {
    settings(context).edit().clear().apply();
    context.getSystemService(JobScheduler.class).cancel(ID);
    context.getSystemService(NotificationManager.class).cancel(ID);
  }

  static boolean shouldCheck(Context context) {
    return !settings(context).getString("actor", "").isEmpty() && permitted(context);
  }

  static synchronized void update(Context context, String actor, long unread) {
    if (!configured(context, actor) || !permitted(context)) return;
    var preferences = settings(context);
    long previous = preferences.getLong("unread", 0);
    preferences.edit().putLong("unread", unread).apply();
    NotificationManager notifications = context.getSystemService(NotificationManager.class);
    if (unread == 0) {
      notifications.cancel(ID);
      return;
    }
    if (unread <= previous) return;
    notifications.createNotificationChannel(new NotificationChannel(CHANNEL, "私信提醒", NotificationManager.IMPORTANCE_DEFAULT));
    Intent open = new Intent(context, MainActivity.class).setAction(OPEN_INBOX)
        .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    PendingIntent target = PendingIntent.getActivity(context, ID, open,
        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    notifications.notify(ID, new Notification.Builder(context, CHANNEL)
        .setSmallIcon(R.drawable.ic_private_message_notification)
        .setContentTitle("收到新私信")
        .setContentText("打开北交干饭吧查看")
        .setVisibility(Notification.VISIBILITY_PRIVATE)
        .setContentIntent(target)
        .setAutoCancel(true)
        .build());
  }
}
