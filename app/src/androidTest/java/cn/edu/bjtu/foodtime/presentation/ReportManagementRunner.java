package cn.edu.bjtu.foodtime.presentation;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import cn.edu.bjtu.foodtime.BuildConfig;
import java.io.File;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONObject;

/** Actual window, accessibility text entry and system touches against an isolated HTTP fixture. */
public final class ReportManagementRunner extends Instrumentation {
  private MainActivity activity;
  private int checks;
  @Override public void onCreate(Bundle args){super.onCreate(args);start();}
  interface Probe {boolean ready() throws Exception;}
  private void await(String label,Probe probe) throws Exception {
    long until=SystemClock.uptimeMillis()+18000;
    while(SystemClock.uptimeMillis()<until){if(probe.ready())return;SystemClock.sleep(60);}
    throw new AssertionError("Timeout: "+label);
  }
  private void ui(Runnable action){
    AtomicReference<Throwable> error=new AtomicReference<>();
    runOnMainSync(()->{try{action.run();}catch(Throwable failure){error.set(failure);}});
    if(error.get()!=null)throw new AssertionError(error.get());
  }
  private void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
  private AccessibilityNodeInfo root(){
    var root=getUiAutomation().getRootInActiveWindow();
    if(root!=null&&!BuildConfig.APPLICATION_ID.contentEquals(root.getPackageName()))
      throw new AssertionError("Input rejected: app window obscured by "+root.getPackageName());
    return root;
  }
  private AccessibilityNodeInfo find(AccessibilityNodeInfo node,String label,boolean editable){
    if(node==null)return null;
    if((label.contentEquals(node.getText()==null?"":node.getText())||label.contentEquals(node.getContentDescription()==null?"":node.getContentDescription()))&&(!editable||node.isEditable()))return node;
    for(int i=0;i<node.getChildCount();i++){var match=find(node.getChild(i),label,editable);if(match!=null)return match;}
    return null;
  }
  private boolean has(String text){return find(root(),text,false)!=null;}
  private void waitText(String text) throws Exception {await(text,()->has(text));}
  private void tap(String text) throws Exception {
    waitText(text);var node=find(root(),text,false);node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId());
    await("visible "+text,()->{var current=find(root(),text,false);return current!=null&&current.isVisibleToUser();});
    waitForIdleSync();node=find(root(),text,false);Rect bounds=new Rect();node.getBoundsInScreen(bounds);
    if(bounds.width()<1||bounds.height()<1)throw new AssertionError("Empty touch target: "+text);
    long time=SystemClock.uptimeMillis();
    MotionEvent down=MotionEvent.obtain(time,time,MotionEvent.ACTION_DOWN,bounds.exactCenterX(),bounds.exactCenterY(),0);
    down.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);sendPointerSync(down);down.recycle();
    SystemClock.sleep(30);
    MotionEvent up=MotionEvent.obtain(time,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,bounds.exactCenterX(),bounds.exactCenterY(),0);
    up.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);sendPointerSync(up);up.recycle();waitForIdleSync();
  }
  private void set(String label,String value) throws Exception {
    await(label,()->find(root(),label,true)!=null);
    Bundle args=new Bundle();args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value);
    if(!find(root(),label,true).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args))throw new AssertionError("Cannot fill "+label);
    waitForIdleSync();
  }
  private String field(String label){var node=find(root(),label,true);return node==null?"":node.getText().toString();}
  private JSONObject stats() throws Exception {
    var connection=(HttpURLConnection)new URL("http://127.0.0.1:18097/fixture/management").openConnection();
    connection.setConnectTimeout(5000);connection.setReadTimeout(5000);
    try(var in=connection.getInputStream()){return new JSONObject(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getJSONObject("data");}
    finally{connection.disconnect();}
  }
  private JSONArray requests(String suffix) throws Exception {
    JSONArray found=new JSONArray(),rows=stats().getJSONArray("requests");
    for(int i=0;i<rows.length();i++)if(rows.getJSONObject(i).getString("path").endsWith(suffix))found.put(rows.getJSONObject(i));
    return found;
  }
  private void shot(String name) throws Exception {
    SystemClock.sleep(2600);root();Bitmap bitmap=getUiAutomation().takeScreenshot();
    if(bitmap==null)throw new AssertionError("Screenshot unavailable");
    try(var out=new FileOutputStream(new File(getTargetContext().getFilesDir(),name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}
  }
  @Override public void onStart(){
    Bundle result=new Bundle();
    try{
      if(!BuildConfig.DEBUG||!BuildConfig.APPLICATION_ID.endsWith(".reportmanagementcheck")||!BuildConfig.API_BASE_URL.equals("http://127.0.0.1:18097/api/v1/"))throw new AssertionError("Only isolated loopback build permitted");
      activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      await("session",()->activity.repo.currentUser()!=null&&!activity.isPageLoading());
      ui(()->{activity.selectTab("admin");activity.go("admin-reports","");});waitText("举报原因：待处理本地举报");
      check(!has("标为已处理"),"Legacy shortcut no longer silently completes cases");
      tap("填写处理决定");tap("提交");waitText("请填写公开理由");check(requests("/decision").length()==0,"Public reason required before decision request");
      set("公开理由（必填，最多500字）","本地公开决定");set("内部备注（不公开，可选）","本地内部备注");
      tap("提交");waitText("决定保存失败");check(field("公开理由（必填，最多500字）").equals("本地公开决定"),"Failed decision retains reason");
      tap("提交");waitText("共 0 条待处理举报");
      var body=requests("/decision").getJSONObject(1).getJSONObject("body");
      check(body.getString("internalNote").equals("本地内部备注")&&body.getString("publicReason").equals("本地公开决定"),"Public reason and internal note stay separate");
      check(requests("/actions").length()==0,"Saving a decision does not execute punishment");
      tap("已处理");waitText("共 22 条已处理决定");tap("下一页");waitText("公开理由：已处理案例21");check(has("公开理由：已处理案例20"),"Completed decisions beyond first page remain available");
      tap("停用被举报用户");tap("再想想");check(requests("/actions").length()==0,"Cancel does not punish");
      tap("停用被举报用户");tap("确认");waitText("动作冲突，请重试");check(has("公开理由：已处理案例20"),"Failed action retains the decision list");
      tap("停用被举报用户");tap("确认");await("one actual action",()->stats().getInt("appliedActions")==1);
      check(requests("/actions").getJSONObject(1).getJSONObject("body").getString("actionType").equals("disable_user"),"Actual action remains explicit");
      waitText("共 22 条已处理决定");tap("查看／回复追问");waitText("追问暂不可用");tap("刷新追问");waitText("共 21 条追问与回复");tap("下一页");waitText("结案后的第21条追问");check(has("结案后的第21条追问"),"Completed-case follow-up history paginates");
      set("公开回复（最多500字）","管理员公开回复");set("内部备注（不公开，最多2000字）","管理内部备注");
      File image=new File(getTargetContext().getFilesDir(),"staff-evidence.jpg");
      Bitmap bitmap=Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888);
      try(var out=new FileOutputStream(image)){bitmap.compress(Bitmap.CompressFormat.JPEG,90,out);}finally{bitmap.recycle();}
      String prefix="staff-evidence:"+activity.repo.currentUser().id()+":"+activity.route().id();
      ui(()->{activity.drafts.putString(prefix+":0",image.toURI().toString());activity.redraw();});
      waitText("结案后的第21条追问");tap("提交回复");waitText("回复关联失败");
      await("uploaded reference retained",()->activity.drafts.getString(prefix+":0","").startsWith("/api/v1/media/"));
      check(stats().getInt("uploads")==1,"Successful evidence upload retained after association failure");
      check(field("公开回复（最多500字）").equals("管理员公开回复"),"Reply failure retains text");
      tap("提交回复");waitText("管理员公开回复");
      check(stats().getInt("uploads")==1,"Retry reuses uploaded reference instead of reuploading");
      var attempts=requests("/follow-ups");
      check(attempts.getJSONObject(0).getJSONObject("body").getString("clientRequestId").equals(attempts.getJSONObject(1).getJSONObject("body").getString("clientRequestId")),"Reply retry reuses request ID");
      check(has("内部备注（不公开）：管理内部备注"),"Staff-only note visible separately");shot("android-staff-followup");
      sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);waitText("公开理由：已处理案例21");check(has("公开理由：已处理案例20"),"Back restores completed tab and page");
      tap("申诉复核");waitText("本地申诉内容");tap("查看申诉详情");waitText("开始复核");tap("开始复核");waitText("申诉已被修改，请刷新");check(has("开始复核"),"Concurrent start leaves retry path");
      tap("刷新详情");waitText("当前状态：已提交");tap("开始复核");waitText("保存复核结论");
      check(requests("/start").getJSONObject(1).getString("query").contains("expectedRevision=2"),"Refresh uses latest appeal revision");
      tap("保存复核结论");waitText("请填写公开复核理由");check(requests("/resolve").length()==0,"Review reason required");
      set("公开复核理由（必填，最多500字）","本地公开复核结果");set("内部复核备注（不公开，可选）","内部复核记录");
      tap("保存复核结论");waitText("复核冲突，请刷新");check(field("公开复核理由（必填，最多500字）").equals("本地公开复核结果"),"Review conflict preserves draft");
      tap("刷新详情");waitText("当前状态：复核中");tap("保存复核结论");waitText("复核结果：成立");
      check(stats().getInt("appliedActions")==1&&requests("/actions").length()==2,"Appeal resolution never automatically restores or punishes");
      check(requests("/resolve").getJSONObject(1).getJSONObject("body").getLong("expectedRevision")==4,"Resolve uses refreshed revision");
      check(has("内部备注（不公开）：内部复核记录"),"Internal review note stays distinct from public reason");shot("android-appeal-resolved");
      var control=(HttpURLConnection)new URL("http://127.0.0.1:18097/fixture/without-capabilities").openConnection();try(var in=control.getInputStream()){in.readAllBytes();}finally{control.disconnect();}
      activity.repo.loadCapabilities();ui(()->activity.go("admin-reports",""));waitText("当前账号没有举报处理权限。");
      check(!has("填写处理决定")&&!has("申诉复核"),"Server capability removal hides management even for superadmin");
      result.putString("stream","Report management device PASS: "+checks+" assertions; system touch and real HTTP fixtures\n");finish(-1,result);
    }catch(Throwable failure){try{shot("android-report-failure");}catch(Throwable ignored){}result.putString("stream","Report management device FAIL: "+android.util.Log.getStackTraceString(failure));finish(1,result);}
    finally{if(activity!=null)ui(activity::finish);}
  }
}
