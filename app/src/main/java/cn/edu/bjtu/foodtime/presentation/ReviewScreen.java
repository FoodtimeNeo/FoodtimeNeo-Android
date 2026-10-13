package cn.edu.bjtu.foodtime.presentation;

import android.view.View;
import android.widget.*;
import cn.edu.bjtu.foodtime.domain.model.Models.*;

final class ReviewScreen {
  static void create(MainActivity a, LinearLayout page, MainActivity.Route route) {
    Dish dish = a.repo.dish(route.id());
    if (dish == null) throw new IllegalArgumentException("菜品不存在");
    a.heading(page, "02", "评价", dish.name());
    LinearLayout card = Ui.card(page);
    card.addView(Ui.title(a, "给菜品打分", 24));
    Ui.gap(card, 16);
    String key = a.draftKey("rating");
    int[] rating = {a.drafts.getInt(key, 0)};
    LinearLayout stars = Ui.row(a);
    card.addView(stars);
    TextView[] buttons = new TextView[5];
    Runnable paint =
        () -> {
          for (int i = 0; i < 5; i++) {
            buttons[i].setText(i < rating[0] ? "★" : "☆");
            buttons[i].setTextColor(i < rating[0] ? Design.RED : Design.MUTED);
            buttons[i].setContentDescription((i + 1) + "星" + (rating[0] == i + 1 ? "，已选中" : ""));
          }
        };
    for (int i = 0; i < 5; i++) {
      int n = i + 1;
      buttons[i] =
          Ui.button(
              a,
              "☆",
              false,
              () -> {
                rating[0] = n;
                a.drafts.putInt(key, n);
                paint.run();
              });
      buttons[i].setTextSize(28);
      buttons[i].setPadding(0, 0, 0, 0);
      buttons[i].setBackground(Ui.ripple(a, Design.PAPER, 0, 0));
      stars.addView(buttons[i], new LinearLayout.LayoutParams(0, Ui.dp(a, 56), 1));
    }
    paint.run();
    Ui.gap(card, 24);
    EditText text = Ui.field(card, "评价内容", false, true);
    TextView error = Ui.text(a, "", 14, Design.RED);
    error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    card.addView(error);
    Ui.gap(card, 8);
    card.addView(Ui.text(a, "审核通过后公开", 14, Design.MUTED));
    Ui.gap(card, 16);
    Ui.submit(card,"提交评价",error,() -> {
      int score=rating[0];String content=text.getText().toString();
      if(score<1||content.isBlank()) throw new IllegalArgumentException("请选择评分并填写评价");
      return () -> a.repo.submitReview(dish.id(),score,content);
    },() -> {a.clearDraft(route);a.message("评价已提交");a.back();});
  }
}
