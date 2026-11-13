package cn.edu.bjtu.foodtime.presentation;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import cn.edu.bjtu.foodtime.BuildConfig;
import cn.edu.bjtu.foodtime.data.remote.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Only operates on an isolated package, owned accounts, loopback API and captured local SMTP. */
public final class AccountLifecycleRunner extends Instrumentation {
  private MainActivity a;private Bundle args;private final List<String> checks=new ArrayList<>();
  @Override public void onCreate(Bundle args){super.onCreate(args);this.args=args;start();}
  private void ui(Runnable work){AtomicReference<Throwable> failure=new AtomicReference<>();runOnMainSync(()->{try{work.run();}catch(Throwable error){failure.set(error);}});if(failure.get()!=null)throw new AssertionError(failure.get());}
  private void await(String label,BooleanSupplier condition){long until=System.currentTimeMillis()+60000;while(System.currentTimeMillis()<until){boolean[] ready={false};ui(()->ready[0]=condition.getAsBoolean());if(ready[0])return;android.os.SystemClock.sleep(80);}throw new AssertionError("Timed out: "+label);}
  private void check(boolean condition,String label){if(!condition)throw new AssertionError(label);checks.add(label);try{java.nio.file.Files.write(new java.io.File(getTargetContext().getFilesDir(),"account-progress.txt").toPath(),String.join("\n",checks).getBytes(StandardCharsets.UTF_8));}catch(Exception error){throw new AssertionError(error);}}
  private View text(View root,String value){if(root instanceof TextView t&&value.contentEquals(t.getText()))return root;if(root instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){View found=text(g.getChildAt(i),value);if(found!=null)return found;}return null;}
  private EditText field(View root,String label){if(root instanceof EditText edit&&label.contentEquals(edit.getContentDescription()))return edit;if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){EditText found=field(group.getChildAt(i),label);if(found!=null)return found;}return null;}
  private View button(View root,String label){if(root instanceof TextView text&&label.contentEquals(text.getText())&&root.isClickable())return root;if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=button(group.getChildAt(i),label);if(found!=null)return found;}return null;}
  private void click(String value){await(value,()->text(a.root,value)!=null);ui(()->text(a.root,value).performClick());}
  private View tag(String value){return a.root.findViewWithTag(value);}
  private void clickTag(String value){await(value,()->tag(value)!=null&&tag(value).isEnabled());ui(()->tag(value).performClick());}
  private void loaded(String route){await(route,()->a.route().kind().equals(route)&&!a.isPageLoading()&&!a.operationInFlight());}
  private String code(String email)throws Exception {
    HttpURLConnection connection=(HttpURLConnection)URI.create("http://127.0.0.1:18090/code?email="+URLEncoder.encode(email,"UTF-8")).toURL().openConnection();
    connection.setConnectTimeout(5000);connection.setReadTimeout(5000);
    try{if(connection.getResponseCode()!=200)throw new AssertionError("Owned SMTP did not capture code");java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[128];try(var stream=connection.getInputStream()){int count;while((count=stream.read(buffer))!=-1)bytes.write(buffer,0,count);}String value=new String(bytes.toByteArray(),StandardCharsets.UTF_8);if(!value.matches("[0-9]{6}"))throw new AssertionError("Invalid local SMTP capture");return value;}finally{connection.disconnect();}
  }
  private void fillCode(boolean delete,String value){ui(()->((EditText)tag(delete?"deletion-verification-code":"unbind-verification-code")).setText(value));}
  private void acknowledge(boolean delete){click(delete?"我已知晓注销不可撤销、新账号不继承旧数据及邮箱 1 小时冷却规则":"我已知晓解绑会移除学校邮箱认证及学校邮箱 1 小时冷却规则");}
  private void confirm(boolean yes){await("confirmation",()->a.accountActionConfirmation!=null&&a.accountActionConfirmation.isShowing());ui(()->a.accountActionConfirmation.getButton(yes?-1:-2).performClick());}
  private boolean contains(View root,String value){if(root instanceof TextView text&&text.getText().toString().contains(value))return true;if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)if(contains(group.getChildAt(i),value))return true;return false;}
  @Override public void onStart(){Bundle result=new Bundle();try{
    if(!BuildConfig.DEBUG||!BuildConfig.APPLICATION_ID.endsWith(".accountlifecyclecheck")||!BuildConfig.API_BASE_URL.equals("http://127.0.0.1:18089/api/v1/"))throw new AssertionError("Isolated loopback build required");
    if(new HttpTransport(BuildConfig.API_BASE_URL).request("GET","system/ping",null,null).status()!=200)throw new AssertionError("Loopback bridge is unavailable");
    // A bounded Activity monitor avoids Instrumentation's unbounded idle wait on a busy emulator.
    ActivityMonitor monitor=addMonitor(MainActivity.class.getName(),null,false);
    getTargetContext().startActivity(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    Activity launched=waitForMonitorWithTimeout(monitor,90000);removeMonitor(monitor);
    if(!(launched instanceof MainActivity))throw new AssertionError("Activity creation timed out");a=(MainActivity)launched;
    // Follow the real form/serial worker, after initial session restoration has finished.
    await("login form",()->field(a.root,"邮箱")!=null&&field(a.root,"密码")!=null);
    ui(()->{field(a.root,"邮箱").setText(args.getString("user"));field(a.root,"密码").setText(args.getString("password"));button(a.root,"登录").performClick();});
    await("login",()->a.repo.currentUser()!=null&&!a.isPageLoading()&&!a.operationInFlight()&&text(a.root,"正在连接…")==null);
    RemoteRepository other=new RemoteRepository(BuildConfig.API_BASE_URL,new HttpTransport(BuildConfig.API_BASE_URL));other.login(args.getString("user"),args.getString("password"));
    ui(()->a.selectTab("profile"));loaded("profile");click("账号");click("账号安全与注销");loaded("account-lifecycle");
    await("email actions",()->tag("unbind-code-send")!=null);check(contains(a.root,"每天只能成功发送一次")&&contains(a.root,"1 小时"),"Native account page explains daily sending, irreversible deletion and one-hour cooldown");
    check(a.repo.accountLifecycle().verificationEmail().equals(args.getString("school")),"Bound account verifies its current school email");
    clickTag("unbind-code-send");await("unbind code sent",()->!a.operationInFlight()&&contains(a.root,"验证码已发送到当前学校邮箱"));String unbind=code(args.getString("school"));check(true,"Real native UI sends unbinding code through captured SMTP");
    fillCode(false,unbind.equals("000000")?"999999":"000000");acknowledge(false);clickTag("unbind-school-submit");confirm(true);await("wrong proof rejected",()->!a.operationInFlight()&&contains(a.root,"验证码错误"));check(a.repo.currentUser().role().equals("user"),"Incorrect code does not downgrade or remove the account");
    fillCode(false,unbind);clickTag("unbind-school-submit");confirm(false);check(a.repo.currentUser().role().equals("user"),"Canceling the final confirmation leaves school binding intact");
    clickTag("unbind-school-submit");confirm(true);await("visitor identity",()->a.repo.currentUser()!=null&&a.repo.currentUser().role().equals("visitor")&&!a.operationInFlight());check(true,"Unbinding preserves the account and immediately restores visitor identity");
    check(a.repo.accountLifecycle().schoolEmail().isBlank(),"School email is absent after unbinding");
    try{a.repo.sendSchoolEmailCode(args.getString("school"));throw new AssertionError("School cooling down was bypassed");}catch(cn.edu.bjtu.foodtime.domain.repository.RepositoryException denied){check(denied.status()==409,"Immediate rebinding is blocked by the real backend cooldown");}
    ui(()->a.go("account-lifecycle",""));loaded("account-lifecycle");await("visitor action",()->tag("deletion-code-send")!=null);
    check(a.repo.accountLifecycle().verificationEmail().equals(args.getString("user")),"Visitor deletion verifies the registration email");
    clickTag("deletion-code-send");await("deletion code sent",()->!a.operationInFlight()&&contains(a.root,"今天不能再次发送"));check(!tag("deletion-code-send").isEnabled(),"Successful deletion code disables repeat sending for the day");
    String deletion=code(args.getString("user"));fillCode(true,deletion);acknowledge(true);clickTag("delete-account-submit");confirm(true);await("deleted",()->a.repo.currentUser()==null&&!a.operationInFlight());
    check(true,"Verified native deletion clears the local account and private state");other.restoreSession();check(other.currentUser()==null,"Another existing native session is invalidated after deletion");
    result.putString("stream",String.join("\n",checks)+"\nAccount native PASS: "+checks.size()+" checks\n");finish(Activity.RESULT_OK,result);
  }catch(Throwable failure){result.putString("stream",String.join("\n",checks)+"\nAccount native FAIL: "+android.util.Log.getStackTraceString(failure));finish(Activity.RESULT_CANCELED,result);}}
}
