package cn.edu.bjtu.foodtime.presentation;

import android.content.Context;
import android.text.InputFilter;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import cn.edu.bjtu.foodtime.domain.model.CatalogSearch;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** Reusable name filter for the current catalog, with route-scoped draft restoration. */
final class CatalogList {
  @FunctionalInterface
  interface GroupRenderer<T> {
    void render(LinearLayout parent,List<T> items);
  }

  static <T> void show(MainActivity a, LinearLayout page, String label, String placeholder,
      String unit, List<T> items, Function<T, String> name, BiConsumer<LinearLayout, T> render) {
    showInternal(a,page,label,placeholder,unit,items,name,false,
        (list,filtered) -> filtered.forEach(item -> render.accept(list,item)));
  }

  static <T> void showGallery(MainActivity a,LinearLayout page,String placeholder,String unit,
      List<T> items,Function<T,String> name,GroupRenderer<T> render) {
    showInternal(a,page,"",placeholder,unit,items,name,true,render);
  }

  private static <T> void showInternal(MainActivity a,LinearLayout page,String label,
      String placeholder,String unit,List<T> items,Function<T,String> name,boolean gallery,
      GroupRenderer<T> render) {
    TextView caption=null;
    if(!label.isBlank()) {
      caption=Ui.text(a,label,14,Design.INK);
      page.addView(caption);
      Ui.gap(page,8);
    }
    LinearLayout row = Ui.row(a);
    EditText search = new EditText(a);
    search.setId(View.generateViewId());
    if(caption!=null) caption.setLabelFor(search.getId());
    search.setContentDescription(label.isBlank()?"查找"+unit:label);
    search.setHint(placeholder);
    search.setTextSize(16);
    search.setTypeface(Design.body());
    search.setSingleLine(true);
    search.setFilters(new InputFilter[] {new InputFilter.LengthFilter(100)});
    search.setPadding(Ui.dp(a, gallery?18:12), 0, Ui.dp(a, gallery?18:12), 0);
    search.setBackground(Ui.shape(a, Design.PAPER, gallery?Design.BORDER:Design.INK, gallery?24:0));
    search.setTextColor(Design.INK);
    search.setHintTextColor(Design.MUTED);
    search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
    String key = a.draftKey("search");
    search.setText(a.drafts.getString(key, ""));
    row.addView(search, new LinearLayout.LayoutParams(0, Ui.dp(a, gallery?52:48), 1));
    TextView clear = Ui.button(a, gallery?"清空":"清除", false, () -> {
      search.setText("");
      search.requestFocus();
    });
    if(gallery) clear.setBackground(Ui.ripple(a,Design.ALT,0,24));
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
      if(gallery) clear.setVisibility(query.isEmpty()?View.GONE:View.VISIBLE);
      list.removeAllViews();
      List<T> filtered=new ArrayList<>();
      for(T item:items) if(CatalogSearch.matches(name.apply(item),query)) filtered.add(item);
      int found=filtered.size();
      count.setText(gallery
          ? (query.isBlank()?"慢慢逛，共 "+items.size()+" "+unit:"找到 "+found+" "+unit)
          : (query.isBlank()?"共 "+items.size()+" "+unit:"找到 "+found+" / "+items.size()+" "+unit));
      if(found==0) Ui.empty(list,"没有找到","");
      else render.render(list,filtered);
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
