package cn.edu.bjtu.foodtime.presentation;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import cn.edu.bjtu.foodtime.BuildConfig;
import java.io.*;
import java.net.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.json.*;

/** Actual attached filters, real HTTP parameters, system touches and retained edit state. */
public final class SearchFiltersRunner extends Instrumentation {
  private MainActivity a;private int checks;
  @Override public void onCreate(Bundle args){super.onCreate(args);start();}
  private void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
  private void ui(Runnable action){var error=new AtomicReference<Throwable>();runOnMainSync(()->{try{action.run();}catch(Throwable t){error.set(t);}});if(error.get()!=null)throw new AssertionError(error.get());}
  private void await(String label,BooleanSupplier probe){long end=SystemClock.uptimeMillis()+18000;while(SystemClock.uptimeMillis()<end){boolean[] ok={false};ui(()->ok[0]=probe.getAsBoolean());if(ok[0])return;SystemClock.sleep(50);}throw new AssertionError("Timeout: "+label);}
  private View tagged(String tag){return a.root.findViewWithTag(tag);}
  private void tap(String tag){await(tag,()->tagged(tag)!=null&&tagged(tag).isEnabled());ui(()->{View v=tagged(tag);v.requestRectangleOnScreen(new Rect(0,0,v.getWidth(),v.getHeight()),true);});waitForIdleSync();int[] point=new int[2];ui(()->{Rect r=new Rect();View v=tagged(tag);check(v.hasWindowFocus()&&v.getGlobalVisibleRect(r)&&r.height()>=Ui.dp(a,44),"Unobscured full touch target: "+tag);point[0]=r.centerX();point[1]=r.centerY();});long start=SystemClock.uptimeMillis();for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}){MotionEvent e=MotionEvent.obtain(start,SystemClock.uptimeMillis(),action,point[0],point[1],0);e.setSource(InputDevice.SOURCE_TOUCHSCREEN);sendPointerSync(e);e.recycle();SystemClock.sleep(30);}waitForIdleSync();}
  private JSONObject state()throws Exception{var c=(HttpURLConnection)new URL("http://127.0.0.1:18099/fixture/state").openConnection();try{return new JSONObject(new String(c.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getJSONObject("data");}finally{c.disconnect();}}
  private JSONObject query()throws Exception{var rows=state().getJSONArray("queries");return rows.getJSONObject(rows.length()-1);}
  private void done(){await("search completed",()->tagged("pagination:page:1")!=null&&tagged("pagination:page:1").isEnabled()&&tagged("dish-search-apply").isEnabled());}
  @Override public void onStart(){var result=new Bundle();try{
    if(!BuildConfig.DEBUG||!BuildConfig.APPLICATION_ID.endsWith(".searchfilterscheck")||!BuildConfig.API_BASE_URL.equals("http://127.0.0.1:18099/api/v1/"))throw new AssertionError("Isolated loopback APK required");
    a=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));await("session",()->a.repo.currentUser()!=null&&!a.isPageLoading());ui(()->a.go("dish-search",""));done();
    ui(()->check(tagged("dish-search-filters").getVisibility()==View.GONE,"Optional filters start collapsed"));tap("dish-search-filter-toggle");ui(()->check(tagged("dish-search-min-price").isShown()&&tagged("dish-search-sort").isShown(),"Filter controls are actually attached and shown"));
    await("hall selector",()->tagged("dish-search-hall:5f9a04ee-a6a1-5c5d-982a-2ebaa1f9f558")!=null);
    ui(()->{((EditText)tagged("dish-search-keyword")).setText("粉");((CheckBox)tagged("dish-search-hall:5f9a04ee-a6a1-5c5d-982a-2ebaa1f9f558")).setChecked(true);((EditText)tagged("dish-search-min-price")).setText("10");((EditText)tagged("dish-search-max-price")).setText("20");((Spinner)tagged("dish-search-rating")).setSelection(3);((Spinner)tagged("dish-search-sort")).setSelection(2);});waitForIdleSync();
    tap("dish-search-filter-toggle");tap("dish-search-filter-toggle");ui(()->check(((EditText)tagged("dish-search-min-price")).getText().toString().equals("10"),"Collapse does not discard edited criteria"));tap("dish-search-apply");done();
    var filtered=query();check(filtered.getJSONArray("minPrice").getString(0).equals("10")&&filtered.getJSONArray("maxPrice").getString(0).equals("20"),"Real HTTP price bounds");check(filtered.getJSONArray("minRating").getString(0).equals("4.0")&&filtered.getJSONArray("sort").getString(0).equals("price_desc"),"Real HTTP rating and sorting");
    check(filtered.getJSONArray("q").getString(0).equals("粉")&&filtered.getJSONArray("diningHallIds").getString(0).equals("5f9a04ee-a6a1-5c5d-982a-2ebaa1f9f558"),"Real HTTP keyword and hall selection");
    tap("pagination:page:2");await("page two",()->tagged("pagination:page:2")!=null&&tagged("pagination:page:2").isSelected()&&tagged("pagination:page:2").isEnabled());check(query().getJSONArray("minPrice").getString(0).equals("10"),"Number jump keeps active filters");
    int before=state().getJSONArray("queries").length();ui(()->{((EditText)tagged("dish-search-min-price")).setText("30");((EditText)tagged("dish-search-max-price")).setText("10");});tap("dish-search-apply");check(state().getJSONArray("queries").length()==before,"Invalid interval does not send a search");tap("dish-search-reset");done();check(!query().has("minPrice")&&!query().has("minRating")&&query().getJSONArray("page").getString(0).equals("1"),"Reset clears criteria and returns to page one");
    ui(()->check(a.root.hasWindowFocus(),"Screenshot unobscured"));SystemClock.sleep(600);Bitmap bitmap=getUiAutomation().takeScreenshot();try(var output=new FileOutputStream(new File(getTargetContext().getFilesDir(),"android-search-filters.png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,output);}finally{bitmap.recycle();}
    result.putString("stream","Search filters device PASS: "+checks+" assertions; attached controls, real search parameters, collapse, validation, pagination and reset\n");finish(-1,result);
  }catch(Throwable t){result.putString("stream","Search filters device FAIL: "+android.util.Log.getStackTraceString(t));finish(1,result);}finally{if(a!=null)ui(a::finish);}}
}
