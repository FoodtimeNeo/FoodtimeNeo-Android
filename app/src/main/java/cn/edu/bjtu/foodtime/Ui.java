package cn.edu.bjtu.foodtime;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.io.*;

/** Small native View toolkit; no WebView, no blurred elevation or default Material colours. */
public final class Ui {
  public static int dp(Context c, float n) {
    return Math.round(n * c.getResources().getDisplayMetrics().density);
  }

  public static LinearLayout col(Context c) {
    LinearLayout l = new LinearLayout(c);
    l.setOrientation(LinearLayout.VERTICAL);
    return l;
  }

  public static LinearLayout row(Context c) {
    LinearLayout l = new LinearLayout(c);
    l.setOrientation(LinearLayout.HORIZONTAL);
    l.setGravity(Gravity.CENTER_VERTICAL);
    return l;
  }

  public static TextView text(Context c, String value, float size, int color) {
    TextView t = new TextView(c);
    t.setText(value);
    t.setTextSize(size);
    t.setTextColor(color);
    t.setTypeface(Design.body());
    t.setIncludeFontPadding(false);
    t.setLineSpacing(dp(c, 3), 1);
    return t;
  }

  public static TextView title(Context c, String value, float size) {
    TextView t = text(c, value, size, Design.INK);
    t.setTypeface(Design.title());
    return t;
  }

  public static TextView note(Context c, String value) {
    TextView t = text(c, value, 20, Design.MUTED);
    t.setTypeface(Design.note());
    return t;
  }

  public static void gap(LinearLayout l, int n) {
    View v = new View(l.getContext());
    l.addView(v, new LinearLayout.LayoutParams(1, dp(l.getContext(), n)));
  }

  public static GradientDrawable shape(Context c, int fill, int stroke, int radius) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(fill);
    d.setCornerRadius(dp(c, radius));
    if (stroke != 0) d.setStroke(dp(c, 1), stroke);
    return d;
  }

  public static RippleDrawable ripple(Context c, int fill, int stroke, int radius) {
    return new RippleDrawable(
        android.content.res.ColorStateList.valueOf(Design.SHADOW),
        shape(c, fill, stroke, radius),
        null);
  }

  public static LinearLayout card(LinearLayout parent) {
    Context c = parent.getContext();
    LinearLayout card = col(c);
    card.setPadding(dp(c, 16), dp(c, 16), dp(c, 16), dp(c, 16));
    card.setBackground(new PaperDrawable(c));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.bottomMargin = dp(c, 20);
    parent.addView(card, lp);
    return card;
  }

  public static TextView button(Context c, String label, boolean primary, Runnable action) {
    TextView v = text(c, label, 16, primary ? Design.ON_RED : Design.INK);
    v.setGravity(Gravity.CENTER);
    v.setMinHeight(dp(c, 48));
    v.setPadding(dp(c, 16), dp(c, 8), dp(c, 16), dp(c, 8));
    v.setBackground(ripple(c, primary ? Design.RED : Design.PAPER, Design.INK, 0));
    v.setFocusable(true);
    v.setClickable(true);
    v.setOnClickListener(x -> action.run());
    return v;
  }

  public static void action(LinearLayout l, String text, boolean primary, Runnable run) {
    l.addView(button(l.getContext(), text, primary, run), new LinearLayout.LayoutParams(-1, -2));
  }

  public static void link(LinearLayout l, String text, Runnable run) {
    TextView t = button(l.getContext(), text, false, run);
    t.setTextColor(Design.RED);
    t.setBackground(ripple(l.getContext(), Design.PAPER, 0, 0));
    l.addView(t, new LinearLayout.LayoutParams(-1, -2));
  }

  public static TextView stamp(Context c, String label, int color) {
    TextView v = text(c, label, 12, color);
    v.setGravity(Gravity.CENTER);
    v.setPadding(dp(c, 8), dp(c, 4), dp(c, 8), dp(c, 4));
    v.setBackground(shape(c, Design.PAPER, color, 0));
    v.setLayoutParams(new LinearLayout.LayoutParams(-2, -2));
    return v;
  }

  public static EditText field(LinearLayout l, String label, boolean password, boolean multiline) {
    Context c = l.getContext();
    TextView caption = text(c, label, 14, Design.MUTED);
    l.addView(caption);
    gap(l, 8);
    EditText input = new EditText(c);
    input.setTextSize(16);
    input.setTextColor(Design.INK);
    input.setHintTextColor(Design.MUTED);
    input.setTypeface(Design.body());
    input.setPadding(dp(c, 12), dp(c, 12), dp(c, 12), dp(c, 12));
    input.setBackground(shape(c, Design.PAPER, Design.INK, 0));
    input.setMinHeight(dp(c, 48));
    input.setInputType(password ? 129 : multiline ? 131073 : 1);
    input.setSaveEnabled(!password);
    input.setSingleLine(!multiline);
    if (multiline) {
      input.setMinLines(3);
      input.setGravity(Gravity.TOP);
    }
    input.setContentDescription(label);
    caption.setLabelFor(View.generateViewId());
    input.setId(caption.getLabelFor());
    l.addView(input, new LinearLayout.LayoutParams(-1, -2));
    gap(l, 16);
    if (!password && c instanceof MainActivity a && a.repo.currentUser() != null) {
      String key = a.draftKey(label);
      input.setText(a.drafts.getString(key, ""));
      observe(input, () -> a.drafts.putString(key, input.getText().toString()));
    }
    return input;
  }

  public static void observe(EditText field, Runnable action) {
    field.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int st, int count, int after) {}

          public void onTextChanged(CharSequence s, int st, int before, int count) {
            action.run();
          }

          public void afterTextChanged(Editable e) {}
        });
  }

  public static void empty(LinearLayout l, String title, String message) {
    LinearLayout c = card(l);
    c.addView(stamp(l.getContext(), "暂时空白", Design.BLUE));
    gap(c, 16);
    c.addView(title(l.getContext(), title, 24));
    gap(c, 8);
    c.addView(text(l.getContext(), message, 14, Design.MUTED));
  }

  public static View image(Context c, String path, String label, boolean wide) {
    FrameLayout frame =
        new FrameLayout(c) {
          @Override
          protected void onMeasure(int w, int h) {
            int width = MeasureSpec.getSize(w);
            super.onMeasure(
                w,
                MeasureSpec.makeMeasureSpec(
                    wide ? width * 9 / 16 : dp(c, 180), MeasureSpec.EXACTLY));
          }
        };
    frame.setBackgroundColor(Design.ALT);
    ImageView image = new ImageView(c);
    image.setScaleType(ImageView.ScaleType.CENTER_CROP);
    image.setContentDescription(label);
    boolean ok = false;
    try {
      if (path != null && !path.isEmpty()) {
        InputStream in =
            path.startsWith("asset:")
                ? c.getAssets().open("images/" + path.substring(6))
                : c.getContentResolver().openInputStream(Uri.parse(path));
        try (in) {
          Bitmap bitmap = BitmapFactory.decodeStream(in);
          if (bitmap != null) {
            image.setImageBitmap(bitmap);
            ok = true;
          }
        }
      }
    } catch (Exception ignored) {
    }
    if (ok) frame.addView(image, new FrameLayout.LayoutParams(-1, -1));
    else {
      TextView t = text(c, label, 14, Design.MUTED);
      t.setGravity(Gravity.CENTER);
      t.setPadding(dp(c, 16), 0, dp(c, 16), 0);
      frame.addView(t, new FrameLayout.LayoutParams(-1, -1));
    }
    return frame;
  }

  public static void confirm(Activity a, String title, String body, Runnable run) {
    AlertDialog dialog =
        new AlertDialog.Builder(a)
            .setTitle(title)
            .setMessage(body)
            .setNegativeButton("再想想", null)
            .setPositiveButton("确认", (d, w) -> run.run())
            .create();
    dialog.setOnShowListener(
        d -> {
          dialog.getWindow().setBackgroundDrawable(shape(a, Design.PAPER, Design.INK, 10));
          dialog.getButton(-1).setTextColor(Design.RED);
          dialog.getButton(-2).setTextColor(Design.MUTED);
        });
    dialog.show();
  }

  private static final class PaperDrawable extends Drawable {
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    final int offset;
    final float stroke;

    PaperDrawable(Context c) {
      offset = dp(c, 4);
      stroke = dp(c, 1);
    }

    public void draw(Canvas c) {
      Rect r = getBounds();
      p.setColor(Design.SHADOW);
      c.drawRect(r.left, r.top + offset, r.right, r.bottom, p);
      p.setColor(Design.PAPER);
      c.drawRect(r.left, r.top, r.right, r.bottom - offset, p);
      p.setColor(Design.INK);
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeWidth(stroke);
      c.drawRect(
          r.left + stroke / 2,
          r.top + stroke / 2,
          r.right - stroke / 2,
          r.bottom - offset - stroke / 2,
          p);
      p.setStyle(Paint.Style.FILL);
    }

    public void setAlpha(int a) {}

    public void setColorFilter(ColorFilter f) {}

    public int getOpacity() {
      return PixelFormat.TRANSLUCENT;
    }
  }

  private Ui() {}
}
