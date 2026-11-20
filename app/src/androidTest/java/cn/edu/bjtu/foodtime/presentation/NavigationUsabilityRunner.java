package cn.edu.bjtu.foodtime.presentation;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.*;
import android.view.*;
import android.widget.*;
import cn.edu.bjtu.foodtime.BuildConfig;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

public final class NavigationUsabilityRunner extends Instrumentation {
  MainActivity a; int checks;
  @Override public void onCreate(Bundle args){super.onCreate(args);start();}
  void ui(Runnable run){var error=new AtomicReference<Throwable>();runOnMainSync(()->{try{run.run();}catch(Throwable t){error.set(t);}});if(error.get()!=null)throw new AssertionError(error.get());}
  void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;}
  void await(BooleanSupplier test){long end=SystemClock.uptimeMillis()+18000;while(SystemClock.uptimeMillis()<end){boolean[] ready={false};ui(()->ready[0]=test.getAsBoolean());if(ready[0])return;SystemClock.sleep(60);}throw new AssertionError("Page did not settle");}
  View description(View parent,String text){if(parent.getContentDescription()!=null&&text.contentEquals(parent.getContentDescription()))return parent;if(parent instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=description(group.getChildAt(i),text);if(found!=null)return found;}return null;}
  @Override public void onStart(){Bundle result=new Bundle();try{
    if(!BuildConfig.DEBUG||!BuildConfig.APPLICATION_ID.endsWith(".campusreview"))throw new AssertionError("Isolated test package required");
    a=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    a.repo.login("review@example.test", "local-fixture-password");ui(a::openApp);await(()->a.repo.currentUser()!=null&&!a.isPageLoading());
    ui(()->{View search=description(a.header,"搜索菜品");check(search instanceof ImageButton,"Catalog search uses community icon");check(description(a.header,"回到食堂首页") instanceof ImageButton,"Home is a proportional icon");a.selectTab("profile");});
    await(()->!a.isPageLoading()&&a.root.findViewWithTag("profile-entry:community-favorites")!=null);
    waitForIdleSync();android.graphics.Bitmap shot=getUiAutomation().takeScreenshot();try(var output=new java.io.FileOutputStream(new java.io.File(getTargetContext().getFilesDir(),"navigation.png"))){shot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output);}finally{shot.recycle();}
    ui(()->{for(String title:new String[]{"收藏与回访","我的记录","消息与处理","账号与隐私"})check(a.root.findViewWithTag("profile-group:"+title)!=null,"Visible profile section: "+title);
      check(a.root.findViewWithTag("profile-entry:favorite-dishes")!=null,"Dish favorites remain separate");
      ((TextView)a.root.findViewWithTag("profile-entry:community-favorites")).performClick();});
    await(()->a.route().kind().equals("community-favorites")&&!a.isPageLoading());check(true,"Post favorites opens directly");ui(a::back);await(()->a.route().kind().equals("profile")&&!a.isPageLoading());
    ui(()->{check(a.root.findViewWithTag("profile-entry:community-favorites")!=null,"Return preserves discoverable profile entries");((TextView)a.root.findViewWithTag("profile-entry:favorite-dishes")).performClick();});await(()->a.route().kind().equals("favorite-dishes")&&!a.isPageLoading());check(true,"Dish favorites has its own route");
    result.putString("stream","Navigation usability PASS: "+checks+" checks\n");finish(-1,result);
  }catch(Throwable t){result.putString("stream","Navigation usability FAIL: "+android.util.Log.getStackTraceString(t));finish(1,result);}finally{if(a!=null)ui(a::finish);}}
}
