package cn.edu.bjtu.foodtime.presentation;

import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.edu.bjtu.foodtime.domain.model.Models.NotificationPreferences;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** Seven server-backed switches; system permission and each category choice remain separate. */
final class NotificationSettingsScreen {
  private final MainActivity activity;
  private final LinearLayout page,rows;
  private final TextView error,refresh;
  private final String actor,key="notification-settings:"+UUID.randomUUID();
  private long ticket;
  static void show(MainActivity a,LinearLayout page,MainActivity.Route route){
    a.heading(page,"05.1","提醒设置","站内消息始终可读；这里控制系统提醒");
    if(a.repo.currentUser()!=null)new NotificationSettingsScreen(a,page);
  }
  private NotificationSettingsScreen(MainActivity a,LinearLayout page){
    activity=a;this.page=page;actor=a.repo.currentUser().id();
    LinearLayout permission=Ui.col(a);permission.setTag("notification-permission-hint");
    permission.addView(Ui.text(a,"系统尚未允许通知，允许后才能收到系统提醒。",14,Design.MUTED));
    Ui.action(permission,"允许系统通知",false,()->a.requestNotificationPermission(()->refreshPermissionState(a)));
    page.addView(permission);Ui.gap(page,12);Ui.action(page,"系统通知设置",false,()->NotificationPermission.openSettings(a));Ui.gap(page,12);
    refresh=Ui.button(a,"刷新设置",false,this::load);page.addView(refresh);
    error=Ui.text(a,"",14,Design.RED);Ui.observeError(error);page.addView(error);
    rows=Ui.col(a);page.addView(rows);refreshPermissionState(a);load();
  }
  static void refreshPermissionState(MainActivity a){
    if(a.root==null)return;View hint=a.root.findViewWithTag("notification-permission-hint");
    if(hint!=null)hint.setVisibility(NotificationEventReminders.permitted(a)?View.GONE:View.VISIBLE);
  }
  private boolean current(){var user=activity.repo.currentUser();return page.isAttachedToWindow()&&user!=null&&actor.equals(user.id())&&activity.route().kind().equals("notification-settings");}
  private void load(){
    long current=++ticket;error.setText("");rows.removeAllViews();rows.addView(Ui.text(activity,"正在读取…",14,Design.MUTED));
    AtomicReference<NotificationPreferences> result=new AtomicReference<>();
    activity.read(key+":"+current,refresh,error,()->result.set(NotificationPreferenceMigration.prepare(activity,activity.repo,actor)),()->{
      if(current==ticket)render(result.get());
    },()->{if(current==ticket)rows.removeAllViews();});
  }
  private void render(NotificationPreferences preferences){
    rows.removeAllViews();refreshPermissionState(activity);
    rows.addView(Ui.text(activity,"开启某类只影响之后产生的事件；站内消息和未读不受影响。",12,Design.MUTED));
    for(var item:preferences.items()){
      CheckBox box=new CheckBox(activity);box.setText(label(item.category()));box.setTag("reminder-category:"+item.category());
      box.setTextColor(Design.INK);box.setTextSize(15);box.setMinHeight(Ui.dp(activity,48));box.setChecked(item.enabled());
      box.setOnCheckedChangeListener((button,value)->{
        if(value==item.enabled())return;
        box.setChecked(item.enabled());
        if(activity.operationInFlight()){activity.message("正在保存上一项设置，请稍候。");return;}
        Runnable save=()->{
          if(!current())return;
          activity.request(box,error,()->{
            NotificationPreferenceMigration.require(activity,activity.repo,actor);
            var saved=activity.repo.updateNotificationPreference(item.category(),value,item.revision());
            NotificationPreferenceMigration.require(activity,activity.repo,actor);
            NotificationEventReminders.accept(activity,actor,new NotificationPreferences(java.util.List.of(saved)));
          },this::load);
        };
        if(value)activity.requestNotificationPermission(save);else save.run();
      });
      rows.addView(box);
    }
  }
  private static String label(String category){return switch(category){case "message"->"私信";case "interaction"->"互动";case "notice"->"公告";case "feedback"->"反馈";case "correction"->"纠错";case "report"->"举报";case "appeal"->"申诉";default->category;};}
}
