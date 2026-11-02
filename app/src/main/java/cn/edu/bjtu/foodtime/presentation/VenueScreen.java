package cn.edu.bjtu.foodtime.presentation;

import android.app.AlertDialog;
import android.view.*;
import android.widget.*;
import cn.edu.bjtu.foodtime.domain.model.Models.LikeState;
import cn.edu.bjtu.foodtime.domain.model.VenueModels.*;
import java.util.*;

/**
 * Extends the existing native catalog and review widgets; private submissions stay in the original
 * discussion.
 */
final class VenueScreen {
  static String scoreLabel(java.math.BigDecimal average, long count) {
    return average == null ? "暂无评分" : String.format(Locale.ROOT, "★ %.1f · %d 条评价", average, count);
  }

  private static String[] args(String id) {
    String[] p = id.split("~", -1);
    if (p.length < 2 || !Set.of("hall", "stall").contains(p[0]))
      throw new IllegalArgumentException("评价对象不合法");
    return p;
  }

  private static boolean writer(MainActivity a) {
    return a.repo.currentUser() != null && !a.repo.currentUser().role().equals("visitor");
  }

  private static String checked(String text) {
    String v = text.strip();
    if (v.isBlank() || v.codePointCount(0, v.length()) > 500)
      throw new IllegalArgumentException("请输入1–500字的内容");
    return v;
  }

  static void summary(MainActivity a, LinearLayout page, String type, String id) {
    LinearLayout card = Ui.card(page);
    TextView error = Ui.text(a, "", 14, Design.RED);
    page.addView(error);
    Runnable paint =
        () -> {
          card.removeAllViews();
          Target t = a.repo.venueSummary(type, id);
          card.addView(Ui.title(a, "评分", 22));
          Ui.gap(card, 8);
          card.addView(
              Ui.text(
                  a,
                  t == null
                      ? "正在读取评分…"
                      : t.averageScore() == null
                          ? "暂无评分"
                          : String.format(Locale.ROOT, "%.1f / 5", t.averageScore()),
                  22,
                  t != null && t.averageScore() != null ? Design.RED : Design.MUTED));
          if (t != null)
            card.addView(
                Ui.text(
                    a,
                    t.reviewCount() + " 条评价 · " + (type.equals("hall") ? "餐厅" : "档口") + "整体体验",
                    14,
                    Design.MUTED));
          Ui.gap(card, 12);
          Ui.action(card, "查看评论", false, () -> a.go("venue-reviews", type + "~" + id + "~all"));
        };
    paint.run();
    TextView retry = Ui.button(a, "刷新评分", false, () -> {});
    retry.setOnClickListener(
        v ->
            a.read(
                "venue-summary:" + type + id,
                v,
                error,
                () -> a.repo.loadVenueSummary(type, id),
                paint,
                () -> {}));
    card.addView(retry);
    a.read(
        "venue-summary:" + type + id,
        null,
        error,
        () -> a.repo.loadVenueSummary(type, id),
        paint,
        () -> {});
  }

  static void reviews(MainActivity a, LinearLayout page, MainActivity.Route route) {
    new Board(a, page, route);
  }

  private static final class Board {
    final MainActivity a;
    final MainActivity.Route route;
    final String type, id, rating, owner;
    final LinearLayout summary, privateList, list;
    final TextView error, more, total;
    final Map<String, RootCard> cards = new LinkedHashMap<>();
    boolean loading;
    long until = System.currentTimeMillis() + 30000;
    Runnable poll;

    Board(MainActivity a, LinearLayout page, MainActivity.Route route) {
      this.a = a;
      this.route = route;
      String[] p = args(route.id());
      type = p[0];
      id = p[1];
      rating = p.length > 2 ? p[2] : "all";
      owner = a.repo.currentUser().id();
      Snapshot snapshot = a.repo.venue(type, id);
      if (snapshot == null) throw new IllegalStateException("请刷新点评数据");
      a.heading(
          page, "", snapshot.target().name() + " · 点评", type.equals("hall") ? "餐厅整体体验" : "档口整体体验");
      summary = Ui.card(page);
      error = Ui.text(a, "", 14, Design.RED);
      page.addView(error);
      Ui.action(
          page,
          "刷新点评",
          false,
          () -> {
            until = System.currentTimeMillis() + 30000;
            load(false);
          });
      Ui.gap(page, 16);
      page.addView(Ui.title(a, "评论", 25));
      privateList = Ui.col(a);
      page.addView(privateList);
      Ui.gap(page, 12);
      String[] values = {"all", "5", "4", "1-3"};
      int selected = Arrays.asList(values).indexOf(rating);
      Spinner filter =
          SubmissionScreen.filter(
              a, page, "公开评分筛选", new String[] {"全部", "5分", "4分", "1–3分"}, Math.max(0, selected));
      filter.setOnItemSelectedListener(
          new AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(AdapterView<?> parent) {}

            public void onItemSelected(AdapterView<?> parent, View v, int position, long row) {
              if (!values[position].equals(rating))
                a.replaceRoute("venue-reviews", type + "~" + id + "~" + values[position]);
            }
          });
      list = Ui.col(a);
      page.addView(list);
      total = Ui.text(a, "", 14, Design.MUTED);
      page.addView(total);
      more = Ui.button(a, "加载更多", false, () -> load(true));
      page.addView(more);
      paint();
      poll =
          () -> {
            if (!active() || System.currentTimeMillis() > until) return;
            if (a.hasWindowFocus() && !a.operationInFlight()) {
              if (loading) {
                schedule();
                return;
              }
              loading = true;
              a.read(
                  "venue-list:" + type + id,
                  null,
                  error,
                  () -> a.repo.refreshVenuePending(type, id),
                  () -> {
                    paint();
                    schedule();
                  },
                  () -> loading = false);
            } else schedule();
          };
      schedule();
    }

    boolean active() {
      return !a.isDestroyed()
          && !a.isFinishing()
          && a.route().equals(route)
          && a.repo.currentUser() != null
          && owner.equals(a.repo.currentUser().id());
    }

    void schedule() {
      Snapshot s = a.repo.venue(type, id);
      if (poll != null
          && active()
          && System.currentTimeMillis() < until
          && s != null
          && s.privateSubmission() != null
          && s.privateSubmission().status().equals("pending")) {
        privateList.removeCallbacks(poll);
        privateList.postDelayed(poll, 2000);
      }
    }

    void load(boolean append) {
      if (loading || !active()) return;
      loading = true;
      a.read(
          "venue-list:" + type + id,
          append ? more : null,
          error,
          () -> a.repo.loadVenue(type, id, rating, append),
          () -> {
            paint();
            schedule();
          },
          () -> loading = false);
    }

    void paint() {
      Snapshot s = a.repo.venue(type, id);
      if (s == null) return;
      summary.removeAllViews();
      Target t = s.target();
      summary.addView(
          Ui.title(
              a,
              t.averageScore() == null
                  ? "暂无评分"
                  : String.format(Locale.ROOT, "%.1f / 5", t.averageScore()),
              24));
      summary.addView(Ui.text(a, t.reviewCount() + " 条已公开评价", 14, Design.MUTED));
      Ui.gap(summary, 12);
      if (writer(a)
          && !(s.privateSubmission() != null && s.privateSubmission().status().equals("pending")))
        Ui.action(
            summary,
            s.publicVersion() == null ? "写点评" : "修改我的评价",
            true,
            () -> a.go("venue-write", type + "~" + id));
      else if (!writer(a)) summary.addView(Ui.text(a, "绑定学校邮箱后可参与评价", 14, Design.MUTED));
      Ui.action(summary, "我的餐厅与档口点评", false, () -> a.go("venue-mine", type + "~reviews~1"));
      privateList.removeAllViews();
      if (s.privateSubmission() != null) {
        Row r = s.privateSubmission();
        LinearLayout card = card(a, privateList, r, true);
        if (s.publicVersion() != null)
          card.addView(Ui.text(a, "这是本次修改，原评价继续公开。", 14, Design.MUTED));
        card.addView(Ui.text(a, "不计入公开人数、均分及筛选结果", 14, Design.MUTED));
        if (r.status().equals("rejected"))
          Ui.action(card, "修改后重提", false, () -> a.go("venue-write", type + "~" + id));
      }
      Set<String> keep = new HashSet<>();
      int position = 0;
      for (Row r : s.published().items()) {
        keep.add(r.id());
        RootCard card = cards.get(r.id());
        if (card == null) {
          card = new RootCard(this, r);
          cards.put(r.id(), card);
        }
        card.update(r);
        if (card.view.getParent() == null) list.addView(card.view, position);
        else if (list.indexOfChild(card.view) != position) {
          list.removeView(card.view);
          list.addView(card.view, position);
        }
        position++;
      }
      for (String key : new ArrayList<>(cards.keySet()))
        if (!keep.contains(key)) {
          list.removeView(cards.get(key).view);
          cards.remove(key);
        }
      total.setText(
          s.published().total() == 0
              ? "暂无符合条件的已公开评价"
              : "共 " + s.published().total() + " 条符合条件的公开评价");
      more.setVisibility(s.published().hasMore() ? View.VISIBLE : View.GONE);
    }
  }

  private static final class RootCard {
    final LinearLayout view;
    final TextView author, score, body, time;
    final LikeButton like;
    final Board board;
    Row row;

    RootCard(Board board, Row row) {
      this.board = board;
      this.row = row;
      MainActivity a = board.a;
      view = Ui.col(a);
      view.setPadding(Ui.dp(a, 16), Ui.dp(a, 16), Ui.dp(a, 16), Ui.dp(a, 16));
      view.setBackground(Ui.shape(a, Design.PAPER, Design.BORDER, 20));
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
      lp.bottomMargin = Ui.dp(a, 14);
      view.setLayoutParams(lp);
      LinearLayout head = Ui.row(a);
      author = Ui.text(a, "", 16, Design.INK);
      score = Ui.text(a, "", 16, Design.RED);
      head.addView(author, new LinearLayout.LayoutParams(0, -2, 1));
      head.addView(score);
      view.addView(head);
      body = Ui.text(a, "", 16, Design.INK);
      Ui.gap(view, 12);
      view.addView(body);
      time = Ui.text(a, "", 12, Design.MUTED);
      Ui.gap(view, 12);
      view.addView(time);
      LinearLayout actions = Ui.row(a);
      view.addView(actions);
      like = new LikeButton(a, actions, row, false);
      new Replies(a, view, row);
      if (row.ownedByMe())
        Ui.action(
            view,
            "删除我的评价",
            false,
            () ->
                Ui.confirm(
                    a,
                    "删除这条评价？",
                    "整楼隐藏，审核历史保留，评分同步扣除。",
                    () ->
                        a.request(
                            null,
                            board.error,
                            () -> a.repo.deleteVenue(row.id(), false, false, null),
                            () -> board.load(false))));
      if (a.repo.currentUser().isAdmin())
        Ui.action(
            view, "下架违规评价", false, () -> removeAdmin(a, row.id(), false, () -> board.load(false)));
    }

    void update(Row next) {
      row = next;
      author.setText(next.userDisplayName() + (next.ownedByMe() ? " · 我" : ""));
      score.setText(next.rating() + " / 5");
      body.setText(next.content());
      time.setText(SubmissionScreen.date(next.createdAt()));
      like.update(next.likes());
    }
  }

  private static LinearLayout card(MainActivity a, LinearLayout list, Row row, boolean privateRow) {
    LinearLayout card = Ui.card(list);
    LinearLayout head = Ui.row(a);
    TextView author =
        Ui.text(a, row.userDisplayName() + (row.ownedByMe() ? " · 我" : ""), 16, Design.INK);
    head.addView(author, new LinearLayout.LayoutParams(0, -2, 1));
    if (row.rating() > 0) head.addView(Ui.text(a, row.rating() + " / 5", 16, Design.RED));
    card.addView(head);
    Ui.gap(card, 12);
    if (!row.replyToId().isBlank())
      card.addView(
          Ui.text(
              a,
              "回复 " + (row.replyToDeleted() ? "已删除的回复" : row.replyToUserDisplayName()),
              14,
              Design.MUTED));
    card.addView(Ui.text(a, row.content(), 16, Design.INK));
    Ui.gap(card, 8);
    if (privateRow)
      card.addView(
          Ui.text(
              a,
              (row.status().equals("pending")
                      ? "审核中"
                      : row.status().equals("invalidated") ? "已失效" : "未通过")
                  + " · 仅自己可见",
              14,
              Design.BLUE));
    if (!row.reviewReason().isBlank())
      card.addView(Ui.text(a, "审核说明：" + row.reviewReason(), 14, Design.RED));
    card.addView(Ui.text(a, SubmissionScreen.date(row.createdAt()), 12, Design.MUTED));
    return card;
  }

  private static final class LikeButton {
    final TextView button, error;
    final MainActivity a;
    final String id;
    final boolean reply;
    LikeState state;

    LikeButton(MainActivity a, LinearLayout parent, Row row, boolean reply) {
      this.a = a;
      this.id = row.id();
      this.reply = reply;
      state = row.likes();
      button = Ui.button(a, "", false, () -> change());
      button.setBackground(Ui.ripple(a, Design.PAPER, 0, 0));
      parent.addView(button);
      error = Ui.text(a, "", 12, Design.RED);
      parent.addView(error);
      paint();
    }

    void paint() {
      button.setText((state.likedByMe() ? "已赞 " : "赞 ") + state.likeCount());
      button.setContentDescription(
          (state.likedByMe() ? "取消点赞，" : "点赞，") + state.likeCount() + "个赞");
      button.setTextColor(state.likedByMe() ? Design.RED : Design.MUTED);
    }

    void update(LikeState next) {
      if (next.counterVersion() >= state.counterVersion()) {
        state = next;
        paint();
      }
    }

    void change() {
      if (!button.isEnabled() || !writer(a)) return;
      LikeState[] result = {null};
      a.request(
          button,
          error,
          () -> result[0] = a.repo.likeVenue(id, !state.likedByMe(), reply),
          () -> update(result[0]));
    }
  }

  private static final class Replies {
    final MainActivity a;
    final String id, owner, key;
    final MainActivity.Route route;
    final TextView toggle, error;
    final LinearLayout panel, list, privateList, editor;
    EditText text;
    Row target;
    boolean loaded, loading, expanded;
    long until = System.currentTimeMillis() + 30000;
    Runnable poll;

    Replies(MainActivity a, LinearLayout parent, Row root) {
      this.a = a;
      id = root.id();
      owner = a.repo.currentUser().id();
      route = a.route();
      key = a.draftKey("venue-reply:" + id + ":");
      toggle = Ui.button(a, "回复 · " + root.replyCount(), false, () -> toggle());
      toggle.setBackground(Ui.ripple(a, Design.PAPER, 0, 0));
      parent.addView(toggle);
      panel = Ui.col(a);
      panel.setPadding(Ui.dp(a, 12), Ui.dp(a, 12), Ui.dp(a, 8), Ui.dp(a, 12));
      panel.setBackground(Ui.shape(a, Design.ALT, Design.BORDER, 16));
      parent.addView(panel);
      error = Ui.text(a, "", 14, Design.RED);
      panel.addView(error);
      Ui.action(
          panel,
          "刷新回复",
          false,
          () -> {
            until = System.currentTimeMillis() + 30000;
            load(false, false);
          });
      list = Ui.col(a);
      privateList = Ui.col(a);
      panel.addView(list);
      panel.addView(privateList);
      editor = Ui.col(a);
      panel.addView(editor);
      expanded = a.drafts.getBoolean(key + "expanded", false);
      panel.setVisibility(expanded ? View.VISIBLE : View.GONE);
      paintEditor();
      poll =
          () -> {
            if (active() && expanded && System.currentTimeMillis() < until) {
              if (a.hasWindowFocus() && !a.operationInFlight()) {
                if (loading) {
                  schedule();
                  return;
                }
                loading = true;
                a.read(
                    "venue-replies:" + id,
                    null,
                    error,
                    () -> a.repo.refreshVenueRepliesPending(id),
                    () -> {
                      paint();
                      schedule();
                    },
                    () -> loading = false);
              } else schedule();
            }
          };
      if (expanded) load(false, false);
    }

    boolean active() {
      return !a.isDestroyed()
          && !a.isFinishing()
          && a.route().equals(route)
          && a.repo.currentUser() != null
          && owner.equals(a.repo.currentUser().id());
    }

    void toggle() {
      expanded = !expanded;
      a.drafts.putBoolean(key + "expanded", expanded);
      panel.setVisibility(expanded ? View.VISIBLE : View.GONE);
      if (expanded && !loaded) load(false, false);
      if (!expanded) panel.removeCallbacks(poll);
      else schedule();
    }

    void schedule() {
      if (expanded
          && active()
          && System.currentTimeMillis() < until
          && a.repo.venueReplies(id, true).items().stream()
              .anyMatch(r -> r.status().equals("pending"))) {
        panel.removeCallbacks(poll);
        panel.postDelayed(poll, 2000);
      }
    }

    void load(boolean more, boolean mineMore) {
      if (loading || !active()) return;
      loading = true;
      a.read(
          "venue-replies:" + id,
          null,
          error,
          () -> a.repo.loadVenueReplies(id, more, mineMore),
          () -> {
            loaded = true;
            paint();
            schedule();
          },
          () -> loading = false);
    }

    void paint() {
      list.removeAllViews();
      privateList.removeAllViews();
      Page pub = a.repo.venueReplies(id, false), own = a.repo.venueReplies(id, true);
      toggle.setText((expanded ? "收起回复" : "回复") + " · " + pub.total());
      for (Row row : pub.items()) {
        LinearLayout c = card(a, list, row, false);
        new LikeButton(a, c, row, true);
        if (writer(a))
          Ui.link(
              c,
              "回复这条",
              () -> {
                target = row;
                a.drafts.putString(key + "target", row.id());
                a.drafts.putString(key + "target-name", row.userDisplayName());
                a.drafts.putBoolean(key + "editing", true);
                paintEditor();
              });
        if (row.ownedByMe()) Ui.link(c, "删除回复", () -> delete(row));
        if (a.repo.currentUser().isAdmin())
          Ui.link(c, "下架回复", () -> removeAdmin(a, row.id(), true, () -> load(false, false)));
      }
      if (pub.hasMore()) Ui.link(list, "更多公开回复", () -> load(true, false));
      for (Row row : own.items()) {
        LinearLayout c = card(a, privateList, row, true);
        Ui.link(c, "删除我的回复", () -> delete(row));
      }
      if (own.hasMore()) Ui.link(privateList, "更多我的待审回复", () -> load(false, true));
      if (pub.items().isEmpty() && own.items().isEmpty()) Ui.empty(list, "暂无回复", "");
    }

    void delete(Row row) {
      Ui.confirm(
          a,
          "删除这条回复？",
          "删除后不再展示。",
          () ->
              a.request(
                  null,
                  error,
                  () -> a.repo.deleteVenue(row.id(), true, false, null),
                  () -> load(false, false)));
    }

    void paintEditor() {
      editor.removeAllViews();
      if (!writer(a)) return;
      if (!a.drafts.getBoolean(key + "editing", false)) {
        Ui.action(
            editor,
            "写下你的回复…",
            false,
            () -> {
              a.drafts.putBoolean(key + "editing", true);
              paintEditor();
            });
        return;
      }
      String targetId = a.drafts.getString(key + "target", "");
      if (!targetId.isBlank()) {
        editor.addView(
            Ui.text(a, "回复 " + a.drafts.getString(key + "target-name", "同学"), 14, Design.MUTED));
        Ui.link(
            editor,
            "取消回复对象",
            () -> {
              a.drafts.putString(key + "target", "");
              paintEditor();
            });
      }
      text = Ui.field(editor, "回复内容", false, true);
      text.setText(a.drafts.getString(key + "content", ""));
      Ui.observe(text, () -> a.drafts.putString(key + "content", text.getText().toString()));
      Ui.submit(
          editor,
          "提交回复",
          error,
          () -> {
            String content = checked(text.getText().toString()),
                to = a.drafts.getString(key + "target", "");
            String sig = content + "\u0000" + to;
            if (!sig.equals(a.drafts.getString(key + "signature", ""))) {
              a.drafts.putString(key + "operation", UUID.randomUUID().toString());
              a.drafts.putString(key + "signature", sig);
            }
            String operation = a.drafts.getString(key + "operation", "");
            return () -> a.repo.submitVenueReply(id, content, to, operation);
          },
          () -> {
            a.drafts.putString(key + "content", "");
            a.drafts.putString(key + "signature", "");
            a.drafts.putBoolean(key + "editing", false);
            until = System.currentTimeMillis() + 30000;
            paintEditor();
            paint();
            load(false, false);
            a.message("已提交审核，通过前仅自己可见");
          });
      Ui.link(
          editor,
          "收起编辑（保留草稿）",
          () -> {
            a.drafts.putBoolean(key + "editing", false);
            paintEditor();
          });
    }
  }

  static void write(MainActivity a, LinearLayout page, MainActivity.Route route) {
    String[] p = args(route.id());
    String type = p[0], id = p[1];
    Snapshot snapshot = a.repo.venue(type, id);
    if (snapshot == null) throw new IllegalStateException("请先加载评价对象");
    a.heading(page, "", "评价", snapshot.target().name());
    if (!writer(a)) {
      Ui.empty(page, "绑定校园邮箱后可参与评价", "");
      return;
    }
    if (snapshot.privateSubmission() != null
        && snapshot.privateSubmission().status().equals("pending")) {
      Ui.empty(page, "已有待审核评价", "通过前仅自己可见");
      Ui.action(
          page, "查看评论区", false, () -> a.replaceRoute("venue-reviews", type + "~" + id + "~all"));
      return;
    }
    LinearLayout card = Ui.card(page);
    card.addView(Ui.title(a, "给" + (type.equals("hall") ? "餐厅" : "档口") + "打分", 24));
    String key = a.draftKey("venue-rating");
    Row source =
        snapshot.privateSubmission() != null
            ? snapshot.privateSubmission()
            : snapshot.publicVersion();
    if (source != null && !a.drafts.getBoolean(a.draftKey("venue-loaded"), false)) {
      a.drafts.putInt(key, source.rating());
      a.drafts.putString(a.draftKey("评价内容"), source.content());
      a.drafts.putBoolean(a.draftKey("venue-loaded"), true);
    }
    if (snapshot.publicVersion() != null)
      card.addView(Ui.text(a, "修改需重新审核，通过前原评价继续公开。", 14, Design.MUTED));
    int[] rating = {a.drafts.getInt(key, 0)};
    TextView[] stars = new TextView[5];
    LinearLayout options = Ui.row(a);
    Ui.gap(card, 16);
    card.addView(options);
    Runnable paint =
        () -> {
          for (int i = 0; i < 5; i++) {
            stars[i].setText(i < rating[0] ? "★" : "☆");
            stars[i].setTextColor(i < rating[0] ? Design.RED : Design.MUTED);
            stars[i].setContentDescription((i + 1) + "星" + (rating[0] == i + 1 ? "，已选中" : ""));
          }
        };
    for (int i = 0; i < 5; i++) {
      int n = i + 1;
      stars[i] =
          Ui.button(
              a,
              "☆",
              false,
              () -> {
                rating[0] = n;
                a.drafts.putInt(key, n);
                paint.run();
              });
      stars[i].setPadding(0, 0, 0, 0);
      stars[i].setTextSize(28);
      stars[i].setBackground(Ui.ripple(a, Design.PAPER, 0, 0));
      options.addView(stars[i], new LinearLayout.LayoutParams(0, Ui.dp(a, 56), 1));
    }
    paint.run();
    Ui.gap(card, 20);
    EditText content = Ui.field(card, "评价内容", false, true);
    content.setHint("分享真实体验，最多500字");
    TextView error = Ui.text(a, "", 14, Design.RED);
    card.addView(error);
    Ui.submit(
        card,
        "提交评价",
        error,
        () -> {
          if (rating[0] < 1 || rating[0] > 5) throw new IllegalArgumentException("请选择1–5星评分");
          String text = checked(content.getText().toString()),
              sig = rating[0] + "\u0000" + text,
              opKey = a.draftKey("venue-operation");
          if (!sig.equals(a.drafts.getString(a.draftKey("venue-signature"), ""))) {
            a.drafts.putString(opKey, UUID.randomUUID().toString());
            a.drafts.putString(a.draftKey("venue-signature"), sig);
          }
          String operation = a.drafts.getString(opKey, "");
          int score = rating[0];
          return () -> a.repo.submitVenue(type, id, score, text, operation);
        },
        () -> {
          a.clearDraft(route);
          a.message("已提交审核，通过前仅自己可见");
          a.finishVenueWrite(type, id);
        });
  }

  static void mine(MainActivity a, LinearLayout page, MainActivity.Route route) {
    String[] p = route.id().split("~", -1);
    String type = p.length > 0 ? p[0] : "";
    boolean replies = p.length > 1 && p[1].equals("replies");
    int number = p.length > 2 ? Integer.parseInt(p[2]) : 1;
    a.heading(page, "", "我的餐厅与档口点评", "");
    String[] types = {"", "hall", "stall"};
    Spinner filter =
        SubmissionScreen.filter(
            a, page, "评价对象", new String[] {"全部", "餐厅", "档口"}, Arrays.asList(types).indexOf(type));
    filter.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          public void onNothingSelected(AdapterView<?> parent) {}

          public void onItemSelected(AdapterView<?> parent, View v, int index, long row) {
            if (!types[index].equals(type))
              a.replaceRoute(
                  "venue-mine", types[index] + "~" + (replies ? "replies" : "reviews") + "~1");
          }
        });
    Ui.action(
        page,
        replies ? "查看我的一级点评" : "查看我的回复",
        false,
        () -> a.replaceRoute("venue-mine", type + "~" + (replies ? "reviews" : "replies") + "~1"));
    Page data = a.repo.myVenueReviews();
    if (data.items().isEmpty()) Ui.empty(page, "暂无记录", "");
    for (Row row : data.items()) {
      LinearLayout card = card(a, page, row, !row.status().equals("approved"));
      card.addView(Ui.title(a, row.name(), 20));
      Ui.link(
          card,
          "进入评论区",
          () -> a.go("venue-reviews", row.targetType() + "~" + row.targetId() + "~all"));
      if (!replies && !row.status().equals("pending"))
        Ui.link(card, "修改评价", () -> a.go("venue-write", row.targetType() + "~" + row.targetId()));
      if (replies || row.status().equals("approved"))
        Ui.link(
            card,
            "删除",
            () ->
                Ui.confirm(
                    a,
                    "删除这条记录？",
                    "审核历史保留。",
                    () ->
                        a.request(
                            null,
                            null,
                            () -> a.repo.deleteVenue(row.id(), replies, false, null),
                            a::render)));
    }
    page.addView(Ui.text(a, "第 " + number + " 页 · 共 " + data.total() + " 条", 14, Design.MUTED));
    if (number > 1)
      Ui.action(
          page,
          "上一页",
          false,
          () ->
              a.replaceRoute(
                  "venue-mine",
                  type + "~" + (replies ? "replies" : "reviews") + "~" + (number - 1)));
    if (data.hasMore())
      Ui.action(
          page,
          "下一页",
          false,
          () ->
              a.replaceRoute(
                  "venue-mine",
                  type + "~" + (replies ? "replies" : "reviews") + "~" + (number + 1)));
  }

  static void removeAdmin(MainActivity a, String id, boolean reply, Runnable success) {
    AdminScreen.guard(a);
    LinearLayout form = Ui.col(a);
    form.setPadding(Ui.dp(a, 20), Ui.dp(a, 16), Ui.dp(a, 20), Ui.dp(a, 8));
    EditText reason = Ui.field(form, "下架原因", false, true);
    TextView error = Ui.text(a, "", 14, Design.RED);
    form.addView(error);
    AlertDialog dialog =
        new AlertDialog.Builder(a)
            .setTitle("下架违规内容？")
            .setView(form)
            .setNegativeButton("取消", null)
            .setPositiveButton("确认下架", null)
            .create();
    dialog.setOnShowListener(
        v ->
            dialog
                .getButton(-1)
                .setOnClickListener(
                    w -> {
                      try {
                        String text = checked(reason.getText().toString());
                        a.request(
                            w,
                            error,
                            () -> a.repo.deleteVenue(id, reply, true, text),
                            () -> {
                              dialog.dismiss();
                              success.run();
                            });
                      } catch (RuntimeException e) {
                        error.setText(e.getMessage());
                      }
                    }));
    dialog.show();
  }
}
