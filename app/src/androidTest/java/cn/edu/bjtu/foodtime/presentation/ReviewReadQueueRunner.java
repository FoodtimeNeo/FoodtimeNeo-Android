package cn.edu.bjtu.foodtime.presentation;

import android.app.*;
import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import cn.edu.bjtu.foodtime.domain.model.Models.*;
import cn.edu.bjtu.foodtime.domain.repository.FoodtimeRepository;
import java.io.*;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/** Regression of restored floors, single-flight reads, lifecycle callbacks and write exclusion. */
public final class ReviewReadQueueRunner extends Instrumentation {
  private MainActivity activity;
  private int checks;
  private final Set<String> loaded=ConcurrentHashMap.newKeySet();
  private final Map<String,AtomicInteger> calls=new ConcurrentHashMap<>();
  private final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
  private void check(boolean value,String label) {if(!value) throw new AssertionError(label);checks++;}
  private void await(BooleanSupplier ready) throws Exception {
    long end=System.currentTimeMillis()+10000;
    while(System.currentTimeMillis()<end) {waitForIdleSync();if(ready.getAsBoolean()) return;Thread.sleep(30);}
    StringBuilder text=new StringBuilder();runOnMainSync(() -> dump(activity.root,text));
    throw new AssertionError("Timed out waiting for read regression: "+text);
  }
  private void dump(View view,StringBuilder text) {
    if(view.getVisibility()!=View.VISIBLE) return;
    if(view instanceof TextView t) text.append(t.getText()).append(" | ");
    if(view instanceof ViewGroup g) for(int i=0;i<g.getChildCount();i++) dump(g.getChildAt(i),text);
  }
  private int count(View view,String text) {
    if(view.getVisibility()!=View.VISIBLE) return 0;
    int n=view instanceof TextView t&&t.getText().toString().equals(text)?1:0;
    if(view instanceof ViewGroup group) for(int i=0;i<group.getChildCount();i++) n+=count(group.getChildAt(i),text);
    return n;
  }
  private FoodtimeRepository repository() {
    return (FoodtimeRepository)Proxy.newProxyInstance(FoodtimeRepository.class.getClassLoader(),new Class<?>[]{FoodtimeRepository.class},(p,m,args) -> switch(m.getName()) {
      case "currentUser" -> new User("user","20260001@bjtu.edu.cn","测试用户","user",true);
      case "restoreSession","refresh" -> null;
      case "halls" -> List.of(new Hall("hall","测试食堂","",true));
      case "dish" -> new Dish("dish","stall","多楼恢复测试",new BigDecimal("10"),"",true);
      case "stall" -> new Stall("stall","hall","测试档口","",true);
      case "hall" -> new Hall("hall","测试食堂","",true);
      case "photos" -> List.of();
      case "rating" -> new RatingSummary(new BigDecimal("5"),2);
      case "reviews" -> List.of(new Review("a","dish","u1","作者甲",5,"评论甲",0,0,1,false,0),new Review("b","dish","u2","作者乙",5,"评论乙",0,0,1,false,0));
      case "dishReviews" -> new ReviewPage(((FoodtimeRepository)p).reviews((String)args[0]),2,1,false);
      case "hasLoadedReviewReplies" -> loaded.contains((String)args[0]);
      case "reviewReplies" -> new ReplyPage(List.of(),loaded.contains((String)args[0])?1:0,false,"");
      case "loadReviewReplies" -> {
        String id=(String)args[0];calls.computeIfAbsent(id,k -> new AtomicInteger()).incrementAndGet();
        entered.countDown();if(!release.await(8,TimeUnit.SECONDS)) throw new IllegalStateException("测试等待超时");loaded.add(id);yield null;
      }
      default -> throw new AssertionError("Unexpected repository call "+m.getName());
    });
  }
  @Override public void onCreate(Bundle args) {super.onCreate(args);start();}
  @Override public void onStart() {
    Bundle report=new Bundle();Object app=getTargetContext().getApplicationContext();Field field=null;Object original=null;
    CountDownLatch callbacksRelease=new CountDownLatch(1),writeRelease=new CountDownLatch(1),staleRelease=new CountDownLatch(1);
    try {
      FoodtimeRepository repo=repository();field=app.getClass().getDeclaredField("repository");field.setAccessible(true);original=field.get(app);field.set(app,repo);
      activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      await(() -> activity.root!=null);
      runOnMainSync(() -> {activity.repo=repo;activity.drafts.putBoolean("embedded-replies:a:expanded",true);activity.drafts.putBoolean("embedded-replies:b:expanded",true);activity.go("detail","dish");});
      check(entered.await(8,TimeUnit.SECONDS),"First restored floor begins loading");waitForIdleSync();
      check(!activity.operationInFlight(),"Reading a floor does not take the write lock");
      runOnMainSync(activity::redraw);waitForIdleSync();release.countDown();
      await(() -> loaded.containsAll(List.of("a","b"))&&count(activity.root,"收起回复 · 1")==2);
      check(calls.get("a").get()==1&&calls.get("b").get()==1,"Both restored floors load once, including redraw subscribers");
      await(() -> count(activity.root,"正在加载回复…")==0);
      check(count(activity.root,"正在加载回复…")==0,"All current floor progress indicators finish");

      AtomicInteger background=new AtomicInteger(),callbacks=new AtomicInteger();
      TextView error=new TextView(activity);
      runOnMainSync(() -> {
        for(String key:List.of("deferred-a","deferred-b")) activity.read(key,null,error,() -> {
          try {callbacksRelease.await(8,TimeUnit.SECONDS);}catch(InterruptedException e) {Thread.currentThread().interrupt();throw new IllegalStateException(e);}
          background.incrementAndGet();
        },callbacks::incrementAndGet,() -> {});
        activity.onSaveInstanceState(new Bundle());
      });
      callbacksRelease.countDown();await(() -> background.get()==2);waitForIdleSync();
      check(callbacks.get()==0,"Read callbacks wait while Activity state is saved");
      runOnMainSync(activity::onResume);await(() -> callbacks.get()==2);
      check(callbacks.get()==2,"Resume delivers every queued callback");

      AtomicInteger recovered=new AtomicInteger();
      runOnMainSync(() -> {
        activity.read("failure",null,error,() -> {throw new IllegalStateException("测试读取失败");},() -> {},() -> {});
        activity.read("healthy",null,null,() -> {},recovered::incrementAndGet,() -> {});
      });
      await(() -> recovered.get()==1&&error.getText().toString().equals("测试读取失败"));
      check(recovered.get()==1,"One failed resource cannot drop another read");
      runOnMainSync(() -> activity.read("failure",null,error,() -> {},recovered::incrementAndGet,() -> {}));
      await(() -> recovered.get()==2);
      check(error.length()==0,"Failed reads can be retried and clear the old error");

      AtomicInteger writes=new AtomicInteger();CountDownLatch writeEntered=new CountDownLatch(1);
      runOnMainSync(() -> {
        activity.read("before-write",null,null,() -> {
          writeEntered.countDown();try {writeRelease.await(8,TimeUnit.SECONDS);}catch(InterruptedException e) {Thread.currentThread().interrupt();}
        },() -> {},() -> {});
        activity.request(null,null,writes::incrementAndGet,() -> {});
        activity.request(null,null,writes::incrementAndGet,() -> {});
      });
      check(writeEntered.await(8,TimeUnit.SECONDS),"Read and write share the serial worker");writeRelease.countDown();
      await(() -> writes.get()==1&&!activity.operationInFlight());
      check(writes.get()==1,"Read separation preserves duplicate write protection");

      AtomicInteger stale=new AtomicInteger();CountDownLatch staleEntered=new CountDownLatch(1);
      runOnMainSync(() -> activity.read("old-route",null,null,() -> {
        staleEntered.countDown();try {staleRelease.await(8,TimeUnit.SECONDS);}catch(InterruptedException e) {Thread.currentThread().interrupt();}
      },stale::incrementAndGet,() -> {}));
      check(staleEntered.await(8,TimeUnit.SECONDS),"Stale-route read started");
      runOnMainSync(activity::home);staleRelease.countDown();await(() -> count(activity.root,"测试食堂")>0);
      check(stale.get()==0,"Navigation works during reads and old callbacks do not repaint it");
      report.putString("stream","Review read queue PASS: "+checks+" native checks; isolated repository, no Backend writes\n");finish(0,report);
    } catch(Throwable failure) {
      StringWriter stack=new StringWriter();failure.printStackTrace(new PrintWriter(stack));report.putString("stream","Review read queue FAIL after "+checks+" checks\n"+stack);finish(1,report);
    } finally {
      release.countDown();callbacksRelease.countDown();writeRelease.countDown();staleRelease.countDown();
      if(activity!=null) runOnMainSync(activity::finish);
      if(field!=null) try {field.set(app,original);}catch(IllegalAccessException ignored) {}
    }
  }
}
