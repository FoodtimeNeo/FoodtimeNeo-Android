package cn.edu.bjtu.foodtime.presentation;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import cn.edu.bjtu.foodtime.BuildConfig;
import cn.edu.bjtu.foodtime.domain.repository.RepositoryException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Opt-in isolated acceptance against the real loopback backend; picker results use app-owned files. */
public final class FeedbackUiRunner extends Instrumentation {
  private MainActivity a;private Bundle args;private final List<String> checks=new ArrayList<>();
  @Override public void onCreate(Bundle args){super.onCreate(args);this.args=args;start();}
  private void ui(Runnable work){AtomicReference<Throwable> failure=new AtomicReference<>();runOnMainSync(()->{try{work.run();}catch(Throwable error){failure.set(error);}});if(failure.get()!=null)throw new AssertionError(failure.get());}
  private void await(String label,BooleanSupplier condition){long end=System.currentTimeMillis()+60000;while(System.currentTimeMillis()<end){boolean[] ready={false};ui(()->ready[0]=condition.getAsBoolean());if(ready[0])return;android.os.SystemClock.sleep(80);}throw new AssertionError("Timed out after emulator warm-up: "+label);}
  private void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks.add(label);try{java.nio.file.Files.write(new File(getTargetContext().getFilesDir(),"feedback-progress.txt").toPath(),String.join("\n",checks).getBytes(StandardCharsets.UTF_8));}catch(IOException failure){throw new AssertionError(failure);}}
  private View text(View root,String value){if(root instanceof TextView copy&&value.contentEquals(copy.getText()))return copy;if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=text(group.getChildAt(i),value);if(found!=null)return found;}return null;}
  private void click(String value){await(value,()->text(a.root,value)!=null);ui(()->text(a.root,value).performClick());}
  private EditText field(View root,String label){if(root instanceof EditText edit&&label.contentEquals(edit.getContentDescription()))return edit;if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){EditText found=field(group.getChildAt(i),label);if(found!=null)return found;}return null;}
  private void fill(String label,String value){ui(()->{EditText edit=field(a.root,label);if(edit==null)throw new AssertionError("Missing "+label);edit.setText(value);});}
  private void loaded(String route){await(route,()->a.route().kind().equals(route)&&!a.isPageLoading()&&!a.operationInFlight());}
  private void login(String email)throws Exception {if(a.repo.currentUser()!=null)a.repo.logout();a.repo.login(email,args.getString("password"));ui(a::restore);await("authenticated",()->a.repo.currentUser()!=null&&!a.isPageLoading()&&text(a.root,"正在连接…")==null);}
  private static String quote(String value){return "\""+value.replace("\"","\"\"")+"\"";}
  private static IntentFilter documentFilter(String action)throws IntentFilter.MalformedMimeTypeException {
    IntentFilter filter=new IntentFilter(action);filter.addCategory(Intent.CATEGORY_OPENABLE);filter.addDataType("*/*");return filter;
  }
  @Override public void onStart(){Bundle result=new Bundle();try{
    if(!BuildConfig.DEBUG||!BuildConfig.APPLICATION_ID.endsWith(".feedbackcheck")||!BuildConfig.API_BASE_URL.equals("http://127.0.0.1:18087/api/v1/"))throw new AssertionError("Isolated loopback build required");
    a=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    android.os.SystemClock.sleep(500);login(args.getString("user"));
    ui(()->a.selectTab("profile"));loaded("profile");click("账号");click("建议与反馈");loaded("feedback");
    await("feedback form",()->field(a.root,"反馈内容")!=null);check(text(a.root,"提交反馈")!=null,"Profile entry opens native feedback form");
    fill("反馈内容","模拟器真实反馈，包含逗号,与😊\n第二行");click("提交反馈");loaded("feedback-detail");
    String id=a.route().id();await("detail",()->text(a.root,"模拟器真实反馈，包含逗号,与😊\n第二行")!=null);
    check(!id.isBlank(),"Native submission receives a real server identifier");
    await("empty history",()->text(a.root,"暂无管理员回复")!=null);check(true,"User can inspect their feedback and reply state");
    try{a.repo.feedback(true,false,1);throw new AssertionError("User accessed admin API");}catch(RepositoryException denied){check(denied.status()==403,"User cannot use superadmin feedback operations");}
    login(args.getString("superadmin"));ui(()->a.selectTab("admin"));loaded("admin");click("建议与反馈管理");loaded("admin-feedback");
    ui(()->a.go("admin-feedback-detail",id));loaded("admin-feedback-detail");await("reply editor",()->field(a.root,"回复内容")!=null);
    fill("回复内容","管理员首次回复");click("保存回复与状态");await("first reply",()->text(a.root,"管理员首次回复")!=null&&field(a.root,"回复内容")!=null&&!a.operationInFlight());
    check(true,"Superadmin reply appears in native history");fill("回复内容","管理员第二次回复");
    click("标为已完成，移入归档并从下次导出中排除");click("保存回复与状态");await("completed",()->field(a.root,"回复内容")==null&&text(a.root,"管理员第二次回复")!=null&&!a.operationInFlight());
    check(true,"Completion archives feedback and disables further editing");
    login(args.getString("user"));ui(()->a.go("feedback-detail",id));loaded("feedback-detail");
    await("both replies",()->text(a.root,"管理员首次回复")!=null&&text(a.root,"管理员第二次回复")!=null);check(true,"User sees both historical replies after relogin");
    var csvFeedback=a.repo.submitFeedback("function","原生 CSV 导入测试",UUID.randomUUID().toString());
    login(args.getString("superadmin"));ui(()->a.go("admin-feedback",""));loaded("admin-feedback");
    File exported=new File(getTargetContext().getCacheDir(),"feedback-export.csv");
    var exportMonitor=addMonitor(documentFilter(Intent.ACTION_CREATE_DOCUMENT),new ActivityResult(Activity.RESULT_OK,new Intent().setData(Uri.fromFile(exported))),true);
    click("导出未完成反馈（CSV）");check(exportMonitor.getHits()>0,"Native export requests a CSV document through the system picker");await("CSV saved",()->exported.length()>0&&!a.operationInFlight());removeMonitor(exportMonitor);
    String saved=new String(java.nio.file.Files.readAllBytes(exported.toPath()),StandardCharsets.UTF_8);
    check(saved.startsWith("\uFEFF反馈编号,反馈类型,反馈内容"),"Native document export writes a real UTF-8 CSV with BOM");
    check(saved.contains(csvFeedback.id()),"Export contains the intended unfinished feedback identifier");
    check(!saved.contains(id),"Completed feedback is excluded from the next native export");
    File imported=new File(getTargetContext().getCacheDir(),"feedback-reply.csv");
    String csv="\uFEFF反馈编号,反馈类型,反馈内容,回复,处理状态\r\n"+quote(csvFeedback.id())+",功能反馈,原生 CSV 导入测试,"+quote("来自 CSV 的回复😊\n多行")+",已完成\r\n";
    java.nio.file.Files.write(imported.toPath(),csv.getBytes(StandardCharsets.UTF_8));
    var importMonitor=addMonitor(documentFilter(Intent.ACTION_OPEN_DOCUMENT),new ActivityResult(Activity.RESULT_OK,new Intent().setData(Uri.fromFile(imported))),true);
    click("导入回复与状态");check(importMonitor.getHits()>0,"Native import requests an openable document");
    await("import confirmation",()->a.feedbackDocuments.confirmation!=null&&a.feedbackDocuments.confirmation.isShowing()&&a.feedbackDocuments.confirmation.getButton(-1)!=null);
    ui(()->a.feedbackDocuments.confirmation.getButton(-1).performClick());await("CSV applied",()->!a.operationInFlight());
    long deadline=System.currentTimeMillis()+10000;boolean complete=false;
    while(System.currentTimeMillis()<deadline){if(a.repo.feedbackDetail(csvFeedback.id(),true).status().equals("completed")){complete=true;break;}Thread.sleep(80);}
    removeMonitor(importMonitor);check(complete,"Native file import maps the row and applies completion on the real server");
    check(a.repo.feedbackReplies(csvFeedback.id(),true,1).items().get(0).content().equals("来自 CSV 的回复😊\n多行"),"Native UTF-8 import preserves emoji and multiline replies");
    result.putString("stream",String.join("\n",checks)+"\nFeedback native PASS: "+checks.size()+" checks\n");finish(Activity.RESULT_OK,result);
  }catch(Throwable failure){result.putString("stream",String.join("\n",checks)+"\nFeedback native FAIL: "+failure+"\n"+android.util.Log.getStackTraceString(failure));finish(Activity.RESULT_CANCELED,result);}}
}
