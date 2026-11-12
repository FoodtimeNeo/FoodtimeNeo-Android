package cn.edu.bjtu.foodtime.presentation;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.edu.bjtu.foodtime.domain.model.Models.ModerationDecision;
import cn.edu.bjtu.foodtime.domain.model.Models.ModerationDecisionPage;
import cn.edu.bjtu.foodtime.domain.model.Models.ReportAction;
import cn.edu.bjtu.foodtime.BuildConfig;
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
    Ui.link(card, "查看处理动作", () -> showActions(item));
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
    var actor = activity.repo.currentUser();
    if (actor == null) return;
    String actorId = actor.id();
    var draftStore = new AppealDraftStore(activity, BuildConfig.API_BASE_URL, actorId);
    String evidenceKey = draftStore.key("evidence:" + item.decisionId());
    input.setText(draftStore.read(item.decisionId()));
    input.addTextChangedListener(new TextWatcher() {
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
      public void onTextChanged(CharSequence s, int start, int before, int count) {}
      public void afterTextChanged(Editable s) {
        if (sameActor(actorId)) draftStore.save(item.decisionId(), s.toString());
      }
    });
    EvidenceSlots.show(activity, card, evidenceKey);
    TextView submit = Ui.button(activity, "提交申诉", true, () -> {});
    submit.setOnClickListener(view -> {
      String content = input.getText().toString().strip();
      if (content.isEmpty()) {
        error.setText("请填写申诉内容");
        return;
      }
      activity.request(submit, error,
          () -> {
            if (!sameActor(actorId)) throw new IllegalStateException("账号已变化，请重新打开申诉");
            var evidence = EvidenceSlots.upload(activity, evidenceKey);
            if (!sameActor(actorId)) throw new IllegalStateException("账号已变化，请重新打开申诉");
            activity.repo.submitAppeal(item.decisionId(), content, draftStore.requestId(item.decisionId()), evidence);
          },
          () -> {
            draftStore.clear(item.decisionId());
            if (!sameActor(actorId)) return;
            activity.message("申诉已提交");
            load();
          });
    });
    card.addView(submit);
  }

  private boolean sameActor(String expected) {
    var current = activity.repo.currentUser();
    return current != null && expected.equals(current.id());
  }

  private void showActions(ModerationDecision item) {
    AtomicReference<java.util.List<ReportAction>> result = new AtomicReference<>();
    activity.read("appeal-actions:" + item.decisionId(), null, error,
        () -> result.set(activity.repo.decisionActions(item.decisionId())), () -> {
          StringBuilder text = new StringBuilder();
          if (result.get().isEmpty()) text.append("尚未记录实际处理动作。");
          for (ReportAction action : result.get()) {
            if (text.length() > 0) text.append((char) 10);
            text.append(action.actionType()).append(" · ").append(action.targetType())
                .append(" · ").append(SubmissionScreen.date(action.createdAt()));
          }
          new android.app.AlertDialog.Builder(activity).setTitle("处理动作").setMessage(text)
              .setPositiveButton("关闭", null).show();
        }, () -> {});
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
