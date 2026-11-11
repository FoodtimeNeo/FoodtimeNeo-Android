package cn.edu.bjtu.foodtime.presentation;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import cn.edu.bjtu.foodtime.BuildConfig;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Opt-in acceptance with isolated accounts and a real loopback backend. */
public final class NoticeManagementRunner extends Instrumentation {
  private MainActivity a;private Bundle args;private final List<String> checks=new ArrayList<>();
  @Override public void onCreate(Bundle args){super.onCreate(args);this.args=args;for(String key:List.of("publication","display"))if(args.containsKey(key))args.putString(key,args.getString(key).replace('T',' '));start();}
  private void ui(Runnable work){AtomicReference<Throwable> failure=new AtomicReference<>();runOnMainSync(()->{try{work.run();}catch(Throwable error){failure.set(error);}});if(failure.get()!=null)throw new AssertionError(failure.get());}
  private void await(String label,BooleanSupplier condition){long end=System.currentTimeMillis()+60000;while(System.currentTimeMillis()<end){boolean[] ready={false};ui(()->ready[0]=condition.getAsBoolean());if(ready[0])return;android.os.SystemClock.sleep(80);}StringBuilder state=new StringBuilder();ui(()->{for(String key:List.of("foreground","contentReady","pageFailed","navigationLoading","stateSaved","updateCheckPending","releaseChecked","releaseChecking","loginNotices"))try{Field f=MainActivity.class.getDeclaredField(key);f.setAccessible(true);Object value=f.get(a);state.append(" ").append(key).append("=").append(value);if(value instanceof LoginNoticeController c)state.append(" pending=").append(c.pending).append(" error=").append(c.checkError);}catch(Exception error){throw new AssertionError(error);}state.append(" focus=").append(a.hasWindowFocus());});throw new AssertionError("Timed out: "+label+state);}
  private void check(boolean value,String label){if(!value)throw new AssertionError(label);checks.add(label);}
  private View text(View root,String value){if(root instanceof TextView t&&value.contentEquals(t.getText()))return root;if(root instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){View found=text(g.getChildAt(i),value);if(found!=null)return found;}return null;}
  private EditText field(View root,String label){if(root instanceof EditText edit&&label.contentEquals(edit.getContentDescription()))return edit;if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){EditText found=field(group.getChildAt(i),label);if(found!=null)return found;}return null;}
  private void click(String value){await(value,()->text(a.root,value)!=null);ui(()->text(a.root,value).performClick());}
  private void fill(String label,String value){ui(()->{EditText input=field(a.root,label);if(input==null)throw new AssertionError(label);input.setText(value);});}
  private void loaded(String route){await(route,()->a.route().kind().equals(route)&&!a.isPageLoading()&&!a.operationInFlight());}
  private LoginNoticeDialog popup(){try{Field field=MainActivity.class.getDeclaredField("loginNoticeDialog");field.setAccessible(true);return (LoginNoticeDialog)field.get(a);}catch(Exception error){throw new AssertionError(error);}}
  private void login(String email){if(a.repo.currentUser()!=null)a.repo.logout();a.repo.login(email,args.getString("password"));ui(a::restore);await("authenticated",()->!a.isPageLoading()&&text(a.root,"正在连接…")==null);}
  @Override public void onStart(){Bundle result=new Bundle();try{
    if(!BuildConfig.DEBUG||!BuildConfig.APPLICATION_ID.endsWith(".noticeschedulecheck")||!BuildConfig.API_BASE_URL.equals("http://127.0.0.1:18088/api/v1/"))throw new AssertionError("Isolated loopback build required");
    a=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    android.os.SystemClock.sleep(500);login(args.getString("user"));
    await("update popup",()->popup()!=null&&popup().dialog.isShowing());
    ui(()->check(text(popup().dialog.getWindow().getDecorView(),"更新公告")!=null,"Update announcement is shown first"));
    LoginNoticeDialog first=popup();ui(()->first.dialog.getButton(-1).performClick());
    await("ordinary popup",()->popup()!=null&&popup()!=first&&popup().dialog.isShowing());
    ui(()->check(text(popup().dialog.getWindow().getDecorView(),"系统公告")!=null,"Ordinary announcement follows after server revalidation"));
    ui(()->popup().dialog.getButton(-1).performClick());await("queue drained",()->popup()==null);check(true,"Both categories can be confirmed without a stale dialog");
    login(args.getString("superadmin"));ui(()->a.go("admin-notices",""));loaded("admin-notices");
    click("创建公告");loaded("admin-notice-create");await("schedule editor",()->field(a.root,"Markdown 内容")!=null);
    fill("Markdown 内容","# 原生排期验收\n\n北京时间独立启停");fill("发布时间（UTC+8）",args.getString("publication"));fill("开始显示时间（UTC+8）",args.getString("display"));
    click("这是更新公告");click("截止到指定日期结束");fill("截止日期（包含当天）",args.getString("deadline"));
    ui(()->{View footer=text(a.root,"保存草稿");int[] xy=new int[2];footer.getLocationOnScreen(xy);check(xy[1]>=0&&xy[1]+footer.getHeight()<=a.getResources().getDisplayMetrics().heightPixels,"Save controls remain in the viewport");});
    click("保存草稿");loaded("admin-notices");await("draft row",()->text(a.root,"原生排期验收")!=null);
    var draft=a.repo.adminNotices(1,"原生排期验收").items().get(0);check(!draft.schedule().published()&&!draft.schedule().enabled(),"Saving a draft never publishes or enables a popup");
    check(a.repo.currentNotices().stream().noneMatch(n->n.id().equals(draft.id())),"Draft is excluded from current announcements");
    click("设置发布计划");loaded("admin-notice-create");await("draft edit",()->field(a.root,"Markdown 内容")!=null);
    ui(()->check(field(a.root,"发布时间（UTC+8）").getText().toString().equals(args.getString("publication")),"Draft editing preserves UTC+8 publication time"));
    click("发布或定时发布");loaded("admin-notices");await("published row",()->text(a.root,"停用弹窗")!=null);
    var published=a.repo.adminNotice(draft.id());check(published.schedule().published()&&published.schedule().enabled()&&published.status().equals("scheduled"),"Publishing schedules the enabled announcement without early display");
    check(published.schedule().expiresOn().equals(args.getString("deadline")),"Date-only deadline survives the native and server round trip");
    click("停用弹窗");await("disabled row",()->!a.operationInFlight()&&text(a.root,"启用弹窗")!=null);
    var stopped=a.repo.adminNotice(draft.id());check(!stopped.schedule().enabled()&&stopped.schedule().publishedAt().equals(published.schedule().publishedAt())&&stopped.schedule().expiresOn().equals(published.schedule().expiresOn()),"Per-announcement disabling preserves publication and deadline metadata");
    click("启用弹窗");await("enabled row",()->!a.operationInFlight()&&text(a.root,"停用弹窗")!=null);check(a.repo.adminNotice(draft.id()).schedule().enabled(),"Announcement can be independently enabled again");
    result.putString("stream",String.join("\n",checks)+"\nNotice native PASS: "+checks.size()+" checks\n");finish(Activity.RESULT_OK,result);
  }catch(Throwable error){result.putString("stream",String.join("\n",checks)+"\nNotice native FAIL: "+android.util.Log.getStackTraceString(error));finish(Activity.RESULT_CANCELED,result);}}
}
