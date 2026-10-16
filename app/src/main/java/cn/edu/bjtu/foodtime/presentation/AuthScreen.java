package cn.edu.bjtu.foodtime.presentation;
import android.text.InputType;
import android.widget.*;

final class AuthScreen {
  static void show(MainActivity a,LinearLayout page,boolean register) {
    page.addView(Ui.title(a,"北交干饭吧",32));Ui.gap(page,24);
    LinearLayout card=Ui.card(page);card.addView(Ui.title(a,register?"注册":"登录",24));Ui.gap(card,20);
    EditText email=Ui.field(card,"学号邮箱",false,false);
    email.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
    email.setHint("20260001@bjtu.edu.cn");
    EditText password=Ui.field(card,"密码",true,false);
    EditText code=register?Ui.field(card,"邮箱验证码",false,false):null;
    TextView error=Ui.text(a,"",14,Design.RED);error.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);card.addView(error);
    if(register) {
      code.setInputType(InputType.TYPE_CLASS_NUMBER);
      Ui.submit(card,"获取验证码",error,() -> {String value=email.getText().toString();return () -> a.repo.sendVerificationCode(value);},() -> a.message("验证码已发送"));
      Ui.gap(card,12);
    }
    Ui.submit(card,register?"注册":"登录",error,() -> {
      String account=email.getText().toString(),secret=password.getText().toString(),verification=register?code.getText().toString():"";
      return () -> {if(register) a.repo.register(account,secret,verification);else a.repo.login(account,secret);};
    },() -> {password.setText("");Ui.clearImages();if(register) {a.message("注册成功，请登录");a.showAuth(false);}else a.openApp();});
    Ui.gap(card,12);Ui.link(card,register?"去登录":"注册账号",() -> a.showAuth(!register));
  }
}
