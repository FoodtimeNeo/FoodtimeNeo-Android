package cn.edu.bjtu.foodtime.presentation;

import android.app.AlertDialog;

/** Editing changes memory only. The committed draft changes only after explicit consent. */
final class PostDraftExit {
  private AlertDialog dialog;
  private boolean leaving;

  boolean intercept(MainActivity activity, Runnable navigation) {
    if (leaving || !activity.route().kind().equals("community-create")) return false;
    String owner = activity.repo.currentUser() == null ? "" : activity.repo.currentUser().id();
    boolean content = !activity.drafts.getString(activity.draftKey("食记标题"), "").isBlank()
        || !activity.drafts.getString(activity.draftKey("动态正文"), "").isBlank()
        || !activity.drafts.getString(activity.draftKey("linkedDishId"), "").isBlank()
        || !CommunityScreen.selectedPhotos(activity).isEmpty();
    if (!content && PostDraftStore.load(activity, owner) == null) return false;
    if (dialog != null) return true;
    MainActivity.Route origin = activity.route();
    dialog = new AlertDialog.Builder(activity).setTitle("保存草稿？")
        .setMessage("不保存只放弃本次修改，原草稿仍保留。")
        .setPositiveButton("保存草稿", null)
        .setNegativeButton("不保存", (d, which) -> leave(activity, origin, navigation))
        .setNeutralButton("继续编辑", (d, which) -> {})
        .create();
    dialog.setOnDismissListener(d -> dialog = null);
    dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
      if (activity.repo.currentUser() == null || !owner.equals(activity.repo.currentUser().id())) {
        close();
        return;
      }
      boolean saved = PostDraftStore.save(activity, owner,
          activity.drafts.getString(activity.draftKey("食记标题"), ""),
          activity.drafts.getString(activity.draftKey("动态正文"), ""),
          activity.drafts.getString(activity.draftKey("linkedDishId"), ""),
          CommunityScreen.selectedPhotos(activity));
      if (!saved) {
        activity.message("草稿保存失败，请清理本机空间后重试；当前内容仍保留");
        return;
      }
      leave(activity, origin, navigation);
      activity.message("已保存草稿");
    }));
    dialog.show();
    return true;
  }

  private void leave(MainActivity activity, MainActivity.Route origin, Runnable navigation) {
    close();
    activity.clearDraft(origin);
    leaving = true;
    try { navigation.run(); } finally { leaving = false; }
  }

  void close() {
    if (dialog != null) { AlertDialog current = dialog; dialog = null; current.dismiss(); }
  }
}
