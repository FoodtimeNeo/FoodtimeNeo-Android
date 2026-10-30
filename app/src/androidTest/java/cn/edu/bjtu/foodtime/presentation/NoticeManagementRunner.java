package cn.edu.bjtu.foodtime.presentation;
import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import cn.edu.bjtu.foodtime.data.local.LocalRepository;
import cn.edu.bjtu.foodtime.domain.repository.FoodtimeRepository;
import cn.edu.bjtu.foodtime.domain.repository.FoodtimeRepository.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.*;

/** Native management fixture; no deployed API or real user data. */
public final class NoticeManagementRunner extends Instrumentation {
 private MainActivity a;private int checks;
 private final AtomicReference<String> active=new AtomicReference<>("");
 private void check(boolean value,String name){if(!value)throw new AssertionError(name);checks++;}
 private View find(View root,String value){if(root instanceof TextView t&&value.contentEquals(t.getText()))return root;if(root instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){View found=find(g.getChildAt(i),value);if(found!=null)return found;}return null;}
 private View text(String value){return find(a.root,value);}
 private void main(Runnable work){AtomicReference<Throwable> failure=new AtomicReference<>();runOnMainSync(()->{try{work.run();}catch(Throwable e){failure.set(e);}});if(failure.get()!=null)throw new AssertionError(failure.get());}
 private void await(java.util.function.BooleanSupplier condition)throws Exception{for(int i=0;i<700;i++){AtomicBoolean done=new AtomicBoolean();main(()->done.set(condition.getAsBoolean()));if(done.get())return;Thread.sleep(50);}throw new AssertionError("Timed out");}
 @Override public void onCreate(Bundle args){super.onCreate(args);start();}
 @Override public void onStart(){Bundle result=new Bundle();String namespace="notice-management-"+UUID.randomUUID();try{
  if(!cn.edu.bjtu.foodtime.BuildConfig.APPLICATION_ID.endsWith(".integration"))throw new AssertionError("isolated APK required");
  Context context=new ContextWrapper(getTargetContext()){@Override public SharedPreferences getSharedPreferences(String name,int mode){return super.getSharedPreferences(namespace,mode);}};
  LocalRepository local=new LocalRepository(context);local.login("20260003@bjtu.edu.cn","Foodtime2026!");
  List<AdminNotice> notices=new ArrayList<>();for(int i=0;i<30;i++)notices.add(new AdminNotice("n"+i,"公告 "+i,"# 公告 "+i+"\n\n**测试**"));
  FoodtimeRepository repo=(FoodtimeRepository)Proxy.newProxyInstance(FoodtimeRepository.class.getClassLoader(),new Class[]{FoodtimeRepository.class},(p,m,args)->{
   switch(m.getName()){
    case "refresh": return null;
    case "currentNotice": return null;
    case "noticeSetting": return active.get();
    case "adminNotice": return notices.stream().filter(n->n.id().equals(args[0])).findFirst().orElseThrow();
    case "adminNotices": {int page=(int)args[0];String q=(String)args[1];var rows=notices.stream().filter(n->n.title().contains(q)).toList();return new AdminNoticePage(rows.subList(Math.min(rows.size(),(page-1)*6),Math.min(rows.size(),page*6)),rows.size());}
    case "setNoticeSetting": active.set((String)args[0]);return null;
    case "saveNotice": {AdminNotice n=new AdminNotice("new","新公告",(String)args[1]);notices.add(0,n);return n;}
    default: try{return m.invoke(local,args);}catch(InvocationTargetException e){throw e.getCause();}
   }
  });
  a=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  main(()->{a.repo=repo;a.signedOut();a.openApp();});await(()->!a.isPageLoading());
  main(()->a.go("admin-notices",""));await(()->text("公告 0")!=null);
  main(()->{text("公告 0").performClick();text("下一页").performClick();});await(()->text("公告 6")!=null);
  main(()->{check(text("待设置：公告 0")!=null,"selection retained across pages");
   View apply=text("确认设置");int[] xy=new int[2];apply.getLocationOnScreen(xy);check(xy[1]>=0&&xy[1]+apply.getHeight()<=a.root.getHeight()+a.root.getTop(),"confirmation in viewport");apply.performClick();});
  await(()->active.get().equals("n0"));main(()->check(text("当前公告：公告 0")!=null,"confirmed current title"));
  main(()->{text("暂不显示公告").performClick();text("确认设置").performClick();});await(()->active.get().isEmpty());check(true,"disable sends empty setting");
  main(()->text("创建公告").performClick());await(()->text("Markdown 内容")!=null);
  main(()->{setDraft(a.content);text("保存公告").performClick();});await(()->text("待设置：新公告")!=null);
  check(active.get().isEmpty(),"creating does not activate");
  result.putString("result",checks+" native notice management checks PASS");finish(Activity.RESULT_OK,result);
 }catch(Throwable e){result.putString("error",android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);}finally{if(a!=null)main(()->a.finish());getTargetContext().getSharedPreferences(namespace,0).edit().clear().commit();}}
 private boolean setDraft(View v){if(v instanceof EditText e){e.setText("# 新公告\n\n**正文**");return true;}if(v instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++)if(setDraft(g.getChildAt(i)))return true;return false;}
}
