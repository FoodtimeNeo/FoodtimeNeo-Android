package cn.edu.bjtu.foodtime.presentation;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.*;
import android.view.*;
import android.widget.*;
import cn.edu.bjtu.foodtime.BuildConfig;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Isolated loopback build only; exercises production widgets and the real HTTP repository. */
public final class CompactLayoutRunner extends Instrumentation {
  private MainActivity activity;private int checks;
  private static String id(int n){return String.format(java.util.Locale.ROOT,"10000000-0000-4000-8000-%012d",n);}
  private void ui(Runnable action){AtomicReference<Throwable> failure=new AtomicReference<>();runOnMainSync(()->{try{action.run();}catch(Throwable e){failure.set(e);}});if(failure.get()!=null)throw new AssertionError(failure.get());}
  private void await(String label,BooleanSupplier condition){long end=SystemClock.uptimeMillis()+20000;while(SystemClock.uptimeMillis()<end){boolean[] ready={false};ui(()->ready[0]=condition.getAsBoolean());if(ready[0])return;SystemClock.sleep(70);}throw new AssertionError("Timeout: "+label);}
  private void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
  private View tag(String value){return activity.root.findViewWithTag(value);}
  private void click(String tag){ui(()->{View view=tag(tag);if(view==null)throw new AssertionError("Missing "+tag);view.performClick();});}
  private void screenshot(String name)throws Exception{waitForIdleSync();Bitmap image=getUiAutomation().takeScreenshot();check(image!=null,"Screenshot available");try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),name+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();}
  private void profile()throws Exception{
    ui(()->activity.selectTab("profile"));await("profile",()->tag("profile-entry:favorite-dishes")!=null);
    ui(()->{
      View first=tag("profile-entry:favorite-dishes"),second=tag("profile-entry:community-favorites");Rect a=new Rect(),b=new Rect();first.getGlobalVisibleRect(a);second.getGlobalVisibleRect(b);
      check(first.getWidth()>0&&!Rect.intersects(a,b),"Profile entries do not overlap");
      check(a.top==b.top,"Common actions share a grid row");
      check(tag("profile-grid:常用") instanceof GridLayout saved&&saved.getChildCount()==4,"Web common actions are present in a grid");
      check(tag("profile-grid:我的记录") instanceof GridLayout records&&records.getChildCount()==5,"All record destinations remain");
      check(tag("profile-grid:服务") instanceof GridLayout services&&services.getChildCount()==4,"All service destinations remain");
      check(tag("profile-entry:history")!=null&&tag("profile-entry:community-create")!=null,"All four common shortcuts remain");
      check(tag("profile-following-count")!=null&&tag("profile-follower-count")!=null,"Following/follower routes retained");
    });
    await("profile totals",()->tag("profile-count:venue-mine") instanceof TextView text&&text.getText().toString().equals("43"));
    ui(()->{
      check(((TextView)tag("profile-count:favorite-dishes")).getText().toString().equals("31"),"Dish total counts unique favorites, not summed groups");
      check(((TextView)tag("profile-count:community-favorites")).getText().toString().equals("52"),"Post favorites use server total beyond first page");
      check(((TextView)tag("profile-count:community-mine")).getText().toString().equals("65"),"Own posts use server total beyond first page");
      check(((TextView)tag("profile-following-count")).getText().toString().equals("关注 12"),"Following refresh retains inline layout");
    });
    screenshot("profile-android");click("profile-entry:favorite-dishes");await("favorite destination",()->activity.route().kind().equals("favorite-dishes"));check(true,"Dish favorites entry navigates");
  }
  private void search()throws Exception{
    ui(()->activity.clearDraft(new MainActivity.Route("dish-search","")));
    String owner=activity.repo.currentUser().id();SearchHistoryStore.purge(getTargetContext(),owner);
    for(int i=0;i<12;i++)SearchHistoryStore.remember(getTargetContext(),owner,"测试"+i);
    check(SearchHistoryStore.read(getTargetContext(),owner).size()==10,"History limit is ten");
    check(SearchHistoryStore.read(getTargetContext(),"another-owner").isEmpty(),"History is isolated by account");
    SearchHistoryStore.remember(getTargetContext(),owner,"测试5");check(SearchHistoryStore.read(getTargetContext(),owner).get(0).equals("测试5"),"Duplicate moves to front");
    SearchHistoryStore.remove(getTargetContext(),owner,"测试5");check(!SearchHistoryStore.read(getTargetContext(),owner).contains("测试5"),"Single deletion works");
    ui(()->activity.go("dish-search",""));await("search",()->tag("dish-search-keyword")!=null&&tag("dish-search-keyword").isEnabled());
    await("daily search discovery",()->tag("search-discovery-card:"+id(100))!=null);
    ui(()->{
      check(tag("dish-search-results").getVisibility()==View.GONE,"Opening search shows discovery instead of fetching all results");
      check(tag("search-discovery-chip:"+id(100))!=null,"Daily stalls provide discovery chips");
      checkInputFits();
    });screenshot("search-landing-android");
    click("search-discovery-chip:"+id(100));await("discovery opens stall",()->activity.route().kind().equals("dishes")&&!activity.isPageLoading());ui(()->check(!activity.pageFailed(),"Discovery opens existing stall, not a mismatched dish keyword"));
    ui(activity::back);await("return to discovery",()->tag("search-discovery-card:"+id(100))!=null);
    ui(()->{EditText input=(EditText)tag("dish-search-keyword");input.setText("牛肉面");input.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);});
    await("search complete",()->tag("search-stall:"+id(100))!=null&&SearchHistoryStore.read(getTargetContext(),owner).get(0).equals("牛肉面"));
    ui(()->{check(tag("dish-search-results").getVisibility()==View.VISIBLE&&tag("dish-search-discovery").getVisibility()==View.GONE,"Submitting swaps discovery for actual results");checkInputFits();});
    check(true,"Keyboard search commits history");screenshot("search-android");
    ui(()->((EditText)tag("dish-search-keyword")).setTextSize(24));waitForIdleSync();ui(this::checkInputFits);screenshot("search-large-text-android");
    ui(()->((EditText)tag("dish-search-keyword")).setTextSize(16));
    click("search-tab:notes");await("unified note results",()->tag("community-post-card:"+id(8000))!=null);
    ui(()->check(tag("unified-search-toolbar")!=null,"Both result types share compact search toolbar"));
    screenshot("unified-search-notes");click("search-tab:dishes");await("return to dish groups",()->tag("search-stall:"+id(100))!=null);
    ui(()->{EditText input=(EditText)tag("dish-search-keyword");input.setText("");input.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);});
    await("empty keyword browse",()->tag("dish-search-keyword").isEnabled()&&tag("dish-search-results").getVisibility()==View.VISIBLE);check(true,"Explicit empty search still browses all dishes");
    await("browse group",()->tag("search-dish:"+id(1000))!=null);
    ui(()->{View first=tag("search-dish:"+id(1000)),fourth=tag("search-dish:"+id(1003));check(first.getWidth()>0&&fourth!=null,"Responsive row displays four dish cards");Rect visible=new Rect();check(fourth.getGlobalVisibleRect(visible)&&visible.width()==fourth.getWidth(),"Four complete cards fit inside page padding");check(tag("app-header-brand")==null,"Search does not retain oversized brand header");});
    await("visible search thumbnail decoded",()->{
      View card=tag("search-dish:"+id(1000));if(!(card instanceof LinearLayout))return false;
      View frame=((LinearLayout)card).getChildAt(0);return frame instanceof FrameLayout image&&image.getChildAt(0) instanceof ImageView bitmap&&bitmap.getDrawable()!=null;
    });
    screenshot("unified-search-four-cards");
    SearchHistoryStore.purge(getTargetContext(),owner);check(SearchHistoryStore.read(getTargetContext(),owner).isEmpty(),"Account purge removes keywords");
  }
  private void checkInputFits(){
    EditText input=(EditText)tag("dish-search-keyword");
    check(input.getLayout()!=null&&input.getLayout().getLineBottom(0)-input.getLayout().getLineTop(0)<=input.getHeight()-input.getCompoundPaddingTop()-input.getCompoundPaddingBottom(),"Search glyph line fits within actual input content height");
  }
  private EditText draftField(View view,String label){
    if(view instanceof EditText input&&label.contentEquals(input.getContentDescription()))return input;
    if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){EditText input=draftField(group.getChildAt(i),label);if(input!=null)return input;}
    return null;
  }
  private void dialog(String label)throws Exception{
    long end=System.currentTimeMillis()+5000;
    while(System.currentTimeMillis()<end){
      var root=getUiAutomation().getRootInActiveWindow();
      if(root!=null&&!root.findAccessibilityNodeInfosByText("Pixel Launcher isn't responding").isEmpty()){
        for(var node:root.findAccessibilityNodeInfosByText("Close app"))if(node.isClickable())node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);
        Thread.sleep(100);continue;
      }
      if(root!=null)for(var node:root.findAccessibilityNodeInfosByText(label))
        if(node.isClickable()&&label.contentEquals(node.getText())&&node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)){waitForIdleSync();return;}
      Thread.sleep(80);
    }
    throw new AssertionError("Missing dialog button "+label);
  }
  private void draftFirstOpen()throws Exception{
    String owner=activity.repo.currentUser().id();
    ui(()->{
      activity.selectTab("profile");
      activity.go("profile","");
      activity.clearDraft(new MainActivity.Route("community-create",""));
      PostDraftStore.save(activity,owner,"本机草稿标题","首次进入应恢复完整正文","",java.util.List.of("asset:hall_1.webp"));
    });
    await("draft profile entry",()->tag("profile-entry:community-create")!=null);
    click("profile-entry:community-create");
    await("first draft open",()->!activity.isPageLoading());
    ui(()->{
      check(!activity.pageFailed(),"First draft entry does not add an attached card twice");
      check(draftField(activity.root,"食记标题")!=null,"Composer exists on first entry without retry");
      check(draftField(activity.root,"食记标题").getText().toString().equals("本机草稿标题"),"Draft title restored on first entry");
      check(draftField(activity.root,"动态正文").getText().toString().equals("首次进入应恢复完整正文"),"Draft text restored on first entry");
      check(CommunityScreen.selectedPhotos(activity).equals(java.util.List.of("asset:hall_1.webp")),"Draft image remains associated");
    });screenshot("draft-first-open-android");
    ui(activity::redraw);
    ui(()->check(!activity.pageFailed()&&draftField(activity.root,"食记标题")!=null,"Draft redraw remains valid"));
    ui(()->draftField(activity.root,"食记标题").setText("本次不保存"));
    Thread.sleep(700);
    check(PostDraftStore.load(activity,owner).title().equals("本机草稿标题"),"Typing never overwrites committed draft");
    ui(activity::back);dialog("继续编辑");
    ui(()->check(draftField(activity.root,"食记标题").getText().toString().equals("本次不保存"),"Continue retains input"));
    ui(activity::home);dialog("不保存");
    await("discard leaves editor",()->activity.route().kind().equals("dining"));
    check(PostDraftStore.load(activity,owner).title().equals("本机草稿标题"),"Discard retains entry draft");
    ui(()->{activity.selectTab("profile");activity.go("community-create","");});
    await("draft reopened",()->draftField(activity.root,"食记标题")!=null);
    ui(()->{check(draftField(activity.root,"食记标题").getText().toString().equals("本机草稿标题"),"Reopening restores previous content");draftField(activity.root,"食记标题").setText("明确保存的新标题");activity.selectTab("dining");});
    dialog("保存草稿");
    await("save leaves editor",()->activity.route().kind().equals("dining"));
    check(PostDraftStore.load(activity,owner).title().equals("明确保存的新标题"),"Explicit save commits current content");
    ui(()->{activity.clearDraft(new MainActivity.Route("community-create",""));PostDraftStore.delete(activity,owner);activity.go("community-create","");});
    await("new editor",()->draftField(activity.root,"食记标题")!=null);
    ui(()->{draftField(activity.root,"食记标题").setText("不保留的新稿");activity.back();});dialog("不保存");
    check(PostDraftStore.load(activity,owner)==null,"Discarding new editor stores nothing");
    java.io.File photo=new java.io.File(activity.getFilesDir(),"photo-draft-regression.jpg");
    try(java.io.FileOutputStream out=new java.io.FileOutputStream(photo)){out.write(new byte[]{1,2,3});}
    String reference=photo.toURI().toString();
    check(PostDraftStore.save(activity,owner,"配图保护","正文","",java.util.List.of(reference)),"Draft metadata save succeeds");
    PhotoImport.discard(activity,reference);
    check(photo.exists(),"Discard cannot delete committed draft photo");
    PostDraftStore.delete(activity,owner);PhotoImport.discard(activity,reference);
    check(!photo.exists(),"Unreferenced temporary photo can be cleaned");
  }
  private void menu()throws Exception{
    ui(()->{activity.selectTab("dining");activity.go("dining","");});
    await("home hall card",()->tag("dining-hall:"+id(10))!=null&&!activity.isPageLoading());
    click("dining-hall:"+id(10));
    await("hall card opens menu",()->activity.route().kind().equals("stalls")&&tag("menu-dish:"+id(1070))!=null);
    check(true,"Actual home hall card opens linked stall/dish menu");
    ui(()->{
      check(tag("canteen-menu-rail")!=null&&tag("canteen-menu-dishes")!=null,"Independent menu panes exist");
      check(tag("menu-section:"+id(103))!=null,"Empty stall still has a selectable section");
      for(int i=0;i<8;i++)check(tag("menu-stall:"+id(100+i))!=null,"All stall buttons exist");
    });screenshot("menu-android");
    click("menu-stall:"+id(106));await("selected stall",()->tag("menu-stall:"+id(106)).isSelected());
    final int[] position={0};ui(()->position[0]=((ScrollView)tag("canteen-menu-dishes")).getScrollY());check(position[0]>0,"Stall click scrolls dishes");
    ui(()->((ScrollView)tag("canteen-menu-dishes")).fullScroll(View.FOCUS_UP));await("scroll sync",()->tag("menu-stall:"+id(100)).isSelected());check(true,"Manual dishes scroll updates stall rail");
    Rect touchArea=new Rect();ui(()->tag("canteen-menu-dishes").getGlobalVisibleRect(touchArea));
    long down=SystemClock.uptimeMillis();float x=touchArea.centerX(),from=touchArea.bottom-100,to=touchArea.top+100;
    sendPointerSync(MotionEvent.obtain(down,down,MotionEvent.ACTION_DOWN,x,from,0));
    for(int step=1;step<=16;step++){SystemClock.sleep(15);sendPointerSync(MotionEvent.obtain(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_MOVE,x,from+(to-from)*step/16,0));}
    sendPointerSync(MotionEvent.obtain(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,x,to,0));
    await("real touch scroll",()->((ScrollView)tag("canteen-menu-dishes")).getScrollY()>0);check(true,"Finger swipe scrolls right pane without parent stealing gesture");
    click("menu-stall:"+id(105));await("middle selected",()->tag("menu-stall:"+id(105)).isSelected());
    click("menu-dish:"+id(1050));await("dish detail",()->activity.route().kind().equals("detail")&&!activity.isPageLoading());check(true,"Menu dish opens existing detail");
    ui(activity::back);await("menu restored",()->tag("menu-dish:"+id(1070))!=null&&((ScrollView)tag("canteen-menu-dishes")).getScrollY()>0);check(true,"Returning restores right-pane position");
  }
  private void changeTheme(String mode)throws Exception{
    AtomicReference<MainActivity> created=new AtomicReference<>();
    android.app.Application app=(android.app.Application)getTargetContext().getApplicationContext();
    android.app.Application.ActivityLifecycleCallbacks callback=new android.app.Application.ActivityLifecycleCallbacks(){
      public void onActivityCreated(android.app.Activity a,Bundle state){if(a instanceof MainActivity next)created.set(next);}
      public void onActivityStarted(android.app.Activity a){} public void onActivityResumed(android.app.Activity a){}
      public void onActivityPaused(android.app.Activity a){} public void onActivityStopped(android.app.Activity a){}
      public void onActivitySaveInstanceState(android.app.Activity a,Bundle b){} public void onActivityDestroyed(android.app.Activity a){}
    };
    app.registerActivityLifecycleCallbacks(callback);
    ui(()->{AppTheme.save(activity,mode);activity.recreate();});
    await("theme recreation",()->created.get()!=null);
    activity=created.get();app.unregisterActivityLifecycleCallbacks(callback);
    await("theme content",()->activity.root!=null&&!activity.isPageLoading()&&activity.repo.currentUser()!=null);
    check(AppTheme.mode(activity).equals(mode),"Theme preference survives activity recreation");
  }
  private void theme()throws Exception{
    ui(()->{activity.selectTab("profile");activity.go("profile","");});
    await("appearance entry",()->tag("appearance-setting")!=null);
    int red=Design.RED;
    changeTheme(AppTheme.DARK);
    ui(()->{
      check(Design.PAPER==0xff141414&&Design.INK==0xfff5f2ed,"Dark palette uses dark paper and readable ink");
      check(Design.RED==red,"Red icon color stays unchanged");
      check(tag("appearance-setting")!=null,"Appearance entry remains available in dark mode");
      activity.scroll.fullScroll(View.FOCUS_DOWN);
      for(int icon:new int[]{cn.edu.bjtu.foodtime.R.drawable.ic_hub_lock,cn.edu.bjtu.foodtime.R.drawable.ic_hub_settings}){
        var drawable=activity.getDrawable(icon);var bitmap=Bitmap.createBitmap(48,48,Bitmap.Config.ARGB_8888);
        drawable.setBounds(0,0,48,48);drawable.draw(new android.graphics.Canvas(bitmap));int filled=0;
        for(int y=0;y<48;y++)for(int x=0;x<48;x++)if(android.graphics.Color.alpha(bitmap.getPixel(x,y))>128)filled++;
        check(filled>30&&filled<48*48/2,"Account icon renders an outline instead of a solid block");bitmap.recycle();
      }
    });screenshot("profile-dark-android");
    search();screenshot("search-dark-android");menu();screenshot("menu-dark-android");
    ui(()->activity.go("community-create",""));await("dark composer",()->draftField(activity.root,"食记标题")!=null);
    ui(()->draftField(activity.root,"食记标题").setText("主题切换保留输入"));
    changeTheme(AppTheme.LIGHT);
    ui(()->{
      check(draftField(activity.root,"食记标题").getText().toString().equals("主题切换保留输入"),"Changing theme does not lose unsaved input");
      check(Design.PAPER==0xfffffaf0&&Design.INK==0xff3a2418,"Light mode restores original palette");
      activity.back();
    });dialog("不保存");
    changeTheme(AppTheme.SYSTEM);
  }
  private String startupScenario="";
  private View splash(){return activity.getWindow().getDecorView().findViewWithTag("cold-start-splash");}
  private void startupScenario()throws Exception{
    if(startupScenario.equals("seed")){
      var cookies=new cn.edu.bjtu.foodtime.data.remote.EncryptedCookieStore(getTargetContext(),BuildConfig.API_BASE_URL);
      var cookie=new java.net.HttpCookie("FOODTIME_SESSION","isolated-startup-fixture");cookie.setPath("/");cookie.setMaxAge(3600);
      cookies.add(java.net.URI.create(BuildConfig.API_BASE_URL),cookie);check(true,"Seeded isolated encrypted fixture session");return;
    }
    long started=SystemClock.elapsedRealtime();
    var coveredOnCreate=new java.util.concurrent.atomic.AtomicBoolean();
    android.app.Application app=(android.app.Application)getTargetContext().getApplicationContext();
    android.app.Application.ActivityLifecycleCallbacks observer=new android.app.Application.ActivityLifecycleCallbacks(){
      public void onActivityCreated(android.app.Activity a,Bundle state){}
      public void onActivityStarted(android.app.Activity a){coveredOnCreate.set(a.getWindow().getDecorView().findViewWithTag("cold-start-splash")!=null);} public void onActivityResumed(android.app.Activity a){}
      public void onActivityPaused(android.app.Activity a){} public void onActivityStopped(android.app.Activity a){}
      public void onActivitySaveInstanceState(android.app.Activity a,Bundle b){} public void onActivityDestroyed(android.app.Activity a){}
    };
    app.registerActivityLifecycleCallbacks(observer);
    activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    app.unregisterActivityLifecycleCallbacks(observer);
    check(coveredOnCreate.get(),"Cold process starts with branded cover before instrumentation waits for idle");
    if(startupScenario.equals("abort")){
      ui(activity::finish);
      activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      ui(()->check(splash()==null,"Aborted cold start does not replay in a surviving process"));return;
    }
    if(startupScenario.equals("slow")){
      Thread.sleep(1700);ui(()->check(splash()!=null,"Slow content stays covered after the minimum"));
      screenshot("startup-slow-android");
    }
    await("startup release "+startupScenario,()->splash()==null);
    long elapsed=SystemClock.elapsedRealtime()-started;
    Bundle timing=new Bundle();timing.putString("stream","Startup "+startupScenario+" content visible after "+elapsed+" ms\n");sendStatus(0,timing);
    check(elapsed>=1500,"Fast startup never flashes below 1.5 seconds");
    if(startupScenario.equals("fail")||startupScenario.equals("timeout")){
      ui(()->check(activity.root.findViewWithTag("startup-session-status")!=null,"Startup error exposes retry page"));
      check(elapsed<19000,"Startup does not remain covered indefinitely");
      if(startupScenario.equals("timeout"))check(elapsed>=14500,"Timeout respects the 15 second boundary");
      screenshot("startup-"+startupScenario+"-android");
    }else{
      await("home content",()->tag("dining-hall:"+id(10))!=null&&!activity.isPageLoading());
      screenshot("startup-"+startupScenario+"-ready-android");
      ui(activity::finish);
      activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      ui(()->check(splash()==null,"Warm activity launch in the same process never repeats the cover"));
    }
  }
  @Override public void onCreate(Bundle args){super.onCreate(args);startupScenario=args==null?"":args.getString("startupScenario","");start();}
  @Override public void onStart(){Bundle result=new Bundle();try{
    if(!BuildConfig.DEBUG||!BuildConfig.APPLICATION_ID.endsWith(".layoutreview")||!BuildConfig.API_BASE_URL.equals("http://127.0.0.1:18530/api/v1/"))throw new AssertionError("Only isolated local build allowed");
    if(!startupScenario.isEmpty()){startupScenario();result.putString("stream","Compact layout PASS "+checks+" checks startup "+startupScenario+"\n");finish(-1,result);return;}
    activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    activity.repo.restoreSession();ui(activity::restore);
    await("fixture session",()->activity.repo.currentUser()!=null&&!activity.isPageLoading()&&splash()==null);
    profile();draftFirstOpen();search();menu();theme();result.putString("stream","Compact layout PASS "+checks+" checks\n");finish(-1,result);
  }catch(Throwable error){try{screenshot("failure-android");}catch(Exception ignored){}result.putString("stream","Compact layout FAIL "+android.util.Log.getStackTraceString(error));finish(1,result);}finally{if(activity!=null)ui(activity::finish);}}
}
