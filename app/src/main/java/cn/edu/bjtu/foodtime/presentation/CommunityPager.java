package cn.edu.bjtu.foodtime.presentation;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Keeps the current feed and one destination attached for the duration of a swipe. */
final class CommunityPager implements RefreshScrollView.SwipeListener {
  private record Section(String route, String label) {}
  private static final List<Section> SECTIONS = List.of(
      new Section("community-following", "关注"), new Section("community", "发现"),
      new Section("community-favorites", "收藏"), new Section("community-mine", "我的食记"));
  private static final int SETTLE_MS = 240;

  record Page(RefreshScrollView scroll, LinearLayout content, Runnable update) {}

  private final MainActivity activity;
  final FrameLayout frame;
  private final ProgressBar spinner;
  private Page current, incoming;
  private int index, candidate = -1;
  private float fraction;
  private boolean dragging, settling;
  private ValueAnimator animation;
  private View underline;
  private LinearLayout tabRow;
  private final List<TextView> tabLabels = new ArrayList<>();

  CommunityPager(MainActivity activity, String section) {
    this.activity = activity;
    index = indexOf(section);
    frame = new FrameLayout(activity) {
      @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        return settling || super.onInterceptTouchEvent(event);
      }

      @Override public boolean onTouchEvent(MotionEvent event) {
        return settling || super.onTouchEvent(event);
      }

      @Override protected void onDetachedFromWindow() {
        stopAnimation();
        super.onDetachedFromWindow();
      }
    };
    frame.setBackgroundColor(Design.PAPER);
    spinner = new ProgressBar(activity);
    spinner.setIndeterminateTintList(ColorStateList.valueOf(Design.RED));
    spinner.setPadding(dp(8), dp(8), dp(8), dp(8));
    spinner.setBackground(Ui.shape(activity, Design.PAPER, Design.BORDER, 24));
    spinner.setElevation(dp(3));
    spinner.setVisibility(View.INVISIBLE);
    spinner.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    current = page(section, false);
    frame.addView(spinner, new FrameLayout.LayoutParams(dp(42), dp(42), Gravity.TOP | Gravity.CENTER_HORIZONTAL));
    View create = CommunityScreen.createButton(activity);
    FrameLayout.LayoutParams createSize = new FrameLayout.LayoutParams(dp(54), dp(54), Gravity.BOTTOM | Gravity.END);
    createSize.setMargins(0, 0, dp(18), dp(15));
    frame.addView(create, createSize);
  }

  static boolean isSection(String route) {
    return indexOf(route) >= 0;
  }

  private static int indexOf(String route) {
    for (int i = 0; i < SECTIONS.size(); i++) {
      if (SECTIONS.get(i).route().equals(route)) return i;
    }
    return -1;
  }

  private int dp(int amount) { return Ui.dp(activity, amount); }

  Page current() { return current; }

  void bindTabs(LinearLayout header) {
    FrameLayout strip = new FrameLayout(activity);
    strip.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
    tabRow = Ui.row(activity);
    tabLabels.clear();
    for (int i = 0; i < SECTIONS.size(); i++) {
      int target = i;
      TextView tab = Ui.text(activity, SECTIONS.get(i).label(), 15, Design.MUTED);
      tab.setGravity(Gravity.CENTER);
      tab.setFocusable(true);
      tab.setBackground(Ui.ripple(activity, Design.BAR, 0, 8));
      tab.setOnClickListener(view -> select(target));
      tabRow.addView(tab, new LinearLayout.LayoutParams(0, -1, 1));
      tabLabels.add(tab);
    }
    strip.addView(tabRow, new FrameLayout.LayoutParams(-1, -1));
    underline = new View(activity);
    underline.setBackground(Ui.shape(activity, Design.RED, 0, 2));
    strip.addView(underline, new FrameLayout.LayoutParams(dp(18), dp(3), Gravity.BOTTOM | Gravity.START));
    tabRow.addOnLayoutChangeListener((view, l, t, r, b, ol, ot, or, ob) -> drawUnderline());
    header.addView(strip, new LinearLayout.LayoutParams(-1, dp(40)));
    updateTabs();
  }

  private void updateTabs() {
    for (int i = 0; i < tabLabels.size(); i++) {
      boolean selected = index == i;
      TextView label = tabLabels.get(i);
      label.setSelected(selected);
      label.setTypeface(Design.body(), selected ? Typeface.BOLD : Typeface.NORMAL);
      label.setTextColor(selected ? Design.RED : Design.MUTED);
    }
    drawUnderline();
  }

  private void drawUnderline() {
    if (underline == null) return;
    float destination = candidate < 0 ? index : candidate;
    float position = index + Math.abs(fraction) * (destination - index);
    float tabWidth = tabRow.getWidth() / (float) SECTIONS.size();
    underline.setTranslationX((position + .5f) * tabWidth - dp(9));
  }

  private Page page(String section, boolean preview) {
    RefreshScrollView scroll = new RefreshScrollView(activity, spinner,
        () -> !activity.isPageLoading() && !settling, activity::refreshCurrent, this);
    scroll.setFillViewport(true);
    LinearLayout content = Ui.col(activity);
    content.setBackgroundColor(Design.PAPER);
    content.setPadding(dp(14), dp(15), dp(14), dp(76));
    scroll.addView(content);
    Runnable update = preview ? CommunityScreen.buildFeed(activity, content, section, true) : null;
    if (preview) scroll.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    frame.addView(scroll, 0, new FrameLayout.LayoutParams(-1, -1));
    if (preview) activity.restoreCommunityScroll(scroll, section);
    return new Page(scroll, content, update);
  }

  @Override public void move(float dx) {
    if (settling || activity.operationInFlight() || frame.getWidth() == 0) return;
    dragging = true;
    float progress = dragProgress(index, dx, frame.getWidth());
    int next = neighbor(index, progress);
    if (next != candidate) prepare(next);
    draw(progress);
  }

  @Override public void end(float dx, boolean commit) {
    if (settling) return;
    if (commit) move(dx);
    dragging = false;
    settle(commit && candidate >= 0 ? Math.signum(candidate - index) : 0);
  }

  private void select(int next) {
    if (next == index || settling || dragging || activity.operationInFlight()) return;
    prepare(next);
    settle(Math.signum(next - index));
  }

  private void prepare(int next) {
    if (incoming != null) frame.removeView(incoming.scroll());
    candidate = next;
    incoming = next < 0 ? null : page(SECTIONS.get(next).route(), true);
  }

  private void draw(float progress) {
    fraction = progress;
    float width = frame.getWidth();
    current.scroll().setTranslationX(-progress * width);
    if (incoming != null) {
      float direction = Math.signum(candidate - index);
      incoming.scroll().setTranslationX((direction - progress) * width);
    }
    drawUnderline();
  }

  private void settle(float target) {
    if (incoming == null) return;
    settling = true;
    if (target == fraction || !ValueAnimator.areAnimatorsEnabled()) {
      draw(target);
      frame.post(() -> {
        if (frame.isAttachedToWindow() && settling) complete(target != 0);
      });
      return;
    }
    animation = ValueAnimator.ofFloat(fraction, target);
    animation.setDuration(SETTLE_MS);
    animation.setInterpolator(new DecelerateInterpolator(1.7f));
    animation.addUpdateListener(value -> draw((float) value.getAnimatedValue()));
    animation.addListener(new AnimatorListenerAdapter() {
      @Override public void onAnimationEnd(Animator value) {
        if (frame.isAttachedToWindow()) complete(target != 0);
      }
    });
    animation.start();
  }

  private void stopAnimation() {
    if (animation == null) return;
    animation.removeAllListeners();
    animation.cancel();
    animation = null;
  }

  void cancelSwipe() {
    if (!dragging && !settling) return;
    stopAnimation();
    dragging = false;
    settling = false;
    prepare(-1);
    draw(0);
  }

  private void complete(boolean commit) {
    settling = false;
    animation = null;
    if (commit) {
      frame.removeView(current.scroll());
      current = incoming;
      index = candidate;
      current.scroll().setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
    } else {
      frame.removeView(incoming.scroll());
    }
    incoming = null;
    candidate = -1;
    draw(0);
    updateTabs();
    if (commit) activity.commitCommunityPage(this, SECTIONS.get(index).route(), current);
    frame.post(Ui::recheckImages);
  }

  static float dragProgress(int index, float dx, int width) {
    if (width <= 0) return 0;
    float progress = Math.max(-1, Math.min(1, -dx / width));
    return Math.max(-index, Math.min(SECTIONS.size() - 1 - index, progress));
  }

  static int neighbor(int index, float progress) {
    if (progress == 0) return -1;
    int next = index + (progress > 0 ? 1 : -1);
    return next >= 0 && next < SECTIONS.size() ? next : -1;
  }
}
