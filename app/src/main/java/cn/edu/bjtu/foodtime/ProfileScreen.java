package cn.edu.bjtu.foodtime;

import android.view.Gravity;
import android.widget.*;
import cn.edu.bjtu.foodtime.Models.*;
import java.util.*;

final class ProfileScreen {
  static void profile(MainActivity a, LinearLayout page, MainActivity.Route route) {
    User user = a.repo.currentUser();
    a.heading(page, "02", "干饭档案", "把每一顿，好好记下来");
    LinearLayout identity = Ui.card(page);
    TextView avatar = Ui.title(a, "饭", 28);
    avatar.setTextColor(Design.RED);
    avatar.setGravity(Gravity.CENTER);
    avatar.setBackground(Ui.shape(a, Design.ALT, Design.RED, 999));
    avatar.setContentDescription("默认个人头像");
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
            user.role().equals("superadmin") ? "平台管理员" : user.isAdmin() ? "管理员" : "干饭同学",
            user.isAdmin() ? Design.RED : Design.BLUE));
    LinearLayout counts = Ui.row(a);
    List<Submission> submissions = a.repo.submissions(user.id());
    long pending = submissions.stream().filter(s -> s.status().equals("pending")).count();
    counts.addView(
        Ui.text(a, a.repo.myReviews().size() + " 条评价", 14, Design.MUTED),
        new LinearLayout.LayoutParams(0, -2, 1));
    counts.addView(
        Ui.text(a, submissions.size() + " 次提交", 14, Design.MUTED),
        new LinearLayout.LayoutParams(0, -2, 1));
    counts.addView(
        Ui.text(a, pending + " 待审核", 14, Design.BLUE), new LinearLayout.LayoutParams(0, -2, 1));
    Ui.gap(identity, 20);
    identity.addView(counts);
    LinearLayout menu = Ui.card(page);
    Ui.action(menu, "我的评论  →", false, () -> a.go("mine-reviews", ""));
    Ui.gap(menu, 12);
    Ui.action(menu, "我的提交  →", false, () -> a.go("mine-submissions", ""));
    Ui.gap(menu, 12);
    Ui.action(menu, "修改密码  →", false, () -> a.go("password", ""));
    Ui.gap(menu, 24);
    Ui.action(menu, "退出登录", true, () -> Ui.confirm(a, "退出这本手账？", "本机的评论和投稿记录会保留。", a::logout));
    page.addView(Ui.text(a, "FOODTIME · 校园食堂手账\n离线试用版", 12, Design.MUTED));
  }

  static void reviews(MainActivity a, LinearLayout page, MainActivity.Route route) {
    a.heading(page, "02.1", "我的评论", "写过的每一句，都记得");
    List<Review> reviews = a.repo.myReviews();
    if (reviews.isEmpty()) Ui.empty(page, "还没有已发布评论", "点评审核通过后，会出现在这里；待审核点评请在“我的提交”查看。");
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
                      "删除后菜品评分会重新计算。",
                      () ->
                          a.safely(
                              () -> {
                                a.repo.deleteReview(r.id());
                                a.message("评论已删除");
                                a.render();
                              })));
      del.setTextColor(Design.RED);
      actions.addView(del, new LinearLayout.LayoutParams(0, -2, 1));
      card.addView(actions);
    }
  }
}
