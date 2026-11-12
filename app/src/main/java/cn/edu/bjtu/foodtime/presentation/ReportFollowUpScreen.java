package cn.edu.bjtu.foodtime.presentation;

import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.edu.bjtu.foodtime.domain.model.Models.ReportFollowUp;
import cn.edu.bjtu.foodtime.domain.model.Models.ReportFollowUpPage;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** Reporter follow-up thread with staff public replies. */
final class ReportFollowUpScreen {
  private final MainActivity activity;
  private final LinearLayout page, rows;
  private final TextView status, error;
  private final EditText input;
  private final String reportId, requestKey;
  private String requestId = UUID.randomUUID().toString();

  private ReportFollowUpScreen(MainActivity activity, LinearLayout page, String reportId) {
    this.activity = activity;
    this.page = page;
    this.reportId = reportId;
    requestKey = "report-followups:" + reportId + ":" + UUID.randomUUID();
    rows = Ui.col(activity);
    status = Ui.text(activity, "正在加载…", 14, Design.MUTED);
    error = Ui.text(activity, "", 14, Design.RED);
    Ui.observeError(error);
    input = Ui.field(page, "追加追问（500字以内）", false, true);
    page.addView(status);
    page.addView(error);
    page.addView(rows);
    Ui.gap(page, 12);
    page.addView(input);
    TextView submit = Ui.button(activity, "提交追问", true, this::submit);
    page.addView(submit);
    Ui.gap(page, 8);
    page.addView(Ui.text(activity, "每个账号每天最多5次追问。", 12, Design.MUTED));
    load(1);
  }

  static void show(MainActivity activity, LinearLayout page, MainActivity.Route route) {
    activity.heading(page, "04.3", "追问记录", "与管理员沟通补充材料");
    new ReportFollowUpScreen(activity, page, route.id());
  }

  private void load(int pageNumber) {
    rows.removeAllViews();
    status.setText("正在加载…");
    AtomicReference<ReportFollowUpPage> result = new AtomicReference<>();
    activity.read(requestKey + ":" + pageNumber, null, error,
        () -> result.set(activity.repo.reportFollowUps(reportId, pageNumber)), () -> {
          ReportFollowUpPage data = result.get();
          status.setText(data.items().isEmpty() ? "还没有追问记录。" : "");
          for (ReportFollowUp item : data.items()) card(item);
          if (data.hasMore()) Ui.action(rows, "加载更多", false, () -> load(pageNumber + 1));
        }, () -> status.setText(""));
  }

  private void card(ReportFollowUp item) {
    LinearLayout card = Ui.card(rows);
    card.addView(Ui.text(activity, "staff".equals(item.authorRole()) ? "管理员回复" : "我", 13,
        "staff".equals(item.authorRole()) ? Design.RED : Design.MUTED));
    card.addView(Ui.text(activity, item.content(), 15, Design.INK));
    card.addView(Ui.text(activity, SubmissionScreen.date(item.createdAt()), 11, Design.MUTED));
  }

  private void submit() {
    String content = input.getText().toString().strip();
    if (content.isEmpty()) {
      error.setText("请填写追问内容");
      return;
    }
    activity.request(null, error,
        () -> activity.repo.addReportFollowUp(reportId, content, requestId), () -> {
          input.setText("");
          requestId = UUID.randomUUID().toString();
          activity.message("追问已提交");
          load(1);
        });
  }
}
