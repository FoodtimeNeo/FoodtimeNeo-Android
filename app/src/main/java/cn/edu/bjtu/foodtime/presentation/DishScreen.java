package cn.edu.bjtu.foodtime.presentation;

import android.widget.*;
import cn.edu.bjtu.foodtime.domain.model.Models.*;
import java.util.*;

final class DishScreen {
  static void dishes(MainActivity a, LinearLayout page, MainActivity.Route route) {
    Stall stall = a.repo.stall(route.id());
    if (stall == null || !stall.active()) throw new IllegalArgumentException("档口不存在或已停用");
    a.heading(page, "01.2", stall.name(), "");
    List<Dish> all = a.repo.dishes(stall.id());
    if (all.isEmpty()) Ui.empty(page, "还没有菜品", "可以通过下方入口投稿一道新菜。");
    else CatalogList.show(a, page, "搜索菜品", "当前档口的菜名", "道菜品", all, Dish::name,
        (list, dish) -> {
          LinearLayout row = Ui.card(list);
          row.setOrientation(LinearLayout.HORIZONTAL);
          row.setGravity(android.view.Gravity.CENTER_VERTICAL);
          row.setPadding(Ui.dp(a, 16), Ui.dp(a, 12), Ui.dp(a, 16), Ui.dp(a, 12));
          TextView n = Ui.title(a, dish.name(), 21);
          row.addView(n, new LinearLayout.LayoutParams(0, -2, 1));
          TextView price = Ui.text(a, "¥" + dish.price().setScale(2), 16, Design.RED);
          row.addView(price);
          row.setClickable(true);
          row.setFocusable(true);
          row.setOnClickListener(v -> a.go("detail", dish.id()));
        });
    Ui.gap(page, 16);
    Ui.action(page, "投稿一道新菜", true, () -> a.go("submit-dish", stall.id()));
  }

  static void detail(MainActivity a, LinearLayout page, MainActivity.Route route) {
    Dish d = a.repo.dish(route.id());
    if (d == null || !d.active()) throw new IllegalArgumentException("菜品不存在或已下架");
    Stall s = a.repo.stall(d.stallId());
    Hall h = a.repo.hall(s.diningHallId());
    a.heading(page, "01.3", d.name(), h.name() + " · " + s.name());
    LinearLayout hero = Ui.card(page);
    hero.setPadding(0, 0, 0, Ui.dp(a, 4));
    FrameLayout photo =
        (FrameLayout)
            Ui.image(
                a,
                a.repo.photos(d.id()).isEmpty() ? "" : a.repo.photos(d.id()).get(0).imageUrl(),
                "暂未上传菜品图片",
                true);
    hero.addView(photo);
    TextView upload = Ui.button(a, "+", false, () -> a.go("upload-photo", d.id()));
    upload.setTextSize(24);
    upload.setPadding(0, 0, 0, 0);
    upload.setContentDescription("上传菜品图片");
    FrameLayout.LayoutParams up =
        new FrameLayout.LayoutParams(
            Ui.dp(a, 48), Ui.dp(a, 48), android.view.Gravity.TOP | android.view.Gravity.END);
    up.setMargins(0, Ui.dp(a, 8), Ui.dp(a, 8), 0);
    photo.addView(upload, up);
    TextView album = Ui.button(a, "图册", false, () -> a.go("gallery", d.id()));
    album.setContentDescription("查看菜品图册");
    FrameLayout.LayoutParams al =
        new FrameLayout.LayoutParams(
            -2, Ui.dp(a, 48), android.view.Gravity.BOTTOM | android.view.Gravity.END);
    al.setMargins(0, 0, Ui.dp(a, 8), Ui.dp(a, 8));
    photo.addView(album, al);
    LinearLayout heroText = Ui.row(a);
    heroText.setPadding(Ui.dp(a, 16), Ui.dp(a, 12), Ui.dp(a, 16), Ui.dp(a, 8));
    heroText.addView(
        Ui.title(a, "¥" + d.price().setScale(2), 25), new LinearLayout.LayoutParams(0, -2, 1));
    hero.setOnClickListener(v -> a.go("gallery", d.id()));
    hero.addView(heroText);
    if(!d.description().isBlank()) {
      TextView description=Ui.text(a,d.description(),16,Design.MUTED);
      description.setPadding(Ui.dp(a,16),0,Ui.dp(a,16),Ui.dp(a,16));hero.addView(description);
    }
    LinearLayout score = Ui.card(page);
    score.addView(Ui.title(a, "评分", 22));
    Ui.gap(score, 8);
    List<Review> reviews = a.repo.reviews(d.id());
    RatingSummary summary=a.repo.rating(d.id());
    float avg=summary.averageScore()==null?0:summary.averageScore().floatValue();
    score.addView(
        Ui.text(
            a,
            summary.averageScore()==null
                ? "暂无评分"
                : "★".repeat(Math.round(avg)) + "  " + String.format(Locale.ROOT, "%.1f / 5", avg),
            22,
            summary.averageScore()==null ? Design.MUTED : Design.RED));
    score.addView(
        Ui.text(
            a,
            summary.reviewCount()+" 条评价 · 每日更新",
            14,
            Design.MUTED));
    LinearLayout action = Ui.row(a);
    action.setGravity(android.view.Gravity.CENTER);
    action.addView(
        Ui.button(a, "写点评", true, () -> a.go("review", d.id())),
        new LinearLayout.LayoutParams(0, Ui.dp(a, 48), 1));
    page.addView(action);
    Ui.gap(page, 20);
    page.addView(Ui.title(a, "评论", 25));
    Ui.gap(page, 12);
    if (reviews.isEmpty()) Ui.empty(page, "暂无评论", "");
    for (Review r : reviews) {
      LinearLayout c = Ui.card(page);
      c.addView(Ui.text(a, r.userDisplayName().isEmpty() ? r.userId() : r.userDisplayName(), 12, Design.BLUE));
      c.addView(Ui.text(a, "★".repeat(r.rating()) + " · " + r.rating() + " / 5", 16, Design.RED));
      Ui.gap(c, 5);
      c.addView(Ui.text(a, r.content(), 16, Design.INK));
    }
  }

  static void gallery(MainActivity a, LinearLayout page, MainActivity.Route route) {
    Dish d = a.repo.dish(route.id());
    a.heading(page, "01.4", d.name() + " · 图册", "");
    List<Photo> photos = a.repo.photos(d.id());
    if (photos.isEmpty()) Ui.empty(page, "暂无图片", "");
    for (Photo p : photos) {
      LinearLayout c = Ui.card(page);
      c.setPadding(0, 0, 0, Ui.dp(a, 4));
      c.addView(Ui.image(a, p.imageUrl(), "菜品图片", true));
    }
    Ui.action(page, "上传菜品图片", true, () -> a.go("upload-photo", d.id()));
  }
}
