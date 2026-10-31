package cn.edu.bjtu.foodtime.presentation;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.edu.bjtu.foodtime.domain.model.Models.*;
import cn.edu.bjtu.foodtime.domain.repository.FoodtimeRepository;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Native moderation screen regression with disposable in-process fixtures; no writes. */
public final class ModerationPublisherRunner extends Instrumentation {
  private int checks;
  private static final String ID="publisher-001", NAME="发布者甲", EMAIL="20260001@bjtu.edu.cn";
  private static final long POSTED=1793318400000L;
  private void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
  private String text(View view){
    StringBuilder result=new StringBuilder();
    if(view instanceof TextView t)result.append(t.getText()).append('\n');
    if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)result.append(text(group.getChildAt(i)));
    return result.toString();
  }
  private void publisher(LinearLayout page){
    String visible=text(page);
    for(String expected:List.of("发布者名称："+NAME,"发布者邮箱："+EMAIL,"发布者 ID："+ID,"发布时间："+SubmissionScreen.date(POSTED),"完整审核正文"))
      check(visible.contains(expected),"Missing moderation field: "+expected);
  }
  @Override public void onCreate(Bundle args){super.onCreate(args);start();}
  @Override public void onStart(){
    Bundle result=new Bundle();MainActivity activity=null;
    try{
      if(!cn.edu.bjtu.foodtime.BuildConfig.APPLICATION_ID.endsWith(".moderationcheck"))throw new IllegalStateException("Use isolated .moderationcheck APK");
      activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      MainActivity target=activity;AtomicReference<Throwable> failure=new AtomicReference<>();
      runOnMainSync(()->{try{
        Submission submission=new Submission("submission","review","dish",ID,NAME,"菜品","完整审核正文",null,"",4,"pending","",POSTED,"食堂",AiAssessment.empty(),EMAIL);
        Review review=new Review("review","dish",ID,NAME,4,"完整审核正文",POSTED,0,0,false,0,AiAssessment.empty(),EMAIL);
        Audit audit=new Audit("log","submission","admin","approve","审核说明",POSTED+3600000,"review","human","",ID,NAME,EMAIL,"菜品","完整审核正文","",POSTED);
        target.repo=(FoodtimeRepository)Proxy.newProxyInstance(FoodtimeRepository.class.getClassLoader(),new Class[]{FoodtimeRepository.class},(p,m,a)->switch(m.getName()){
          case "currentUser"->new User("admin","admin@bjtu.edu.cn","管理员","admin",true);
          case "submissions"->List.of(submission);
          case "reviews"->List.of(review);
          case "audits"->List.of(audit);
          case "context"->"示例食堂";
          case "communityAudit"->new CommunityAuditPage("posts","pending",1,1,List.of(submission));
          default->throw new UnsupportedOperationException(m.getName());
        });
        target.tab="admin";target.stacks.get("admin").clear();target.stacks.get("admin").add(new MainActivity.Route("admin-audit",""));
        LinearLayout page=Ui.col(target);AdminScreen.audit(target,page,target.route());publisher(page);
        page=Ui.col(target);AdminScreen.reviews(target,page,target.route());publisher(page);
        page=Ui.col(target);AdminScreen.logs(target,page,target.route());publisher(page);
        check(text(page).contains("审核时间："+SubmissionScreen.date(POSTED+3600000)),"Decision time is separate");
        page=Ui.col(target);CommunityAdminScreen.queue(target,page,target.route());publisher(page);
        page=Ui.col(target);PublisherDetailsView.show(target,page,null,null,null,0);
        check(text(page).contains("未记录")&&!text(page).contains("1970"),"Missing metadata does not invent a time");
      }catch(Throwable e){failure.set(e);}});
      if(failure.get()!=null)throw new AssertionError(failure.get());
      result.putString("stream","Moderation publisher PASS: "+checks+" checks\n");finish(-1,result);
    }catch(Throwable error){result.putString("stream","Moderation publisher FAIL: "+error+"\n");finish(1,result);}
    finally{if(activity!=null){MainActivity target=activity;runOnMainSync(target::finish);}}
  }
}
