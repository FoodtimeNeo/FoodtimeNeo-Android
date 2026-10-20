package cn.edu.bjtu.foodtime.presentation;

import android.app.*;
import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import cn.edu.bjtu.foodtime.data.remote.*;
import cn.edu.bjtu.foodtime.domain.model.Models.*;
import cn.edu.bjtu.foodtime.domain.repository.*;
import java.io.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Real review acceptance; only an explicit .completion package connected to loopback may write. */
public final class ReviewInteractionsRunner extends Instrumentation {
  private static final String BASE=cn.edu.bjtu.foodtime.BuildConfig.API_BASE_URL;
  private MainActivity a;
  private FoodtimeRepository student;
  private RemoteRepository admin;
  private final String prefix="安卓核验"+UUID.randomUUID().toString().substring(0,6);
  private final List<String> passed=new ArrayList<>();
  private void check(boolean condition,String label) {if(!condition) throw new AssertionError(label);passed.add(label);Bundle status=new Bundle();status.putString("stream",label+"\n");sendStatus(1,status);}
  private void load(FoodtimeRepository r,ContentRequest.Query q,String id) {r.refresh(new ContentRequest(q,id));}
  private TextView find(View view,String label) {
    if(view.getVisibility()!=View.VISIBLE) return null;
    if(view instanceof TextView t&&t.getText().toString().equals(label)) return t;
    if(view instanceof ViewGroup group) for(int i=0;i<group.getChildCount();i++) {TextView found=find(group.getChildAt(i),label);if(found!=null) return found;}
    return null;
  }
  private View description(View view,String label) {
    if(view.getVisibility()!=View.VISIBLE) return null;
    if(label.contentEquals(view.getContentDescription()==null?"":view.getContentDescription())) return view;
    if(view instanceof ViewGroup group) for(int i=0;i<group.getChildCount();i++) {View found=description(group.getChildAt(i),label);if(found!=null) return found;}
    return null;
  }
  private EditText editor(View view) {
    if(view.getVisibility()!=View.VISIBLE) return null;
    if(view instanceof EditText e) return e;
    if(view instanceof ViewGroup group) for(int i=0;i<group.getChildCount();i++) {EditText found=editor(group.getChildAt(i));if(found!=null) return found;}
    return null;
  }
  private void dump(View view,StringBuilder out) {
    if(view.getVisibility()!=View.VISIBLE) return;
    if(view instanceof TextView t) out.append(t.getText()).append(" | ");
    if(view instanceof ViewGroup group) for(int i=0;i<group.getChildCount();i++) dump(group.getChildAt(i),out);
  }
  private void await(BooleanSupplier ready) throws Exception {
    long end=System.currentTimeMillis()+12000;
    while(System.currentTimeMillis()<end) {waitForIdleSync();if(ready.getAsBoolean()) return;Thread.sleep(50);}
    StringBuilder visible=new StringBuilder();runOnMainSync(() -> dump(a.root,visible));throw new AssertionError("UI timeout: "+visible);
  }
  private void click(String label) throws Exception {await(() -> find(a.root,label)!=null);runOnMainSync(() -> find(a.root,label).performClick());waitForIdleSync();}
  private void clickDescription(String label) throws Exception {await(() -> description(a.root,label)!=null);runOnMainSync(() -> description(a.root,label).performClick());waitForIdleSync();}
  private void enter(FoodtimeRepository r,String route,String id) throws Exception {
    await(() -> !a.operationInFlight());
    runOnMainSync(() -> {a.repo=r;a.drafts.clear();a.go(route,id);});
    await(() -> a.route().kind().equals(route)&&find(a.root,"加载中…")==null);
  }
  private void confirmDialog() throws Exception {
    long end=System.currentTimeMillis()+4000;
    while(System.currentTimeMillis()<end) {
      AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();
      if(root!=null) for(AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText("确认")) {
        if(node.getText()!=null&&node.getText().toString().equals("确认")&&node.isClickable()) {node.performAction(AccessibilityNodeInfo.ACTION_CLICK);return;}
      }
      Thread.sleep(50);
    }
    throw new AssertionError("Confirmation dialog missing");
  }
  private void approveUsingUi(String content) throws Exception {
    enter(admin,"admin-audit","");await(() -> find(a.root,content)!=null);
    check(find(a.root,"通过")!=null,"原生审核页包含待审内容与通过操作："+content);
    click("通过");confirmDialog();
    await(() -> !a.operationInFlight()&&admin.submissions(null).stream().noneMatch(s -> s.content().equals(content)&&s.status().equals("pending")));
  }
  private void picture(String name) throws Exception {
    runOnMainSync(() -> a.scroll.fullScroll(View.FOCUS_DOWN));waitForIdleSync();
    android.graphics.Bitmap bitmap=getUiAutomation().takeScreenshot();File dir=new File(getTargetContext().getFilesDir(),"review-audit");dir.mkdirs();
    try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))) {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally {bitmap.recycle();}
  }
  @Override public void onCreate(Bundle args) {super.onCreate(args);start();}
  @Override public void onStart() {
    Bundle report=new Bundle();
    try {
      if(!getTargetContext().getPackageName().endsWith(".completion")||!java.net.URI.create(BASE).getHost().equals("127.0.0.1")) throw new IllegalStateException("Use the isolated .completion package with an explicit loopback Backend");
      a=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      await(() -> a.repo!=null&&(find(a.root,"登录")!=null||find(a.root,"食堂")!=null));
      student=a.repo;student.login("20260001@bjtu.edu.cn","test1234");
      admin=new RemoteRepository(BASE,new HttpTransport(BASE));admin.login("20260002@bjtu.edu.cn","test1234");
      check(student.currentUser().role().equals("user")&&admin.currentUser().role().equals("admin"),"真实登录与角色识别");
      String stall="00000000-0000-4000-8000-000000000201";
      admin.editEntity("dishes","",stall,prefix,"真实联调专用", "12.30","",true);
      load(student,ContentRequest.Query.DISHES,stall);
      Dish dish=student.dishes(stall).stream().filter(d -> d.name().equals(prefix)).findFirst().orElseThrow();
      enter(student,"detail",dish.id());check(student.rating(dish.id()).reviewCount()==0&&student.rating(dish.id()).averageScore()==null,"无评论时均分为NULL");
      runOnMainSync(() -> a.go("review",dish.id()));await(() -> find(a.root,"提交评价")!=null);
      clickDescription("5星");runOnMainSync(() -> editor(a.root).setText(prefix+"主点评"));click("提交评价");
      await(() -> !a.operationInFlight()&&a.route().kind().equals("detail"));
      check(student.reviews(dish.id()).isEmpty(),"原生主点评提交后待审不公开");
      approveUsingUi(prefix+"主点评");
      enter(student,"detail",dish.id());await(() -> find(a.root,prefix+"主点评")!=null);
      Review root=student.reviews(dish.id()).stream().filter(r -> r.content().equals(prefix+"主点评")).findFirst().orElseThrow();
      check(student.rating(dish.id()).reviewCount()==1&&student.rating(dish.id()).averageScore().compareTo(new BigDecimal("5"))==0,"审核后菜品数量与均分立即更新");
      clickDescription("评论点赞，0个赞");await(() -> find(a.root,"已赞 1")!=null&&!a.operationInFlight());
      load(student,ContentRequest.Query.DISH_DETAIL,dish.id());check(student.reviews(dish.id()).get(0).likedByMe(),"原生评论点赞写入真实后端");
      clickDescription("取消评论点赞，1个赞");await(() -> find(a.root,"赞 0")!=null&&!a.operationInFlight());
      load(student,ContentRequest.Query.DISH_DETAIL,dish.id());check(!student.reviews(dish.id()).get(0).likedByMe(),"原生取消评论点赞持久化");
      click("回复 · 0");await(() -> student.hasLoadedReviewReplies(root.id())&&!a.operationInFlight());
      click("写下你的回复…");runOnMainSync(() -> editor(a.root).setText(prefix+"回复甲"));click("发布回复");
      await(() -> !a.operationInFlight()&&find(a.root,"写下你的回复…")!=null);
      load(student,ContentRequest.Query.MY_REVIEW_REPLIES,"");ReviewReply first=student.myReviewReplies().items().stream().filter(r -> r.content().equals(prefix+"回复甲")).findFirst().orElseThrow();
      check(first.status().equals("pending")&&student.reviewReplies(root.id()).items().isEmpty(),"回复真实提交、待审状态与公开隔离");
      enter(student,"mine-review-replies","");check(find(a.root,"待审核")!=null&&find(a.root,prefix+"回复甲")!=null,"我的回复显示审核进度");
      approveUsingUi(prefix+"回复甲");
      enter(student,"detail",dish.id());click("回复 · 1");await(() -> find(a.root,prefix+"回复甲")!=null);
      clickDescription("回复点赞，0个赞");await(() -> find(a.root,"已赞 1")!=null&&!a.operationInFlight());
      student.loadReviewReplies(root.id());check(student.reviewReplies(root.id()).items().get(0).likes().likedByMe(),"原生回复点赞持久化");
      clickDescription("取消回复点赞，1个赞");await(() -> find(a.root,"赞 0")!=null&&!a.operationInFlight());
      student.loadReviewReplies(root.id());check(!student.reviewReplies(root.id()).items().get(0).likes().likedByMe(),"原生取消回复点赞持久化");
      click("回复这条");runOnMainSync(() -> editor(a.root).setText(prefix+"回复乙"));click("发布回复");await(() -> !a.operationInFlight()&&find(a.root,"写下你的回复…")!=null);
      load(student,ContentRequest.Query.MY_REVIEW_REPLIES,"");ReviewReply child=student.myReviewReplies().items().stream().filter(r -> r.content().equals(prefix+"回复乙")).findFirst().orElseThrow();
      check(child.replyToId().equals(first.id()),"回复某条使用结构化对象");
      approveUsingUi(prefix+"回复乙");
      enter(student,"detail",dish.id());click("回复 · 2");await(() -> find(a.root,prefix+"回复乙")!=null);
      picture("01-real-replies");
      student.deleteReply(first.id());student.loadReviewReplies(root.id());
      check(student.reviewReplies(root.id()).total()==1&&student.reviewReplies(root.id()).items().get(0).replyToDeleted(),"删除被回复对象后子回复保留且显示占位");
      runOnMainSync(a::render);await(() -> find(a.root,prefix+"主点评")!=null);
      if(find(a.root,"回复 · 1")!=null) click("回复 · 1");await(() -> find(a.root,"回复 已删除的回复")!=null);check(find(a.root,prefix+"回复乙")!=null,"原生楼层渲染删除对象占位");
      load(student,ContentRequest.Query.DISH_DETAIL,dish.id());check(student.rating(dish.id()).reviewCount()==1&&student.rating(dish.id()).averageScore().intValue()==5,"回复和两类点赞不改变菜品评分");
      student.submitReply(root.id(),"",prefix+"驳回",UUID.randomUUID().toString());load(admin,ContentRequest.Query.MODERATION,"");
      Submission rejected=admin.submissions(null).stream().filter(s -> s.content().equals(prefix+"驳回")).findFirst().orElseThrow();admin.moderate(rejected.id(),"reject","核验驳回原因");
      enter(student,"mine-review-replies","");check(find(a.root,"已驳回")!=null&&find(a.root,"审核说明：核验驳回原因")!=null,"原生个人页显示驳回状态与原因");
      student.deleteReview(root.id());load(student,ContentRequest.Query.DISH_DETAIL,dish.id());
      check(student.rating(dish.id()).reviewCount()==0&&student.rating(dish.id()).averageScore()==null,"删除主点评立即恢复NULL均分和零计数");
      load(student,ContentRequest.Query.MY_REVIEW_REPLIES,"");check(student.myReviewReplies().items().stream().filter(r -> r.reviewId().equals(root.id())).allMatch(ReviewReply::reviewDeleted),"删楼后个人回复保留历史标记");
      try {student.loadReviewReplies(root.id());throw new AssertionError("Deleted thread exposed");}catch(RepositoryException expected) {check(expected.status()==404,"已删除整楼不可公开读取");}
      report.putString("stream","REAL Android review interactions PASS: "+passed.size()+" checks\n"+String.join("\n",passed));
      finish(0,report);
    } catch(Throwable failure) {
      StringWriter stack=new StringWriter();failure.printStackTrace(new PrintWriter(stack));report.putString("stream","REAL Android FAIL after "+passed.size()+" checks\n"+String.join("\n",passed)+"\n"+stack);finish(1,report);
    } finally {if(a!=null) runOnMainSync(a::finish);}
  }
}
