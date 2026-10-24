package cn.edu.bjtu.foodtime.presentation;

import android.widget.*;
import cn.edu.bjtu.foodtime.domain.model.Models.*;
import cn.edu.bjtu.foodtime.domain.validation.Validation;

final class UploadScreen {
  static void photo(MainActivity a, LinearLayout page, MainActivity.Route route) {
    Dish dish = a.repo.dish(route.id());
    a.heading(page, "01.4", "上传图片", dish.name());
    LinearLayout card = Ui.card(page);
    String key = a.draftKey("image");
    String image = a.drafts.getString(key, "");
    card.addView(Ui.image(a, image, "请选择菜品实拍图片", true));
    Ui.gap(card, 16);
    Ui.action(card, image.isBlank() ? "从相册选择" : "更换图片", false, () -> a.pickImage(key));
    Ui.gap(card, 16);
    TextView error=Ui.text(a,"",14,Design.RED);card.addView(error);
    Ui.submit(card,"提交图片",error,() -> () -> a.repo.submitPhoto(dish.id(),image),
        () -> {a.clearDraft(route);a.message("图片已提交");a.back();});
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
        isStall ? "投稿档口" : "投稿菜品",
        isStall ? a.repo.hall(route.id()).name() : a.repo.stall(route.id()).name());
    LinearLayout card = Ui.card(page);
    EditText name = Ui.field(card, isStall ? "档口名称" : "菜品名称", false, false);
    EditText description = Ui.field(card, "简短描述（可选）", false, true);
    EditText price = isStall ? null : Ui.field(card, "参考价格（元，可选）", false, false);
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
    Ui.submit(card,"提交投稿",error,() -> {
      String title=Validation.text(name.getText().toString(),isStall?100:120,"名称"),body=description.getText().toString();
      String amount=price==null?"":price.getText().toString();java.math.BigDecimal value=amount.isBlank()?null:Validation.price(amount);
      if(image.isBlank()) throw new IllegalArgumentException("请选择图片");
      return () -> {if(isStall) a.repo.submitStall(route.id(),title,body,image);else a.repo.submitDish(route.id(),title,body,value,image);};
    },() -> {a.clearDraft(route);a.message("投稿已提交");a.back();});
  }
}
