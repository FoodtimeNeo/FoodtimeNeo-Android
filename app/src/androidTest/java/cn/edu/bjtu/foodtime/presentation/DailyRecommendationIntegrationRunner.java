package cn.edu.bjtu.foodtime.presentation;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.*;
import android.view.*;
import android.widget.TextView;
import cn.edu.bjtu.foodtime.BuildConfig;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Opt-in native acceptance. Credentials are supplied at runtime; only loopback is permitted. */
public final class DailyRecommendationIntegrationRunner extends Instrumentation {
  private Bundle args;private int checks;
  @Override public void onCreate(Bundle arguments){super.onCreate(arguments);args=arguments;start();}
  private void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
  private void ui(Runnable work){AtomicReference<Throwable> failure=new AtomicReference<>();runOnMainSync(()->{try{work.run();}catch(Throwable error){failure.set(error);}});if(failure.get()!=null)throw new AssertionError(failure.get());}
  private String text(View v){StringBuilder s=new StringBuilder();if(v instanceof TextView t)s.append(t.getText()).append('\n');if(v instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++)s.append(text(g.getChildAt(i)));return s.toString();}
  private void until(BooleanSupplier predicate,String message){long end=SystemClock.uptimeMillis()+25000;while(SystemClock.uptimeMillis()<end){AtomicReference<Boolean> done=new AtomicReference<>(false);ui(()->done.set(predicate.getAsBoolean()));if(done.get())return;SystemClock.sleep(100);}throw new AssertionError(message);}
  private View section(MainActivity a){return a.content.findViewWithTag("daily-recommendations");}
  private void layout(View v) {
    if(v instanceof TextView t&&t.getVisibility()==View.VISIBLE&&t.getLayout()!=null)
      for(int i=0;i<t.getLayout().getLineCount();i++)check(t.getLayout().getEllipsisCount(i)==0,"Truncated: "+t.getText());
    if(v instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++)layout(g.getChildAt(i));
  }
  private void screenshot(MainActivity a,String mode)throws Exception {
    ui(()->{View s=section(a);var config=a.getResources().getConfiguration();var data=a.repo.dailyRecommendations();if(config.screenWidthDp>config.screenHeightDp&&data!=null&&!data.items().isEmpty()){ViewGroup card=a.content.findViewWithTag("daily-recommendation-card:"+data.items().get(0).dishId());s=card.getChildAt(1);}int[] point=new int[2],origin=new int[2];s.getLocationInWindow(point);a.scroll.getLocationInWindow(origin);a.scroll.scrollBy(0,point[1]-origin[1]-Ui.dp(a,12));});
    waitForIdleSync();SystemClock.sleep(3500);
    var window=getUiAutomation().getRootInActiveWindow();long end=SystemClock.uptimeMillis()+5000;
    while((window==null||!BuildConfig.APPLICATION_ID.contentEquals(window.getPackageName()))&&SystemClock.uptimeMillis()<end){SystemClock.sleep(100);window=getUiAutomation().getRootInActiveWindow();}
    check(window!=null&&BuildConfig.APPLICATION_ID.contentEquals(window.getPackageName()),"System window obscures screenshot");
    try(var out=new FileOutputStream(new java.io.File(getTargetContext().getFilesDir(),"recommendations-"+mode+args.getString("evidenceSuffix","")+".png"))){getUiAutomation().takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}
  }
  @Override public void onStart(){Bundle result=new Bundle();MainActivity activity=null;
    try {
      check(BuildConfig.APPLICATION_ID.endsWith(".dailycheck"),"Use isolated .dailycheck package");
      check(BuildConfig.API_BASE_URL.equals("http://127.0.0.1:18080/api/v1/"),"Only loopback is allowed");
      String account=args.getString("testAccount"),password=args.getString("testPassword"),mode=args.getString("mode","normal");
      check(account!=null&&password!=null,"Missing local credentials");
      activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));MainActivity a=activity;
      until(()->!text(a.root).contains("正在连接…"),"Session restoration timed out");
      a.repo.login(account,password);ui(()->{a.tab="dining";a.stacks.get("dining").clear();a.stacks.get("dining").add(new MainActivity.Route("dining",""));a.render();});
      until(()->section(a)!=null,"No native recommendation section");
      if(mode.equals("retry")) {
        until(()->section(a)!=null&&text(section(a)).contains("推荐服务暂不可用"),"Missing recommendation error");
        ui(()->{check(!a.repo.halls().isEmpty()&&text(a.content).contains(a.repo.halls().get(0).name()),"Recommendation failure blocked the catalog");View retry=a.content.findViewWithTag("daily-recommendation-retry");check(retry.getHeight()>=Ui.dp(a,48),"Retry touch target too small");retry.performClick();});
      }
      if(mode.equals("empty")) {
        until(()->section(a)!=null&&text(section(a)).contains("今日暂无推荐"),"Missing empty state");
        check(a.repo.dailyRecommendations()!=null&&a.repo.dailyRecommendations().items().isEmpty(),"Empty state is not from Backend");
        screenshot(a,mode);
      } else {
        until(()->a.repo.dailyRecommendations()!=null&&!a.repo.dailyRecommendations().items().isEmpty()&&section(a)!=null&&text(section(a)).contains(a.repo.dailyRecommendations().items().get(0).dishName()),"Real recommendation cards missing");
        var data=a.repo.dailyRecommendations();check(data.items().size()==10,"Expected ten real recommendations");
        var first=data.items().get(0);
        ui(()->{
          String content=text(section(a));for(var dish:data.items()) {check(content.contains(dish.dishName()),"Dish missing");check(content.contains(dish.reason()),"Reason missing");check(a.content.findViewWithTag("daily-recommendation-card:"+dish.dishId())!=null,"Missing real dish target");}
          check(!content.contains("¥"),"Invented recommendation price");
          if(data.items().stream().anyMatch(d->d.averageRating()==null))check(content.contains("暂无评分"),"Null rating must remain unrated");
          layout(section(a));
        });
        screenshot(a,mode);
        ui(()->a.content.findViewWithTag("daily-recommendation-card:"+first.dishId()).performClick());
        until(()->!a.isPageLoading()&&text(a.content).contains(first.dishName()),"Real dish detail failed");
        ui(()->check(a.route().kind().equals("detail")&&a.route().id().equals(first.dishId()),"Wrong detail route"));
      }
      a.repo.logout();check(a.repo.dailyRecommendations()==null,"Logout retained recommendations");
      result.putString("stream","Daily recommendation native PASS: "+checks+" checks, mode="+mode+"\n");finish(-1,result);
    }catch(Throwable failure){result.putString("stream","Daily recommendation native FAIL: "+failure+"\n");finish(1,result);}
    finally{if(activity!=null){MainActivity a=activity;ui(a::finish);}}
  }
}
