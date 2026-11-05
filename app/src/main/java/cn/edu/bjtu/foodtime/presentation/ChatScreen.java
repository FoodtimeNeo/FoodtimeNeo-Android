package cn.edu.bjtu.foodtime.presentation;

import android.graphics.Color;
import android.view.Gravity;
import android.widget.*;
import cn.edu.bjtu.foodtime.domain.model.CommunityText;
import cn.edu.bjtu.foodtime.domain.model.Models.*;
import cn.edu.bjtu.foodtime.domain.repository.ContentRequest;
import java.util.*;

/** 一对一私信：消息按时间正序，自己靠右、对方靠左；进入时清除未读。 */
final class ChatScreen {
  static void chat(MainActivity a, LinearLayout page, MainActivity.Route route) {
    Conversation conversation = a.repo.conversation(route.id());
    if (conversation == null) {
      Ui.empty(page, "正在打开私信…", "消息马上就来。");
      return;
    }
    ChatUser other = conversation.other();
    String name = other.displayName().isBlank() ? "同学" : other.displayName();
    a.heading(page, "04.1", "私信", "与 " + name + " 的对话");

    if (conversation.unreadCount() > 0) {
      a.read("chat-read:" + route.id(), null, null,
          () -> a.repo.readConversation(route.id()), a::redraw, () -> {});
    }

    LinearLayout toolbar = Ui.row(a);
    toolbar.setGravity(Gravity.CENTER_VERTICAL);
    toolbar.addView(Ui.text(a, conversation.lastMessage().isBlank() ? "还没有消息" : "最近：" + conversation.lastMessage(), 13, Design.MUTED),
        new LinearLayout.LayoutParams(0, -2, 1));
    TextView refresh = Ui.button(a, "刷新", false, () -> {});
    refresh.setTextSize(13);
    refresh.setBackground(Ui.ripple(a, Design.PAPER, Design.BORDER, 16));
    refresh.setTextColor(Design.INK);
    refresh.setContentDescription("刷新私信");
    refresh.setOnClickListener(v -> a.read("chat-refresh:" + route.id(), v, null,
        () -> a.repo.refresh(new ContentRequest(ContentRequest.Query.CONVERSATIONS, route.id())),
        a::redraw, () -> {}));
    toolbar.addView(refresh, new LinearLayout.LayoutParams(-2, -2));
    page.addView(toolbar, new LinearLayout.LayoutParams(-1, -2));
    Ui.gap(page, 10);

    DirectMessagePage messages = a.repo.conversationMessages(route.id());
    LinearLayout list = Ui.card(page);
    if (messages.hasMore()) {
      TextView older = Ui.button(a, "加载更早的消息", false, () -> {});
      older.setTextSize(13);
      older.setBackground(Ui.ripple(a, Design.PAPER, Design.RED, 16));
      older.setTextColor(Design.RED);
      older.setOnClickListener(v -> a.read("chat-older:" + route.id(), v, null,
          () -> a.repo.loadMoreConversationMessages(route.id()), a::redraw, () -> {}));
      list.addView(older, new LinearLayout.LayoutParams(-1, -2));
      Ui.gap(list, 10);
    }
    if (messages.items().isEmpty()) {
      Ui.empty(list, "还没有消息", "发一句问候开始聊天吧。");
    } else {
      String me = a.repo.currentUser() == null ? "" : a.repo.currentUser().id();
      for (DirectMessage message : messages.items()) message(a, list, message, me);
    }
    page.addView(list, new LinearLayout.LayoutParams(-1, -2));
    Ui.gap(page, 14);

    LinearLayout composer = Ui.card(page);
    EditText input = Ui.field(composer, "说点什么…", false, true);
    input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(1000)});
    input.setHint("私信内容（最多1000字）");
    TextView error = Ui.text(a, "", 13, Design.RED);
    error.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
    composer.addView(error);
    TextView send = Ui.button(a, "发送", true, () -> {});
    send.setBackground(Ui.ripple(a, Design.RED, 0, 18));
    send.setOnClickListener(v -> {
      String body = input.getText().toString().trim();
      if (body.isEmpty()) { error.setText("消息不能为空"); return; }
      if (body.length() > 1000) { error.setText("消息不能超过1000字"); return; }
      error.setText("");
      a.read("chat-send:" + route.id(), send, error,
          () -> a.repo.sendDirectMessage(route.id(), body, UUID.randomUUID().toString()),
          () -> { input.setText(""); a.redraw(); }, () -> {});
    });
    composer.addView(send, new LinearLayout.LayoutParams(-1, -2));
    page.addView(composer, new LinearLayout.LayoutParams(-1, -2));
    Ui.gap(page, 18);
    LinearLayout marks = Ui.row(a);
    marks.setGravity(Gravity.CENTER);
    marks.addView(Ui.foodDoodle(a, 5, 0x665b7a3a), new LinearLayout.LayoutParams(Ui.dp(a, 74), Ui.dp(a, 56)));
    page.addView(marks);
  }

  private static void message(MainActivity a, LinearLayout list, DirectMessage message, String me) {
    boolean mine = message.senderId().equals(me);
    LinearLayout line = Ui.row(a);
    line.setGravity(mine ? Gravity.END : Gravity.START);
    LinearLayout bubble = Ui.col(a);
    bubble.setPadding(Ui.dp(a, 12), Ui.dp(a, 9), Ui.dp(a, 12), Ui.dp(a, 9));
    bubble.setBackground(Ui.shape(a, mine ? Design.RED_SOFT : Design.PAPER, mine ? Design.RED : Design.BORDER, 14));
    TextView text = Ui.text(a, message.content(), 15, Design.INK);
    text.setMaxWidth(Ui.dp(a, 280));
    bubble.addView(text);
    Ui.gap(bubble, 4);
    TextView time = Ui.text(a, CommunityText.publishedAt(message.createdAt()), 10, Design.MUTED);
    time.setGravity(mine ? Gravity.END : Gravity.START);
    bubble.addView(time);
    line.addView(bubble, new LinearLayout.LayoutParams(-2, -2));
    list.addView(line, new LinearLayout.LayoutParams(-1, -2));
    Ui.gap(list, 8);
  }
}
