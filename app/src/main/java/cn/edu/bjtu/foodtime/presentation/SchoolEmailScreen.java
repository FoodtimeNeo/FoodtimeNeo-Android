package cn.edu.bjtu.foodtime.presentation;

import android.view.*;
import android.widget.*;

final class SchoolEmailScreen {
  static void binder(MainActivity a,LinearLayout page,MainActivity.Route route) {
    a.heading(page,"03.2","绑定学校邮箱","绑定后解锁发帖、评论、点赞与投稿");
    LinearLayout card=Ui.col(a);card.setPadding(Ui.dp(a,18),Ui.dp(a,20),Ui.dp(a,18),Ui.dp(a,20));
    card.setBackground(Ui.shape(a,Design.ALT,0,24));
    card.addView(Ui.text(a,"当前是访客身份，只能浏览。绑定学号邮箱后即可发布内容。",14,Design.MUTED));
    Ui.gap(card,16);
    EditText email=Ui.field(card,"学校邮箱",false,false);
    email.setHint("20260001@bjtu.edu.cn");
    email.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
    Ui.gap(card,10);
    TextView send=Ui.button(a,"发送验证码",false,() -> {});
    send.setBackground(Ui.ripple(a,Design.PAPER,Design.RED,18));send.setTextColor(Design.RED);
    card.addView(send,new LinearLayout.LayoutParams(-1,-2));Ui.gap(card,10);
    TextView error=Ui.text(a,"",14,Design.RED);
    error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);card.addView(error);Ui.gap(card,4);
    EditText code=Ui.field(card,"验证码",false,false);
    code.setHint("6 位数字");
    code.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
    Ui.gap(card,12);
    TextView bind=Ui.button(a,"绑定并解锁",true,() -> {});
    bind.setBackground(Ui.ripple(a,Design.RED,0,18));
    send.setOnClickListener(v -> a.request(v,error,
        () -> a.repo.sendSchoolEmailCode(email.getText().toString()),
        () -> a.message("验证码已发送到学校邮箱")));
    bind.setOnClickListener(v -> {
      String address=email.getText().toString(),verification=code.getText().toString();
      a.request(v,error,
          () -> a.repo.bindSchoolEmail(address,verification),
          () -> {a.message("绑定成功，已解锁发布权限");a.back();});
    });
    card.addView(bind,new LinearLayout.LayoutParams(-1,-2));
    page.addView(card,new LinearLayout.LayoutParams(-1,-2));Ui.gap(page,18);
    LinearLayout marks=Ui.row(a);marks.setGravity(Gravity.CENTER);
    marks.addView(Ui.foodDoodle(a,4,0x665b7a3a),new LinearLayout.LayoutParams(Ui.dp(a,74),Ui.dp(a,56)));
    page.addView(marks);
  }
}
