package cn.edu.bjtu.foodtime.presentation;

import android.app.Dialog;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.*;
import cn.edu.bjtu.foodtime.domain.model.CommunityText;
import cn.edu.bjtu.foodtime.domain.repository.ContentRequest;
import cn.edu.bjtu.foodtime.domain.model.Models.*;
import java.util.*;

/** Native announcement + system-message inbox, mirroring the CommunityScreen list skeleton. */
final class InboxScreen {
  static void inbox(MainActivity a, LinearLayout page, MainActivity.Route route) {
    a.heading(page, "04", "通知信箱", "公告与系统消息都在这里，点开即标为已读。");
    LinearLayout unreadArea = Ui.col(a);
    page.addView(unreadArea,new LinearLayout.LayoutParams(-1,-2));
    if(a.loginNoticeFailed()) Ui.action(page,"重试登录公告",false,a::retryLoginNotice);

    String tabKey = a.draftKey("tab");
    String tab = a.drafts.getString(tabKey, "notice");
    TextView[] segments = new TextView[3];
    java.util.function.Consumer<String> select = next -> {
      if(next.equals(a.drafts.getString(tabKey,"notice")))return;
      a.drafts.putString(tabKey,next);
      for(int index=0;index<segments.length;index++)
        styleSegment(a,segments[index],List.of("notice","message","chat").get(index).equals(next));
      a.refreshContent();
      ContentRequest.Query query=switch(next) {
        case "message" -> ContentRequest.Query.MESSAGES;
        case "chat" -> ContentRequest.Query.CONVERSATIONS;
        default -> ContentRequest.Query.NOTICES;
      };
      a.read("inbox-tab:"+next,null,null,()->a.repo.refresh(new ContentRequest(query,"")),
          a::refreshContent,()->{});
    };
    LinearLayout tabs = Ui.row(a);
    tabs.setPadding(Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 4));
    tabs.setBackground(Ui.shape(a, Design.KRAFT_SOFT, 0, 24));
    TextView noticeTab = segment(a, "公告", tab.equals("notice"), () -> select.accept("notice"));
    TextView messageTab = segment(a, "系统消息", tab.equals("message"), () -> select.accept("message"));
    long directUnread = a.repo.directUnreadCount();
    TextView chatTab = segment(a, directUnread > 0 ? "私信 " + directUnread : "私信", tab.equals("chat"), () -> select.accept("chat"));
    segments[0]=noticeTab;segments[1]=messageTab;segments[2]=chatTab;
    tabs.addView(noticeTab, new LinearLayout.LayoutParams(0, -2, 1));
    tabs.addView(messageTab, new LinearLayout.LayoutParams(0, -2, 1));
    tabs.addView(chatTab, new LinearLayout.LayoutParams(0, -2, 1));
    page.addView(tabs, new LinearLayout.LayoutParams(-1, -2));
    Ui.gap(page, 12);
    LinearLayout body=Ui.col(a);
    page.addView(body,new LinearLayout.LayoutParams(-1,-2));
    Runnable refresh=()->{
      unreadArea.removeAllViews();
      long unread=a.repo.unreadCount();
      if(unread>0){unreadArea.addView(Ui.text(a,"还有 "+unread+" 条未读",14,Design.RED));Ui.gap(unreadArea,12);}
      long count=a.repo.directUnreadCount();chatTab.setText(count>0?"私信 "+count:"私信");
      body.removeAllViews();
      String selected=a.drafts.getString(tabKey,"notice");
      if(selected.equals("message"))messages(a,body);
      else if(selected.equals("chat"))chats(a,body);
      else notices(a,body);
    };
    a.onContentRefresh(refresh);
    refresh.run();
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

  private static void styleSegment(MainActivity a,TextView tab,boolean active) {
    tab.setTextColor(active?Design.ON_RED:Design.INK);
    tab.setBackground(Ui.ripple(a,active?Design.RED:0x00000000,0,20));
  }

  private static void notices(MainActivity a, LinearLayout page) {
    NoticePage result = a.repo.notices();
    if (result.items().isEmpty()) {
      Ui.empty(page, "暂无公告", "有新的全员通知时，会第一时间送到这里。");
      return;
    }
    for (Notice notice : result.items()) {
      LinearLayout card = Ui.card(page);
      card.addView(Ui.text(a, "系统公告", 17, Design.INK));
      Ui.gap(card, 6);
      String body = notice.content() == null ? "" : notice.content();
      if (!body.isBlank()) {
        // 列表内直接渲染 Markdown，截断交给 maxLines，避免把标记符号当正文展示。
        TextView preview = Ui.text(a, body, 14, Design.MUTED);
        NoticeMarkdown.render(preview, body);
        preview.setMaxLines(6);
        preview.setEllipsize(android.text.TextUtils.TruncateAt.END);
        card.addView(preview);
        Ui.gap(card, 6);
      }
      card.addView(Ui.text(a, CommunityText.publishedAt(notice.publishedAt()), 12, Design.MUTED));
      card.setClickable(true);
      card.setFocusable(true);
      card.setContentDescription("系统公告" + "，查看全文");
      card.setOnClickListener(v -> openNotice(a, notice));
    }
    if (result.hasMore()) {
      TextView more = Ui.button(a, "加载更多公告", false, () -> {});
      page.addView(more);
      more.setOnClickListener(v -> a.read("inbox-notices", v, null, () -> a.repo.loadMoreNotices(), a::refreshContent, () -> {}));
    }
  }

  private static void chats(MainActivity a, LinearLayout page) {
    ConversationPage result = a.repo.conversations();
    if (result.items().isEmpty()) {
      Ui.empty(page, "还没有私信", "在社区食记或关注列表找到同学，点击「私信」开始聊天。");
      return;
    }
    for (Conversation conversation : result.items()) {
      LinearLayout card = Ui.card(page);
      card.setPadding(Ui.dp(a, 14), Ui.dp(a, 14), Ui.dp(a, 14), Ui.dp(a, 14));
      card.setBackground(Ui.ripple(a, conversation.unreadCount() > 0 ? Design.ALT : Design.PAPER, Design.BORDER, 18));
      LinearLayout row = Ui.row(a);
      row.setGravity(Gravity.CENTER_VERTICAL);
      ChatUser other = conversation.other();
      if (!other.avatarUrl().isBlank()) {
        android.view.View avatar = Ui.image(a, other.avatarUrl(), "头像", false);
        avatar.setBackground(Ui.shape(a, Design.ALT, 0, 999)); avatar.setClipToOutline(true);
        row.addView(avatar, new LinearLayout.LayoutParams(Ui.dp(a, 44), Ui.dp(a, 44)));
      } else {
        TextView initial = Ui.text(a, other.displayName().isBlank() ? "饭" : other.displayName().substring(0, 1), 16, Design.MUTED);
        initial.setGravity(Gravity.CENTER);
        initial.setBackground(Ui.shape(a, Design.ALT, 0, 999));
        row.addView(initial, new LinearLayout.LayoutParams(Ui.dp(a, 44), Ui.dp(a, 44)));
      }
      LinearLayout copy = Ui.col(a);
      LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(0, -2, 1);
      copyLp.leftMargin = Ui.dp(a, 12);
      row.addView(copy, copyLp);
      TextView name = Ui.text(a, other.displayName().isBlank() ? "同学" : other.displayName(), 16, Design.INK);
      name.setTypeface(Design.title());
      copy.addView(name);
      Ui.gap(copy, 4);
      TextView preview = Ui.text(a, conversation.lastMessage().isBlank() ? "还没有消息" : conversation.lastMessage(), 13, Design.MUTED);
      preview.setSingleLine(true);
      preview.setEllipsize(android.text.TextUtils.TruncateAt.END);
      copy.addView(preview);
      if (conversation.unreadCount() > 0) {
        TextView badge = Ui.text(a, conversation.unreadCount() > 99 ? "99+" : String.valueOf(conversation.unreadCount()), 11, Color.WHITE);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(Ui.shape(a, Design.RED, 0, 999));
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(-2, -2);
        badgeLp.leftMargin = Ui.dp(a, 8);
        row.addView(badge, badgeLp);
      }
      card.addView(row, new LinearLayout.LayoutParams(-1, -2));
      card.setClickable(true); card.setFocusable(true);
      card.setContentDescription(other.displayName() + " 的私信" + (conversation.unreadCount() > 0 ? "，未读" + conversation.unreadCount() : ""));
      card.setOnClickListener(v -> a.go("direct-chat", conversation.id()));
    }
    if (result.hasMore()) {
      TextView more = Ui.button(a, "加载更多私信", false, () -> {});
      page.addView(more);
      more.setOnClickListener(v -> a.read("inbox-chats-more", v, null, () -> a.repo.loadMoreConversations(), a::refreshContent, () -> {}));
    }
  }

  private static void messages(MainActivity a, LinearLayout page) {
    MessagePage result = a.repo.messages();
    LinearLayout toolbar = Ui.row(a);
    toolbar.setGravity(Gravity.CENTER_VERTICAL);
    toolbar.addView(Ui.text(a, "共 " + result.total() + " 条", 13, Design.MUTED),
        new LinearLayout.LayoutParams(0, -2, 1));
    TextView all = Ui.button(a, "全部标为已读", false, () -> {});
    all.setOnClickListener(v -> a.request(v, null, () -> a.repo.readAllMessages(), a::refreshContent));
    toolbar.addView(all, new LinearLayout.LayoutParams(-2, -2));
    page.addView(toolbar, new LinearLayout.LayoutParams(-1, -2));
    Ui.gap(page, 12);
    if (result.items().isEmpty()) {
      if (a.isPageLoading()) Ui.empty(page, "正在读取系统消息…", "互动提醒马上就来。");
      else Ui.empty(page, "暂无系统消息", "审核结果、点赞与评论提醒会出现在这里。");
      return;
    }
    for (Message message : result.items()) {
      LinearLayout card = Ui.card(page);
      card.setPadding(0, 0, 0, 0);
      card.setBackground(Ui.ripple(a, message.isRead() ? Design.PAPER : Design.ALT, Design.BORDER, 18));
      LinearLayout row = Ui.row(a);
      row.setGravity(Gravity.TOP);
      row.setPadding(Ui.dp(a, 14), Ui.dp(a, 14), Ui.dp(a, 14), Ui.dp(a, 14));
      card.addView(row, new LinearLayout.LayoutParams(-1, -2));

      TextView icon = Ui.text(a, iconFor(message), 21, colorFor(message));
      icon.setGravity(Gravity.CENTER);
      icon.setBackground(Ui.shape(a, iconBackground(message), 0, 999));
      row.addView(icon, new LinearLayout.LayoutParams(Ui.dp(a, 42), Ui.dp(a, 42)));

      LinearLayout copy = Ui.col(a);
      LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(0, -2, 1);
      copyLp.leftMargin = Ui.dp(a, 12);
      row.addView(copy, copyLp);

      LinearLayout headline = Ui.row(a);
      headline.setGravity(Gravity.CENTER_VERTICAL);
      TextView title = Ui.text(a, message.title(), 16, message.isRead() ? Design.MUTED : Design.INK);
      title.setTypeface(Design.title());
      headline.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
      if (!message.isRead()) headline.addView(Ui.text(a, "●", 11, Design.RED));
      copy.addView(headline);
      Ui.gap(copy, 5);

      String body = message.body() == null ? "" : message.body();
      if (!body.isBlank()) {
        TextView preview = Ui.text(a, body, 14, Design.INK);
        preview.setMaxLines(3);
        preview.setEllipsize(android.text.TextUtils.TruncateAt.END);
        copy.addView(preview);
        Ui.gap(copy, 7);
      }

      LinearLayout meta = Ui.row(a);
      meta.setGravity(Gravity.CENTER_VERTICAL);
      meta.addView(Ui.text(a, categoryLabel(message), 11, Design.MUTED),
          new LinearLayout.LayoutParams(0, -2, 1));
      if (message.actorCount() > 1) {
        TextView count = Ui.text(a, message.actorCount() + "人", 11, Design.RED);
        LinearLayout.LayoutParams countLp = new LinearLayout.LayoutParams(-2, -2);
        countLp.rightMargin = Ui.dp(a, 8);
        meta.addView(count, countLp);
      }
      meta.addView(Ui.text(a, CommunityText.publishedAt(message.createdAt()), 11, Design.MUTED));
      copy.addView(meta);

      if (isCommunityMessage(message)) {
        Ui.gap(copy, 8);
        TextView open = Ui.button(a, "查看原帖", false, () -> openMessage(a, message));
        open.setTextSize(13);
        open.setMinHeight(Ui.dp(a, 36));
        open.setBackground(Ui.ripple(a, Design.PAPER, Design.RED, 16));
        open.setTextColor(Design.RED);
        copy.addView(open, new LinearLayout.LayoutParams(-2, -2));
      }

      card.setClickable(true);
      card.setFocusable(true);
      card.setContentDescription(message.title() + (message.isRead() ? "" : "，未读") + "，查看消息");
      card.setOnClickListener(v -> openMessage(a, message));
    }
    if (result.hasMore()) {
      TextView more = Ui.button(a, "加载更多消息", false, () -> {});
      page.addView(more);
      more.setOnClickListener(v -> a.read("inbox-messages", v, null, () -> a.repo.loadMoreMessages(), a::refreshContent, () -> {}));
    }
  }

  private static String iconFor(Message message) {
    return switch (message.category() == null ? "" : message.category()) {
      case "LIKE_POST", "LIKE_COMMENT" -> "♥";
      case "COMMENT_POST" -> "💬";
      case "REPLY_COMMENT" -> "↩";
      default -> "🔔";
    };
  }

  private static int colorFor(Message message) {
    return switch (message.category() == null ? "" : message.category()) {
      case "LIKE_POST", "LIKE_COMMENT" -> Design.RED;
      case "COMMENT_POST" -> Design.BLUE;
      case "REPLY_COMMENT" -> Design.GREEN;
      default -> Design.GOLD;
    };
  }

  private static int iconBackground(Message message) {
    return switch (message.category() == null ? "" : message.category()) {
      case "LIKE_POST", "LIKE_COMMENT" -> Design.RED_SOFT;
      case "COMMENT_POST" -> 0x1f3f7291;
      case "REPLY_COMMENT" -> 0x1f5b7a3a;
      default -> 0x1fb88924;
    };
  }

  private static String categoryLabel(Message message) {
    return switch (message.category() == null ? "" : message.category()) {
      case "LIKE_POST" -> "食记点赞";
      case "LIKE_COMMENT" -> "评论点赞";
      case "COMMENT_POST" -> "食记评论";
      case "REPLY_COMMENT" -> "评论回复";
      default -> message.category() == null || message.category().isBlank() ? "系统消息" : message.category();
    };
  }

  private static boolean isCommunityMessage(Message message) {
    if (message.refId() == null || message.refId().isBlank()) return false;
    if (!"community-post".equals(message.refType())) return false;
    return switch (message.category() == null ? "" : message.category()) {
      case "LIKE_POST", "LIKE_COMMENT", "COMMENT_POST", "REPLY_COMMENT" -> true;
      default -> false;
    };
  }

  private static void openNotice(MainActivity a, Notice notice) {
    String body = notice.content() == null ? "" : notice.content();
    openFull(a, "系统公告", body, true);
    a.request(null, null, () -> a.repo.markNoticeRead(notice.id()), a::refreshContent);
  }

  private static void openMessage(MainActivity a, Message message) {
    if (isCommunityMessage(message)) {
      if (!message.isRead()) {
        a.read("message-read:" + message.id(), null, null,
            () -> a.repo.readMessage(message.id()), () -> {}, () -> {});
      }
      if (message.refCommentId() != null && !message.refCommentId().isBlank()) {
        String prefix = "community-detail:" + message.refId() + ":";
        a.drafts.putString(prefix + "focusComment", message.refCommentId());
        a.drafts.putString(prefix + "focusRoot",
            message.refRootCommentId() == null ? "" : message.refRootCommentId());
        a.drafts.remove(prefix + "focusTried");
      }
      a.go("community-detail", message.refId());
      return;
    }
    String body = message.body() == null ? "" : message.body();
    showDetail(a, message.title(), body);
    if (!message.isRead()) a.request(null, null, () -> a.repo.readMessage(message.id()), a::refreshContent);
  }

  /** Short bodies reuse the Ui.confirm AlertDialog pattern; long bodies get a full-screen reader. */
  private static void showDetail(MainActivity a, String title, String body) {
    String text = body == null ? "" : body;
    if (text.length() > 120) openFull(a, title, text);
    else Ui.confirm(a, title, text.isEmpty() ? "（正文为空）" : text, () -> {});
  }

  /** Full-screen text reader following the CommunityGallery.open() Dialog pattern. */
  private static void openFull(MainActivity a, String title, String body) { openFull(a,title,body,false); }
  private static void openFull(MainActivity a, String title, String body, boolean markdown) {
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
    TextView text=Ui.text(a, body, 16, Design.INK);
    if(markdown) NoticeMarkdown.render(text,body);
    content.addView(text);
    scroll.addView(content);
    screen.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    dialog.setContentView(screen);
    dialog.show();
  }

  private InboxScreen() {}
}
