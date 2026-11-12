package cn.edu.bjtu.foodtime.presentation;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import cn.edu.bjtu.foodtime.domain.repository.RepositoryProvider;
import java.util.Locale;

final class AuthScreen {
  static void show(MainActivity a,LinearLayout page,boolean register) {
    page.addView(Ui.title(a,"北交干饭吧",32));Ui.gap(page,24);
    LinearLayout card=Ui.card(page);card.addView(Ui.title(a,register?"注册":"登录",24));Ui.gap(card,20);
    EditText email=Ui.field(card,"邮箱",false,false);
    email.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
    email.setHint("QQ/163/Gmail/Outlook 或学校邮箱");
    if(register) card.addView(Ui.text(a,"支持 QQ、163、126、Gmail、Outlook 等主流邮箱；学校邮箱可直接发布内容。",13,Design.MUTED));
    LinearLayout invitationBox=new LinearLayout(a);
    invitationBox.setOrientation(LinearLayout.VERTICAL);
    invitationBox.setVisibility(View.GONE);
    card.addView(invitationBox);
    TextView policyStatus=register?Ui.text(a,"正在读取注册策略…",13,Design.MUTED):null;
    if(policyStatus!=null) card.addView(policyStatus);
    EditText code=register?Ui.field(card,"邮箱验证码",false,false):null;
    EditText password=Ui.field(card,"密码",true,false);
    EditText confirm=register?Ui.field(card,"确认密码",true,false):null;
    TextView error=Ui.text(a,"",14,Design.RED);error.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);card.addView(error);
    boolean[] ready={false},closed={false},invitationRequired={false},canSend={false};
    EditText[] invitation={null};
    if(register) {
      code.setInputType(InputType.TYPE_CLASS_NUMBER);
      Ui.submit(card,"获取验证码",error,() -> {
        requireReady(ready,closed);
        if(!canSend[0]) throw new IllegalArgumentException("当前邮箱验证码发送未启用，请联系管理员");
        String value=email.getText().toString();return () -> a.repo.sendVerificationCode(value);
      },() -> a.message("验证码已发送"));
      Ui.gap(card,12);
    }
    Ui.submit(card,register?"注册":"登录",error,() -> {
      String account=email.getText().toString(),secret=password.getText().toString(),verification=register?code.getText().toString():"";
      String invite=invitation[0]==null?"":invitation[0].getText().toString().strip();
      if(register) {
        requireReady(ready,closed);
        String normalized=invite.toUpperCase(Locale.ROOT);
        if(invitationRequired[0]&&normalized.isEmpty()) throw new IllegalArgumentException("当前注册需要邀请码");
        if(!normalized.isEmpty()&&!normalized.matches("[A-HJ-NP-Z2-9]{12}")) throw new IllegalArgumentException("邀请码为12位发放字符");
        if(!secret.equals(confirm.getText().toString())){
          confirm.setError("两次输入的密码不一致");confirm.requestFocus();
          throw new IllegalArgumentException("两次输入的密码不一致");
        }
      }
      return () -> {if(register) a.repo.register(account,secret,verification,invite.isEmpty()?null:invite);else a.repo.login(account,secret);};
    },() -> {password.setText("");if(confirm!=null)confirm.setText("");if(code!=null)code.setText("");if(invitation[0]!=null)invitation[0].setText("");Ui.clearImages();if(register) {a.message("注册成功，请登录");a.showAuth(false);}else a.openApp();});
    Ui.gap(card,12);Ui.link(card,register?"去登录":"注册账号",() -> a.showAuth(!register));
    if(!register){Ui.gap(card,12);Ui.link(card,"忘记密码？",a::showPasswordRecovery);}
    if(register) loadPolicy(a,policyStatus,invitationBox,invitation,ready,closed,invitationRequired,canSend);
  }

  private static void requireReady(boolean[] ready,boolean[] closed) {
    if(closed[0]) throw new IllegalArgumentException("注册暂未开放");
    if(!ready[0]) throw new IllegalArgumentException("正在读取注册策略，请稍候");
  }

  /** Fetch the public policy on the app worker; the auth screen has no session, so it cannot use MainActivity.read. */
  private static void loadPolicy(MainActivity a,TextView status,LinearLayout box,EditText[] invitation,
      boolean[] ready,boolean[] closed,boolean[] required,boolean[] canSend) {
    status.setText("正在读取注册策略…");
    status.setTextColor(Design.MUTED);
    status.setOnClickListener(null);
    ((RepositoryProvider)a.getApplication()).workExecutor().execute(() -> {
      try {
        var policy=a.repo.registrationPolicy();
        a.runOnUiThread(() -> {
          if(a.isFinishing()||a.isDestroyed())return;
          ready[0]=true;closed[0]=policy.closed();required[0]=policy.invitationRequired();canSend[0]=policy.canSendEmailCode();
          if(closed[0]) {
            status.setText("注册暂未开放：当前部署已关闭注册与邮箱验证码发送。");
            status.setTextColor(Design.RED);
            box.setVisibility(View.GONE);
            return;
          }
          status.setText(required[0]?"当前注册需要平台发放的 12 位邀请码，大小写均可。":"邀请码可选；使用学校邮箱注册可直接发布内容。");
          status.setTextColor(Design.MUTED);
          if(!canSend[0]) status.append("\n当前邮件验证码发送未启用，请联系管理员。");
          if(invitation[0]==null) {
            invitation[0]=Ui.field(box,required[0]?"邀请码（必填）":"邀请码（可选）",false,false);
            invitation[0].setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
            invitation[0].setFilters(new InputFilter[]{new InputFilter.LengthFilter(12)});
          }
          box.setVisibility(View.VISIBLE);
        });
      } catch(RuntimeException failure) {
        a.runOnUiThread(() -> {
          if(a.isFinishing()||a.isDestroyed())return;
          ready[0]=false;
          status.setText("无法读取注册策略："+(failure.getMessage()==null?"请重试":failure.getMessage()));
          status.setTextColor(Design.RED);
          status.setOnClickListener(v -> loadPolicy(a,status,box,invitation,ready,closed,required,canSend));
        });
      }
    });
  }
}
