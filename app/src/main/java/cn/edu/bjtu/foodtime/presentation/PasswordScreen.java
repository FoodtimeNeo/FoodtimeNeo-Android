package cn.edu.bjtu.foodtime.presentation;

import android.widget.*;

final class PasswordScreen {
  static void show(MainActivity a, LinearLayout page, MainActivity.Route route) {
    a.heading(page, "02.3", "换一把新钥匙", "修改密码后，需要重新登录");
    LinearLayout card = Ui.card(page);
    EditText old = Ui.field(card, "旧密码", true, false);
    EditText next = Ui.field(card, "新密码", true, false);
    EditText confirm = Ui.field(card, "确认新密码", true, false);
    card.addView(Ui.text(a, "8–128位，至少包含一个字母和一个数字；仅限英文、数字和半角符号。", 14, Design.MUTED));
    Ui.gap(card, 16);
    TextView error = Ui.text(a, "", 14, Design.RED);
    error.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
    card.addView(error);
    Ui.gap(card, 16);
    Ui.action(
        card,
        "确认修改",
        true,
        () -> {
          try {
            if (!next.getText().toString().equals(confirm.getText().toString()))
              throw new IllegalArgumentException("两次输入的新密码不一致");
            a.repo.changePassword(old.getText().toString(), next.getText().toString());
            old.setText("");
            next.setText("");
            confirm.setText("");
            a.drafts.clear();
            a.message("密码已修改，请用新密码重新登录");
            a.logout();
          } catch (RuntimeException e) {
            error.setText(e.getMessage());
          }
        });
  }
}
