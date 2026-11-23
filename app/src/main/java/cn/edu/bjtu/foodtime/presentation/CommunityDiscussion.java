package cn.edu.bjtu.foodtime.presentation;

import android.app.AlertDialog;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.edu.bjtu.foodtime.domain.model.CommunityText;
import cn.edu.bjtu.foodtime.domain.model.Models.CommunityAction;
import cn.edu.bjtu.foodtime.domain.model.Models.CommunityComment;
import cn.edu.bjtu.foodtime.domain.model.Models.CommunityPost;
import cn.edu.bjtu.foodtime.domain.model.Models.FavoriteGroup;
import cn.edu.bjtu.foodtime.domain.model.Models.User;
import cn.edu.bjtu.foodtime.domain.validation.CommunityPermissions;
import java.util.UUID;

/** The comment region owns pagination and row updates inside the scrollable post. */
final class CommunityDiscussion {
  private final MainActivity activity;
  private final LinearLayout page, section, rows;
  private final CommunityPost post;
  private final TextView heading, count;
  private final CommunityCommentComposer composer;

  CommunityDiscussion(MainActivity activity, LinearLayout page, CommunityPost post) {
    this.activity = activity;
    this.page = page;
    this.post = post;
    composer = new CommunityCommentComposer(activity, page, post, this::paint, this::onSubmitted);
    section = Ui.col(activity);
    section.setPadding(dp(14), 0, dp(14), dp(18));
    section.setTag("community-discussion");
    rows = Ui.col(activity);
    heading = Ui.text(activity, "", 16, Design.INK);
    heading.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
    count = action(activity, "", this::scrollToComments);
  }

  TextView show() {
    section.addView(divider());
    Ui.gap(section, 13);
    section.addView(heading);
    Ui.gap(section, 8);
    LinearLayout entry = Ui.row(activity);
    entry.setGravity(Gravity.CENTER_VERTICAL);
    var viewer = activity.repo.currentUser();
    entry.addView(avatar(activity, viewer.avatarUrl(), viewer.displayName(), 32));
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
    params.leftMargin = dp(10);
    entry.addView(entryPill("有话要说，快来评论", "community-comment-entry"), params);
    section.addView(entry);
    Ui.gap(section, 6);
    section.addView(rows);
    page.addView(section);
    TextView postLike = buildDock();
    activity.onCommunityCommentsRefresh(this::paint);
    paint();
    focusFromNotice();
    return postLike;
  }

  private TextView buildDock() {
    LinearLayout dock = Ui.col(activity);
    dock.setTag("community-discussion-dock");
    dock.addView(divider());
    LinearLayout actions = Ui.row(activity);
    actions.setGravity(Gravity.CENTER_VERTICAL);
    actions.setPadding(dp(12), dp(4), dp(8), dp(4));
    var entry = entryPill("说点什么…", "community-comment-dock-entry");
    actions.addView(entry, new LinearLayout.LayoutParams(0, -2, 1));
    var like = toggle("community-post-like", post.likedByMe(), post.likeCount(), "♡", "♥", "点赞",
        Design.RED, next -> activity.repo.setCommunityLike("post", post.id(), next));
    var favorite = toggle("community-post-favorite", post.favoritedByMe(), post.favoriteCount(), "☆", "★", "收藏",
        Design.GOLD, next -> activity.repo.setCommunityFavorite(post.id(), next));
    actions.addView(like);
    actions.addView(favorite);
    TextView group = action(activity, "分组", this::chooseGroups);
    group.setTextColor(Design.MUTED);
    actions.addView(group);
    actions.addView(count);
    dock.addView(actions);
    activity.setDetailBottomBar(dock);
    return like;
  }

  private TextView entryPill(String label, String tag) {
    TextView entry = action(activity, label, () -> composer.open(null, null));
    entry.setTag(tag);
    entry.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
    entry.setPadding(dp(14), 0, dp(8), 0);
    entry.setTextColor(Design.MUTED);
    entry.setBackground(Ui.ripple(activity, Design.ALT, Color.TRANSPARENT, 24));
    return entry;
  }

  /** Immediately show the pending comment without rebuilding every existing floor. */
  private void onSubmitted(String rootId, String content) {
    if (rows.getParent() == null) return;
    User viewer = activity.repo.currentUser();
    if (viewer == null) return;
    CommunityPost current = activity.repo.communityPost(post.id());
    long total = (current == null ? post.commentCount() : current.commentCount()) + 1;
    heading.setText("共 " + total + " 条评论");
    count.setText("评论 " + total);
    count.setContentDescription("查看评论，共 " + total + " 条");
    // Replies keep the existing floor layout; the approval watcher refreshes the thread later.
    if (!rootId.isBlank()) return;
    CommunityComment pending = new CommunityComment("pending:" + UUID.randomUUID(), post.id(),
        viewer.id(), viewer.displayName(), content, 0, "pending", System.currentTimeMillis());
    LinearLayout thread = Ui.col(activity);
    thread.setPadding(0, dp(12), 0, dp(10));
    thread.addView(comment(pending, pending));
    rows.addView(thread, 0);
  }

  private void chooseGroups() {
    CommunityPost current=activity.repo.communityPost(post.id());
    if(!(current==null?post:current).favoritedByMe()){
      activity.message("请先点击 ☆ 收藏；未选择分组时会保存在默认分组。");return;
    }
    FavoriteGroupTools.choose(activity,"post",post.id(),()->activity.message("收藏分组已更新"));
  }

  private void paint() {
    if (rows.getParent() == null) return;
    CommunityPost updated = activity.repo.communityPost(post.id());
    long total = updated == null ? post.commentCount() : updated.commentCount();
    heading.setText("共 " + total + " 条评论");
    count.setText("评论 " + total);
    count.setContentDescription("查看评论，共 " + total + " 条");
    rows.removeAllViews();
    var roots = activity.repo.communityRoots(post.id());
    if (roots.items().isEmpty()) {
      TextView empty = Ui.text(activity, "还没有评论，来分享你的体验吧。", 14, Design.MUTED);
      empty.setPadding(0, dp(14), 0, dp(14));
      rows.addView(empty);
    }
    for (CommunityComment root : roots.items()) {
      LinearLayout thread = Ui.col(activity);
      thread.setPadding(0, dp(12), 0, dp(10));
      thread.addView(comment(root, root));
      var replies = activity.repo.communityReplies(root.id());
      if (!replies.items().isEmpty() || replies.hasMore()) {
        LinearLayout floor = Ui.col(activity);
        LinearLayout.LayoutParams indent = new LinearLayout.LayoutParams(-1, -2);
        indent.leftMargin = dp(42);
        thread.addView(floor, indent);
        for (CommunityComment reply : replies.items()) floor.addView(comment(reply, root));
        if (replies.hasMore()) {
          long remaining = root.replyCount() - replies.items().size();
          String label = remaining > 0 ? "展开 " + remaining + " 条回复" : "展开更多回复";
          TextView more = action(activity, label, () -> {});
          more.setTextColor(Design.MUTED);
          more.setOnClickListener(v -> activity.read("community-replies:" + root.id(), v, null,
              () -> activity.repo.loadCommunityReplies(post.id(), root.id(), true), this::paint, () -> {}));
          floor.addView(more, new LinearLayout.LayoutParams(-2, -2));
        }
      }
      rows.addView(thread);
      rows.addView(divider());
    }
    if (roots.hasMore()) {
      TextView more = action(activity, "加载更多评论", () -> {});
      more.setOnClickListener(v -> activity.read("community-roots:" + post.id(), v, null,
          () -> activity.repo.loadMoreCommunityComments(post.id()), this::paint, () -> {}));
      rows.addView(more);
    }
  }

  private View comment(CommunityComment item, CommunityComment root) {
    boolean reply = !item.rootCommentId().isBlank();
    String name = item.deleted() ? "原评论已删除" : item.userDisplayName();
    LinearLayout line = Ui.row(activity);
    line.setGravity(Gravity.TOP);
    line.setTag("community-comment:" + item.id());
    View avatar = avatar(activity, item.deleted() ? "" : item.userAvatarUrl(), name, reply ? 24 : 32);
    line.addView(avatar);
    LinearLayout body = Ui.col(activity);
    LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(0, -2, 1);
    bodyParams.leftMargin = dp(10);
    line.addView(body, bodyParams);
    boolean author = !item.deleted() && post.userId().equals(item.userId());

    boolean approved = !item.deleted() && item.status().equals("approved");
    boolean canRemove = !item.deleted() && CommunityPermissions.canRemove(activity.repo.currentUser(), item.userId());
    User viewer = activity.repo.currentUser();
    boolean canReport = approved && viewer != null && !viewer.id().equals(item.userId());

    // 昵称行：左侧作者/用户昵称，右侧小 "···" 操作菜单按钮
    LinearLayout nameRow = Ui.row(activity);
    nameRow.setGravity(Gravity.CENTER_VERTICAL);
    nameRow.addView(Ui.text(activity, name + (author ? "  ·  作者" : ""), 13, author ? Design.RED : Design.MUTED),
        new LinearLayout.LayoutParams(0, -2, 1));

    if (canRemove || canReport) {
      TextView moreBtn = action(activity, "···", () -> showCommentMenu(item));
      moreBtn.setTextSize(14);
      moreBtn.setTextColor(Design.MUTED);
      moreBtn.setPadding(dp(6), 0, dp(6), 0);
      moreBtn.setContentDescription("评论操作菜单");
      moreBtn.setTag("community-comment-more:" + item.id());
      nameRow.addView(moreBtn);
    }
    body.addView(nameRow, new LinearLayout.LayoutParams(-1, -2));
    Ui.gap(body, 3);

    String prefix = reply ? "回复 " + item.replyToDisplayName() + "：" : "";
    TextView content = Ui.text(activity, item.deleted() ? "这条评论已删除，楼内回复仍可查看。" : prefix + item.content(),
        15, item.deleted() ? Design.MUTED : Design.INK);
    content.setLineSpacing(dp(2), 1f);
    body.addView(content);

    // 长按评论行或正文也可以唤出操作菜单
    if (canRemove || canReport) {
      View.OnLongClickListener onLongClick = v -> {
        showCommentMenu(item);
        return true;
      };
      line.setOnLongClickListener(onLongClick);
      content.setOnLongClickListener(onLongClick);
    }

    if (!item.status().equals("approved") && !item.deleted()) {
      String status = item.status().equals("pending") ? "审核中，仅自己可见" : "未通过：" + item.reviewReason();
      body.addView(Ui.text(activity, status, 12, Design.MUTED));
    }

    // 底部操作栏：左侧时间，右下角仅保留 "回复" 与 点赞图标
    LinearLayout meta = Ui.row(activity);
    meta.setGravity(Gravity.CENTER_VERTICAL);
    body.addView(meta);
    meta.addView(Ui.text(activity, CommunityText.publishedAt(item.createdAt()), 11, Design.MUTED));
    meta.addView(new View(activity), new LinearLayout.LayoutParams(0, 1, 1));

    if (approved && !root.deleted()) {
      TextView respond = action(activity, "回复", () -> composer.open(root, item));
      respond.setTag("community-reply:" + item.id());
      respond.setContentDescription("回复 " + name);
      LinearLayout.LayoutParams replyLp = new LinearLayout.LayoutParams(-2, -2);
      replyLp.rightMargin = dp(12);
      meta.addView(respond, replyLp);
    }

    if (approved) {
      meta.addView(toggle("community-comment-like:" + item.id(), item.likedByMe(), item.likeCount(),
          "♡", "♥", "点赞评论", Design.RED, next -> activity.repo.setCommunityLike("comment", item.id(), next)));
    }
    return line;
  }

  private void showCommentMenu(CommunityComment item) {
    boolean approved = !item.deleted() && item.status().equals("approved");
    boolean canRemove = !item.deleted() && CommunityPermissions.canRemove(activity.repo.currentUser(), item.userId());
    User viewer = activity.repo.currentUser();
    boolean canReport = approved && viewer != null && !viewer.id().equals(item.userId());
    if (!canRemove && !canReport) return;

    android.app.Dialog dialog = new android.app.Dialog(activity, android.R.style.Theme_Material_Light_Dialog_NoActionBar);
    dialog.setCanceledOnTouchOutside(true);
    android.view.Window window = dialog.getWindow();
    if (window != null) {
      window.setGravity(Gravity.BOTTOM);
      window.setLayout(-1, -2);
      window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
      window.setWindowAnimations(android.R.style.Animation_InputMethod);
    }
    LinearLayout panel = Ui.col(activity);
    panel.setBackground(Ui.shape(activity, Design.PAPER, Design.BORDER, 24));
    panel.setPadding(dp(20), dp(12), dp(20), dp(20));

    View handle = new View(activity);
    handle.setBackground(Ui.shape(activity, 0x33000000, 0, 4));
    LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(dp(36), dp(4));
    handleLp.gravity = Gravity.CENTER_HORIZONTAL;
    handleLp.bottomMargin = dp(14);
    panel.addView(handle, handleLp);

    TextView title = Ui.title(activity, "评论操作", 17);
    title.setGravity(Gravity.CENTER);
    panel.addView(title);
    Ui.gap(panel, 14);

    if (canRemove) {
      TextView removeBtn = Ui.text(activity, "删除评论", 16, Design.RED);
      removeBtn.setGravity(Gravity.CENTER);
      removeBtn.setMinimumHeight(dp(48));
      removeBtn.setPadding(dp(16), dp(12), dp(16), dp(12));
      removeBtn.setBackground(Ui.ripple(activity, Design.PAPER, 0, 14));
      removeBtn.setFocusable(true);
      removeBtn.setClickable(true);
      removeBtn.setTag("community-comment-delete:" + item.id());
      removeBtn.setOnClickListener(v -> {
        dialog.dismiss();
        CommunityRemoval.confirm(activity, "comments", item.id(), item.userId(), item.content(), activity::render);
      });
      panel.addView(removeBtn, new LinearLayout.LayoutParams(-1, -2));
      Ui.gap(panel, 6);
    }

    if (canReport) {
      TextView reportBtn = Ui.text(activity, "举报评论", 16, Design.RED);
      reportBtn.setGravity(Gravity.CENTER);
      reportBtn.setMinimumHeight(dp(48));
      reportBtn.setPadding(dp(16), dp(12), dp(16), dp(12));
      reportBtn.setBackground(Ui.ripple(activity, Design.PAPER, 0, 14));
      reportBtn.setFocusable(true);
      reportBtn.setClickable(true);
      reportBtn.setTag("community-comment-report:" + item.id());
      reportBtn.setOnClickListener(v -> {
        dialog.dismiss();
        ContentSafety.report(activity, "comment", item.id());
      });
      panel.addView(reportBtn, new LinearLayout.LayoutParams(-1, -2));
      Ui.gap(panel, 6);
    }

    Ui.gap(panel, 8);
    TextView cancel = Ui.button(activity, "取消", false, dialog::dismiss);
    cancel.setBackground(Ui.ripple(activity, Design.ALT, 0, 16));
    panel.addView(cancel, new LinearLayout.LayoutParams(-1, -2));

    dialog.setContentView(panel);
    dialog.show();
    // The dialog theme measures to its content during show(); apply the
    // full-width bottom-sheet size after that measurement.
    android.view.Window shownWindow = dialog.getWindow();
    if (shownWindow != null) {
      shownWindow.setGravity(Gravity.BOTTOM);
      shownWindow.setLayout(-1, -2);
    }
  }

  private TextView toggle(String tag, boolean active, long total, String off, String on, String label,
      int activeColor, java.util.function.Function<Boolean, CommunityAction> write) {
    TextView button = action(activity, "", () -> {});
    button.setTag(tag);
    java.util.function.BiConsumer<TextView, CommunityAction> paint = (view, state) -> {
      view.setSelected(state.active());
      view.setText((state.active() ? on : off) + " " + state.count());
      view.setTextColor(state.active() ? activeColor : Design.INK);
      view.setContentDescription((state.active() ? "取消" : "") + label + "，当前 " + state.count());
    };
    paint.accept(button, new CommunityAction(active, total));
    button.setOnClickListener(v -> {
      boolean next = !button.isSelected();
      CommunityAction[] result = {null};
      activity.request(button, null, () -> result[0] = write.apply(next), () -> {
        // An approval refresh can replace the comment row while this write is in flight.
        TextView current = activity.root.findViewWithTag(tag);
        if (current != null) paint.accept(current, result[0]);
      });
    });
    return button;
  }

  private void focusFromNotice() {
    String focusKey = activity.draftKey("focusComment");
    String focus = activity.drafts.getString(focusKey, "");
    if (focus.isBlank()) return;
    View target = rows.findViewWithTag("community-comment:" + focus);
    String rootKey = activity.draftKey("focusRoot");
    String triedKey = activity.draftKey("focusTried");
    String root = activity.drafts.getString(rootKey, "");
    if (target == null && !root.isBlank() && !activity.drafts.getBoolean(triedKey)) {
      activity.drafts.putBoolean(triedKey, true);
      activity.read("community-focus:" + post.id(), null, null,
          () -> activity.repo.ensureCommunityCommentVisible(post.id(), root, focus),
          () -> { paint(); focusFromNotice(); }, () -> {});
      return;
    }
    for (String key : new String[]{focusKey, rootKey, triedKey}) activity.drafts.remove(key);
    if (target == null) return;
    target.post(() -> {
      scrollTo(target, 12);
      target.setBackgroundColor(Design.ALT);
      target.postDelayed(() -> target.setBackground(null), 2600);
    });
  }

  void scrollToComments() { scrollTo(section, 0); }

  private void scrollTo(View target, int offset) {
    int[] position = new int[2], viewport = new int[2];
    target.getLocationOnScreen(position);
    activity.scroll.getLocationOnScreen(viewport);
    activity.scroll.smoothScrollTo(0, Math.max(0, activity.scroll.getScrollY() + position[1] - viewport[1] - dp(offset)));
  }

  private View divider() {
    View divider = new View(activity);
    divider.setBackgroundColor(Design.BORDER);
    divider.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(1)));
    return divider;
  }

  static TextView action(MainActivity activity, String label, Runnable run) {
    TextView button = Ui.button(activity, label, false, run);
    button.setTextSize(13);
    button.setMinWidth(Ui.dp(activity, 48));
    button.setPadding(Ui.dp(activity, 8), 0, Ui.dp(activity, 8), 0);
    button.setBackground(Ui.ripple(activity, Color.TRANSPARENT, Color.TRANSPARENT, 16));
    return button;
  }

  static View avatar(MainActivity activity, String url, String name, int size) {
    String initial = name.isBlank() ? "同" : new String(Character.toChars(name.codePointAt(0)));
    View view;
    if (url.isBlank()) {
      TextView fallback = Ui.text(activity, initial, size <= 24 ? 11 : 13, Design.MUTED);
      fallback.setGravity(Gravity.CENTER);
      view = fallback;
    } else {
      view = Ui.image(activity, Ui.thumb(url), initial, false);
    }
    view.setBackground(Ui.shape(activity, Design.ALT, Color.TRANSPARENT, size / 2));
    view.setClipToOutline(true);
    view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    view.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(activity, size), Ui.dp(activity, size)));
    return view;
  }

  private int dp(int size) { return Ui.dp(activity, size); }
}
