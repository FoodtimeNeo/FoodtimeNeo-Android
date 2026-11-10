package cn.edu.bjtu.foodtime.presentation;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import cn.edu.bjtu.foodtime.domain.model.Models.CommunityPost;
import cn.edu.bjtu.foodtime.domain.model.CommunityText;
import java.util.List;
import java.util.function.IntConsumer;

/** The viewport owns gestures and zoom. Chrome is laid out separately above it. */
final class CommunityPhotoDialog {
  record PostActions(Runnable share, Runnable comment, TextView like) {}

  private final MainActivity activity;
  private final List<String> images;
  private final IntConsumer onPageChanged;
  private final boolean thumbnailFirst;
  private final Dialog dialog;
  private final PhotoCanvas canvas;
  private final TextView original, counter, previous, next;
  private FrameLayout originalLayer;
  private int page;

  CommunityPhotoDialog(MainActivity activity, List<String> images, int index, boolean thumbnailFirst,
      CommunityPost post, PostActions actions, IntConsumer onPageChanged) {
    this.activity = activity;
    this.images = images;
    this.thumbnailFirst = thumbnailFirst;
    this.onPageChanged = onPageChanged;
    dialog = new Dialog(activity, android.R.style.Theme_Material_NoActionBar_Fullscreen);
    FrameLayout screen = new FrameLayout(activity);
    screen.setBackgroundColor(Color.BLACK);
    canvas = new PhotoCanvas(post != null);
    screen.addView(canvas, new FrameLayout.LayoutParams(-1, -1));

    FrameLayout top = new FrameLayout(activity);
    top.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
        new int[]{0xdd000000, Color.TRANSPARENT}));
    TextView close = control("×", "关闭看图", dialog::dismiss);
    close.setTextSize(25);
    top.addView(close, position(Gravity.START | Gravity.TOP));
    original = control("加载原图", "加载原图", this::loadOriginal);
    original.setTag("photo-original");
    top.addView(original, position(Gravity.TOP | Gravity.CENTER_HORIZONTAL));
    counter = Ui.text(activity, "", 14, Color.WHITE);
    counter.setGravity(Gravity.CENTER);
    counter.setMinHeight(dp(48));
    counter.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    LinearLayout topEnd = Ui.row(activity);
    topEnd.setGravity(Gravity.CENTER_VERTICAL);
    topEnd.addView(counter);
    TextView options = control("⋮", "看图选项", () -> {});
    options.setTextSize(22);
    options.setOnClickListener(view -> {
      PopupMenu menu = new PopupMenu(activity, options);
      menu.getMenu().add("放大").setOnMenuItemClickListener(item -> {
        canvas.stepZoom(1.5f);
        return true;
      });
      menu.getMenu().add("缩小").setOnMenuItemClickListener(item -> {
        canvas.stepZoom(1 / 1.5f);
        return true;
      });
      menu.show();
    });
    topEnd.addView(options);
    top.addView(topEnd, position(Gravity.END | Gravity.TOP));
    screen.addView(top, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

    previous = control("‹", "上一张图片", () -> changePage(page - 1));
    next = control("›", "下一张图片", () -> changePage(page + 1));
    for (TextView arrow : List.of(previous, next)) arrow.setTextSize(25);
    screen.addView(previous, position(Gravity.START | Gravity.CENTER_VERTICAL));
    screen.addView(next, position(Gravity.END | Gravity.CENTER_VERTICAL));
    FrameLayout bottom = post == null ? null : footer(post, actions);
    if (bottom != null) screen.addView(bottom, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

    screen.setOnApplyWindowInsetsListener((view, insets) -> {
      if (Build.VERSION.SDK_INT >= 30) {
        var safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
        top.setPadding(safe.left, safe.top, safe.right, 0);
        if (bottom != null) bottom.setPadding(safe.left, 0, safe.right, safe.bottom);
        var left = (FrameLayout.LayoutParams) previous.getLayoutParams();
        var right = (FrameLayout.LayoutParams) next.getLayoutParams();
        left.leftMargin = dp(10) + safe.left;
        right.rightMargin = dp(10) + safe.right;
        previous.setLayoutParams(left);
        next.setLayoutParams(right);
      }
      return insets;
    });
    dialog.setContentView(screen);
    showPage(index);
  }

  void show() {
    dialog.show();
    dialog.getWindow().setLayout(-1, -1);
  }
  void dismiss() { dialog.dismiss(); }

  private int dp(int value) { return Ui.dp(activity, value); }

  private FrameLayout.LayoutParams position(int gravity) {
    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-2, -2, gravity);
    params.setMargins(dp(10), dp(8), dp(10), dp(8));
    return params;
  }

  private TextView control(String label, String description, Runnable action) {
    TextView button = Ui.button(activity, label, false, action);
    button.setTextColor(Color.WHITE);
    button.setMinWidth(dp(48));
    button.setMinHeight(dp(48));
    button.setGravity(Gravity.CENTER);
    button.setPadding(dp(12), 0, dp(12), 0);
    button.setContentDescription(description);
    button.setBackground(Ui.ripple(activity, 0xdd303030, Color.TRANSPARENT, 24));
    return button;
  }

  private FrameLayout footer(CommunityPost post, PostActions actions) {
    FrameLayout shell = new FrameLayout(activity);
    LinearLayout details = Ui.col(activity);
    details.setPadding(dp(16), dp(16), dp(16), dp(10));
    details.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
        new int[]{0x88000000, Color.BLACK}));
    details.setTag("photo-context");
    shell.addView(details, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

    String author = post.userDisplayName().isBlank() ? "同学" : post.userDisplayName();
    LinearLayout byline = Ui.row(activity);
    byline.setGravity(Gravity.CENTER_VERTICAL);
    byline.setPadding(0, 0, dp(58), 0);
    View avatar = Ui.image(activity, Ui.thumb(post.userAvatarUrl()),
        new String(Character.toChars(author.codePointAt(0))), false);
    avatar.setBackground(Ui.shape(activity, 0xff303030, Color.TRANSPARENT, 20));
    avatar.setClipToOutline(true);
    avatar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    byline.addView(avatar, new LinearLayout.LayoutParams(dp(40), dp(40)));
    LinearLayout identity = Ui.col(activity);
    identity.setPadding(dp(10), 0, 0, 0);
    identity.addView(Ui.text(activity, author, 16, Color.WHITE));
    identity.addView(Ui.text(activity, CommunityText.publishedAt(post.createdAt()), 12, 0xffcccccc));
    byline.addView(identity, new LinearLayout.LayoutParams(0, -2, 1));
    details.addView(byline);

    TextView summary = Ui.text(activity, post.title() + " " + post.content(), 14, Color.WHITE);
    summary.setMaxLines(2);
    summary.setEllipsize(TextUtils.TruncateAt.END);
    summary.setPadding(0, dp(8), 0, 0);
    details.addView(summary);
    TextView postLink = control("查看帖子", "查看帖子", dialog::dismiss);
    postLink.setPadding(0, 0, dp(10), 0);
    postLink.setTextSize(14);
    postLink.setTextColor(0xffabd9f3);
    postLink.setBackground(Ui.ripple(activity, Color.TRANSPARENT, Color.TRANSPARENT, 8));
    details.addView(postLink, new LinearLayout.LayoutParams(-2, -2));
    if (actions != null) {
      LinearLayout row = Ui.row(activity);
      TextView share = control("↗ 分享", "分享帖子", actions.share());
      TextView comment = control("评论 " + post.commentCount(), "查看评论", () -> {
        dialog.dismiss();
        actions.comment().run();
      });
      TextView like = control(actions.like().getText().toString(), "点赞帖子", () -> {
        if (actions.like().isEnabled()) actions.like().performClick();
      });
      TextWatcher mirror = new TextWatcher() {
        @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence value, int start, int before, int count) {
          like.setText(value);
          like.setContentDescription(value);
        }
        @Override public void afterTextChanged(Editable value) {}
      };
      actions.like().addTextChangedListener(mirror);
      dialog.setOnDismissListener(unused -> actions.like().removeTextChangedListener(mirror));
      for (TextView action : List.of(share, comment, like)) {
        action.setTextSize(13);
        action.setPadding(dp(4), 0, dp(4), 0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        params.setMargins(dp(3), 0, dp(3), 0);
        row.addView(action, params);
      }
      details.addView(row);
    }

    TextView toggle = control("⌄", "收起帖子信息", () -> {});
    toggle.setTag("photo-context-toggle");
    toggle.setTextSize(23);
    shell.addView(toggle, position(Gravity.END | Gravity.TOP));
    toggle.setOnClickListener(view -> {
      boolean collapse = details.getVisibility() == View.VISIBLE;
      details.setVisibility(collapse ? View.GONE : View.VISIBLE);
      toggle.setText(collapse ? "⌃" : "⌄");
      toggle.setContentDescription(collapse ? "展开帖子信息" : "收起帖子信息");
    });
    return shell;
  }

  private void changePage(int index) {
    if (index < 0 || index >= images.size()) return;
    showPage(index);
    onPageChanged.accept(index);
  }

  private void showPage(int index) {
    page = index;
    canvas.removeAllViews();
    canvas.resetZoom();
    originalLayer = null;
    String url = images.get(index);
    String thumbnail = Ui.thumb(url);
    original.setVisibility(thumbnailFirst && !thumbnail.equals(fullUrl(url)) ? View.VISIBLE : View.GONE);
    setOriginalStatus("加载原图", true);
    counter.setText((index + 1) + "/" + images.size());
    previous.setVisibility(index > 0 ? View.VISIBLE : View.GONE);
    next.setVisibility(index + 1 < images.size() ? View.VISIBLE : View.GONE);
    addPhoto(thumbnailFirst ? thumbnail : url, false);
  }

  private static String fullUrl(String url) {
    return url.startsWith("/api/v1/media/") ? url.split("\\?", 2)[0] + "?size=full" : url;
  }

  private void setOriginalStatus(String label, boolean enabled) {
    original.setText(label);
    original.setContentDescription(label);
    original.setEnabled(enabled);
  }

  private void loadOriginal() {
    if (originalLayer != null) canvas.removeView(originalLayer);
    setOriginalStatus("原图加载中…", false);
    originalLayer = addPhoto(fullUrl(images.get(page)), true);
  }

  private FrameLayout addPhoto(String url, boolean full) {
    FrameLayout layer = new FrameLayout(activity);
    layer.setTag(full ? "photo-full" : "photo-thumb");
    ImageView image = new ImageView(activity);
    image.setScaleType(ImageView.ScaleType.MATRIX);
    image.setContentDescription("第 " + (page + 1) + " 张图片");
    layer.addView(image, new FrameLayout.LayoutParams(-1, -1));
    TextView placeholder = Ui.text(activity, "图片加载中…", 14, Color.WHITE);
    placeholder.setGravity(Gravity.CENTER);
    placeholder.setMinHeight(dp(48));
    if (!full) layer.addView(placeholder, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
    ImageLoader.bind(activity, layer, image, placeholder, url, bitmap -> {
      canvas.updateImages();
      if (full) setOriginalStatus("已加载原图", false);
    }, () -> {
      if (full) setOriginalStatus("重试加载原图", true);
    });
    canvas.addView(layer, new FrameLayout.LayoutParams(-1, -1));
    return layer;
  }

  private final class PhotoCanvas extends FrameLayout {
    private final ScaleGestureDetector pinch;
    private final GestureDetector taps;
    private final Matrix matrix = new Matrix();
    private final boolean hasPost;
    private float scale = 1, shiftX, shiftY, downX, downY, focusX, focusY;
    private boolean multiplePointers;

    PhotoCanvas(boolean hasPost) {
      super(activity);
      this.hasPost = hasPost;
      setTag("photo-canvas");
      pinch = new ScaleGestureDetector(activity, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
        @Override public boolean onScaleBegin(ScaleGestureDetector detector) {
          focusX = detector.getFocusX();
          focusY = detector.getFocusY();
          return true;
        }
        @Override public boolean onScale(ScaleGestureDetector detector) {
          zoom(scale * detector.getScaleFactor(), focusX, focusY, detector.getFocusX(), detector.getFocusY());
          focusX = detector.getFocusX();
          focusY = detector.getFocusY();
          return true;
        }
      });
      pinch.setQuickScaleEnabled(false);
      taps = new GestureDetector(activity, new GestureDetector.SimpleOnGestureListener() {
        @Override public boolean onDown(MotionEvent event) { return true; }
        @Override public boolean onSingleTapConfirmed(MotionEvent event) {
          return !multiplePointers && performClick();
        }
        @Override public boolean onDoubleTap(MotionEvent event) {
          if (multiplePointers) return false;
          if (scale > 1) resetZoom();
          else zoom(2.5f, event.getX(), event.getY(), event.getX(), event.getY());
          return true;
        }
        @Override public boolean onScroll(MotionEvent first, MotionEvent current, float dx, float dy) {
          if (!multiplePointers && scale > 1) {
            shiftX -= dx;
            shiftY -= dy;
            updateImages();
          }
          return true;
        }
      });
      taps.setIsLongpressEnabled(false);
    }

    @Override public boolean performClick() {
      super.performClick();
      dialog.dismiss();
      return true;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
      int action = event.getActionMasked();
      if (action == MotionEvent.ACTION_DOWN) {
        multiplePointers = false;
        downX = event.getX();
        downY = event.getY();
      } else if (action == MotionEvent.ACTION_POINTER_DOWN) {
        multiplePointers = true;
      }
      pinch.onTouchEvent(event);
      taps.onTouchEvent(event);
      if (action == MotionEvent.ACTION_UP && !multiplePointers && scale == 1) {
        float dx = event.getX() - downX;
        float dy = event.getY() - downY;
        if (Math.abs(dx) > dp(44) && Math.abs(dx) > 1.3f * Math.abs(dy)) changePage(page + (dx < 0 ? 1 : -1));
      }
      return true;
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
      super.onSizeChanged(width, height, oldWidth, oldHeight);
      updateImages();
    }

    void resetZoom() {
      scale = 1;
      shiftX = 0;
      shiftY = 0;
      updateImages();
    }

    private void zoom(float target, float fromX, float fromY, float toX, float toY) {
      float bounded = Math.max(1, Math.min(5, target));
      float ratio = bounded / scale;
      float centerX = getWidth() / 2f, centerY = getHeight() / 2f;
      shiftX = toX - centerX - (fromX - centerX - shiftX) * ratio;
      shiftY = toY - centerY - (fromY - centerY - shiftY) * ratio;
      scale = bounded;
      updateImages();
    }

    void stepZoom(float factor) {
      float cx = getWidth() / 2f, cy = getHeight() / 2f;
      zoom(scale * factor, cx, cy, cx, cy);
    }

    void updateImages() {
      ImageView active = null;
      for (int i = getChildCount() - 1; i >= 0 && active == null; i--) {
        ImageView image = imageAt(i);
        if (image.getDrawable() != null) active = image;
      }
      if (active == null || getWidth() == 0 || getHeight() == 0) return;
      int width = active.getDrawable().getIntrinsicWidth(), height = active.getDrawable().getIntrinsicHeight();
      float factor = fit(width, height);
      shiftX = limit(shiftX, width * factor - getWidth());
      shiftY = limit(shiftY, height * factor - getHeight());
      for (int i = 0; i < getChildCount(); i++) {
        ImageView image = imageAt(i);
        if (image.getDrawable() == null) continue;
        int w = image.getDrawable().getIntrinsicWidth(), h = image.getDrawable().getIntrinsicHeight();
        float fit = fit(w, h);
        matrix.setScale(fit, fit);
        matrix.postTranslate((getWidth() - w * fit) / 2 + shiftX, (getHeight() - h * fit) / 2 + shiftY);
        image.setImageMatrix(matrix);
      }
    }

    private ImageView imageAt(int index) {
      return (ImageView) ((FrameLayout) getChildAt(index)).getChildAt(0);
    }

    private float fit(int width, int height) {
      int margin = Math.min(dp(hasPost ? 166 : 58), getHeight() / 5);
      return Math.min((float) getWidth() / width, (float) (getHeight() - 2 * margin) / height) * scale;
    }

    private float limit(float offset, float overflow) {
      float maximum = Math.max(0, overflow / 2);
      return Math.max(-maximum, Math.min(maximum, offset));
    }
  }
}
