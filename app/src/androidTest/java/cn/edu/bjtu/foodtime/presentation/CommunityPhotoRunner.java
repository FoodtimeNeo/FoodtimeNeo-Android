package cn.edu.bjtu.foodtime.presentation;

import android.app.Dialog;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.HorizontalScrollView;
import cn.edu.bjtu.foodtime.BuildConfig;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Loopback media fixture, production transport and real native touch dispatch. */
public final class CommunityPhotoRunner extends Instrumentation {
  private String postId;
  private MainActivity activity;
  private View decor;
  private Dialog dialog;
  private int checks;

  @Override public void onCreate(Bundle args) {
    super.onCreate(args);
    postId = args.getString("postId", "");
    start();
  }

  private void ui(Runnable action) {
    var failure = new AtomicReference<Throwable>();
    runOnMainSync(() -> {
      try { action.run(); } catch (Throwable error) { failure.set(error); }
    });
    if (failure.get() != null) throw new AssertionError(failure.get());
  }

  private void await(String label, BooleanSupplier condition) {
    long end = SystemClock.uptimeMillis() + 15000;
    while (SystemClock.uptimeMillis() < end) {
      boolean[] ready = {false};
      ui(() -> ready[0] = condition.getAsBoolean());
      if (ready[0]) return;
      SystemClock.sleep(60);
    }
    throw new AssertionError("Timeout: " + label);
  }

  private void check(boolean value, String label) {
    if (!value) throw new AssertionError(label);
    checks++;
  }

  private static <T extends View> T find(View root, Class<T> type) {
    if (type.isInstance(root)) return type.cast(root);
    if (root instanceof ViewGroup group) {
      for (int i = 0; i < group.getChildCount(); i++) {
        T match = find(group.getChildAt(i), type);
        if (match != null) return match;
      }
    }
    return null;
  }

  private static Object field(Object target, String name) throws ReflectiveOperationException {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return field.get(target);
  }

  private static TextView findText(View root, String text) {
    if (root instanceof TextView label && text.contentEquals(label.getText())) return label;
    if (root instanceof ViewGroup group) {
      for (int i = 0; i < group.getChildCount(); i++) {
        TextView match = findText(group.getChildAt(i), text);
        if (match != null) return match;
      }
    }
    return null;
  }

  private ImageView image(String layer) { return find(decor.findViewWithTag(layer), ImageView.class); }

  private int width(String layer) {
    ImageView photo = image(layer);
    return photo != null && photo.getDrawable() instanceof BitmapDrawable drawable ? drawable.getBitmap().getWidth() : 0;
  }

  private float matrixValue(int index) {
    float[] values = new float[9];
    image("photo-thumb").getImageMatrix().getValues(values);
    return values[index];
  }

  private float scale() { return matrixValue(Matrix.MSCALE_X); }

  private void sendTouch(long start, int action, float... coordinates) {
    int[] origin = new int[2];
    ui(() -> {
      if (!decor.hasWindowFocus()) throw new AssertionError("Photo window lost focus to another window");
      decor.getLocationOnScreen(origin);
    });
    int count = coordinates.length / 2;
    MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[count];
    MotionEvent.PointerCoords[] points = new MotionEvent.PointerCoords[count];
    for (int i = 0; i < count; i++) {
      properties[i] = new MotionEvent.PointerProperties();
      properties[i].id = i;
      properties[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
      points[i] = new MotionEvent.PointerCoords();
      points[i].x = coordinates[2 * i] + origin[0];
      points[i].y = coordinates[2 * i + 1] + origin[1];
      points[i].pressure = points[i].size = 1;
    }
    MotionEvent event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action, count, properties,
        points, 0, 0, 1, 1, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0);
    sendPointerSync(event);
    event.recycle();
  }

  private void tap(float x, float y) {
    long start = SystemClock.uptimeMillis();
    sendTouch(start, MotionEvent.ACTION_DOWN, x, y);
    SystemClock.sleep(28);
    sendTouch(start, MotionEvent.ACTION_UP, x, y);
  }

  private void touch(View target, long start, int action, float x, float y) {
    int[] origin = new int[2];
    ui(() -> {
      if (!target.hasWindowFocus()) throw new AssertionError("Post window lost focus to another window");
      target.getLocationOnScreen(origin);
    });
    MotionEvent event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action, x+origin[0], y+origin[1], 0);
    event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
    sendPointerSync(event);
    event.recycle();
  }

  private void inlineSwipe(CommunityGallery gallery) throws Exception {
    HorizontalScrollView viewport = (HorizontalScrollView) field(gallery, "viewport");
    await("inline width", () -> viewport.getWidth() > 0);
    waitForIdleSync();
    float width = viewport.getWidth(), y = viewport.getHeight() / 2f;
    long start = SystemClock.uptimeMillis();
    touch(viewport, start, MotionEvent.ACTION_DOWN, width * .8f, y);
    SystemClock.sleep(30);
    touch(viewport, start, MotionEvent.ACTION_MOVE, width * .65f, y);
    SystemClock.sleep(30);
    touch(viewport, start, MotionEvent.ACTION_MOVE, width * .45f, y);
    check(viewport.getScrollX() > width * .1f, "Inline image follows the finger before release: scroll="+viewport.getScrollX()+", viewport="+width+", strip="+viewport.getChildAt(0).getWidth()+", height="+viewport.getHeight());
    int beforeResize=viewport.getScrollX();
    ui(() -> viewport.layout(viewport.getLeft(), viewport.getTop(), viewport.getRight(), viewport.getBottom()+12));
    waitForIdleSync();
    check(viewport.getScrollX() >= beforeResize - 2, "Height-only image layout does not reset an active swipe");
    var inlineFrames=new java.util.ArrayList<Integer>();
    Runnable sampleInline=new Runnable(){
      @Override public void run(){
        inlineFrames.add(viewport.getScrollX());
        if(inlineFrames.size()<90)viewport.postOnAnimation(this);
      }
    };
    int released=viewport.getScrollX();
    ui(() -> viewport.postOnAnimation(sampleInline));
    touch(viewport, start, MotionEvent.ACTION_UP, width * .3f, y);
    await("inline settled second page", () -> Math.abs(viewport.getScrollX() - viewport.getWidth()) < 2);
    ui(() -> {
      viewport.removeCallbacks(sampleInline);
      check(inlineFrames.stream().filter(value -> value > released + 1 && value < width - 1).distinct().count() >= 2,
          "Inline release renders multiple intermediate frames: "+inlineFrames);
    });
    check(((TextView) field(gallery,"dots")).getContentDescription().toString().startsWith("第2张"), "Inline counter follows settled page");
  }

  private int page() {
    try { return (int) field(field(find(activity.content, CommunityGallery.class), "viewer"), "page"); }
    catch (ReflectiveOperationException error) { throw new AssertionError(error); }
  }

  private void smoothSwipe(FrameLayout canvas) throws Exception {
    float width = canvas.getWidth(), y = canvas.getHeight() / 2f;
    long start = SystemClock.uptimeMillis();
    sendTouch(start, MotionEvent.ACTION_DOWN, width * .8f, y);
    sendTouch(start, MotionEvent.ACTION_MOVE, width * .6f, y);
    check(decor.findViewWithTag("photo-thumb").getTranslationX() < -width * .15f, "Full-screen image follows the finger");
    SystemClock.sleep(70);
    screenshot("android-swipe-dragging");
    sendTouch(start, MotionEvent.ACTION_MOVE, width * .3f, y);
    View outgoing = decor.findViewWithTag("photo-thumb");
    float released = outgoing.getTranslationX();
    var swipeFrames = new java.util.ArrayList<Float>();
    Runnable sampleSwipe = new Runnable() {
      @Override public void run() {
        swipeFrames.add(outgoing.getTranslationX());
        if (swipeFrames.size() < 90) canvas.postOnAnimation(this);
      }
    };
    ui(() -> canvas.postOnAnimation(sampleSwipe));
    sendTouch(start, MotionEvent.ACTION_UP, width * .3f, y);
    await("full-screen second page", () -> page() == 1);
    ui(() -> {
      canvas.removeCallbacks(sampleSwipe);
      check(swipeFrames.stream().filter(value -> value < released - 1 && value > -width + 1)
          .map(Math::round).distinct().count() >= 2,
          "Release renders multiple intermediate frames before settling: " + swipeFrames);
    });
    check(Math.abs(decor.findViewWithTag("photo-thumb").getTranslationX()) < 1, "Incoming image is aligned after settling");

    start = SystemClock.uptimeMillis();
    sendTouch(start, MotionEvent.ACTION_DOWN, width * .5f, y);
    sendTouch(start, MotionEvent.ACTION_MOVE, width * .55f, y);
    sendTouch(start, MotionEvent.ACTION_UP, width * .55f, y);
    await("short swipe rebound", () -> Math.abs(decor.findViewWithTag("photo-thumb").getTranslationX()) < 1);
    check(page() == 1, "Short swipe rebounds without turning a page");

    start = SystemClock.uptimeMillis();
    sendTouch(start, MotionEvent.ACTION_DOWN, width * .3f, y);
    sendTouch(start, MotionEvent.ACTION_MOVE, width * .8f, y);
    sendTouch(start, MotionEvent.ACTION_CANCEL, width * .8f, y);
    await("cancel rebound", () -> Math.abs(decor.findViewWithTag("photo-thumb").getTranslationX()) < 1);
    check(page() == 1, "Cancelled swipe never changes the page");

    start = SystemClock.uptimeMillis();
    sendTouch(start, MotionEvent.ACTION_DOWN, width * .7f, y);
    sendTouch(start, MotionEvent.ACTION_MOVE, width * .3f, y);
    check(Math.abs(decor.findViewWithTag("photo-thumb").getTranslationX()) < width * .15f, "Last page provides edge resistance");
    sendTouch(start, MotionEvent.ACTION_UP, width * .3f, y);
    await("edge rebound", () -> Math.abs(decor.findViewWithTag("photo-thumb").getTranslationX()) < 1);
    check(page() == 1, "Cannot swipe beyond last page");
    ui(() -> check(canvas.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, null),
        "Accessibility can return to the previous image without a visible arrow"));
    await("return to first page", () -> page() == 0);
    ui(() -> check(!canvas.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, null),
        "Accessibility cannot navigate before the first image"));
    ui(() -> check(canvas.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, null),
        "Accessibility can advance to the next image"));
    await("accessibility second page", () -> page() == 1);
    ui(() -> check(!canvas.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, null),
        "Accessibility cannot navigate after the last image"));
    ui(() -> canvas.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, null));
    await("accessibility return", () -> page() == 0);
  }

  private static Object uncheckedField(Object target, String name) {
    try { return field(target,name); } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
  }

  private void screenshot(String name) throws Exception {
    waitForIdleSync();
    ui(() -> {
      if (!decor.hasWindowFocus()) throw new AssertionError("Screenshot rejected: photo window is obscured");
    });
    Bitmap shot = getUiAutomation().takeScreenshot();
    if (shot == null) throw new AssertionError("Screenshot unavailable");
    try (var output = new FileOutputStream(new File(getTargetContext().getFilesDir(), name + ".png"))) {
      shot.compress(Bitmap.CompressFormat.PNG, 100, output);
    } finally { shot.recycle(); }
  }

  private void footerControls() throws Exception {
    ui(() -> {
      TextView[] controls = {decor.findViewWithTag("photo-action-share"),
          decor.findViewWithTag("photo-action-comment"), decor.findViewWithTag("photo-action-like")};
      for (TextView control : controls) {
        var icon = control.getCompoundDrawables()[1];
        check(control.getHeight() >= Ui.dp(activity,72)
            && control.getTextSize() >= 15.9f * activity.getResources().getDisplayMetrics().scaledDensity
            && icon != null && icon.getBounds().width() >= Ui.dp(activity,24),
            "Footer action has a larger label, icon and touch area: " + control.getContentDescription());
      }
      int[] first = new int[2], second = new int[2];
      controls[0].getLocationInWindow(first); controls[1].getLocationInWindow(second);
      check(second[0] - first[0] - controls[0].getWidth() >= Ui.dp(activity,8), "Larger footer controls retain separate touch areas");
      TextView source = activity.root.findViewWithTag("community-post-like");
      String original = source.getText().toString();
      source.setText("♥ 12");
      check(controls[2].isSelected() && controls[2].getText().toString().equals("已赞 12"),
          "Larger heart mirrors the original like state and count");
      source.setText(original);
      check(!controls[2].isSelected(), "Heart returns to the original unliked state");
    });
    screenshot("android-footer-actions");
  }

  @Override public void onStart() {
    Bundle result = new Bundle();
    try {
      boolean isolated = (BuildConfig.APPLICATION_ID.endsWith(".photocheck") && BuildConfig.API_BASE_URL.equals("http://127.0.0.1:18081/api/v1/")) ||
          (BuildConfig.APPLICATION_ID.endsWith(".smoothswipecheck") && BuildConfig.API_BASE_URL.equals("http://127.0.0.1:18084/api/v1/")) ||
          (BuildConfig.APPLICATION_ID.endsWith(".interactionreview") && BuildConfig.API_BASE_URL.equals("http://127.0.0.1:18094/api/v1/"));
      if (!BuildConfig.DEBUG || !isolated) {
        throw new AssertionError("Only the isolated loopback photo build is allowed");
      }
      activity = (MainActivity) startActivitySync(new Intent(getTargetContext(), MainActivity.class)
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      await("local session", () -> activity.repo.currentUser() != null && !activity.isPageLoading());
      ui(() -> { activity.selectTab("community"); activity.go("community-detail", postId); });
      await("gallery", () -> find(activity.content, CommunityGallery.class) != null);
      var gallery = find(activity.content, CommunityGallery.class);
      check(findText(gallery, "‹") == null && findText(gallery, "›") == null,
          "Inline gallery has no redundant arrow buttons");
      inlineSwipe(gallery);
      ui(() -> {
        try {
          var strip = (ViewGroup) field(gallery, "strip");
          strip.getChildAt(0).performClick();
          dialog = (Dialog) field(field(gallery, "viewer"), "dialog");
          decor = dialog.getWindow().getDecorView();
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
      });
      await("480px preview", () -> width("photo-thumb") == 480);
      await("photo window focus", () -> decor.hasWindowFocus());
      footerControls();
      check(decor.findViewWithTag("photo-full") == null, "Opening the viewer did not create an original request");
      screenshot("android-expanded");
      View toggle = decor.findViewWithTag("photo-context-toggle");
      ui(toggle::performClick);
      check(decor.findViewWithTag("photo-context").getVisibility() == View.GONE, "Context collapses");
      check("展开帖子信息".contentEquals(toggle.getContentDescription()), "Toggle changes to expand");
      screenshot("android-collapsed");
      FrameLayout canvas = decor.findViewWithTag("photo-canvas");
      check(findText(decor, "‹") == null && findText(decor, "›") == null,
          "Touch viewer has no persistent side arrows");
      smoothSwipe(canvas);
      float x = canvas.getWidth() / 2f, y = canvas.getHeight() / 2f;
      float fitted = scale();
      var zoomFrames = new java.util.ArrayList<Float>();
      Runnable sampleZoom = new Runnable() {
        @Override public void run() {
          zoomFrames.add(scale() / fitted);
          if (zoomFrames.size() < 90) canvas.postOnAnimation(this);
        }
      };
      ui(() -> canvas.postOnAnimation(sampleZoom));
      tap(x, y); SystemClock.sleep(60); tap(x, y);
      await("double tap zoom", () -> scale() > fitted * 2.45f);
      ui(() -> {
        canvas.removeCallbacks(sampleZoom);
        check(zoomFrames.stream().filter(value -> value > 1.02f && value < 2.48f)
            .map(value -> Math.round(value * 100)).distinct().count() >= 2,
            "Double-tap has multiple intermediate frames instead of jumping: " + zoomFrames);
      });
      check(dialog.isShowing(), "Double tap does not close the dialog");
      float beforeDrag = matrixValue(Matrix.MTRANS_X);
      long drag = SystemClock.uptimeMillis();
      sendTouch(drag, MotionEvent.ACTION_DOWN, x, y);
      sendTouch(drag, MotionEvent.ACTION_MOVE, x + 90, y);
      sendTouch(drag, MotionEvent.ACTION_MOVE, x + 180, y);
      sendTouch(drag, MotionEvent.ACTION_UP, x + 180, y);
      check(matrixValue(Matrix.MTRANS_X) > beforeDrag + 40, "Drag pans the enlarged image without moving controls");
      screenshot("android-zoomed");
      SystemClock.sleep(380);
      tap(x, y); SystemClock.sleep(60); tap(x, y);
      await("double tap reset", () -> Math.abs(scale() - fitted) < 0.01f);
      SystemClock.sleep(380);
      float top = canvas.getHeight() * .22f;
      float span = canvas.getWidth();
      long start = SystemClock.uptimeMillis();
      sendTouch(start, MotionEvent.ACTION_DOWN, x - .08f * span, top);
      sendTouch(start, MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT), x - .08f * span, top, x + .08f * span, top);
      for (float halfSpan : new float[]{.22f, .31f, .42f}) {
        SystemClock.sleep(22);
        sendTouch(start, MotionEvent.ACTION_MOVE, x - halfSpan * span, top, x + halfSpan * span, top);
      }
      sendTouch(start, MotionEvent.ACTION_POINTER_UP | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT), x - .42f * span, top, x + .42f * span, top);
      sendTouch(start, MotionEvent.ACTION_UP, x - .42f * span, top);
      await("pinch zoom", () -> scale() > fitted * 1.1f);
      check(dialog.isShowing(), "Pinch outside original photo bounds uses the full viewport");
      TextView original = decor.findViewWithTag("photo-original");
      check(original.isEnabled() && original.isShown(), "Original control stays above zoomed image");
      int[] buttonPosition = new int[2];
      ui(() -> original.getLocationInWindow(buttonPosition));
      tap(buttonPosition[0] + original.getWidth() / 2f, buttonPosition[1] + original.getHeight() / 2f);
      await("original loaded", () -> width("photo-full") == 1600);
      check(original.getText().toString().equals("已加载原图"), "Only explicit click enables full resolution");
      tap(x, y);
      await("single tap close", () -> !dialog.isShowing());
      check(activity.route().kind().equals("community-detail"), "Closing returns to same post");
      ui(() -> {
        try {
          ((ViewGroup) field(gallery, "strip")).getChildAt(0).performClick();
          dialog = (Dialog) field(field(gallery, "viewer"), "dialog");
          decor = dialog.getWindow().getDecorView();
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
      });
      await("comment window focus", () -> decor.hasWindowFocus());
      int[] commentPosition = new int[2];
      View commentAction = decor.findViewWithTag("photo-action-comment");
      ui(() -> commentAction.getLocationInWindow(commentPosition));
      tap(commentPosition[0]+commentAction.getWidth()/2f,commentPosition[1]+commentAction.getHeight()/2f);
      await("comment closes photo", () -> !dialog.isShowing());
      check(!dialog.isShowing() && activity.route().kind().equals("community-detail"),
          "Larger comment control closes the image and keeps the post route");
      result.putString("stream", "Community photo device PASS: " + checks + " assertions, including double-tap, pinch and single-tap waits\n");
      finish(-1, result);
    } catch (Throwable error) {
      result.putString("stream", "Community photo device FAIL: " + android.util.Log.getStackTraceString(error));
      finish(1, result);
    } finally {
      if (dialog != null) ui(dialog::dismiss);
      if (activity != null) ui(activity::finish);
    }
  }
}
