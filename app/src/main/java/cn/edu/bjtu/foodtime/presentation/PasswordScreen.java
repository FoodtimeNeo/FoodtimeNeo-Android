package cn.edu.bjtu.foodtime.presentation;

import android.widget.*;

final class PasswordScreen {
  static void show(MainActivity a, LinearLayout page, MainActivity.Route route) {
    a.heading(page, "03.2", "修改密码", "");
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
    Ui.submit(card,"确认修改",error,() -> {
      String previous=old.getText().toString(),replacement=next.getText().toString();
      if(!replacement.equals(confirm.getText().toString())) throw new IllegalArgumentException("两次输入的新密码不一致");
      return () -> a.repo.changePassword(previous,replacement);
    },() -> {old.setText("");next.setText("");confirm.setText("");a.message("密码已修改，请重新登录");a.signedOut();});
  }
}
