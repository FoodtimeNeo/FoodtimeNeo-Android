package cn.edu.bjtu.foodtime.presentation;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.*;
import android.view.*;
import android.widget.*;
import cn.edu.bjtu.foodtime.BuildConfig;
import cn.edu.bjtu.foodtime.domain.repository.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;

/** Real isolated Backend; only the explicitly named failed append is injected. */
public final class UserSearchRunner extends Instrumentation {
  private Bundle args;private MainActivity a;private FoodtimeRepository real;private int checks;
  private final AtomicInteger reads=new AtomicInteger();private final AtomicBoolean failNext=new AtomicBoolean();
  @Override public void onCreate(Bundle arguments){super.onCreate(arguments);args=arguments;start();}
  private void ui(Runnable work){AtomicReference<Throwable> failure=new AtomicReference<>();runOnMainSync(()->{try{work.run();}catch(Throwable error){failure.set(error);}});if(failure.get()!=null)throw new AssertionError(failure.get());}
  private void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
  private View tag(String name){return a.content.findViewWithTag(name);}
  private EditText input(){return (EditText)tag("user-search-input");}
  private boolean connecting(View view){if(view instanceof TextView text&&"正在连接…".contentEquals(text.getText()))return true;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)if(connecting(group.getChildAt(i)))return true;return false;}
  private void await(BooleanSupplier condition){long end=SystemClock.uptimeMillis()+20000;while(SystemClock.uptimeMillis()<end){AtomicBoolean done=new AtomicBoolean();ui(()->done.set(condition.getAsBoolean()));if(done.get()){waitForIdleSync();return;}SystemClock.sleep(60);}throw new AssertionError("Timed out: "+a.route());}
  private void click(String name){ui(()->{View v=tag(name);if(v==null||!v.isEnabled())throw new AssertionError("Missing/disabled "+name);v.performClick();});}
  private void ready(){await(()->!a.operationInFlight()&&tag("user-search-submit")!=null&&tag("user-search-submit").isEnabled());}
  private void screenshot(String name)throws IOException{waitForIdleSync();try(var out=new FileOutputStream(new File(getTargetContext().getFilesDir(),name+".png"))){var bitmap=getUiAutomation().takeScreenshot();bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);bitmap.recycle();}}
  private FoodtimeRepository proxy(){return (FoodtimeRepository)Proxy.newProxyInstance(FoodtimeRepository.class.getClassLoader(),new Class<?>[]{FoodtimeRepository.class},(p,m,values)->{
    if(m.getName().equals("loadUserSearch")){reads.incrementAndGet();if(failNext.compareAndSet(true,false))throw new RepositoryException(503,"LOCAL_TEST_FAILURE","本地验收：暂时无法加载，请重试",0);}
    try{return m.invoke(real,values);}catch(InvocationTargetException failure){throw failure.getCause();}
  });}
  @Override public void onStart(){Bundle result=new Bundle();try{
    if(!BuildConfig.DEBUG||!BuildConfig.APPLICATION_ID.endsWith(".integration")||!BuildConfig.API_BASE_URL.equals("http://127.0.0.1:18080/api/v1/"))throw new AssertionError("Only isolated loopback build allowed");
    for(String key:new String[]{"userAccount","testPassword","query","targetId","targetName"})if(args.getString(key,"").isBlank())throw new AssertionError("Missing "+key);
    a=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    await(()->!connecting(a.root)&&!a.isPageLoading());
    if("portrait".equals(args.getString("layoutName"))&&a.getResources().getConfiguration().orientation!=android.content.res.Configuration.ORIENTATION_PORTRAIT) {
      ActivityMonitor monitor=addMonitor(MainActivity.class.getName(),null,false);
      ui(()->a.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
      android.app.Activity rotated=monitor.waitForActivityWithTimeout(12000);removeMonitor(monitor);
      if(!(rotated instanceof MainActivity))throw new AssertionError("Portrait recreation missing");a=(MainActivity)rotated;
    }
    await(()->!connecting(a.root)&&!a.isPageLoading());real=a.repo;
    if(real.currentUser()!=null&&!real.currentUser().email().equals(args.getString("userAccount")))throw new AssertionError("Clear only the isolated integration package before switching test actors");
    if(real.currentUser()==null)real.login(args.getString("userAccount"),args.getString("testPassword"));
    String id=args.getString("targetId");real.setFollow(id,false);
    ui(()->{a.repo=proxy();a.drafts.remove(a.draftKey("userSearchQuery"));a.openApp();});await(()->!a.isPageLoading());
    ui(()->a.go("user-search",""));await(()->input()!=null);
    check(reads.get()==0,"Empty page performs no search");
    EditText editor=input();ui(()->{input().setText("短");input().setText(args.getString("query"));});
    await(()->real.userSearchResults().page()==1&&tag("user-search-more").getVisibility()==View.VISIBLE);ready();
    check(reads.get()==1,"Debounce sends only final query");check(real.userSearchResults().total()==25,"Self excluded from 26 real users");
    check(real.userSearchResults().items().size()==20,"First page has twenty rows");check(input()==editor,"Search keeps editor View");
    ui(()->{
      View follow=tag("user-search-follow:"+id),chat=tag("user-search-chat:"+id);
      check(follow.getHeight()>=Ui.dp(a,48)&&chat.getHeight()>=Ui.dp(a,48),"Row actions have 48dp height");
      check(chat.getRight()<=((View)chat.getParent()).getWidth(),"Row actions fit current width");
    });screenshot("search-android-first");
    failNext.set(true);click("user-search-more");await(()->tag("user-search-error") instanceof TextView t&&!t.getText().toString().isEmpty());ready();
    check(real.userSearchResults().page()==1&&real.userSearchResults().items().size()==20,"Failed append keeps previous page");
    click("user-search-more");await(()->real.userSearchResults().page()==2);ready();
    check(real.userSearchResults().items().size()==25&&!real.userSearchResults().hasMore(),"Append reaches all twenty-five matches");
    View row=tag("user-search-row:"+id);int before=reads.get();click("user-search-follow:"+id);ready();
    check(tag("user-search-row:"+id)==row&&input()==editor,"Follow preserves row and editor");
    check(((Button)tag("user-search-follow:"+id)).getText().toString().equals("已关注"),"Follow changes relation flag");check(reads.get()==before,"Follow does not repeat search");
    click("user-search-chat:"+id);await(()->a.route().kind().equals("direct-chat")&&!a.operationInFlight());
    check(real.conversation(a.route().id()).other().id().equals(id),"Private chat opens the searched user");
    ui(a::back);await(()->input()!=null&&tag("user-search-follow:"+id)!=null);ready();
    check(input().getText().toString().equals(args.getString("query")),"Returning restores keyword");
    ui(()->input().setText(""));SystemClock.sleep(400);int blank=reads.get();
    check(((LinearLayout)tag("user-search-rows")).getChildCount()==0,"Clearing removes old results");
    ui(()->input().setText("完全不存在的昵称-"+args.getString("query")));await(()->real.userSearchResults().query().startsWith("完全不存在"));ready();
    check(real.userSearchResults().items().isEmpty(),"Real no-match empty state");
    check(reads.get()==blank+1,"Only non-empty new query requests data");screenshot("search-android-empty");
    result.putString("stream","User search real Backend/native UI PASS: "+checks+" checks\n");finish(-1,result);
  }catch(Throwable failure){result.putString("stream","User search FAIL: "+android.util.Log.getStackTraceString(failure)+"\n");finish(1,result);}finally{if(a!=null)ui(a::finish);}}
}
