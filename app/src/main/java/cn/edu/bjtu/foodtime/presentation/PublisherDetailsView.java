package cn.edu.bjtu.foodtime.presentation;

import android.widget.LinearLayout;

/** Publisher identity shown only by guarded moderation screens. */
final class PublisherDetailsView {
  static void show(MainActivity activity, LinearLayout parent, String id, String name, String email, long time) {
    parent.addView(Ui.text(activity,"发布者名称："+fallback(name,"未命名用户"),14,Design.MUTED));
    parent.addView(Ui.text(activity,"发布者邮箱："+fallback(email,"未记录"),14,Design.MUTED));
    parent.addView(Ui.text(activity,"发布者 ID："+fallback(id,"未记录"),14,Design.MUTED));
    parent.addView(Ui.text(activity,"发布时间："+(time>0?SubmissionScreen.date(time):"未记录"),14,Design.MUTED));
  }
  private static String fallback(String value,String fallback) { return value==null||value.isBlank()?fallback:value; }
  private PublisherDetailsView() {}
}
