package cn.edu.bjtu.foodtime.presentation;

import android.app.AlertDialog;
import android.text.InputFilter;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import java.util.function.Consumer;

final class ContentSafety {
  private ContentSafety() {}

  static void report(MainActivity activity, String type, String id) {
    EditText reason = new EditText(activity);
    reason.setHint("举报说明（最多500字）");
    reason.setFilters(new InputFilter[] {new InputFilter.LengthFilter(500)});
    var content = Ui.col(activity);
    content.setPadding(Ui.dp(activity, 20), 0, Ui.dp(activity, 20), 0);
    String prompt = type.equals("message") ? "平台管理员会收到所选私信及举报说明。" : "请说明内容违反了哪些社区规则。";
    content.addView(Ui.text(activity, prompt, 14, Design.MUTED));
    content.addView(reason);
    TextView error = Ui.text(activity, "", 13, Design.RED);
    content.addView(error);
    AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("举报")
        .setView(content).setNegativeButton("取消", null).setPositiveButton("提交举报", null).create();
    dialog.setOnShowListener(ignored -> {
      View submit = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
      submit.setOnClickListener(view -> {
        String note = reason.getText().toString().strip();
        if (note.isEmpty()) {
          error.setText("请输入举报说明");
          return;
        }
        activity.read("report:" + type + ":" + id, submit, error,
            () -> activity.repo.reportContent(type, id, note), () -> {
              dialog.dismiss();
              activity.message("举报已提交");
            }, () -> {});
      });
    });
    dialog.show();
  }

  static void manageBlock(MainActivity activity, View trigger, String userId, Consumer<Boolean> onChange) {
    boolean[] current = {false};
    activity.read("user-block:" + userId, trigger, null,
        () -> current[0] = activity.repo.blockedByMe(userId), () -> {
          boolean next = !current[0];
          String label = next ? "拉黑" : "解除拉黑";
          String prompt = next ? "拉黑后双方无法继续发送私信。" : "解除你对该用户的私信限制？";
          Ui.confirm(activity, label, prompt, () -> activity.request(trigger, null,
              () -> activity.repo.setBlocked(userId, next), () -> {
                activity.message(next ? "已拉黑" : "已解除拉黑");
                onChange.accept(next);
              }));
        }, () -> {});
  }
}
