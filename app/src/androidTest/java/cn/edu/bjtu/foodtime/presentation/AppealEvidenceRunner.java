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
public final class AppealEvidenceRunner extends Instrumentation {
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
  private void shot()throws Exception{ui(()->check(a.root.hasWindowFocus(),"Screenshot app focus"));Bitmap bitmap=getUiAutomation().takeScreenshot();try(var out=new FileOutputStream(new File(getTargetContext().getFilesDir(),"android-normal-appeal-evidence.png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}}
  @Override public void onStart(){var result=new Bundle();try{
    if(!BuildConfig.DEBUG||!BuildConfig.APPLICATION_ID.endsWith(".appealgapscheck")||!BuildConfig.API_BASE_URL.equals("http://127.0.0.1:18101/api/v1/"))throw new AssertionError("Isolated loopback APK required");
    a=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));await("normal session",()->a.repo.currentUser()!=null&&!a.isPageLoading());actor=a.repo.currentUser().id();
    var data=a.repo.myModerationDecisions(1);decision=data.items().get(0).decisionId();
    ui(()->a.go("report-receipts","decisions"));await("received decision entry",()->{boolean[] ok={false};ui(()->ok[0]=text(a.root,"提交申诉")!=null);return ok[0];});
    ui(()->text(a.root,"提交申诉").setTag("open-received-appeal"));tap("open-received-appeal");await("editor reached from receipt",this::ready);check(a.route().kind().equals("appeal-compose")&&a.route().id().equals(decision),"Receipt action opens the exact recipient editor");
    tap("appeal-submit");check(error().contains("请填写"),"Empty content keeps editor open");
    ui(()->((EditText)tag("appeal-content")).setText("😀".repeat(2001)));tap("appeal-submit");check(error().contains("2000"),"Code-point limit enforced before uploads");check(fixture("state").getJSONArray("uploads").length()==0,"Invalid content sends no image");
    ui(()->((EditText)tag("appeal-content")).setText("本人申诉：请结合这三张图片复核。"));
    attach(0);tap("evidence-remove:0");await("removed selection",()->ready()&&tag("evidence-preview:0")==null);check(true,"Remove survives redraw without restoring the deleted selection");
    attach(0);attach(1);attach(2);check(tag("evidence-choose:3")==null,"Three evidence slots only");
    tap("appeal-submit");await("partial failure",()->ready()&&error().contains("第二张证据"));
    check(content().contains("本人申诉"),"Upload error preserves content");var store=new AppealDraftStore(a,BuildConfig.API_BASE_URL,actor);check(store.evidence(decision,0).startsWith("/api/v1/media/"),"First uploaded reference is durable before retry");
    ui(a::onBackPressed);open(decision);check(content().contains("本人申诉")&&tag("evidence-preview:2")!=null,"Returning restores text and all three selections");
    tap("appeal-submit");await("association failure",()->ready()&&error().contains("关联模拟失败"));
    var failed=fixture("state");check(failed.getJSONArray("uploads").length()==4,"Retry sends only the failed and remaining local images");shot();
    tap("appeal-submit");await("submission succeeded",()->fixture("state").getJSONArray("submitted").length()==1);
    var done=fixture("state");check(done.getJSONArray("uploads").length()==4,"Association retry never uploads the files again");
    JSONObject first=done.getJSONArray("submits").getJSONObject(0).getJSONObject("body"),second=done.getJSONArray("submits").getJSONObject(1).getJSONObject("body");check(first.toString().equals(second.toString()),"Association retries reuse evidence and request ID");check(first.getJSONArray("evidenceUrls").length()==3,"All three owned references reach the appeal endpoint");
    await("success clears this draft",()->store.read(decision).isEmpty());check(store.evidence(decision,0).isEmpty(),"Success clears owned evidence draft");
    String next=data.items().get(1).decisionId();await("operation completion",()->!a.operationInFlight());open(next);ui(()->((EditText)tag("appeal-content")).setText("A 账号私密草稿"));attach(0);fixture("hold-upload");tap("appeal-submit");await("held upload",()->fixture("state").getBoolean("uploadHeld"));
    fixture("actor?value=b");a.repo.restoreSession();ui(a::refreshCurrent);fixture("release");await("new account editor",this::ready);check(content().isEmpty()&&tag("evidence-preview:0")==null,"Other recipient of the same decision sees no old text or images");check(fixture("state").getJSONArray("submits").length()==2,"Switch during upload stops the old appeal association");
    result.putString("stream","Appeal evidence device PASS: "+checks+" assertions; real picker lifecycle with controlled URI, PhotoImport, HTTP retry, persistence and account isolation\n");finish(-1,result);
  }catch(Throwable t){String focus="";try{focus=focusedWindow();}catch(Exception ignored){}result.putString("stream","Appeal evidence device FAIL: "+android.util.Log.getStackTraceString(t)+"\nLast focus: "+focus);finish(1,result);}finally{if(a!=null)ui(a::finish);}}
}
