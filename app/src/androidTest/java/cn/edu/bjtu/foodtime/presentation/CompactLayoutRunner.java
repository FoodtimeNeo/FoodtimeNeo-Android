package cn.edu.bjtu.foodtime.presentation;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.*;
import android.view.*;
import android.widget.*;
import cn.edu.bjtu.foodtime.BuildConfig;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Isolated loopback build only; exercises production widgets and the real HTTP repository. */
public final class CompactLayoutRunner extends Instrumentation {
  private MainActivity activity;private int checks;
  private static String id(int n){return String.format(java.util.Locale.ROOT,"10000000-0000-4000-8000-%012d",n);}
  private void ui(Runnable action){AtomicReference<Throwable> failure=new AtomicReference<>();runOnMainSync(()->{try{action.run();}catch(Throwable e){failure.set(e);}});if(failure.get()!=null)throw new AssertionError(failure.get());}
  private void await(String label,BooleanSupplier condition){long end=SystemClock.uptimeMillis()+20000;while(SystemClock.uptimeMillis()<end){boolean[] ready={false};ui(()->ready[0]=condition.getAsBoolean());if(ready[0])return;SystemClock.sleep(70);}throw new AssertionError("Timeout: "+label);}
  private void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
  private View tag(String value){return activity.root.findViewWithTag(value);}
  private void click(String tag){ui(()->{View view=tag(tag);if(view==null)throw new AssertionError("Missing "+tag);view.performClick();});}
  private void screenshot(String name)throws Exception{waitForIdleSync();Bitmap image=getUiAutomation().takeScreenshot();check(image!=null,"Screenshot available");try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),name+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();}
  private void profile()throws Exception{
    ui(()->activity.selectTab("profile"));await("profile",()->tag("profile-entry:favorite-dishes")!=null);
    ui(()->{
      View first=tag("profile-entry:favorite-dishes"),second=tag("profile-entry:community-favorites");Rect a=new Rect(),b=new Rect();first.getGlobalVisibleRect(a);second.getGlobalVisibleRect(b);
      check(first.getWidth()>0&&!Rect.intersects(a,b),"Profile entries do not overlap");
      check(tag("profile-entry:history")!=null&&tag("profile-entry:community-create")!=null,"All four common shortcuts remain");
      check(tag("profile-following-count")!=null&&tag("profile-follower-count")!=null,"Following/follower routes retained");
    });
    await("profile totals",()->tag("profile-count:venue-mine") instanceof TextView text&&text.getText().toString().equals("43"));
    ui(()->{
      check(((TextView)tag("profile-count:favorite-dishes")).getText().toString().equals("31"),"Dish total counts unique favorites, not summed groups");
      check(((TextView)tag("profile-count:community-favorites")).getText().toString().equals("52"),"Post favorites use server total beyond first page");
      check(((TextView)tag("profile-count:community-mine")).getText().toString().equals("65"),"Own posts use server total beyond first page");
      check(((TextView)tag("profile-following-count")).getText().toString().equals("关注 12"),"Following refresh retains inline layout");
    });
    screenshot("profile-android");click("profile-entry:favorite-dishes");await("favorite destination",()->activity.route().kind().equals("favorite-dishes"));check(true,"Dish favorites entry navigates");
  }
  private void search()throws Exception{
    String owner=activity.repo.currentUser().id();SearchHistoryStore.purge(getTargetContext(),owner);
    for(int i=0;i<12;i++)SearchHistoryStore.remember(getTargetContext(),owner,"测试"+i);
    check(SearchHistoryStore.read(getTargetContext(),owner).size()==10,"History limit is ten");
    check(SearchHistoryStore.read(getTargetContext(),"another-owner").isEmpty(),"History is isolated by account");
    SearchHistoryStore.remember(getTargetContext(),owner,"测试5");check(SearchHistoryStore.read(getTargetContext(),owner).get(0).equals("测试5"),"Duplicate moves to front");
    SearchHistoryStore.remove(getTargetContext(),owner,"测试5");check(!SearchHistoryStore.read(getTargetContext(),owner).contains("测试5"),"Single deletion works");
    ui(()->activity.go("dish-search",""));await("search",()->tag("dish-search-keyword")!=null&&tag("dish-search-keyword").isEnabled());
    ui(()->{EditText input=(EditText)tag("dish-search-keyword");input.setText("牛肉面");input.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);});
    await("search complete",()->tag("dish-search-keyword").isEnabled()&&SearchHistoryStore.read(getTargetContext(),owner).get(0).equals("牛肉面"));
    check(true,"Keyboard search commits history");screenshot("search-android");
    SearchHistoryStore.purge(getTargetContext(),owner);check(SearchHistoryStore.read(getTargetContext(),owner).isEmpty(),"Account purge removes keywords");
  }
  private void menu()throws Exception{
    ui(()->{activity.selectTab("dining");activity.go("stalls",id(10));});await("menu",()->tag("menu-dish:"+id(1070))!=null);
    ui(()->{
      check(tag("canteen-menu-rail")!=null&&tag("canteen-menu-dishes")!=null,"Independent menu panes exist");
      check(tag("menu-section:"+id(103))!=null,"Empty stall still has a selectable section");
      for(int i=0;i<8;i++)check(tag("menu-stall:"+id(100+i))!=null,"All stall buttons exist");
    });screenshot("menu-android");
    click("menu-stall:"+id(106));await("selected stall",()->tag("menu-stall:"+id(106)).isSelected());
    final int[] position={0};ui(()->position[0]=((ScrollView)tag("canteen-menu-dishes")).getScrollY());check(position[0]>0,"Stall click scrolls dishes");
    ui(()->((ScrollView)tag("canteen-menu-dishes")).fullScroll(View.FOCUS_UP));await("scroll sync",()->tag("menu-stall:"+id(100)).isSelected());check(true,"Manual dishes scroll updates stall rail");
    Rect touchArea=new Rect();ui(()->tag("canteen-menu-dishes").getGlobalVisibleRect(touchArea));
    long down=SystemClock.uptimeMillis();float x=touchArea.centerX(),from=touchArea.bottom-100,to=touchArea.top+100;
    sendPointerSync(MotionEvent.obtain(down,down,MotionEvent.ACTION_DOWN,x,from,0));
    for(int step=1;step<=16;step++){SystemClock.sleep(15);sendPointerSync(MotionEvent.obtain(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_MOVE,x,from+(to-from)*step/16,0));}
    sendPointerSync(MotionEvent.obtain(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,x,to,0));
    await("real touch scroll",()->((ScrollView)tag("canteen-menu-dishes")).getScrollY()>0);check(true,"Finger swipe scrolls right pane without parent stealing gesture");
    click("menu-stall:"+id(105));await("middle selected",()->tag("menu-stall:"+id(105)).isSelected());
    click("menu-dish:"+id(1050));await("dish detail",()->activity.route().kind().equals("detail")&&!activity.isPageLoading());check(true,"Menu dish opens existing detail");
    ui(activity::back);await("menu restored",()->tag("menu-dish:"+id(1070))!=null&&((ScrollView)tag("canteen-menu-dishes")).getScrollY()>0);check(true,"Returning restores right-pane position");
  }
  @Override public void onCreate(Bundle args){super.onCreate(args);start();}
  @Override public void onStart(){Bundle result=new Bundle();try{
    if(!BuildConfig.DEBUG||!BuildConfig.APPLICATION_ID.endsWith(".layoutreview")||!BuildConfig.API_BASE_URL.equals("http://127.0.0.1:18530/api/v1/"))throw new AssertionError("Only isolated local build allowed");
    activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    activity.repo.restoreSession();ui(activity::restore);
    await("fixture session",()->activity.repo.currentUser()!=null&&!activity.isPageLoading());
    profile();search();menu();result.putString("stream","Compact layout PASS "+checks+" checks\n");finish(-1,result);
  }catch(Throwable error){result.putString("stream","Compact layout FAIL "+android.util.Log.getStackTraceString(error));finish(1,result);}finally{if(activity!=null)ui(activity::finish);}}
}
