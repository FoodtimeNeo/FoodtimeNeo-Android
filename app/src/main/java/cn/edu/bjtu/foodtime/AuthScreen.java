package cn.edu.bjtu.foodtime;

import android.text.InputType;
import android.widget.*;

final class AuthScreen {
  static void show(MainActivity a, LinearLayout page, boolean register) {
    page.addView(Ui.stamp(a, "BJTU · FOODTIME", Design.RED));
    Ui.gap(page, 24);
    page.addView(Ui.title(a, "北交干饭吧", 36));
    Ui.gap(page, 8);
    page.addView(Ui.note(a, "今天食堂不开盲盒"));
    Ui.gap(page, 24);
    LinearLayout card = Ui.card(page);
    card.addView(Ui.title(a, register ? "加入干饭手账" : "翻开你的干饭手账", 24));
    Ui.gap(card, 24);
    EditText email = Ui.field(card, "学号邮箱", false, false);
    email.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
    email.setHint("20260001@bjtu.edu.cn");
    EditText password = Ui.field(card, "密码", true, false);
    EditText code = register ? Ui.field(card, "邮箱验证码", false, false) : null;
    TextView error = Ui.text(a, "", 14, Design.RED);
    error.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
    card.addView(error);
    if (register) {
      code.setInputType(InputType.TYPE_CLASS_NUMBER);
      Ui.link(
          card,
          "获取验证码",
          () -> {
            try {
              String value = a.repo.sendVerificationCode(email.getText().toString());
              a.message("本地演示验证码：" + value);
              code.setText(value);
              error.setText("演示验证码已填入，真实邮件将在接入后端后发送");
            } catch (RuntimeException e) {
              error.setText(e.getMessage());
            }
          });
    }
    Ui.gap(card, 8);
    Ui.action(
        card,
        register ? "注册账号" : "登录手账",
        true,
        () -> {
          try {
            if (register) {
              a.repo.register(
                  email.getText().toString(),
                  password.getText().toString(),
                  code.getText().toString());
              a.message("注册成功，请登录");
              a.showAuth(false);
            } else {
              a.repo.login(email.getText().toString(), password.getText().toString());
              a.openApp();
            }
          } catch (RuntimeException e) {
            error.setText(e.getMessage());
          }
        });
    Ui.gap(card, 12);
    Ui.link(card, register ? "已有账号，去登录" : "还没有账号？注册", () -> a.showAuth(!register));
    LinearLayout demo = Ui.card(page);
    demo.addView(Ui.stamp(a, "本地试用", Design.BLUE));
    Ui.gap(demo, 12);
    demo.addView(Ui.text(a, "当前为离线演示，账号与数据只保存在本机。", 14, Design.MUTED));
    Ui.link(
        demo,
        "以同学身份体验",
        () -> {
          email.setText("20260001@bjtu.edu.cn");
          password.setText("Foodtime2026!");
        });
    Ui.link(
        demo,
        "以管理员身份体验",
        () -> {
          email.setText("20260002@bjtu.edu.cn");
          password.setText("Foodtime2026!");
        });
    Ui.link(
        demo,
        "以平台管理员身份体验",
        () -> {
          email.setText("20260003@bjtu.edu.cn");
          password.setText("Foodtime2026!");
        });
  }
}
