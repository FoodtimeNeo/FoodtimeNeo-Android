package cn.edu.bjtu.foodtime.presentation;

import android.text.InputFilter;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.edu.bjtu.foodtime.domain.model.Models.CommunityAuthor;
import cn.edu.bjtu.foodtime.domain.model.Models.CommunityFeedSlice;
import cn.edu.bjtu.foodtime.domain.model.Models.CommunityPost;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

final class CommunityDiscoveryScreen {
  static void show(MainActivity activity, LinearLayout page, MainActivity.Route route) {
    new CommunityDiscoveryScreen(activity, page, route);
  }

  private final MainActivity activity;
  private final LinearLayout page, authorHeader, rows;
  private final String authorId, requestKey = "discover-posts:" + UUID.randomUUID();
  private final boolean following;
  private final EditText input;
  private final TextView search, status, error, more;
  private final List<CommunityPost> posts = new ArrayList<>();
  private String submitted = "", cursor = "";
  private boolean loading, loaded;
  private CommunityAuthor author;

  private CommunityDiscoveryScreen(MainActivity activity, LinearLayout page, MainActivity.Route route) {
    this.activity = activity;
    this.page = page;
    authorId = route.kind().equals("community-author") ? route.id() : "";
    following = route.kind().equals("community-search") && route.id().equals("following");
    if (authorId.isEmpty()) {
      activity.heading(page, "", following ? "搜索关注的食记" : "搜索食记", "按食记标题或正文里的关键词查找。");
      input = Ui.field(page, "食记关键词", false, false);
      input.setHint("菜名、档口或关键词");
      input.setFilters(new InputFilter[] {new InputFilter.LengthFilter(50)});
      input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
      input.setOnEditorActionListener((view, action, event) -> {
        if (action != EditorInfo.IME_ACTION_SEARCH) return false;
        search();
        return true;
      });
    } else {
      input = null;
    }
    authorHeader = Ui.col(activity);
    page.addView(authorHeader);
    search = Ui.button(activity, input == null ? "刷新食记" : "搜索", false, this::search);
    page.addView(search);
    status = Ui.text(activity, "", 14, Design.MUTED);
    page.addView(status);
    error = Ui.text(activity, "", 14, Design.RED);
    Ui.observeError(error);
    page.addView(error);
    rows = Ui.col(activity);
    page.addView(rows);
    more = Ui.button(activity, "加载更多", false, () -> load(submitted, true));
    more.setVisibility(View.GONE);
    page.addView(more);
    if (input == null || input.length() > 0) page.post(this::search);
  }

  private void search() {
    if (loading) return;
    String keyword = input == null ? "" : input.getText().toString().strip();
    if (authorId.isEmpty() && !following && keyword.isEmpty()) {
      posts.clear();
      rows.removeAllViews();
      cursor = "";
      submitted = "";
      loaded = false;
      status.setText("输入关键词开始搜索。");
      error.setText("");
      more.setVisibility(View.GONE);
      return;
    }
    load(keyword, false);
  }

  private void load(String keyword, boolean append) {
    if (loading || !page.isAttachedToWindow()) return;
    if (!append && !keyword.equals(submitted)) {
      posts.clear();
      rows.removeAllViews();
      cursor = "";
      loaded = false;
      more.setVisibility(View.GONE);
    }
    loading = true;
    if (input != null) input.setEnabled(false);
    search.setEnabled(false);
    more.setEnabled(false);
    if (!loaded) status.setText("正在加载食记…");
    AtomicReference<CommunityAuthor> profile = new AtomicReference<>(author);
    AtomicReference<CommunityFeedSlice> response = new AtomicReference<>();
    String position = append ? cursor : "";
    activity.read(requestKey, null, error, () -> {
      if (!authorId.isEmpty() && profile.get() == null) profile.set(activity.repo.communityAuthor(authorId));
      response.set(activity.repo.discoverPosts(authorId, following, keyword, position));
    }, () -> {
      if (author == null && profile.get() != null) {
        author = profile.get();
        authorHeader.addView(CommunityDiscussion.avatar(activity, author.avatarUrl(), author.displayName(), 48));
        activity.heading(authorHeader, "", author.displayName(), "公开食记");
      }
      var result = response.get();
      if (!append) posts.clear();
      posts.addAll(result.items());
      loaded = true;
      submitted = keyword;
      cursor = result.nextCursor();
      rows.removeAllViews();
      if (!posts.isEmpty()) CommunityScreen.waterfall(activity, rows, posts);
      status.setText(posts.isEmpty() ? "没有符合条件的公开食记。" : "共 " + result.total() + " 篇食记");
      more.setVisibility(cursor.isEmpty() ? View.GONE : View.VISIBLE);
    }, () -> {
      loading = false;
      if (input != null) input.setEnabled(true);
      search.setEnabled(true);
      more.setEnabled(true);
      if (!loaded) status.setText("");
    });
  }
}
