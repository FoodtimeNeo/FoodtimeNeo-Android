package cn.edu.bjtu.foodtime.presentation;

import android.app.AlertDialog;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.edu.bjtu.foodtime.domain.model.Models.DishSearchPage;
import cn.edu.bjtu.foodtime.domain.model.Models.FavoriteGroup;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

final class FavoriteDishesScreen {
  static void show(MainActivity activity, LinearLayout page, MainActivity.Route route) {
    activity.heading(page, "", "想吃清单", "存下想吃的菜，下次直接找。");
    new FavoriteDishesScreen(activity, page);
  }

  private final MainActivity activity;
  private final LinearLayout page, rows, groupBar;
  private final TextView status, error, reload;
  private final String requestKey = "favorite-dishes:" + UUID.randomUUID();
  private List<FavoriteGroup> groups = List.of();
  private String selectedGroupId;
  private boolean unfiled;
  private int groupEpoch,loadEpoch;

  private FavoriteDishesScreen(MainActivity activity, LinearLayout page) {
    this.activity = activity;
    this.page = page;
    status = Ui.text(activity, "", 14, Design.MUTED);
    error = Ui.text(activity, "", 14, Design.RED);
    Ui.observeError(error);
    reload = Ui.button(activity, "重新加载", false, () -> load(1));
    groupBar = Ui.col(activity);
    rows = Ui.col(activity);
    page.addView(groupBar);
    page.addView(status);
    page.addView(error);
    page.addView(reload);
    page.addView(rows);
    paintGroups();
    page.post(() -> {
      if (page.isAttachedToWindow()) {
        loadGroups();
        load(1);
      }
    });
  }

  private void loadGroups() {
    int ticket=++groupEpoch;
    AtomicReference<List<FavoriteGroup>> result = new AtomicReference<>();
    activity.read(requestKey + ":groups:"+ticket, null, error, () -> result.set(activity.repo.favoriteGroups("dish")), () -> {
      if(ticket!=groupEpoch)return;
      groups = result.get();
      if (selectedGroupId != null && groups.stream().noneMatch(group -> group.id().equals(selectedGroupId)))
        selectedGroupId = null;
      paintGroups();
    }, () -> {});
  }

  private void paintGroups() {
    groupBar.removeAllViews();
    LinearLayout chips = Ui.row(activity);
    chips.setTag("favorite-group-chips");
    chips.addView(Ui.button(activity, "全部", selectedGroupId == null && !unfiled, () -> selectGroup(null, false)));
    chips.addView(Ui.button(activity, "默认分组", unfiled, () -> selectGroup(null, true)));
    for (FavoriteGroup group : groups) {
      chips.addView(Ui.button(activity, group.name() + " (" + group.itemCount() + ")",
          group.id().equals(selectedGroupId), () -> selectGroup(group.id(), false)));
    }
    android.widget.HorizontalScrollView scroll=new android.widget.HorizontalScrollView(activity);
    scroll.setHorizontalScrollBarEnabled(false);scroll.addView(chips);
    groupBar.addView(scroll,new LinearLayout.LayoutParams(-1,-2));
    LinearLayout actions = Ui.row(activity);
    actions.addView(Ui.button(activity, "+ 新建分组", false, this::createGroup));
    if (selectedGroupId != null)
      actions.addView(Ui.button(activity, "管理当前分组", false, this::manageSelectedGroup));
    groupBar.addView(actions);
  }

  private void selectGroup(String groupId, boolean onlyUnfiled) {
    selectedGroupId = groupId;
    unfiled = onlyUnfiled;
    paintGroups();
    load(1);
  }

  private void createGroup() {
    FavoriteGroupTools.create(activity,"dish",()->{loadGroups();load(1);});
  }

  private void manageSelectedGroup() {
    FavoriteGroup selected = groups.stream().filter(group -> group.id().equals(selectedGroupId)).findFirst().orElse(null);
    if (selected == null) return;
    new AlertDialog.Builder(activity).setTitle(selected.name()).setItems(
        new String[]{"重命名", "删除分组"}, (dialog, which) -> {
          if (which == 0) promptName("重命名分组", selected.name(), name -> activity.request(null, error,
              () -> activity.repo.updateFavoriteGroup(selected.id(), name, null, selected.revision()),
              () -> { loadGroups(); load(1); }));
          else new AlertDialog.Builder(activity).setTitle("删除分组？")
              .setMessage("分组内的收藏仍会保留。")
              .setNegativeButton("取消", null)
              .setPositiveButton("删除", (confirm, ignored) -> activity.request(null, error,
                  () -> activity.repo.deleteFavoriteGroup(selected.id()),
                  () -> { selectedGroupId = null; loadGroups(); load(1); })).show();
        }).show();
  }

  private void promptName(String title, String initial, java.util.function.Consumer<String> action) {
    EditText input = new EditText(activity);
    input.setSingleLine(true);
    input.setInputType(InputType.TYPE_CLASS_TEXT);
    input.setText(initial);
    input.setHint("1–20 个字符");
    new AlertDialog.Builder(activity).setTitle(title).setView(input)
        .setNegativeButton("取消", null)
        .setPositiveButton("保存", (dialog, which) -> {
          String name = input.getText().toString().strip();
          if (name.isEmpty()) { error.setText("分组名称不能为空"); return; }
          action.accept(name);
        }).show();
  }

  private void load(int page) {
    int ticket=++loadEpoch;String groupId=selectedGroupId;boolean onlyUnfiled=unfiled;
    rows.removeAllViews();
    status.setText("正在加载想吃清单…");
    AtomicReference<DishSearchPage> data = new AtomicReference<>();
    activity.read(requestKey + ":page:"+ticket, reload, error,
        () -> data.set(activity.repo.favoriteDishes(page, groupId, onlyUnfiled)), () -> {
          if(ticket!=loadEpoch)return;
          DishSearchPage result = data.get();
          status.setText(result.items().isEmpty() ? (selectedGroupId == null && !unfiled
              ? "这页还没有想吃的菜。" : "这个分组还没有菜品。")
              : "共 " + result.total() + " 道菜 · 第 " + page + " 页");
          for (var dish : result.items()) {
            DishSearchScreen.appendRow(activity, rows, dish, () -> activity.go("detail", dish.id()));
            Ui.link(rows, "设置分组", () -> chooseGroups(dish.id()));
          }
          if (page > 1) Ui.action(rows, "上一页", false, () -> load(page - 1));
          if (result.hasMore()) Ui.action(rows, "下一页", false, () -> load(page + 1));
        }, () -> status.setText(""));
  }

  private void chooseGroups(String dishId) {
    FavoriteGroupTools.choose(activity,"dish",dishId,()->{loadGroups();load(1);});
  }
}
