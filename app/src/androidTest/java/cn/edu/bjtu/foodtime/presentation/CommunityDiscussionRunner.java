package cn.edu.bjtu.foodtime.presentation;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.TextView;
import cn.edu.bjtu.foodtime.BuildConfig;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/** Native discussion acceptance using a dedicated loopback HTTP fixture. */
public final class CommunityDiscussionRunner extends Instrumentation {
  private MainActivity activity;
  private Bundle arguments;
  private int checks;

  @Override public void onCreate(Bundle args) { super.onCreate(args); arguments = args; start(); }

  private void check(boolean result, String name) {
    if (!result) throw new AssertionError(name);
    checks++;
  }

  private void await(String name, BooleanSupplier condition) {
    long deadline = SystemClock.uptimeMillis() + 12000;
    while (SystemClock.uptimeMillis() < deadline) {
      if (condition.getAsBoolean()) return;
      SystemClock.sleep(75);
    }
    throw new AssertionError("Timeout: " + name);
  }

  private boolean uiState(BooleanSupplier test) {
    AtomicBoolean result = new AtomicBoolean();
    runOnMainSync(() -> result.set(test.getAsBoolean()));
    return result.get();
  }

  private View tagged(String tag) { return activity.root.findViewWithTag(tag); }

  private void click(String tag) {
    await(tag, () -> uiState(() -> tagged(tag) != null && tagged(tag).isEnabled()));
    runOnMainSync(() -> tagged(tag).performClick());
  }

  private AccessibilityNodeInfo node(AccessibilityNodeInfo current, Predicate<AccessibilityNodeInfo> match) {
    if (current == null || match.test(current)) return current;
    for (int i = 0; i < current.getChildCount(); i++) {
      AccessibilityNodeInfo found = node(current.getChild(i), match);
      if (found != null) return found;
    }
    return null;
  }

  private AccessibilityNodeInfo sheetNode(Predicate<AccessibilityNodeInfo> match) {
    for (AccessibilityWindowInfo window : getUiAutomation().getWindows()) {
      if (window.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
      AccessibilityNodeInfo found = node(window.getRoot(), match);
      if (found != null) return found;
    }
    return null;
  }

  private AccessibilityNodeInfo editor() { return sheetNode(AccessibilityNodeInfo::isEditable); }

  private AccessibilityNodeInfo button(String text) {
    return sheetNode(n -> n.isClickable() && (text.contentEquals(n.getText() == null ? "" : n.getText())
        || text.contentEquals(n.getContentDescription() == null ? "" : n.getContentDescription())));
  }

  private void keyboardFits(String description) {
    await("sheet editor", () -> editor() != null && description.contentEquals(editor().getContentDescription()));
    await("software keyboard", () -> getUiAutomation().getWindows().stream()
        .anyMatch(w -> w.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD));
    Rect send = new Rect();
    button("发送").getBoundsInScreen(send);
    Rect keyboard = new Rect();
    getUiAutomation().getWindows().stream().filter(w -> w.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD)
        .findFirst().orElseThrow().getBoundsInScreen(keyboard);
    check(send.bottom <= keyboard.top && send.height() > 0, "Send remains above the keyboard");
  }

  private void type(String value) {
    Bundle args = new Bundle();
    args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value);
    if (!editor().performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) throw new AssertionError("Cannot edit sheet");
  }

  private void press(String label) {
    await(label, () -> button(label) != null && button(label).isEnabled());
    if (!button(label).performAction(AccessibilityNodeInfo.ACTION_CLICK)) throw new AssertionError("Cannot click " + label);
  }

  private void screenshot(String name) throws Exception {
    waitForIdleSync();
    SystemClock.sleep(300);
    Bitmap image = getUiAutomation().takeScreenshot();
    try (var output = new FileOutputStream(new File(getTargetContext().getFilesDir(), name + ".png"))) {
      image.compress(Bitmap.CompressFormat.PNG, 100, output);
    } finally { image.recycle(); }
  }

  @Override public void onStart() {
    Bundle result = new Bundle();
    try {
      if (!BuildConfig.DEBUG || !BuildConfig.APPLICATION_ID.endsWith(".discussioncheck")
          || !BuildConfig.API_BASE_URL.equals("http://127.0.0.1:18082/api/v1/")) {
        throw new AssertionError("Only the isolated loopback discussion build is allowed");
      }
      var automation = getUiAutomation();
      var info = automation.getServiceInfo();
      info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
      automation.setServiceInfo(info);
      String post = arguments.getString("post"), root = arguments.getString("root"), reply = arguments.getString("reply");
      activity = (MainActivity) startActivitySync(new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      await("local session", () -> uiState(() -> activity.repo.currentUser() != null && !activity.isPageLoading()));
      runOnMainSync(() -> { activity.selectTab("community"); activity.go("community-detail", post); });
      await("community rows", () -> uiState(() -> tagged("community-comment:" + reply) != null));
      screenshot("post-detail");
      click("community-gallery-image:0");
      press("点赞帖子");
      await("gallery like", () -> uiState(() -> tagged("community-post-like").isSelected()));
      check(button("加载原图") != null, "Fullscreen retains opt-in original");
      press("查看评论");
      await("gallery comment anchor", () -> uiState(() -> activity.scroll.getScrollY() > 0));
      runOnMainSync(() -> activity.scroll.fullScroll(View.FOCUS_DOWN));
      screenshot("comment-layout");
      check(uiState(() -> {
        int[] rootX = new int[2], replyX = new int[2];
        tagged("community-comment:" + root).getLocationOnScreen(rootX);
        tagged("community-comment:" + reply).getLocationOnScreen(replyX);
        return replyX[0] - rootX[0] == Ui.dp(activity, 42);
      }), "Nested replies are one level in");

      click("community-comment-dock-entry");
      keyboardFits("评论内容");
      check(!button("发送").isEnabled(), "Blank comment cannot send");
      screenshot("write-comment");
      type("帖子草稿保留");
      press("收起输入面板");
      await("sheet closes", () -> editor() == null);
      click("community-reply:" + reply);
      keyboardFits("回复 @有饭吃");
      check(!button("发送").isEnabled(), "Reply does not inherit comment draft");
      screenshot("write-reply");
      type("验证失败重试");
      press("发送");
      await("retry keeps draft", () -> editor() != null && editor().isEnabled()
          && sheetNode(n -> n.getText() != null && n.getText().toString().contains("样例请求失败")) != null);
      check("验证失败重试".contentEquals(editor().getText()), "Failure keeps reply text");
      press("发送");
      await("reply sent", () -> editor() == null);
      click("community-comment-entry");
      keyboardFits("评论内容");
      check("帖子草稿保留".contentEquals(editor().getText()), "Reply success does not clear top-level draft");
      press("发送");
      await("comment sent", () -> editor() == null);
      click("community-comment-like:" + root);
      await("comment like", () -> uiState(() -> ((TextView) tagged("community-comment-like:" + root)).isSelected()));
      click("community-post-like");
      await("post unlike", () -> uiState(() -> !tagged("community-post-like").isSelected()));
      click("community-post-favorite");
      await("favorite", () -> uiState(() -> tagged("community-post-favorite").isSelected()));
      check(uiState(() -> "community-detail".equals(activity.route().kind())), "Actions stay in post detail");
      result.putString("stream", "Community discussion device PASS: " + checks + " checks, including all three like/favorite waits\n");
      finish(-1, result);
    } catch (Throwable failure) {
      result.putString("stream", "Community discussion device FAIL: " + android.util.Log.getStackTraceString(failure));
      finish(1, result);
    }
  }
}
