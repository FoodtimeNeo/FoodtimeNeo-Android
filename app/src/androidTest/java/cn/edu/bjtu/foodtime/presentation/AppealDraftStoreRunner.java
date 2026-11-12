package cn.edu.bjtu.foodtime.presentation;

import android.app.Instrumentation;
import android.os.Bundle;
import cn.edu.bjtu.foodtime.BuildConfig;
import cn.edu.bjtu.foodtime.domain.model.Models.User;
import cn.edu.bjtu.foodtime.domain.repository.FoodtimeRepository;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Actual private preferences and production evidence guard, with no network calls. */
public final class AppealDraftStoreRunner extends Instrumentation {
  private int checks;

  @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }

  private void check(boolean value, String label) {
    if (!value) throw new AssertionError(label);
    checks++;
  }

  @Override public void onStart() {
    Bundle result = new Bundle();
    try {
      if (!BuildConfig.DEBUG || !BuildConfig.APPLICATION_ID.endsWith(".appealdraftcheck") ||
          !BuildConfig.API_BASE_URL.equals("http://127.0.0.1:18095/api/v1/"))
        throw new AssertionError("Only isolated appeal draft test build allowed");
      var context = getTargetContext();
      var preferences = context.getSharedPreferences("appeal-drafts", 0);
      preferences.edit().clear().commit();
      var a = new AppealDraftStore(context, "https://one.example/api/v1/", "actor-a");
      var b = new AppealDraftStore(context, "https://one.example/api/v1/", "actor-b");
      var otherService = new AppealDraftStore(context, "https://two.example/api/v1/", "actor-a");
      preferences.edit().putString("appeal:shared-decision", "unowned legacy text").commit();
      check(a.read("shared-decision").isEmpty(), "Unowned legacy draft is not assigned to current actor");
      check(preferences.contains("appeal:shared-decision"), "Unowned legacy entry is retained without unsafe migration");
      a.save("shared-decision", "private text from A");
      check(b.read("shared-decision").isEmpty(), "The other recipient of the same decision cannot read A text");
      check(otherService.read("shared-decision").isEmpty(), "The same UUID on another service cannot read A text");
      b.save("shared-decision", "private text from B");
      var restoredA = new AppealDraftStore(context, "https://one.example/api/v1/", "actor-a");
      check(restoredA.read("shared-decision").equals("private text from A"), "The original actor recovers their own draft");
      check(!a.key("evidence:shared-decision").equals(b.key("evidence:shared-decision")), "Evidence selections use the same account isolation");
      String request = a.requestId("shared-decision");
      check(request.equals(restoredA.requestId("shared-decision")), "Reauthentication and retry preserve the request ID");
      check(!request.equals(b.requestId("shared-decision")), "The second recipient has a distinct request ID");
      a.clear("shared-decision");
      check(a.read("shared-decision").isEmpty() && b.read("shared-decision").equals("private text from B"), "Success clears only the submitting actor's content");
      check(!request.equals(a.requestId("shared-decision")), "Clear removes the old request ID");

      User first = new User("actor-a", "a@example.test", "A", "user", true);
      User second = new User("actor-b", "b@example.test", "B", "user", true);
      var actor = new AtomicReference<>(first);
      var uploads = new AtomicInteger();
      MainActivity[] activity = new MainActivity[1];
      runOnMainSync(() -> activity[0] = new MainActivity());
      activity[0].repo = (FoodtimeRepository) Proxy.newProxyInstance(
          FoodtimeRepository.class.getClassLoader(), new Class<?>[]{FoodtimeRepository.class},
          (proxy, method, args) -> {
            if (method.getName().equals("currentUser")) return actor.get();
            if (method.getName().equals("uploadEvidence")) {
              uploads.incrementAndGet();
              actor.set(second);
              return "/api/v1/media/owned-by-a";
            }
            throw new AssertionError("Unexpected repository call: " + method.getName());
          });
      activity[0].drafts.putString("evidence:0", "local-a.png");
      activity[0].drafts.putString("evidence:1", "local-b.png");
      boolean rejected = false;
      try { EvidenceSlots.upload(activity[0], "evidence"); }
      catch (IllegalStateException expected) { rejected = true; }
      check(rejected && uploads.get() == 1, "Account switch after upload stops the next private file and association");
      actor.set(null);
      rejected = false;
      try { EvidenceSlots.upload(activity[0], "evidence"); }
      catch (IllegalStateException expected) { rejected = true; }
      check(rejected && uploads.get() == 1, "Signed-out evidence cannot start an upload");
      preferences.edit().clear().commit();
      result.putString("stream", "Appeal draft storage PASS: " + checks + " assertions\n");
      finish(-1, result);
    } catch (Throwable error) {
      result.putString("stream", "Appeal draft storage FAIL: " + android.util.Log.getStackTraceString(error));
      finish(1, result);
    }
  }
}
