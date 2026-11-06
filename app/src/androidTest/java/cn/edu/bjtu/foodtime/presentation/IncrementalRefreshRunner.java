package cn.edu.bjtu.foodtime.presentation;

import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import cn.edu.bjtu.foodtime.data.local.LocalRepository;
import cn.edu.bjtu.foodtime.domain.model.Models.*;
import cn.edu.bjtu.foodtime.domain.repository.FoodtimeRepository;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/** Native View identity and input checks using an in-process repository, never business writes. */
public final class IncrementalRefreshRunner extends Instrumentation {
  private MainActivity activity;
  private int checks;

  @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
  private void main(Runnable work) { runOnMainSync(work); waitForIdleSync(); }
  private void check(boolean condition,String message) {
    if(!condition)throw new AssertionError(message);
    checks++;
  }
  private void await(BooleanSupplier condition) {
    long deadline=android.os.SystemClock.elapsedRealtime()+6000;
    while(!condition.getAsBoolean()&&android.os.SystemClock.elapsedRealtime()<deadline)
      android.os.SystemClock.sleep(30);
    if(!condition.getAsBoolean())throw new AssertionError("Timed out waiting for local refresh");
    waitForIdleSync();
  }
  private View find(View view,java.util.function.Predicate<View> predicate) {
    if(predicate.test(view))return view;
    if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++) {
      View found=find(group.getChildAt(i),predicate);if(found!=null)return found;
    }
    return null;
  }
  private EditText composer() {
    return (EditText)find(activity.root,view->view instanceof EditText edit
        &&edit.getHint()!=null&&edit.getHint().toString().contains("私信内容"));
  }
  private View text(String value) {
    return find(activity.root,view->view instanceof TextView label&&label.getText().toString().equals(value));
  }

  @Override public void onStart() {
    Bundle report=new Bundle();String namespace="incremental-refresh-"+UUID.randomUUID();
    try {
      if(!cn.edu.bjtu.foodtime.BuildConfig.DEBUG
          ||!cn.edu.bjtu.foodtime.BuildConfig.APPLICATION_ID.endsWith(".integration"))
        throw new IllegalStateException("Only the isolated integration package may run this fixture");
      Context context=new ContextWrapper(getTargetContext()) {
        @Override public SharedPreferences getSharedPreferences(String name,int mode) {
          return getBaseContext().getSharedPreferences(namespace,mode);
        }
      };
      LocalRepository local=new LocalRepository(context);
      local.login("20260001@bjtu.edu.cn","Foodtime2026!");
      AtomicInteger reads=new AtomicInteger(),count=new AtomicInteger(1);
      FoodtimeRepository repository=(FoodtimeRepository)Proxy.newProxyInstance(
          FoodtimeRepository.class.getClassLoader(),new Class<?>[]{FoodtimeRepository.class},(proxy,method,args)->{
            switch(method.getName()) {
              case "refresh": reads.incrementAndGet();Thread.sleep(250);count.set(2);return null;
              case "conversation": return new Conversation("c1",new ChatUser("other","对方",""),"最新消息","other",2,0,2);
              case "conversationMessages": return new DirectMessagePage(
                  count.get()==1?List.of(new DirectMessage("m1","c1","other","第一条消息",1)):
                  count.get()==2?List.of(new DirectMessage("m1","c1","other","第一条消息",1),new DirectMessage("m2","c1","other","第二条新消息",2)):
                  List.of(new DirectMessage("m1","c1","other","第一条消息",1),new DirectMessage("m2","c1","other","第二条新消息",2),new DirectMessage("m3","c1",local.currentUser().id(),"保留草稿",3)),false,"");
              case "sendDirectMessage": count.set(3);return new DirectMessage("m3","c1",local.currentUser().id(),"保留草稿",3);
              case "noticeFeed": return new NoticeFeed(List.of(),0);
              default:
                try { return method.invoke(local,args); }
                catch(InvocationTargetException error) { throw error.getCause(); }
            }
          });
      activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      main(()->{activity.repo=repository;activity.openApp();});
      await(()->!activity.isPageLoading());
      int before=reads.get();
      main(()->activity.go("profile-edit",""));
      check(reads.get()==before,"A static profile form must not request page data");
      count.set(1);
      main(()->activity.go("direct-chat","c1"));
      EditText input=composer();check(input!=null,"The composer appears before cloud refresh completes");
      main(()->{input.setText("保留草稿");input.requestFocus();input.setSelection(2);});
      await(()->!activity.isPageLoading());
      main(()->{
        check(composer()==input,"Navigation refresh preserves the input View");
        check(input.getText().toString().equals("保留草稿"),"Navigation refresh preserves its draft");
        check(input.hasFocus()&&input.getSelectionStart()==2,"Background refresh preserves focus and cursor position");
        check(text("第二条新消息")!=null,"Only the message region receives updated data");
      });
      View refresh=find(activity.root,view->"刷新私信".contentEquals(view.getContentDescription()==null?"":view.getContentDescription()));
      main(refresh::performClick);await(refresh::isEnabled);
      main(()->check(composer()==input&&input.getText().toString().equals("保留草稿"),"Explicit refresh preserves composer and draft"));
      main(()->text("发送").performClick());
      await(()->input.getText().length()==0);
      main(()->{
        check(composer()==input,"Sending does not recreate the editor");
        check(text("保留草稿")!=null,"The sent message appears in the message region");
      });
      report.putString("stream","Incremental refresh PASS: "+checks+" checks\n");finish(-1,report);
    } catch(Throwable error) {
      java.io.StringWriter stack=new java.io.StringWriter();error.printStackTrace(new java.io.PrintWriter(stack));
      report.putString("stream","Incremental refresh FAIL after "+checks+" checks\n"+stack);finish(1,report);
    } finally {
      if(activity!=null)main(()->activity.finish());
      getTargetContext().deleteSharedPreferences(namespace);
    }
  }
}
