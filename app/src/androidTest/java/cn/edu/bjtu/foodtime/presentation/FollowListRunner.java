package cn.edu.bjtu.foodtime.presentation;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import cn.edu.bjtu.foodtime.BuildConfig;
import cn.edu.bjtu.foodtime.domain.model.Models.*;
import cn.edu.bjtu.foodtime.domain.repository.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;

/** Real local Backend + native list UI. Failure injection is explicit and limited to three calls. */
public final class FollowListRunner extends Instrumentation {
  private Bundle args;private MainActivity a;private FoodtimeRepository real;private int checks;
  private final AtomicReference<String> failNext=new AtomicReference<>("");
  @Override public void onCreate(Bundle arguments){super.onCreate(arguments);args=arguments;start();}
  private void ui(Runnable work){AtomicReference<Throwable> error=new AtomicReference<>();runOnMainSync(()->{try{work.run();}catch(Throwable failure){error.set(failure);}});if(error.get()!=null)throw new AssertionError(error.get());}
  private void check(boolean value,String name){if(!value)throw new AssertionError(name);checks++;Bundle status=new Bundle();status.putString("stream",name+" PASS\n");sendStatus(1,status);}
  private void await(BooleanSupplier condition){long end=SystemClock.uptimeMillis()+20000;while(SystemClock.uptimeMillis()<end){AtomicBoolean done=new AtomicBoolean();ui(()->done.set(condition.getAsBoolean()));if(done.get())return;SystemClock.sleep(70);}throw new AssertionError("Timed out, route="+a.route()+", failure="+text(a.content));}
  private String text(View v){StringBuilder s=new StringBuilder();if(v instanceof TextView t)s.append(t.getText()).append('\n');if(v instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++)s.append(text(g.getChildAt(i)));return s.toString();}
  private View find(View v,String label){if(v instanceof TextView t&&label.contentEquals(t.getText())&&v.getVisibility()==View.VISIBLE)return v;if(v instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){View found=find(g.getChildAt(i),label);if(found!=null)return found;}return null;}
  private View tag(String name){return a.content.findViewWithTag(name);}
  private void click(String name){ui(()->{View v=tag(name);if(v==null||!v.isEnabled())throw new AssertionError("Missing or disabled "+name);v.performClick();});}
  private void loaded(){await(()->!a.isPageLoading()&&tag("follow-refresh")!=null);waitForIdleSync();}
  private void ready(){await(()->!a.operationInFlight()&&tag("follow-refresh")!=null&&tag("follow-refresh").isEnabled());waitForIdleSync();}
  private void dialog(String label){long end=SystemClock.uptimeMillis()+5000;while(SystemClock.uptimeMillis()<end){AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();if(root!=null)for(var node:root.findAccessibilityNodeInfosByText(label))if(node.isClickable()&&label.contentEquals(node.getText())){node.performAction(AccessibilityNodeInfo.ACTION_CLICK);SystemClock.sleep(200);return;}SystemClock.sleep(70);}throw new AssertionError("Dialog action missing: "+label);}
  private void screenshot(String name)throws IOException{waitForIdleSync();SystemClock.sleep(350);try(var out=new FileOutputStream(new File(getTargetContext().getFilesDir(),name+".png"))){var bitmap=getUiAutomation().takeScreenshot();bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);bitmap.recycle();}}
  private void visit(boolean fans){ui(()->a.go(fans?"followers":"following",""));loaded();}
  private void login(String role){if(real.currentUser()!=null)real.logout();real.login(args.getString(role+"Account"),args.getString("testPassword"));ui(()->{a.repo=proxy();a.signedOut();a.tab="profile";a.render();});await(()->!a.isPageLoading()&&find(a.content,"编辑资料")!=null);}
  private FoodtimeRepository proxy(){return (FoodtimeRepository)Proxy.newProxyInstance(FoodtimeRepository.class.getClassLoader(),new Class[]{FoodtimeRepository.class},(p,m,values)->{
    if(Set.of("loadMoreFollows","loadFollows","setFollow","refresh").contains(m.getName())&&failNext.compareAndSet(m.getName(),""))throw new RepositoryException(503,"LOCAL_TEST_FAILURE","本地验收：暂时无法完成，请重试",0);
    try{return m.invoke(real,values);}catch(InvocationTargetException failure){throw failure.getCause();}
  });}
  private void layout(View v){if(v.getVisibility()!=View.VISIBLE)return;if(v instanceof Button){check(v.getMeasuredWidth()<=a.content.getWidth(),"Button fits width");check(v.getMeasuredHeight()>=Ui.dp(a,48),"Android 48dp target");if(v instanceof TextView t&&t.getLayout()!=null)for(int line=0;line<t.getLineCount();line++)check(t.getLayout().getEllipsisCount(line)==0,"No truncated action text");}if(v instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++)layout(g.getChildAt(i));}
  @Override public void onStart(){Bundle result=new Bundle();try{
    if(!BuildConfig.DEBUG||!BuildConfig.APPLICATION_ID.endsWith(".followcheck")||!BuildConfig.API_BASE_URL.equals("http://127.0.0.1:18080/api/v1/"))throw new AssertionError("Only isolated loopback build allowed");
    a=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    if("landscape".equals(args.getString("layoutName"))&&a.getResources().getConfiguration().orientation!=android.content.res.Configuration.ORIENTATION_LANDSCAPE){
      ActivityMonitor monitor=addMonitor(MainActivity.class.getName(),null,false);
      ui(()->a.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
      Activity rotated=monitor.waitForActivityWithTimeout(12000);removeMonitor(monitor);
      if(!(rotated instanceof MainActivity))throw new AssertionError("Landscape recreation missing");a=(MainActivity)rotated;
    }
    await(()->find(a.root,"正在连接…")==null);real=a.repo;
    login("user");visit(true);check(real.followers().total()==125,"Real Backend fan count 125");
    if("true".equals(args.getString("layoutOnly"))){
      if("landscape".equals(args.getString("layoutName")))check(a.getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE,"Actual landscape orientation");
      ui(()->layout(a.content));screenshot("follow-layout-"+args.getString("layoutName","check"));result.putString("stream","Follow layout PASS: "+checks+" checks\n");finish(-1,result);return;
    }
    check(real.followers().items().size()==20,"First page is 20");check(text(a.content).contains("我的关系"),"Clear-list heading");
    screenshot("followers-first-page");
    var before=real.followers();failNext.set("loadMoreFollows");click("follow-more");await(()->tag("follow-page-error") instanceof TextView t&&!t.getText().toString().isEmpty());ready();check(real.followers()==before,"Failed append keeps old rows");
    click("follow-more");await(()->real.followers().items().size()==40);ready();
    while(real.followers().hasMore()){click("follow-more");ready();}
    check(real.followers().items().size()==125,"All fans beyond 100 are accessible");check(new HashSet<>(real.followers().items().stream().map(FollowUser::id).toList()).size()==125,"No duplicated fans");
    click("following-tab");loaded();check(real.following().total()==45,"Real Backend follow count 45");click("follow-more");await(()->real.following().items().size()==40);ready();
    ui(()->a.scroll.scrollTo(0,800));waitForIdleSync();AtomicInteger y=new AtomicInteger();ui(()->y.set(a.scroll.getScrollY()));
    int depth=a.stacks.get("profile").size();click("followers-tab");loaded();click("following-tab");loaded();await(()->a.scroll.getScrollY()==y.get());
    check(real.following().items().size()==40,"Tab switch retains page two");check(a.stacks.get("profile").size()==depth,"Tab switch does not grow history");
    String removed=real.following().items().get(0).id();click("follow-action:"+removed);dialog("再想想");check(real.following().items().size()==40,"Cancel dialog leaves relation unchanged");
    click("follow-action:"+removed);dialog("取消关注");ready();check(real.currentUser().followingCount()==44,"Unfollow updates own count");check(real.following().items().stream().noneMatch(r->r.id().equals(removed)),"Unfollow removes only following row");
    click("follow-more");ready();check(real.following().items().size()==44,"Append after removal does not skip users");check(!real.following().hasMore(),"Following list ends correctly");
    click("followers-tab");loaded();String target=real.followers().items().get(0).id();failNext.set("setFollow");click("follow-action:"+target);await(()->tag("follow-error:"+target) instanceof TextView t&&!t.getText().toString().isEmpty());ready();
    check(real.currentUser().followingCount()==44&&!real.followers().items().get(0).followedByMe(),"Failed follow keeps count and flag");screenshot("follow-operation-error");
    click("follow-action:"+target);ready();check(real.currentUser().followingCount()==45,"Follow back updates own count");check(real.followers().items().get(0).followedByMe(),"Follow back shows mutual relation");check(real.followers().items().size()==125,"Follow back preserves loaded fans");
    click("follow-action:"+target);dialog("取消关注");ready();check(real.currentUser().followingCount()==44&&real.followers().items().size()==125,"Unfollow fan preserves fan membership");
    var cached=real.followers();failNext.set("loadFollows");click("follow-refresh");await(()->tag("follow-refresh-error") instanceof TextView t&&!t.getText().toString().isEmpty());ready();check(real.followers()==cached,"Failed refresh retains old list");
    click("follow-refresh");ready();check(real.followers().items().size()==20,"Explicit refresh starts from first page");
    ui(a::back);await(()->!a.isPageLoading()&&find(a.content,"编辑资料")!=null);check(text(a.content).contains("关注 44"),"Profile uses updated server follow count");
    login("admin");check(real.followers().page()==0&&real.following().page()==0,"Account switch clears both private lists");visit(true);check(real.followers().total()==0&&text(a.content).contains("还没有粉丝"),"Admin can read empty fans list");
    login("superadmin");visit(false);check(real.following().total()==0&&text(a.content).contains("你还没有关注任何人"),"Superadmin can read empty following list");
    login("user");failNext.set("refresh");ui(()->a.go("followers",""));await(()->!a.isPageLoading()&&find(a.content,"重试")!=null);check(tag("follow-rows")==null,"First failure is not an empty success");ui(()->find(a.content,"重试").performClick());loaded();check(real.followers().items().size()==20,"First-load retry recovers");
    real.logout();check(real.followers().page()==0&&real.following().page()==0,"Logout clears relationship caches");
    result.putString("stream","Follow real Backend/native UI PASS: "+checks+" checks\n");finish(-1,result);
  }catch(Throwable failure){result.putString("stream","Follow FAIL: "+android.util.Log.getStackTraceString(failure)+"\n");finish(1,result);}finally{if(a!=null)ui(a::finish);}}
}
