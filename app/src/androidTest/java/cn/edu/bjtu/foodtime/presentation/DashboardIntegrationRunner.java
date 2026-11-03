package cn.edu.bjtu.foodtime.presentation;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.*;
import android.widget.TextView;
import cn.edu.bjtu.foodtime.BuildConfig;
import cn.edu.bjtu.foodtime.domain.model.Models.*;
import cn.edu.bjtu.foodtime.domain.repository.*;
import java.io.FileOutputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Real native acceptance against the opt-in local Backend, never a cloud API. */
public final class DashboardIntegrationRunner extends Instrumentation {
  private Bundle args;private int checks;
  @Override public void onCreate(Bundle arguments){super.onCreate(arguments);args=arguments;start();}
  private void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
  private String text(View view){StringBuilder value=new StringBuilder();if(view instanceof TextView label)value.append(label.getText()).append('\n');if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)value.append(text(group.getChildAt(i)));return value.toString();}
  private void ui(Runnable action){AtomicReference<Throwable> failure=new AtomicReference<>();runOnMainSync(()->{try{action.run();}catch(Throwable error){failure.set(error);}});if(failure.get()!=null)throw new AssertionError(failure.get());}
  private TextView label(MainActivity a,String tag){View view=a.content.findViewWithTag(tag);if(!(view instanceof TextView))throw new AssertionError("Missing label "+tag);return (TextView)view;}
  private void ready(MainActivity a){long end=SystemClock.uptimeMillis()+20000;while(SystemClock.uptimeMillis()<end){AtomicReference<Boolean> done=new AtomicReference<>(false);ui(()->done.set(!a.isPageLoading()&&a.content.findViewWithTag("dashboard-refresh")!=null));if(done.get())return;SystemClock.sleep(100);}throw new AssertionError("Dashboard did not finish loading");}
  private static String format(long value){return String.format(Locale.CHINA,"%,d",value);}
  private void summary(MainActivity a,AdminDashboard snapshot){
    String[] keys={"users","reviews","replies","posts"};int[] periods={1,7,30};
    for(int column=0;column<periods.length;column++) {
      DashboardAdditions added=snapshot.additions(periods[column]);long[] values={added.newUsers(),added.newReviews(),added.newReplies(),added.newPosts()};
      for(int row=0;row<keys.length;row++)check(label(a,"dashboard-summary-"+keys[row]+"-"+(column+1)).getText().toString().equals("+"+format(values[row])),"Wrong "+keys[row]+" summary for "+periods[column]+" days");
    }
  }
  private void layout(View view){
    if(view.getParent() instanceof ViewGroup parent&&view.getVisibility()==View.VISIBLE) {
      check(view.getLeft()>=-1&&view.getRight()<=parent.getWidth()+1,"Horizontal clipping: "+view.getTag());
    }
    if(view instanceof TextView label&&label.getLayout()!=null) {
      for(int line=0;line<label.getLayout().getLineCount();line++)check(label.getLayout().getEllipsisCount(line)==0,"Truncated text: "+label.getText());
    }
    if(view instanceof ViewGroup group)for(int index=0;index<group.getChildCount();index++)layout(group.getChildAt(index));
  }
  private void screenshot(MainActivity a,String tag,String name) throws Exception {
    ui(()->{if(tag==null)a.scroll.scrollTo(0,0);else{View target=a.content.findViewWithTag(tag);int[] destination=new int[2],origin=new int[2];target.getLocationInWindow(destination);a.scroll.getLocationInWindow(origin);a.scroll.scrollBy(0,destination[1]-origin[1]-Ui.dp(a,12));}});
    waitForIdleSync();SystemClock.sleep(300);
    var activeWindow=getUiAutomation().getRootInActiveWindow();
    long focusBy=SystemClock.uptimeMillis()+5000;
    while((activeWindow==null||!BuildConfig.APPLICATION_ID.contentEquals(activeWindow.getPackageName()))&&SystemClock.uptimeMillis()<focusBy){SystemClock.sleep(100);activeWindow=getUiAutomation().getRootInActiveWindow();}
    check(activeWindow!=null&&BuildConfig.APPLICATION_ID.contentEquals(activeWindow.getPackageName()),"A system window obscures the dashboard screenshot: "+(activeWindow==null?"none":activeWindow.getPackageName()));
    String suffix=args.getString("evidenceSuffix","");
    try(var output=new FileOutputStream(new java.io.File(getTargetContext().getFilesDir(),name+suffix+".png"))){getUiAutomation().takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,output);}
  }
  @Override public void onStart(){Bundle result=new Bundle();MainActivity activity=null;
    try {
      check(BuildConfig.APPLICATION_ID.endsWith(".dashboardcheck"),"Use isolated .dashboardcheck package");
      check(BuildConfig.API_BASE_URL.equals("http://127.0.0.1:18080/api/v1/"),"Only dedicated loopback Backend permitted");
      String password=args.getString("testPassword"),account=args.getString("superadminAccount");
      check(password!=null&&account!=null,"Missing local credentials");
      activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));MainActivity a=activity;
      long restoredBy=SystemClock.uptimeMillis()+20000;
      while(SystemClock.uptimeMillis()<restoredBy){AtomicReference<Boolean> done=new AtomicReference<>(false);ui(()->done.set(!text(a.root).contains("正在连接…")));if(done.get())break;SystemClock.sleep(100);}
      FoodtimeRepository repository=a.repo;repository.login(account,password);
      ui(()->{a.tab="dashboard";a.stacks.get("dashboard").clear();a.stacks.get("dashboard").add(new MainActivity.Route("admin-dashboard",""));a.render();});
      ready(a);AdminDashboard snapshot=repository.adminDashboard();
      check(snapshot!=null,"No server snapshot");check(snapshot.trend().size()==30,"Expected complete 30-day snapshot");
      check(snapshot.totalUsers()>snapshot.registeredUsers(),"Disabled fixture must be counted in total users");check(snapshot.onlineUsers()>=1,"Authenticated presence missing");
      ui(()->{
        check(label(a,"dashboard-range-title").getText().toString().equals("近 7 日趋势"),"Default range must be 7 days");
        check(label(a,"dashboard-total-users").getText().toString().equals(format(snapshot.totalUsers())),"Wrong user total");
        long[] values={snapshot.registeredUsers(),snapshot.reviews(),snapshot.replies(),snapshot.posts(),snapshot.halls(),snapshot.stalls(),snapshot.dishes(),snapshot.onlineUsers()};
        for(int index=0;index<values.length;index++)check(label(a,"dashboard-metric-"+index).getText().toString().equals(format(values[index])),"Wrong overview metric");
        summary(a,snapshot);
        for(int days:new int[]{30,7,30}) {
          label(a,"dashboard-range-"+days).performClick();check(label(a,"dashboard-range-title").getText().toString().equals("近 "+days+" 日趋势"),"Range did not switch");
          check(label(a,"dashboard-range-"+days).isSelected(),"Selected range not exposed");
          for(int metric=0;metric<5;metric++) {
            TextView button=label(a,"dashboard-metric-tab-"+metric);check(button.getHeight()>=Ui.dp(a,48),"Small touch target");button.performClick();
            check(button.isSelected(),"Selected metric not exposed");check(label(a,"dashboard-point-detail").getText().toString().contains(button.getText()),"Metric detail did not change");
          }
          summary(a,snapshot);check(repository.adminDashboard()==snapshot,"Presentation switch fetched another snapshot");
        }
        View chart=a.content.findViewWithTag("dashboard-chart");chart.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD,null);
        check(label(a,"dashboard-point-detail").getText().toString().contains(snapshot.trend().get(28).day().toString().replace('-','/')),"Accessible daily selection failed");
        String page=text(a.content);check(page.contains("北京时间"),"Business timezone absent");check(!page.contains("查看每日数据")&&!page.contains("期末用户总量")&&!page.contains("各日截至当日的累计注册账号数"),"Removed content returned");
        layout(a.content);
        label(a,"dashboard-metric-tab-0").performClick();
      });
      screenshot(a,null,"dashboard-top");screenshot(a,"dashboard-range-30","dashboard-trend");screenshot(a,"dashboard-summary","dashboard-summary");
      SystemClock.sleep(100);ui(()->label(a,"dashboard-refresh").performClick());ready(a);
      check(repository.adminDashboard().generatedAt()>snapshot.generatedAt(),"Refresh did not reach Backend");
      ui(()->{check(label(a,"dashboard-range-title").getText().toString().equals("近 30 日趋势"),"Refresh lost range");check(label(a,"dashboard-metric-tab-0").isSelected(),"Refresh lost metric");summary(a,repository.adminDashboard());});
      for(String role:new String[]{"admin","user"}) {
        repository.logout();repository.login(args.getString(role+"Account"),password);check(repository.adminDashboard()==null,"Private snapshot leaked to another role");
        try{repository.refresh(new ContentRequest(ContentRequest.Query.ADMIN_DASHBOARD,""));throw new AssertionError("Non-superadmin read dashboard");}
        catch(RepositoryException denied){check(denied.status()==403,"Wrong denial status");}
      }
      repository.logout();check(repository.adminDashboard()==null,"Logout retained statistics");
      result.putString("stream","Dashboard local Backend/native UI PASS: "+checks+" checks\n");finish(-1,result);
    }catch(Throwable failure){result.putString("stream","Dashboard FAIL: "+failure+"\n");finish(1,result);}
    finally{if(activity!=null){MainActivity a=activity;ui(a::finish);}}
  }
}
