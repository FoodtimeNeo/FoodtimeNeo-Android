package cn.edu.bjtu.foodtime.presentation;

import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import cn.edu.bjtu.foodtime.domain.model.CommunityText;
import cn.edu.bjtu.foodtime.domain.model.Models.*;
import java.util.*;

/**
 * 一对一私信：顶部固定返回/联系人，底部固定输入条，中间消息区随页面滚动。
 * 布局参考常见手机聊天应用；发送、加载更早与已读都只刷新消息区域。
 */
final class ChatScreen {
  static void chat(MainActivity a, LinearLayout page, MainActivity.Route route) {
    Conversation conversation = a.repo.conversation(route.id());
    if (conversation == null) {
      Ui.empty(page, "正在打开私信…", "消息马上就来。");
      return;
    }
    if (conversation.unreadCount() > 0) {
      a.read("chat-read:" + route.id(), null, null,
          () -> a.repo.readConversation(route.id()), () -> {}, () -> {});
    }

    LinearLayout list = Ui.col(a);
    list.setPadding(Ui.dp(a, 12), Ui.dp(a, 12), Ui.dp(a, 12), Ui.dp(a, 16));
    page.addView(list, new LinearLayout.LayoutParams(-1, -2));
    renderMessages(a, list, route.id());

    if (!a.repo.conversationMessages(route.id()).items().isEmpty())
      a.scroll.post(() -> a.scroll.fullScroll(View.FOCUS_DOWN));
    a.onContentRefresh(() -> {
      list.removeAllViews();
      renderMessages(a, list, route.id());
      Conversation current = a.repo.conversation(route.id());
      if (current != null && current.unreadCount() > 0) {
        a.read("chat-read:" + route.id(), null, null,
            () -> a.repo.readConversation(route.id()), () -> {}, () -> {});
      }
    });

    LinearLayout bar = Ui.col(a);
    View rule = new View(a);
    rule.setBackgroundColor(Design.BORDER);
    bar.addView(rule, new LinearLayout.LayoutParams(-1, Ui.dp(a, 1)));
    LinearLayout composer = Ui.row(a);
    composer.setGravity(Gravity.CENTER_VERTICAL);
    composer.setPadding(Ui.dp(a, 10), Ui.dp(a, 8), Ui.dp(a, 10), Ui.dp(a, 8));
    composer.setBackgroundColor(Design.PAPER);

    EditText input = new EditText(a);
    input.setTextSize(15);
    input.setTextColor(Design.INK);
    input.setHintTextColor(Design.MUTED);
    input.setTypeface(Design.body());
    input.setHint("说点什么…");
    input.setContentDescription("私信内容");
    input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
    input.setSingleLine(false);
    input.setMaxLines(4);
    input.setMinHeight(Ui.dp(a, 44));
    input.setPadding(Ui.dp(a, 14), Ui.dp(a, 10), Ui.dp(a, 14), Ui.dp(a, 10));
    input.setBackground(Ui.shape(a, Design.ALT, Design.BORDER, 22));
    input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(1000)});
    LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(0, -2, 1);
    inputLp.rightMargin = Ui.dp(a, 8);
    composer.addView(input, inputLp);

    TextView send = Ui.button(a, "发送", true, () -> {});
    send.setMinWidth(Ui.dp(a, 72));
    send.setMinHeight(Ui.dp(a, 44));
    send.setBackground(Ui.ripple(a, Design.RED, 0, 22));
    composer.addView(send, new LinearLayout.LayoutParams(-2, -2));
    bar.addView(composer, new LinearLayout.LayoutParams(-1, -2));
    a.setChatComposer(bar);

    send.setOnClickListener(v -> {
      String body = input.getText().toString().trim();
      if (body.isEmpty()) { a.message("消息不能为空"); return; }
      if (body.length() > 1000) { a.message("消息不能超过1000字"); return; }
      a.read("chat-send:" + route.id(), send, null,
          () -> a.repo.sendDirectMessage(route.id(), body, UUID.randomUUID().toString()),
          () -> {
            input.setText("");
            a.refreshContent();
            a.scroll.post(() -> a.scroll.fullScroll(View.FOCUS_DOWN));
          }, () -> {});
    });
  }

  private static void renderMessages(MainActivity a, LinearLayout list, String conversationId) {
    DirectMessagePage messages = a.repo.conversationMessages(conversationId);
    if (messages.hasMore()) {
      TextView older = Ui.button(a, "加载更早的消息", false, () -> {});
      older.setTextSize(12);
      older.setBackground(Ui.ripple(a, Design.PAPER, Design.BORDER, 16));
      older.setTextColor(Design.MUTED);
      older.setOnClickListener(v -> {
        int oldHeight = list.getHeight(), oldOffset = a.scroll.getScrollY();
        a.read("chat-older:" + conversationId, v, null,
            () -> a.repo.loadMoreConversationMessages(conversationId), () -> {
              a.refreshContent();
              list.post(() -> a.scroll.scrollTo(0, oldOffset + list.getHeight() - oldHeight));
            }, () -> {});
      });
      list.addView(older, new LinearLayout.LayoutParams(-1, -2));
      Ui.gap(list, 10);
    }
    if (messages.items().isEmpty()) {
      Ui.empty(list, "还没有消息", "发一句问候开始聊天吧。");
    } else {
      String me = a.repo.currentUser() == null ? "" : a.repo.currentUser().id();
      for (DirectMessage message : messages.items()) message(a, list, message, me);
    }
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
    // 垂直 LinearLayout 默认给子 View MATCH_PARENT；在 wrap_content 气泡里会测得 0 宽，
    // 表现为只有内边距的窄空框，因此显式使用 WRAP_CONTENT。
    bubble.addView(text, new LinearLayout.LayoutParams(-2, -2));
    Ui.gap(bubble, 3);
    TextView time = Ui.text(a, CommunityText.publishedAt(message.createdAt()), 10, Design.MUTED);
    time.setGravity(mine ? Gravity.END : Gravity.START);
    bubble.addView(time, new LinearLayout.LayoutParams(-2, -2));
    line.addView(bubble, new LinearLayout.LayoutParams(-2, -2));
    list.addView(line, new LinearLayout.LayoutParams(-1, -2));
    Ui.gap(list, 7);
  }
}
