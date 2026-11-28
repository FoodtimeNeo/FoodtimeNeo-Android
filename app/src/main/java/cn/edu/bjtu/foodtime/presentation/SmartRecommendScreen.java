package cn.edu.bjtu.foodtime.presentation;

import android.graphics.Typeface;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import cn.edu.bjtu.foodtime.domain.model.DishTagModels;
import cn.edu.bjtu.foodtime.domain.model.DishTagModels.*;
import cn.edu.bjtu.foodtime.domain.model.Models.Hall;
import cn.edu.bjtu.foodtime.domain.model.PriceHistory;
import cn.edu.bjtu.foodtime.domain.model.SearchModels;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** High-freedom smart dish recommender: single-portion budget, 4-dimension tags, strict exclusions, relaxed fallback & spotlight roll. */
final class SmartRecommendScreen {
  static void show(MainActivity a, LinearLayout page, MainActivity.Route route) {
    new SmartRecommendScreen(a, page);
  }

  private final MainActivity a;
  private final LinearLayout page, hallChips, includeContainer, excludeChips, ratingChips, sortChips, resultContainer;
  private final EditText minInput, maxInput;
  private final CheckBox portionCheck;
  private final TextView errorView, summaryView, submitBtn, rollBtn;
  private final String key, requestKey = "smart-rec:" + UUID.randomUUID();

  private final List<Hall> availableHalls = new ArrayList<>();
  private final List<DishTag> catalog = new ArrayList<>(DishTagModels.DEFAULTS);
  private final List<String> selectedHalls = new ArrayList<>();
  private final List<String> includeTags = new ArrayList<>();
  private final List<String> excludeTags = new ArrayList<>();
  private final List<String> excludeDishIds = new ArrayList<>();

  private String minRating = "";
  private String sort = "match";
  private Double latitude, longitude;
  private long locatedAt, revision, seed;
  private boolean loading, locating;
  private SmartRecommendResult currentResult;

  private SmartRecommendScreen(MainActivity a, LinearLayout page) {
    this.a = a;
    this.page = page;
    this.key = "smart-rec::" + a.repo.currentUser().id();

    a.heading(page, "01.4", "今天吃什么 · 精准选餐", "自由组合单份预算、食堂、主食肉类做法与忌口，也可一键抽菜");

    LinearLayout topLinks = Ui.row(a);
    topLinks.setGravity(Gravity.END);
    TextView toSearch = Ui.text(a, "带着条件去搜菜 ›", 13, Design.RED);
    toSearch.setTypeface(Typeface.create(Design.body(), Typeface.BOLD));
    toSearch.setPadding(Ui.dp(a, 10), Ui.dp(a, 6), Ui.dp(a, 10), Ui.dp(a, 6));
    toSearch.setBackground(Ui.ripple(a, Design.RED_SOFT, 0, 14));
    toSearch.setClickable(true);
    toSearch.setFocusable(true);
    toSearch.setOnClickListener(v -> jumpToUnifiedSearch());
    topLinks.addView(toSearch);
    page.addView(topLinks, new LinearLayout.LayoutParams(-1, -2));
    Ui.gap(page, 10);

    LinearLayout studio = Ui.card(page);

    // 1. Single-portion budget
    studio.addView(sectionLabel("单份预算"));
    Ui.gap(studio, 6);
    LinearLayout budgetPresets = Ui.row(a);
    String[][] presets = {{"不限", ""}, {"¥10内", "10"}, {"¥15内", "15"}, {"¥20内", "20"}, {"¥25内", "25"}};
    minInput = new EditText(a);
    maxInput = new EditText(a);
    for (int i = 0; i < presets.length; i++) {
      String label = presets[i][0], val = presets[i][1];
      TextView chip = makeChip(label, false, false, () -> {
        minInput.setText("");
        maxInput.setText(val);
        saveDraft();
        updateSummary();
      });
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
      if (i > 0) lp.leftMargin = Ui.dp(a, 6);
      budgetPresets.addView(chip, lp);
    }
    HorizontalScrollView budgetScroll = new HorizontalScrollView(a);
    budgetScroll.setHorizontalScrollBarEnabled(false);
    budgetScroll.addView(budgetPresets);
    studio.addView(budgetScroll);
    Ui.gap(studio, 8);

    LinearLayout priceRow = Ui.row(a);
    priceRow.setGravity(Gravity.CENTER_VERTICAL);
    configureAmountInput(minInput, "最低 ¥");
    configureAmountInput(maxInput, "最高 ¥");
    priceRow.addView(minInput, new LinearLayout.LayoutParams(0, -2, 1));
    TextView dash = Ui.text(a, " — ", 14, Design.MUTED);
    priceRow.addView(dash);
    priceRow.addView(maxInput, new LinearLayout.LayoutParams(0, -2, 1));
    studio.addView(priceRow);
    Ui.gap(studio, 6);

    portionCheck = new CheckBox(a);
    portionCheck.setText("仅看按份或碗计价（排除称重、按个与未知价格）");
    portionCheck.setTextSize(13);
    portionCheck.setTextColor(Design.INK);
    portionCheck.setChecked(true);
    portionCheck.setOnCheckedChangeListener((v, checked) -> {
      saveDraft();
      updateSummary();
    });
    studio.addView(portionCheck);
    Ui.gap(studio, 14);

    // 2. Dining halls
    studio.addView(sectionLabel("食堂范围（不选为全部）"));
    Ui.gap(studio, 6);
    hallChips = Ui.col(a);
    studio.addView(hallChips);
    Ui.gap(studio, 14);

    // 3. 4-dimension includeTags
    studio.addView(sectionLabel("我想吃（同组任选，跨组同时满足）"));
    Ui.gap(studio, 6);
    includeContainer = Ui.col(a);
    studio.addView(includeContainer);
    Ui.gap(studio, 14);

    // 4. Exclude tags (忌口)
    studio.addView(sectionLabel("忌口 / 绝不想吃（近似放宽也绝不突破）"));
    Ui.gap(studio, 6);
    excludeChips = Ui.col(a);
    studio.addView(excludeChips);
    Ui.gap(studio, 14);

    // 5. Rating & Sort
    studio.addView(sectionLabel("最低口碑"));
    Ui.gap(studio, 6);
    ratingChips = Ui.col(a);
    studio.addView(ratingChips);
    Ui.gap(studio, 12);

    studio.addView(sectionLabel("排序偏好"));
    Ui.gap(studio, 6);
    sortChips = Ui.col(a);
    studio.addView(sortChips);
    Ui.gap(studio, 12);

    errorView = Ui.text(a, "", 13, Design.RED);
    Ui.observeError(errorView);
    studio.addView(errorView);

    // Action row
    LinearLayout actions = Ui.row(a);
    submitBtn = Ui.button(a, "精准推荐", true, () -> runRecommend(false));
    submitBtn.setTag("smart-rec-submit");
    rollBtn = Ui.button(a, "帮我抽一道", false, () -> runRecommend(true));
    rollBtn.setTag("smart-rec-roll");
    LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(0, Ui.dp(a, 46), 1);
    LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(0, Ui.dp(a, 46), 1);
    rLp.leftMargin = Ui.dp(a, 8);
    actions.addView(submitBtn, sLp);
    actions.addView(rollBtn, rLp);
    studio.addView(actions);
    Ui.gap(studio, 8);

    LinearLayout footerRow = Ui.row(a);
    footerRow.setGravity(Gravity.CENTER_VERTICAL);
    summaryView = Ui.text(a, "", 12, Design.MUTED);
    footerRow.addView(summaryView, new LinearLayout.LayoutParams(0, -2, 1));
    TextView resetBtn = Ui.text(a, "重置条件", 12, Design.RED);
    resetBtn.setPadding(Ui.dp(a, 8), Ui.dp(a, 6), Ui.dp(a, 4), Ui.dp(a, 6));
    resetBtn.setClickable(true);
    resetBtn.setFocusable(true);
    resetBtn.setOnClickListener(v -> resetAll());
    footerRow.addView(resetBtn);
    studio.addView(footerRow);

    resultContainer = Ui.col(a);
    resultContainer.setTag("smart-rec-results");
    page.addView(resultContainer);

    page.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
      @Override public void onViewAttachedToWindow(View v) {}
      @Override public void onViewDetachedFromWindow(View v) {
        revision++;
        a.searchLocation.clear();
      }
    });

    restoreDraft();
    paintAllControls();
    updateSummary();

    // Load halls + tag catalog asynchronously, and run initial recommendation
    availableHalls.addAll(a.repo.halls());
    paintHallChips();
    var hallRef = new AtomicReference<List<Hall>>();
    a.read(requestKey + ":halls", null, new TextView(a),
        () -> hallRef.set(a.repo.loadHalls()),
        () -> {
          if (hallRef.get() != null) {
            availableHalls.clear();
            availableHalls.addAll(hallRef.get());
            paintHallChips();
          }
        }, () -> {});

    var tagRef = new AtomicReference<List<DishTag>>();
    a.read(requestKey + ":tags", null, new TextView(a),
        () -> tagRef.set(a.repo.loadDishTags()),
        () -> {
          if (tagRef.get() != null && !tagRef.get().isEmpty()) {
            catalog.clear();
            catalog.addAll(tagRef.get());
            paintIncludeTags();
            paintExcludeTags();
            updateSummary();
          }
        }, () -> {});

    page.post(() -> runRecommend(false));
  }

  private void configureAmountInput(EditText input, String hint) {
    input.setSingleLine();
    input.setHint(hint);
    input.setTextSize(14);
    input.setTypeface(Design.body());
    input.setTextColor(Design.INK);
    input.setHintTextColor(Design.MUTED);
    input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
    input.setPadding(Ui.dp(a, 10), Ui.dp(a, 8), Ui.dp(a, 10), Ui.dp(a, 8));
    input.setBackground(Ui.shape(a, Design.ALT, Design.BORDER, 10));
    input.setMinHeight(Ui.dp(a, 42));
  }

  private TextView sectionLabel(String text) {
    TextView tv = Ui.text(a, text, 14, Design.INK);
    tv.setTypeface(Typeface.create(Design.body(), Typeface.BOLD));
    return tv;
  }

  private TextView makeChip(String text, boolean active, boolean excludeStyle, Runnable action) {
    int bg = active ? (excludeStyle ? Design.INK : Design.RED) : Design.ALT;
    int fg = active ? 0xffffffff : Design.INK;
    int stroke = active ? bg : Design.BORDER;
    TextView chip = Ui.text(a, text, 12, fg);
    chip.setTypeface(active ? Typeface.create(Design.body(), Typeface.BOLD) : Design.body());
    chip.setPadding(Ui.dp(a, 11), Ui.dp(a, 6), Ui.dp(a, 11), Ui.dp(a, 6));
    chip.setMinHeight(Ui.dp(a, 34));
    chip.setGravity(Gravity.CENTER);
    chip.setBackground(Ui.ripple(a, bg, stroke, 18));
    chip.setClickable(true);
    chip.setFocusable(true);
    chip.setOnClickListener(v -> action.run());
    return chip;
  }

  private void addWrappedChips(LinearLayout host, List<TextView> chips) {
    host.removeAllViews();
    if (chips.isEmpty()) return;
    int maxWidth = Math.max(220, a.getResources().getConfiguration().screenWidthDp - 56);
    float density = Math.max(1f, a.getResources().getDisplayMetrics().density);
    LinearLayout row = Ui.row(a);
    host.addView(row);
    int usedDp = 0;
    for (TextView chip : chips) {
      chip.measure(View.MeasureSpec.makeMeasureSpec(Ui.dp(a, maxWidth), View.MeasureSpec.AT_MOST), 0);
      int chipDp = Math.round(chip.getMeasuredWidth() / density) + 8;
      if (usedDp > 0 && usedDp + chipDp > maxWidth) {
        Ui.gap(host, 6);
        row = Ui.row(a);
        host.addView(row);
        usedDp = 0;
      }
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
      lp.rightMargin = Ui.dp(a, 6);
      row.addView(chip, lp);
      usedDp += chipDp;
    }
  }

  private void paintAllControls() {
    paintHallChips();
    paintIncludeTags();
    paintExcludeTags();
    paintRatingChips();
    paintSortChips();
  }

  private void paintHallChips() {
    List<TextView> chips = new ArrayList<>();
    for (Hall hall : availableHalls) {
      boolean active = selectedHalls.contains(hall.id());
      chips.add(makeChip(hall.name(), active, false, () -> {
        if (selectedHalls.contains(hall.id())) selectedHalls.remove(hall.id());
        else selectedHalls.add(hall.id());
        excludeDishIds.clear();
        saveDraft();
        paintHallChips();
        updateSummary();
      }));
    }
    addWrappedChips(hallChips, chips);
    if (chips.isEmpty()) {
      hallChips.addView(Ui.text(a, "正在加载食堂列表…", 12, Design.MUTED));
    }
  }

  private void paintIncludeTags() {
    includeContainer.removeAllViews();
    for (String dim : DishTagModels.DIMENSIONS) {
      List<DishTag> items = new ArrayList<>();
      for (DishTag tag : catalog) {
        if (dim.equals(tag.dimension())) items.add(tag);
      }
      if (items.isEmpty()) continue;
      TextView dimLabel = Ui.text(a, DishTagModels.dimensionLabel(dim), 12, Design.MUTED);
      includeContainer.addView(dimLabel);
      Ui.gap(includeContainer, 4);
      LinearLayout box = Ui.col(a);
      List<TextView> chips = new ArrayList<>();
      for (DishTag tag : items) {
        boolean active = includeTags.contains(tag.code());
        chips.add(makeChip(tag.label(), active, false, () -> {
          if (includeTags.contains(tag.code())) includeTags.remove(tag.code());
          else {
            includeTags.add(tag.code());
            excludeTags.remove(tag.code());
          }
          excludeDishIds.clear();
          saveDraft();
          paintIncludeTags();
          paintExcludeTags();
          updateSummary();
        }));
      }
      addWrappedChips(box, chips);
      includeContainer.addView(box);
      Ui.gap(includeContainer, 8);
    }
  }

  private void paintExcludeTags() {
    List<TextView> chips = new ArrayList<>();
    for (DishTag tag : catalog) {
      boolean active = excludeTags.contains(tag.code());
      chips.add(makeChip("✕ " + tag.label(), active, true, () -> {
        if (excludeTags.contains(tag.code())) excludeTags.remove(tag.code());
        else {
          excludeTags.add(tag.code());
          includeTags.remove(tag.code());
        }
        excludeDishIds.clear();
        saveDraft();
        paintIncludeTags();
        paintExcludeTags();
        updateSummary();
      }));
    }
    addWrappedChips(excludeChips, chips);
  }

  private void paintRatingChips() {
    String[][] opts = {{"不限", ""}, {"3.5★+", "3.5"}, {"4.0★+", "4.0"}, {"4.5★+", "4.5"}};
    List<TextView> chips = new ArrayList<>();
    for (String[] opt : opts) {
      chips.add(makeChip(opt[0], minRating.equals(opt[1]), false, () -> {
        minRating = opt[1];
        excludeDishIds.clear();
        saveDraft();
        paintRatingChips();
        updateSummary();
      }));
    }
    addWrappedChips(ratingChips, chips);
  }

  private void paintSortChips() {
    String[][] opts = {{"match", "综合推荐"}, {"price_asc", "价格最低"}, {"rating_desc", "评分最高"}, {"distance", locating ? "定位中…" : "距离最近"}};
    List<TextView> chips = new ArrayList<>();
    for (String[] opt : opts) {
      chips.add(makeChip(opt[1], sort.equals(opt[0]), false, () -> {
        if ("distance".equals(opt[0])) {
          locateForDistance();
        } else {
          sort = opt[0];
          saveDraft();
          paintSortChips();
          runRecommend(false);
        }
      }));
    }
    addWrappedChips(sortChips, chips);
  }

  private void locateForDistance() {
    if (locating || loading) return;
    locating = true;
    paintSortChips();
    errorView.setText("");
    boolean[] enabled = {false};
    a.read(requestKey + ":loc-cap", null, errorView,
        () -> enabled[0] = a.repo.searchDistanceAvailable(),
        () -> {
          if (!enabled[0]) {
            locating = false;
            paintSortChips();
            errorView.setText("食堂位置待补充，暂不能按距离推荐");
            return;
          }
          a.searchLocation.request(a, pos -> {
            locating = false;
            latitude = pos.getLatitude();
            longitude = pos.getLongitude();
            locatedAt = android.os.SystemClock.elapsedRealtime();
            sort = "distance";
            saveDraft();
            paintSortChips();
            runRecommend(false);
          }, msg -> {
            locating = false;
            paintSortChips();
            errorView.setText(msg == null ? "无法获取位置" : msg);
          });
        }, () -> {
          if (!enabled[0]) {
            locating = false;
            paintSortChips();
          }
        });
  }

  private void resetAll() {
    selectedHalls.clear();
    includeTags.clear();
    excludeTags.clear();
    excludeDishIds.clear();
    minInput.setText("");
    maxInput.setText("");
    portionCheck.setChecked(true);
    minRating = "";
    sort = "match";
    seed = 0;
    errorView.setText("");
    saveDraft();
    paintAllControls();
    updateSummary();
    runRecommend(false);
  }

  private void updateSummary() {
    List<String> parts = new ArrayList<>();
    String low = minInput.getText().toString().strip();
    String high = maxInput.getText().toString().strip();
    if (!low.isEmpty() && !high.isEmpty()) parts.add("¥" + low + "–¥" + high);
    else if (!high.isEmpty()) parts.add("¥" + high + "以内");
    else if (!low.isEmpty()) parts.add("¥" + low + "以上");
    if (portionCheck.isChecked()) parts.add("仅按份/碗");
    if (!selectedHalls.isEmpty()) parts.add(selectedHalls.size() + "个食堂");
    if (!includeTags.isEmpty()) parts.add("想吃:" + DishTagModels.joinLabels(includeTags, catalog, 3));
    if (!excludeTags.isEmpty()) parts.add("忌口:" + DishTagModels.joinLabels(excludeTags, catalog, 3));
    summaryView.setText("当前：" + (parts.isEmpty() ? "不限条件 · 全校探索" : String.join(" · ", parts)));
  }

  private BigDecimal parseAmount(EditText field, String label) {
    String raw = field.getText().toString().strip();
    if (raw.isEmpty()) return null;
    try {
      BigDecimal val = new BigDecimal(raw);
      if (val.signum() < 0) throw new IllegalArgumentException(label + "不能为负数");
      return val;
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(label + "格式不正确");
    }
  }

  private void runRecommend(boolean roll) {
    errorView.setText("");
    BigDecimal minPrice, maxPrice;
    try {
      minPrice = parseAmount(minInput, "最低预算");
      maxPrice = parseAmount(maxInput, "最高预算");
      if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
        throw new IllegalArgumentException("最低预算不能高于最高预算");
      }
    } catch (IllegalArgumentException bad) {
      errorView.setText(bad.getMessage());
      return;
    }

    Double lat = null, lng = null;
    if (latitude != null && longitude != null && android.os.SystemClock.elapsedRealtime() - locatedAt <= 300000) {
      lat = latitude;
      lng = longitude;
    } else if ("distance".equals(sort)) {
      errorView.setText("定位已过期，请重新点击「距离最近」");
      return;
    }

    if (roll) {
      if (currentResult != null && currentResult.picked() != null) {
        String pid = currentResult.picked().id();
        if (!excludeDishIds.contains(pid)) {
          excludeDishIds.add(pid);
          if (excludeDishIds.size() > 40) excludeDishIds.remove(0);
        }
      }
      seed = seed + 1;
    } else {
      excludeDishIds.clear();
      seed = 0;
    }

    saveDraft();
    updateSummary();

    BigDecimal ratingVal = minRating.isEmpty() ? null : new BigDecimal(minRating);
    SmartRecommendQuery query = new SmartRecommendQuery(
        List.copyOf(selectedHalls), minPrice, maxPrice, portionCheck.isChecked(),
        ratingVal, List.copyOf(includeTags), List.copyOf(excludeTags),
        List.copyOf(excludeDishIds), sort, lat, lng, 12, seed);

    long req = ++revision;
    loading = true;
    submitBtn.setEnabled(false);
    rollBtn.setEnabled(false);
    resultContainer.removeAllViews();
    resultContainer.addView(Ui.text(a, "正在为你挑选最合胃口的菜品…", 13, Design.MUTED));

    var ref = new AtomicReference<SmartRecommendResult>();
    TextView reqErr = new TextView(a);
    a.read(requestKey + ":query:" + req, null, reqErr,
        () -> ref.set(a.repo.recommendDishes(query)),
        () -> {
          if (req != revision) return;
          currentResult = ref.get();
          paintResults();
        },
        () -> {
          if (req != revision) return;
          loading = false;
          submitBtn.setEnabled(true);
          rollBtn.setEnabled(true);
          if (reqErr.length() > 0) {
            errorView.setText(reqErr.getText());
            resultContainer.removeAllViews();
          }
        });
  }

  private void paintResults() {
    resultContainer.removeAllViews();
    if (currentResult == null) return;

    if (!currentResult.exactMode() && !currentResult.items().isEmpty()) {
      LinearLayout banner = Ui.card(resultContainer);
      banner.setBackground(Ui.shape(a, 0xfffef3c7, 0xffd97706, 12));
      TextView heading = Ui.text(a, "严格组合暂无完全匹配，已推荐最接近的菜品", 14, 0xff92400e);
      heading.setTypeface(Typeface.create(Design.body(), Typeface.BOLD));
      banner.addView(heading);
      for (String hint : currentResult.relaxedHints()) {
        Ui.gap(banner, 4);
        banner.addView(Ui.text(a, "• " + hint, 12, 0xff78350f));
      }
      Ui.gap(banner, 10);
      Ui.action(banner, "一键放宽筛选重搜", false, () -> {
        String high = maxInput.getText().toString().strip();
        if (!high.isEmpty()) {
          try {
            BigDecimal bumped = new BigDecimal(high).multiply(new BigDecimal("1.3"))
                .setScale(0, java.math.RoundingMode.CEILING);
            maxInput.setText(bumped.toPlainString());
          } catch (NumberFormatException ignored) {}
        }
        portionCheck.setChecked(false);
        minRating = "";
        paintRatingChips();
        runRecommend(false);
      });
    }

    if (currentResult.items().isEmpty()) {
      Ui.empty(resultContainer, "暂无符合当前食堂与忌口的菜品", "可取消部分忌口或选择全部食堂再试");
      return;
    }

    // Spotlight picked dish
    RecommendedDish picked = currentResult.picked();
    if (picked != null) {
      LinearLayout spot = Ui.card(resultContainer);
      spot.setBackground(Ui.shape(a, Design.PAPER, Design.RED, 14));
      TextView eyebrow = Ui.text(a,
          currentResult.exactMode()
              ? "精准命中 · 就吃这道（共 " + currentResult.exactTotal() + " 道符合）"
              : "近似首选 · 最接近口味",
          12, Design.RED);
      eyebrow.setTypeface(Typeface.create(Design.body(), Typeface.BOLD));
      spot.addView(eyebrow);
      Ui.gap(spot, 6);

      LinearLayout topRow = Ui.row(a);
      topRow.setGravity(Gravity.CENTER_VERTICAL);
      if (picked.imageUrl() != null && !picked.imageUrl().isBlank()) {
        View img = Ui.image(a, Ui.thumb(picked.imageUrl()), picked.name(), false);
        img.setBackground(Ui.shape(a, Design.ALT, 0, 10));
        img.setClipToOutline(true);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(Ui.dp(a, 82), Ui.dp(a, 82));
        ilp.rightMargin = Ui.dp(a, 12);
        topRow.addView(img, ilp);
      }
      LinearLayout info = Ui.col(a);
      TextView title = Ui.title(a, picked.name(), 20);
      info.addView(title);
      Ui.gap(info, 3);
      String venue = picked.diningHallName() + " · " + picked.stallName()
          + (picked.distanceMeters() != null ? " · " + SearchModels.distance(picked.distanceMeters()) : "");
      info.addView(Ui.text(a, venue, 12, Design.MUTED));
      Ui.gap(info, 4);
      TextView price = Ui.text(a, PriceHistory.price(picked.price(), picked.priceUnit()), 20, Design.RED);
      price.setTypeface(Typeface.create(Design.title(), Typeface.BOLD));
      info.addView(price);
      if (picked.averageScore() != null) {
        info.addView(Ui.text(a, "★ " + picked.averageScore().stripTrailingZeros().toPlainString()
            + "（" + picked.reviewCount() + "评）", 12, Design.GOLD));
      }
      topRow.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
      spot.addView(topRow);

      if (!picked.reasons().isEmpty()) {
        Ui.gap(spot, 8);
        spot.addView(Ui.text(a, "推荐理由：" + String.join(" · ", picked.reasons()), 12, Design.INK));
      }
      if (!picked.missedRequirements().isEmpty()) {
        Ui.gap(spot, 4);
        spot.addView(Ui.text(a, "近似提示：" + String.join("；", picked.missedRequirements()), 12, 0xff92400e));
      }
      Ui.gap(spot, 10);
      LinearLayout cta = Ui.row(a);
      TextView detailBtn = Ui.button(a, "查看菜品详情", true, () -> a.go("detail", picked.id()));
      TextView nextBtn = Ui.button(a, "换一道", false, () -> runRecommend(true));
      LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(0, Ui.dp(a, 44), 1);
      LinearLayout.LayoutParams nLp = new LinearLayout.LayoutParams(0, Ui.dp(a, 44), 1);
      nLp.leftMargin = Ui.dp(a, 8);
      cta.addView(detailBtn, dLp);
      cta.addView(nextBtn, nLp);
      spot.addView(cta);
    }

    Ui.gap(resultContainer, 8);
    resultContainer.addView(Ui.title(a, "推荐清单（" + currentResult.items().size() + " 道）", 17));
    Ui.gap(resultContainer, 8);

    for (RecommendedDish dish : currentResult.items()) {
      LinearLayout card = Ui.card(resultContainer);
      card.setClickable(true);
      card.setFocusable(true);
      card.setOnClickListener(v -> a.go("detail", dish.id()));
      LinearLayout row = Ui.row(a);
      row.setGravity(Gravity.CENTER_VERTICAL);
      if (dish.imageUrl() != null && !dish.imageUrl().isBlank()) {
        View thumb = Ui.image(a, Ui.thumb(dish.imageUrl()), dish.name(), false);
        thumb.setBackground(Ui.shape(a, Design.ALT, 0, 8));
        thumb.setClipToOutline(true);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(Ui.dp(a, 64), Ui.dp(a, 64));
        tlp.rightMargin = Ui.dp(a, 10);
        row.addView(thumb, tlp);
      }
      LinearLayout col = Ui.col(a);
      TextView name = Ui.text(a, dish.name(), 15, Design.INK);
      name.setTypeface(Typeface.create(Design.body(), Typeface.BOLD));
      name.setSingleLine(true);
      name.setEllipsize(TextUtils.TruncateAt.END);
      col.addView(name);
      col.addView(Ui.text(a, dish.diningHallName() + " · " + dish.stallName(), 12, Design.MUTED));
      LinearLayout metrics = Ui.row(a);
      TextView p = Ui.text(a, PriceHistory.price(dish.price(), dish.priceUnit()), 14, Design.RED);
      p.setTypeface(Typeface.create(Design.body(), Typeface.BOLD));
      metrics.addView(p);
      if (dish.averageScore() != null) {
        metrics.addView(Ui.text(a, "  ★ " + dish.averageScore().stripTrailingZeros().toPlainString(), 12, Design.GOLD));
      }
      if (dish.distanceMeters() != null) {
        metrics.addView(Ui.text(a, "  " + SearchModels.distance(dish.distanceMeters()), 11, Design.MUTED));
      }
      col.addView(metrics);
      String tagText = DishTagModels.joinLabels(dish.tags(), catalog, 4);
      if (!tagText.isBlank()) {
        col.addView(Ui.text(a, tagText, 11, Design.MUTED));
      }
      if (!dish.missedRequirements().isEmpty()) {
        col.addView(Ui.text(a, "近似：" + String.join("；", dish.missedRequirements()), 11, 0xff92400e));
      }
      row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
      card.addView(row);
    }
  }

  private void jumpToUnifiedSearch() {
    String prefix = "dish-search::unified-search:" + a.repo.currentUser().id();
    String dishPrefix = prefix + ":dishes";
    a.drafts.putString(prefix + ":tab", "dishes");
    a.drafts.putString(prefix + ":q", "");
    a.drafts.putBoolean(prefix + ":searched", true);
    a.drafts.putInt(prefix + ":page", 1);
    a.drafts.putStringArrayList(dishPrefix + ":halls", new ArrayList<>(selectedHalls));
    a.drafts.putString(dishPrefix + ":min", minInput.getText().toString().strip());
    a.drafts.putString(dishPrefix + ":max", maxInput.getText().toString().strip());
    a.drafts.putString(dishPrefix + ":rating", minRating);
    a.drafts.putBoolean(dishPrefix + ":portionOnly", portionCheck.isChecked());
    a.drafts.putStringArrayList(dishPrefix + ":tags", new ArrayList<>(includeTags));
    a.drafts.putStringArrayList(dishPrefix + ":excludeTags", new ArrayList<>(excludeTags));
    a.go("dish-search", "");
  }

  private void saveDraft() {
    a.drafts.putStringArrayList(key + ":halls", new ArrayList<>(selectedHalls));
    a.drafts.putString(key + ":min", minInput.getText().toString().strip());
    a.drafts.putString(key + ":max", maxInput.getText().toString().strip());
    a.drafts.putBoolean(key + ":portionOnly", portionCheck.isChecked());
    a.drafts.putString(key + ":rating", minRating);
    a.drafts.putStringArrayList(key + ":includeTags", new ArrayList<>(includeTags));
    a.drafts.putStringArrayList(key + ":excludeTags", new ArrayList<>(excludeTags));
    a.drafts.putString(key + ":sort", "distance".equals(sort) ? "match" : sort);
  }

  private void restoreDraft() {
    selectedHalls.clear();
    var h = a.drafts.getStringArrayList(key + ":halls");
    if (h != null) selectedHalls.addAll(h);
    minInput.setText(a.drafts.getString(key + ":min", ""));
    maxInput.setText(a.drafts.getString(key + ":max", ""));
    portionCheck.setChecked(a.drafts.getBoolean(key + ":portionOnly", true));
    minRating = a.drafts.getString(key + ":rating", "");
    includeTags.clear();
    var inc = a.drafts.getStringArrayList(key + ":includeTags");
    if (inc != null) includeTags.addAll(inc);
    excludeTags.clear();
    var exc = a.drafts.getStringArrayList(key + ":excludeTags");
    if (exc != null) excludeTags.addAll(exc);
    sort = a.drafts.getString(key + ":sort", "match");
    if ("distance".equals(sort) && latitude == null) sort = "match";
  }
}
