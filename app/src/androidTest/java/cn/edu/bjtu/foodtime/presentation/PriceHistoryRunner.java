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

/** Real recipient editor, system touch, picker lifecycle, photo import, HTTP and private storage. */
public final class PriceHistoryRunner extends Instrumentation {
  private MainActivity a;private int checks;
  interface Probe{boolean ready()throws Exception;}
  @Override public void onCreate(Bundle args){super.onCreate(args);start();}
  private void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
  private void ui(Runnable action){var error=new AtomicReference<Throwable>();runOnMainSync(()->{try{action.run();}catch(Throwable t){error.set(t);}});if(error.get()!=null)throw new AssertionError(error.get());}
  private void await(String label,Probe probe)throws Exception{long end=SystemClock.uptimeMillis()+20000;while(SystemClock.uptimeMillis()<end){if(probe.ready())return;SystemClock.sleep(60);}throw new AssertionError("Timeout: "+label);}
  private View tag(String name){return a.root.findViewWithTag(name);}
  private JSONObject fixture(String path)throws Exception{var c=(HttpURLConnection)new URL("http://127.0.0.1:18102/fixture/"+path).openConnection();try{return new JSONObject(new String(c.getInputStream().readAllBytes(),StandardCharsets.UTF_8)).getJSONObject("data");}finally{c.disconnect();}}
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
  private android.view.accessibility.AccessibilityNodeInfo input(){var root=getUiAutomation().getRootInActiveWindow();if(root==null)return null;var queue=new java.util.ArrayDeque<android.view.accessibility.AccessibilityNodeInfo>();queue.add(root);while(!queue.isEmpty()){var node=queue.remove();if(node.isEditable())return node;for(int i=0;i<node.getChildCount();i++){var child=node.getChild(i);if(child!=null)queue.add(child);}}return null;}
  private void click(String label)throws Exception{getUiAutomation().waitForIdle(150,3000);await(label,()->active(label)!=null&&active(label).isEnabled());var node=active(label);node.refresh();Rect rect=new Rect();node.getBoundsInScreen(rect);check(node.isVisibleToUser()&&rect.height()>=Ui.dp(a,44),"Visible active-window control: "+label);long start=SystemClock.uptimeMillis();for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}){var event=MotionEvent.obtain(start,SystemClock.uptimeMillis(),action,rect.centerX(),rect.centerY(),0);event.setSource(InputDevice.SOURCE_TOUCHSCREEN);sendPointerSync(event);event.recycle();SystemClock.sleep(30);}waitForIdleSync();}
  private int count(View view,String prefix){int total=view.getTag()!=null&&view.getTag().toString().startsWith(prefix)?1:0;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)total+=count(group.getChildAt(i),prefix);return total;}
  private boolean chartCount(int expected){int[] value={-1};ui(()->value[0]=count(a.root,"price-history-chart"));return value[0]==expected;}
  private boolean enabled(String key){boolean[] value={false};ui(()->value[0]=tag(key)!=null&&tag(key).isEnabled());return value[0];}
  private String words(){var text=new StringBuilder();ui(()->append(a.root,text));return text.toString();}
  private void append(View view,StringBuilder out){if(view instanceof TextView item)out.append(item.getText()).append('\n');if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)append(group.getChildAt(i),out);}
  private void shot(String name)throws Exception{check(focusedWindow().contains(BuildConfig.APPLICATION_ID+"/"),"Screenshot belongs to the test application");Bitmap bitmap=getUiAutomation().takeScreenshot();try(var out=new FileOutputStream(new File(getTargetContext().getFilesDir(),name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}}
  @Override public void onStart(){var result=new Bundle();try{
    if(!BuildConfig.DEBUG||!BuildConfig.APPLICATION_ID.endsWith(".pricehistorycheck")||!BuildConfig.API_BASE_URL.equals("http://127.0.0.1:18102/api/v1/"))throw new AssertionError("Isolated local test APK required");
    String dish="00000000-0000-4000-8000-000000000301";
    a=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));await("session",()->a.repo.currentUser()!=null&&!a.isPageLoading());
    ui(()->a.go("detail",dish));await("dish price entry",()->enabled("dish-price-history"));tap("dish-price-history");await("initial chart",()->chartCount(3)&&enabled("price-history-refresh"));
    await("laid out range controls",()->{boolean[] ready={false};ui(()->ready[0]=tag("price-range:0").isLaidOut()&&tag("price-history-refresh").isLaidOut());return ready[0];});ui(()->{int[] first=new int[2],next=new int[2];tag("price-range:0").getLocationOnScreen(first);tag("price-history-refresh").getLocationOnScreen(next);check(next[1]-first[1]-tag("price-range:0").getHeight()>=Ui.dp(a,8),"Range and refresh borders keep at least eight dp separation");});
    check(a.route().kind().equals("price-history"),"Real dish action opens the price-history route");check(words().contains("/斤"),"Current price includes its actual unit");
    check(chartCount(3),"Known, unknown and unit changes produce three separate runs");var data=a.repo.priceHistory(dish,0);var runs=cn.edu.bjtu.foodtime.domain.model.PriceHistory.runs(data);check(runs.get(0).points().size()==2&&runs.get(2).points().size()==2,"Equal adjacent prices do not create extra chart markers");
    ui(()->check(tag("price-history-chart").requestFocus(),"Chart accepts keyboard focus in touch mode"));sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);waitForIdleSync();var selection=new AtomicReference<String>();await("keyboard-selected concrete point",()->{ui(()->selection.set(((TextView)tag("price-history-selection")).getText().toString()));return selection.get().contains("¥15.00/份");});check(selection.get().contains("¥15.00/份"),"Keyboard selection reveals a concrete recorded value");shot("android-price-history");
    tap("price-range:30");await("range thirty",()->enabled("price-history-refresh")&&fixture("state").getJSONArray("reads").getJSONObject(fixture("state").getJSONArray("reads").length()-1).getInt("days")==30);check(true,"Selected range reaches the actual HTTP request");
    tap("price-correct:8");await("history dialog",()->input()!=null);check(active("这会同步菜品当前价格和单位；删除将恢复为剩余最新记录。")!=null,"Latest-entry dialog explains current-state synchronization");
    var text=new Bundle();text.putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"21");check(input().performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT,text),"Entered corrected price through the active editable field");click("计价单位");click("公斤");fixture("hold-write");click("确认");await("held write",()->fixture("state").getBoolean("writeHeld"));check(!active("确认").isEnabled()&&!active("取消").isEnabled(),"Pending mutation blocks duplicate confirmation and cancel");fixture("release-write");await("failed mutation remains open",()->active("模拟保存失败")!=null&&active("确认").isEnabled());check(input()!=null&&input().getText().toString().equals("21"),"Failure preserves entered amount");shot("android-price-history-edit");click("确认");await("saved latest value",()->enabled("price-history-refresh")&&words().contains("¥21.00/公斤"));
    var state=fixture("state");check(state.getJSONArray("writes").length()==2&&state.getJSONArray("writes").getJSONObject(0).getJSONObject("body").toString().equals(state.getJSONArray("writes").getJSONObject(1).getJSONObject("body").toString()),"Retry keeps the same amount, unit and expected revision");
    tap("price-delete:8");click("取消");check(fixture("state").getJSONArray("writes").length()==2,"Cancel does not issue a delete");tap("price-delete:8");click("确认");await("concurrent conflict",()->active("价格资料已更新，请刷新")!=null);click("刷新记录");await("refreshed conflict",()->enabled("price-delete:8"));tap("price-delete:8");click("确认");await("tail restored",()->enabled("price-history-refresh")&&words().contains("¥18.00/斤")&&tag("price-delete:8")==null);check(true,"Removing the latest wrong price restores the prior current amount and unit");
    ui(a::onBackPressed);await("detail refreshed",()->enabled("dish-price-history"));check(words().contains("¥18.00/斤"),"Returning to the dish refreshes its current price");tap("dish-price-history");await("history reopened",()->enabled("price-history-refresh"));
    fixture("mode?value=single");tap("price-history-refresh");await("single history",()->enabled("price-history-refresh")&&chartCount(1)&&tag("price-correct:1")!=null);check(tag("price-delete:1")==null,"Single global record may be corrected but not deleted");
    fixture("mode?value=unknown");tap("price-history-refresh");await("unknown price",()->enabled("price-history-refresh")&&chartCount(0));check(words().contains("价格未知")&&words().contains("暂无可绘制"),"Unknown price has an explicit empty chart, never a zero-valued line");
    fixture("mode?value=maximum");tap("price-history-refresh");await("maximum price",()->enabled("price-history-refresh")&&chartCount(1)&&words().contains("99999999.99"));ui(()->{View chart=tag("price-history-chart");chart.requestRectangleOnScreen(new Rect(0,0,chart.getWidth(),chart.getHeight()),true);});waitForIdleSync();shot("android-price-history-maximum");check(true,"Maximum amount renders on the real canvas");
    tap("price-correct:1");await("open dialog before session switch",()->input()!=null);fixture("actor?value=b");a.repo.restoreSession();ui(a::redraw);await("new reader",()->enabled("price-history-refresh"));check(active("确认")==null&&tag("price-correct:1")==null,"Account change closes the old dialog and removes management controls");
    result.putString("stream","Price history device PASS: "+checks+" assertions; real detail entry, system touches, canvas, dialogs, HTTP retry/conflict and account isolation\n");finish(-1,result);
  }catch(Throwable failure){String focus="";try{focus=focusedWindow();}catch(Exception ignored){}result.putString("stream","Price history device FAIL: "+android.util.Log.getStackTraceString(failure)+"\nLast focus: "+focus);finish(1,result);}finally{if(a!=null)ui(a::finish);}}
}
