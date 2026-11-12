package cn.edu.bjtu.foodtime.presentation;

import android.app.AlertDialog;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.edu.bjtu.foodtime.domain.model.Models.ModerationDecision;
import cn.edu.bjtu.foodtime.domain.model.Models.ModerationDecisionPage;
import cn.edu.bjtu.foodtime.domain.model.Models.MyReport;
import cn.edu.bjtu.foodtime.domain.model.Models.MyReportPage;
import cn.edu.bjtu.foodtime.domain.model.Models.ReportAction;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** Read-only report receipts and moderation decisions for the current account. */
final class ReportReceiptScreen {
  private final MainActivity activity;
  private final LinearLayout body;
  private final String requestKey = "report-receipts:" + UUID.randomUUID();
  private LinearLayout list;
  private TextView status;
  private boolean decisions;
  private int page = 1;
  private boolean loading;

  private ReportReceiptScreen(MainActivity activity, LinearLayout page) {
    this.activity = activity;
    body = Ui.col(activity);
    page.addView(body);
  }

  static void show(MainActivity activity, LinearLayout page, MainActivity.Route route) {
    activity.heading(page, "04.2", "举报与处理决定", "公开结果、入箱时间与申诉截止");
    new ReportReceiptScreen(activity, page).render();
  }

  private void render() {
    body.removeAllViews();
    LinearLayout tabs = Ui.row(activity);
    tabs.addView(tab("我的举报", false));
    tabs.addView(tab("收到的决定", true));
    body.addView(tabs);
    status = Ui.text(activity, loading ? "正在加载…" : "", 13, Design.MUTED);
    body.addView(status);
    list = Ui.col(activity);
    body.addView(list);
    load(1);
  }

  private TextView tab(String label, boolean wantDecisions) {
    return Ui.button(activity, label, decisions == wantDecisions, () -> {
      if (decisions == wantDecisions) return;
      decisions = wantDecisions;
      render();
    });
  }

  private void load(int pageNumber) {
    loading = true;
    if (status != null) status.setText("正在加载…");
    if (decisions) loadDecisions(pageNumber);
    else loadReports(pageNumber);
  }

  private void loadReports(int pageNumber) {
    AtomicReference<MyReportPage> result = new AtomicReference<>();
    activity.read(requestKey + ":reports:" + pageNumber, null, null,
        () -> result.set(activity.repo.myReports(pageNumber)), () -> {
          loading = false;
          MyReportPage data = result.get();
          status.setText(data.items().isEmpty() ? "还没有举报记录。" : "共 " + data.total() + " 条");
          for (MyReport item : data.items()) reportCard(item);
          if (data.hasMore()) Ui.action(list, "加载更多", false, () -> load(pageNumber + 1));
        }, () -> loading = false);
  }

  private void loadDecisions(int pageNumber) {
    AtomicReference<ModerationDecisionPage> result = new AtomicReference<>();
    activity.read(requestKey + ":decisions:" + pageNumber, null, null,
        () -> result.set(activity.repo.myModerationDecisions(pageNumber)), () -> {
          loading = false;
          ModerationDecisionPage data = result.get();
          status.setText(data.items().isEmpty() ? "还没有收到的处理决定。" : "共 " + data.total() + " 条");
          for (ModerationDecision item : data.items()) decisionCard(item);
          if (data.hasMore()) Ui.action(list, "加载更多", false, () -> load(pageNumber + 1));
        }, () -> loading = false);
  }

  private void reportCard(MyReport item) {
    LinearLayout card = Ui.card(list);
    card.addView(Ui.title(activity, typeLabel(item.targetType()) + " · " + statusLabel(item.status()), 17));
    card.addView(Ui.text(activity, SubmissionScreen.date(item.createdAt()), 12, Design.MUTED));
    Ui.gap(card, 6);
    card.addView(Ui.text(activity, "举报理由：" + item.reason(), 14, Design.INK));
    if (!item.excerpt().isBlank()) card.addView(Ui.text(activity, item.excerpt(), 13, Design.MUTED));
    if (!item.outcome().isBlank()) {
      card.addView(Ui.text(activity, "处理结果：" + outcomeLabel(item.outcome()), 15, Design.RED));
      card.addView(Ui.text(activity, "公开理由：" + item.publicReason(), 14, Design.INK));
    } else if ("legacy".equals(item.status())) {
      card.addView(Ui.text(activity, "历史记录，未提供详细结果。", 13, Design.MUTED));
    }
    Ui.link(card, "追问记录", () -> activity.go("report-followups", item.id()));
  }

  private void decisionCard(ModerationDecision item) {
    LinearLayout card = Ui.card(list);
    card.addView(Ui.title(activity, typeLabel(item.targetType()) + " · " + outcomeLabel(item.outcome()), 17));
    card.addView(Ui.text(activity, "公开理由：" + item.publicReason(), 14, Design.INK));
    card.addView(Ui.text(activity, "入箱时间：" + SubmissionScreen.date(item.deliveredAt()), 12, Design.MUTED));
    card.addView(Ui.text(activity, "申诉截止：" + SubmissionScreen.date(item.appealDeadline()), 12, Design.MUTED));
    card.addView(Ui.text(activity, "reporter".equals(item.side()) ? "你是举报人" : "你是被举报人", 12, Design.MUTED));
    Ui.link(card, "查看处理动作", () -> showActions(item));
    if (!item.appealStatus().isBlank()) {
      card.addView(Ui.text(activity, "你的申诉：" + appealStatusLabel(item.appealStatus())
          + (item.appealOutcome().isBlank() ? "" : " · " + outcomeLabel(item.appealOutcome())),
          13, Design.MUTED));
    } else if (item.appealDeadline() > System.currentTimeMillis()) {
      Ui.link(card, "提交申诉", () -> appealDialog(item));
    } else {
      card.addView(Ui.text(activity, "申诉期限已过。", 12, Design.MUTED));
    }
  }

  private void showActions(ModerationDecision item) {
    AtomicReference<List<ReportAction>> result = new AtomicReference<>();
    activity.read("decision-actions:" + item.decisionId(), null, null,
        () -> result.set(activity.repo.decisionActions(item.decisionId())), () -> {
          StringBuilder text = new StringBuilder();
          if (result.get().isEmpty()) text.append("尚未记录实际处理动作。");
          for (ReportAction action : result.get()) {
            if (text.length() > 0) text.append((char) 10);
            text.append(action.actionType()).append(" · ").append(action.targetType())
                .append(" · ").append(SubmissionScreen.date(action.createdAt()));
            if (!action.note().isBlank()) text.append((char) 10).append(action.note());
          }
          new AlertDialog.Builder(activity).setTitle("处理动作").setMessage(text)
              .setPositiveButton("关闭", null).show();
        }, () -> {});
  }

  private void appealDialog(ModerationDecision item) {
    EditText input = new EditText(activity);
    input.setHint("申诉理由，最多2000字");
    input.setSingleLine(false);
    input.setMinLines(3);
    new AlertDialog.Builder(activity).setTitle("提交申诉").setView(input)
        .setNegativeButton("取消", null)
        .setPositiveButton("提交", (dialog, which) -> {
          String content = input.getText().toString().strip();
          if (content.isEmpty()) {
            activity.message("请填写申诉内容");
            return;
          }
          activity.request(null, null,
              () -> activity.repo.submitAppeal(item.decisionId(), content, UUID.randomUUID().toString()),
              () -> {
                activity.message("申诉已提交");
                render();
              });
        }).show();
  }

  private static String appealStatusLabel(String status) {
    return switch (status) {
      case "under_review" -> "复核中";
      case "resolved" -> "已复核";
      default -> "已提交";
    };
  }

  private static String typeLabel(String type) {
    return switch (type) {
      case "post" -> "食记";
      case "comment" -> "评论";
      case "message" -> "私信";
      default -> type;
    };
  }

  private static String statusLabel(String status) {
    return switch (status) {
      case "completed" -> "已完成";
      case "legacy" -> "历史记录";
      default -> "待处理";
    };
  }

  private static String outcomeLabel(String outcome) {
    return "upheld".equals(outcome) ? "成立" : "不成立";
  }
}
