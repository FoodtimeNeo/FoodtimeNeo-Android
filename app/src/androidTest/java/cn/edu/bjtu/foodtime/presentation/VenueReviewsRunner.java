package cn.edu.bjtu.foodtime.presentation;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import cn.edu.bjtu.foodtime.BuildConfig;
import cn.edu.bjtu.foodtime.data.remote.RemoteRepository;
import cn.edu.bjtu.foodtime.domain.repository.ContentRequest;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Real native UI + isolated local Backend. Refuses shared packages and remote endpoints. */
public final class VenueReviewsRunner extends Instrumentation {
  private MainActivity activity;
  private int checks;
  private static final String HALL = "00000000-0000-4000-8000-000000000101",
      STALL = "00000000-0000-4000-8000-000000000201";

  private void check(boolean ok, String message) {
    if (!ok) throw new AssertionError(message);
    checks++;
  }

  private void ui(Runnable action) {
    AtomicReference<Throwable> error = new AtomicReference<>();
    runOnMainSync(
        () -> {
          try {
            action.run();
          } catch (Throwable failure) {
            error.set(failure);
          }
        });
    if (error.get() != null) throw new AssertionError(error.get());
  }

  private boolean onUi(BooleanSupplier query) {
    AtomicReference<Boolean> value = new AtomicReference<>(false);
    ui(() -> value.set(query.getAsBoolean()));
    return value.get();
  }

  private void await(BooleanSupplier query) throws Exception {
    long end = SystemClock.uptimeMillis() + 20000;
    while (SystemClock.uptimeMillis() < end) {
      if (onUi(query)) return;
      SystemClock.sleep(75);
    }
    throw new AssertionError("UI timeout: " + visible(activity.root));
  }

  private String visible(View view) {
    if (view.getVisibility() != View.VISIBLE) return "";
    StringBuilder text = new StringBuilder();
    if (view instanceof TextView t) text.append(t.getText()).append(" | ");
    if (view instanceof ViewGroup g)
      for (int i = 0; i < g.getChildCount(); i++) text.append(visible(g.getChildAt(i)));
    return text.toString();
  }

  private View find(View view, String text) {
    if (view.getVisibility() != View.VISIBLE) return null;
    if (view instanceof TextView t && text.contentEquals(t.getText())) return view;
    if (view instanceof ViewGroup g)
      for (int i = 0; i < g.getChildCount(); i++) {
        View v = find(g.getChildAt(i), text);
        if (v != null) return v;
      }
    return null;
  }

  private View described(View view, String text) {
    if (view.getContentDescription() != null && text.contentEquals(view.getContentDescription()))
      return view;
    if (view instanceof ViewGroup g)
      for (int i = 0; i < g.getChildCount(); i++) {
        View v = described(g.getChildAt(i), text);
        if (v != null) return v;
      }
    return null;
  }

  private EditText editor(View view) {
    if (view.getVisibility() != View.VISIBLE) return null;
    if (view instanceof EditText e) return e;
    if (view instanceof ViewGroup g)
      for (int i = 0; i < g.getChildCount(); i++) {
        EditText e = editor(g.getChildAt(i));
        if (e != null) return e;
      }
    return null;
  }

  private void click(String text) {
    ui(
        () -> {
          View v = find(activity.root, text);
          if (v == null) throw new AssertionError("Missing " + text);
          v.performClick();
        });
  }

  private void go(String kind, String id) throws Exception {
    ui(() -> activity.go(kind, id));
    await(() -> !activity.isPageLoading());
  }

  private void screenshot(String name) throws Exception {
    ui(
        () -> {
          activity
              .getSystemService(android.view.inputmethod.InputMethodManager.class)
              .hideSoftInputFromWindow(activity.root.getWindowToken(), 0);
          activity.scroll.fullScroll(View.FOCUS_UP);
        });
    waitForIdleSync();
    SystemClock.sleep(250);
    try (var out =
        new FileOutputStream(new File(getTargetContext().getFilesDir(), name + ".png"))) {
      var bitmap = getUiAutomation().takeScreenshot();
      bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
      bitmap.recycle();
    }
  }

  private void moderate(RemoteRepository moderator, String id) {
    moderator.refresh(new ContentRequest(ContentRequest.Query.MODERATION, ""));
    moderator.moderate(id, "approve", "");
  }

  @Override
  public void onCreate(Bundle args) {
    super.onCreate(args);
    start();
  }

  @Override
  public void onStart() {
    Bundle result = new Bundle();
    try {
      check(
          BuildConfig.APPLICATION_ID.endsWith(".integration"),
          "Use isolated integration application");
      check(
          BuildConfig.API_BASE_URL.equals("http://127.0.0.1:8080/api/v1/"),
          "Local Backend required");
      activity =
          (MainActivity)
              startActivitySync(
                  new Intent(getTargetContext(), MainActivity.class)
                      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      await(() -> activity.root != null && find(activity.root, "正在连接…") == null);
      activity.repo.login("20260002@bjtu.edu.cn", "VenueTest2026!");
      RemoteRepository moderator =
          new RemoteRepository(
              BuildConfig.API_BASE_URL,
              new cn.edu.bjtu.foodtime.data.remote.HttpTransport(BuildConfig.API_BASE_URL));
      moderator.login("20260003@bjtu.edu.cn", "VenueTest2026!");
      for (String type : new String[] {"hall", "stall"}) {
        String target = type.equals("hall") ? HALL : STALL;
        activity.repo.loadVenue(type, target, "all", false);
        var old = activity.repo.venue(type, target);
        if (old.privateSubmission() != null && old.privateSubmission().status().equals("pending")) {
          moderator.refresh(new ContentRequest(ContentRequest.Query.MODERATION, ""));
          moderator.moderate(old.privateSubmission().submissionId(), "reject", "重跑本地隔离验收");
        }
        if (old.publicVersion() != null)
          activity.repo.deleteVenue(old.publicVersion().reviewId(), false, false, null);
      }
      String content = "安卓真实验收餐厅 " + System.currentTimeMillis();
      go("venue-reviews", "hall~" + HALL + "~all");
      click("写点评");
      await(() -> !activity.isPageLoading() && editor(activity.root) != null);
      ui(
          () -> {
            described(activity.root, "3星").performClick();
            editor(activity.root).setText(content);
          });
      click("提交评价");
      await(
          () ->
              !activity.operationInFlight()
                  && !activity.isPageLoading()
                  && activity.route().kind().equals("venue-reviews")
                  && find(activity.root, content) != null);
      check(
          onUi(() -> visible(activity.root).contains("审核中 · 仅自己可见")), "Native pending card inline");
      var mine = activity.repo.venue("hall", HALL);
      String root = mine.privateSubmission().reviewId(),
          submission = mine.privateSubmission().submissionId();
      check(
          mine.published().items().stream().noneMatch(r -> r.content().equals(content)),
          "Pending absent from public cache");
      moderator.loadVenue("hall", HALL, "all", false);
      check(
          moderator.venue("hall", HALL).privateSubmission() == null,
          "Other account cannot obtain pending body");
      screenshot("venue-native-pending");
      click("刷新点评");
      await(() -> !activity.isPageLoading() && find(activity.root, content) != null);
      check(
          activity.repo.venue("hall", HALL).privateSubmission() != null,
          "Refresh restores pending from Backend");
      moderate(moderator, submission);
      click("刷新点评");
      await(
          () ->
              activity.repo.venue("hall", HALL).privateSubmission() == null
                  && find(activity.root, content) != null);
      check(
          activity.repo.venue("hall", HALL).published().items().stream()
                  .filter(r -> r.id().equals(root))
                  .count()
              == 1,
          "Approval merges once");
      click("回复 · 0");
      await(() -> find(activity.root, "写下你的回复…") != null);
      click("写下你的回复…");
      String replyText = "安卓真实验收待审回复 " + System.currentTimeMillis();
      ui(() -> editor(activity.root).setText(replyText));
      click("提交回复");
      await(() -> !activity.operationInFlight() && find(activity.root, replyText) != null);
      check(onUi(() -> visible(activity.root).contains("审核中 · 仅自己可见")), "Pending reply inline");
      var reply = activity.repo.venueReplies(root, true).items().get(0);
      moderator.loadVenueReplies(root, false, false);
      check(
          moderator.venueReplies(root, false).total() == 0,
          "Pending reply absent for another user");
      moderate(moderator, reply.id());
      click("刷新回复");
      await(
          () ->
              activity.repo.venueReplies(root, true).items().isEmpty()
                  && activity.repo.venueReplies(root, false).total() == 1);
      check(onUi(() -> find(activity.root, replyText) != null), "Approved reply stays in floor");
      click("回复这条");
      String nested = "放心，安卓楼中楼回复 " + System.currentTimeMillis();
      ui(() -> editor(activity.root).setText(nested));
      click("提交回复");
      await(
          () ->
              !activity.operationInFlight()
                  && activity.repo.venueReplies(root, false).total() == 2);
      check(
          activity.repo.venueReplies(root, false).items().stream()
              .anyMatch(r -> r.content().equals(nested) && r.replyToId().equals(reply.id())),
          "Actual nested reply target preserved and AI approved");
      ui(
          () -> {
            View block = (View) find(activity.root, content).getParent();
            described(block, "点赞，0个赞").performClick();
          });
      await(() -> !activity.operationInFlight());
      check(
          activity.repo.likeVenue(root, true, false).likeCount() == 1,
          "Root UI like is idempotent");
      check(
          activity.repo.likeVenue(reply.id(), true, true).likeCount() == 1, "Reply like connected");
      screenshot("venue-native-discussion");
      go("venue-write", "stall~" + STALL);
      await(() -> editor(activity.root) != null);
      String stallText = "放心，安卓档口真实验收 " + System.currentTimeMillis();
      ui(
          () -> {
            described(activity.root, "5星").performClick();
            editor(activity.root).setText(stallText);
          });
      click("提交评价");
      await(
          () ->
              !activity.operationInFlight()
                  && !activity.isPageLoading()
                  && activity.route().kind().equals("venue-reviews")
                  && activity.repo.venue("stall", STALL).privateSubmission() == null);
      check(
          activity.repo.venue("stall", STALL).published().items().stream()
              .anyMatch(r -> r.content().equals(stallText)),
          "Stall root AI approval visible");
      go("venue-mine", "~reviews~1");
      check(
          onUi(
              () -> find(activity.root, content) != null && find(activity.root, stallText) != null),
          "Personal records include both target types");
      screenshot("venue-native-history");
      go("admin-audit", "");
      await(() -> find(activity.root, "投稿类型") != null);
      check(
          onUi(() -> visible(activity.root).contains("投稿类型")),
          "Native admin type filter available");
      result.putString("stream", "Venue local Backend/native UI PASS: " + checks + " checks\n");
      finish(-1, result);
    } catch (Throwable failure) {
      StringWriter stack = new StringWriter();
      failure.printStackTrace(new PrintWriter(stack));
      result.putString("stream", "Venue FAIL after " + checks + " checks\n" + stack);
      finish(1, result);
    } finally {
      if (activity != null) ui(activity::finish);
    }
  }
}
