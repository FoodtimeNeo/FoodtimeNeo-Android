package cn.edu.bjtu.foodtime.presentation;

import android.content.Context;
import android.graphics.Typeface;

/** Mirrors the actual Web tokens.scss. All application colours live here. */
public final class Design {
  public static final int INK = 0xff3a2418,
      MUTED = 0xff6e5547,
      RED = 0xffc9341e,
      RED_PRESSED = 0xffab1f0b,
      GREEN = 0xff5b7a3a,
      BLUE = 0xff3f7291,
      PAPER = 0xfffffaf0,
      ALT = 0xfff7efd9,
      KRAFT = 0xffd8c08b,
      KRAFT_SOFT = 0xffeadbbb,
      BAR = 0xfff2efe6,
      BORDER = 0xffd5ccbc,
      PRESSED = 0xfff1ece2,
      ON_RED = 0xfffff5f2,
      SHADOW = 0x1a3a2418,
      RED_SOFT = 0x24b72f1c;
  private static Typeface title, body, note;

  public static void init(Context c) {
    title = load(c, "fonts/title.ttf", Typeface.create("serif", Typeface.BOLD));
    body = load(c, "fonts/body.ttf", Typeface.create("sans-serif", Typeface.NORMAL));
    note = load(c, "fonts/note.ttf", Typeface.create("serif", Typeface.NORMAL));
    if (android.os.Build.VERSION.SDK_INT >= 28) {
      title = Typeface.create(title, 900, false);
      body = Typeface.create(body, 400, false);
    } else {
      title = Typeface.create(title, Typeface.BOLD);
      body = Typeface.create(body, Typeface.NORMAL);
    }
  }

  private static Typeface load(Context c, String name, Typeface fallback) {
    try {
      return Typeface.createFromAsset(c.getAssets(), name);
    } catch (RuntimeException e) {
      return fallback;
    }
  }

  public static Typeface title() {
    return title;
  }

  public static Typeface body() {
    return body;
  }

  public static Typeface note() {
    return note;
  }

  private Design() {}
}
