package cn.edu.bjtu.foodtime.presentation;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.edu.bjtu.foodtime.domain.model.Models.ReportPage;
import cn.edu.bjtu.foodtime.domain.model.Models.ReportRow;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

final class ReportsScreen {
  static void show(MainActivity activity, LinearLayout page, MainActivity.Route route) {
    var actor = activity.repo.currentUser();
    if (actor == null || !"superadmin".equals(actor.role())) return;
    new ReportsScreen(activity, page);
  }

  private final MainActivity activity;
  private final LinearLayout content;
  private final TextView error, reload;
  private final String requestKey = "report-queue:" + UUID.randomUUID();

  private ReportsScreen(MainActivity activity, LinearLayout page) {
    this.activity = activity;
    activity.heading(page, "", "举报处理", "私信举报只包含当事人所选的消息。");
    error = Ui.text(activity, "", 14, Design.RED);
    Ui.observeError(error);
    page.addView(error);
    reload = Ui.button(activity, "刷新举报", false, () -> load(1));
    page.addView(reload);
    content = Ui.col(activity);
    page.addView(content);
    page.post(() -> {
      if (page.isAttachedToWindow()) load(1);
    });
  }

  private void load(int page) {
    content.setVisibility(View.INVISIBLE);
    AtomicReference<ReportPage> data = new AtomicReference<>();
    activity.read(requestKey, reload, error, () -> data.set(activity.repo.reports(page)), () -> {
      content.removeAllViews();
      content.setVisibility(View.VISIBLE);
      ReportPage result = data.get();
      if (result.items().isEmpty()) Ui.empty(content, "没有待处理举报", "");
      for (ReportRow row : result.items()) render(row, result);
      if (page > 1) Ui.action(content, "上一页", false, () -> load(page - 1));
      if (result.hasMore()) Ui.action(content, "下一页", false, () -> load(page + 1));
    }, () -> {});
  }

  private void render(ReportRow row, ReportPage result) {
    LinearLayout card = Ui.card(content);
    String type = switch (row.type()) {
      case "post" -> "食记";
      case "comment" -> "评论";
      default -> "私信";
    };
    card.addView(Ui.title(activity, type, 18));
    card.addView(Ui.text(activity, "举报原因：" + row.reason(), 14, Design.INK));
    card.addView(Ui.text(activity, row.excerpt(), 15, Design.INK));
    card.addView(Ui.text(activity, "被举报用户：" + row.targetUserId(), 13, Design.MUTED));
    if (!row.postId().isEmpty()) {
      Ui.action(card, "查看原帖", false, () -> activity.go("community-detail", row.postId()));
    }
    TextView review = Ui.button(activity, "标为已处理", true, () -> {});
    review.setOnClickListener(view -> activity.request(view, error, () -> activity.repo.reviewReport(row.id()), () -> {
      int nextPage = result.items().size() == 1 ? Math.max(1, result.page() - 1) : result.page();
      load(nextPage);
    }));
    card.addView(review);
  }
}
