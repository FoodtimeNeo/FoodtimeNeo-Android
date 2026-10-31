package cn.edu.bjtu.foodtime.presentation;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.text.Spanned;
import android.view.*;
import android.widget.*;
import cn.edu.bjtu.foodtime.data.local.LocalRepository;
import cn.edu.bjtu.foodtime.data.remote.*;
import cn.edu.bjtu.foodtime.domain.model.Models.*;
import cn.edu.bjtu.foodtime.domain.repository.*;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import org.json.JSONObject;

/** Isolated native/HTTP contract smoke check; never writes to a deployed server. */
public final class LoginNoticeRunner extends Instrumentation {
  private MainActivity activity;
  private int checks;
  private final AtomicInteger loads=new AtomicInteger();
  private final AtomicBoolean suppressed=new AtomicBoolean(),failConfirm=new AtomicBoolean();
  private static final String CONTENT="# 公告样例\n\n**重要提醒** 与 *斜体*\n\n- 第一项\n- 第二项\n\n> 引用说明\n\n    sample code\n\n| 日期 | 状态 |\n|---|---|\n| 今天 | 正常 |\n\n[帮助](https://example.com)";
  private void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
  private void main(Runnable work){AtomicReference<Throwable> error=new AtomicReference<>();runOnMainSync(()->{try{work.run();}catch(Throwable e){error.set(e);}});if(error.get()!=null)throw new AssertionError(error.get());}
  private void await(BooleanSupplier condition,String name)throws Exception{
    long until=System.currentTimeMillis()+8000;
    while(System.currentTimeMillis()<until){AtomicBoolean ready=new AtomicBoolean();main(()->ready.set(condition.getAsBoolean()));if(ready.get())return;Thread.sleep(60);}
    StringBuilder info=new StringBuilder("loads="+loads.get());
    main(()->{for(String field:new String[]{"foreground","contentReady","pageFailed","navigationLoading","stateSaved","loginNotices","loginNoticeDialog"})try{Field f=MainActivity.class.getDeclaredField(field);f.setAccessible(true);Object value=f.get(activity);info.append(" ").append(field).append("=").append(value);if(value instanceof LoginNoticeController c)info.append(" pending=").append(c.pending).append(" error=").append(c.checkError);}catch(Exception ignored){} info.append(" focus=").append(activity.hasWindowFocus());});
    var root=getUiAutomation().getRootInActiveWindow();info.append(" root=").append(root);
    var shot=getUiAutomation().takeScreenshot();if(shot!=null){try(var out=new FileOutputStream(new File(getTargetContext().getFilesDir(),"notice-failure.png"))){shot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}shot.recycle();}
    throw new AssertionError("Timeout: "+name+" "+info);
  }
  private LoginNoticeDialog dialog(){
    try{Field f=MainActivity.class.getDeclaredField("loginNoticeDialog");f.setAccessible(true);return (LoginNoticeDialog)f.get(activity);}
    catch(Exception e){throw new IllegalStateException(e);}
  }
  private View find(View v,String text){
    if(v instanceof TextView t&&text.contentEquals(t.getText()))return v;
    if(v instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View result=find(group.getChildAt(i),text);if(result!=null)return result;}
    return null;
  }
  private View text(String text){return find(dialog().dialog.getWindow().getDecorView(),text);}
  private FoodtimeRepository controlled(LocalRepository local){
    return (FoodtimeRepository)java.lang.reflect.Proxy.newProxyInstance(FoodtimeRepository.class.getClassLoader(),new Class[]{FoodtimeRepository.class},(p,method,args)->{
      if(method.getName().equals("refresh"))return null;
      if(method.getName().equals("currentNotice")){loads.incrementAndGet();return suppressed.get()?null:new Notice("fixture",CONTENT,0);}
      if(method.getName().equals("confirmNotice")){if(failConfirm.getAndSet(false))throw new RepositoryException(503,"TEMP","暂时不可用",0);suppressed.set((boolean)args[1]);return null;}
      try{return method.invoke(local,args);}catch(InvocationTargetException e){throw e.getCause();}
    });
  }
  private void protocol()throws Exception{
    AtomicReference<String> posted=new AtomicReference<>();
    AtomicBoolean noNotice=new AtomicBoolean();
    try(ServerSocket server=new ServerSocket(0,8,InetAddress.getByName("127.0.0.1"))){
      Thread worker=new Thread(()->{
        while(!server.isClosed())try(Socket socket=server.accept()){
          BufferedReader reader=new BufferedReader(new InputStreamReader(socket.getInputStream(),java.nio.charset.StandardCharsets.UTF_8));
          String request=reader.readLine(),line;int length=0;
          while((line=reader.readLine())!=null&&!line.isEmpty())if(line.toLowerCase(Locale.ROOT).startsWith("content-length:"))length=Integer.parseInt(line.substring(15).trim());
          char[] payload=new char[length];int read=0;while(read<length){int n=reader.read(payload,read,length-read);if(n<0)break;read+=n;}
          Object data;
          if(request.contains("/current"))data=noNotice.get()?JSONObject.NULL:new JSONObject().put("id","http-fixture").put("content",CONTENT).put("createdTime","2026-10-30T00:00:00Z");
          else if(request.contains("/auth/csrf"))data=new JSONObject().put("token","fixture-csrf");
          else if(request.contains("/confirm")){posted.set(new String(payload));data=new JSONObject().put("isRead",true);}
          else data=new JSONObject().put("unreadCount",0);
          byte[] body=new JSONObject().put("code","OK").put("data",data).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
          OutputStream out=socket.getOutputStream();out.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: "+body.length+"\r\nConnection: close\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));out.write(body);out.flush();
        }catch(Exception error){if(!server.isClosed())throw new RuntimeException(error);}
      },"notice-loopback");worker.setDaemon(true);worker.start();
      String base="http://127.0.0.1:"+server.getLocalPort()+"/api/v1/";
      RemoteRepository remote=new RemoteRepository(base,new HttpTransport(base));
      check(remote.currentNotice().content().equals(CONTENT),"HTTP parses content only");
      remote.confirmNotice("http-fixture",true);
      check(new JSONObject(posted.get()).getBoolean("dontShowAgain"),"HTTP sends explicit suppression flag");
      noNotice.set(true);check(remote.currentNotice()==null,"HTTP accepts null configuration/suppression response");
    }
  }
  @Override public void onCreate(Bundle args){super.onCreate(args);start();}
  @Override public void onStart(){
    Bundle report=new Bundle();String namespace="notice-test-"+UUID.randomUUID();
    try{
      if(!cn.edu.bjtu.foodtime.BuildConfig.DEBUG||!cn.edu.bjtu.foodtime.BuildConfig.APPLICATION_ID.endsWith(".integration"))throw new IllegalStateException("Only isolated integration APK");
      protocol();
      Context context=new ContextWrapper(getTargetContext()){@Override public SharedPreferences getSharedPreferences(String name,int mode){return super.getSharedPreferences(namespace,mode);}};
      LocalRepository local=new LocalRepository(context);local.login("20260001@bjtu.edu.cn","Foodtime2026!");
      FoodtimeRepository repo=controlled(local);
      // Repeated explicit retries while a request is queued must not create concurrent checks.
      java.util.List<Runnable> queued=new java.util.ArrayList<>();
      main(()->{
        LoginNoticeController state=new LoginNoticeController();
        state.check(repo,queued::add);
        state.retry(repo,queued::add);
        state.retry(repo,queued::add);
        check(queued.size()==1,"Pending login check deduplicates rapid retries");
        state.reset();
      });
      activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      main(()->{activity.repo=repo;activity.signedOut();activity.openApp();});
      await(()->dialog()!=null&&dialog().dialog.isShowing(),"login announcement");
      main(()->{
        TextView rendered=new TextView(activity);NoticeMarkdown.render(rendered,CONTENT);
        check(rendered.getText() instanceof Spanned,"Native Markdown produces spans");
        Spanned spans=(Spanned)rendered.getText();
        check(Arrays.stream(spans.getSpans(0,spans.length(),Object.class)).anyMatch(s->s.getClass().getSimpleName().equals("HeadingSpan")),"Markdown heading is styled");
        check(!rendered.getText().toString().contains("**"),"Emphasis is rendered, not raw Markdown");
        check(!((CheckBox)text("下次不再显示")).isChecked(),"Suppression defaults off");
      });
      waitForIdleSync();File folder=new File(getTargetContext().getFilesDir(),"notice-evidence");folder.mkdirs();
      var bitmap=getUiAutomation().takeScreenshot();try(var out=new FileOutputStream(new File(folder,"login-markdown.png"))){check(bitmap!=null&&bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out),"Screenshot saved");}finally{if(bitmap!=null)bitmap.recycle();}
      main(()->text("确认").performClick());await(()->dialog()==null,"unchecked confirmation");
      main(()->activity.openApp());Thread.sleep(200);check(loads.get()==1,"Normal navigation does not recheck or poll");
      main(()->{activity.signedOut();activity.openApp();});await(()->dialog()!=null,"Next login checks read-but-not-suppressed notice");
      failConfirm.set(true);
      main(()->{((CheckBox)text("下次不再显示")).setChecked(true);text("确认").performClick();});
      await(()->text("确认失败，请重试")!=null,"retry feedback");
      main(()->check(((CheckBox)text("下次不再显示")).isChecked(),"Failed confirmation preserves selection"));
      main(()->text("确认").performClick());await(()->dialog()==null,"checked confirmation");
      main(()->{activity.signedOut();activity.openApp();});Thread.sleep(300);
      main(()->check(dialog()==null,"Suppressed notice stays hidden after login"));
      // Retained state remains the same across Activity recreation.
      int before=loads.get();ActivityMonitor monitor=addMonitor(MainActivity.class.getName(),null,false);
      main(()->activity.recreate());Activity next=waitForMonitorWithTimeout(monitor,8000);removeMonitor(monitor);
      check(next instanceof MainActivity,"Activity recreates");
      activity=(MainActivity)next;main(()->{activity.repo=repo;activity.openApp();});Thread.sleep(300);
      check(loads.get()==before,"Activity recreation does not create another login check");
      report.putString("stream","Login notice native PASS: "+checks+" checks\n");finish(-1,report);
    }catch(Throwable error){StringWriter stack=new StringWriter();error.printStackTrace(new PrintWriter(stack));report.putString("stream","Login notice FAIL after "+checks+" checks\n"+stack);finish(1,report);}
    finally{if(activity!=null)main(()->activity.finish());getTargetContext().deleteSharedPreferences(namespace);}
  }
}
