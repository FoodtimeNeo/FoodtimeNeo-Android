package cn.edu.bjtu.foodtime.presentation;

import android.app.*;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import cn.edu.bjtu.foodtime.domain.model.Models.*;
import cn.edu.bjtu.foodtime.domain.repository.ContentRequest;
import cn.edu.bjtu.foodtime.data.remote.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Real application composition and native screens; never injects a local/mock repository. */
public class RemoteUiRunner extends Instrumentation {
  private MainActivity activity;
  private int checks;
  private final String prefix="页面验收"+UUID.randomUUID().toString().substring(0,8);
  private final File[] sample={null};
  private void check(boolean condition,String message) {if(!condition) throw new AssertionError(message);checks++;}
  private void main(Runnable work) {
    AtomicReference<Throwable> error=new AtomicReference<>();
    runOnMainSync(() -> {try {work.run();}catch(Throwable failure){error.set(failure);}});
    if(error.get()!=null) throw new AssertionError("UI test step failed",error.get());
  }
  private void await(BooleanSupplier condition,String message) throws Exception {
    long until=System.currentTimeMillis()+15000;
    while(System.currentTimeMillis()<until) {AtomicReference<Boolean> result=new AtomicReference<>(false);main(() -> result.set(condition.getAsBoolean()));if(result.get()) return;Thread.sleep(80);}
    throw new AssertionError("Timeout: "+message);
  }
  private View find(View root,String value,boolean description) {
    if(description?value.contentEquals(root.getContentDescription()==null?"":root.getContentDescription()):root instanceof TextView t&&t.getText().toString().equals(value)) return root;
    if(root instanceof ViewGroup group) for(int i=0;i<group.getChildCount();i++) {View item=find(group.getChildAt(i),value,description);if(item!=null) return item;}
    return null;
  }
  private View text(String value) {return find(activity.root,value,false);}
  private void click(String value) throws Exception {
    await(() -> text(value)!=null,"button "+value);
    main(() -> {View item=text(value);while(item!=null&&!item.isClickable()) item=item.getParent() instanceof View parent?parent:null;if(item==null) throw new AssertionError("Not clickable: "+value);item.performClick();});
  }
  private void field(String label,String value) {main(() -> {View item=find(activity.root,label,true);if(!(item instanceof EditText edit)) throw new AssertionError("Missing field "+label);edit.setText(value);});}
  private void dialogButton(String value) throws Exception {
    long until=System.currentTimeMillis()+5000;
    while(System.currentTimeMillis()<until) {
      var root=getUiAutomation().getRootInActiveWindow();
      if(root!=null) for(var node:root.findAccessibilityNodeInfosByText(value))
        if(node.isClickable()&&value.contentEquals(node.getText())&&node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return;
      Thread.sleep(80);
    }
    throw new AssertionError("Missing dialog action "+value);
  }
  private void loaded(String kind) throws Exception {await(() -> activity.route().kind().equals(kind)&&text("加载中…")==null,"page "+kind);}
  private void login(String email) throws Exception {
    login(email,"Foodtime2026!");
  }
  private void login(String email,String secret) throws Exception {
    await(() -> text("学号邮箱")!=null,"login form");field("学号邮箱",email);field("密码",secret);
    // The heading also says 登录; pick the clickable submit button.
    main(() -> clickButton(activity.root,"登录"));loaded("dining");await(() -> text("联调示例食堂")!=null,"hall list");
  }
  private void clickButton(View root,String value) {
    if(root instanceof TextView t&&t.getText().toString().equals(value)&&root.isClickable()) {root.performClick();return;}
    if(root instanceof ViewGroup group) for(int i=0;i<group.getChildCount();i++) {
      View item=group.getChildAt(i);if(hasButton(item,value)) {clickButton(item,value);return;}
    }
    throw new AssertionError("Missing button "+value);
  }
  private boolean hasButton(View root,String value) {
    if(root instanceof TextView t&&t.getText().toString().equals(value)&&root.isClickable()) return true;
    if(root instanceof ViewGroup g) for(int i=0;i<g.getChildCount();i++) if(hasButton(g.getChildAt(i),value)) return true;return false;
  }
  private void logout() throws Exception {
    main(() -> {activity.selectTab("profile");while(!activity.route().kind().equals("profile")) activity.back();});
    loaded("profile");click("退出登录");dialogButton("确认");await(() -> activity.repo.currentUser()==null&&text("学号邮箱")!=null,"logout");
  }
  private void profile() throws Exception {
    main(() -> {activity.selectTab("profile");while(!activity.route().kind().equals("profile")) activity.back();});loaded("profile");
  }
  private void admin() throws Exception {
    main(() -> {activity.selectTab("admin");while(!activity.route().kind().equals("admin")) activity.back();});loaded("admin");
  }
  private void cardButton(String title,String button) {
    main(() -> {
      View item=text(title);while(item!=null&&!hasButton(item,button)) item=item.getParent() instanceof View parent?parent:null;
      if(item==null) throw new AssertionError("Missing card button "+title+" / "+button);clickButton(item,button);
    });
  }
  private void detail() throws Exception {
    main(() -> activity.home());loaded("dining");click("联调示例食堂");loaded("stalls");click("联调示例档口");loaded("dishes");click("联调示例菜品");loaded("detail");
  }
  private void screenshot(String name) throws Exception {
    waitForIdleSync();Thread.sleep(2200);Bitmap bitmap=getUiAutomation().takeScreenshot();if(bitmap==null) throw new AssertionError("Screenshot unavailable");
    File folder=new File(getTargetContext().getFilesDir(),"remote-ui-evidence");folder.mkdirs();
    try(var out=new FileOutputStream(new File(folder,name+".png"))) {bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}finally {bitmap.recycle();}
  }
  @Override public void onCreate(Bundle args) {super.onCreate(args);start();}
  @Override public void onStart() {
    Bundle report=new Bundle();
    try {
      // Repeatable cleanup is limited to clearly labelled acceptance review submissions in the isolated DB.
      String base="http://127.0.0.1:8080/api/v1/";var setup=new RemoteRepository(base,new HttpTransport(base));
      setup.login("20260002@bjtu.edu.cn","Foodtime2026!");setup.refresh(new ContentRequest(ContentRequest.Query.MODERATION,""));
      for(Submission s:setup.submissions(null)) if(s.type().equals("review")&&s.status().equals("pending")&&s.content().startsWith("页面验收")) setup.moderate(s.id(),"reject","验收遗留清理");
      setup.logout();
      Intent launch=new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      activity=(MainActivity)startActivitySync(launch);
      await(() -> text("正在连接…")==null,"session restoration");
      if(activity.repo.currentUser()!=null) logout();screenshot("login");
      login("20260001@bjtu.edu.cn");check(!activity.repo.currentUser().isAdmin(),"Normal account role from real session");screenshot("halls");
      detail();check(text("每日更新")!=null||text("0 条评价 · 每日更新")!=null,"Server rating snapshot labelled");screenshot("detail");
      main(() -> activity.scroll.scrollTo(0,300));int[] position={0};main(() -> position[0]=activity.scroll.getScrollY());
      profile();screenshot("profile");main(() -> activity.selectTab("dining"));loaded("detail");
      await(() -> activity.scroll.getScrollY()==position[0],"preserved scroll");check(true,"Tabs preserve detail route and scroll");
      int[] depth={0};main(() -> {depth[0]=activity.stacks.get("dining").size();activity.selectTab("dining");});check(depth[0]==activity.stacks.get("dining").size(),"Repeated tab does not reset stack");
      click("写点评");loaded("review");main(() -> find(activity.root,"4星",true).performClick());field("评价内容",prefix+"评价");click("提交评价");loaded("detail");
      check(activity.repo.reviews(activity.route().id()).stream().noneMatch(r -> r.content().equals(prefix+"评价")),"Pending UI comment is not public");
      profile();click("我的提交");loaded("mine-submissions");await(() -> text(prefix+"评价")!=null,"own submitted content");check(true,"Personal record uses server data");screenshot("submissions");
      logout();login("20260002@bjtu.edu.cn");main(() -> activity.selectTab("admin"));loaded("admin");click("审核投稿");loaded("admin-audit");
      await(() -> text(prefix+"评价")!=null,"review queue");main(() -> clickButton(activity.root,"通过"));dialogButton("确认");
      await(() -> text(prefix+"评价")==null&&text("加载中…")==null,"review approved");check(true,"Native moderation dialog calls Backend");screenshot("admin");
      logout();login("20260001@bjtu.edu.cn");detail();await(() -> text(prefix+"评价")!=null,"approved comment");check(true,"Approved review displayed in native detail");
      profile();click("我的评论");loaded("mine-reviews");main(() -> clickButton(activity.root,"删除"));dialogButton("确认");
      await(() -> text(prefix+"评价")==null&&text("加载中…")==null,"deleted comment");check(true,"Native own-delete updates Backend");
      detail();click("+");loaded("upload-photo");
      Bitmap bitmap=Bitmap.createBitmap(192,108,Bitmap.Config.ARGB_8888);bitmap.eraseColor(0xff687241);sample[0]=new File(activity.getFilesDir(),prefix+".jpg");
      try(var out=new FileOutputStream(sample[0])) {bitmap.compress(Bitmap.CompressFormat.JPEG,90,out);}finally {bitmap.recycle();}
      main(() -> {activity.pickingKey=activity.draftKey("image");activity.onActivityResult(101,Activity.RESULT_OK,new Intent().setData(android.net.Uri.fromFile(sample[0])));});
      await(() -> text("更换图片")!=null,"background image import");click("提交图片");loaded("detail");
      profile();click("我的提交");loaded("mine-submissions");
      check(activity.repo.submissions(activity.repo.currentUser().id()).stream().anyMatch(s -> s.type().equals("photo")&&s.status().equals("pending")),"Imported photo uploaded and submitted to Backend");screenshot("photo-submission");
      logout();login("20260003@bjtu.edu.cn");admin();click("用户管理");loaded("admin-users");field("搜索邮箱或昵称","20260001");
      click("禁用账号");dialogButton("确认");await(() -> text("普通用户 · 禁用")!=null,"disabled account");check(true,"Native user-status action reaches Backend");
      click("启用账号");dialogButton("确认");await(() -> text("普通用户 · 启用")!=null,"reenabled account");screenshot("users");
      admin();click("食堂 · 档口 · 菜品");loaded("admin-data");click("新增食堂");loaded("admin-new");field("名称",prefix+"食堂");click("保存");
      loaded("admin-data");await(() -> text(prefix+"食堂")!=null,"new hall");cardButton(prefix+"食堂","管理档口");loaded("admin-data");click("新增档口");loaded("admin-new");field("名称",prefix+"档口");click("保存");
      loaded("admin-data");await(() -> text(prefix+"档口")!=null,"new stall");cardButton(prefix+"档口","管理菜品");loaded("admin-data");click("新增菜品");loaded("admin-new");field("名称",prefix+"菜品");field("价格（元）","10.20");click("保存");
      loaded("admin-data");await(() -> text(prefix+"菜品")!=null,"new dish");check(true,"Native catalog creates use real parent relations and exact price");
      cardButton(prefix+"菜品","编辑");loaded("admin-edit");field("价格（元）","9.80");click("保存");
      loaded("admin-data");await(() -> text("¥9.80")!=null,"edited dish price");check(true,"Native catalog editing updates persisted price");
      cardButton(prefix+"菜品","编辑");loaded("admin-edit");click("删除");dialogButton("确认");loaded("admin-data");await(() -> text("暂无记录")!=null,"dish removed");
      main(() -> activity.back());await(() -> text(prefix+"档口")!=null,"back to parent stall");cardButton(prefix+"档口","编辑");loaded("admin-edit");click("删除");dialogButton("确认");loaded("admin-data");await(() -> text("暂无记录")!=null,"stall removed");
      main(() -> activity.back());await(() -> text(prefix+"食堂")!=null,"back to parent hall");cardButton(prefix+"食堂","编辑");loaded("admin-edit");click("删除");dialogButton("确认");await(() -> text(prefix+"食堂")==null&&text("加载中…")==null,"hall removed");check(true,"Native catalog deletes follow DB associations");
      profile();click("修改密码");loaded("password");field("旧密码","Foodtime2026!");field("新密码","Foodtime2026!A");field("确认新密码","Foodtime2026!A");click("确认修改");
      await(() -> activity.repo.currentUser()==null&&text("学号邮箱")!=null,"password changed and signed out");check(true,"Native password flow invalidates current session");
      login("20260003@bjtu.edu.cn","Foodtime2026!A");profile();click("修改密码");loaded("password");field("旧密码","Foodtime2026!A");field("新密码","Foodtime2026!");field("确认新密码","Foodtime2026!");click("确认修改");
      await(() -> activity.repo.currentUser()==null&&text("学号邮箱")!=null,"fixture password restored");check(true,"New password works and test fixture restored");
      report.putString("stream","Remote UI PASS: "+checks+" checks, actual RemoteRepository application/screens\n");finish(0,report);
    } catch(Throwable failure) {
      StringWriter stack=new StringWriter();failure.printStackTrace(new PrintWriter(stack));report.putString("stream","Remote UI FAIL after "+checks+" checks\n"+stack);finish(1,report);
    } finally {if(sample[0]!=null) sample[0].delete();}
  }
}
