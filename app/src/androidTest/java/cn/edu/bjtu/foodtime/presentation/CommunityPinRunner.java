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
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.json.*;

/** Isolated real community feed, pin controls, system touches and loopback HTTP checks. */
public final class CommunityPinRunner extends Instrumentation {
  private MainActivity a;private int checks;
  interface Probe{boolean ready()throws Exception;}
  @Override public void onCreate(Bundle args){super.onCreate(args);start();}
  private void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
  private void ui(Runnable action){var error=new AtomicReference<Throwable>();runOnMainSync(()->{try{action.run();}catch(Throwable t){error.set(t);}});if(error.get()!=null)throw new AssertionError(error.get());}
  private void await(String label,Probe probe)throws Exception{long end=SystemClock.uptimeMillis()+20000;while(SystemClock.uptimeMillis()<end){if(probe.ready())return;SystemClock.sleep(60);}throw new AssertionError("Timeout: "+label);}
  private View tag(String name){return a.root.findViewWithTag(name);}
  private JSONObject fixture(String path)throws Exception{var c=(HttpURLConnection)new URL("http://127.0.0.1:18104/fixture/"+path).openConnection();try{return new JSONObject(new String(c.getInputStream().readAllBytes(),StandardCharsets.UTF_8)).getJSONObject("data");}finally{c.disconnect();}}
  private String focusedWindow()throws Exception{try(var input=new ParcelFileDescriptor.AutoCloseInputStream(getUiAutomation().executeShellCommand("dumpsys window"))){String output=new String(input.readAllBytes(),StandardCharsets.UTF_8);for(String line:output.split("\n"))if(line.contains("mCurrentFocus="))return line;return "NO_CURRENT_FOCUS_FIELD";}}
  private void tap(String key)throws Exception{
    await(key,()->{boolean[] ok={false};ui(()->ok[0]=tag(key)!=null&&tag(key).isEnabled());return ok[0];});
    ui(()->{View v=tag(key);v.requestRectangleOnScreen(new Rect(0,0,v.getWidth(),v.getHeight()),true);});waitForIdleSync();
    int[] point=new int[2];ui(()->{View v=tag(key);Rect r=new Rect();check(v.hasWindowFocus()&&v.getGlobalVisibleRect(r)&&r.height()>=Ui.dp(a,44),"Unobscured touch target: "+key);point[0]=r.centerX();point[1]=r.centerY();});
    long start=SystemClock.uptimeMillis();for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}){MotionEvent e=MotionEvent.obtain(start,SystemClock.uptimeMillis(),action,point[0],point[1],0);e.setSource(InputDevice.SOURCE_TOUCHSCREEN);sendPointerSync(e);e.recycle();SystemClock.sleep(30);}waitForIdleSync();
  }
  private android.view.accessibility.AccessibilityNodeInfo active(String label){
    var root=getUiAutomation().getRootInActiveWindow();if(root==null)return null;var queue=new java.util.ArrayDeque<android.view.accessibility.AccessibilityNodeInfo>();queue.add(root);android.view.accessibility.AccessibilityNodeInfo textMatch=null;
    while(!queue.isEmpty()){var node=queue.remove();if(label.equals(node.getContentDescription()==null?"":node.getContentDescription().toString()))return node;if(textMatch==null&&label.equals(node.getText()==null?"":node.getText().toString()))textMatch=node;for(int i=0;i<node.getChildCount();i++){var child=node.getChild(i);if(child!=null)queue.add(child);}}return textMatch;
  }
  private void click(String label)throws Exception{getUiAutomation().waitForIdle(150,3000);await(label,()->active(label)!=null&&active(label).isEnabled());var node=active(label);node.refresh();Rect rect=new Rect();node.getBoundsInScreen(rect);check(node.isVisibleToUser()&&rect.height()>=Ui.dp(a,44),"Visible active-window control: "+label);long start=SystemClock.uptimeMillis();for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}){var event=MotionEvent.obtain(start,SystemClock.uptimeMillis(),action,rect.centerX(),rect.centerY(),0);event.setSource(InputDevice.SOURCE_TOUCHSCREEN);sendPointerSync(event);event.recycle();SystemClock.sleep(30);}waitForIdleSync();}
  private int count(View view,String prefix){int total=view.getTag()!=null&&view.getTag().toString().startsWith(prefix)?1:0;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)total+=count(group.getChildAt(i),prefix);return total;}
  private boolean enabled(String key){boolean[] value={false};ui(()->value[0]=tag(key)!=null&&tag(key).isEnabled());return value[0];}
  private String words(){var text=new StringBuilder();ui(()->append(a.root,text));return text.toString();}
  private void append(View view,StringBuilder out){if(view instanceof TextView item)out.append(item.getText()).append('\n');if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)append(group.getChildAt(i),out);}
  private void shot(String name)throws Exception{check(focusedWindow().contains(BuildConfig.APPLICATION_ID+"/"),"Screenshot belongs to the test application");Bitmap bitmap=getUiAutomation().takeScreenshot();try(var out=new FileOutputStream(new File(getTargetContext().getFilesDir(),name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}}
  @Override public void onStart(){var result=new Bundle();try{
    if(!BuildConfig.DEBUG||!BuildConfig.APPLICATION_ID.endsWith(".pincheck")||!BuildConfig.API_BASE_URL.equals("http://127.0.0.1:18104/api/v1/"))throw new AssertionError("Isolated local pin APK required");
    String p1="00000000-0000-4000-8000-000000000401",p2="00000000-0000-4000-8000-000000000402",p3="00000000-0000-4000-8000-000000000403";
    a=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));await("session",()->a.repo.currentUser()!=null&&!a.isPageLoading());
    ui(()->a.go("community",""));await("real public pins",()->enabled("community-pinned-post:"+p1)&&enabled("community-post-card:"+p3));
    check(!words().contains("置顶乙"),"Pending pin title is absent from the public feed");check(words().contains("普通食记丙")&&words().contains("普通食记丁"),"Regular posts remain below the independent compact list");check(words().split("置顶甲",-1).length-1==1,"Pinned title is not duplicated in the ordinary grid");shot("android-pins");
    tap("community-pinned-post:"+p1);await("original post detail",()->a.route().kind().equals("community-detail")&&enabled("community-pins-open"));check(a.route().id().equals(p1),"Pin opens the original post id");sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);await("feed returned",()->enabled("community-post-card:"+p3));
    tap("community-post-card:"+p3);await("regular detail",()->a.route().id().equals(p3)&&enabled("community-pins-open"));tap("community-pins-open");await("loaded management",()->enabled("community-pin-current")&&tag("community-pin:"+p2)!=null);check(words().contains("待审核，保留名额"),"Management shows a hidden reserved slot");check(!enabled("community-pin-up:"+p1),"First reserved position cannot move above the list");
    fixture("hold-write");tap("community-pin-current");await("held pin",()->fixture("state").getBoolean("writeHeld"));check(!enabled("community-pins-refresh")&&!enabled("community-unpin:"+p1),"Pending write locks duplicate, cancellation and refresh controls");fixture("release-write");await("explicit retry",()->enabled("community-pin-current")&&words().contains("模拟置顶失败"));shot("android-pin-manager");tap("community-pin-current");await("third slot",()->tag("community-pin:"+p3)!=null&&enabled("community-pin-up:"+p3));
    var writes=fixture("state").getJSONArray("writes");check(writes.length()==2&&writes.getJSONObject(0).getJSONObject("body").toString().equals(writes.getJSONObject(1).getJSONObject("body").toString()),"Failed pin retry reuses the exact expected revision");check(tag("community-pin-current")==null,"Already pinned post cannot be inserted twice");
    tap("community-pin-up:"+p3);await("reordered snapshot",()->fixture("state").getJSONArray("pins").getString(1).equals(p3)&&enabled("community-unpin:"+p2));check(true,"Reorder includes the hidden reserved entry");tap("community-unpin:"+p2);await("conflict",()->words().contains("置顶列表已变化")&&enabled("community-pins-refresh"));int before=fixture("state").getJSONArray("writes").length();SystemClock.sleep(150);check(fixture("state").getJSONArray("writes").length()==before,"A conflict does not automatically repeat the write");tap("community-pins-refresh");await("manual refresh",()->enabled("community-unpin:"+p2)&&!words().contains("置顶列表已变化"));tap("community-unpin:"+p2);await("cancelled reserved pin",()->tag("community-pin:"+p2)==null&&enabled("community-pins-refresh"));check(fixture("state").getJSONArray("pins").length()==2,"Explicit refreshed cancellation releases a slot");
    sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);await("detail back",()->a.route().kind().equals("community-detail"));sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);await("fresh public feed",()->enabled("community-pinned-post:"+p3));check(tag("community-post-card:"+p3)==null,"Returning after management refreshes the ordinary grid without duplicates");
    fixture("hold-read");tap("community-pins-open");await("old actor read held",()->fixture("state").getBoolean("readHeld"));fixture("actor?value=b");a.repo.restoreSession();ui(a::redraw);fixture("release-read");ui(()->a.go("community",""));await("ordinary reader",()->enabled("community-pinned-post:"+p1)&&!a.isPageLoading());check(tag("community-pins-open")==null&&tag("community-unpin:"+p1)==null,"Account switch cannot retain or restore old management controls");shot("android-pin-reader");
    result.putString("stream","Community pins device PASS: "+checks+" assertions; real feed/detail entry, system touches, HTTP conflict/retry and account isolation\n");finish(-1,result);
  }catch(Throwable failure){String focus="";try{focus=focusedWindow();}catch(Exception ignored){}result.putString("stream","Community pins device FAIL: "+android.util.Log.getStackTraceString(failure)+"\nLast focus: "+focus);finish(1,result);}finally{if(a!=null)ui(a::finish);}}
}
