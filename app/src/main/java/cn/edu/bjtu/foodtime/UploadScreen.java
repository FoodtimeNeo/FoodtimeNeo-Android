package cn.edu.bjtu.foodtime;

import android.widget.*;
import cn.edu.bjtu.foodtime.Models.*;

final class UploadScreen {
  static void photo(MainActivity a, LinearLayout page, MainActivity.Route route) {
    Dish dish = a.repo.dish(route.id());
    a.heading(page, "01.4", "贴一张菜品照片", dish.name());
    LinearLayout card = Ui.card(page);
    String key = a.draftKey("image");
    String image = a.drafts.getString(key, "");
    card.addView(Ui.image(a, image, "请选择菜品实拍图片", true));
    Ui.gap(card, 16);
    Ui.action(card, image.isBlank() ? "从相册选择" : "更换图片", false, () -> a.pickImage(key));
    Ui.gap(card, 16);
    card.addView(Ui.text(a, "提交后进入待审核状态，审核通过后展示在图册。", 14, Design.MUTED));
    Ui.gap(card, 16);
    Ui.action(
        card,
        "提交图片",
        true,
        () ->
            a.safely(
                () -> {
                  a.repo.submitPhoto(dish.id(), image);
                  a.drafts.remove(key);
                  a.message("图片已提交，等待审核");
                  a.back();
                }));
  }

  static void dish(MainActivity a, LinearLayout page, MainActivity.Route route) {
    form(a, page, route, false);
  }

  static void stall(MainActivity a, LinearLayout page, MainActivity.Route route) {
    form(a, page, route, true);
  }

  private static void form(
      MainActivity a, LinearLayout page, MainActivity.Route route, boolean isStall) {
    a.heading(
        page,
        "03",
        isStall ? "添一个新档口" : "推荐一道新菜",
        isStall ? a.repo.hall(route.id()).name() : a.repo.stall(route.id()).name());
    LinearLayout card = Ui.card(page);
    EditText name = Ui.field(card, isStall ? "档口名称" : "菜品名称", false, false);
    EditText description = Ui.field(card, "简短描述（可选）", false, true);
    EditText price = isStall ? null : Ui.field(card, "参考价格（元）", false, false);
    if (price != null) price.setInputType(8194);
    String imageKey = a.draftKey("image");
    String image = a.drafts.getString(imageKey, "");
    card.addView(Ui.image(a, image, isStall ? "添加档口封面" : "添加菜品实拍图", true));
    Ui.gap(card, 16);
    Ui.action(card, image.isBlank() ? "从相册选择图片" : "更换图片", false, () -> a.pickImage(imageKey));
    Ui.gap(card, 16);
    TextView error = Ui.text(a, "", 14, Design.RED);
    error.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
    card.addView(error);
    Ui.gap(card, 8);
    card.addView(Ui.text(a, "审核通过后，将收录到对应的食堂或档口。", 14, Design.MUTED));
    Ui.gap(card, 16);
    Ui.action(
        card,
        "提交投稿",
        true,
        () -> {
          try {
            if (isStall)
              a.repo.submitStall(
                  route.id(), name.getText().toString(), description.getText().toString(), image);
            else
              a.repo.submitDish(
                  route.id(),
                  name.getText().toString(),
                  description.getText().toString(),
                  Validation.price(price.getText().toString()),
                  image);
            String prefix = route.kind() + ":" + route.id() + ":";
            for (String key : new java.util.HashSet<>(a.drafts.keySet()))
              if (key.startsWith(prefix)) a.drafts.remove(key);
            a.message("投稿已提交，等待审核");
            a.back();
          } catch (RuntimeException e) {
            error.setText(e.getMessage());
          }
        });
  }
}
