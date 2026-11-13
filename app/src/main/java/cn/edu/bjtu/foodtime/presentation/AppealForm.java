package cn.edu.bjtu.foodtime.presentation;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.edu.bjtu.foodtime.BuildConfig;
import cn.edu.bjtu.foodtime.domain.model.Models.ModerationDecision;

/** The same account-scoped editor for normal and restricted sessions. Navigation belongs to the host. */
final class AppealForm {
  private AppealForm(){}
  static void show(MainActivity a,LinearLayout parent,ModerationDecision decision,Runnable submitted){
    var user=a.repo.currentUser();if(user==null)return;
    String actor=user.id(),mode=a.repo.sessionMode(),id=decision.decisionId();
    var store=new AppealDraftStore(a,BuildConfig.API_BASE_URL,actor);String evidence=store.key("evidence:"+id);
    if(!decision.appealStatus().isBlank()){
      clear(a,store,id,evidence);
      parent.addView(Ui.text(a,"你的申诉："+switch(decision.appealStatus()){case "under_review"->"复核中";case "resolved"->"已复核";default->"已提交";},14,Design.MUTED));return;
    }
    if(decision.appealDeadline()<=System.currentTimeMillis()){parent.addView(Ui.text(a,"申诉期限已过。",14,Design.MUTED));return;}
    LinearLayout form=Ui.col(a);parent.addView(form);
    boolean restored=a.drafts.getBoolean(evidence+":restored",false);
    for(int i=0;i<3;i++){
      String key=evidence+":"+i;
      if(!restored)a.drafts.putString(key,store.evidence(id,i));
      if(!a.operationInFlight())store.evidence(id,i,a.drafts.getString(key,""));
    }
    a.drafts.putBoolean(evidence+":restored",true);
    var input=Ui.field(form,"申诉内容（最多2000字）",false,true);input.setTag("appeal-content");input.setText(store.read(id));
    input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){}public void afterTextChanged(Editable value){if(current(a,actor,mode))store.save(id,value.toString());}});
    form.addView(Ui.text(a,"证据图片（可选，最多3张，点击缩略图预览）",13,Design.MUTED));
    EvidenceSlots.show(a,form,evidence,true);
    TextView formError=Ui.text(a,"",14,Design.RED);formError.setTag("appeal-form-error");Ui.observeError(formError);form.addView(formError);
    TextView progress=Ui.text(a,"正在上传证据并提交…",14,Design.MUTED);progress.setVisibility(android.view.View.GONE);form.addView(progress);
    formError.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){}public void afterTextChanged(Editable value){if(value.length()>0){enabled(form,true);progress.setVisibility(android.view.View.GONE);}}});
    TextView submit=Ui.button(a,"提交申诉",true,()->{});submit.setTag("appeal-submit");
    submit.setOnClickListener(view->{
      if(a.operationInFlight())return;
      String content=input.getText().toString().strip();
      if(content.isEmpty()){formError.setText("请填写申诉内容");return;}
      if(content.codePointCount(0,content.length())>2000){formError.setText("申诉内容不能超过2000字");return;}
      if(!current(a,actor,mode)){formError.setText("账号已变化，请重新打开申诉");return;}
      enabled(form,false);progress.setVisibility(android.view.View.VISIBLE);
      a.request(submit,formError,()->{
        require(a,actor,mode);
        var uploaded=EvidenceSlots.upload(a,evidence,(previous,url)->{require(a,actor,mode);store.uploaded(id,previous,url);});
        require(a,actor,mode);
        a.repo.submitAppeal(id,content,store.requestId(id),uploaded);
      },()->{
        if(!current(a,actor,mode))return;
        clear(a,store,id,evidence);a.message("申诉已提交");submitted.run();
      });
    });form.addView(submit);
  }
  private static void enabled(android.view.View view,boolean value){view.setEnabled(value);if(view instanceof android.view.ViewGroup group)for(int i=0;i<group.getChildCount();i++)enabled(group.getChildAt(i),value);}
  private static boolean current(MainActivity a,String actor,String mode){var user=a.repo.currentUser();return user!=null&&actor.equals(user.id())&&mode.equals(a.repo.sessionMode());}
  private static void require(MainActivity a,String actor,String mode){if(!current(a,actor,mode))throw new IllegalStateException("账号已变化，请重新打开申诉");}
  private static void clear(MainActivity a,AppealDraftStore store,String id,String evidence){store.clear(id);for(int i=0;i<3;i++)a.drafts.remove(evidence+":"+i);a.drafts.remove(evidence+":restored");}
}
