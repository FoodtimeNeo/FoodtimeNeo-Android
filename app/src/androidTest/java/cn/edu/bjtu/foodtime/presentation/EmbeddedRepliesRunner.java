package cn.edu.bjtu.foodtime.presentation;

import android.app.*;
import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import cn.edu.bjtu.foodtime.domain.model.Models.*;
import cn.edu.bjtu.foodtime.domain.repository.FoodtimeRepository;
import java.io.*;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/** Isolated native component behavior checks. No server content, accounts or passwords are changed. */
public final class EmbeddedRepliesRunner extends Instrumentation {
  private MainActivity activity;
  private int checks;
  private final AtomicInteger loads=new AtomicInteger(),likes=new AtomicInteger(),posts=new AtomicInteger();
  private volatile boolean loaded;
  private volatile long publicCount=1,cachedTotal=1;
  private volatile LikeState like=new LikeState(false,0,0);
  private final List<String> requests=Collections.synchronizedList(new ArrayList<>());
  private void check(boolean result,String label) {if(!result) throw new AssertionError(label);checks++;}
  private void await(BooleanSupplier ready) throws Exception {
    long end=System.currentTimeMillis()+8000;
    while(System.currentTimeMillis()<end) {waitForIdleSync();if(ready.getAsBoolean()) return;Thread.sleep(40);}
    StringBuilder visible=new StringBuilder();runOnMainSync(() -> dump(activity.root,visible));
    throw new AssertionError("Timed out waiting for UI operation: "+visible);
  }
  private void dump(View view,StringBuilder out) {
    if(view.getVisibility()!=View.VISIBLE) return;
    if(view instanceof TextView text) out.append(text.getText()).append(" | ");
    if(view instanceof ViewGroup group) for(int i=0;i<group.getChildCount();i++) dump(group.getChildAt(i),out);
  }
  private TextView find(View view,String value) {
    if(view.getVisibility()!=View.VISIBLE) return null;
    if(view instanceof TextView text&&text.getText().toString().equals(value)) return text;
    if(view instanceof ViewGroup group) for(int i=0;i<group.getChildCount();i++) {TextView found=find(group.getChildAt(i),value);if(found!=null) return found;}
    return null;
  }
  private EditText editor(View view) {
    if(view instanceof EditText field&&field.getVisibility()==View.VISIBLE) return field;
    if(view instanceof ViewGroup group&&view.getVisibility()==View.VISIBLE) for(int i=0;i<group.getChildCount();i++) {EditText found=editor(group.getChildAt(i));if(found!=null) return found;}
    return null;
  }
  private void click(String label) {runOnMainSync(() -> {TextView v=find(activity.root,label);if(v==null) throw new AssertionError("Missing "+label);v.performClick();});waitForIdleSync();}
  private View described(View view,String description) {
    if(description.contentEquals(view.getContentDescription()==null?"":view.getContentDescription())) return view;
    if(view instanceof ViewGroup group) for(int i=0;i<group.getChildCount();i++) {View found=described(group.getChildAt(i),description);if(found!=null) return found;}
    return null;
  }
  private ReplyPage page() {
    ReviewReply row=new ReviewReply("reply","root","deleted","回复作者","原回复内容","approved","",0,true,"",true,"dish","测试菜品",false,like);
    return new ReplyPage(loaded?List.of(row):List.of(),cachedTotal,false,"");
  }
  private FoodtimeRepository repository() {
    return (FoodtimeRepository)Proxy.newProxyInstance(FoodtimeRepository.class.getClassLoader(),new Class<?>[]{FoodtimeRepository.class},(proxy,method,args) -> switch(method.getName()) {
      case "currentUser" -> new User("user","20260001@bjtu.edu.cn","测试用户","user",true);
      case "restoreSession","refresh" -> null;
      case "hasLoadedReviewReplies" -> loaded;
      case "reviewReplies" -> page();
      case "loadReviewReplies" -> {loads.incrementAndGet();loaded=true;cachedTotal=publicCount;yield null;}
      case "setReplyLike" -> {likes.incrementAndGet();like=new LikeState((boolean)args[1],1,1);yield like;}
      case "setReviewLike" -> new LikeState(true,1,1);
      case "submitReply" -> {
        requests.add(args[1]+"|"+args[2]+"|"+args[3]);
        if(posts.incrementAndGet()==1) throw new IllegalStateException("测试连接失败");yield null;
      }
      case "dish" -> new Dish("dish","stall","测试菜品",new BigDecimal("10.00"),"",true);
      case "stall" -> new Stall("stall","hall","测试档口","",true);
      case "hall" -> new Hall("hall","测试食堂","",true);
      case "photos" -> List.of();
      case "rating" -> new RatingSummary(new BigDecimal("5.00"),1);
      case "reviews" -> List.of(new Review("root","dish","user","评论作者",5,"一级评论正文",0,0,publicCount,false,0));
      case "dishReviews" -> new ReviewPage(((FoodtimeRepository)proxy).reviews((String)args[0]),1,1,false);
      case "loadImage" -> null;
      default -> throw new AssertionError("Unexpected repository call "+method.getName());
    });
  }
  private void screenshot(String name) throws Exception {
    runOnMainSync(() -> activity.scroll.fullScroll(View.FOCUS_DOWN));waitForIdleSync();
    android.graphics.Bitmap bitmap=getUiAutomation().takeScreenshot();
    File dir=new File(getTargetContext().getFilesDir(),"embedded-reply-evidence");dir.mkdirs();
    try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))) {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally {bitmap.recycle();}
  }
  @Override public void onCreate(Bundle args) {super.onCreate(args);start();}
  @Override public void onStart() {
    Bundle report=new Bundle();Object app=getTargetContext().getApplicationContext();Field field=null;Object original=null;
    try {
      field=app.getClass().getDeclaredField("repository");field.setAccessible(true);original=field.get(app);field.set(app,repository());
      activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      await(() -> activity.root!=null);
      runOnMainSync(() -> {activity.repo=repository();activity.go("detail","dish");});
      field.set(app,activity.repo);
      await(() -> find(activity.root,"回复 · 1")!=null);
      check(loads.get()==0,"No thread fetch before expansion");check(editor(activity.root)==null,"No textarea before intent");
      click("回复 · 1");await(() -> find(activity.root,"原回复内容")!=null);
      check(loads.get()==1,"Only one page loaded");check(activity.route().kind().equals("detail"),"Expansion keeps dish route");
      check(find(activity.root,"回复 已删除的回复")!=null,"Deleted target remains a structured tombstone");
      click("回复这条");runOnMainSync(() -> editor(activity.root).setText("保留的草稿"));click("收起编辑");
      check(editor(activity.root)==null,"Editor collapses");click("继续编辑回复…");
      check(editor(activity.root).getText().toString().equals("保留的草稿"),"Draft survives editor collapse");
      check(find(activity.root,"回复 回复作者")!=null,"Reply target survives collapse");
      click("发布回复");await(() -> find(activity.root,"测试连接失败")!=null);
      check(editor(activity.root).isEnabled(),"Failure re-enables input");check(editor(activity.root).getText().toString().equals("保留的草稿"),"Failure keeps draft");
      click("发布回复");await(() -> find(activity.root,"写下你的回复…")!=null);
      check(requests.size()==2&&requests.get(0).equals(requests.get(1)),"Retry uses same content, target and id");
      check(find(activity.root,"保留的草稿")==null,"Pending content is not inserted into public floor");
      runOnMainSync(() -> described(activity.root,"回复点赞，0个赞").performClick());await(() -> likes.get()==1&&!activity.operationInFlight());
      check(loads.get()==1,"Like does not fetch thread");
      click("收起回复 · 1");click("回复 · 1");
      check(loads.get()==1&&find(activity.root,"已赞 1")!=null,"Collapse uses cached page and latest like");
      runOnMainSync(() -> {
        ReviewActionRow actions=new ReviewActionRow(activity);
        for(String label:List.of("已赞 999999","回复这条","更多操作")) {
          TextView button=ReviewActions.button(activity,label,() -> {});button.setTextSize(18.2f);actions.addView(button);
        }
        int width=Ui.dp(activity,248);
        actions.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        actions.layout(0,0,width,actions.getMeasuredHeight());
        check(actions.getMeasuredHeight()>Ui.dp(activity,48),"Narrow reply actions wrap with 1.3x text");
        for(int i=0;i<actions.getChildCount();i++) {View child=actions.getChildAt(i);check(child.getRight()<=width&&child.getHeight()>=Ui.dp(activity,48),"Actions fit width and preserve touch height");}
      });
      publicCount=2;runOnMainSync(activity::redraw);
      await(() -> loads.get()==2&&find(activity.root,"收起回复 · 2")!=null&&!activity.operationInFlight());
      check(cachedTotal==2,"A newer root reply count refreshes an outdated cached floor");
      screenshot("01-inline-thread");
      click("写下你的回复…");runOnMainSync(() -> editor(activity.root).setText("保存到状态的草稿"));
      Bundle state=new Bundle();runOnMainSync(() -> activity.onSaveInstanceState(state));
      check(state.getBundle("drafts").getString("embedded-replies:root:body").equals("保存到状态的草稿"),"Draft saved for Activity recreation");
      screenshot("02-inline-composer");
      runOnMainSync(activity::finish);activity=null;
      report.putString("stream","Embedded replies PASS: "+checks+" native UI checks; isolated repository, no Backend writes\n");finish(0,report);
    } catch(Throwable failure) {
      StringWriter stack=new StringWriter();failure.printStackTrace(new PrintWriter(stack));report.putString("stream","Embedded replies FAIL after "+checks+" checks\n"+stack);finish(1,report);
    } finally {
      if(activity!=null) runOnMainSync(activity::finish);
      if(field!=null) try {field.set(app,original);}catch(IllegalAccessException ignored) {}
    }
  }
}
