package cn.edu.bjtu.foodtime.presentation;

import android.app.AlertDialog;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.edu.bjtu.foodtime.domain.model.Models.HistoryItem;
import cn.edu.bjtu.foodtime.domain.model.Models.HistoryPage;
import cn.edu.bjtu.foodtime.domain.model.Models.HistorySettings;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** Server-backed browsing history, split into dishes and posts. */
final class HistoryScreen {
  private final MainActivity activity;
  private final LinearLayout page, body;
  private final String requestKey = "history:" + UUID.randomUUID();
  private HistorySettings settings;
  private List<HistoryItem> items = List.of();
  private long total;
  private String kind = "dish";
  private boolean loading;

  private HistoryScreen(MainActivity activity, LinearLayout page) {
    this.activity = activity;
    this.page = page;
    body = Ui.col(activity);
    page.addView(body);
  }

  static void show(MainActivity activity, LinearLayout page, MainActivity.Route route) {
    activity.heading(page, "01.6", "浏览历史", "帖子与菜品分别保留最近 50 条");
    HistoryScreen screen = new HistoryScreen(activity, page);
    screen.loadSettings();
  }

  private void loadSettings() {
    AtomicReference<HistorySettings> result = new AtomicReference<>();
    activity.read(requestKey + ":settings", null, null,
        () -> result.set(activity.repo.refreshHistorySettings()), () -> {
          settings = result.get();
          render();
          load(1);
        }, this::render);
  }

  private void load(int pageNumber) {
    loading = true;
    render();
    AtomicReference<HistoryPage> result = new AtomicReference<>();
    activity.read(requestKey + ":" + kind + ":" + pageNumber, null, null,
        () -> result.set(activity.repo.history(kind, pageNumber)), () -> {
          HistoryPage data = result.get();
          items = data.items();
          total = data.total();
          loading = false;
          render();
        }, () -> {
          loading = false;
          render();
        });
  }

  private void render() {
    body.removeAllViews();
    CheckBox toggle = new CheckBox(activity);
    toggle.setText("记录浏览历史");
    toggle.setTextColor(Design.INK);
    toggle.setChecked(settings == null || settings.enabled());
    toggle.setEnabled(settings != null);
    toggle.setOnCheckedChangeListener((button, checked) -> {
      if (settings == null || checked == settings.enabled()) return;
      HistorySettings current = settings;
      activity.request(null, null,
          () -> activity.repo.updateHistorySettings(checked, current.revision()),
          () -> {
            settings = null;
            loadSettings();
          });
    });
    body.addView(toggle);
    body.addView(Ui.text(activity, "关闭只停止新增，已有记录保留。", 12, Design.MUTED));

    LinearLayout tabs = Ui.row(activity);
    tabs.addView(tab("菜品", "dish"));
    tabs.addView(tab("帖子", "post"));
    TextView clear = Ui.button(activity, "清空本类", false, this::confirmClear);
    tabs.addView(clear, new LinearLayout.LayoutParams(0, -2, 1));
    body.addView(tabs);

    String statusText = loading ? "正在加载…" : "共 " + total + " 条";
    body.addView(Ui.text(activity, statusText, 13, Design.MUTED));
    LinearLayout rows = Ui.col(activity);
    body.addView(rows);
    if (!loading && items.isEmpty()) Ui.empty(rows, "还没有浏览记录", "");
    for (HistoryItem item : items) card(rows, item);
  }

  private TextView tab(String label, String value) {
    TextView tab = Ui.button(activity, label, kind.equals(value), () -> {
      if (kind.equals(value)) return;
      kind = value;
      load(1);
    });
    return tab;
  }

  private void card(LinearLayout rows, HistoryItem item) {
    LinearLayout card = Ui.card(rows);
    card.setPadding(Ui.dp(activity, 14), Ui.dp(activity, 12), Ui.dp(activity, 14), Ui.dp(activity, 12));
    card.addView(Ui.title(activity, item.available() ? item.title() : "内容已失效", 17));
    if (item.available()) {
      card.addView(Ui.text(activity, item.subtitle(), 13, Design.MUTED));
      card.setOnClickListener(view -> activity.go(item.kind().equals("post") ? "community-detail" : "detail",
          item.targetId()));
    } else {
      card.addView(Ui.text(activity, "可删除这条记录", 13, Design.MUTED));
    }
    card.addView(Ui.text(activity, SubmissionScreen.date(item.visitedAt()), 12, Design.MUTED));
    Ui.link(card, "删除这条记录", () -> confirmDelete(item));
  }

  private void confirmDelete(HistoryItem item) {
    new AlertDialog.Builder(activity).setTitle("删除浏览记录？")
        .setMessage("只删除这一条；删除前的旧任务不能补回。")
        .setNegativeButton("取消", null)
        .setPositiveButton("删除", (dialog, which) -> activity.request(null, null,
            () -> activity.repo.deleteHistory(item.kind(), item.targetId()), () -> {
              settings = null;
              loadSettings();
            })).show();
  }

  private void confirmClear() {
    new AlertDialog.Builder(activity).setTitle("清空本类浏览历史？")
        .setMessage(kind.equals("dish") ? "清空全部菜品浏览记录，帖子不受影响。" : "清空全部帖子浏览记录，菜品不受影响。")
        .setNegativeButton("取消", null)
        .setPositiveButton("清空", (dialog, which) -> activity.request(null, null,
            () -> activity.repo.clearHistory(kind), () -> {
              settings = null;
              loadSettings();
            })).show();
  }
}
