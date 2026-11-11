package cn.edu.bjtu.foodtime.presentation;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import cn.edu.bjtu.foodtime.BuildConfig;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Read-only loopback fixture: verify cached covers before the first visible frame. */
public final class CommunityCoverCacheRunner extends Instrumentation {
  private MainActivity activity;
  private final List<String> failures = new ArrayList<>();
  private final List<String> evidence = new ArrayList<>();
  private int checks;
  private boolean stress;

  @Override public void onCreate(Bundle args) { super.onCreate(args); stress="true".equals(args.getString("stress")); start(); }
  private void ui(Runnable action) {
    AtomicReference<Throwable> failure = new AtomicReference<>();
    runOnMainSync(() -> { try { action.run(); } catch (Throwable error) { failure.set(error); } });
    if (failure.get() != null) throw new AssertionError(failure.get());
  }
  private void await(String label, BooleanSupplier condition) {
    long end = SystemClock.uptimeMillis() + 12000;
    while (SystemClock.uptimeMillis() < end) {
      boolean[] ready = {false}; ui(() -> ready[0] = condition.getAsBoolean());
      if (ready[0]) return;
      SystemClock.sleep(35);
    }
    throw new AssertionError("Timed out: " + label);
  }
  private void check(boolean valid, String label) {
    checks++; evidence.add((valid ? "PASS " : "FAIL ") + label);
    if (!valid) failures.add(label);
  }
  private static Object field(Object target, String name) {
    try { Class<?> type=target instanceof Class<?> value?value:target.getClass(); Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(target instanceof Class<?>?null:target); }
    catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
  }
  private static TextView button(View root, String text) {
    if (root instanceof TextView value && value.isClickable() && text.contentEquals(value.getText())) return value;
    if (root instanceof ViewGroup group) for (int i=0;i<group.getChildCount();i++) {
      TextView result=button(group.getChildAt(i),text); if(result!=null)return result;
    }
    return null;
  }
  private void section(String label, String route) {
    ui(() -> { TextView tab=button(activity.root,label); if(tab==null)throw new AssertionError("Missing tab "+label); tab.performClick(); });
    await(route, () -> activity.route().kind().equals(route) && !activity.isPageLoading());
    waitForIdleSync();
  }
  private void warm(List<String> paths) throws Exception {
    CountDownLatch done=new CountDownLatch(paths.size());
    List<Runnable> releases=new ArrayList<>();
    ui(() -> {
      try {
        var subscribe=ImageLoader.class.getDeclaredMethod("subscribe",MainActivity.class,String.class,int.class,Consumer.class);
        subscribe.setAccessible(true);
        for(String path:paths) releases.add((Runnable)subscribe.invoke(null,activity,path,480,(Consumer<Bitmap>)bitmap -> {
          if(bitmap==null)failures.add("Fixture thumbnail could not be decoded");
          done.countDown();
        }));
      } catch(ReflectiveOperationException failure) { throw new AssertionError(failure); }
    });
    if(!done.await(12,TimeUnit.SECONDS))throw new AssertionError("Warmup timed out");
    ui(() -> releases.forEach(Runnable::run));
  }
  private void collect(View root, List<ImageView> target) {
    if(root instanceof ImageView image && image.getContentDescription()!=null &&
        image.getContentDescription().toString().startsWith("缓存测试食记")) target.add(image);
    if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)collect(group.getChildAt(i),target);
  }
  private void visibleCovers(View root, String label) {
    List<ImageView> images=new ArrayList<>(); collect(root,images);
    int[] visible={0,0},missing={0,0};
    int[] origin=new int[2];root.getLocationOnScreen(origin);
    int midpoint=origin[0]+root.getWidth()/2;
    Rect rect=new Rect();
    for(ImageView image:images)if(image.getGlobalVisibleRect(rect)&&rect.width()>8&&rect.height()>8){
      int[] location=new int[2];image.getLocationOnScreen(location);
      int column=location[0]+image.getWidth()/2<midpoint?0:1;
      visible[column]++;if(image.getDrawable()==null)missing[column]++;
    }
    evidence.add(label+" visible="+visible[0]+","+visible[1]+" missing="+missing[0]+","+missing[1]);
    check(visible[0]+visible[1]>0&&missing[0]+missing[1]==0,label+" has no visible blank cached covers");
  }
  private void attachProbe(String path) { attachProbe(path,true,"Warm cached cover restored synchronously on off-screen attach"); }
  private void attachProbe(String path, boolean expected, String label) {
    ui(() -> {
      FrameLayout shell=new FrameLayout(activity);shell.setTranslationX(activity.root.getWidth()*2f);
      View cover=Ui.communityImage(activity,path,"缓存测试食记 attach",1);
      shell.addView(cover,new FrameLayout.LayoutParams(300,300));
      ViewGroup decor=(ViewGroup)activity.getWindow().getDecorView();
      decor.addView(shell,new ViewGroup.LayoutParams(300,300));
      check((((ImageView)((ViewGroup)cover).getChildAt(0)).getDrawable()!=null)==expected,label);
      decor.removeView(shell);
    });
  }
  private void snapshot(String name)throws Exception {
    SystemClock.sleep(45);
    Bitmap bitmap=getUiAutomation().takeScreenshot();
    if(bitmap!=null)try(var out=new FileOutputStream(new File(getTargetContext().getFilesDir(),name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}
  }
  private void probeSwipe(boolean left, String name)throws Exception {
    CommunityPager pager=(CommunityPager)field(activity,"communityPager");
    for(float amount:new float[]{.12f,.48f,.86f}){
      ui(() -> pager.move(pager.frame.getWidth()*amount*(left?-1:1)));
      waitForIdleSync();SystemClock.sleep(45);
      ui(() -> {
        CommunityPager.Page incoming=(CommunityPager.Page)field(pager,"incoming");
        if(incoming==null)throw new AssertionError("Incoming feed missing");
        visibleCovers(incoming.content(),name+" "+amount);
      });
      if(amount==.48f&&!name.startsWith("stress"))snapshot(name);
    }
    ui(() -> pager.end(pager.frame.getWidth()*.86f*(left?-1:1),true));
    await("swipe complete",() -> !(boolean)field(pager,"settling")&&!activity.isPageLoading());
  }

  private void firstFrame(Runnable navigation, String label)throws Exception {
    CountDownLatch first=new CountDownLatch(1);
    ui(() -> {
      navigation.run();
      View root=activity.root;
      root.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener(){
        @Override public boolean onPreDraw(){root.getViewTreeObserver().removeOnPreDrawListener(this);visibleCovers(activity.content,label);first.countDown();return true;}
      });
    });
    if(!first.await(5,TimeUnit.SECONDS))throw new AssertionError("No first frame: "+label);
    await(label+" idle",() -> !activity.isPageLoading());
  }

  private View visibleProbe(String path) {
    View[] result={null};
    ui(() -> {
      result[0]=Ui.communityImage(activity,path,"缓存测试食记 guarded",1);
      ((ViewGroup)activity.getWindow().getDecorView()).addView(result[0],new ViewGroup.LayoutParams(300,300));
    });
    return result[0];
  }
  private ImageView image(View cover){return (ImageView)((ViewGroup)cover).getChildAt(0);}
  private void remove(View view){ui(() -> ((ViewGroup)view.getParent()).removeView(view));}
  private String resource(int tail){return "/api/v1/media/00000000-0000-4000-8000-"+String.format(java.util.Locale.ROOT,"%012d",tail)+"?size=thumb";}

  private boolean allVisibleReady(View root) {
    List<ImageView> images=new ArrayList<>();collect(root,images);
    Rect rect=new Rect();int visible=0;
    for(ImageView image:images)if(image.getGlobalVisibleRect(rect)&&rect.width()>8&&rect.height()>8){visible++;if(image.getDrawable()==null)return false;}
    return visible>0;
  }
  private org.json.JSONObject fixture(String command)throws Exception {
    var connection=(java.net.HttpURLConnection)new java.net.URL("http://127.0.0.1:18085/fixture/"+command).openConnection();
    connection.setConnectTimeout(1000);connection.setReadTimeout(1000);
    try(var reader=new java.io.BufferedReader(new java.io.InputStreamReader(connection.getInputStream(),java.nio.charset.StandardCharsets.UTF_8))){return new org.json.JSONObject(reader.readLine()).getJSONObject("data");}
    finally{connection.disconnect();}
  }

  private void diskOnlySwipe()throws Exception {
    ui(ImageLoader::trim);
    CommunityPager pager=(CommunityPager)field(activity,"communityPager");
    ui(() -> pager.move(-pager.frame.getWidth()*.12f));
    await("disk-only first column while finger is held",() -> allVisibleReady(((CommunityPager.Page)field(pager,"incoming")).content()));
    ui(() -> pager.move(-pager.frame.getWidth()*.86f));
    await("disk-only newly exposed column while finger is held",() -> allVisibleReady(((CommunityPager.Page)field(pager,"incoming")).content()));
    ui(() -> {
      check(!(boolean)field(pager,"settling"),"Disk-only thumbnails finish while dragging, before release or settling");
      visibleCovers(((CommunityPager.Page)field(pager,"incoming")).content(),"disk-only held swipe");
      pager.end(-pager.frame.getWidth()*.86f,true);
    });
    await("disk-only settled",() -> !(boolean)field(pager,"settling")&&!activity.isPageLoading());
    section("发现","community");
  }

  private void strictChecks(List<String> paths)throws Exception {
    int bindings=((java.util.Map<?,?>)field(ImageLoader.class,"BINDINGS")).size();
    android.util.LruCache<?,?> memory=(android.util.LruCache<?,?>)field(ImageLoader.class,"MEMORY");
    int decoded=memory.putCount();
    for(int cycle=0;cycle<20;cycle++){
      probeSwipe(true,"stress-left-"+cycle);
      probeSwipe(false,"stress-right-"+cycle);
    }
    ui(() -> {
      check(memory.putCount()==decoded,"Forty warmed transitions do not decode the cached thumbnails again");
      check(((java.util.Map<?,?>)field(ImageLoader.class,"BINDINGS")).size()==bindings,"Forty transitions leave no detached image bindings");
    });
    diskOnlySwipe();
    for(int cycle=0;cycle<10;cycle++){
      ui(() -> activity.scroll.scrollTo(0,Ui.dp(activity,650)));
      waitForIdleSync();
      ui(() -> activity.selectTab("dining"));await("dining",() -> !activity.isPageLoading());
      firstFrame(() -> activity.selectTab("community"),"deep reentry "+cycle);
    }
    ui(() -> activity.go("community-detail",activity.repo.communityPosts().get(0).id()));
    await("detail",() -> activity.route().kind().equals("community-detail")&&!activity.isPageLoading());
    firstFrame(activity::back,"detail return first frame");
    ui(() -> callActivityOnPause(activity));
    attachProbe(paths.get(0),false,"Paused image pipeline does not restore a new off-screen view");
    ui(() -> callActivityOnResume(activity));
    attachProbe(paths.get(0),true,"Resume restores warmed covers synchronously");
    ui(() -> visibleCovers(activity.content,"resume visible columns"));

    warm(List.of(resource(96),resource(97),resource(98)));
    attachProbe(resource(97),false,"No-store preview never enters the decoded display cache");
    SystemClock.sleep(1700);
    attachProbe(resource(96),false,"Expired cached bytes do not bypass visibility or revalidation");
    View denied=visibleProbe(resource(98));
    ui(() -> check(image(denied).getDrawable()==null,"Expired image is not painted while permission is being checked"));
    await("permission rejection",() -> ((TextView)((ViewGroup)denied).getChildAt(1)).getText().toString().contains("失败"));
    ui(() -> check(image(denied).getDrawable()==null,"403 permission rejection leaves no stale bitmap"));
    remove(denied);

    String previous=activity.repo.imageIdentity(),previousActor=activity.repo.currentUser().id();
    activity.repo.login("cache-b@example.test","FixturePass123!");
    check(!previousActor.equals(activity.repo.currentUser().id()),"Read-only local fixture switches to a second authenticated account");
    check(!previous.equals(activity.repo.imageIdentity()),"Image identity changes for the new account and session");
    attachProbe(paths.get(0),false,"Another account cannot restore the previous decoded bitmap");

    View late=visibleProbe(resource(99));
    long deadline=SystemClock.uptimeMillis()+5000;
    while(!fixture("state").getBoolean("slowStarted")){
      if(SystemClock.uptimeMillis()>deadline)throw new AssertionError("Slow HTTP request did not reach the fixture");SystemClock.sleep(30);
    }
    ui(ImageLoader::clear);remove(late);fixture("release-slow");SystemClock.sleep(350);
    attachProbe(resource(99),false,"A late response after clear cannot repopulate the old display cache");
  }
  @Override public void onStart() {
    Bundle result=new Bundle();
    try {
      if(!BuildConfig.DEBUG||!BuildConfig.APPLICATION_ID.endsWith(".covercachecheck")||
          !BuildConfig.API_BASE_URL.equals("http://127.0.0.1:18085/api/v1/"))throw new AssertionError("Only isolated loopback build allowed");
      activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      await("fixture session",() -> activity.repo.currentUser()!=null&&!activity.isPageLoading());
      ui(() -> activity.selectTab("community"));
      await("discovery",() -> activity.route().kind().equals("community")&&!activity.isPageLoading()&&!activity.repo.communityPosts().isEmpty());
      List<String> paths=activity.repo.communityPosts().stream().map(post -> Ui.thumb(post.imageUrls().get(0))).toList();
      warm(paths);
      section("关注","community-following");section("发现","community");
      section("收藏","community-favorites");section("我的食记","community-mine");section("发现","community");
      attachProbe(paths.get(0));
      probeSwipe(true,"discovery-to-favorites");
      probeSwipe(false,"favorites-to-discovery");
      for(int repeat=0;repeat<3;repeat++){
        ui(() -> activity.selectTab("dining"));await("dining",() -> !activity.isPageLoading());
        CountDownLatch first=new CountDownLatch(1);
        int cycle=repeat;
        ui(() -> {
          activity.selectTab("community");
          activity.root.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener(){
            @Override public boolean onPreDraw(){activity.root.getViewTreeObserver().removeOnPreDrawListener(this);visibleCovers(activity.content,"reentry first frame "+cycle);first.countDown();return true;}
          });
        });
        if(!first.await(5,TimeUnit.SECONDS))throw new AssertionError("No reentry frame");
        await("community restored",() -> !activity.isPageLoading());
      }
      if(stress)strictChecks(paths);
      ui(ImageLoader::clear);
      ui(() -> {
        FrameLayout shell=new FrameLayout(activity);shell.setTranslationX(activity.root.getWidth()*2f);
        View cover=Ui.communityImage(activity,paths.get(0),"缓存测试食记 cleared",1);
        shell.addView(cover,new FrameLayout.LayoutParams(300,300));
        ViewGroup decor=(ViewGroup)activity.getWindow().getDecorView();decor.addView(shell,new ViewGroup.LayoutParams(300,300));
        check(((ImageView)((ViewGroup)cover).getChildAt(0)).getDrawable()==null,"Clearing the image session prevents stale off-screen restoration");
        decor.removeView(shell);
      });
      result.putString("stream",String.join("\n",evidence)+"\nCover cache "+(failures.isEmpty()?"PASS":"FAIL")+": "+checks+" checks; failures="+failures+"\n");
      finish(failures.isEmpty()?-1:1,result);
    }catch(Throwable failure){result.putString("stream",String.join("\n",evidence)+"\nCover cache ERROR: "+android.util.Log.getStackTraceString(failure));finish(1,result);}
    finally{if(activity!=null)ui(activity::finish);}
  }
}
