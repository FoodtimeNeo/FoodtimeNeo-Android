package cn.edu.bjtu.foodtime.presentation;

import android.app.AlertDialog;
import android.widget.*;
import cn.edu.bjtu.foodtime.domain.model.Models.*;
import java.util.*;

final class AdminScreen {
  static void guard(MainActivity a) {
    if (a.repo.currentUser() == null || !a.repo.currentUser().isAdmin())
      throw new IllegalStateException("需要管理员权限");
  }

  static void home(MainActivity a, LinearLayout page, MainActivity.Route route) {
    guard(a);
    a.heading(page, "02", "管理手账", "让每条信息，都值得信赖");
    long pending =
        a.repo.submissions(null).stream().filter(s -> s.status().equals("pending")).count();
    LinearLayout card = Ui.card(page);
    card.addView(Ui.title(a, pending + " 条投稿待审核", 28));
    Ui.gap(card, 20);
    Ui.action(card, "审核投稿", true, () -> a.go("admin-audit", ""));
    Ui.gap(card, 12);
    Ui.action(card, "食堂 · 档口 · 菜品管理", false, () -> a.go("admin-data", "halls~"));
    Ui.gap(card, 12);
    Ui.action(card, "评论管理", false, () -> a.go("admin-reviews", ""));
    Ui.gap(card, 12);
    Ui.action(card, "审核日志", false, () -> a.go("admin-logs", ""));
    if (a.repo.currentUser().role().equals("superadmin")) {
      Ui.gap(card, 12);
      Ui.action(card, "用户与权限管理", false, () -> a.go("admin-users", ""));
    }
  }

  static void audit(MainActivity a, LinearLayout page, MainActivity.Route route) {
    guard(a);
    a.heading(page, "02.1", "审核投稿", "通过后公开展示，驳回请说明原因");
    int count = 0;
    for (Submission s : a.repo.submissions(null))
      if (s.status().equals("pending")) {
        count++;
        LinearLayout card = SubmissionScreen.card(a, page, s);
        Ui.gap(card, 16);
        Ui.action(
            card,
            "通过审核",
            false,
            () ->
                Ui.confirm(
                    a,
                    "通过这条投稿？",
                    "通过后将更新公开内容。",
                    () ->
                        a.safely(
                            () -> {
                              a.repo.moderate(s.id(), "approve", "");
                              a.message("审核已通过");
                              a.render();
                            })));
        Ui.gap(card, 8);
        Ui.action(card, "驳回投稿", false, () -> reason(a, s));
      }
    if (count == 0) Ui.empty(page, "投稿都处理完了", "下一条分享来了，再来看看。");
  }

  private static void reason(MainActivity a, Submission s) {
    LinearLayout form = Ui.col(a);
    form.setPadding(Ui.dp(a, 20), Ui.dp(a, 16), Ui.dp(a, 20), Ui.dp(a, 8));
    EditText input = Ui.field(form, "驳回原因（必填）", false, true);
    TextView error = Ui.text(a, "", 14, Design.RED);
    form.addView(error);
    AlertDialog dialog =
        new AlertDialog.Builder(a)
            .setTitle("驳回投稿")
            .setView(form)
            .setNegativeButton("取消", null)
            .setPositiveButton("确认驳回", null)
            .create();
    dialog.setOnShowListener(
        v -> {
          dialog.getWindow().setBackgroundDrawable(Ui.shape(a, Design.PAPER, Design.INK, 10));
          dialog.getButton(-1).setTextColor(Design.RED);
          dialog
              .getButton(-1)
              .setOnClickListener(
                  w -> {
                    try {
                      a.repo.moderate(s.id(), "reject", input.getText().toString());
                      dialog.dismiss();
                      a.message("已驳回投稿");
                      a.render();
                    } catch (RuntimeException e) {
                      error.setText(e.getMessage());
                    }
                  });
        });
    dialog.show();
  }

  static void data(MainActivity a, LinearLayout page, MainActivity.Route route) {
    guard(a);
    String[] args = route.id().split("~", -1);
    String table = args[0], parent = args.length > 1 ? args[1] : "";
    String title = table.equals("halls") ? "食堂管理" : table.equals("stalls") ? "档口管理" : "菜品管理";
    a.heading(page, "02.2", title, "停用的信息不会出现在同学的列表中");
    List<AdminEntity> list = a.repo.entities(table, parent);
    if (list.isEmpty()) Ui.empty(page, "还没有记录", "点击下方按钮新增一条信息。");
    for (AdminEntity o : list) {
      LinearLayout card = Ui.card(page);
      card.addView(Ui.title(a, o.name(), 25));
      Ui.gap(card, 8);
      card.addView(
          Ui.stamp(
              a,
              o.active() ? "启用" : "停用", o.active() ? Design.GREEN : Design.RED));
      if (table.equals("dishes")) {
        Ui.gap(card, 8);
        card.addView(Ui.text(a, "¥" + o.price(), 16, Design.RED));
      }
      Ui.gap(card, 16);
      Ui.action(card, "编辑信息与状态", false, () -> a.go("admin-edit", table + "~" + o.id()));
      if (!table.equals("dishes")) {
        Ui.gap(card, 8);
        Ui.action(
            card,
            table.equals("halls") ? "管理该食堂档口" : "管理该档口菜品",
            false,
            () ->
                a.go(
                    "admin-data",
                    (table.equals("halls") ? "stalls" : "dishes") + "~" + o.id()));
      }
    }
    Ui.action(
        page,
        "新增" + (table.equals("halls") ? "食堂" : table.equals("stalls") ? "档口" : "菜品"),
        true,
        () -> a.go("admin-new", table + "~" + parent));
  }

  static void edit(MainActivity a, LinearLayout page, MainActivity.Route route) {
    guard(a);
    String[] args = route.id().split("~", -1);
    String table = args[0], value = args.length > 1 ? args[1] : "";
    boolean creating = route.kind().equals("admin-new");
    AdminEntity o = creating ? null : a.repo.entity(table, value);
    if (!creating && o == null) throw new IllegalArgumentException("记录不存在");
    a.heading(page, "02.2", creating ? "添一条新信息" : "编辑信息", "名称、价格和状态按实际情况填写");
    LinearLayout form = Ui.card(page);
    EditText name = Ui.field(form, "名称", false, false);
    EditText description = Ui.field(form, "描述（可选）", false, true);
    EditText price = table.equals("dishes") ? Ui.field(form, "价格（元）", false, false) : null;
    if (price != null) price.setInputType(8194);
    if (o != null) {
      if (!a.drafts.containsKey(a.draftKey("loaded"))) {
        name.setText(o.name());
        description.setText(o.description());
        if (price != null) price.setText(o.price());
        a.drafts.putBoolean(a.draftKey("loaded"), true);
      }
    }
    Switch enabled = new Switch(a);
    enabled.setText("启用并对同学展示");
    enabled.setTextColor(Design.INK);
    enabled.setTextSize(16);
    enabled.setMinHeight(Ui.dp(a, 48));
    String activeKey = a.draftKey("active");
    enabled.setChecked(a.drafts.getBoolean(activeKey, o == null || o.active()));
    enabled.setOnCheckedChangeListener((v, checked) -> a.drafts.putBoolean(activeKey, checked));
    form.addView(enabled);
    Ui.gap(form, 16);
    String imageKey = a.draftKey("image");
    String image = a.drafts.getString(imageKey, o == null ? "" : o.coverImageUrl());
    if (!table.equals("dishes")) {
      form.addView(Ui.image(a, image, "可添加封面", true));
      Ui.gap(form, 16);
      Ui.action(form, "选择封面图片", false, () -> a.pickImage(imageKey));
      Ui.gap(form, 16);
    }
    TextView error = Ui.text(a, "", 14, Design.RED);
    form.addView(error);
    Ui.gap(form, 12);
    Ui.action(
        form,
        "保存信息",
        true,
        () -> {
          try {
            a.repo.editEntity(
                table,
                creating ? "" : value,
                creating ? value : "",
                name.getText().toString(),
                description.getText().toString(),
                price == null ? "" : price.getText().toString(),
                image,
                enabled.isChecked());
            String prefix = route.kind() + ":" + route.id() + ":";
            for (String key : new HashSet<>(a.drafts.keySet()))
              if (key.startsWith(prefix)) a.drafts.remove(key);
            a.message("信息已保存");
            a.back();
          } catch (RuntimeException e) {
            error.setText(e.getMessage());
          }
        });
  }

  static void reviews(MainActivity a, LinearLayout page, MainActivity.Route route) {
    guard(a);
    a.heading(page, "02.3", "评论管理", "清理违规或无关的内容");
    List<Review> list = a.repo.reviews(null);
    if (list.isEmpty()) Ui.empty(page, "暂无公开评论", "审核通过的评论将出现在这里。");
    for (Review r : list) {
      LinearLayout card = Ui.card(page);
      card.addView(Ui.text(a, r.userId(), 12, Design.BLUE));
      Ui.gap(card, 8);
      card.addView(Ui.text(a, a.repo.context(r.dishId()), 14, Design.MUTED));
      card.addView(Ui.text(a, "★".repeat(r.rating()), 16, Design.RED));
      card.addView(Ui.text(a, r.content(), 16, Design.INK));
      Ui.gap(card, 12);
      Ui.action(
          card,
          "删除违规评论",
          false,
          () ->
              Ui.confirm(
                  a,
                  "删除这条评论？",
                  "删除后会更新该菜品的评分。",
                  () ->
                      a.safely(
                          () -> {
                            a.repo.removeReviewAsAdmin(r.id());
                            a.render();
                          })));
    }
  }

  static void users(MainActivity a, LinearLayout page, MainActivity.Route route) {
    a.heading(page, "02.4", "用户与权限", "平台管理员可维护角色与账号状态");
    for (User u : a.repo.users()) {
      LinearLayout card = Ui.card(page);
      card.addView(Ui.title(a, u.displayName(), 24));
      card.addView(Ui.text(a, u.email(), 14, Design.MUTED));
      card.addView(Ui.text(a, u.role() + " · " + (u.active() ? "启用" : "停用"), 14, Design.BLUE));
      if (!u.id().equals(a.repo.currentUser().id())) {
        Ui.gap(card, 12);
        Ui.action(
            card,
            u.active() ? "停用账号" : "启用账号",
            false,
            () ->
                Ui.confirm(
                    a,
                    "修改账号状态？",
                    u.email(),
                    () ->
                        a.safely(
                            () -> {
                              a.repo.updateUser(u.id(), u.role(), !u.active());
                              a.render();
                            })));
        Ui.gap(card, 8);
        Ui.action(
            card,
            "修改角色",
            false,
            () ->
                new AlertDialog.Builder(a)
                    .setTitle("设置角色")
                    .setItems(
                        new String[] {"普通用户", "管理员", "平台管理员"},
                        (dialog, n) ->
                            Ui.confirm(
                                a,
                                "确认修改角色？",
                                u.email(),
                                () ->
                                    a.safely(
                                        () -> {
                                          a.repo.updateUser(
                                              u.id(),
                                              new String[] {"user", "admin", "superadmin"}[n],
                                              u.active());
                                          a.render();
                                        })))
                    .show());
      }
    }
  }

  static void logs(MainActivity a, LinearLayout page, MainActivity.Route route) {
    guard(a);
    a.heading(page, "02.5", "审核日志", "每一次处理，都留下记录");
    List<Audit> all = a.repo.audits();
    if (all.isEmpty()) Ui.empty(page, "还没有审核操作", "处理投稿后，这里会记录操作者与结果。");
    for (Audit audit : all) {
      LinearLayout card = Ui.card(page);
      card.addView(
          Ui.stamp(
              a,
              audit.action().equals("approve") ? "通过" : "驳回",
              audit.action().equals("approve") ? Design.GREEN : Design.RED));
      Ui.gap(card, 12);
      card.addView(Ui.text(a, "提交：" + audit.submissionId(), 12, Design.MUTED));
      card.addView(Ui.text(a, "管理员：" + audit.moderatorId(), 12, Design.MUTED));
      card.addView(Ui.text(a, SubmissionScreen.date(audit.createdAt()), 12, Design.MUTED));
      if (!audit.reason().isBlank()) card.addView(Ui.text(a, audit.reason(), 16, Design.INK));
    }
  }
}
