package cn.edu.bjtu.foodtime;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import cn.edu.bjtu.foodtime.Models.*;
import java.io.*;
import java.util.*;

/** On-device acceptance: real Android JSON, preferences, activity, navigation and rendering. */
public class AcceptanceRunner extends Instrumentation {
  int checks;

  private void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
    checks++;
  }

  private void rejects(Runnable action, String message) {
    try {
      action.run();
      throw new AssertionError(message);
    } catch (IllegalArgumentException | IllegalStateException expected) {
      checks++;
    }
  }

  @Override
  public void onCreate(Bundle args) {
    super.onCreate(args);
    start();
  }

  @Override
  public void onStart() {
    Bundle report = new Bundle();
    String isolated = "acceptance-" + UUID.randomUUID();
    Context target = getTargetContext();
    Context storage =
        new ContextWrapper(target) {
          @Override
          public SharedPreferences getSharedPreferences(String name, int mode) {
            return target.getSharedPreferences(isolated, mode);
          }
        };
    try {
      LocalRepository r = new LocalRepository(storage);
      check(r.halls().size() == 16, "Web dining fixture");
      r.login("20260001@bjtu.edu.cn", "Foodtime2026!");
      User student = r.currentUser();
      check(!student.isAdmin(), "Student role from account");
      rejects(() -> r.login("20260001@bjtu.edu.cn", "Wrongpass1"), "Wrong password rejected");
      Hall h = r.halls().get(0);
      Stall s = r.stalls(h.id()).get(0);
      Dish d = r.dishes(s.id()).get(0);
      Dish empty = r.dishes(s.id()).get(2);
      check(r.photos(empty.id()).isEmpty(), "Missing photo fixture");
      int before = r.reviews(d.id()).size();
      r.submitReview(d.id(), 4, "汤底不错，面条筋道。");
      check(r.reviews(d.id()).size() == before, "Pending review hidden");
      rejects(() -> r.submitReview(d.id(), 5, "重复评论"), "Duplicate pending review rejected");
      Submission review = r.submissions(student.id()).get(0);
      rejects(() -> r.moderate(review.id(), "approve", ""), "Student cannot moderate");
      rejects(() -> r.submissions(null), "Student cannot read audit queue");
      r.login("20260002@bjtu.edu.cn", "Foodtime2026!");
      r.moderate(review.id(), "approve", "");
      check(r.reviews(d.id()).size() == before + 1, "Approved review published");
      rejects(() -> r.moderate(review.id(), "approve", ""), "Review decision cannot replay");
      check(r.audits().size() == 1, "Audit history recorded");
      r.login(student.email(), "Foodtime2026!");
      r.submitReview(d.id(), 2, "更新后的真实感受。");
      String second = r.submissions(student.id()).get(0).id();
      r.login("20260002@bjtu.edu.cn", "Foodtime2026!");
      r.moderate(second, "approve", "");
      check(
          r.reviews(d.id()).stream().filter(v -> v.userId().equals(student.id())).count() == 1,
          "Review upsert unique per user and dish");
      r.login(student.email(), "Foodtime2026!");
      check(r.myReviews().get(0).rating() == 2, "Updated review reflected");
      r.deleteReview(r.myReviews().get(0).id());
      check(r.myReviews().isEmpty(), "Own review deleted");
      rejects(() -> r.deleteReview(r.reviews(d.id()).get(0).id()), "Other user review protected");
      int photos = r.photos(empty.id()).size();
      r.submitPhoto(empty.id(), "asset:dish_1.webp");
      String photo = r.submissions(student.id()).get(0).id();
      check(r.photos(empty.id()).size() == photos, "Pending photo hidden");
      r.login("20260002@bjtu.edu.cn", "Foodtime2026!");
      r.moderate(photo, "approve", "");
      check(r.photos(empty.id()).size() == photos + 1, "Approved photo published");
      r.login(student.email(), "Foodtime2026!");
      int dishes = r.dishes(s.id()).size();
      r.submitDish(s.id(), "验收新菜", "测试投稿", Validation.price("12.30"), "asset:dish_2.webp");
      String submission = r.submissions(student.id()).get(0).id();
      check(r.dishes(s.id()).size() == dishes, "Pending dish hidden");
      r.login("20260002@bjtu.edu.cn", "Foodtime2026!");
      r.moderate(submission, "approve", "");
      check(r.dishes(s.id()).size() == dishes + 1, "Approved dish added");
      Dish created = r.dishes(s.id()).get(dishes);
      check(created.price().toPlainString().equals("12.30"), "Exact price retained");
      check(r.photos(created.id()).size() == 1, "Submitted dish photo linked");
      r.login(student.email(), "Foodtime2026!");
      r.submitStall(h.id(), "验收档口", "", "asset:stall_1.webp");
      String stallSubmission = r.submissions(student.id()).get(0).id();
      r.login("20260002@bjtu.edu.cn", "Foodtime2026!");
      r.moderate(stallSubmission, "approve", "");
      check(
          r.stalls(h.id()).stream().anyMatch(v -> v.name().equals("验收档口")),
          "Stall published under parent hall");
      r.login(student.email(), "Foodtime2026!");
      r.submitPhoto(d.id(), "asset:dish_1.webp");
      String rejected = r.submissions(student.id()).get(0).id();
      r.login("20260002@bjtu.edu.cn", "Foodtime2026!");
      rejects(() -> r.moderate(rejected, "reject", ""), "Reject requires reason");
      r.moderate(rejected, "reject", "图片不清晰");
      check(
          r.submissions(student.id()).stream()
              .anyMatch(v -> v.id().equals(rejected) && v.reason().equals("图片不清晰")),
          "Reject feedback visible to owner");
      r.editEntity("halls", h.id(), "", h.name(), "", "", "asset:hall_1.webp", false);
      check(r.halls().stream().noneMatch(v -> v.id().equals(h.id())), "Disabled hall hidden");
      rejects(() -> r.stalls(h.id()), "Disabled parent blocks stalls");
      rejects(() -> r.dishes(s.id()), "Disabled parent blocks dishes");
      r.editEntity("halls", h.id(), "", h.name(), "", "", "asset:hall_1.webp", true);
      rejects(r::users, "Admin cannot manage users");
      r.login("20260003@bjtu.edu.cn", "Foodtime2026!");
      check(r.users().size() == 3, "Superadmin user list");
      r.updateUser(student.id(), "user", false);
      rejects(() -> r.login(student.email(), "Foodtime2026!"), "Disabled user login refused");
      r.updateUser(student.id(), "user", true);
      r.logout();
      String code = r.sendVerificationCode("20269999@bjtu.edu.cn");
      rejects(
          () ->
              r.register(
                  "20269999@bjtu.edu.cn",
                  "Foodtime2026!",
                  "999999".equals(code) ? "888888" : "999999"),
          "Wrong verification code rejected");
      r.register("20269999@bjtu.edu.cn", "Foodtime2026!", code);
      check(r.currentUser() == null, "Registration does not login automatically");
      r.login("20269999@bjtu.edu.cn", "Foodtime2026!");
      check(r.currentUser().role().equals("user"), "Registration role fixed to user");
      rejects(() -> r.changePassword("Wrongpass1", "Changed2026!"), "Old password checked");
      r.changePassword("Foodtime2026!", "Changed2026!");
      check(r.currentUser() == null, "Password change invalidates session");
      rejects(
          () -> r.login("20269999@bjtu.edu.cn", "Foodtime2026!"),
          "Old password no longer accepted");
      r.login("20269999@bjtu.edu.cn", "Changed2026!");
      LocalRepository restored = new LocalRepository(storage);
      check(restored.currentUser() != null, "Session persisted after restart");
      r.login(student.email(), "Foodtime2026!");
      Intent launch =
          new Intent(target, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      MainActivity a = (MainActivity) startActivitySync(launch);
      waitForIdleSync();
      runOnMainSync(
          () -> {
            a.repo = r;
            a.home();
          });
      waitForIdleSync();
      check(has(a.root, "食堂手账"), "Dining screen title");
      shot("01-dining");
      runOnMainSync(() -> a.go("stalls", h.id()));
      waitForIdleSync();
      check(has(a.root, s.name()), "Stall cover list rendered");
      shot("02-stalls");
      runOnMainSync(() -> a.go("dishes", s.id()));
      waitForIdleSync();
      check(has(a.root, d.name()), "Dish name list rendered");
      shot("03-dishes");
      runOnMainSync(() -> a.go("detail", d.id()));
      waitForIdleSync();
      check(has(a.root, "评分"), "Single-dimensional rating rendered");
      shot("04-detail");
      runOnMainSync(() -> a.scroll.fullScroll(View.FOCUS_DOWN));
      waitForIdleSync();
      shot("04-detail-comments");
      int depth = a.stacks.get("dining").size();
      runOnMainSync(() -> a.selectTab("dining"));
      check(a.stacks.get("dining").size() == depth, "Repeat tab does not reset stack");
      runOnMainSync(() -> a.selectTab("profile"));
      waitForIdleSync();
      check(has(a.root, "干饭档案"), "Profile rendered");
      shot("05-profile");
      runOnMainSync(() -> a.selectTab("dining"));
      check(a.route().kind().equals("detail"), "Tab switch preserves detail");
      runOnMainSync(a::back);
      check(
          a.route().kind().equals("dishes") && a.route().id().equals(s.id()),
          "Back preserves stall context");
      runOnMainSync(() -> a.go("review", d.id()));
      waitForIdleSync();
      shot("06-review");
      runOnMainSync(() -> a.back());
      runOnMainSync(() -> a.go("upload-photo", empty.id()));
      waitForIdleSync();
      shot("09-upload");
      File fixture = new File(target.getFilesDir(), "acceptance-image.webp");
      try (InputStream source = target.getAssets().open("images/dish_1.webp");
          FileOutputStream out = new FileOutputStream(fixture)) {
        source.transferTo(out);
      }
      String imageKey = a.draftKey("image");
      Intent picker = MainActivity.imagePickerIntent();
      check(
          picker.getAction().equals(Intent.ACTION_OPEN_DOCUMENT)
              && picker.getType().equals("image/*")
              && picker.hasCategory(Intent.CATEGORY_OPENABLE),
          "Native image document picker contract");
      runOnMainSync(() -> a.pickingKey = imageKey);
      Intent result = new Intent().setData(android.net.Uri.fromFile(fixture));
      runOnMainSync(() -> a.onActivityResult(101, Activity.RESULT_OK, result));
      waitForIdleSync();
      String imported = a.drafts.getString(imageKey, "");
      check(imported.startsWith("file:"), "Picked image copied to private storage");
      android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
      bounds.inJustDecodeBounds = true;
      android.graphics.BitmapFactory.decodeFile(android.net.Uri.parse(imported).getPath(), bounds);
      check(
          bounds.outWidth > 0 && bounds.outWidth <= 1600, "Imported image decoded at bounded size");
      runOnMainSync(
          () -> {
            r.submitPhoto(empty.id(), imported);
            a.back();
            a.selectTab("profile");
            a.go("mine-submissions", "");
          });
      waitForIdleSync();
      check(has(a.root, "待审核"), "Submission status visible");
      shot("10-submissions");
      runOnMainSync(
          () -> {
            a.selectTab("dining");
            a.go("review", d.id());
          });
      waitForIdleSync();
      Bundle state = new Bundle();
      runOnMainSync(
          () -> {
            a.drafts.putString(a.draftKey("test"), "草稿");
            a.onSaveInstanceState(state);
          });
      check(
          state.getBundle("drafts").containsKey(a.draftKey("test")),
          "Form draft saved with activity state");
      runOnMainSync(
          () -> {
            r.login("20260002@bjtu.edu.cn", "Foodtime2026!");
            a.selectTab("admin");
          });
      waitForIdleSync();
      check(has(a.root, "管理手账"), "Admin screen rendered");
      shot("07-admin");
      runOnMainSync(() -> a.go("admin-audit", ""));
      waitForIdleSync();
      check(has(a.root, "通过审核"), "Pending content has moderation action");
      shot("11-audit");
      runOnMainSync(
          () -> {
            a.logout();
          });
      waitForIdleSync();
      check(has(a.root, "登录手账"), "Logout returns to authentication");
      shot("08-auth");
      report.putString(
          "stream",
          "\nPASS: "
              + checks
              + " acceptance assertions; screenshots saved in target external files.\n");
      report.putInt("assertions", checks);
      finish(Activity.RESULT_OK, report);
    } catch (Throwable e) {
      report.putString(
          "stream",
          "\nFAIL after " + checks + " assertions: " + android.util.Log.getStackTraceString(e));
      finish(Activity.RESULT_CANCELED, report);
    } finally {
      target.deleteSharedPreferences(isolated);
    }
  }

  private boolean has(View view, String text) {
    if (view instanceof TextView t && t.getText().toString().contains(text)) return true;
    if (view instanceof ViewGroup group)
      for (int i = 0; i < group.getChildCount(); i++)
        if (has(group.getChildAt(i), text)) return true;
    return false;
  }

  private void shot(String name) throws IOException {
    try {
      Thread.sleep(400);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    Bitmap b = getUiAutomation().takeScreenshot();
    if (b == null) return;
    File dir = new File(getTargetContext().getExternalFilesDir(null), "acceptance-screenshots");
    dir.mkdirs();
    try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) {
      b.compress(Bitmap.CompressFormat.PNG, 100, out);
    }
    b.recycle();
  }
}
