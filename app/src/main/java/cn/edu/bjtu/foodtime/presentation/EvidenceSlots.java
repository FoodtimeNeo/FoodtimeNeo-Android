package cn.edu.bjtu.foodtime.presentation;

import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Up to three private evidence images chosen through the shared ImagePicker flow. */
final class EvidenceSlots {
  private EvidenceSlots() {}

  static List<String> paths(MainActivity activity, String prefix) {
    List<String> paths = new ArrayList<>();
    for (int index = 0; index < 3; index++) {
      String path = activity.drafts.getString(key(prefix, index), "");
      if (!path.isBlank()) paths.add(path);
    }
    return paths;
  }

  static List<String> upload(MainActivity activity, String prefix) {
    var actor = activity.repo.currentUser();
    if (actor == null) throw new IllegalStateException("请先登录");
    List<String> urls = new ArrayList<>();
    for (String path : paths(activity, prefix)) {
      requireActor(activity, actor.id());
      String uploaded = activity.repo.uploadEvidence(path);
      requireActor(activity, actor.id());
      // Keep successful references when a later upload or association fails.
      activity.runOnUiThread(() -> {
        var current = activity.repo.currentUser();
        if (current == null || !actor.id().equals(current.id())) return;
        for (int index = 0; index < 3; index++) {
          String slot = key(prefix, index);
          if (path.equals(activity.drafts.getString(slot, ""))) activity.drafts.putString(slot, uploaded);
        }
      });
      urls.add(uploaded);
    }
    return urls;
  }

  private static void requireActor(MainActivity activity, String expected) {
    var current = activity.repo.currentUser();
    if (current == null || !expected.equals(current.id()))
      throw new IllegalStateException("账号已变化，请重新选择证据图片");
  }

  static void show(MainActivity activity, LinearLayout parent, String prefix) {
    for (int index = 0; index < 3; index++) {
      String key = key(prefix, index);
      String path = activity.drafts.getString(key, "");
      LinearLayout row = Ui.row(activity);
      TextView choose = Ui.button(activity,
          path.isBlank() ? "＋ 添加证据 " + (index + 1) : "证据 " + (index + 1) + " 已选择",
          false, () -> activity.pickImage(key));
      row.addView(choose, new LinearLayout.LayoutParams(0, -2, 1));
      if (!path.isBlank()) {
        TextView remove = Ui.button(activity, "移除", false, () -> {
          activity.drafts.remove(key);
          activity.redraw();
        });
        row.addView(remove);
      }
      parent.addView(row);
    }
  }

  private static String key(String prefix, int index) {
    return prefix + ":" + index;
  }
}
