package cn.edu.bjtu.foodtime.presentation;

import android.app.NotificationManager;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.SharedPreferences;

/** Read-and-retire bridge for an installed version's local private-message choice. */
final class PrivateMessageReminders {
  static final String OPEN_INBOX="cn.edu.bjtu.foodtime.OPEN_PRIVATE_MESSAGE_INBOX";
  private static final int ID=2107;
  private PrivateMessageReminders(){}
  private static SharedPreferences settings(Context c){return c.getSharedPreferences("private-message-reminders",Context.MODE_PRIVATE);}
  static boolean configured(Context c,String actor){return !actor.isEmpty()&&actor.equals(settings(c).getString("actor",""));}
  static boolean hasLegacyChoice(Context c){return !settings(c).getString("actor","").isEmpty();}
  static synchronized void disable(Context c){
    settings(c).edit().clear().apply();var scheduler=c.getSystemService(JobScheduler.class);if(scheduler!=null)scheduler.cancel(ID);
    var manager=c.getSystemService(NotificationManager.class);if(manager!=null)manager.cancel(ID);
  }
}
