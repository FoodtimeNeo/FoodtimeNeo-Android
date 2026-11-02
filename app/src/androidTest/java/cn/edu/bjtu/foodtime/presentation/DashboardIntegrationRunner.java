package cn.edu.bjtu.foodtime.presentation;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.*;
import android.widget.TextView;
import cn.edu.bjtu.foodtime.BuildConfig;
import cn.edu.bjtu.foodtime.domain.model.Models.AdminDashboard;
import cn.edu.bjtu.foodtime.domain.repository.*;
import java.io.FileOutputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Opt-in local Backend + real native dashboard; refuses all cloud URLs and shared packages. */
public final class DashboardIntegrationRunner extends Instrumentation {
  private Bundle args;private int checks;
  @Override public void onCreate(Bundle arguments){super.onCreate(arguments);args=arguments;start();}
  private void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
  private String text(View view){StringBuilder s=new StringBuilder();if(view instanceof TextView t)s.append(t.getText()).append('\n');if(view instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++)s.append(text(g.getChildAt(i)));return s.toString();}
  private View find(View view,String label){if(view instanceof TextView t&&label.contentEquals(t.getText()))return view;if(view instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){View result=find(g.getChildAt(i),label);if(result!=null)return result;}return null;}
  private void ui(Runnable action){AtomicReference<Throwable> error=new AtomicReference<>();runOnMainSync(()->{try{action.run();}catch(Throwable failed){error.set(failed);}});if(error.get()!=null)throw new AssertionError(error.get());}
  private void ready(MainActivity a){long end=SystemClock.uptimeMillis()+20000;while(SystemClock.uptimeMillis()<end){AtomicReference<Boolean> done=new AtomicReference<>(false);ui(()->done.set(!a.isPageLoading()&&a.content.findViewWithTag("dashboard-refresh")!=null));if(done.get())return;SystemClock.sleep(100);}throw new AssertionError("Dashboard did not finish loading");}
  @Override public void onStart(){Bundle result=new Bundle();MainActivity activity=null;
    try {
      check(BuildConfig.APPLICATION_ID.endsWith(".dashboardcheck"),"Use isolated .dashboardcheck package");
      check(BuildConfig.API_BASE_URL.equals("http://127.0.0.1:18080/api/v1/"),"Only dedicated loopback Backend permitted");
      String password=args.getString("testPassword"),account=args.getString("superadminAccount");
      check(password!=null&&account!=null,"Missing local test credentials");
      activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      MainActivity a=activity;
      // Launch first so Application.onCreate and the initial session restoration finish.
      long restoredBy=SystemClock.uptimeMillis()+20000;
      while(SystemClock.uptimeMillis()<restoredBy){AtomicReference<Boolean> done=new AtomicReference<>(false);ui(()->done.set(find(a.root,"正在连接…")==null));if(done.get())break;SystemClock.sleep(100);}
      FoodtimeRepository repository=a.repo;
      repository.login(account,password);
      ui(()->{a.tab="dashboard";a.stacks.get("dashboard").clear();a.stacks.get("dashboard").add(new MainActivity.Route("admin-dashboard",""));a.render();});
      ready(a);AdminDashboard snapshot=repository.adminDashboard();
      check(snapshot!=null,"No real Backend snapshot");check(snapshot.trend().size()==7,"Expected 7 business days");
      check(snapshot.registeredUsers()==Long.parseLong(args.getString("expectedUsers")),"User count differs from Backend");
      check(snapshot.halls()==Long.parseLong(args.getString("expectedHalls")),"Hall count differs from Backend");
      check(snapshot.onlineUsers()>=1,"Authenticated presence missing");
      ui(()->{
        String page=text(a.content);check(page.contains("北京时间")&&page.contains("有效用户"),"Snapshot metadata missing");
        check(page.contains(String.format(Locale.CHINA,"%,d",snapshot.registeredUsers())),"Rendered counter differs from snapshot");
        View daily=find(a.content,"日活用户");check(daily!=null,"Daily activity tab absent");daily.performClick();
        check(repository.adminDashboard()==snapshot,"Tab switch fetched another snapshot");
      });
      waitForIdleSync();SystemClock.sleep(350);
      try(var output=new FileOutputStream(new java.io.File(getTargetContext().getFilesDir(),"dashboard-top.png"))){getUiAutomation().takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,output);}
      ui(()->a.scroll.fullScroll(View.FOCUS_DOWN));SystemClock.sleep(300);
      try(var output=new FileOutputStream(new java.io.File(getTargetContext().getFilesDir(),"dashboard-trend.png"))){getUiAutomation().takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,output);}
      SystemClock.sleep(1100);ui(()->{a.scroll.scrollTo(0,0);a.content.findViewWithTag("dashboard-refresh").performClick();});ready(a);
      check(repository.adminDashboard().generatedAt()>snapshot.generatedAt(),"Refresh did not reach Backend");
      for(String role:new String[]{"admin","user"}){
        repository.logout();repository.login(args.getString(role+"Account"),password);
        check(repository.adminDashboard()==null,"Previous superadmin statistics leaked");
        try{repository.refresh(new ContentRequest(ContentRequest.Query.ADMIN_DASHBOARD,""));throw new AssertionError("Non-superadmin read dashboard");}
        catch(RepositoryException denied){check(denied.status()==403,"Wrong denial status");}
      }
      repository.logout();check(repository.adminDashboard()==null,"Logout retained statistics");
      result.putString("stream","Dashboard local Backend/native UI PASS: "+checks+" checks\n");finish(-1,result);
    }catch(Throwable failed){result.putString("stream","Dashboard FAIL: "+failed+"\n");finish(1,result);}
    finally{if(activity!=null){MainActivity a=activity;ui(a::finish);}}
  }
}
