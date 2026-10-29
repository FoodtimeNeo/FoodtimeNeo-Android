package cn.edu.bjtu.foodtime.presentation;

import android.app.Dialog;
import android.view.Gravity;
import android.widget.*;
import cn.edu.bjtu.foodtime.domain.model.CommunityText;
import cn.edu.bjtu.foodtime.domain.model.Models.*;
import java.util.*;

/** Native announcement + system-message inbox, mirroring the CommunityScreen list skeleton. */
final class InboxScreen {
  static void inbox(MainActivity a, LinearLayout page, MainActivity.Route route) {
    a.heading(page, "04", "通知信箱", "公告与系统消息都在这里，点开即标为已读。");
    long unread = a.repo.unreadCount();
    if (unread > 0) {
      page.addView(Ui.text(a, "还有 " + unread + " 条未读", 14, Design.RED));
      Ui.gap(page, 12);
    }
    // Seed the background poll watermark so it only pops truly new announcements.
    List<String> known = new ArrayList<>();
    for (Notice notice : a.repo.notices().items()) known.add(notice.id());
    for (Message message : a.repo.messages().items()) known.add(message.id());
    a.noteInboxSeen(known);

    String tabKey = a.draftKey("tab");
    String tab = a.drafts.getString(tabKey, "notice");
    LinearLayout tabs = Ui.row(a);
    tabs.setPadding(Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 4));
    tabs.setBackground(Ui.shape(a, Design.KRAFT_SOFT, 0, 24));
    TextView noticeTab = segment(a, "公告", tab.equals("notice"), () -> {
      a.drafts.putString(tabKey, "notice");
      a.redraw();
    });
    TextView messageTab = segment(a, unread > 0 ? "系统消息 · " + unread + "未读" : "系统消息", tab.equals("message"), () -> {
      a.drafts.putString(tabKey, "message");
      a.redraw();
    });
    tabs.addView(noticeTab, new LinearLayout.LayoutParams(0, -2, 1));
    tabs.addView(messageTab, new LinearLayout.LayoutParams(0, -2, 1));
    page.addView(tabs, new LinearLayout.LayoutParams(-1, -2));
    Ui.gap(page, 12);
    if (tab.equals("message")) messages(a, page);
    else notices(a, page);
  }

  private static TextView segment(MainActivity a, String label, boolean active, Runnable run) {
    TextView tab = Ui.text(a, label, 14, active ? Design.ON_RED : Design.INK);
    tab.setGravity(Gravity.CENTER);
    tab.setMinHeight(Ui.dp(a, 42));
    tab.setClickable(true);
    tab.setFocusable(true);
    tab.setBackground(Ui.ripple(a, active ? Design.RED : 0x00000000, 0, 20));
    tab.setOnClickListener(v -> run.run());
    return tab;
  }

  private static void notices(MainActivity a, LinearLayout page) {
    NoticePage result = a.repo.notices();
    if (result.items().isEmpty()) {
      Ui.empty(page, "暂无公告", "有新的全员通知时，会第一时间送到这里。");
      return;
    }
    for (Notice notice : result.items()) {
      LinearLayout card = Ui.card(page);
      card.addView(Ui.text(a, notice.title(), 17, Design.INK));
      Ui.gap(card, 6);
      String body = notice.body() == null ? "" : notice.body();
      if (!body.isBlank()) {
        String snippet = body.length() > 80 ? body.substring(0, 80) + "…" : body;
        card.addView(Ui.text(a, snippet, 14, Design.MUTED));
        Ui.gap(card, 6);
      }
      card.addView(Ui.text(a, CommunityText.publishedAt(notice.publishedAt()), 12, Design.MUTED));
      card.setClickable(true);
      card.setFocusable(true);
      card.setContentDescription(notice.title() + "，查看全文");
      card.setOnClickListener(v -> openNotice(a, notice));
    }
    if (result.hasMore()) {
      TextView more = Ui.button(a, "加载更多公告", false, () -> {});
      page.addView(more);
      more.setOnClickListener(v -> a.read("inbox-notices", v, null, () -> a.repo.loadMoreNotices(), a::redraw, () -> {}));
    }
  }

  private static void messages(MainActivity a, LinearLayout page) {
    MessagePage result = a.repo.messages();
    LinearLayout toolbar = Ui.row(a);
    toolbar.setGravity(Gravity.CENTER_VERTICAL);
    toolbar.addView(Ui.text(a, "共 " + result.total() + " 条", 13, Design.MUTED),
        new LinearLayout.LayoutParams(0, -2, 1));
    TextView all = Ui.button(a, "全部标为已读", false, () -> {});
    all.setOnClickListener(v -> a.request(v, null, () -> a.repo.readAllMessages(), a::redraw));
    toolbar.addView(all, new LinearLayout.LayoutParams(-2, -2));
    page.addView(toolbar, new LinearLayout.LayoutParams(-1, -2));
    Ui.gap(page, 12);
    if (result.items().isEmpty()) {
      Ui.empty(page, "暂无系统消息", "审核结果、互动提醒等个人消息会出现在这里。");
      return;
    }
    for (Message message : result.items()) {
      LinearLayout card = Ui.card(page);
      LinearLayout headline = Ui.row(a);
      if (!message.isRead()) {
        TextView dot = Ui.text(a, "● ", 13, Design.RED);
        headline.addView(dot);
      }
      TextView title = Ui.text(a, message.title(), 16, message.isRead() ? Design.MUTED : Design.INK);
      title.setTypeface(Design.title());
      headline.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
      card.addView(headline);
      Ui.gap(card, 6);
      String body = message.body() == null ? "" : message.body();
      if (!body.isBlank()) {
        String snippet = body.length() > 80 ? body.substring(0, 80) + "…" : body;
        card.addView(Ui.text(a, snippet, 14, Design.MUTED));
        Ui.gap(card, 6);
      }
      String category = message.category() == null || message.category().isBlank() ? "系统消息" : message.category();
      card.addView(Ui.text(a, category + " · " + CommunityText.publishedAt(message.createdAt()), 12, Design.MUTED));
      card.setClickable(true);
      card.setFocusable(true);
      card.setContentDescription(message.title() + (message.isRead() ? "" : "，未读") + "，查看全文");
      card.setOnClickListener(v -> openMessage(a, message));
    }
    if (result.hasMore()) {
      TextView more = Ui.button(a, "加载更多消息", false, () -> {});
      page.addView(more);
      more.setOnClickListener(v -> a.read("inbox-messages", v, null, () -> a.repo.loadMoreMessages(), a::redraw, () -> {}));
    }
  }

  private static void openNotice(MainActivity a, Notice notice) {
    String body = notice.body() == null ? "" : notice.body();
    showDetail(a, notice.title(), body);
    a.request(null, null, () -> a.repo.markNoticeRead(notice.id()), a::redraw);
  }

  private static void openMessage(MainActivity a, Message message) {
    String body = message.body() == null ? "" : message.body();
    showDetail(a, message.title(), body);
    if (!message.isRead()) a.request(null, null, () -> a.repo.readMessage(message.id()), a::redraw);
  }

  /** Short bodies reuse the Ui.confirm AlertDialog pattern; long bodies get a full-screen reader. */
  private static void showDetail(MainActivity a, String title, String body) {
    String text = body == null ? "" : body;
    if (text.length() > 120) openFull(a, title, text);
    else Ui.confirm(a, title, text.isEmpty() ? "（正文为空）" : text, () -> {});
  }

  /** Full-screen text reader following the CommunityGallery.open() Dialog pattern. */
  private static void openFull(MainActivity a, String title, String body) {
    Dialog dialog = new Dialog(a, android.R.style.Theme_Material_NoActionBar_Fullscreen);
    LinearLayout screen = Ui.col(a);
    screen.setBackgroundColor(Design.PAPER);
    LinearLayout toolbar = Ui.row(a);
    toolbar.setPadding(Ui.dp(a, 12), Ui.dp(a, 12), Ui.dp(a, 12), Ui.dp(a, 12));
    TextView close = Ui.button(a, "关闭", false, dialog::dismiss);
    close.setContentDescription("关闭全文");
    toolbar.addView(close, new LinearLayout.LayoutParams(-2, -2));
    TextView hint = Ui.text(a, "全文", 13, Design.MUTED);
    hint.setGravity(Gravity.END);
    toolbar.addView(hint, new LinearLayout.LayoutParams(0, -2, 1));
    screen.addView(toolbar, new LinearLayout.LayoutParams(-1, -2));
    ScrollView scroll = new ScrollView(a);
    LinearLayout content = Ui.col(a);
    content.setPadding(Ui.dp(a, 20), Ui.dp(a, 8), Ui.dp(a, 20), Ui.dp(a, 32));
    content.addView(Ui.title(a, title, 24));
    Ui.gap(content, 12);
    content.addView(Ui.text(a, body, 16, Design.INK));
    scroll.addView(content);
    screen.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    dialog.setContentView(screen);
    dialog.show();
  }

  private InboxScreen() {}
}
