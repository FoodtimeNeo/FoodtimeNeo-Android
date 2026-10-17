package cn.edu.bjtu.foodtime.presentation;

import android.view.Gravity;
import android.widget.*;
import cn.edu.bjtu.foodtime.domain.model.Models.*;
import java.util.*;

final class ProfileScreen {
  static void profile(MainActivity a, LinearLayout page, MainActivity.Route route) {
    User user = a.repo.currentUser();
    a.heading(page, "02", "我的", "");
    LinearLayout identity = Ui.card(page);
    android.view.View avatar=Ui.image(a,Ui.thumb(user.avatarUrl()),user.displayName().isEmpty()?"用户":user.displayName().substring(0,1),false);
    avatar.setBackground(Ui.shape(a,Design.ALT,Design.RED,999));avatar.setClipToOutline(true);
    avatar.setContentDescription("个人头像");
    identity.addView(avatar, new LinearLayout.LayoutParams(Ui.dp(a, 64), Ui.dp(a, 64)));
    Ui.gap(identity, 16);
    identity.addView(Ui.title(a, user.displayName(), 25));
    Ui.gap(identity, 8);
    identity.addView(Ui.text(a, "ID：" + user.id(), 12, Design.MUTED));
    identity.addView(Ui.text(a, user.email(), 14, Design.MUTED));
    Ui.gap(identity, 16);
    identity.addView(
        Ui.stamp(
            a,
            user.role().equals("superadmin") ? "平台管理员" : user.isAdmin() ? "管理员" : "普通用户",
            user.isAdmin() ? Design.RED : Design.BLUE));
    LinearLayout menu = Ui.card(page);
    Ui.action(menu, "我的评论 · 已发布", false, () -> a.go("mine-reviews", ""));
    Ui.gap(menu, 12);
    Ui.action(menu, "我的提交 · 审核记录", false, () -> a.go("mine-submissions", ""));
    Ui.gap(menu, 12);
    Ui.action(menu, "修改密码", false, () -> a.go("password", ""));
    Ui.gap(menu, 24);
    Ui.action(menu, "退出登录", true, () -> Ui.confirm(a, "退出登录？", "", a::logout));
  }

  static void reviews(MainActivity a, LinearLayout page, MainActivity.Route route) {
    a.heading(page, "02.1", "我的评论", "");
    List<Review> reviews = a.repo.myReviews();
    if (reviews.isEmpty()) {
      Ui.empty(page, "暂无已发布评论", "刚提交的点评需要先审核，可在“我的提交”查看进度和驳回原因。");
      Ui.action(page, "查看点评审核记录", true, () -> a.go("mine-submissions", ""));
    }
    for (Review r : reviews) {
      LinearLayout card = Ui.card(page);
      card.addView(Ui.text(a, "评论ID：" + r.id(), 12, Design.MUTED));
      card.addView(Ui.text(a, SubmissionScreen.date(r.createdAt()), 12, Design.MUTED));
      Ui.gap(card, 12);
      card.addView(Ui.text(a, a.repo.context(r.dishId()), 14, Design.BLUE));
      Ui.gap(card, 8);
      card.addView(Ui.text(a, "★".repeat(r.rating()) + " / " + r.rating() + "分", 16, Design.RED));
      card.addView(Ui.text(a, r.content(), 16, Design.INK));
      Ui.gap(card, 12);
      LinearLayout actions = Ui.row(a);
      actions.addView(
          Ui.button(a, "查看菜品", false, () -> a.go("detail", r.dishId())),
          new LinearLayout.LayoutParams(0, -2, 1));
      TextView del =
          Ui.button(
              a,
              "删除",
              false,
              () ->
                  Ui.confirm(
                      a,
                      "删除这条评论？",
                      "删除后不可恢复",
                      () -> a.request(null,null,() -> a.repo.deleteReview(r.id()),() -> {a.message("评论已删除");a.render();})));
      del.setTextColor(Design.RED);
      actions.addView(del, new LinearLayout.LayoutParams(0, -2, 1));
      card.addView(actions);
    }
  }
}
