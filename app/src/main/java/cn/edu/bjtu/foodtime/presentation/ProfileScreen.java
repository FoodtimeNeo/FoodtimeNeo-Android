package cn.edu.bjtu.foodtime.presentation;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.widget.*;
import cn.edu.bjtu.foodtime.BuildConfig;
import cn.edu.bjtu.foodtime.R;
import cn.edu.bjtu.foodtime.domain.model.Models.*;
import cn.edu.bjtu.foodtime.domain.model.ReleaseVersion;
import cn.edu.bjtu.foodtime.domain.repository.ContentRequest;
import java.util.*;

final class ProfileScreen {
  static void profile(MainActivity a, LinearLayout page, MainActivity.Route route) {
    User user = a.repo.currentUser();
    page.setBackgroundColor(Design.PAPER);
    profileHeader(a, page);

    // 1. 顶部身份卡片瘦身与去测试态
    LinearLayout identity = Ui.col(a);
    identity.setPadding(Ui.dp(a, 16), Ui.dp(a, 16), Ui.dp(a, 16), Ui.dp(a, 16));
    identity.setBackground(Ui.shape(a, Design.ALT, Design.BORDER, 16));

    LinearLayout person = Ui.row(a);
    person.setGravity(Gravity.CENTER_VERTICAL);
    View avatar = Ui.image(a, Ui.thumb(user.avatarUrl()), user.displayName().isEmpty() ? "用户" : user.displayName().substring(0, 1), false);
    avatar.setBackground(Ui.shape(a, Design.PAPER, Design.RED, 999));
    avatar.setClipToOutline(true);
    avatar.setContentDescription("个人头像");
    person.addView(avatar, new LinearLayout.LayoutParams(Ui.dp(a, 62), Ui.dp(a, 62)));

    LinearLayout copy = Ui.col(a);
    LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(0, -2, 1);
    copyLp.leftMargin = Ui.dp(a, 14);

    copy.addView(Ui.title(a, user.displayName(), 21));
    Ui.gap(copy, 3);
    copy.addView(Ui.text(a, "认真吃饭，也认真生活", 12, Design.MUTED));
    Ui.gap(copy, 8);

    // 关注、粉丝轻量数字排布与“编辑资料”紧凑排列
    LinearLayout statsRow = Ui.row(a);
    statsRow.setGravity(Gravity.CENTER_VERTICAL);

    TextView followingCount = new TextView(a);
    followingCount.setText("关注 " + user.followingCount());
    followingCount.setTextSize(13);
    followingCount.setTextColor(Design.INK);
    followingCount.setTag("profile-following-count");
    followingCount.setContentDescription("我的关注，" + user.followingCount() + "人");
    followingCount.setFocusable(true);
    followingCount.setClickable(true);
    followingCount.setBackground(Ui.ripple(a, 0, 0, 8));
    followingCount.setPadding(0, Ui.dp(a, 2), Ui.dp(a, 4), Ui.dp(a, 2));
    followingCount.setOnClickListener(v -> a.go("following", ""));
    statsRow.addView(followingCount);

    View div1 = new View(a);
    div1.setBackgroundColor(0x25000000);
    LinearLayout.LayoutParams div1Lp = new LinearLayout.LayoutParams(Ui.dp(a, 1), Ui.dp(a, 11));
    div1Lp.leftMargin = Ui.dp(a, 6);
    div1Lp.rightMargin = Ui.dp(a, 6);
    statsRow.addView(div1, div1Lp);

    TextView followerCount = new TextView(a);
    followerCount.setText("粉丝 " + user.followerCount());
    followerCount.setTextSize(13);
    followerCount.setTextColor(Design.INK);
    followerCount.setTag("profile-follower-count");
    followerCount.setContentDescription("关注我的，" + user.followerCount() + "人");
    followerCount.setFocusable(true);
    followerCount.setClickable(true);
    followerCount.setBackground(Ui.ripple(a, 0, 0, 8));
    followerCount.setPadding(Ui.dp(a, 4), Ui.dp(a, 2), Ui.dp(a, 4), Ui.dp(a, 2));
    followerCount.setOnClickListener(v -> a.go("followers", ""));
    statsRow.addView(followerCount);

    statsRow.addView(new View(a), new LinearLayout.LayoutParams(0, 1, 1));

    TextView edit = Ui.button(a, "编辑资料", false, () -> a.go("profile-edit", ""));
    edit.setTextSize(12);
    edit.setMinHeight(Ui.dp(a, 28));
    edit.setPadding(Ui.dp(a, 10), Ui.dp(a, 2), Ui.dp(a, 10), Ui.dp(a, 2));
    edit.setBackground(Ui.ripple(a, Design.PAPER, Design.RED, 14));
    edit.setTextColor(Design.RED);
    statsRow.addView(edit);

    copy.addView(statsRow);
    person.addView(copy, copyLp);
    identity.addView(person);
    page.addView(identity, new LinearLayout.LayoutParams(-1, -2));
    Ui.gap(page, 16);

    if ("visitor".equals(user.role())) {
      LinearLayout guest = Ui.col(a);
      guest.setPadding(Ui.dp(a, 18), Ui.dp(a, 16), Ui.dp(a, 18), Ui.dp(a, 18));
      guest.setBackground(Ui.shape(a, Design.KRAFT_SOFT, Design.RED, 22));
      guest.addView(Ui.title(a, "学校邮箱认证", 20));
      Ui.gap(guest, 6);
      guest.addView(Ui.text(a, "学校邮箱绑定为可选认证；当前可用功能以账号权限为准。", 14, Design.INK));
      Ui.gap(guest, 12);
      TextView unlock = Ui.button(a, "去绑定学校邮箱", true, () -> a.go("bind-school-email", ""));
      unlock.setBackground(Ui.ripple(a, Design.RED, 0, 18));
      guest.addView(unlock, new LinearLayout.LayoutParams(-1, -2));
      page.addView(guest, new LinearLayout.LayoutParams(-1, -2));
      Ui.gap(page, 16);
    }

    GridGroup saved = new GridGroup(a, page, "常用");
    saved.add("想吃清单", "favorite-dishes", "", R.drawable.ic_hub_dish, "");
    saved.add("帖子收藏", "community-favorites", "", R.drawable.ic_hub_star, "");
    saved.add("浏览历史", "history", "", R.drawable.ic_hub_clock, "");
    saved.add("发帖草稿", "community-create", "", R.drawable.ic_hub_edit, "");

    List<Review> myReviews = a.repo.myReviews();
    String reviewsCount = myReviews != null && !myReviews.isEmpty() ? String.valueOf(myReviews.size()) : "";
    GridGroup records = new GridGroup(a, page, "我的记录");
    records.add("我的食记", "community-mine", "", R.drawable.ic_hub_note, "");
    records.add("菜品点评", "mine-reviews", "", R.drawable.ic_hub_comment, reviewsCount);
    records.add("餐厅点评", "venue-mine", "", R.drawable.ic_hub_store, "");
    records.add("我的回复", "mine-review-replies", "1", R.drawable.ic_hub_reply, "");
    records.add("我的提交", "mine-submissions", "", R.drawable.ic_hub_upload, "");

    long unread = a.repo.unreadCount();
    GridGroup services = new GridGroup(a, page, "服务");
    services.add("消息信箱", "inbox", "", R.drawable.ic_hub_inbox, unread > 0 ? String.valueOf(unread) : "");
    services.add("建议与反馈", "feedback", "", R.drawable.ic_hub_feedback, "");
    services.add("我的纠错", "my-corrections", "", R.drawable.ic_hub_correct, "");
    services.add("举报与申诉", "report-receipts", "", R.drawable.ic_hub_shield, "");

    // 5. 重构“账号与隐私”标准分组卡片列表
    GroupBuilder account = new GroupBuilder(a, page, "账号与隐私");
    account.addRow(R.drawable.ic_hub_lock, false, 0, "修改密码", "password", "", "");
    account.addRow(R.drawable.ic_hub_block, false, 0, "黑名单", "blocked-users", "", "");
    account.addRow(R.drawable.ic_hub_bell, false, 0, "提醒设置", "notification-settings", "", "");
    TextView appearance = account.addCustomRow(R.drawable.ic_hub_palette, false, 0, "外观", () -> AppTheme.choose(a));
    appearance.setText(switch(AppTheme.mode(a)){case AppTheme.LIGHT -> "浅色";case AppTheme.DARK -> "深色";default -> "跟随系统";});
    appearance.setTag("appearance-setting");
    if (!user.isAdmin()) {
      account.addRow(R.drawable.ic_hub_key, false, 0, "账号安全与注销", "account-lifecycle", "", "");
    }

    // 图片缓存清理行
    TextView cacheSize = account.addCustomRow(R.drawable.ic_hub_trash, false, 0, "清理图片缓存", () -> {
      Ui.confirm(a, "清理图片缓存？", "仅清理本地图片缓存，不影响账号数据。", () -> {
        a.request(null, null, a.repo::clearImageCache, () -> {
          Ui.clearImages();
          Ui.resumeImages();
          TextView cs = page.findViewWithTag("profile-cache-size");
          if (cs != null) cs.setText("0 MiB");
          a.message("图片缓存已清理");
        });
      });
    });
    cacheSize.setTag("profile-cache-size");
    long[] usage = {0};
    a.read("image-cache-size", null, null,
        () -> usage[0] = a.repo.imageCacheBytes(),
        () -> cacheSize.setText(String.format(Locale.CHINA, "%.1f MiB", usage[0] / 1048576.0)),
        () -> {});

    // 退出登录
    Ui.gap(page, 8);
    roundedAction(a, page, "退出登录", false, () -> Ui.confirm(a, "退出登录？", "", a::logout));
    Ui.gap(page, 16);

    // 页底低调版本号与检查更新
    TextView versionFooter = Ui.text(a, "版本 " + ReleaseVersion.display(BuildConfig.VERSION_NAME) + " · 检查更新", 12, Design.MUTED);
    versionFooter.setGravity(Gravity.CENTER);
    versionFooter.setPadding(0, Ui.dp(a, 4), 0, Ui.dp(a, 12));
    versionFooter.setOnClickListener(v -> a.checkAppRelease(true));
    page.addView(versionFooter);

    // 涂鸦底饰
    LinearLayout doodles = Ui.row(a);
    doodles.setGravity(Gravity.CENTER);
    doodles.addView(Ui.foodDoodle(a, 7, 0x554f7250), new LinearLayout.LayoutParams(Ui.dp(a, 74), Ui.dp(a, 58)));
    doodles.addView(Ui.foodDoodle(a, 8, 0x55c9341e), new LinearLayout.LayoutParams(Ui.dp(a, 74), Ui.dp(a, 58)));
    page.addView(doodles);

    // 异步加载各模块最新数字统计
    loadProfileStatsAsync(a, page);
  }

  private static void loadProfileStatsAsync(MainActivity a, LinearLayout page) {
    page.post(() -> {
      if (!page.isAttachedToWindow()) return;
      // 1. 想吃清单总数
      long[] favCount = {0};
      a.read("profile-fav-count", null, null, () -> {
        favCount[0] = a.repo.favoriteDishes(1).total();
      }, () -> {
        if (favCount[0] > 0) {
          TextView v = page.findViewWithTag("profile-count:favorite-dishes");
          if (v != null) {
            v.setVisibility(View.VISIBLE);
            v.setText(String.valueOf(favCount[0]));
          }
        }
      }, () -> {});

      // 2. 帖子收藏数
      long[] postFavCount = {0};
      a.read("profile-post-fav-count", null, null, () -> {
        a.repo.refresh(new ContentRequest(ContentRequest.Query.MY_COMMUNITY_FAVORITES, ""));
        var pageData = a.repo.communityCollection(true);
        postFavCount[0] = pageData != null ? pageData.total() : 0;
      }, () -> {
        if (postFavCount[0] > 0) {
          TextView v = page.findViewWithTag("profile-count:community-favorites");
          if (v != null) {
            v.setVisibility(View.VISIBLE);
            v.setText(String.valueOf(postFavCount[0]));
          }
        }
      }, () -> {});

      // 3. 我的食记数
      long[] myPostsCount = {0};
      a.read("profile-my-posts-count", null, null, () -> {
        a.repo.refresh(new ContentRequest(ContentRequest.Query.MY_COMMUNITY_POSTS, ""));
        var pageData = a.repo.communityCollection(false);
        myPostsCount[0] = pageData != null ? pageData.total() : 0;
      }, () -> {
        if (myPostsCount[0] > 0) {
          TextView v = page.findViewWithTag("profile-count:community-mine");
          if (v != null) {
            v.setVisibility(View.VISIBLE);
            v.setText(String.valueOf(myPostsCount[0]));
          }
        }
      }, () -> {});

      // 4. 餐厅档口点评数
      long[] venueReviewsCount = {0};
      a.read("profile-venue-reviews-count", null, null, () -> {
        a.repo.loadMyVenueReviews("", false, 1);
        var venueReviews = a.repo.myVenueReviews();
        venueReviewsCount[0] = venueReviews != null ? venueReviews.total() : 0;
      }, () -> {
        if (venueReviewsCount[0] > 0) {
          TextView v = page.findViewWithTag("profile-count:venue-mine");
          if (v != null) {
            v.setVisibility(View.VISIBLE);
            v.setText(String.valueOf(venueReviewsCount[0]));
          }
        }
      }, () -> {});
    });
  }

  private static void profileHeader(MainActivity a, LinearLayout page) {
    LinearLayout header = Ui.row(a);
    header.setGravity(Gravity.CENTER_VERTICAL);
    header.addView(Ui.title(a, "我的", 28), new LinearLayout.LayoutParams(0, -2, 1));

    FrameLayout inbox = new FrameLayout(a);
    inbox.setClickable(true);
    inbox.setFocusable(true);
    inbox.setTag("profile-inbox-button");
    inbox.setClipChildren(false);
    inbox.setClipToPadding(false);
    inbox.setBackground(Ui.ripple(a, Design.ALT, 0, 24));

    ImageView bell = new ImageView(a);
    bell.setImageResource(R.drawable.ic_hub_inbox);
    bell.setImageTintList(android.content.res.ColorStateList.valueOf(Design.RED));
    bell.setPadding(Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8));
    bell.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    inbox.addView(bell, new FrameLayout.LayoutParams(-1, -1));

    TextView badge = Ui.text(a, "", 10, Design.ON_RED);
    badge.setGravity(Gravity.CENTER);
    badge.setTypeface(Design.body(), android.graphics.Typeface.BOLD);
    badge.setTag("profile-inbox-badge");
    badge.setBackground(Ui.shape(a, Design.RED, 0, 999));
    badge.setMinWidth(Ui.dp(a, 18));
    badge.setMinHeight(Ui.dp(a, 18));
    FrameLayout.LayoutParams badgeLp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
    badgeLp.topMargin = -Ui.dp(a, 2);
    badgeLp.rightMargin = -Ui.dp(a, 2);
    inbox.addView(badge, badgeLp);

    inbox.setOnClickListener(v -> a.go("inbox", ""));
    LinearLayout.LayoutParams inboxLp = new LinearLayout.LayoutParams(Ui.dp(a, 44), Ui.dp(a, 44));
    inboxLp.leftMargin = Ui.dp(a, 12);
    header.addView(inbox, inboxLp);

    page.addView(header, new LinearLayout.LayoutParams(-1, -2));
    Ui.gap(page, 16);
    updateUnreadBadge(a);
  }

  static void updateUnreadBadge(MainActivity activity) {
    if (activity.root == null) return;
    View inbox = activity.root.findViewWithTag("profile-inbox-button");
    TextView badge = activity.root.findViewWithTag("profile-inbox-badge");
    if (inbox == null || badge == null) return;
    long unread = activity.repo.unreadCount();
    inbox.setContentDescription(unread > 0 ? "通知信箱，" + unread + "条未读" : "通知信箱");
    badge.setVisibility(unread > 0 ? View.VISIBLE : View.GONE);
    badge.setText(unread > 99 ? "99+" : String.valueOf(unread));
  }

  private static final class GridGroup {
    private final MainActivity a;
    private final GridLayout grid;
    private final int columns;

    GridGroup(MainActivity a, LinearLayout page, String title) {
      this.a = a;
      LinearLayout container = Ui.col(a);
      container.setPadding(Ui.dp(a, 12), Ui.dp(a, 14), Ui.dp(a, 12), Ui.dp(a, 8));
      container.setBackground(Ui.shape(a, Design.ALT, Design.BORDER, 16));
      TextView heading = Ui.title(a, title, 17);
      heading.setPadding(Ui.dp(a, 4), 0, Ui.dp(a, 4), 0);
      heading.setTag("profile-group:" + title);
      container.addView(heading);
      Ui.gap(container, 10);
      var config = a.getResources().getConfiguration();
      float available = Math.max(240, config.screenWidthDp - 56) / Math.max(1, config.fontScale);
      columns = available >= 320 ? 4 : available >= 240 ? 3 : 2;
      grid = new GridLayout(a);
      grid.setColumnCount(columns);
      grid.setTag("profile-grid:" + title);
      container.addView(grid, new LinearLayout.LayoutParams(-1, -2));
      LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
      cardLp.bottomMargin = Ui.dp(a, 12);
      page.addView(container, cardLp);
    }

    void add(String label, String route, String id, int iconRes, String count) {
      int index = grid.getChildCount();
      LinearLayout tile = Ui.col(a);
      tile.setTag("profile-entry:" + route);
      tile.setGravity(Gravity.CENTER_HORIZONTAL);
      tile.setPadding(Ui.dp(a, 3), Ui.dp(a, 12), Ui.dp(a, 3), Ui.dp(a, 12));
      tile.setMinimumHeight(Ui.dp(a, 108));
      tile.setFocusable(true);
      tile.setClickable(true);
      tile.setContentDescription(label);
      tile.setBackground(Ui.ripple(a, Color.TRANSPARENT, 0, 12));
      tile.setOnClickListener(v -> a.go(route, id));

      FrameLayout iconFrame = new FrameLayout(a);
      ImageView image = new ImageView(a);
      image.setImageResource(iconRes);
      image.setImageTintList(android.content.res.ColorStateList.valueOf(Design.RED));
      image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
      iconFrame.addView(image, new FrameLayout.LayoutParams(Ui.dp(a, 28), Ui.dp(a, 28), Gravity.CENTER));
      TextView badge = Ui.text(a, count, 10, Design.RED);
      badge.setTag("profile-count:" + route);
      badge.setGravity(Gravity.CENTER);
      badge.setBackground(Ui.shape(a, Design.PAPER, 0, 8));
      badge.setVisibility(count == null || count.isEmpty() || count.equals("0") ? View.GONE : View.VISIBLE);
      iconFrame.addView(badge, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END));
      tile.addView(iconFrame, new LinearLayout.LayoutParams(Ui.dp(a, 52), Ui.dp(a, 38)));
      Ui.gap(tile, 8);
      TextView text = Ui.text(a, label, 13, Design.INK);
      text.setGravity(Gravity.CENTER);
      text.setMaxLines(2);
      tile.addView(text, new LinearLayout.LayoutParams(-1, -2));
      GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
          GridLayout.spec(index / columns), GridLayout.spec(index % columns, 1f));
      lp.width = 0;
      lp.setGravity(Gravity.FILL_HORIZONTAL);
      grid.addView(tile, lp);
    }
  }

  private static final class GroupBuilder {
    final MainActivity a;
    final LinearLayout groupCard;
    final String title;
    int itemCount = 0;

    GroupBuilder(MainActivity a, LinearLayout page, String title) {
      this.a = a;
      this.title = title;
      groupCard = Ui.col(a);
      groupCard.setTag("profile-group:" + title);
      groupCard.setBackground(Ui.shape(a, Design.PAPER, Design.BORDER, 20));
      groupCard.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 8));

      TextView heading = Ui.title(a, title, 17);
      heading.setPadding(Ui.dp(a, 16), Ui.dp(a, 6), Ui.dp(a, 16), Ui.dp(a, 6));
      groupCard.addView(heading);

      page.addView(groupCard, new LinearLayout.LayoutParams(-1, -2));
      Ui.gap(page, 14);
    }

    void addRow(int iconRes, boolean isFoodDoodle, int doodleVariant,
                String label, String kind, String id, String countText) {
      if (itemCount > 0) {
        View divider = new View(a);
        divider.setBackgroundColor(Design.BORDER); // 1dp 细浅色分割线
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(-1, Ui.dp(a, 1));
        divLp.leftMargin = Ui.dp(a, 48);
        divLp.rightMargin = Ui.dp(a, 16);
        groupCard.addView(divider, divLp);
      }
      itemCount++;

      LinearLayout row = Ui.row(a);
      row.setGravity(Gravity.CENTER_VERTICAL);
      row.setMinimumHeight(Ui.dp(a, 48));
      row.setPadding(Ui.dp(a, 16), Ui.dp(a, 9), Ui.dp(a, 16), Ui.dp(a, 9));
      row.setFocusable(true);
      row.setClickable(true);
      row.setBackground(Ui.ripple(a, Color.TRANSPARENT, 0, 0));

      View iconView;
      if (isFoodDoodle) {
        iconView = Ui.foodDoodle(a, doodleVariant, Design.RED);
      } else {
        ImageView iv = new ImageView(a);
        Drawable d = a.getDrawable(iconRes);
        if (d != null) {
          Drawable mutated = d.mutate();
          if (iconRes == R.drawable.ic_photo_like
              || iconRes == R.drawable.ic_thumb_up
              || iconRes == R.drawable.ic_community_home) {
            mutated.setTint(Design.RED);
          } else {
            mutated.setTint(Design.INK);
          }
          iv.setImageDrawable(mutated);
        }
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iconView = iv;
      }
      iconView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
      LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(Ui.dp(a, 20), Ui.dp(a, 20));
      iconLp.rightMargin = Ui.dp(a, 12);
      row.addView(iconView, iconLp);

      TextView titleView = Ui.text(a, label, 15, Design.INK);
      titleView.setGravity(Gravity.CENTER_VERTICAL);
      titleView.setTag("profile-entry:" + kind);
      titleView.setFocusable(true);
      titleView.setClickable(true);
      titleView.setOnClickListener(v -> a.go(kind, id));
      row.addView(titleView, new LinearLayout.LayoutParams(0, -2, 1));

      TextView countView = Ui.text(a, countText != null ? countText : "", 13, Design.MUTED);
      countView.setGravity(Gravity.CENTER_VERTICAL);
      countView.setTag("profile-count:" + kind);
      if (countText == null || countText.isEmpty() || "0".equals(countText)) {
        countView.setVisibility(View.GONE);
      }
      LinearLayout.LayoutParams countLp = new LinearLayout.LayoutParams(-2, -2);
      countLp.rightMargin = Ui.dp(a, 6);
      row.addView(countView, countLp);

      TextView chevron = Ui.text(a, "›", 18, Design.MUTED);
      chevron.setGravity(Gravity.CENTER_VERTICAL);
      chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
      row.addView(chevron);

      row.setOnClickListener(v -> a.go(kind, id));
      groupCard.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    TextView addCustomRow(int iconRes, boolean isFoodDoodle, int doodleVariant,
                          String label, Runnable onClick) {
      if (itemCount > 0) {
        View divider = new View(a);
        divider.setBackgroundColor(Design.BORDER);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(-1, Ui.dp(a, 1));
        divLp.leftMargin = Ui.dp(a, 48);
        divLp.rightMargin = Ui.dp(a, 16);
        groupCard.addView(divider, divLp);
      }
      itemCount++;

      LinearLayout row = Ui.row(a);
      row.setGravity(Gravity.CENTER_VERTICAL);
      row.setMinimumHeight(Ui.dp(a, 48));
      row.setPadding(Ui.dp(a, 16), Ui.dp(a, 9), Ui.dp(a, 16), Ui.dp(a, 9));
      row.setFocusable(true);
      row.setClickable(true);
      row.setBackground(Ui.ripple(a, Color.TRANSPARENT, 0, 0));

      View iconView;
      if (isFoodDoodle) {
        iconView = Ui.foodDoodle(a, doodleVariant, Design.RED);
      } else {
        ImageView iv = new ImageView(a);
        Drawable d = a.getDrawable(iconRes);
        if (d != null) {
          Drawable mutated = d.mutate();
          mutated.setTint(Design.INK);
          iv.setImageDrawable(mutated);
        }
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iconView = iv;
      }
      iconView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
      LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(Ui.dp(a, 20), Ui.dp(a, 20));
      iconLp.rightMargin = Ui.dp(a, 12);
      row.addView(iconView, iconLp);

      TextView titleView = Ui.text(a, label, 15, Design.INK);
      titleView.setGravity(Gravity.CENTER_VERTICAL);
      row.addView(titleView, new LinearLayout.LayoutParams(0, -2, 1));

      TextView rightText = Ui.text(a, "", 13, Design.MUTED);
      rightText.setGravity(Gravity.CENTER_VERTICAL);
      LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(-2, -2);
      rightLp.rightMargin = Ui.dp(a, 6);
      row.addView(rightText, rightLp);

      TextView chevron = Ui.text(a, "›", 18, Design.MUTED);
      chevron.setGravity(Gravity.CENTER_VERTICAL);
      chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
      row.addView(chevron);

      row.setOnClickListener(v -> onClick.run());
      groupCard.addView(row, new LinearLayout.LayoutParams(-1, -2));
      return rightText;
    }
  }

  private static void roundedAction(MainActivity a, LinearLayout panel, String label, boolean primary, Runnable run) {
    TextView button = Ui.button(a, label, primary, run);
    if (!primary) {
      button.setTextColor(Design.RED);
      button.setBackground(Ui.ripple(a, Design.PAPER, Design.BORDER, 14));
    }
    panel.addView(button, new LinearLayout.LayoutParams(-1, -2));
  }

  static void edit(MainActivity a, LinearLayout page, MainActivity.Route route) {
    User user = a.repo.currentUser();
    a.heading(page, "03.1", "编辑资料", "");
    LinearLayout card = Ui.col(a);
    card.setPadding(Ui.dp(a, 18), Ui.dp(a, 20), Ui.dp(a, 18), Ui.dp(a, 20));
    card.setBackground(Ui.shape(a, Design.ALT, 0, 24));
    String imageKey = a.draftKey("image");
    if (!a.drafts.containsKey(imageKey)) a.drafts.putString(imageKey, user.avatarUrl());
    String image = a.drafts.getString(imageKey, "");
    FrameLayout avatarFrame = new FrameLayout(a);
    avatarFrame.setBackground(Ui.shape(a, Design.PAPER, Design.RED, 999));
    avatarFrame.setClipToOutline(true);
    avatarFrame.addView(Ui.image(a, Ui.thumb(image), "头像预览", false), new FrameLayout.LayoutParams(-1, -1));
    LinearLayout avatarRow = Ui.row(a);
    avatarRow.setGravity(Gravity.CENTER);
    avatarRow.addView(avatarFrame, new LinearLayout.LayoutParams(Ui.dp(a, 112), Ui.dp(a, 112)));
    card.addView(avatarRow);
    Ui.gap(card, 12);
    TextView choose = Ui.button(a, "选择新头像", false, () -> a.pickAvatar(imageKey));
    choose.setBackground(Ui.ripple(a, Design.PAPER, Design.RED, 18));
    choose.setTextColor(Design.RED);
    card.addView(choose);
    Ui.gap(card, 8);
    card.addView(Ui.text(a, "JPEG / PNG，原图不超过10MB。选择后进行1:1方形裁剪。", 13, Design.MUTED));
    Ui.gap(card, 18);
    String nameKey = a.draftKey("昵称");
    if (!a.drafts.containsKey(nameKey)) a.drafts.putString(nameKey, user.displayName());
    EditText name = Ui.field(card, "昵称", false, false);
    name.setBackground(Ui.shape(a, Design.PAPER, Design.BORDER, 16));
    TextView error = Ui.text(a, "", 14, Design.RED);
    error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    card.addView(error);
    Ui.gap(card, 8);
    TextView save = Ui.button(a, "保存资料", true, () -> {});
    save.setBackground(Ui.ripple(a, Design.RED, 0, 18));
    save.setOnClickListener(v -> {
      String displayName = name.getText().toString();
      String selected = a.drafts.getString(imageKey, "");
      a.request(v, error, () -> a.repo.updateProfile(displayName, selected), () -> {
        Ui.clearImages();
        a.clearDraft(route);
        a.message("个人资料已更新");
        a.back();
      });
    });
    card.addView(save, new LinearLayout.LayoutParams(-1, -2));
    page.addView(card, new LinearLayout.LayoutParams(-1, -2));
    Ui.gap(page, 18);
    LinearLayout marks = Ui.row(a);
    marks.setGravity(Gravity.CENTER);
    marks.addView(Ui.foodDoodle(a, 2, 0x665b7a3a), new LinearLayout.LayoutParams(Ui.dp(a, 74), Ui.dp(a, 56)));
    marks.addView(Ui.foodDoodle(a, 6, 0x66c9341e), new LinearLayout.LayoutParams(Ui.dp(a, 74), Ui.dp(a, 56)));
    page.addView(marks);
  }

  static void reviews(MainActivity a, LinearLayout page, MainActivity.Route route) {
    a.heading(page, "02.1", "我的评论", "");
    Ui.action(page, "餐厅与档口点评及回复", false, () -> a.go("venue-mine", "~reviews~1"));
    Ui.gap(page, 16);
    List<Review> reviews = a.repo.myReviews();
    if (reviews.isEmpty()) {
      Ui.empty(page, "暂无已发布评论", "刚提交的点评需要先审核，可在“我的提交”查看进度和驳回原因。");
      Ui.action(page, "查看点评审核记录", true, () -> a.go("mine-submissions", ""));
    }
    for (Review r : reviews) {
      LinearLayout card = Ui.card(page);
      card.addView(Ui.text(a, SubmissionScreen.date(r.createdAt()), 12, Design.MUTED));
      Ui.gap(card, 12);
      card.addView(Ui.text(a, a.repo.context(r.dishId()), 14, Design.BLUE));
      Ui.gap(card, 8);
      card.addView(Ui.text(a, "★".repeat(r.rating()) + " / " + r.rating() + "分", 16, Design.RED));
      card.addView(Ui.text(a, r.content(), 16, Design.INK));
      Ui.gap(card, 12);
      LinearLayout actions = Ui.row(a);
      actions.addView(
          Ui.button(a, "查看菜品", false, () -> a.go("detail", r.dishId())),
          new LinearLayout.LayoutParams(0, -2, 1));
      TextView del =
          Ui.button(
              a,
              "删除",
              false,
              () ->
                  Ui.confirm(
                      a,
                      "删除这条评论？",
                      "删除后不可恢复",
                      () -> a.request(null, null, () -> a.repo.deleteReview(r.id()), () -> {
                        a.message("评论已删除");
                        a.render();
                      })));
      del.setTextColor(Design.RED);
      actions.addView(del, new LinearLayout.LayoutParams(0, -2, 1));
      card.addView(actions);
    }
  }

  private static final class ProfileDoodleDrawable extends Drawable {
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    final int paper;
    final float unit;
    ProfileDoodleDrawable(MainActivity a) {
      paper = Design.PAPER;
      unit = Ui.dp(a, 110);
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeWidth(Ui.dp(a, 1.2f));
      p.setStrokeCap(Paint.Cap.ROUND);
    }
    @Override public void draw(Canvas c) {
      c.drawColor(paper);
      Rect b = getBounds();
      p.setColor(0x16c9341e);
      float rx = b.right - unit * .35f, top = unit * .9f;
      c.drawCircle(rx, top, unit * .18f, p);
      c.drawLine(rx - unit * .22f, top + unit * .18f, rx + unit * .22f, top + unit * .18f, p);
      float lx = unit * .34f, mid = unit * 3.7f;
      Path leaf = new Path();
      leaf.moveTo(lx - unit * .24f, mid + unit * .12f);
      leaf.cubicTo(lx - unit * .12f, mid - unit * .26f, lx + unit * .22f, mid - unit * .3f, lx + unit * .28f, mid - unit * .12f);
      leaf.cubicTo(lx + unit * .14f, mid + unit * .2f, lx - unit * .08f, mid + unit * .25f, lx - unit * .24f, mid + unit * .12f);
      c.drawPath(leaf, p);
      float bx = b.right - unit * .38f, bottom = unit * 6.5f;
      c.drawArc(new RectF(bx - unit * .28f, bottom - unit * .14f, bx + unit * .28f, bottom + unit * .18f), 180, 180, false, p);
      c.drawLine(bx - unit * .28f, bottom + unit * .02f, bx + unit * .28f, bottom + unit * .02f, p);
    }
    @Override public void setAlpha(int alpha) {}
    @Override public void setColorFilter(android.graphics.ColorFilter filter) {}
    @Override public int getOpacity() { return PixelFormat.OPAQUE; }
  }
}
