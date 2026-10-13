package cn.edu.bjtu.foodtime.presentation;

import android.widget.*;
import cn.edu.bjtu.foodtime.domain.model.Models.*;
import java.util.*;

final class SubmissionScreen {
  static String status(String value) {
    return switch (value) {
      case "approved" -> "已通过";
      case "rejected" -> "已驳回";
      default -> "待审核";
    };
  }

  static String type(String value) {
    return switch (value) {
      case "photo" -> "图片提交";
      case "review" -> "评论提交";
      case "stall" -> "档口提交";
      default -> "菜品提交";
    };
  }

  static String date(long value) {
    java.text.SimpleDateFormat format =
        new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
    format.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
    return format.format(new Date(value));
  }

  static void mine(MainActivity a, LinearLayout page, MainActivity.Route route) {
    a.heading(page, "03", "我的提交", "");
    List<Submission> all = a.repo.submissions(a.repo.currentUser().id());
    Spinner filter = new Spinner(a);
    String[] options = {"全部提交", "待审核", "已通过", "已驳回"};
    filter.setAdapter(
        new ArrayAdapter<>(a, android.R.layout.simple_spinner_dropdown_item, options));
    filter.setMinimumHeight(Ui.dp(a, 48));
    filter.setContentDescription("按审核状态筛选");
    page.addView(filter);
    Ui.gap(page, 16);
    LinearLayout list = Ui.col(a);
    page.addView(list);
    filter.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          public void onNothingSelected(AdapterView<?> p) {}

          public void onItemSelected(AdapterView<?> p, android.view.View v, int pos, long id) {
            list.removeAllViews();
            String state =
                pos == 0 ? "" : new String[] {"", "pending", "approved", "rejected"}[pos];
            int count = 0;
            for (Submission s : all)
              if (state.isEmpty() || s.status().equals(state)) {
                count++;
                card(a, list, s);
              }
            if (count == 0) Ui.empty(list, "暂无提交", "");
          }
        });
  }

  static LinearLayout card(MainActivity a, LinearLayout page, Submission s) {
    LinearLayout card = Ui.card(page);
    LinearLayout top = Ui.row(a);
    top.addView(
        Ui.text(a, type(s.type()), 14, Design.MUTED), new LinearLayout.LayoutParams(0, -2, 1));
    top.addView(
        Ui.stamp(
            a,
            status(s.status()),
            s.status().equals("approved")
                ? Design.GREEN
                : s.status().equals("rejected") ? Design.RED : Design.BLUE));
    card.addView(top);
    Ui.gap(card, 12);
    card.addView(Ui.title(a, s.name(), 24));
    Ui.gap(card, 8);
    card.addView(Ui.text(a, "提交ID：" + s.id(), 12, Design.MUTED));
    card.addView(Ui.text(a, date(s.createdAt()), 12, Design.MUTED));
    if(!s.context().isBlank()) card.addView(Ui.text(a,s.context(),14,Design.MUTED));
    else if(s.type().equals("review")||s.type().equals("photo")) card.addView(Ui.text(a,a.repo.context(s.targetId()),14,Design.MUTED));
    if(s.type().equals("dish")) card.addView(Ui.text(a,s.price()==null?"价格未填写":"¥"+s.price().setScale(2),14,Design.MUTED));
    if (s.rating() > 0)
      card.addView(Ui.text(a, "★".repeat(s.rating()) + " / " + s.rating() + "分", 16, Design.RED));
    if (!s.content().isBlank()) {
      Ui.gap(card, 8);
      card.addView(Ui.text(a, s.content(), 16, Design.INK));
    }
    if (!s.imageUrl().isBlank()) {
      Ui.gap(card, 12);
      card.addView(Ui.image(a, s.imageUrl(), "提交的图片", true));
    }
    if (!s.reason().isBlank()) {
      Ui.gap(card, 12);
      card.addView(Ui.text(a, "审核说明：" + s.reason(), 14, Design.RED));
    }
    return card;
  }
}
