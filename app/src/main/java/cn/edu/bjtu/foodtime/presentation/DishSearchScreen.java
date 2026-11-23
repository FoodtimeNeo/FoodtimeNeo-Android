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
  private final LinearLayout page, results, hallList, discovery, resultArea;
  private final EditText input, minPrice, maxPrice;
  private final Spinner rating, sort;
  private final TextView search, error, status, hallStatus, filterApply, filterReset;
  private final NumberedPagination pagination;
  private boolean loading, editingHistory, discoveryLoaded;
  private LinearLayout history;
  private String owner;
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
    owner=activity.repo.currentUser().id();

    LinearLayout searchBar=Ui.row(activity);searchBar.setPadding(Ui.dp(activity,10),Ui.dp(activity,4),Ui.dp(activity,4),Ui.dp(activity,4));searchBar.setBackground(Ui.shape(activity,Design.PAPER,Design.RED,28));
    searchBar.setGravity(android.view.Gravity.CENTER_VERTICAL);
    input=new EditText(activity);input.setSingleLine(true);input.setTextSize(16);input.setTextColor(Design.INK);input.setHintTextColor(Design.MUTED);input.setBackgroundColor(android.graphics.Color.TRANSPARENT);input.setTypeface(Design.body());input.setContentDescription("搜索菜品");
    input.setPadding(Ui.dp(activity,8),Ui.dp(activity,8),Ui.dp(activity,8),Ui.dp(activity,8));
    input.setGravity(android.view.Gravity.CENTER_VERTICAL);input.setIncludeFontPadding(true);input.setMinHeight(Ui.dp(activity,48));
    searchBar.addView(input,new LinearLayout.LayoutParams(0,-2,1));
    input.setTag("dish-search-keyword");
    input.setHint("牛肉面");
    input.setFilters(new InputFilter[] { new InputFilter.LengthFilter(50) });
    input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
    input.setText(activity.drafts.getString(draftKey, ""));
    search = Ui.button(activity, "搜索", true, this::submit);
    search.setBackground(Ui.ripple(activity,Design.RED,0,24));searchBar.addView(search,new LinearLayout.LayoutParams(-2,-2));page.addView(searchBar);
    search.setTag("dish-search-submit");
    history=Ui.col(activity);history.setTag("dish-search-history");page.addView(history);paintHistory();
    input.setOnEditorActionListener((view, action, event) -> {
      if (action != EditorInfo.IME_ACTION_SEARCH) return false;
      if (search.isEnabled()) submit();
      return true;
    });
    Ui.gap(page, 12);

    discovery=Ui.col(activity);discovery.setTag("dish-search-discovery");page.addView(discovery);
    resultArea=Ui.col(activity);resultArea.setTag("dish-search-results");page.addView(resultArea);
    LinearLayout resultActions=Ui.row(activity);
    TextView backToSearch=Ui.button(activity,"搜索发现",false,this::showDiscovery);backToSearch.setTextSize(13);backToSearch.setBackground(Ui.ripple(activity,Design.PAPER,0,8));backToSearch.setTag("dish-search-discovery-link");resultActions.addView(backToSearch);
    resultArea.addView(resultActions);
    LinearLayout filterBox = Ui.col(activity);
    filterBox.setTag("dish-search-filters");filterBox.setVisibility(View.GONE);
    TextView filterToggle=Ui.button(activity,"筛选与排序 ▾",false,()->{});
    filterToggle.setTag("dish-search-filter-toggle");
    filterToggle.setOnClickListener(v->{boolean open=filterBox.getVisibility()!=View.VISIBLE;filterBox.setVisibility(open?View.VISIBLE:View.GONE);filterToggle.setText(open?"收起筛选 ▴":"筛选与排序 ▾");filterToggle.setContentDescription(open?"筛选与排序，已展开":"筛选与排序，已收起");});
    resultActions.addView(filterToggle,new LinearLayout.LayoutParams(0,-2,1));resultArea.addView(filterBox);
    minPrice = Ui.field(filterBox, "最低价", false, false);
    minPrice.setTag("dish-search-min-price");
    minPrice.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
    minPrice.setHint("不限");
    maxPrice = Ui.field(filterBox, "最高价", false, false);
    maxPrice.setTag("dish-search-max-price");
    maxPrice.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
    maxPrice.setHint("不限");
    rating = spinner(filterBox, "最低评分", RATING_LABELS);
    rating.setTag("dish-search-rating");
    sort = spinner(filterBox, "排序", SORT_LABELS);
    sort.setTag("dish-search-sort");
    filterBox.addView(Ui.text(activity, "食堂", 14, Design.MUTED));
    hallStatus = Ui.text(activity, "正在加载食堂…", 13, Design.MUTED);
    filterBox.addView(hallStatus);
    hallList = Ui.col(activity);
    filterBox.addView(hallList);
    LinearLayout actions = Ui.row(activity);
    filterApply=Ui.button(activity,"应用筛选",true,this::submit);filterApply.setTag("dish-search-apply");
    actions.addView(filterApply,
        new LinearLayout.LayoutParams(0, -2, 1));
    LinearLayout.LayoutParams resetLayout=new LinearLayout.LayoutParams(0,-2,1);resetLayout.leftMargin=Ui.dp(activity,8);
    filterReset=Ui.button(activity,"重置",false,this::reset);filterReset.setTag("dish-search-reset");
    actions.addView(filterReset,resetLayout);
    filterBox.addView(actions);

    Ui.gap(page, 12);
    error = Ui.text(activity, "", 14, Design.RED);
    Ui.observeError(error);
    resultArea.addView(error);
    status = Ui.text(activity, "", 14, Design.MUTED);
    status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    resultArea.addView(status);
    results = Ui.col(activity);
    resultArea.addView(results);
    pagination=new NumberedPagination(activity,number->load(submitted,filters,number));resultArea.addView(pagination);
    paint();
    activity.read(requestKey + ":halls", null, error,
        () -> activity.repo.refresh(new ContentRequest(ContentRequest.Query.HALLS, "")),
        this::paintHalls, () -> {});
    if(activity.drafts.getBoolean(draftKey+":searched",false)) {
      minPrice.setText(activity.drafts.getString(draftKey+":min",""));maxPrice.setText(activity.drafts.getString(draftKey+":max",""));
      selectedHalls.addAll(activity.drafts.getStringArrayList(draftKey+":halls")==null?List.of():activity.drafts.getStringArrayList(draftKey+":halls"));
      rating.setSelection(Math.max(0,Math.min(RATING_VALUES.length-1,activity.drafts.getInt(draftKey+":rating",0))));sort.setSelection(Math.max(0,Math.min(SORT_VALUES.length-1,activity.drafts.getInt(draftKey+":sort",0))));
      page.post(()->load(activity.drafts.getString(draftKey+":query",""),new DishSearchFilters(new ArrayList<>(selectedHalls),price(minPrice,"最低价格"),price(maxPrice,"最高价格"),RATING_VALUES[rating.getSelectedItemPosition()],SORT_VALUES[sort.getSelectedItemPosition()]),Math.max(1,activity.drafts.getInt(draftKey+":page",1))));
      discovery.setVisibility(View.GONE);history.setVisibility(View.GONE);
    } else showDiscovery();
  }

  private void showDiscovery(){
    if(loading)return;
    activity.drafts.putBoolean(draftKey+":searched",false);
    resultArea.setVisibility(View.GONE);discovery.setVisibility(View.VISIBLE);paintHistory();
    if(!discoveryLoaded){discoveryLoaded=true;new SearchDiscovery(activity,discovery);}
  }

  private void paintHistory(){
    history.removeAllViews();List<String> terms=SearchHistoryStore.read(activity,owner);history.setVisibility(terms.isEmpty()?View.GONE:View.VISIBLE);if(terms.isEmpty())return;
    Ui.gap(history,12);LinearLayout heading=Ui.row(activity);heading.addView(Ui.text(activity,"历史搜索",16,Design.INK),new LinearLayout.LayoutParams(0,-2,1));
    TextView edit=Ui.button(activity,editingHistory?"完成":"编辑",false,()->{editingHistory=!editingHistory;paintHistory();});edit.setTextSize(13);edit.setBackground(Ui.ripple(activity,Design.PAPER,0,12));heading.addView(edit);
    if(editingHistory){TextView clear=Ui.button(activity,"清空",false,()->Ui.confirm(activity,"清空搜索历史？","",()->{SearchHistoryStore.purge(activity,owner);editingHistory=false;paintHistory();}));clear.setTextSize(13);clear.setBackground(Ui.ripple(activity,Design.PAPER,0,12));heading.addView(clear);}history.addView(heading);
    int maxWidth=Ui.dp(activity,activity.getResources().getConfiguration().screenWidthDp-28),used=0;LinearLayout row=Ui.row(activity);history.addView(row);
    for(String term:terms){TextView chip=Ui.button(activity,term+(editingHistory?" ×":""),false,()->{if(editingHistory){SearchHistoryStore.remove(activity,owner,term);paintHistory();}else{input.setText(term);submit();}});chip.setTextSize(14);chip.setMaxWidth(Math.max(Ui.dp(activity,120),maxWidth));chip.setBackground(Ui.ripple(activity,Design.ALT,0,18));chip.setContentDescription(editingHistory?"删除搜索历史："+term:term);
      chip.measure(View.MeasureSpec.makeMeasureSpec(maxWidth,View.MeasureSpec.AT_MOST),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));int width=chip.getMeasuredWidth()+Ui.dp(activity,8);
      if(used>0&&used+width>maxWidth){Ui.gap(history,6);row=Ui.row(activity);history.addView(row);used=0;}
      LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.rightMargin=Ui.dp(activity,8);row.addView(chip,lp);used+=width;
    }
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
      hallStatus.setText("暂无食堂");
      return;
    }
    hallStatus.setText("");
    for (Hall hall : halls) {
      CheckBox box = new CheckBox(activity);
      box.setTag("dish-search-hall:"+hall.id());
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
    SearchHistoryStore.remember(activity,owner,query);paintHistory();
    load(query, next, 1);
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
    if(loading)return;
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
    activity.drafts.putBoolean(draftKey+":searched",true);
    discovery.setVisibility(View.GONE);history.setVisibility(View.GONE);resultArea.setVisibility(View.VISIBLE);
    loading=true;
    filterApply.setEnabled(false);filterReset.setEnabled(false);filterApply.setAlpha(.4f);filterReset.setAlpha(.4f);
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
          activity.drafts.putString(draftKey+":query",query);activity.drafts.putInt(draftKey+":page",data.page());
          activity.drafts.putString(draftKey+":min",minPrice.getText().toString());activity.drafts.putString(draftKey+":max",maxPrice.getText().toString());
          activity.drafts.putStringArrayList(draftKey+":halls",new ArrayList<>(selectedHalls));activity.drafts.putInt(draftKey+":rating",rating.getSelectedItemPosition());activity.drafts.putInt(draftKey+":sort",sort.getSelectedItemPosition());
          paint();
        },
        () -> {
          loading=false;
          filterApply.setEnabled(true);filterReset.setEnabled(true);filterApply.setAlpha(1f);filterReset.setAlpha(1f);
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
    status.setText(data == null ? "" : data.items().isEmpty()
        ? "没有匹配的菜品。" : "找到 " + data.total() + " 道菜 · 第 " + data.page() + " / "+totalPages()+" 页");
  }
  private int totalPages(){return data==null?1:(int)Math.min(Integer.MAX_VALUE,Math.max(1,(data.total()-1)/Math.max(1,data.size())+1));}
}
