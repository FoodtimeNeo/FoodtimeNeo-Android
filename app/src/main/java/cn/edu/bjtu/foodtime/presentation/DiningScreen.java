package cn.edu.bjtu.foodtime.presentation;

import android.view.*;
import android.widget.*;
import cn.edu.bjtu.foodtime.domain.model.Models.*;

final class DiningScreen {
  static void halls(MainActivity a, LinearLayout page, MainActivity.Route route) {
    a.heading(page, "01", "食堂", "");
    if (a.repo.halls().isEmpty()) {
      Ui.empty(page, "暂无食堂", "");
      return;
    }
    for (Hall hall : a.repo.halls())
      cover(a, page, hall.name(), hall.coverImageUrl(), () -> a.go("stalls", hall.id()));
  }

  static void stalls(MainActivity a, LinearLayout page, MainActivity.Route route) {
    Hall hall = a.repo.hall(route.id());
    if (hall == null || !hall.active()) throw new IllegalArgumentException("食堂不存在或已停用");
    a.heading(page, "01.1", hall.name(), "");
    if (a.repo.stalls(hall.id()).isEmpty()) {
      Ui.empty(page, "暂无档口", "");
      Ui.action(page, "投稿一个新档口", true, () -> a.go("submit-stall", hall.id()));
      return;
    }
    for (Stall stall : a.repo.stalls(hall.id()))
      cover(a, page, stall.name(), stall.coverImageUrl(), () -> a.go("dishes", stall.id()));
    Ui.action(page, "投稿一个新档口", true, () -> a.go("submit-stall", hall.id()));
  }

  static void cover(MainActivity a, LinearLayout page, String name, String image, Runnable action) {
    LinearLayout card = Ui.card(page);
    card.setPadding(0, 0, 0, Ui.dp(a, 4));
    card.addView(Ui.image(a, image, "暂未上传封面", true));
    LinearLayout label = Ui.row(a);
    label.setPadding(Ui.dp(a, 16), Ui.dp(a, 12), Ui.dp(a, 16), Ui.dp(a, 12));
    label.addView(Ui.title(a, name, 24), new LinearLayout.LayoutParams(0, -2, 1));
    label.addView(Ui.text(a, "↗", 24, Design.RED));
    card.addView(label);
    card.setFocusable(true);
    card.setClickable(true);
    card.setContentDescription(name + "，查看");
    card.setOnClickListener(v -> action.run());
  }
}
