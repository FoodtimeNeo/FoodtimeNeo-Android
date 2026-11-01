package cn.edu.bjtu.foodtime.presentation;

import android.os.*;
import android.text.*;
import android.view.View;
import android.widget.*;
import cn.edu.bjtu.foodtime.domain.model.PasswordResetGrant;
import cn.edu.bjtu.foodtime.domain.validation.Validation;
import java.util.concurrent.atomic.AtomicReference;

/** Verification and the reset capability live only in this activity instance. */
final class PasswordRecoveryScreen {
  private final MainActivity a;private final LinearLayout page;
  private final Handler handler=new Handler(Looper.getMainLooper());
  private PasswordResetGrant grant;private int step=1;private long resendAt;private volatile boolean closed;
  private EditText password,confirmation,code;private String address="";
  PasswordRecoveryScreen(MainActivity a,LinearLayout page){this.a=a;this.page=page;render();}
  void close(){closed=true;grant=null;verifiedResult=null;handler.removeCallbacksAndMessages(null);clearSecrets();}
  private void clearSecrets(){if(password!=null)password.setText("");if(confirmation!=null)confirmation.setText("");if(code!=null)code.setText("");}
  void back(){if(step==2){grant=null;clearSecrets();step=1;render();}else a.showAuth(false);}
  private void render(){
    if(closed)return;handler.removeCallbacksAndMessages(null);page.removeAllViews();
    Ui.link(page,"← 返回登录",()->a.showAuth(false));Ui.gap(page,18);
    page.addView(Ui.title(a,"北交干饭吧",30));Ui.gap(page,20);
    LinearLayout card=Ui.card(page);card.addView(Ui.title(a,step==1?"找回密码":step==2?"设置新密码":"密码已重置",24));Ui.gap(card,16);
    if(step==3){card.addView(Ui.text(a,"请使用新密码登录。此前登录的设备需要重新登录。",15,Design.MUTED));Ui.gap(card,18);Ui.action(card,"返回登录",true,()->a.showAuth(false));return;}
    card.addView(Ui.text(a,step==1?"01 验证邮箱   /   02 设置新密码":"01 已验证邮箱   /   02 设置新密码",12,Design.RED));Ui.gap(card,18);
    TextView error=Ui.text(a,"",14,Design.RED);error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    if(step==1){
      EditText email=new EditText(a);email.setText(address);email.setSingleLine();email.setSaveEnabled(false);
      email.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
      card.addView(Ui.text(a,"账号绑定邮箱",14,Design.MUTED));card.addView(email,new LinearLayout.LayoutParams(-1,-2));
      code=Ui.field(card,"邮箱验证码",false,false);code.setSaveEnabled(false);code.setInputType(InputType.TYPE_CLASS_NUMBER);code.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
      TextView send=Ui.button(a,"发送验证码",false,()->{});card.addView(send);TextView notice=Ui.text(a,"",14,Design.MUTED);notice.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);card.addView(notice);
      Runnable countdown=new Runnable(){public void run(){if(closed||step!=1)return;long left=Math.max(0,(resendAt-SystemClock.elapsedRealtime()+999)/1000);send.setText(left>0?left+"s 后重发":"发送验证码");send.setEnabled(left==0&&!a.operationInFlight());handler.postDelayed(this,1000);}};countdown.run();
      send.setOnClickListener(v->{if(a.operationInFlight())return;try{
        String target=Validation.accountEmail(email.getText().toString());AtomicReference<Long> seconds=new AtomicReference<>(60L);
        a.request(send,error,()->seconds.set(a.repo.sendPasswordResetCode(target)),()->{resendAt=SystemClock.elapsedRealtime()+seconds.get()*1000;notice.setText("请求已受理。若该邮箱已注册且账号可用，将收到6位数字验证码，10分钟内有效。");handler.removeCallbacks(countdown);countdown.run();});
      }catch(IllegalArgumentException e){error.setText(e.getMessage());email.requestFocus();}});
      card.addView(error);Ui.gap(card,12);
      Ui.submit(card,"验证并继续",error,()->{
        String target=Validation.accountEmail(email.getText().toString()),verification=code.getText().toString();
        if(!verification.matches("[0-9]{6}"))throw new IllegalArgumentException("请输入6位数字验证码");
        AtomicReference<PasswordResetGrant> result=new AtomicReference<>();
        return ()->{result.set(a.repo.verifyPasswordResetCode(target,verification));if(!closed){verifiedResult=result.get();verifiedAddress=target;}};
      },()->{grant=verifiedResult;verifiedResult=null;address=verifiedAddress;clearSecrets();step=2;render();});
    }else{
      card.addView(Ui.text(a,"邮箱验证已通过。重置后所有设备需要重新登录。",14,Design.MUTED));Ui.gap(card,12);
      password=Ui.field(card,"新密码",true,false);card.addView(Ui.text(a,"8–128位ASCII可见字符，须包含字母和数字。",12,Design.MUTED));
      confirmation=Ui.field(card,"确认新密码",true,false);card.addView(error);Ui.gap(card,12);
      Ui.submit(card,"确认重置密码",error,()->{
        String value=password.getText().toString();Validation.password(value,true);
        if(!value.equals(confirmation.getText().toString())){confirmation.setError("两次输入的新密码不一致");confirmation.requestFocus();throw new IllegalArgumentException("两次输入的新密码不一致");}
        if(grant==null||System.currentTimeMillis()>=grant.expiresAtMillis())throw new IllegalArgumentException("验证已过期，请点击重新验证邮箱");
        String token=grant.token();return ()->a.repo.resetPassword(token,value);
      },()->{grant=null;clearSecrets();step=3;render();});
      Ui.gap(card,12);Ui.link(card,"更换邮箱 / 重新验证",this::back);
    }
  }
  private volatile PasswordResetGrant verifiedResult;private volatile String verifiedAddress="";
}
