package cn.edu.bjtu.foodtime.presentation;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.edu.bjtu.foodtime.domain.model.StartupGate;
import java.util.ArrayList;
import java.util.List;

/** An opaque cold-process cover; the normal page still measures and loads behind it. */
final class ColdStartSplash {
  private static StartupGate processGate;
  private final MainActivity activity;
  private final StartupGate gate;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Runnable released;
  private final List<Runnable> pending = new ArrayList<>();
  private FrameLayout host;
  private View cover, page;
  private boolean ready, closed, painted;

  ColdStartSplash(MainActivity activity, Runnable timeout, Runnable released) {
    this.activity = activity;
    this.released = released;
    // Lives only in this process: activity recreation and warm launches reuse its deadline.
    if (processGate == null) processGate = new StartupGate(SystemClock.elapsedRealtime(), 1500, 15000);
    gate = processGate;
    if (gate.showing()) handler.postDelayed(() -> {
      if (!closed && gate.timedOut(SystemClock.elapsedRealtime(), ready)) timeout.run();
    }, gate.timeoutRemaining(SystemClock.elapsedRealtime()));
  }

  boolean showing() { return gate.showing(); }

  void install(LinearLayout content) {
    if (!showing()) { activity.setContentView(content); return; }
    ready = false;
    painted = false;
    page = content;
    page.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    host = new FrameLayout(activity);
    host.addView(page, new FrameLayout.LayoutParams(-1, -1));
    LinearLayout brand = Ui.col(activity);
    brand.setGravity(Gravity.CENTER);
    brand.setPadding(Ui.dp(activity, 28), 0, Ui.dp(activity, 28), 0);
    brand.setBackgroundColor(Design.PAPER);
    brand.setClickable(true);
    brand.setFocusable(true);
    brand.setTag("cold-start-splash");
    brand.setContentDescription("北交干饭吧，正在启动");
    TextView name = Ui.title(activity, "北交干饭吧", 42);
    name.setTextColor(Design.RED);
    name.setGravity(Gravity.CENTER);
    name.setMaxLines(1);
    name.setAutoSizeTextTypeUniformWithConfiguration(24, 42, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
    brand.addView(name, new LinearLayout.LayoutParams(-1, Ui.dp(activity, 76)));
    TextView slogan = Ui.note(activity, "今天也要好好吃饭");
    slogan.setTextColor(Design.INK);
    slogan.setGravity(Gravity.CENTER);
    brand.addView(slogan);
    cover = brand;
    brand.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
      @Override public boolean onPreDraw() {
        brand.getViewTreeObserver().removeOnPreDrawListener(this);
        gate.shown(SystemClock.elapsedRealtime());
        painted = true;
        revealWhenReady();
        return true;
      }
    });
    host.addView(cover, new FrameLayout.LayoutParams(-1, -1));
    activity.setContentView(host);
  }

  void contentReady() {
    if (!showing() || page == null) return;
    View current = page;
    current.post(() -> {
      if (closed || current != page) return;
      ready = true;
      revealWhenReady();
    });
  }

  private void revealWhenReady() {
    if (closed || !showing() || !ready || !painted) return;
    long delay = gate.remaining(SystemClock.elapsedRealtime(), ready);
    if (delay > 0) { handler.postDelayed(this::revealWhenReady, delay); return; }
    gate.dismiss();
    handler.removeCallbacksAndMessages(null);
    if (cover != null) host.removeView(cover);
    if (page != null) page.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
    released.run();
    List<Runnable> tasks = List.copyOf(pending); pending.clear();
    for (Runnable task : tasks) task.run();
  }

  void afterReady(Runnable task) { if (showing()) pending.add(task); else task.run(); }
  void finish() { gate.dismiss(); close(); }
  void close() {
    closed = true;
    handler.removeCallbacksAndMessages(null);
    pending.clear();
    if (activity.isFinishing()) gate.dismiss();
  }
}
