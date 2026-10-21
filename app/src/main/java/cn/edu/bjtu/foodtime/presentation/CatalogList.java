package cn.edu.bjtu.foodtime.presentation;

import android.content.Context;
import android.text.InputFilter;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import cn.edu.bjtu.foodtime.domain.model.CatalogSearch;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** Reusable name filter for the current catalog, with route-scoped draft restoration. */
final class CatalogList {
  static <T> void show(MainActivity a, LinearLayout page, String label, String placeholder,
      String unit, List<T> items, Function<T, String> name, BiConsumer<LinearLayout, T> render) {
    TextView caption = Ui.text(a, label, 14, Design.INK);
    page.addView(caption);
    Ui.gap(page, 8);
    LinearLayout row = Ui.row(a);
    EditText search = new EditText(a);
    search.setId(View.generateViewId());
    caption.setLabelFor(search.getId());
    search.setContentDescription(label);
    search.setHint(placeholder);
    search.setTextSize(16);
    search.setTypeface(Design.body());
    search.setSingleLine(true);
    search.setFilters(new InputFilter[] {new InputFilter.LengthFilter(100)});
    search.setPadding(Ui.dp(a, 12), 0, Ui.dp(a, 12), 0);
    search.setBackground(Ui.shape(a, Design.PAPER, Design.INK, 0));
    search.setTextColor(Design.INK);
    search.setHintTextColor(Design.MUTED);
    search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
    String key = a.draftKey("search");
    search.setText(a.drafts.getString(key, ""));
    row.addView(search, new LinearLayout.LayoutParams(0, Ui.dp(a, 48), 1));
    TextView clear = Ui.button(a, "清除", false, () -> {
      search.setText("");
      search.requestFocus();
    });
    clear.setContentDescription("清除搜索关键词");
    LinearLayout.LayoutParams clearLayout = new LinearLayout.LayoutParams(-2, -2);
    clearLayout.leftMargin = Ui.dp(a, 8);
    row.addView(clear, clearLayout);
    page.addView(row);
    Ui.gap(page, 8);
    TextView count = Ui.text(a, "", 14, Design.MUTED);
    count.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    page.addView(count);
    Ui.gap(page, 16);
    LinearLayout list = Ui.col(a);
    page.addView(list);
    Runnable filter = () -> {
      String query = search.getText().toString();
      a.drafts.putString(key, query);
      clear.setEnabled(!query.isEmpty());
      clear.setAlpha(query.isEmpty() ? 0.5f : 1f);
      list.removeAllViews();
      int found = 0;
      for (T item : items) if (CatalogSearch.matches(name.apply(item), query)) {
        found++;
        render.accept(list, item);
      }
      count.setText(query.isBlank() ? "共 " + items.size() + " " + unit
          : "找到 " + found + " / " + items.size() + " " + unit);
      if (found == 0) Ui.empty(list, "没有匹配的结果", "换一个关键词，或清除搜索查看全部。");
    };
    Ui.observe(search, filter);
    search.setOnEditorActionListener((view, action, event) -> {
      if (action != EditorInfo.IME_ACTION_SEARCH) return false;
      ((InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE))
          .hideSoftInputFromWindow(search.getWindowToken(), 0);
      return true;
    });
    filter.run();
  }
}
