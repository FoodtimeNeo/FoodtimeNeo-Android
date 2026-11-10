package cn.edu.bjtu.foodtime.presentation;

import android.content.Context;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.edu.bjtu.foodtime.domain.model.Models.Dish;
import cn.edu.bjtu.foodtime.domain.model.Models.Hall;
import cn.edu.bjtu.foodtime.domain.model.Models.Stall;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

final class DishPickerScreen {
  static void show(MainActivity activity, LinearLayout page, MainActivity.Route route) {
    new DishPickerScreen(activity, page, route);
  }

  private final MainActivity activity;
  private final LinearLayout panel;
  private final String pickerDraft, postDraft;
  private final String requestKey = "dish-picker:" + UUID.randomUUID();
  private int request;

  private DishPickerScreen(MainActivity activity, LinearLayout page, MainActivity.Route route) {
    this.activity = activity;
    pickerDraft = activity.draftKey("");
    postDraft = "community-create:" + route.id() + ":";
    activity.heading(page, "", "选择关联菜品", "依次选择食堂、档口和菜品。留空显示当前列表的全部选项。");
    panel = Ui.col(activity);
    page.addView(panel);
    panel.post(() -> {
      if (panel.isAttachedToWindow()) render();
    });
  }

  private void render() {
    panel.removeAllViews();
    String hallId = value("hallId");
    String stallId = value("stallId");
    if (hallId.isEmpty()) {
      load("食堂", "个食堂", activity.repo::loadHalls, Hall::name, this::chooseHall);
      return;
    }
    Ui.action(panel, "更换食堂 · " + value("hallName"), false, () -> {
      activity.drafts.remove(pickerDraft + "hallId");
      clearStall();
      changeLevel();
    });
    if (stallId.isEmpty()) {
      load("档口", "个档口", () -> activity.repo.loadStalls(hallId), Stall::name, this::chooseStall);
      return;
    }
    Ui.action(panel, "更换档口 · " + value("stallName"), false, () -> {
      clearStall();
      changeLevel();
    });
    load("菜品", "道菜品", () -> activity.repo.loadDishes(stallId), Dish::name, this::chooseDish);
  }

  private <T> void load(String level, String unit, Supplier<List<T>> query,
      Function<T, String> name, Consumer<T> select) {
    int ticket = ++request;
    LinearLayout content = Ui.col(activity);
    panel.addView(content);
    TextView status = Ui.text(activity, "正在加载" + level + "…", 14, Design.MUTED);
    TextView error = Ui.text(activity, "", 14, Design.RED);
    TextView retry = Ui.button(activity, "重试", false, this::render);
    retry.setVisibility(android.view.View.GONE);
    Ui.observeError(error);
    content.addView(status);
    content.addView(error);
    content.addView(retry);
    AtomicReference<List<T>> result = new AtomicReference<>();
    activity.read(requestKey + ":" + ticket, null, error, () -> result.set(query.get()), () -> {
      if (ticket != request || !content.isAttachedToWindow()) return;
      content.removeAllViews();
      CatalogList.show(activity, content, "筛选" + level, "输入" + level + "名称", unit, result.get(), name,
          (list, item) -> Ui.action(list, name.apply(item), false, () -> select.accept(item)));
    }, () -> {
      if (ticket == request && result.get() == null) {
        status.setVisibility(android.view.View.GONE);
        retry.setVisibility(android.view.View.VISIBLE);
      }
    });
  }

  private void chooseHall(Hall hall) {
    activity.drafts.putString(pickerDraft + "hallId", hall.id());
    activity.drafts.putString(pickerDraft + "hallName", hall.name());
    clearStall();
    changeLevel();
  }

  private void chooseStall(Stall stall) {
    activity.drafts.putString(pickerDraft + "stallId", stall.id());
    activity.drafts.putString(pickerDraft + "stallName", stall.name());
    changeLevel();
  }

  private void chooseDish(Dish dish) {
    hideKeyboard();
    activity.drafts.putString(postDraft + "linkedDishId", dish.id());
    activity.drafts.putString(postDraft + "linkedDishName",
        value("hallName") + " · " + value("stallName") + " · " + dish.name());
    activity.back();
  }

  private void clearStall() {
    activity.drafts.remove(pickerDraft + "stallId");
    activity.drafts.remove(pickerDraft + "stallName");
  }

  private void changeLevel() {
    hideKeyboard();
    activity.drafts.remove(pickerDraft + "search");
    render();
    activity.scroll.scrollTo(0, 0);
  }

  private String value(String key) {
    return activity.drafts.getString(pickerDraft + key, "");
  }

  private void hideKeyboard() {
    ((InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE))
        .hideSoftInputFromWindow(panel.getWindowToken(), 0);
  }
}
