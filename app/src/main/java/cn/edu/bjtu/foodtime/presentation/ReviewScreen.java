package cn.edu.bjtu.foodtime.presentation;

import android.view.View;
import android.widget.*;
import cn.edu.bjtu.foodtime.domain.model.Models.*;

final class ReviewScreen {
  static void create(MainActivity a, LinearLayout page, MainActivity.Route route) {
    Dish dish = a.repo.dish(route.id());
    if (dish == null) throw new IllegalArgumentException("菜品不存在");
    a.heading(page, "02", "记下这一餐", dish.name());
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
    EditText text = Ui.field(card, "写下真实的用餐感受（最多500字）", false, true);
    text.setFilters(
        new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(500)});
    TextView error = Ui.text(a, "", 14, Design.RED);
    error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    card.addView(error);
    Ui.gap(card, 8);
    card.addView(Ui.text(a, "评价会先进入待审核状态，通过后才会公开展示。", 14, Design.MUTED));
    Ui.gap(card, 16);
    Ui.action(
        card,
        "提交评价",
        true,
        () -> {
          try {
            a.repo.submitReview(dish.id(), rating[0], text.getText().toString());
            a.drafts.remove(key);
            a.drafts.remove(a.draftKey("写下真实的用餐感受（最多500字）"));
            a.message("评价已提交，等待审核");
            a.back();
          } catch (RuntimeException e) {
            error.setText(e.getMessage());
          }
        });
  }
}
