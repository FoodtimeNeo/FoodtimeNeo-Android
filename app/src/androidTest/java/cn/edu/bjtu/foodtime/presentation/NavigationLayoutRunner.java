package cn.edu.bjtu.foodtime.presentation;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import cn.edu.bjtu.foodtime.BuildConfig;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Measured real navigation on both five-tab management and profile pages. */
public final class NavigationLayoutRunner extends Instrumentation {
  private MainActivity activity;private int checks;
  @Override public void onCreate(Bundle args){super.onCreate(args);start();}
  private void ui(Runnable action){
    AtomicReference<Throwable> error=new AtomicReference<>();runOnMainSync(()->{try{action.run();}catch(Throwable t){error.set(t);}});
    if(error.get()!=null)throw new AssertionError(error.get());
  }
  private void await(String label,BooleanSupplier probe){
    long end=SystemClock.uptimeMillis()+18000;
    while(SystemClock.uptimeMillis()<end){boolean[] value={false};ui(()->value[0]=probe.getAsBoolean());if(value[0])return;SystemClock.sleep(60);}
    throw new AssertionError("Timeout: "+label);
  }
  private void check(boolean result,String label){if(!result)throw new AssertionError(label);checks++;}
  private TextView tagged(String tag){return activity.root.findViewWithTag(tag);}
  private void measure(String selected){
    ui(()->{
      check(activity.root.hasWindowFocus(),"Navigation window is not obscured");
      for(String key:new String[]{"dining","community","admin","dashboard","profile"}){
        TextView item=tagged("main-nav:"+key);
        check(item!=null&&item.isShown()&&item.getHeight()>=Ui.dp(activity,44)&&item.getWidth()>=Ui.dp(activity,44),"Navigation touch area remains available: "+key);
        check(item.getLineCount()==1,"Two-character tab stays on one line: "+key+" lines="+item.getLineCount());
        check(item.isSelected()==key.equals(selected),"Selection follows current tab: "+key);
      }
      TextView brand=tagged("app-header-brand"),back=tagged("app-header-back");
      if(back!=null){int[] b=new int[2],t=new int[2];back.getLocationOnScreen(b);brand.getLocationOnScreen(t);
        check(t[0]-b[0]-back.getWidth()>=Ui.dp(activity,6),"Back and title retain a visible gap");}
    });
  }
  private void tap(String tag){
    int[] point=new int[2];ui(()->{View view=tagged(tag);check(view.hasWindowFocus(),"Touch target has focus");Rect rect=new Rect();if(!view.getGlobalVisibleRect(rect))throw new AssertionError("Target is hidden");point[0]=rect.centerX();point[1]=rect.centerY();});
    long start=SystemClock.uptimeMillis();
    for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}){MotionEvent event=MotionEvent.obtain(start,SystemClock.uptimeMillis(),action,point[0],point[1],0);event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);sendPointerSync(event);event.recycle();SystemClock.sleep(30);}
  }
  private void shot(String name)throws Exception{
    ui(()->check(activity.root.hasWindowFocus(),"Screenshot window has focus"));
    Bitmap bitmap=getUiAutomation().takeScreenshot();if(bitmap==null)throw new AssertionError("Screenshot missing");
    try(var out=new FileOutputStream(new File(getTargetContext().getFilesDir(),name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}
  }
  @Override public void onStart(){Bundle result=new Bundle();try{
    if(!BuildConfig.DEBUG||!BuildConfig.APPLICATION_ID.endsWith(".reportmanagementcheck")||!BuildConfig.API_BASE_URL.equals("http://127.0.0.1:18097/api/v1/"))throw new AssertionError("Isolated loopback build required");
    activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    await("session",()->activity.repo.currentUser()!=null&&!activity.isPageLoading());
    ui(()->{activity.selectTab("admin");activity.go("admin-reports","");});
    await("admin navigation",()->tagged("main-nav:admin")!=null&&tagged("app-header-back")!=null&&!activity.isPageLoading());waitForIdleSync();measure("admin");shot("android-navigation-admin");
    tap("main-nav:profile");await("profile selected",()->activity.route().kind().equals("profile")&&tagged("main-nav:profile")!=null&&tagged("main-nav:profile").isSelected());waitForIdleSync();measure("profile");shot("android-navigation-profile");
    tap("main-nav:admin");await("return to report",()->activity.route().kind().equals("admin-reports")&&tagged("main-nav:admin")!=null&&tagged("main-nav:admin").isSelected());measure("admin");
    result.putString("stream","Navigation layout device PASS: "+checks+" assertions; real tabs, system touch and measured layout\n");finish(-1,result);
  }catch(Throwable failure){result.putString("stream","Navigation layout device FAIL: "+android.util.Log.getStackTraceString(failure));finish(1,result);}finally{if(activity!=null)ui(activity::finish);}}
}
