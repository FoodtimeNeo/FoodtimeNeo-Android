package cn.edu.bjtu.foodtime.presentation;

import android.text.InputFilter;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.edu.bjtu.foodtime.domain.model.Models.DishSearchPage;
import cn.edu.bjtu.foodtime.domain.model.Models.DishSearchRow;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

final class DishSearchScreen {
  static void show(MainActivity activity, LinearLayout page, MainActivity.Route route) {
    new DishSearchScreen(activity, page);
  }

  private final MainActivity activity;
  private final LinearLayout page, results;
  private final EditText input;
  private final TextView search, error, status, previous, next;
  private final String requestKey = "dish-search:" + UUID.randomUUID();
  private final String draftKey;
  private String submitted = "";
  private DishSearchPage data;

  private DishSearchScreen(MainActivity activity, LinearLayout page) {
    this.activity = activity;
    this.page = page;
    draftKey = activity.draftKey("dishSearch");
    activity.heading(page, "", "跨食堂搜菜",
        "按菜名查找，看看在哪个食堂能吃到。");
    input = Ui.field(page, "菜名", false, false);
    input.setHint("例如：鸡腿饭");
    input.setFilters(new InputFilter[] { new InputFilter.LengthFilter(50) });
    input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
    input.setText(activity.drafts.getString(draftKey, ""));
    search = Ui.button(activity, "搜索", true, this::submit);
    page.addView(search);
    input.setOnEditorActionListener((view, action, event) -> {
      if (action != EditorInfo.IME_ACTION_SEARCH) return false;
      if (search.isEnabled()) submit();
      return true;
    });
    Ui.gap(page, 12);
    error = Ui.text(activity, "", 14, Design.RED);
    Ui.observeError(error);
    page.addView(error);
    status = Ui.text(activity, "", 14, Design.MUTED);
    status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    page.addView(status);
    results = Ui.col(activity);
    page.addView(results);
    LinearLayout controls = Ui.row(activity);
    previous = Ui.button(activity, "上一页", false, () -> load(submitted, data.page() - 1));
    next = Ui.button(activity, "下一页", false, () -> load(submitted, data.page() + 1));
    controls.addView(previous, new LinearLayout.LayoutParams(0, -2, 1));
    controls.addView(next, new LinearLayout.LayoutParams(0, -2, 1));
    page.addView(controls);
    paint();
    if (input.length() > 0) page.post(this::submit);
  }

  private void submit() {
    if (!page.isAttachedToWindow()) return;
    String query = input.getText().toString().strip();
    activity.drafts.putString(draftKey, query);
    if (query.isEmpty()) {
      submitted = "";
      data = null;
      error.setText("");
      paint();
      return;
    }
    if (!query.equals(submitted) || data == null) load(query, 1);
  }

  private void load(String query, int number) {
    input.setEnabled(false);
    search.setEnabled(false);
    previous.setEnabled(false);
    next.setEnabled(false);
    status.setText("正在查找菜品…");
    if (!query.equals(submitted)) {
      data = null;
      results.removeAllViews();
    }
    AtomicReference<DishSearchPage> response = new AtomicReference<>();
    activity.read(requestKey, null, error,
        () -> response.set(activity.repo.searchDishes(query, number)),
        () -> {
          submitted = query;
          data = response.get();
          paint();
        },
        () -> {
          input.setEnabled(true);
          search.setEnabled(true);
          updateStatus();
        });
  }

  private void paint() {
    results.removeAllViews();
    if (data != null) {
      for (DishSearchRow dish : data.items()) {
        appendRow(activity, results, dish, () -> activity.go("detail", dish.id()));
      }
    }
    updateStatus();
  }

  static void appendRow(MainActivity activity, LinearLayout results, DishSearchRow dish, Runnable action) {
    String price = dish.price() == null ? "价格待补充" : "¥" + dish.price().stripTrailingZeros().toPlainString();
    TextView row = Ui.button(activity, dish.name() + "  " + price + "\n"
        + dish.diningHallName() + " · " + dish.stallName(), false, action);
    results.addView(row, new LinearLayout.LayoutParams(-1, -2));
    Ui.gap(results, 8);
  }

  private void updateStatus() {
    previous.setVisibility(data != null && data.page() > 1 ? View.VISIBLE : View.GONE);
    next.setVisibility(data != null && data.hasMore() ? View.VISIBLE : View.GONE);
    previous.setEnabled(data != null && data.page() > 1);
    next.setEnabled(data != null && data.hasMore());
    status.setText(data == null ? "输入菜名开始搜索。" : data.items().isEmpty()
        ? "这页没有匹配的菜品。" : "找到 " + data.total() + " 道菜 · 第 " + data.page() + " 页");
  }
}
