package cn.edu.bjtu.foodtime.presentation;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import cn.edu.bjtu.foodtime.data.local.LocalRepository;
import cn.edu.bjtu.foodtime.domain.repository.*;
import cn.edu.bjtu.foodtime.domain.model.Models.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;

/** Native route/lifecycle tests with controlled read latency, independent from API contract tests. */
public final class LoadingExperienceRunner extends Instrumentation {
  private MainActivity activity;private int checks;
  private final AtomicLong delay=new AtomicLong();private final AtomicBoolean fail=new AtomicBoolean();private final AtomicInteger announcements=new AtomicInteger();
  private void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
  private void main(Runnable work){AtomicReference<Throwable> error=new AtomicReference<>();runOnMainSync(()->{try{work.run();}catch(Throwable e){error.set(e);}});if(error.get()!=null)throw new AssertionError(error.get());}
  private void await(BooleanSupplier condition,String label,long timeout)throws Exception {long end=System.currentTimeMillis()+timeout;while(System.currentTimeMillis()<end){AtomicBoolean ok=new AtomicBoolean();main(()->ok.set(condition.getAsBoolean()));if(ok.get())return;Thread.sleep(50);}throw new AssertionError("Timeout: "+label);}
  private View find(View root,String value,boolean description){if(root==null)return null;if(description?value.contentEquals(root.getContentDescription()==null?"":root.getContentDescription()):root instanceof TextView text&&value.contentEquals(text.getText()))return root;if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View result=find(group.getChildAt(i),value,description);if(result!=null)return result;}return null;}
  private View text(String value){return find(activity.root,value,false);}
  private boolean dialogVisible(){var root=getUiAutomation().getRootInActiveWindow();return root!=null&&!root.findAccessibilityNodeInfosByText("收到新公告").isEmpty();}
  private void dismissNotice()throws Exception {long end=System.currentTimeMillis()+3000;while(System.currentTimeMillis()<end){var root=getUiAutomation().getRootInActiveWindow();if(root!=null)for(var item:root.findAccessibilityNodeInfosByText("再想想"))if(item.isClickable()&&item.performAction(AccessibilityNodeInfo.ACTION_CLICK))return;Thread.sleep(50);}throw new AssertionError("Notice close missing");}
  private void ready()throws Exception {await(()->!activity.isPageLoading()&&text("加载中…")==null,"page ready",8000);}
  private void capture(String name)throws Exception {waitForIdleSync();var bitmap=getUiAutomation().takeScreenshot();File folder=new File(getTargetContext().getFilesDir(),"loading-evidence");folder.mkdirs();try(var out=new FileOutputStream(new File(folder,name+".png"))){if(bitmap==null||!bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out))throw new IOException("Screenshot failed");}finally{if(bitmap!=null)bitmap.recycle();}}
  private FoodtimeRepository controlled(LocalRepository local) {
    return (FoodtimeRepository)Proxy.newProxyInstance(FoodtimeRepository.class.getClassLoader(),new Class[]{FoodtimeRepository.class},(proxy,method,args)->{
      if(method.getName().equals("refresh")) {
        long wait=delay.get();if(wait>0)try{Thread.sleep(wait);}catch(InterruptedException cancelled){throw new RepositoryException(0,"CANCELLED","取消读取",0);}
        if(fail.get())throw new RepositoryException(0,"NETWORK_UNAVAILABLE","网络断开",0);
        return null;
      }
      if(method.getName().equals("noticeFeed")){List<Notice> list=new ArrayList<>();for(int i=0;i<announcements.get();i++)list.add(new Notice("n"+i,"加载期间的新公告 "+i,"正文",0));return new NoticeFeed(list,list.size());}
      try{return method.invoke(local,args);}catch(InvocationTargetException error){throw error.getCause();}
    });
  }
  @Override public void onCreate(Bundle args){super.onCreate(args);start();}
  @Override public void onStart(){Bundle report=new Bundle();String namespace="loading-test-"+UUID.randomUUID();try{
    if(!cn.edu.bjtu.foodtime.BuildConfig.DEBUG||!cn.edu.bjtu.foodtime.BuildConfig.APPLICATION_ID.endsWith(".integration"))throw new IllegalStateException("Only an isolated integration debug package may run this fixture");
    Context context=new ContextWrapper(getTargetContext()){@Override public SharedPreferences getSharedPreferences(String name,int mode){return getBaseContext().getSharedPreferences(namespace,mode);}};
    LocalRepository local=new LocalRepository(context);local.login("20260001@bjtu.edu.cn","Foodtime2026!");FoodtimeRepository repo=controlled(local);
    activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    main(()->{activity.repo=repo;activity.openApp();});ready();
    main(()->{FrameLayout image=(FrameLayout)Ui.image(activity,"asset:hall_1.webp","验收图片",false);check(!image.getChildAt(1).isClickable(),"Normal image placeholder does not swallow card clicks");});
    Hall hall=repo.halls().get(0),other=repo.halls().get(1);Stall stall=repo.stalls(hall.id()).get(0);
    delay.set(1600);main(()->activity.go("stalls",hall.id()));
    main(()->{check(text(hall.name())!=null,"Destination title appears immediately");check(find(activity.root,"返回上一级",true)!=null,"Back stays available");check(text("食堂")!=null&&text("我的")!=null,"Navigation stays available");check(text("加载中…")==null,"No isolated loading text on target route");});
    await(()->find(activity.root,"正在加载档口",true)!=null,"skeleton",1500);capture("stalls-skeleton");ready();
    main(()->check(text(repo.stalls(hall.id()).get(0).name())!=null,"Stall content replaces skeleton"));
    delay.set(1300);main(()->activity.go("dishes",stall.id()));await(()->find(activity.root,"正在加载菜品",true)!=null,"dish skeleton",1500);capture("dishes-skeleton");ready();
    String dish=repo.dishes(stall.id()).get(0).name();main(()->check(text(dish)!=null,"Dish content becomes interactive"));
    delay.set(1800);main(()->{activity.drafts.putString("community-detail:fixture:评论内容","保留草稿");activity.render();check(text(dish)!=null,"Same-target refresh retains cards");check(text("更新中")!=null,"Same-target refresh is lightweight");});ready();main(()->check(activity.drafts.getString("community-detail:fixture:评论内容").equals("保留草稿"),"Refresh preserves unrelated draft"));
    delay.set(4000);main(()->activity.go("stalls",other.id()));await(()->text("加载较慢，请稍候")!=null,"slow hint",5000);capture("slow-network");ready();
    fail.set(true);delay.set(250);main(()->activity.go("dishes",repo.stalls(other.id()).get(0).id()));await(()->text("加载失败")!=null,"network error",3000);capture("failure");main(()->check(find(activity.root,"返回上一级",true)!=null,"Error retains back"));
    fail.set(false);delay.set(0);main(()->text("重试").performClick());ready();main(()->check(text("加载失败")==null,"Retry recovers content"));
    delay.set(2500);main(()->activity.go("stalls",hall.id()));Thread.sleep(250);main(()->{activity.back();check(!activity.route().id().equals(hall.id()),"Back switches target during load");});delay.set(0);ready();Thread.sleep(300);main(()->check(!activity.route().id().equals(hall.id()),"Cancelled response cannot restore old route"));
    delay.set(20000);main(()->activity.go("stalls",hall.id()));await(()->text("加载失败")!=null,"15-second read deadline",17000);main(()->check(!activity.isPageLoading(),"Timeout ends busy state"));delay.set(0);main(()->text("重试").performClick());ready();
    delay.set(1800);main(()->activity.go("dishes",stall.id()));Thread.sleep(250);main(()->{callActivityOnPause(activity);check(!activity.isPageLoading(),"Background cancels pending navigation");callActivityOnResume(activity);});ready();main(()->check(activity.route().id().equals(stall.id()),"Resume restores selected target"));
    ActivityMonitor recreated=addMonitor(MainActivity.class.getName(),null,false);
    String retainedDraft="community-detail:fixture:评论内容";
    main(()->activity.recreate());
    Activity nextActivity=waitForMonitorWithTimeout(recreated,8000);removeMonitor(recreated);
    check(nextActivity instanceof MainActivity,"Rotation/recreation produces an attached Activity");activity=(MainActivity)nextActivity;
    delay.set(0);main(()->{activity.repo=repo;activity.openApp();});ready();
    main(()->{check(activity.route().id().equals(stall.id()),"Recreation restores route stack");check("保留草稿".equals(activity.drafts.getString(retainedDraft)),"Recreation preserves draft bundle");});
    main(()->{try{Field baseline=MainActivity.class.getDeclaredField("inboxBaseline");baseline.setAccessible(true);baseline.set(activity,true);}catch(Exception e){throw new IllegalStateException(e);}});
    delay.set(2200);main(()->activity.go("stalls",other.id()));announcements.set(1);
    main(()->{try{Field task=MainActivity.class.getDeclaredField("inboxPollTask");task.setAccessible(true);((Runnable)task.get(activity)).run();}catch(Exception e){throw new IllegalStateException(e);}});Thread.sleep(400);check(!dialogVisible(),"Announcement waits for target content");ready();
    long popupDeadline=System.currentTimeMillis()+4000;while(!dialogVisible()&&System.currentTimeMillis()<popupDeadline)Thread.sleep(100);check(dialogVisible(),"Queued announcement appears after load");dismissNotice();
    for(String[] actor:new String[][]{{"20260001@bjtu.edu.cn","user"},{"20260002@bjtu.edu.cn","admin"},{"20260003@bjtu.edu.cn","superadmin"}}){local.login(actor[0],"Foodtime2026!");delay.set(0);main(()->{activity.signedOut();activity.openApp();});ready();delay.set(350);main(()->activity.go("stalls",hall.id()));ready();main(()->check(activity.repo.currentUser().role().equals(actor[1])&&text(repo.stalls(hall.id()).get(0).name())!=null,"Catalog loads for "+actor[1]));}
    capture("loaded-catalog");report.putString("stream","Loading experience native PASS: "+checks+" checks\n");finish(-1,report);
  }catch(Throwable error){StringWriter stack=new StringWriter();error.printStackTrace(new PrintWriter(stack));report.putString("stream","Loading experience FAIL after "+checks+" checks\n"+stack);finish(1,report);}finally{if(activity!=null)main(()->activity.finish());getTargetContext().deleteSharedPreferences(namespace);}}
}
