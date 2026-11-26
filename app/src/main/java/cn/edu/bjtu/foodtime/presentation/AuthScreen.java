package cn.edu.bjtu.foodtime.presentation;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.InputFilter;
import android.text.InputType;
import android.util.Base64;
import android.view.View;
import android.widget.*;
import cn.edu.bjtu.foodtime.domain.model.Models.CaptchaChallenge;
import cn.edu.bjtu.foodtime.domain.repository.RepositoryException;
import cn.edu.bjtu.foodtime.domain.repository.RepositoryProvider;
import java.util.Locale;

final class AuthScreen {
  static void show(MainActivity a,LinearLayout page,boolean register) {
    Ui.gap(page,8);
    page.addView(Ui.title(a,"北交干饭吧",30));
    Ui.gap(page,4);
    page.addView(Ui.text(a,"校园食堂真实评价与干饭手账",14,Design.MUTED));
    Ui.gap(page,20);
    LinearLayout card=Ui.card(page);card.addView(Ui.title(a,register?"注册账号":"欢迎回来",22));Ui.gap(card,16);
    EditText email=Ui.field(card,"邮箱",false,false);
    email.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
    email.setHint("QQ/163/Gmail/Outlook 或学校邮箱");
    if(register) card.addView(Ui.text(a,"QQ、163、Gmail 等常用邮箱均可注册",13,Design.MUTED));
    LinearLayout invitationBox=new LinearLayout(a);
    invitationBox.setOrientation(LinearLayout.VERTICAL);
    invitationBox.setVisibility(View.GONE);
    card.addView(invitationBox);
    TextView policyStatus=register?Ui.text(a,"正在读取注册策略…",13,Design.MUTED):null;
    if(policyStatus!=null) card.addView(policyStatus);
    EditText code=register?Ui.field(card,"邮箱验证码",false,false):null;
    EditText password=Ui.field(card,"密码",true,false);
    EditText confirm=register?Ui.field(card,"确认密码",true,false):null;
    final LinearLayout captchaBox;
    final ImageView captchaImage;
    final TextView captchaStatus;
    final EditText captchaAnswer;
    String[] challengeId={null};
    if(!register) {
      captchaBox=new LinearLayout(a);
      captchaBox.setOrientation(LinearLayout.VERTICAL);
      captchaBox.setVisibility(View.GONE);
      captchaImage=new ImageView(a);
      captchaImage.setAdjustViewBounds(true);
      captchaImage.setContentDescription("登录验证码图片");
      captchaBox.addView(captchaImage,new LinearLayout.LayoutParams(-2,-2));
      captchaStatus=Ui.text(a,"",13,Design.MUTED);
      captchaBox.addView(captchaStatus);
      captchaAnswer=Ui.field(captchaBox,"验证码答案",false,false);
      captchaAnswer.setInputType(InputType.TYPE_CLASS_NUMBER);
      captchaAnswer.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});
      Ui.link(captchaBox,"换一题",() -> loadCaptcha(a,captchaBox,captchaImage,captchaStatus,challengeId,captchaAnswer));
      card.addView(captchaBox);
    } else {
      captchaBox=null;
      captchaImage=null;
      captchaStatus=null;
      captchaAnswer=null;
    }
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
      Ui.submit(card,"注册",error,() -> {
        String account=email.getText().toString(),secret=password.getText().toString(),verification=code.getText().toString();
        String invite=invitation[0]==null?"":invitation[0].getText().toString().strip();
        requireReady(ready,closed);
        String normalized=invite.toUpperCase(Locale.ROOT);
        if(invitationRequired[0]&&normalized.isEmpty()) throw new IllegalArgumentException("当前注册需要邀请码");
        if(!normalized.isEmpty()&&!normalized.matches("[A-HJ-NP-Z2-9]{12}")) throw new IllegalArgumentException("邀请码为12位发放字符");
        if(!secret.equals(confirm.getText().toString())){
          confirm.setError("两次输入的密码不一致");confirm.requestFocus();
          throw new IllegalArgumentException("两次输入的密码不一致");
        }
        return () -> a.repo.register(account,secret,verification,invite.isEmpty()?null:invite);
      },() -> {password.setText("");confirm.setText("");code.setText("");if(invitation[0]!=null)invitation[0].setText("");Ui.clearImages();a.message("注册成功，请登录");a.showAuth(false);});
    } else {
      final LinearLayout captchaArea=captchaBox;
      final ImageView captchaImageView=captchaImage;
      final TextView captchaStatusView=captchaStatus;
      final EditText captchaInput=captchaAnswer;
      TextView loginButton=Ui.button(a,"登录",true,() -> {});
      loginButton.setOnClickListener(view -> submitLogin(a,email,password,error,captchaInput,challengeId,
          loginButton,captchaArea,captchaImageView,captchaStatusView));
      card.addView(loginButton,new LinearLayout.LayoutParams(-1,-2));
    }
    Ui.gap(card,12);
    if(register) {
      Ui.link(card,"已有账号？去登录",() -> a.showAuth(false));
    } else {
      LinearLayout links=Ui.row(a);
      TextView goRegister=Ui.button(a,"注册账号",false,() -> a.showAuth(true));
      goRegister.setTextColor(Design.RED);
      goRegister.setBackground(Ui.ripple(a,Design.PAPER,0,10));
      TextView goRecovery=Ui.button(a,"忘记密码？",false,a::showPasswordRecovery);
      goRecovery.setTextColor(Design.MUTED);
      goRecovery.setBackground(Ui.ripple(a,Design.PAPER,0,10));
      links.addView(goRegister,new LinearLayout.LayoutParams(0,-2,1));
      links.addView(goRecovery,new LinearLayout.LayoutParams(0,-2,1));
      card.addView(links,new LinearLayout.LayoutParams(-1,-2));
    }
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
          status.setText(required[0]?"当前注册需要平台发放的 12 位邀请码，大小写均可。":"邀请码可选；学校邮箱可在注册后绑定。");
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

  private static final java.util.concurrent.atomic.AtomicLong CAPTCHA_TICKET = new java.util.concurrent.atomic.AtomicLong();
  private static final java.util.concurrent.atomic.AtomicLong LOGIN_TICKET = new java.util.concurrent.atomic.AtomicLong();

  private static void submitLogin(MainActivity a,EditText email,EditText password,TextView error,
      EditText captchaAnswer,String[] challengeId,TextView button,LinearLayout captchaBox,
      ImageView captchaImage,TextView captchaStatus) {
    if(!button.isEnabled())return;
    String account=email.getText().toString(),secret=password.getText().toString();
    String answer=captchaAnswer==null?"":captchaAnswer.getText().toString().strip();
    long ticket=LOGIN_TICKET.incrementAndGet();
    error.setText("");
    button.setEnabled(false);
    ((RepositoryProvider)a.getApplication()).workExecutor().execute(() -> {
      try {
        a.repo.login(account,secret,challengeId[0],answer.isEmpty()?null:answer);
        a.runOnUiThread(() -> {
          if(a.isFinishing()||a.isDestroyed()||ticket!=LOGIN_TICKET.get()||!button.isAttachedToWindow())return;
          password.setText("");
          if(captchaAnswer!=null)captchaAnswer.setText("");
          challengeId[0]=null;
          Ui.clearImages();
          a.openApp();
        });
      } catch(RuntimeException failure) {
        a.runOnUiThread(() -> {
          if(a.isFinishing()||a.isDestroyed()||ticket!=LOGIN_TICKET.get()||!button.isAttachedToWindow())return;
          error.setText(failure.getMessage()==null?"登录失败":failure.getMessage());
          String code=failure instanceof RepositoryException exception?exception.code():"";
          if("CAPTCHA_REQUIRED".equals(code)||"CAPTCHA_INVALID".equals(code))
            loadCaptcha(a,captchaBox,captchaImage,captchaStatus,challengeId,captchaAnswer);
          button.setEnabled(true);
        });
      }
    });
  }

  /** Worker-side challenge fetch; the image is decoded off the UI thread and never persisted. */
  private static void loadCaptcha(MainActivity a,LinearLayout box,ImageView image,TextView status,
      String[] challengeId,EditText answer) {
    if(box==null)return;
    long ticket=CAPTCHA_TICKET.incrementAndGet();
    status.setText("正在获取验证码…");
    status.setTextColor(Design.MUTED);
    box.setVisibility(View.VISIBLE);
    ((RepositoryProvider)a.getApplication()).workExecutor().execute(() -> {
      try {
        CaptchaChallenge challenge=a.repo.captcha();
        byte[] bytes=Base64.decode(challenge.imageBase64(),Base64.DEFAULT);
        BitmapFactory.Options bounds=new BitmapFactory.Options();
        bounds.inJustDecodeBounds=true;
        BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
        if(bounds.outWidth<=0||bounds.outHeight<=0||(long)bounds.outWidth*bounds.outHeight>4_000_000L)
          throw new IllegalArgumentException("验证码图片尺寸异常");
        Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length);
        if(bitmap==null)throw new IllegalArgumentException("验证码图片无法解析");
        a.runOnUiThread(() -> {
          if(a.isFinishing()||a.isDestroyed()||ticket!=CAPTCHA_TICKET.get()||!box.isAttachedToWindow()){
            bitmap.recycle();
            return;
          }
          image.setImageBitmap(bitmap);
          challengeId[0]=challenge.challengeId();
          if(answer!=null)answer.setText("");
          status.setText("请输入图片中的答案；2 分钟内有效。");
          status.setTextColor(Design.MUTED);
          box.setVisibility(View.VISIBLE);
        });
      } catch(RuntimeException failure) {
        a.runOnUiThread(() -> {
          if(a.isFinishing()||a.isDestroyed()||ticket!=CAPTCHA_TICKET.get()||!box.isAttachedToWindow())return;
          challengeId[0]=null;
          status.setText("无法获取验证码："+(failure.getMessage()==null?"请重试":failure.getMessage()));
          status.setTextColor(Design.RED);
          box.setVisibility(View.VISIBLE);
        });
      }
    });
  }
}
