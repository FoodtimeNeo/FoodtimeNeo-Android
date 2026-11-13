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
import android.net.Uri;
import cn.edu.bjtu.foodtime.BuildConfig;
import cn.edu.bjtu.foodtime.R;
import cn.edu.bjtu.foodtime.domain.model.Models.NotificationEvent;
import cn.edu.bjtu.foodtime.domain.model.Models.NotificationPreference;
import cn.edu.bjtu.foodtime.domain.model.Models.NotificationPreferences;
import cn.edu.bjtu.foodtime.domain.notification.NotificationBatchPoller;
import cn.edu.bjtu.foodtime.domain.repository.FoodtimeRepository;
import cn.edu.bjtu.foodtime.domain.repository.RepositoryException;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;

/** Scoped journal, periodic scheduling and privacy-preserving system notification adapter. */
final class NotificationEventReminders {
  static final String OPEN_EVENT="cn.edu.bjtu.foodtime.OPEN_NOTIFICATION_EVENT";
  static final int JOB_ID=2108;
  private static final String CHANNEL="activity-reminders",PRIVATE_CHANNEL="private-messages";
  private static final HashMap<String,NotificationPreference> choices=new HashMap<>();
  private static long preferenceEpoch;
  private NotificationEventReminders(){}
  private static SharedPreferences settings(Context c){return c.getSharedPreferences("notification-events",Context.MODE_PRIVATE);}
  static boolean configured(Context c,String actor){return !actor.isEmpty()&&actor.equals(settings(c).getString("actor",""))&&BuildConfig.API_BASE_URL.equals(settings(c).getString("origin",""));}
  static boolean permitted(Context c){var manager=c.getSystemService(NotificationManager.class);return manager!=null&&manager.areNotificationsEnabled();}
  static synchronized void configure(Context c,String actor){
    if(configured(c,actor))return;
    choices.clear();preferenceEpoch++;
    settings(c).edit().clear().putString("actor",actor).putString("origin",BuildConfig.API_BASE_URL).apply();
    var manager=c.getSystemService(NotificationManager.class);if(manager!=null)manager.cancelAll();
  }
  static synchronized void disable(Context c){
    choices.clear();preferenceEpoch++;
    settings(c).edit().clear().apply();var scheduler=c.getSystemService(JobScheduler.class);if(scheduler!=null)scheduler.cancel(JOB_ID);
    var manager=c.getSystemService(NotificationManager.class);if(manager!=null)manager.cancelAll();
  }
  static synchronized boolean migrated(Context c,String actor){return configured(c,actor)&&settings(c).getBoolean("migrated",false);}
  static synchronized void completeMigration(Context c,String actor){
    require(c,actor);if(!settings(c).edit().putBoolean("migrated",true).commit())throw storage();
    PrivateMessageReminders.disable(c);
  }
  static void initialize(Context context,FoodtimeRepository repo,Executor worker){
    Context c=context.getApplicationContext();var current=repo.currentUser();if(current==null)return;String actor=current.id();
    worker.execute(()->{try{NotificationPreferenceMigration.prepare(c,repo,actor);}catch(RepositoryException|CancellationException ignored){/* Settings and the scheduled job provide retry paths. */}});
  }
  static boolean ensureScheduled(Context c){
    var scheduler=c.getSystemService(JobScheduler.class);if(scheduler==null)return false;
    if(scheduler.getPendingJob(JOB_ID)!=null)return true;
    try{return scheduler.schedule(new JobInfo.Builder(JOB_ID,new ComponentName(c,NotificationEventJob.class))
        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setRequiresBatteryNotLow(true).setPersisted(true)
        .setPeriodic(JobInfo.getMinPeriodMillis()).build())==JobScheduler.RESULT_SUCCESS;}
    catch(SecurityException|IllegalStateException rejected){return false;}
  }
  static synchronized long cursor(Context c,String actor){return configured(c,actor)?settings(c).getLong("cursor",0):0;}
  static NotificationBatchPoller.Journal journal(Context c,String actor){
    return new NotificationBatchPoller.Journal(){
      public long cursor(){synchronized(NotificationEventReminders.class){require(c,actor);return settings(c).getLong("cursor",0);}}
      public boolean wasShown(String id){synchronized(NotificationEventReminders.class){require(c,actor);return shownIds(c).contains(id);}}
      public void shown(List<String> ids){synchronized(NotificationEventReminders.class){require(c,actor);var values=shownIds(c);values.addAll(ids);if(!settings(c).edit().putStringSet("shown",values).commit())throw storage();}}
      public void checkpoint(long seq,String id){synchronized(NotificationEventReminders.class){require(c,actor);var values=shownIds(c);values.remove(id);
        if(!settings(c).edit().putLong("cursor",Math.max(seq,settings(c).getLong("cursor",0))).putStringSet("shown",values).commit())throw storage();}}
    };
  }
  private static HashSet<String> shownIds(Context c){return new HashSet<>(settings(c).getStringSet("shown",new HashSet<>()));}
  private static void require(Context c,String actor){if(!configured(c,actor))throw new CancellationException("Reminder account changed");}
  private static RepositoryException storage(){return new RepositoryException(0,"REMINDER_STORAGE","无法保存提醒状态，请重试",0);}
  static synchronized void cancelCategory(Context c,String actor,String category){
    if(!configured(c,actor))return;var manager=c.getSystemService(NotificationManager.class);if(manager!=null)manager.cancel(tag(actor,category),JOB_ID);
  }
  /** Revision ordering prevents a slow GET from undoing a choice saved while it was in flight. */
  static synchronized NotificationPreferences accept(Context c,String actor,NotificationPreferences snapshot){
    require(c,actor);
    var merged=new java.util.ArrayList<NotificationPreference>();
    for(var item:snapshot.items()){
      var known=choices.get(item.category());
      if(known==null||item.revision()>known.revision()){
        choices.put(item.category(),item);preferenceEpoch++;
        if(!item.enabled())cancelCategory(c,actor,item.category());
        known=item;
      }
      merged.add(known);
    }
    return new NotificationPreferences(merged);
  }
  static synchronized long epoch(Context c,String actor,NotificationPreferences snapshot){
    require(c,actor);
    for(var item:snapshot.items())if(!item.equals(choices.get(item.category())))throw new CancellationException("Reminder preferences changed");
    return preferenceEpoch;
  }
  static synchronized boolean unchanged(Context c,String actor,long epoch){return configured(c,actor)&&preferenceEpoch==epoch;}
  static synchronized boolean post(Context c,String actor,String category,NotificationEvent latest,BooleanSupplier active){
    if(!active.getAsBoolean()||!configured(c,actor)||!permitted(c))return false;
    var manager=c.getSystemService(NotificationManager.class);if(manager==null)return false;
    String channel="message".equals(category)?PRIVATE_CHANNEL:CHANNEL;
    manager.createNotificationChannel(new NotificationChannel(channel,"message".equals(category)?"私信提醒":"互动与处理提醒",NotificationManager.IMPORTANCE_DEFAULT));
    Intent open=new Intent(c,MainActivity.class).setAction(OPEN_EVENT)
        .setData(Uri.parse("foodtime://notification/"+actor+"/"+category))
        .putExtra("actor",actor).putExtra("category",category).putExtra("refType",latest.refType()).putExtra("refId",latest.refId())
        .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
    PendingIntent target=PendingIntent.getActivity(c,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    try{
      manager.notify(tag(actor,category),JOB_ID,new Notification.Builder(c,channel)
          .setSmallIcon(R.drawable.ic_private_message_notification).setContentTitle(title(category))
          .setContentText("打开北交干饭吧查看").setVisibility(Notification.VISIBILITY_PRIVATE)
          .setContentIntent(target).setAutoCancel(true).build());
      return true;
    }catch(SecurityException revoked){return false;}
  }
  private static String tag(String actor,String category){return "events:"+actor+":"+category;}
  private static String title(String category){return switch(category){
    case "message" -> "收到新私信";case "interaction" -> "有新的互动提醒";case "notice" -> "公告有更新";
    case "feedback" -> "反馈有新回复";case "correction" -> "目录纠错有结果";case "report" -> "举报处理有更新";case "appeal" -> "申诉复核有结果";default -> "饭搭子提醒";};}
}
