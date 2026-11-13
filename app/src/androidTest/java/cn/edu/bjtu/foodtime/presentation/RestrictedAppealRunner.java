package cn.edu.bjtu.foodtime.presentation;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import cn.edu.bjtu.foodtime.BuildConfig;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.json.*;

/** Real recipient editor, system touch, picker lifecycle, photo import, HTTP and private storage. */
public final class RestrictedAppealRunner extends Instrumentation {
  private MainActivity a;private int checks;private String decision,actor;
  interface Probe{boolean ready()throws Exception;}
  @Override public void onCreate(Bundle args){super.onCreate(args);start();}
  private void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
  private void ui(Runnable action){var error=new AtomicReference<Throwable>();runOnMainSync(()->{try{action.run();}catch(Throwable t){error.set(t);}});if(error.get()!=null)throw new AssertionError(error.get());}
  private void await(String label,Probe probe)throws Exception{long end=SystemClock.uptimeMillis()+20000;while(SystemClock.uptimeMillis()<end){if(probe.ready())return;SystemClock.sleep(60);}throw new AssertionError("Timeout: "+label);}
  private View tag(String name){return a.root.findViewWithTag(name);}
  private View text(View view,String label){if(view instanceof TextView item&&label.contentEquals(item.getText()))return view;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=text(group.getChildAt(i),label);if(found!=null)return found;}return null;}
  private boolean ready(){boolean[] value={false};ui(()->value[0]=tag("appeal-submit")!=null&&tag("appeal-submit").isEnabled()&&!a.operationInFlight());return value[0];}
  private String content(){var value=new AtomicReference<String>();ui(()->value.set(((EditText)tag("appeal-content")).getText().toString()));return value.get();}
  private String error(){var value=new AtomicReference<String>();ui(()->value.set(tag("appeal-form-error")==null?"":((TextView)tag("appeal-form-error")).getText().toString()));return value.get();}
  private JSONObject fixture(String path)throws Exception{var c=(HttpURLConnection)new URL("http://127.0.0.1:18101/fixture/"+path).openConnection();try{return new JSONObject(new String(c.getInputStream().readAllBytes(),StandardCharsets.UTF_8)).getJSONObject("data");}finally{c.disconnect();}}
  private String focusedWindow()throws Exception{try(var input=new ParcelFileDescriptor.AutoCloseInputStream(getUiAutomation().executeShellCommand("dumpsys window"))){String output=new String(input.readAllBytes(),StandardCharsets.UTF_8);for(String line:output.split("\n"))if(line.contains("mCurrentFocus="))return line;return "NO_CURRENT_FOCUS_FIELD";}}
  private boolean pickerFocus(String focus,String expected,String fallback){
    // API 36's media-module entry forwards into the separately packaged system Photo Picker.
    return focus.contains(expected+"/")||(!fallback.isEmpty()&&focus.contains(fallback+"/"))
        ||(expected.equals("com.google.android.providers.media.module")&&focus.contains("com.google.android.photopicker/com.android.photopicker.MainActivity"));
  }
  private void tap(String key)throws Exception{
    await(key,()->{boolean[] ok={false};ui(()->ok[0]=tag(key)!=null&&tag(key).isEnabled());return ok[0];});
    ui(()->{View v=tag(key);v.requestRectangleOnScreen(new Rect(0,0,v.getWidth(),v.getHeight()),true);});waitForIdleSync();
    int[] point=new int[2];ui(()->{View v=tag(key);Rect r=new Rect();check(v.hasWindowFocus()&&v.getGlobalVisibleRect(r)&&r.height()>=Ui.dp(a,44),"Unobscured touch target: "+key);point[0]=r.centerX();point[1]=r.centerY();});
    long start=SystemClock.uptimeMillis();for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}){MotionEvent e=MotionEvent.obtain(start,SystemClock.uptimeMillis(),action,point[0],point[1],0);e.setSource(InputDevice.SOURCE_TOUCHSCREEN);sendPointerSync(e);e.recycle();SystemClock.sleep(30);}waitForIdleSync();
  }
  private void attach(int index)throws Exception{
    File source=new File(getTargetContext().getFilesDir(),"source-"+index+".png");Bitmap bitmap=Bitmap.createBitmap(80,120,Bitmap.Config.ARGB_8888);bitmap.eraseColor(new int[]{Color.RED,Color.GREEN,Color.BLUE}[index]);try(var out=new FileOutputStream(source)){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}
    tap("evidence-choose:"+index);
    String expected=ImagePicker.intent(a,false,1,null).resolveActivity(a.getPackageManager()).getPackageName();
    var fallbackComponent=ImagePicker.fallback(false,null).resolveActivity(a.getPackageManager());
    String fallback=fallbackComponent==null?"":fallbackComponent.getPackageName();
    await("focused resolved system image picker "+expected,()->pickerFocus(focusedWindow(),expected,fallback));
    check(pickerFocus(focusedWindow(),expected,fallback),"External picker window matches the resolved system component or its known API 36 system redirect");
    sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);await("app focus after picker",()->{boolean[] focused={false};ui(()->focused[0]=a.root.hasWindowFocus());return focused[0];});
    // Controlled URI return exercises the real lifecycle and PhotoImport; not a claim of manually selecting a system album item.
    ui(()->a.onActivityResult(101,-1,new Intent().setData(Uri.fromFile(source))));
    await("imported image "+index,()->ready()&&tag("evidence-preview:"+index)!=null);
  }
  private void open(String id)throws Exception{ui(()->a.go("appeal-compose",id));await("recipient editor",this::ready);}
  private void shot()throws Exception{ui(()->check(a.root.hasWindowFocus(),"Screenshot app focus"));Bitmap bitmap=getUiAutomation().takeScreenshot();try(var out=new FileOutputStream(new File(getTargetContext().getFilesDir(),"android-restricted-page21.png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}}
  @Override public void onStart(){var result=new Bundle();try{
    if(!BuildConfig.DEBUG||!BuildConfig.APPLICATION_ID.endsWith(".appealgapscheck")||!BuildConfig.API_BASE_URL.equals("http://127.0.0.1:18101/api/v1/"))throw new AssertionError("Isolated loopback APK required");
    a=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    await("restricted page one",()->tag("pagination:page:2")!=null&&tag("pagination:page:2").isEnabled());
    check("appeal_only".equals(a.repo.sessionMode()),"Disabled account remains in the restricted session");actor=a.repo.currentUser().id();
    var first=a.repo.myModerationDecisions(1);String one=first.items().get(0).decisionId();
    check(first.items().size()==20&&first.total()==23,"First page holds twenty of twenty-three own decisions");
    check(tag("main-nav:home")==null&&text(a.root,"首页")==null,"No normal application navigation");
    check(tag("appeal-content")==null,"Decision editors are collapsed until requested");
    tap("pagination:page:2");
    await("second page failed with retry",()->{boolean[] ok={false};ui(()->ok[0]=text(a.root,"重试第 2 页")!=null);return ok[0];});
    check(tag("pagination:page:1").isSelected()&&tag("restricted-decision:"+one)!=null,"Failed read keeps page one and its rows");
    tap("restricted-refresh");await("second page loaded",()->tag("pagination:page:2").isSelected()&&tag("pagination:page:2").isEnabled());
    var next=a.repo.myModerationDecisions(2);decision=next.items().get(0).decisionId();String expired=next.items().get(2).decisionId();
    check(next.items().size()==3&&tag("restricted-decision:"+one)==null,"Page two replaces rather than appends the first twenty");
    check(tag("restricted-open:"+expired)==null,"Expired twenty-third decision has no appeal editor");
    tap("restricted-open:"+decision);await("page twenty-one editor",this::ready);
    ui(()->((EditText)tag("appeal-content")).setText("第二页本人申诉草稿，切页后应保留。"));attach(0);
    await("pager layout after image import",()->{boolean[] ok={false};ui(()->ok[0]=tag("pagination:page:2")!=null&&tag("pagination:page:2").isEnabled()&&tag("pagination:page:2").isSelected());return ok[0];});
    check(tag("pagination:page:2").isSelected()&&content().contains("第二页"),"Picker return retains page two, expanded decision and text");
    check(tag("evidence-preview:0")!=null,"Picker lifecycle retains the imported evidence");
    tap("pagination:page:1");await("back to page one",()->tag("pagination:page:1").isSelected()&&tag("pagination:page:1").isEnabled());
    tap("pagination:page:2");await("return page two editor",()->ready()&&tag("pagination:page:2").isSelected());
    check(content().contains("第二页")&&tag("evidence-preview:0")!=null,"Page changes preserve the account-owned decision draft and image");
    fixture("hold-upload");tap("appeal-submit");await("upload held",()->fixture("state").getBoolean("uploadHeld"));
    check(!tag("pagination:page:1").isEnabled()&&!tag("restricted-refresh").isEnabled(),"Upload locks page changes and refresh");fixture("release");
    await("association retry",()->ready()&&error().contains("关联模拟失败"));shot();
    check(tag("pagination:page:2").isSelected(),"Association failure stays on the current page");
    tap("appeal-submit");await("submitted twenty-first",()->fixture("state").getJSONArray("submitted").length()==1&&tag("restricted-open:"+decision)==null);
    var done=fixture("state");check(done.getJSONArray("uploads").length()==1,"Association retry reuses the uploaded reference");
    JSONObject initial=done.getJSONArray("submits").getJSONObject(0).getJSONObject("body"),retry=done.getJSONArray("submits").getJSONObject(1).getJSONObject("body");
    check(initial.toString().equals(retry.toString())&&initial.getJSONArray("evidenceUrls").length()==1,"Retries retain request ID and owned image");
    check(tag("pagination:page:2").isSelected(),"Successful appeal refreshes page two");
    fixture("shrink");tap("restricted-refresh");await("shrunk last page",()->tag("pagination:page:1")!=null&&tag("pagination:page:1").isSelected()&&tag("pagination:page:2")==null);
    check(tag("restricted-decision:"+one)!=null,"Reduced total returns to the valid last page");
    fixture("actor?value=b");a.repo.restoreSession();ui(a::redraw);await("other restricted account",()->tag("pagination:page:1")!=null&&tag("pagination:page:1").isEnabled());
    check(tag("pagination:page:1").isSelected()&&tag("appeal-content")==null,"Other account gets its own page and collapsed editor state");
    result.putString("stream","Restricted appeal pagination device PASS: "+checks+" assertions; system touches, controlled picker URI, HTTP retries and page preservation\n");finish(-1,result);
  }catch(Throwable t){String focus="";try{focus=focusedWindow();}catch(Exception ignored){}result.putString("stream","Restricted appeal pagination device FAIL: "+android.util.Log.getStackTraceString(t)+"\nLast focus: "+focus);finish(1,result);}finally{if(a!=null)ui(a::finish);}}
}
