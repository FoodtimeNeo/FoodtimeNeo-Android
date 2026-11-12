package cn.edu.bjtu.foodtime.presentation;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.edu.bjtu.foodtime.domain.model.Models.ModerationDecision;
import cn.edu.bjtu.foodtime.domain.model.Models.ModerationDecisionPage;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** Minimal disabled-account surface: own decisions, one appeal, logout. */
final class AppealOnlyScreen {
  private final MainActivity activity;
  private final LinearLayout page, rows;
  private final TextView status, error;
  private final String requestKey = "appeal-only:" + UUID.randomUUID();

  private AppealOnlyScreen(MainActivity activity, LinearLayout page) {
    this.activity = activity;
    this.page = page;
    rows = Ui.col(activity);
    status = Ui.text(activity, "正在加载…", 14, Design.MUTED);
    error = Ui.text(activity, "", 14, Design.RED);
    Ui.observeError(error);
  }

  static void show(MainActivity activity, LinearLayout page) {
    activity.heading(page, "受限", "处理决定与申诉", "仅可查看本人的决定并提交一次申诉");
    page.addView(Ui.text(activity,
        "会话在登录后45分钟到期；未提交的申诉内容会在重新登录后恢复。", 12, Design.MUTED));
    AppealOnlyScreen screen = new AppealOnlyScreen(activity, page);
    page.addView(screen.status);
    page.addView(screen.error);
    page.addView(screen.rows);
    TextView logout = Ui.button(activity, "退出登录", false, activity::logout);
    page.addView(logout);
    screen.load();
  }

  private void load() {
    rows.removeAllViews();
    status.setText("正在加载…");
    AtomicReference<ModerationDecisionPage> result = new AtomicReference<>();
    activity.read(requestKey, null, error,
        () -> result.set(activity.repo.myModerationDecisions(1)), () -> {
          ModerationDecisionPage data = result.get();
          status.setText(data.items().isEmpty() ? "当前没有可查看的处理决定。" : "");
          for (ModerationDecision item : data.items()) card(item);
        }, () -> status.setText(""));
  }

  private void card(ModerationDecision item) {
    LinearLayout card = Ui.card(rows);
    card.addView(Ui.title(activity, outcomeLabel(item.outcome()), 18));
    card.addView(Ui.text(activity, "公开理由：" + item.publicReason(), 14, Design.INK));
    card.addView(Ui.text(activity, "入箱时间：" + SubmissionScreen.date(item.deliveredAt()), 12, Design.MUTED));
    card.addView(Ui.text(activity, "申诉截止：" + SubmissionScreen.date(item.appealDeadline()), 12, Design.MUTED));
    if (!item.appealStatus().isBlank()) {
      card.addView(Ui.text(activity, "申诉状态：" + statusLabel(item.appealStatus())
          + (item.appealOutcome().isBlank() ? "" : " · " + outcomeLabel(item.appealOutcome())),
          13, Design.MUTED));
      return;
    }
    if (item.appealDeadline() <= System.currentTimeMillis()) {
      card.addView(Ui.text(activity, "申诉期限已过。", 13, Design.MUTED));
      return;
    }
    EditText input = Ui.field(card, "申诉内容（最多2000字）", false, true);
    String draftKey = "appeal:" + item.decisionId();
    var draftStore = activity.getSharedPreferences("appeal-drafts", MainActivity.MODE_PRIVATE);
    input.setText(draftStore.getString(draftKey, ""));
    input.addTextChangedListener(new TextWatcher() {
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
      public void onTextChanged(CharSequence s, int start, int before, int count) {}
      public void afterTextChanged(Editable s) {
        draftStore.edit().putString(draftKey, s.toString()).apply();
      }
    });
    EvidenceSlots.show(activity, card, "appeal-evidence:" + item.decisionId());
    TextView submit = Ui.button(activity, "提交申诉", true, () -> {});
    submit.setOnClickListener(view -> {
      String content = input.getText().toString().strip();
      if (content.isEmpty()) {
        error.setText("请填写申诉内容");
        return;
      }
      activity.request(submit, error,
          () -> {
            var evidence = EvidenceSlots.upload(activity, "appeal-evidence:" + item.decisionId());
            activity.repo.submitAppeal(item.decisionId(), content, UUID.randomUUID().toString(), evidence);
          },
          () -> {
            draftStore.edit().remove(draftKey).apply();
            activity.message("申诉已提交");
            load();
          });
    });
    card.addView(submit);
  }

  private static String outcomeLabel(String outcome) {
    return "upheld".equals(outcome) ? "处理成立" : "处理不成立";
  }

  private static String statusLabel(String status) {
    return switch (status) {
      case "under_review" -> "复核中";
      case "resolved" -> "已复核";
      default -> "已提交";
    };
  }
}
