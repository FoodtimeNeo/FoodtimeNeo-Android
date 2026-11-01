package cn.edu.bjtu.foodtime.presentation;

import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.edu.bjtu.foodtime.domain.model.Models.FollowPage;
import cn.edu.bjtu.foodtime.domain.model.Models.FollowUser;

/** 我的关注 / 关注我的：两条列表共用一套渲染与关注切换逻辑。 */
final class FollowListScreen {
  private FollowListScreen() { }

  static void following(MainActivity a, LinearLayout page, MainActivity.Route route) {
    render(a, page, true, a.repo.following());
  }

  static void followers(MainActivity a, LinearLayout page, MainActivity.Route route) {
    render(a, page, false, a.repo.followers());
  }

  private static void render(MainActivity a, LinearLayout page, boolean following, FollowPage data) {
    a.heading(page, "09", following ? "我的关注" : "关注我的", "");
    page.addView(tabs(a, following));
    Ui.gap(page, 12);

    if (data.items().isEmpty()) {
      Ui.empty(page, following ? "还没有关注任何人" : "还没有人关注你",
          following ? "在社区食记里点击作者旁的“关注”，就会出现在这里。" : "发布食记或点评，让更多同学发现你。");
      return;
    }

    for (FollowUser row : data.items()) {
      LinearLayout card = Ui.card(page);
      LinearLayout line = Ui.row(a);
      line.setGravity(Gravity.CENTER_VERTICAL);
      String name = row.displayName().isBlank() ? "校园干饭人" : row.displayName();
      android.view.View avatar = Ui.image(a, Ui.thumb(row.avatarUrl()),
          name.isEmpty() ? "饭" : name.substring(0, 1), false);
      avatar.setContentDescription(name + "的头像");
      LinearLayout.LayoutParams avatarLp = new LinearLayout.LayoutParams(Ui.dp(a, 44), Ui.dp(a, 44));
      avatarLp.rightMargin = Ui.dp(a, 12);
      line.addView(avatar, avatarLp);
      TextView label = Ui.text(a, name, 17, Design.INK);
      label.setSingleLine(true);
      line.addView(label, new LinearLayout.LayoutParams(0, -2, 1));

      boolean next = !row.followedByMe();
      TextView action = Ui.button(a, row.followedByMe() ? "已关注" : "关注", false, () -> {});
      action.setOnClickListener(v -> a.request(action, null,
          () -> a.repo.setFollow(row.id(), next),
          () -> {
            a.message(next ? "已关注" : "已取消关注");
            a.render();
          }));
      line.addView(action);
      card.addView(line);
    }
    Ui.gap(page, 8);
    page.addView(Ui.text(a, "共 " + data.total() + (following ? " 人被我关注" : " 人关注了我"), 12, Design.MUTED));
  }

  private static LinearLayout tabs(MainActivity a, boolean following) {
    LinearLayout tabs = Ui.row(a);
    tabs.setPadding(Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 4));
    tabs.setBackground(Ui.shape(a, Design.KRAFT_SOFT, 0, 24));
    String[] labels = {"我的关注", "关注我的"};
    for (int i = 0; i < labels.length; i++) {
      boolean active = (i == 0) == following;
      TextView tab = Ui.text(a, labels[i], 14, active ? Design.ON_RED : Design.INK);
      tab.setGravity(Gravity.CENTER);
      tab.setMinHeight(Ui.dp(a, 42));
      tab.setClickable(true);
      tab.setFocusable(true);
      tab.setBackground(Ui.ripple(a, active ? Design.RED : android.graphics.Color.TRANSPARENT, 0, 20));
      String target = i == 0 ? "following" : "followers";
      tab.setOnClickListener(v -> a.go(target, ""));
      tabs.addView(tab, new LinearLayout.LayoutParams(0, -2, 1));
    }
    return tabs;
  }
}
