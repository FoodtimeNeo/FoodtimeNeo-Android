package cn.edu.bjtu.foodtime.presentation;

import android.content.Context;
import android.text.InputFilter;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import cn.edu.bjtu.foodtime.domain.model.CatalogSearch;
import cn.edu.bjtu.foodtime.domain.model.Models.Dish;
import cn.edu.bjtu.foodtime.domain.model.Models.Hall;
import cn.edu.bjtu.foodtime.domain.model.Models.Stall;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** Inline optional association for the community composer. */
final class DishPickerScreen {
  static void show(MainActivity activity, LinearLayout form) {
    new DishPickerScreen(activity, form).render();
  }

  private enum Level {
    HALL("食堂"), STALL("档口"), DISH("菜品");
    final String label;
    Level(String label) { this.label = label; }
  }

  private final MainActivity activity;
  private final LinearLayout panel;
  private final String draft;
  private final String requestKey = "dish-picker:" + UUID.randomUUID();
  private Level open;
  private int request;

  private DishPickerScreen(MainActivity activity, LinearLayout form) {
    this.activity = activity;
    draft = activity.draftKey("");
    panel = Ui.col(activity);
    form.addView(panel);
  }

  private void render() {
    panel.removeAllViews();
    panel.addView(Ui.text(activity, "关联菜品（可选）", 13, Design.MUTED));
    row(Level.HALL, value("HallName"), true);
    row(Level.STALL, value("StallName"), !value("HallId").isEmpty());
    row(Level.DISH, value("Name"), !value("StallId").isEmpty());
    if (!value("Id").isEmpty()) {
      panel.addView(Ui.button(activity, "移除关联", false, () -> {
        store("Id", "");
        store("Name", "");
        close();
      }));
    }
  }

  private void row(Level level, String name, boolean enabled) {
    LinearLayout row = Ui.row(activity);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setMinimumHeight(Ui.dp(activity, 42));
    row.setPadding(Ui.dp(activity, 4), 0, Ui.dp(activity, 4), 0);
    row.setBackground(Ui.ripple(activity, Design.PAPER, 0, 8));
    TextView label = Ui.text(activity, level.label, 14, Design.INK);
    row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
    TextView current = Ui.text(activity, name.isEmpty() ? "请选择" : name, 14, Design.MUTED);
    current.setSingleLine();
    current.setEllipsize(TextUtils.TruncateAt.END);
    row.addView(current, new LinearLayout.LayoutParams(0, -2, 2));
    current.setGravity(Gravity.END);
    TextView arrow = Ui.text(activity, open == level ? "⌃" : "⌄", 16, Design.MUTED);
    arrow.setGravity(Gravity.CENTER);
    row.addView(arrow, new LinearLayout.LayoutParams(Ui.dp(activity, 24), -2));
    row.setContentDescription(level.label + "，" + current.getText() + (open == level ? "，收起" : "，展开"));
    row.setOnClickListener(view -> {
      hideKeyboard();
      open = open == level ? null : level;
      request++;
      render();
    });
    row.setEnabled(enabled);
    row.setFocusable(enabled);
    row.setAlpha(enabled ? 1f : .5f);
    panel.addView(row, new LinearLayout.LayoutParams(-1, -2));
    if (open != level) return;
    switch (level) {
      case HALL -> load(level, activity.repo::loadHalls, Hall::name, this::chooseHall);
      case STALL -> {
        String hallId = value("HallId");
        load(level, () -> activity.repo.loadStalls(hallId), Stall::name, this::chooseStall);
      }
      case DISH -> {
        String stallId = value("StallId");
        load(level, () -> activity.repo.loadDishes(stallId), Dish::name, this::chooseDish);
      }
    }
  }

  private <T> void load(Level level, Supplier<List<T>> query, Function<T, String> name, Consumer<T> select) {
    int ticket = ++request;
    LinearLayout content = Ui.col(activity);
    panel.addView(content);
    TextView status = Ui.text(activity, "正在加载" + level.label + "…", 13, Design.MUTED);
    TextView error = Ui.text(activity, "", 13, Design.RED);
    TextView retry = Ui.button(activity, "重试", false, this::render);
    retry.setVisibility(View.GONE);
    Ui.observeError(error);
    content.addView(status);
    content.addView(error);
    content.addView(retry);
    AtomicReference<List<T>> result = new AtomicReference<>();
    activity.read(requestKey + ":" + ticket, null, error, () -> result.set(query.get()), () -> {
      if (ticket != request || !content.isAttachedToWindow()) return;
      content.removeAllViews();
      options(content, level, result.get(), name, select);
    }, () -> {
      if (ticket == request && result.get() == null) {
        status.setVisibility(View.GONE);
        retry.setVisibility(View.VISIBLE);
      }
    });
  }

  private <T> void options(LinearLayout content, Level level, List<T> items, Function<T, String> name,
      Consumer<T> select) {
    EditText search = new EditText(activity);
    search.setSingleLine();
    search.setTextSize(16);
    search.setHint("筛选" + level.label + "，留空显示全部");
    search.setTextColor(Design.INK);
    search.setHintTextColor(Design.MUTED);
    search.setFilters(new InputFilter[] {new InputFilter.LengthFilter(50)});
    content.addView(search, new LinearLayout.LayoutParams(-1, Ui.dp(activity, 42)));
    ScrollView list = new ScrollView(activity) {
      @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
          getParent().requestDisallowInterceptTouchEvent(true);
        }
        return super.dispatchTouchEvent(event);
      }
    };
    LinearLayout rows = Ui.col(activity);
    list.addView(rows);
    content.addView(list, new LinearLayout.LayoutParams(-1, 0));
    TextView empty = Ui.text(activity, "没有可选的" + level.label, 13, Design.MUTED);
    content.addView(empty);
    Runnable filter = () -> {
      rows.removeAllViews();
      String keyword = search.getText().toString();
      for (T item : items) {
        String label = name.apply(item);
        if (!CatalogSearch.matches(label, keyword)) continue;
        TextView option = Ui.text(activity, label, 14, Design.INK);
        option.setGravity(Gravity.CENTER_VERTICAL);
        option.setPadding(Ui.dp(activity, 8), 0, Ui.dp(activity, 8), 0);
        option.setSingleLine();
        option.setEllipsize(TextUtils.TruncateAt.END);
        option.setBackground(Ui.ripple(activity, Design.ALT, 0, 6));
        option.setOnClickListener(view -> select.accept(item));
        option.setFocusable(true);
        rows.addView(option, new LinearLayout.LayoutParams(-1, Ui.dp(activity, 40)));
      }
      int count = rows.getChildCount();
      empty.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
      var size = list.getLayoutParams();
      size.height = Ui.dp(activity, Math.min(136, count * 40));
      list.setLayoutParams(size);
      list.scrollTo(0, 0);
    };
    Ui.observe(search, filter);
    filter.run();
  }

  private void chooseHall(Hall hall) {
    if (!hall.id().equals(value("HallId"))) {
      store("HallId", hall.id());
      store("HallName", hall.name());
      store("StallId", "");
      store("StallName", "");
      store("Id", "");
      store("Name", "");
    }
    close();
  }

  private void chooseStall(Stall stall) {
    if (!stall.id().equals(value("StallId"))) {
      store("StallId", stall.id());
      store("StallName", stall.name());
      store("Id", "");
      store("Name", "");
    }
    close();
  }

  private void chooseDish(Dish dish) {
    store("Id", dish.id());
    store("Name", dish.name());
    close();
  }

  private void close() {
    hideKeyboard();
    open = null;
    request++;
    render();
  }

  private String value(String key) {
    return activity.drafts.getString(draft + "linkedDish" + key, "");
  }

  private void store(String key, String value) {
    activity.drafts.putString(draft + "linkedDish" + key, value);
    if ("Id".equals(key) && !value.isBlank()) CommunityScreen.persistDraft(activity);
  }

  private void hideKeyboard() {
    ((InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE))
        .hideSoftInputFromWindow(panel.getWindowToken(), 0);
  }
}
