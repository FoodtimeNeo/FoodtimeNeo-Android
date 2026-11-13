package cn.edu.bjtu.foodtime.presentation;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.TextView;
import cn.edu.bjtu.foodtime.BuildConfig;
import java.io.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Real search HTTP pages, window geometry and system touches, including failed jump recovery. */
public final class PaginationRunner extends Instrumentation {
  private MainActivity a;private int checks;
  @Override public void onCreate(Bundle args){super.onCreate(args);start();}
  private void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
  private void ui(Runnable task){var failure=new AtomicReference<Throwable>();runOnMainSync(()->{try{task.run();}catch(Throwable t){failure.set(t);}});if(failure.get()!=null)throw new AssertionError(failure.get());}
  private void await(String label,BooleanSupplier condition){long end=SystemClock.uptimeMillis()+18000;while(SystemClock.uptimeMillis()<end){boolean[] ready={false};ui(()->ready[0]=condition.getAsBoolean());if(ready[0])return;SystemClock.sleep(50);}throw new AssertionError("Timeout: "+label);}
  private boolean text(View view,String content){if(view instanceof TextView item&&item.getText().toString().contains(content))return true;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)if(text(group.getChildAt(i),content))return true;return false;}
  private View tagged(String tag){return a.root.findViewWithTag(tag);}
  private void tap(String tag){
    await(tag,()->tagged(tag)!=null&&tagged(tag).isEnabled());
    ui(()->{View view=tagged(tag);view.requestRectangleOnScreen(new Rect(0,0,view.getWidth(),view.getHeight()),true);});waitForIdleSync();
    int[] point=new int[2];ui(()->{View view=tagged(tag);check(view.hasWindowFocus(),"Touch window unobscured");Rect r=new Rect();check(view.getGlobalVisibleRect(r)&&r.height()>=Ui.dp(a,44),"Full click target visible");point[0]=r.centerX();point[1]=r.centerY();});
    long start=SystemClock.uptimeMillis();for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}){MotionEvent event=MotionEvent.obtain(start,SystemClock.uptimeMillis(),action,point[0],point[1],0);event.setSource(InputDevice.SOURCE_TOUCHSCREEN);sendPointerSync(event);event.recycle();SystemClock.sleep(30);}waitForIdleSync();
  }
  private void page(int number){await("page "+number,()->{View view=tagged("pagination:page:"+number);return view!=null&&view.isSelected()&&view.isEnabled()&&text(a.root,"第"+number+"页菜品");});}
  private void measure(){ui(()->{ViewGroup group=(ViewGroup)((ViewGroup)tagged("numbered-pagination")).getChildAt(0);int right=0;check(group.getChildCount()>=5,"At least five one-digit page numbers fit");for(int i=0;i<group.getChildCount();i++){View v=group.getChildAt(i);check(v.getWidth()>=Ui.dp(a,44)&&v.getHeight()>=Ui.dp(a,44),"Accessible number target");if(i>0)check(v.getLeft()-right>=Ui.dp(a,7),"Page numbers retain gaps");right=v.getRight();}check(right<=group.getWidth(),"Numbers fit without horizontal clipping");});}
  @Override public void onStart(){var result=new Bundle();try{
    if(!BuildConfig.DEBUG||!BuildConfig.APPLICATION_ID.endsWith(".paginationcheck")||!BuildConfig.API_BASE_URL.equals("http://127.0.0.1:18099/api/v1/"))throw new AssertionError("Isolated loopback APK required");
    a=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));await("session",()->a.repo.currentUser()!=null&&!a.isPageLoading());
    ui(()->a.go("dish-search",""));page(1);measure();
    tap("pagination:page:3");await("failed jump remains retryable",()->text(a.root,"模拟分页失败")&&tagged("pagination:page:1").isSelected()&&tagged("pagination:page:3").isEnabled());check(true,"Failed jump preserves prior result and allows retry");
    tap("pagination:page:3");page(3);tap("pagination:page:5");page(5);tap("pagination:page:3");page(3);tap("pagination:page:1");page(1);check(true,"Direct 1-3-5-3-1 system touches");
    tap("pagination:末页");page(100);check(!tagged("pagination:下一页").isEnabled(),"Last page boundary");tap("pagination:首页");page(1);check(!tagged("pagination:上一页").isEnabled(),"First page boundary");
    tap("pagination:下一页");page(2);tap("pagination:上一页");page(1);measure();
    ui(()->check(a.root.hasWindowFocus(),"Screenshot unobscured"));Bitmap bitmap=getUiAutomation().takeScreenshot();try(var output=new FileOutputStream(new File(getTargetContext().getFilesDir(),"android-pagination.png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,output);}finally{bitmap.recycle();}
    result.putString("stream","Pagination device PASS: "+checks+" assertions; actual search, retry, measured number row and system touches\n");finish(-1,result);
  }catch(Throwable t){result.putString("stream","Pagination device FAIL: "+android.util.Log.getStackTraceString(t));finish(1,result);}finally{if(a!=null)ui(a::finish);}}
}
