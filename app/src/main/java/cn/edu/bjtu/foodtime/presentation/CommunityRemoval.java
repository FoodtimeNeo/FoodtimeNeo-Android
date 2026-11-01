package cn.edu.bjtu.foodtime.presentation;

import android.app.AlertDialog;
import android.text.InputFilter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.edu.bjtu.foodtime.domain.validation.CommunityPermissions;
import cn.edu.bjtu.foodtime.domain.validation.Validation;

final class CommunityRemoval {
  static void confirm(MainActivity a, String type, String id, String authorId, Runnable success) {
    var actor = a.repo.currentUser();
    if (!CommunityPermissions.canRemove(actor, authorId) || a.operationInFlight()) return;
    boolean post = type.equals("posts");
    if (!post && !type.equals("comments")) throw new IllegalArgumentException("不支持的内容类型");
    String title = post ? "删除帖子" : "删除评论";
    Runnable completed = () -> {
      a.message(post ? "帖子已删除" : "评论已删除");
      success.run();
    };
    if (!CommunityPermissions.requiresReason(actor, authorId)) {
      Ui.confirm(a, title, post ? "帖子将从社区隐藏，删除后无法恢复。" : "删除后无法恢复，已通过的楼内回复会保留。",
          () -> {
            if (!sameActor(a, actor.id(), actor.role())) return;
            a.request(null, null, () -> {
              if (post) a.repo.deleteCommunityPost(id);
              else a.repo.deleteCommunityComment(id);
            }, completed);
          });
      return;
    }
    LinearLayout form = Ui.col(a);
    int padding = Ui.dp(a, 20);
    form.setPadding(padding, padding, padding, padding);
    EditText reason = Ui.field(form, "删除原因", false, true);
    reason.setHint("请填写1–500字的删除原因");
    reason.setFilters(new InputFilter[]{new InputFilter.LengthFilter(500)});
    TextView error = Ui.text(a, "", 13, Design.RED);
    form.addView(error);
    AlertDialog dialog = new AlertDialog.Builder(a).setTitle(title).setView(form)
        .setNegativeButton("取消", null).setPositiveButton("确认删除", null).create();
    dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
      String explanation;
      try { explanation = Validation.text(reason.getText().toString(), 500, "删除原因"); }
      catch (IllegalArgumentException e) { error.setText(e.getMessage()); return; }
      if (!sameActor(a, actor.id(), actor.role())) { dialog.dismiss(); return; }
      a.request(button, error, () -> a.repo.removeCommunityAsAdmin(type, id, explanation), () -> {
        dialog.dismiss();
        completed.run();
      });
    }));
    dialog.show();
  }

  private static boolean sameActor(MainActivity a, String id, String role) {
    var current = a.repo.currentUser();
    if (current != null && current.active() && current.id().equals(id) && current.role().equals(role)) return true;
    a.message("登录状态已变化，请重新操作");
    return false;
  }

  private CommunityRemoval() {}
}
