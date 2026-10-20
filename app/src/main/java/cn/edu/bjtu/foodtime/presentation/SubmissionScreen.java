package cn.edu.bjtu.foodtime.presentation;

import android.widget.*;
import android.view.View;
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
      case "dish-review-reply" -> "点评回复";
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
    page.addView(Ui.text(a, "通过审核的点评会出现在“我的评论”。", 14, Design.MUTED));
    Ui.gap(page, 16);
    List<Submission> all = a.repo.submissions(a.repo.currentUser().id());
    String statusKey = a.draftKey("submission-status");
    String typeKey = a.draftKey("submission-type");
    String[] statuses = {"", "pending", "approved", "rejected"};
    String[] types = {"", "review", "dish", "stall", "photo"};
    Spinner status = filter(a, page, "审核状态", new String[] {"全部状态", "待审核", "已通过", "已驳回"}, a.drafts.getInt(statusKey, 0));
    Spinner type = filter(a, page, "投稿类型", new String[] {"全部类型", "点评", "菜品", "档口", "图片"}, a.drafts.getInt(typeKey, 0));
    Ui.gap(page, 8);
    TextView count = Ui.text(a, "", 14, Design.MUTED);
    count.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    page.addView(count);
    Ui.gap(page, 16);
    LinearLayout list = Ui.col(a);
    page.addView(list);
    Runnable update = () -> {
      int statusIndex = status.getSelectedItemPosition();
      int typeIndex = type.getSelectedItemPosition();
      a.drafts.putInt(statusKey, statusIndex);
      a.drafts.putInt(typeKey, typeIndex);
      list.removeAllViews();
      int found = 0;
      for (Submission submission : all) {
        if ((statusIndex == 0 || submission.status().equals(statuses[statusIndex]))
            && (typeIndex == 0 || submission.type().equals(types[typeIndex]))) {
          found++;
          card(a, list, submission);
        }
      }
      count.setText("显示 " + found + " / " + all.size() + " 条记录");
      if (found == 0) {
        if (all.isEmpty()) {
          Ui.empty(list, "暂无投稿记录", "从食堂找到档口和菜品，即可在对应页面参与投稿。");
          Ui.action(list, "去逛食堂", true, a::home);
        } else {
          Ui.empty(list, "没有符合筛选条件的记录", "调整类型或状态，或清除筛选查看全部。");
          Ui.action(list, "清除筛选", false, () -> {
            status.setSelection(0);
            type.setSelection(0);
          });
        }
      }
    };
    AdapterView.OnItemSelectedListener listener = new AdapterView.OnItemSelectedListener() {
      public void onNothingSelected(AdapterView<?> parent) {}
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        update.run();
      }
    };
    status.setOnItemSelectedListener(listener);
    type.setOnItemSelectedListener(listener);
    update.run();
  }

  static Spinner filter(MainActivity a, LinearLayout page, String label, String[] options, int selected) {
    TextView caption = Ui.text(a, label, 14, Design.INK);
    page.addView(caption);
    Spinner filter = new Spinner(a);
    filter.setId(View.generateViewId());
    caption.setLabelFor(filter.getId());
    filter.setContentDescription("按" + label + "筛选");
    filter.setAdapter(new ArrayAdapter<>(a, android.R.layout.simple_spinner_dropdown_item, options));
    filter.setMinimumHeight(Ui.dp(a, 48));
    filter.setSelection(Math.max(0, Math.min(options.length - 1, selected)));
    page.addView(filter);
    return filter;
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
    if(!s.userDisplayName().isBlank()) card.addView(Ui.text(a, "投稿者：" + s.userDisplayName(), 12, Design.BLUE));
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
      card.addView(Ui.image(a, Ui.thumb(s.imageUrl()), "提交的图片", true));
    }
    if (!s.reason().isBlank()) {
      Ui.gap(card, 12);
      card.addView(Ui.text(a, "审核说明：" + s.reason(), 14, Design.RED));
    }
    return card;
  }
}
