package cn.edu.bjtu.foodtime.presentation;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import cn.edu.bjtu.foodtime.BuildConfig;
import java.io.*;
import java.net.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Checks complete image geometry in the real dish page at five aspect ratios and a missing image. */
public final class DishImageRunner extends Instrumentation {
  private MainActivity a;private int checks;
  @Override public void onCreate(Bundle args){super.onCreate(args);start();}
  private void ui(Runnable action){var error=new AtomicReference<Throwable>();runOnMainSync(()->{try{action.run();}catch(Throwable t){error.set(t);}});if(error.get()!=null)throw new AssertionError(error.get());}
  private void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
  private void await(String label,BooleanSupplier probe){long end=SystemClock.uptimeMillis()+18000;while(SystemClock.uptimeMillis()<end){boolean[] ready={false};ui(()->ready[0]=probe.getAsBoolean());if(ready[0])return;SystemClock.sleep(50);}throw new AssertionError("Timeout: "+label);}
  private FrameLayout photo(){return a.root.findViewWithTag("dish-detail-photo");}
  private void cover(int index)throws Exception{var c=(HttpURLConnection)new URL("http://127.0.0.1:18100/fixture/cover?index="+index).openConnection();try{c.getInputStream().close();}finally{c.disconnect();}ui(()->a.refreshCurrent());}
  private void shot(String name)throws Exception{ui(()->check(a.root.hasWindowFocus(),"Screenshot has app focus"));Bitmap b=getUiAutomation().takeScreenshot();try(var output=new FileOutputStream(new File(getTargetContext().getFilesDir(),name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,output);}finally{b.recycle();}}
  @Override public void onStart(){var result=new Bundle();try{
    if(!BuildConfig.DEBUG||!BuildConfig.APPLICATION_ID.endsWith(".dishimagecheck")||!BuildConfig.API_BASE_URL.equals("http://127.0.0.1:18100/api/v1/"))throw new AssertionError("Isolated loopback build required");
    a=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));await("session",()->a.repo.currentUser()!=null&&!a.isPageLoading());ui(()->a.go("detail","5f9a04ee-a6a1-5c5d-982a-2ebaa1f9f558"));
    for(int index=0;index<5;index++){
      if(index>0)cover(index);
      await("decoded dish photo",()->!a.isPageLoading()&&photo()!=null&&((ImageView)photo().getChildAt(0)).getDrawable() instanceof BitmapDrawable);
      ui(()->{View p=photo();p.requestRectangleOnScreen(new Rect(0,0,p.getWidth(),p.getHeight()),true);});waitForIdleSync();SystemClock.sleep(100);
      ui(()->{FrameLayout frame=photo();ImageView image=(ImageView)frame.getChildAt(0);var drawable=image.getDrawable();RectF mapped=new RectF(0,0,drawable.getIntrinsicWidth(),drawable.getIntrinsicHeight());image.getImageMatrix().mapRect(mapped);
        check(a.root.hasWindowFocus(),"Dish page is unobscured");check(image.getScaleType()==ImageView.ScaleType.FIT_CENTER,"Full photo uses contain fit");
        check(mapped.left>=-.5f&&mapped.top>=-.5f&&mapped.right<=image.getWidth()+.5f&&mapped.bottom<=image.getHeight()+.5f,"All source corners lie inside image frame");
        check(Math.abs(mapped.width()/mapped.height()-(float)drawable.getIntrinsicWidth()/drawable.getIntrinsicHeight())<.01f,"Aspect ratio preserved");
        check(frame.getHeight()<=Ui.dp(a,421)&&frame.getHeight()>=Ui.dp(a,120),"Long photos have bounded height");
        check(frame.getChildCount()==2,"Upload and album controls do not overlay the photo");check(frame.isClickable(),"Photo still opens full viewer");});
      shot("android-dish-ratio-"+index);
    }
    cover(-1);await("empty dish placeholder",()->!a.isPageLoading()&&photo()==null);check(true,"Missing images preserve a usable placeholder");
    result.putString("stream","Dish image device PASS: "+checks+" assertions; five real HTTP image ratios, preserved corners, no overlay and bounded height\n");finish(-1,result);
  }catch(Throwable t){result.putString("stream","Dish image device FAIL: "+android.util.Log.getStackTraceString(t));finish(1,result);}finally{if(a!=null)ui(a::finish);}}
}
