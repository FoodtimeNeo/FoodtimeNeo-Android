package cn.edu.bjtu.foodtime.presentation;

import android.app.AlertDialog;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import cn.edu.bjtu.foodtime.domain.model.AccountLifecycle;
import cn.edu.bjtu.foodtime.domain.model.NoticeTimes;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

final class AccountLifecycleScreen {
  static void show(MainActivity a,LinearLayout page,MainActivity.Route route){
    if(a.repo.currentUser()==null||a.repo.currentUser().isAdmin())throw new IllegalStateException("管理员和超级管理员不能注销、绑定或解绑学校邮箱");
    a.heading(page,"","账号安全与注销","验证码只用于当前账号和当前邮箱；管理员与超级管理员不能执行这些操作。");
    TextView error=Ui.text(a,"",14,Design.RED);Ui.observeError(error);page.addView(error);LinearLayout body=Ui.col(a);page.addView(body);
    TextView reload=Ui.button(a,"重新读取账号状态",false,()->{});page.addView(reload);
    List<View> controls=new ArrayList<>();controls.add(reload);List<Runnable> updates=new ArrayList<>();
    Runnable read=()->{
      AtomicReference<AccountLifecycle.Summary> result=new AtomicReference<>();
      a.read("account-lifecycle:"+UUID.randomUUID(),reload,error,()->result.set(a.repo.accountLifecycle()),()->{
        body.removeAllViews();controls.clear();controls.add(reload);updates.clear();var summary=result.get();
        if(!summary.canDelete()){body.addView(Ui.text(a,"此账号不能注销或变更学校邮箱。",16,Design.RED));return;}
        if(summary.canUnbind())new Action(a,body,route,summary,false,controls,updates);
        else{body.addView(Ui.text(a,"当前未绑定学校邮箱。注销时验证注册邮箱。",14,Design.MUTED));Ui.action(body,"绑定学校邮箱",false,()->a.go("bind-school-email",""));}
        new Action(a,body,route,summary,true,controls,updates);
      },()->{});
    };
    reload.setOnClickListener(view->{if(!a.operationInFlight())read.run();});page.post(()->{if(page.isAttachedToWindow())read.run();});
    page.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View view){}public void onViewDetachedFromWindow(View view){if(a.accountActionConfirmation!=null){a.accountActionConfirmation.dismiss();a.accountActionConfirmation=null;}}});
  }
  private static final class Action {
    final MainActivity a;final MainActivity.Route route;final LinearLayout card;final AccountLifecycle.Summary summary;final boolean deletion;final List<View> controls;
    final EditText code;final CheckBox accepted;final TextView send,submit,error,message;final String actor;final Handler handler=new Handler(Looper.getMainLooper());
    boolean spent;long resendAfter;
    final List<Runnable> updates;
    Action(MainActivity a,LinearLayout body,MainActivity.Route route,AccountLifecycle.Summary summary,boolean deletion,List<View> controls,List<Runnable> updates){
      this.a=a;this.route=route;this.summary=summary;this.deletion=deletion;this.controls=controls;this.updates=updates;actor=a.repo.currentUser().id()+":"+a.repo.currentUser().role();
      spent=deletion&&!summary.deletionCodeAvailableAt().isBlank();card=Ui.card(body);card.addView(Ui.title(a,deletion?"注销账号":"解绑学校邮箱",22));
      card.addView(Ui.text(a,deletion?"注销立即生效且不可撤销。已发布的帖子、评论、点评和反馈保留，显示“已注销用户”；所有设备旧登录失效。\n登录邮箱与学校邮箱冷却 1 小时后可重新注册或绑定，新账号不继承旧数据。\n注销验证码每天只能成功发送一次，UTC+8 每天 00:00 重置；发送失败可重试，验证码 15 分钟有效。":"解绑后账号依然存在，移除学校邮箱认证，可用功能按平台当前权限策略执行。\n学校邮箱冷却 1 小时后可重新绑定；登录邮箱仍用于原账号登录。",14,Design.INK));
      card.addView(Ui.text(a,"验证邮箱："+(deletion?summary.verificationEmail():summary.schoolEmail()),14,Design.MUTED));
      message=Ui.text(a,spent?"今天已申请注销验证码，请检查邮箱。再次发送时间："+NoticeTimes.display(summary.deletionCodeAvailableAt())+"。跨日后请重新读取状态。":"",14,Design.GREEN);card.addView(message);
      error=Ui.text(a,"",14,Design.RED);Ui.observeError(error);card.addView(error);
      send=Ui.button(a,"发送邮箱验证码",false,()->send());send.setTag(deletion?"deletion-code-send":"unbind-code-send");card.addView(send);controls.add(send);
      code=Ui.field(card,"邮箱验证码",false,false);code.setSaveEnabled(false);code.setTag(deletion?"deletion-verification-code":"unbind-verification-code");code.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);code.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});controls.add(code);
      accepted=new CheckBox(a);accepted.setText(deletion?"我已知晓注销不可撤销、新账号不继承旧数据及邮箱 1 小时冷却规则":"我已知晓解绑会移除学校邮箱认证及学校邮箱 1 小时冷却规则");accepted.setTextColor(Design.INK);accepted.setMinHeight(Ui.dp(a,48));card.addView(accepted);controls.add(accepted);
      submit=Ui.button(a,deletion?"注销账号":"解绑学校邮箱",true,()->confirm());submit.setTag(deletion?"delete-account-submit":"unbind-school-submit");card.addView(submit);controls.add(submit);
      error.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int af){}public void onTextChanged(CharSequence s,int st,int before,int count){if(s.length()>0){if(deletion&&s.toString().contains("每天只能"))spent=true;enable();}}public void afterTextChanged(Editable e){}});
      code.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int af){}public void onTextChanged(CharSequence s,int st,int before,int count){update();}public void afterTextChanged(Editable e){}});accepted.setOnCheckedChangeListener((button,value)->update());
      card.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View view){}public void onViewDetachedFromWindow(View view){handler.removeCallbacksAndMessages(null);code.setText("");}});updates.add(this::update);update();
    }
    boolean current(){return card.isAttachedToWindow()&&a.route().equals(route)&&a.repo.currentUser()!=null&&actor.equals(a.repo.currentUser().id()+":"+a.repo.currentUser().role())&&!a.repo.currentUser().isAdmin();}
    void enable(){for(View view:controls)view.setEnabled(true);for(Runnable update:updates)update.run();}
    void disable(){for(View view:controls)view.setEnabled(false);}
    void update(){boolean idle=!a.operationInFlight();send.setEnabled(idle&&!spent&&SystemClock.elapsedRealtime()>=resendAfter);submit.setEnabled(idle&&accepted.isChecked()&&code.getText().toString().matches("[0-9]{6}"));}
    void send(){
      if(!current()||a.operationInFlight()||spent||SystemClock.elapsedRealtime()<resendAfter)return;disable();AtomicReference<AccountLifecycle.Sent> result=new AtomicReference<>();
      a.request(send,error,()->result.set(a.repo.sendAccountActionCode(deletion)),()->{
        if(!current())return;var sent=result.get();String target=deletion?summary.verificationEmail():summary.schoolEmail();
        if(!target.equals(sent.verificationEmail())){enable();error.setText("验证邮箱已变化，请重新读取账号状态后检查邮箱");return;}
        if(deletion)spent=true;else{resendAfter=SystemClock.elapsedRealtime()+60000;handler.postDelayed(this::update,60000);}
        message.setText(deletion?"验证码已发送，今天不能再次发送；请在 15 分钟内验证。再次发送："+NoticeTimes.display(sent.nextSendAt()):"验证码已发送到当前学校邮箱，15 分钟有效，60 秒后可重发。");enable();
      });
    }
    void confirm(){
      if(!current()||a.operationInFlight()||!accepted.isChecked()||!code.getText().toString().matches("[0-9]{6}"))return;
      String verification=code.getText().toString();if(a.accountActionConfirmation!=null)a.accountActionConfirmation.dismiss();
      AlertDialog dialog=new AlertDialog.Builder(a).setTitle(deletion?"确认注销账号？":"确认解绑学校邮箱？").setMessage(deletion?"立即注销且不可撤销。旧内容显示已注销用户，新账号不继承旧数据；两个邮箱冷却 1 小时后可重新使用。":"账号保留，移除学校邮箱认证，权限以平台当前策略为准；学校邮箱冷却 1 小时后可重新绑定。").setNegativeButton("取消",null).setPositiveButton("确认",null).create();
      a.accountActionConfirmation=dialog;dialog.setOnDismissListener(ignored->{if(a.accountActionConfirmation==dialog)a.accountActionConfirmation=null;});
      dialog.setOnShowListener(ignored->dialog.getButton(-1).setOnClickListener(view->{
        if(!current()){dialog.dismiss();a.message("页面或账号状态已变化，请重新读取后操作");return;}if(a.operationInFlight())return;dialog.dismiss();disable();AtomicReference<String> reusable=new AtomicReference<>();
        String ownerId=a.repo.currentUser()==null?"":a.repo.currentUser().id();
        a.request(submit,error,()->reusable.set(deletion?a.repo.deleteAccount(verification):a.repo.unbindSchoolEmail(verification)),()->{
          code.setText("");a.clearDraft(route);
          if(deletion){if(!ownerId.isBlank())PostDraftStore.purge(a,ownerId);a.signedOut();a.message("账号已注销，邮箱可在 "+NoticeTimes.display(reusable.get())+" 后重新使用；新注册账号不继承旧数据。");}
          else{a.back();a.message("学校邮箱已解绑，可在 "+NoticeTimes.display(reusable.get())+" 后重新绑定。");}
        });
      }));dialog.show();
    }
  }
  private AccountLifecycleScreen(){}
}
