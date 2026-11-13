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
public final class PostEditRunner extends Instrumentation {
  private MainActivity a;private int checks;
  interface Probe{boolean ready()throws Exception;}
  @Override public void onCreate(Bundle args){super.onCreate(args);start();}
  private void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
  private void ui(Runnable action){var error=new AtomicReference<Throwable>();runOnMainSync(()->{try{action.run();}catch(Throwable t){error.set(t);}});if(error.get()!=null)throw new AssertionError(error.get());}
  private void await(String label,Probe probe)throws Exception{long end=SystemClock.uptimeMillis()+20000;while(SystemClock.uptimeMillis()<end){if(probe.ready())return;SystemClock.sleep(60);}throw new AssertionError("Timeout: "+label);}
  private View tag(String name){return a.root.findViewWithTag(name);}
  private JSONObject fixture(String path)throws Exception{var c=(HttpURLConnection)new URL("http://127.0.0.1:18106/fixture/"+path).openConnection();try{return new JSONObject(new String(c.getInputStream().readAllBytes(),StandardCharsets.UTF_8)).getJSONObject("data");}finally{c.disconnect();}}
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
    if(!BuildConfig.DEBUG||!BuildConfig.APPLICATION_ID.endsWith(".editcheck")||!BuildConfig.API_BASE_URL.equals("http://127.0.0.1:18106/api/v1/"))throw new AssertionError("Isolated local edit APK required");
    String id="00000000-0000-4000-8000-000000000403";
    a=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));await("session",()->a.repo.currentUser()!=null&&!a.isPageLoading());
    ui(()->PostDraftStore.save(a,a.repo.currentUser().id(),"独立发帖草稿","不可覆盖的本机草稿","",java.util.List.of()));
    ui(()->a.go("community-detail",id));await("own detail entry",()->enabled("post-edit-open"));tap("post-edit-open");await("editor",()->enabled("post-edit-title")&&!a.isPageLoading());
    check(words().contains("点赞、评论、收藏、浏览量和原发布时间保留"),"Editor explains moderation and preservation");
    check(a.drafts.getLong(a.draftKey("editRevision"))==1,"Editor hydrates the captured server revision");
    ui(()->{((EditText)tag("post-edit-title")).setText("手机更正标题");((EditText)tag("post-edit-content")).setText("完整的修改正文");});
    shot("android-post-edit");tap("post-edit-submit");await("failed edit retains input",()->enabled("post-edit-submit")&&words().contains("模拟修改失败"));
    check(words().contains("完整的修改正文"),"Failed edit retains all entered content");
    fixture("external-edit");tap("post-edit-submit");await("version conflict",()->words().contains("帖子内容已变化")&&enabled("post-edit-reload"));
    var edits=fixture("state").getJSONArray("edits");check(edits.length()==2&&edits.getJSONObject(0).getJSONObject("body").toString().equals(edits.getJSONObject(1).getJSONObject("body").toString()),"Retry reuses exact request id and expected revision");
    check(a.drafts.getLong(a.draftKey("editRevision"))==1,"Conflict does not silently advance revision");
    tap("post-edit-reload");click("读取");await("explicit latest version",()->words().contains("另一设备的更新")&&a.drafts.getLong(a.draftKey("editRevision"))==2);
    check(fixture("state").getJSONArray("edits").length()==2,"Reading latest never automatically writes");
    ui(()->{((EditText)tag("post-edit-title")).setText("最终更正");((EditText)tag("post-edit-content")).setText("已核对最新版本的正文");});tap("post-edit-submit");
    await("pending detail",()->a.route().kind().equals("community-detail")&&!a.isPageLoading()&&words().contains("最终更正"));
    var row=fixture("state").getJSONObject("posts").getJSONObject(id);check(row.getString("status").equals("pending")&&row.getLong("contentRevision")==3,"Same identity returns to pending revision three");
    check(row.getLong("likeCount")==12&&row.getLong("viewCount")==20,"Original interactions and view count are retained");
    check(words().contains("修改于"),"Detail displays last edited time");
    check(PostDraftStore.load(a,a.repo.currentUser().id()).title().equals("独立发帖草稿"),"Author editing never overwrites or deletes the separate creation draft");
    shot("android-post-edited");
    tap("post-edit-open");await("reopen pending post",()->enabled("post-edit-title"));check(a.drafts.getLong(a.draftKey("editRevision"))==3,"Pending post may be edited again at latest revision");
    sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);click("放弃修改");await("discard returns to same post",()->a.route().kind().equals("community-detail")&&!a.isPageLoading());check(a.repo.communityPost(id).contentRevision()==3,"Discard leaves server revision unchanged");
    result.putString("stream","Post edit device PASS: "+checks+" assertions; real detail entry, system touches, conflict/retry and creation-draft isolation\n");finish(-1,result);
  }catch(Throwable failure){result.putString("stream","Post edit device FAIL: "+android.util.Log.getStackTraceString(failure));finish(1,result);}finally{if(a!=null)ui(a::finish);}}
}
