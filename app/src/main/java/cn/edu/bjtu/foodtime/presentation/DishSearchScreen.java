package cn.edu.bjtu.foodtime.presentation;

import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import cn.edu.bjtu.foodtime.domain.model.Models.DishSearchFilters;
import cn.edu.bjtu.foodtime.domain.model.Models.DishSearchPage;
import cn.edu.bjtu.foodtime.domain.model.Models.DishSearchRow;
import cn.edu.bjtu.foodtime.domain.model.Models.Hall;
import cn.edu.bjtu.foodtime.domain.repository.ContentRequest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

final class DishSearchScreen {
  private static final String[] RATING_LABELS={"不限评分","3.0 分","3.5 分","4.0 分","4.5 分"};
  private static final BigDecimal[] RATING_VALUES={null,new BigDecimal("3.0"),new BigDecimal("3.5"),
      new BigDecimal("4.0"),new BigDecimal("4.5")};
  private static final String[] SORT_LABELS={"综合","价格从低到高","价格从高到低","评分优先"};
  private static final String[] SORT_VALUES={"relevance","price_asc","price_desc","rating_desc"};

  static void show(MainActivity activity, LinearLayout page, MainActivity.Route route) {
    new DishSearchScreen(activity, page);
  }

  private final MainActivity activity;
  private final LinearLayout page, results, hallList;
  private final EditText input, minPrice, maxPrice;
  private final Spinner rating, sort;
  private final TextView search, error, status, hallStatus;
  private final NumberedPagination pagination;
  private boolean loading;
  private final String requestKey = "dish-search:" + UUID.randomUUID();
  private final String draftKey;
  private final List<String> selectedHalls = new ArrayList<>();
  private String submitted = "";
  private DishSearchFilters filters = DishSearchFilters.empty();
  private DishSearchPage data;

  private DishSearchScreen(MainActivity activity, LinearLayout page) {
    this.activity = activity;
    this.page = page;
    draftKey = activity.draftKey("dishSearch");
    activity.heading(page, "", "跨食堂搜菜",
        "按菜名、价格、食堂和评分筛选菜品。");
    input = Ui.field(page, "菜名", false, false);
    input.setHint("可留空浏览全部菜品");
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

    LinearLayout filterBox = Ui.col(activity);
    minPrice = Ui.field(filterBox, "最低价", false, false);
    minPrice.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
    minPrice.setHint("不限");
    maxPrice = Ui.field(filterBox, "最高价", false, false);
    maxPrice.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
    maxPrice.setHint("不限");
    rating = spinner(filterBox, "最低评分", RATING_LABELS);
    sort = spinner(filterBox, "排序", SORT_LABELS);
    filterBox.addView(Ui.text(activity, "食堂（可多选）", 14, Design.MUTED));
    hallStatus = Ui.text(activity, "正在加载食堂…", 13, Design.MUTED);
    filterBox.addView(hallStatus);
    hallList = Ui.col(activity);
    LinearLayout actions = Ui.row(activity);
    actions.addView(Ui.button(activity, "应用筛选", true, this::submit),
        new LinearLayout.LayoutParams(0, -2, 1));
    actions.addView(Ui.button(activity, "重置", false, this::reset),
        new LinearLayout.LayoutParams(0, -2, 1));

    Ui.gap(page, 12);
    error = Ui.text(activity, "", 14, Design.RED);
    Ui.observeError(error);
    page.addView(error);
    status = Ui.text(activity, "", 14, Design.MUTED);
    status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    page.addView(status);
    results = Ui.col(activity);
    page.addView(results);
    pagination=new NumberedPagination(activity,number->load(submitted,filters,number));page.addView(pagination);
    paint();
    activity.read(requestKey + ":halls", null, error,
        () -> activity.repo.refresh(new ContentRequest(ContentRequest.Query.HALLS, "")),
        this::paintHalls, () -> {});
    page.post(this::submit);
  }

  private Spinner spinner(LinearLayout parent, String label, String[] values) {
    parent.addView(Ui.text(activity, label, 14, Design.MUTED));
    Spinner spinner = new Spinner(activity);
    spinner.setAdapter(new ArrayAdapter<>(activity,
        android.R.layout.simple_spinner_dropdown_item, List.of(values)));
    parent.addView(spinner);
    return spinner;
  }

  private void paintHalls() {
    hallList.removeAllViews();
    List<Hall> halls = activity.repo.halls();
    if (halls.isEmpty()) {
      hallStatus.setText("暂无可选食堂；仍可按菜名和价格搜索。");
      return;
    }
    hallStatus.setText("");
    for (Hall hall : halls) {
      CheckBox box = new CheckBox(activity);
      box.setText(hall.name());
      box.setChecked(selectedHalls.contains(hall.id()));
      box.setOnCheckedChangeListener((button, checked) -> {
        if (checked) {
          if (!selectedHalls.contains(hall.id())) selectedHalls.add(hall.id());
        } else selectedHalls.remove(hall.id());
      });
      hallList.addView(box);
    }
  }

  private void submit() {
    if (!page.isAttachedToWindow()) return;
    String query = input.getText().toString().strip();
    BigDecimal min = price(minPrice, "最低价格");
    if (min == null && minPrice.getText().toString().strip().length() > 0) return;
    BigDecimal max = price(maxPrice, "最高价格");
    if (max == null && maxPrice.getText().toString().strip().length() > 0) return;
    if (min != null && max != null && min.compareTo(max) > 0) {
      error.setText("最低价格不能高于最高价格");
      return;
    }
    DishSearchFilters next = new DishSearchFilters(new ArrayList<>(selectedHalls), min, max,
        RATING_VALUES[rating.getSelectedItemPosition()], SORT_VALUES[sort.getSelectedItemPosition()]);
    activity.drafts.putString(draftKey, query);
    if (!query.equals(submitted) || !next.equals(filters) || data == null) {
      load(query, next, 1);
    }
  }

  private BigDecimal price(EditText field, String label) {
    String value = field.getText().toString().strip();
    if (value.isEmpty()) return null;
    try {
      BigDecimal price = new BigDecimal(value);
      if (price.signum() < 0) {
        error.setText(label + "不能为负数");
        return null;
      }
      return price;
    } catch (NumberFormatException failure) {
      error.setText(label + "格式不正确");
      return null;
    }
  }

  private void reset() {
    input.setText("");
    minPrice.setText("");
    maxPrice.setText("");
    rating.setSelection(0);
    sort.setSelection(0);
    selectedHalls.clear();
    paintHalls();
    submitted = "";
    filters = DishSearchFilters.empty();
    data = null;
    error.setText("");
    paint();
    submit();
  }

  private void load(String query, DishSearchFilters activeFilters, int number) {
    if (number < 1 || loading) return;
    loading=true;
    input.setEnabled(false);
    search.setEnabled(false);
    pagination.state(data==null?1:data.page(),totalPages(),false);
    status.setText("正在查找菜品…");
    boolean changed = !query.equals(submitted) || !activeFilters.equals(filters) || data == null;
    submitted = query;
    filters = activeFilters;
    if (changed) {
      data = null;
      results.removeAllViews();
    }
    AtomicReference<DishSearchPage> response = new AtomicReference<>();
    String key = requestKey + ":" + query + ":" + activeFilters + ":" + number;
    activity.read(key, null, error,
        () -> response.set(activity.repo.searchDishes(query, number, activeFilters)),
        () -> {
          submitted = query;
          filters = activeFilters;
          data = response.get();
          paint();
        },
        () -> {
          loading=false;
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
    String price = dish.price() == null ? "价格待补充"
        : "¥" + dish.price().stripTrailingZeros().toPlainString();
    String rating = dish.averageScore() == null ? "暂无评分"
        : dish.averageScore().stripTrailingZeros().toPlainString() + "分 · " + dish.reviewCount() + "条";
    TextView row = Ui.button(activity, dish.name() + "  " + price + "\n"
        + dish.diningHallName() + " · " + dish.stallName() + " · " + rating, false, action);
    results.addView(row, new LinearLayout.LayoutParams(-1, -2));
    Ui.gap(results, 8);
  }

  private void updateStatus() {
    pagination.state(data==null?1:data.page(),totalPages(),!loading&&data!=null);
    status.setText(data == null ? "可留空关键词浏览全部菜品。" : data.items().isEmpty()
        ? "没有匹配的菜品。" : "找到 " + data.total() + " 道菜 · 第 " + data.page() + " / "+totalPages()+" 页");
  }
  private int totalPages(){return data==null?1:(int)Math.min(Integer.MAX_VALUE,Math.max(1,(data.total()-1)/Math.max(1,data.size())+1));}
}
