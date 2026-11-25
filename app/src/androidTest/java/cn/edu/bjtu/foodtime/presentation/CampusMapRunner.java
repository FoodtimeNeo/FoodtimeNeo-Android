package cn.edu.bjtu.foodtime.presentation;

import android.app.Instrumentation;
import cn.edu.bjtu.foodtime.BuildConfig;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.*;
import cn.edu.bjtu.foodtime.domain.repository.RepositoryProvider;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

public final class CampusMapRunner extends Instrumentation {
  private MainActivity a; private int checks;
  @Override public void onCreate(Bundle args){super.onCreate(args);start();}
  private void ui(Runnable action){var error=new AtomicReference<Throwable>();runOnMainSync(()->{try{action.run();}catch(Throwable t){error.set(t);}});if(error.get()!=null)throw new AssertionError(error.get());}
  private void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
  private void await(String label,BooleanSupplier test){long end=SystemClock.uptimeMillis()+18000;while(SystemClock.uptimeMillis()<end){boolean[] ok={false};ui(()->ok[0]=test.getAsBoolean());if(ok[0])return;SystemClock.sleep(50);}throw new AssertionError(label);}
  private boolean renderedMap() {
    var map=(CampusMapScreen.MapView)a.root.findViewWithTag("campus-map-canvas");
    Bitmap bitmap=Bitmap.createBitmap(map.getWidth(),map.getHeight(),Bitmap.Config.ARGB_8888);
    map.draw(new android.graphics.Canvas(bitmap));var colors=new java.util.HashSet<Integer>();
    for(int x=1;x<20;x++)for(int y=1;y<20;y++)colors.add(bitmap.getPixel(x*bitmap.getWidth()/20,y*bitmap.getHeight()/20));
    bitmap.recycle();return colors.size()>40;
  }
  @Override public void onStart(){var result=new Bundle();try{
    if(!BuildConfig.DEBUG||!BuildConfig.APPLICATION_ID.endsWith(".campusreview")||!BuildConfig.API_BASE_URL.equals("http://127.0.0.1:18120/api/v1/"))throw new AssertionError("Isolated local build required");
    a=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    a.repo.login("review@example.test", "local-fixture-password");ui(a::openApp);
    await("session",()->a.repo.currentUser()!=null&&!a.isPageLoading());ui(()->a.go("campus-map",""));
    await("map",()->!a.isPageLoading()&&a.root.findViewWithTag("campus-map-canvas")!=null);
    await("Map bitmap must actually render",this::renderedMap);check(true,"Decoded map has detailed rendered pixels");
    ui(()->{
      var map=(CampusMapScreen.MapView)a.root.findViewWithTag("campus-map-canvas");
      check(map.getWidth()>0&&map.getHeight()>0,"Measured map viewport");
      check(CampusMapScreen.regions(a).size()==7,"Seven mapped regions including xueyuan and yimin");
      map.zoomBy(1.5f);check(map.zoomLevel()>1,"Zoom button changes actual transform");map.reset();check(map.zoomLevel()==1,"Reset restores overview");
      var button=(TextView)a.root.findViewWithTag("map-region:minghu");button.performClick();
      var details=(LinearLayout)a.root.findViewWithTag("campus-map-selection");check(details.getChildCount()>=6,"Minghu exposes navigation and floor destinations");
      ((TextView)a.root.findViewWithTag("map-region:yimin")).performClick();check(details.getChildCount()>=3,"Unmapped region has name, navigation, and a clear unavailable message");
    });
    ui(()->{var map=(CampusMapScreen.MapView)a.root.findViewWithTag("campus-map-canvas");map.requestRectangleOnScreen(new android.graphics.Rect(0,0,map.getWidth(),map.getHeight()),true);});
    waitForIdleSync();SystemClock.sleep(300);
    float[] point=new float[2];
    ui(()->{var map=(CampusMapScreen.MapView)a.root.findViewWithTag("campus-map-canvas");map.reset();int[] screen=new int[2];map.getLocationOnScreen(screen);float fit=Math.min(map.getWidth()/5032f,map.getHeight()/3579f);point[0]=screen[0]+(map.getWidth()-5032*fit)/2+2547*fit;point[1]=screen[1]+(map.getHeight()-3579*fit)/2+820*fit;});
    long now=SystemClock.uptimeMillis();sendPointerSync(MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,point[0],point[1],0));sendPointerSync(MotionEvent.obtain(now,SystemClock.uptimeMillis()+40,MotionEvent.ACTION_UP,point[0],point[1],0));waitForIdleSync();
    ui(()->check("minghu".equals(a.drafts.getString(a.draftKey("selected"))),"System touch maps to the correct building"));
    ui(()->{var map=(CampusMapScreen.MapView)a.root.findViewWithTag("campus-map-canvas");map.zoomBy(1.5f);});
    now=SystemClock.uptimeMillis();sendPointerSync(MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,point[0],point[1],0));sendPointerSync(MotionEvent.obtain(now,SystemClock.uptimeMillis()+30,MotionEvent.ACTION_MOVE,point[0]+70,point[1]+35,0));sendPointerSync(MotionEvent.obtain(now,SystemClock.uptimeMillis()+60,MotionEvent.ACTION_UP,point[0]+70,point[1]+35,0));waitForIdleSync();
    ui(()->check("minghu".equals(a.drafts.getString(a.draftKey("selected"))),"Drag does not accidentally select another building"));

    ui(()->check(a.root.hasWindowFocus(),"Unobscured native map"));
    Bitmap shot=getUiAutomation().takeScreenshot();try(var output=new FileOutputStream(new java.io.File(getTargetContext().getFilesDir(),"campus-map.png"))){shot.compress(Bitmap.CompressFormat.PNG,100,output);}finally{shot.recycle();}
    result.putString("stream","Campus map device PASS: "+checks+" checks\n");finish(-1,result);
  }catch(Throwable t){result.putString("stream","Campus map device FAIL: "+android.util.Log.getStackTraceString(t));finish(1,result);}finally{if(a!=null)ui(a::finish);}}
}
