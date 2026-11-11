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
    int count = coordinates.length / 2;
    MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[count];
    MotionEvent.PointerCoords[] points = new MotionEvent.PointerCoords[count];
    for (int i = 0; i < count; i++) {
      properties[i] = new MotionEvent.PointerProperties();
      properties[i].id = i;
      properties[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
      points[i] = new MotionEvent.PointerCoords();
      points[i].x = coordinates[2 * i];
      points[i].y = coordinates[2 * i + 1];
      points[i].pressure = points[i].size = 1;
    }
    MotionEvent event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action, count, properties,
        points, 0, 0, 1, 1, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0);
    ui(() -> decor.dispatchTouchEvent(event));
    event.recycle();
  }

  private void tap(float x, float y) {
    long start = SystemClock.uptimeMillis();
    sendTouch(start, MotionEvent.ACTION_DOWN, x, y);
    SystemClock.sleep(28);
    sendTouch(start, MotionEvent.ACTION_UP, x, y);
  }

  private void touch(View target, long start, int action, float x, float y) {
    MotionEvent event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action, x, y, 0);
    ui(() -> target.dispatchTouchEvent(event));
    event.recycle();
  }

  private void inlineSwipe(CommunityGallery gallery) throws Exception {
    HorizontalScrollView viewport = (HorizontalScrollView) field(gallery, "viewport");
    await("inline width", () -> viewport.getWidth() > 0);
    float width = viewport.getWidth(), y = viewport.getHeight() / 2f;
    long start = SystemClock.uptimeMillis();
    touch(viewport, start, MotionEvent.ACTION_DOWN, width * .8f, y);
    SystemClock.sleep(30);
    touch(viewport, start, MotionEvent.ACTION_MOVE, width * .65f, y);
    SystemClock.sleep(30);
    touch(viewport, start, MotionEvent.ACTION_MOVE, width * .45f, y);
    check(viewport.getScrollX() > width * .1f, "Inline image follows the finger before release: scroll="+viewport.getScrollX()+", viewport="+width+", strip="+viewport.getChildAt(0).getWidth()+", height="+viewport.getHeight());
    touch(viewport, start, MotionEvent.ACTION_UP, width * .3f, y);
    check(viewport.getScrollX() < width - 1, "Inline release starts an animation instead of jumping");
    await("inline settled second page", () -> Math.abs(viewport.getScrollX() - viewport.getWidth()) < 2);
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
    sendTouch(start, MotionEvent.ACTION_UP, width * .3f, y);
    check(page() == 0, "Full-screen outgoing image stays until animation completes");
    float released = decor.findViewWithTag("photo-thumb").getTranslationX();
    SystemClock.sleep(60);
    check(page() == 0 && decor.findViewWithTag("photo-thumb").getTranslationX() < released - 1, "Release has an intermediate animation frame before settling");
    await("full-screen second page", () -> page() == 1);
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
    var viewer = field(find(activity.content, CommunityGallery.class), "viewer");
    ui(() -> ((TextView) uncheckedField(viewer,"previous")).performClick());
    await("return to first page", () -> page() == 0);
  }

  private static Object uncheckedField(Object target, String name) {
    try { return field(target,name); } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
  }

  private void screenshot(String name) throws Exception {
    waitForIdleSync();
    Bitmap shot = getUiAutomation().takeScreenshot();
    if (shot == null) throw new AssertionError("Screenshot unavailable");
    try (var output = new FileOutputStream(new File(getTargetContext().getFilesDir(), name + ".png"))) {
      shot.compress(Bitmap.CompressFormat.PNG, 100, output);
    } finally { shot.recycle(); }
  }

  @Override public void onStart() {
    Bundle result = new Bundle();
    try {
      boolean isolated = (BuildConfig.APPLICATION_ID.endsWith(".photocheck") && BuildConfig.API_BASE_URL.equals("http://127.0.0.1:18081/api/v1/")) ||
          (BuildConfig.APPLICATION_ID.endsWith(".smoothswipecheck") && BuildConfig.API_BASE_URL.equals("http://127.0.0.1:18084/api/v1/"));
      if (!BuildConfig.DEBUG || !isolated) {
        throw new AssertionError("Only the isolated loopback photo build is allowed");
      }
      activity = (MainActivity) startActivitySync(new Intent(getTargetContext(), MainActivity.class)
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      await("local session", () -> activity.repo.currentUser() != null && !activity.isPageLoading());
      ui(() -> { activity.selectTab("community"); activity.go("community-detail", postId); });
      await("gallery", () -> find(activity.content, CommunityGallery.class) != null);
      var gallery = find(activity.content, CommunityGallery.class);
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
      check(decor.findViewWithTag("photo-full") == null, "Opening the viewer did not create an original request");
      screenshot("android-expanded");
      View toggle = decor.findViewWithTag("photo-context-toggle");
      ui(toggle::performClick);
      check(decor.findViewWithTag("photo-context").getVisibility() == View.GONE, "Context collapses");
      check("展开帖子信息".contentEquals(toggle.getContentDescription()), "Toggle changes to expand");
      screenshot("android-collapsed");
      FrameLayout canvas = decor.findViewWithTag("photo-canvas");
      smoothSwipe(canvas);
      float x = canvas.getWidth() / 2f, y = canvas.getHeight() / 2f;
      float fitted = scale();
      tap(x, y); SystemClock.sleep(60); tap(x, y);
      await("double tap zoom", () -> scale() > fitted * 2);
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
