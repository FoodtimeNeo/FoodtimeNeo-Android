package cn.edu.bjtu.foodtime.presentation;

import android.app.*;
import android.app.job.JobScheduler;
import android.content.*;
import android.os.*;
import android.widget.CheckBox;
import cn.edu.bjtu.foodtime.BuildConfig;
import cn.edu.bjtu.foodtime.domain.model.Models.*;
import cn.edu.bjtu.foodtime.domain.repository.RepositoryException;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.json.*;

/** Real private storage, NotificationManager, JobScheduler and safe click routes on a loopback-only APK. */
public final class NotificationReminderRunner extends Instrumentation {
  private MainActivity a;private int checks;
  @Override public void onCreate(Bundle args){super.onCreate(args);start();}
  private void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
  private void ui(Runnable task){var error=new AtomicReference<Throwable>();runOnMainSync(()->{try{task.run();}catch(Throwable t){error.set(t);}});if(error.get()!=null)throw new AssertionError(error.get());}
  interface Probe{boolean ready()throws Exception;}
  private void await(String label,Probe probe)throws Exception{long end=SystemClock.uptimeMillis()+20000;while(SystemClock.uptimeMillis()<end){if(probe.ready())return;SystemClock.sleep(50);}throw new AssertionError("Timeout: "+label);}
  private JSONObject fixture(String path)throws Exception{var connection=(HttpURLConnection)new URL("http://127.0.0.1:18098/fixture/"+path).openConnection();try{return new JSONObject(new String(connection.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getJSONObject("data");}finally{connection.disconnect();}}
  private void poll(){NotificationEventPolling.poll(a,a.repo,()->true);}
  // Android may add its own ranker_group summary; category assertions inspect the actual app event tags.
  private android.service.notification.StatusBarNotification[] notifications(){return Arrays.stream(a.getSystemService(NotificationManager.class).getActiveNotifications()).filter(n->n.getTag()!=null&&n.getTag().startsWith("events:")).toArray(android.service.notification.StatusBarNotification[]::new);}
  private void open(String category,String type,String ref)throws Exception{
    ui(()->a.onNewIntent(new Intent(a,MainActivity.class).setAction(NotificationEventReminders.OPEN_EVENT).putExtra("actor",a.repo.currentUser().id()).putExtra("category",category).putExtra("refType",type).putExtra("refId",ref)));
    waitForIdleSync();
  }
  @Override public void onStart(){var result=new Bundle();try{
    if(!BuildConfig.DEBUG||!BuildConfig.APPLICATION_ID.endsWith(".notificationcheck")||!BuildConfig.API_BASE_URL.equals("http://127.0.0.1:18098/api/v1/"))throw new AssertionError("Isolated loopback build required");
    String actor="9f59875b-8d8f-4b8b-8d76-4bdc48ad6f51";
    getTargetContext().getSharedPreferences("private-message-reminders",0).edit().putString("actor",actor).commit();
    a=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    await("session and migration",()->a.repo.currentUser()!=null&&NotificationEventReminders.migrated(a,actor));
    check(NotificationEventReminders.permitted(a),"Isolated APK permission granted by harness");
    var scheduler=a.getSystemService(JobScheduler.class);var job=scheduler.getPendingJob(2108);
    check(job!=null&&job.isPersisted()&&job.isPeriodic()&&job.getNetworkType()==android.app.job.JobInfo.NETWORK_TYPE_ANY,"Persistent network-constrained periodic job exists");
    scheduler.cancel(2108);
    check(!PrivateMessageReminders.hasLegacyChoice(a)&&scheduler.getPendingJob(2107)==null,"Old choice and old job retired");
    poll();check(notifications().length==0&&NotificationEventReminders.cursor(a,actor)==1,"Migration baselines historical pending event");
    fixture("add");poll();check(notifications().length==1,"Stable event posts although unread count stays five");
    var first=notifications()[0];long firstPost=first.getPostTime();
    check("private-messages".equals(first.getNotification().getChannelId()),"Private message preserves installed notification channel");
    check(first.getNotification().visibility==Notification.VISIBILITY_PRIVATE,"Private lockscreen visibility");
    check(!first.getNotification().extras.toString().contains("PRIVATE BODY")&&!first.getNotification().extras.toString().contains("PRIVATE TITLE"),"System notification carries no private message text");
    fixture("add");fixture("add");poll();check(notifications().length==1&&notifications()[0].getPostTime()>=firstPost,"Same category updates one notification");
    fixture("add");fixture("fail-receipt");
    try{poll();throw new AssertionError("Expected receipt failure");}catch(RepositoryException failure){check(failure.status()==503,"Transient receipt failure retained");}
    long receiptFailurePost=notifications()[0].getPostTime(),cursor=NotificationEventReminders.cursor(a,actor);SystemClock.sleep(80);poll();
    check(notifications()[0].getPostTime()==receiptFailurePost&&NotificationEventReminders.cursor(a,actor)>cursor,"Receipt retry advances without reposting");
    var p=a.repo.notificationPreferences().items().stream().filter(v->v.category().equals("message")).findFirst().orElseThrow();
    var off=a.repo.updateNotificationPreference("message",false,p.revision());
    NotificationEventReminders.accept(a,actor,new NotificationPreferences(List.of(off)));
    await("system category cancellation",()->notifications().length==0);
    check(notifications().length==0,"Explicit off cancels existing category notification");
    getTargetContext().getSharedPreferences("notification-events",0).edit().putBoolean("migrated",false).commit();
    getTargetContext().getSharedPreferences("private-message-reminders",0).edit().putString("actor",actor).commit();
    var prepared=NotificationPreferenceMigration.prepare(a,a.repo,actor);
    check(!prepared.items().stream().filter(v->v.category().equals("message")).findFirst().orElseThrow().enabled(),"R07 explicit newer off wins legacy on");
    fixture("add");poll();check(notifications().length==0,"Off category cannot alert on newly fetched event");
    long epoch=NotificationEventReminders.epoch(a,actor,prepared);
    var on=a.repo.updateNotificationPreference("message",true,off.revision());NotificationEventReminders.accept(a,actor,new NotificationPreferences(List.of(on)));
    check(!NotificationEventReminders.unchanged(a,actor,epoch),"Saving a choice invalidates an in-flight polling snapshot");
    var merged=NotificationEventReminders.accept(a,actor,prepared);
    check(merged.items().stream().filter(v->v.category().equals("message")).findFirst().orElseThrow().enabled(),"Slow stale GET cannot overwrite newer saved choice");
    for(var item:a.repo.notificationPreferences().items())if(!item.enabled())NotificationEventReminders.accept(a,actor,new NotificationPreferences(List.of(a.repo.updateNotificationPreference(item.category(),true,item.revision()))));
    for(String category:new String[]{"message","interaction","notice","feedback","correction","report","appeal"})fixture("add?category="+category);
    poll();check(notifications().length==7,"All seven categories have a distinct merged notification");
    for(var n:notifications())check("打开北交干饭吧查看".contentEquals(n.getNotification().extras.getCharSequence(Notification.EXTRA_TEXT)),"Generic body for "+n.getTag());
    ui(()->{a.selectTab("profile");a.go("notification-settings","");});
    await("seven server switches",()->{boolean[] ready={false};ui(()->ready[0]=a.root.findViewWithTag("reminder-category:appeal")!=null);return ready[0];});
    ui(()->{check(a.root.hasWindowFocus(),"Settings window unobscured");for(String category:new String[]{"message","interaction","notice","feedback","correction","report","appeal"})check(((CheckBox)a.root.findViewWithTag("reminder-category:"+category)).isChecked(),"Authoritative setting visible: "+category);});
    ui(()->((CheckBox)a.root.findViewWithTag("reminder-category:message")).performClick());
    await("saved switch off",()->!fixture("state").getJSONObject("preferences").getJSONObject("message").getBoolean("enabled"));
    await("reloaded switch",()->{boolean[] ready={false};ui(()->{CheckBox box=a.root.findViewWithTag("reminder-category:message");ready[0]=box!=null&&!box.isChecked()&&!a.operationInFlight();});return ready[0];});
    await("only disabled category removed",()->notifications().length==6);
    check(notifications().length==6,"Actual settings change persists off and removes only private message category");
    ui(()->((CheckBox)a.root.findViewWithTag("reminder-category:message")).performClick());
    await("saved switch on",()->fixture("state").getJSONObject("preferences").getJSONObject("message").getBoolean("enabled"));
    await("saved action complete",()->{boolean[] done={false};ui(()->done[0]=!a.operationInFlight());return done[0];});
    fixture("add");poll();
    var message=Arrays.stream(notifications()).filter(n->n.getTag().endsWith(":message")).findFirst().orElseThrow();
    message.getNotification().contentIntent.send();
    await("OS pending intent navigation",()->a.route().kind().equals("inbox")&&a.drafts.getString("inbox::tab","").equals("chat"));
    check(true,"Actual immutable OS PendingIntent opens the matching account inbox");
    String ref="00000000-0000-4000-8000-000000000001";
    open("message","message",ref);check(a.route().kind().equals("inbox")&&a.drafts.getString("inbox::tab","").equals("chat"),"Message click opens private inbox");
    open("notice","notice",ref);check(a.route().kind().equals("inbox")&&a.drafts.getString("inbox::tab","").equals("notice"),"Notice click opens announcement archive");
    open("feedback","feedback",ref);check(a.route().kind().equals("feedback-detail")&&a.route().id().equals(ref),"Feedback click opens own feedback route");
    open("report","moderation-decision",ref);check(a.route().kind().equals("report-receipts")&&a.route().id().equals("decisions"),"Decision click opens received decisions");
    ui(()->a.onNewIntent(new Intent(a,MainActivity.class).setAction(NotificationEventReminders.OPEN_EVENT).putExtra("actor",ref).putExtra("category","message")));
    check(a.route().kind().equals("report-receipts"),"Different account notification does not open a private route");
    NotificationEventReminders.configure(a,ref);await("old actor notifications removed",()->notifications().length==0);
    check(notifications().length==0&&NotificationEventReminders.cursor(a,ref)==0,"Account switch clears notifications and cursor");
    check(!NotificationEventReminders.unchanged(a,actor,epoch),"Old actor snapshot cannot publish after switch");
    NotificationEventReminders.disable(a);
    result.putString("stream","Notification reminder device PASS: "+checks+" assertions; controlled polling, real OS notifications/storage/jobs, not natural background scheduling\n");finish(-1,result);
  }catch(Throwable failure){result.putString("stream","Notification reminder device FAIL: "+android.util.Log.getStackTraceString(failure));finish(1,result);}finally{if(a!=null)ui(a::finish);}}
}
