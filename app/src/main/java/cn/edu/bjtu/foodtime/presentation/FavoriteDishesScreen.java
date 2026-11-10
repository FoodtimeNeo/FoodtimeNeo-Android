package cn.edu.bjtu.foodtime.presentation;

import android.widget.LinearLayout;
import android.widget.TextView;
import cn.edu.bjtu.foodtime.domain.model.Models.DishSearchPage;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

final class FavoriteDishesScreen {
  static void show(MainActivity activity, LinearLayout page, MainActivity.Route route) {
    activity.heading(page, "", "想吃清单", "存下想吃的菜，下次直接找。");
    new FavoriteDishesScreen(activity, page);
  }

  private final MainActivity activity;
  private final LinearLayout rows;
  private final TextView status, error, reload;
  private final String requestKey = "favorite-dishes:" + UUID.randomUUID();

  private FavoriteDishesScreen(MainActivity activity, LinearLayout page) {
    this.activity = activity;
    status = Ui.text(activity, "", 14, Design.MUTED);
    error = Ui.text(activity, "", 14, Design.RED);
    Ui.observeError(error);
    reload = Ui.button(activity, "重新加载", false, () -> load(1));
    rows = Ui.col(activity);
    page.addView(status);
    page.addView(error);
    page.addView(reload);
    page.addView(rows);
    page.post(() -> {
      if (page.isAttachedToWindow()) load(1);
    });
  }

  private void load(int page) {
    rows.removeAllViews();
    status.setText("正在加载想吃清单…");
    AtomicReference<DishSearchPage> data = new AtomicReference<>();
    activity.read(requestKey, reload, error, () -> data.set(activity.repo.favoriteDishes(page)), () -> {
      DishSearchPage result = data.get();
      status.setText(result.items().isEmpty() ? "这页还没有想吃的菜。"
          : "共 " + result.total() + " 道菜 · 第 " + page + " 页");
      for (var dish : result.items()) {
        DishSearchScreen.appendRow(activity, rows, dish, () -> activity.go("detail", dish.id()));
      }
      if (page > 1) Ui.action(rows, "上一页", false, () -> load(page - 1));
      if (result.hasMore()) Ui.action(rows, "下一页", false, () -> load(page + 1));
    }, () -> status.setText(""));
  }
}
