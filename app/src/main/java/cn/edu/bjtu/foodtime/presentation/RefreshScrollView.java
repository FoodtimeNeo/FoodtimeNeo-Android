package cn.edu.bjtu.foodtime.presentation;

import android.content.Context;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ScrollView;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

/** Locks the gesture to its first axis and keeps the refresh indicator outside the feed layout. */
final class RefreshScrollView extends ScrollView {
  private enum Axis { UNDECIDED, SCROLL, PULL, SWIPE }

  private final View indicator;
  private final BooleanSupplier canRefresh;
  private final Runnable refresh;
  private final IntConsumer switchSection;
  private final int slop, threshold;
  private float startX, startY;
  private boolean fromTop, refreshing;
  private Axis axis = Axis.UNDECIDED;

  RefreshScrollView(Context context, View indicator, BooleanSupplier canRefresh,
      Runnable refresh, IntConsumer switchSection) {
    super(context);
    this.indicator = indicator;
    this.canRefresh = canRefresh;
    this.refresh = refresh;
    this.switchSection = switchSection;
    slop = ViewConfiguration.get(context).getScaledTouchSlop();
    threshold = Ui.dp(context, 88);
    setOverScrollMode(OVER_SCROLL_NEVER);
    int refreshAction = View.generateViewId();
    setAccessibilityDelegate(new AccessibilityDelegate() {
      @Override public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(host, info);
        info.addAction(new AccessibilityNodeInfo.AccessibilityAction(refreshAction, "刷新社区"));
      }
      @Override public boolean performAccessibilityAction(View host, int action, Bundle args) {
        if (action != refreshAction) return super.performAccessibilityAction(host, action, args);
        if (!refreshing && canRefresh.getAsBoolean()) beginRefresh();
        return true;
      }
    });
  }

  @Override public boolean onInterceptTouchEvent(MotionEvent event) {
    updateAxis(event);
    if (axis == Axis.PULL || axis == Axis.SWIPE) return true;
    return super.onInterceptTouchEvent(event);
  }

  @Override public boolean onTouchEvent(MotionEvent event) {
    updateAxis(event);
    if (axis != Axis.PULL && axis != Axis.SWIPE) return super.onTouchEvent(event);
    float dx = event.getX() - startX;
    float dy = event.getY() - startY;
    switch (event.getActionMasked()) {
      case MotionEvent.ACTION_MOVE -> {
        if (axis == Axis.PULL) showPull(dy);
      }
      case MotionEvent.ACTION_UP -> {
        if (axis == Axis.PULL) {
          if (dy >= threshold && canRefresh.getAsBoolean()) beginRefresh();
          else finishRefresh();
        } else if (Math.abs(dx) >= threshold && Math.abs(dx) > Math.abs(dy)) {
          post(() -> switchSection.accept(dx < 0 ? 1 : -1));
        }
        axis = Axis.UNDECIDED;
      }
      case MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> {
        if (axis == Axis.PULL) finishRefresh();
        axis = Axis.SCROLL;
      }
      default -> { }
    }
    return true;
  }

  private void updateAxis(MotionEvent event) {
    if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
      startX = event.getX();
      startY = event.getY();
      fromTop = !canScrollVertically(-1);
      axis = Axis.UNDECIDED;
    }
    if (axis != Axis.UNDECIDED || event.getActionMasked() != MotionEvent.ACTION_MOVE
        || event.getPointerCount() != 1) return;
    float dx = Math.abs(event.getX() - startX);
    float dy = event.getY() - startY;
    if (dx <= slop && Math.abs(dy) <= slop) return;
    if (dx > Math.abs(dy) * 1.3f) axis = Axis.SWIPE;
    else if (fromTop && dy > slop && !refreshing && canRefresh.getAsBoolean()) axis = Axis.PULL;
    else axis = Axis.SCROLL;
  }

  private void showPull(float distance) {
    indicator.animate().cancel();
    float travel = Math.max(0, Math.min(Ui.dp(getContext(), 65), (distance - slop) * .55f));
    indicator.setTranslationY(travel - Ui.dp(getContext(), 28));
    indicator.setAlpha(Math.min(1, travel / Ui.dp(getContext(), 36)));
    indicator.setVisibility(VISIBLE);
  }

  private void beginRefresh() {
    refreshing = true;
    indicator.setVisibility(VISIBLE);
    indicator.animate().translationY(Ui.dp(getContext(), 16)).alpha(1).setDuration(160).start();
    refresh.run();
  }

  void finishRefresh() {
    refreshing = false;
    if (indicator.getVisibility() != VISIBLE) return;
    indicator.animate().translationY(-Ui.dp(getContext(), 30)).alpha(0).setDuration(180)
        .withEndAction(() -> indicator.setVisibility(INVISIBLE)).start();
  }
}
