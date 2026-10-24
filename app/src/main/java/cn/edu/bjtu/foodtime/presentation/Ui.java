package cn.edu.bjtu.foodtime.presentation;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Small native View toolkit; no WebView, no blurred elevation or default Material colours. */
public final class Ui {
  private static final ExecutorService IMAGES=Executors.newFixedThreadPool(2);
  private static final android.util.LruCache<String,Bitmap> IMAGE_CACHE=new android.util.LruCache<>(12*1024*1024) {
    @Override protected int sizeOf(String key,Bitmap value) {return value.getByteCount();}
  };
  private static int imageGeneration;
  public static void clearImages() {imageGeneration++;IMAGE_CACHE.evictAll();}
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

  /** Small code-drawn food sketch used as decoration without bundling another image asset. */
  public static View foodDoodle(Context c,int variant,int color) {
    return new View(c) {
      final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
      {p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(c,1.6f));p.setStrokeCap(Paint.Cap.ROUND);}
      @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);float w=getWidth(),h=getHeight(),cx=w/2f,cy=h/2f;
        int kind=Math.floorMod(variant,9);
        if(kind==0) {
          RectF bowl=new RectF(w*.16f,h*.39f,w*.84f,h*.77f);
          canvas.drawArc(bowl,0,180,false,p);canvas.drawLine(w*.2f,h*.58f,w*.8f,h*.58f,p);
          for(int i=-1;i<=1;i++) {Path steam=new Path();float x=cx+i*w*.16f;steam.moveTo(x,h*.35f);steam.cubicTo(x-w*.05f,h*.25f,x+w*.06f,h*.2f,x,h*.1f);canvas.drawPath(steam,p);}
        } else if(kind==1) {
          canvas.drawOval(new RectF(w*.12f,h*.42f,w*.88f,h*.74f),p);
          canvas.drawCircle(w*.39f,h*.47f,w*.12f,p);canvas.drawCircle(w*.61f,h*.51f,w*.15f,p);
          canvas.drawLine(w*.74f,h*.12f,w*.48f,h*.66f,p);canvas.drawLine(w*.86f,h*.16f,w*.61f,h*.69f,p);
        } else if(kind==2) {
          Path leaf=new Path();leaf.moveTo(w*.18f,h*.7f);leaf.cubicTo(w*.25f,h*.23f,w*.63f,h*.12f,w*.82f,h*.22f);
          leaf.cubicTo(w*.78f,h*.57f,w*.5f,h*.79f,w*.18f,h*.7f);canvas.drawPath(leaf,p);
          canvas.drawLine(w*.23f,h*.66f,w*.75f,h*.28f,p);canvas.drawCircle(w*.77f,h*.66f,w*.08f,p);
        } else if(kind==3) {
          RectF cup=new RectF(w*.2f,h*.34f,w*.68f,h*.75f);canvas.drawRoundRect(cup,w*.07f,w*.07f,p);
          canvas.drawArc(new RectF(w*.6f,h*.42f,w*.88f,h*.68f),-80,240,false,p);
          for(int i=0;i<2;i++) {Path steam=new Path();float x=w*(.36f+i*.17f);steam.moveTo(x,h*.28f);steam.cubicTo(x-w*.06f,h*.19f,x+w*.07f,h*.14f,x,h*.06f);canvas.drawPath(steam,p);}
        } else if(kind==4) {
          Path fish=new Path();fish.moveTo(w*.16f,h*.5f);fish.cubicTo(w*.34f,h*.2f,w*.69f,h*.23f,w*.8f,h*.5f);
          fish.cubicTo(w*.68f,h*.77f,w*.34f,h*.78f,w*.16f,h*.5f);canvas.drawPath(fish,p);
          Path tail=new Path();tail.moveTo(w*.17f,h*.5f);tail.lineTo(w*.04f,h*.29f);tail.lineTo(w*.04f,h*.71f);tail.close();canvas.drawPath(tail,p);
          canvas.drawCircle(w*.67f,h*.43f,w*.025f,p);
        } else if(kind==5) {
          for(int i=0;i<3;i++) {float left=w*(.08f+i*.29f);RectF dumpling=new RectF(left,h*.31f,left+w*.26f,h*.72f);
            canvas.drawArc(dumpling,180,180,false,p);canvas.drawLine(left,h*.515f,left+w*.26f,h*.515f,p);
            canvas.drawLine(left+w*.09f,h*.43f,left+w*.13f,h*.51f,p);canvas.drawLine(left+w*.17f,h*.43f,left+w*.13f,h*.51f,p);}
        } else if(kind==6) {
          Path apple=new Path();apple.moveTo(cx,h*.28f);apple.cubicTo(w*.24f,h*.17f,w*.13f,h*.5f,w*.25f,h*.72f);
          apple.cubicTo(w*.36f,h*.88f,w*.47f,h*.75f,cx,h*.76f);apple.cubicTo(w*.56f,h*.75f,w*.68f,h*.88f,w*.78f,h*.72f);
          apple.cubicTo(w*.9f,h*.48f,w*.76f,h*.17f,cx,h*.28f);canvas.drawPath(apple,p);
          canvas.drawLine(cx,h*.27f,w*.55f,h*.08f,p);canvas.drawArc(new RectF(w*.52f,h*.08f,w*.74f,h*.26f),185,150,false,p);
        } else if(kind==7) {
          canvas.drawOval(new RectF(w*.16f,h*.12f,w*.42f,h*.46f),p);canvas.drawLine(w*.29f,h*.44f,w*.29f,h*.88f,p);
          for(int i=0;i<3;i++) canvas.drawLine(w*(.58f+i*.09f),h*.12f,w*(.58f+i*.09f),h*.39f,p);
          canvas.drawLine(w*.67f,h*.38f,w*.67f,h*.88f,p);canvas.drawArc(new RectF(w*.58f,h*.28f,w*.76f,h*.48f),0,180,false,p);
        } else {
          Path bread=new Path();bread.moveTo(w*.13f,h*.66f);bread.cubicTo(w*.22f,h*.25f,w*.6f,h*.12f,w*.84f,h*.34f);
          bread.cubicTo(w*.75f,h*.7f,w*.43f,h*.84f,w*.13f,h*.66f);canvas.drawPath(bread,p);
          canvas.drawLine(w*.37f,h*.39f,w*.46f,h*.55f,p);canvas.drawLine(w*.52f,h*.31f,w*.61f,h*.47f,p);canvas.drawLine(w*.67f,h*.29f,w*.73f,h*.41f,p);
        }
      }
    };
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
    card.setBackground(ripple(c, Design.PAPER, Design.BORDER, 20));
    card.setClipToOutline(true);
    card.setElevation(dp(c, 2));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.bottomMargin = dp(c, 14);
    parent.addView(card, lp);
    return card;
  }

  /** Gives every route below a top-level tab the same quiet, hand-drawn paper backdrop. */
  public static void decorateSubpage(LinearLayout page, int seed) {
    page.setBackground(new DoodlePaperDrawable(page.getContext(), seed));
  }

  public static TextView button(Context c, String label, boolean primary, Runnable action) {
    TextView v = text(c, label, 16, primary ? Design.ON_RED : Design.INK);
    v.setGravity(Gravity.CENTER);
    v.setMinHeight(dp(c, 48));
    v.setPadding(dp(c, 16), dp(c, 8), dp(c, 16), dp(c, 8));
    v.setBackground(ripple(c, primary ? Design.RED : Design.PAPER, Design.INK, 16));
    v.setFocusable(true);
    v.setClickable(true);
    v.setOnClickListener(x -> action.run());
    return v;
  }

  public static void action(LinearLayout l, String text, boolean primary, Runnable run) {
    l.addView(button(l.getContext(), text, primary, run), new LinearLayout.LayoutParams(-1, -2));
  }

  public static void submit(LinearLayout parent,String label,TextView error,Supplier<Runnable> prepare,Runnable success) {
    MainActivity a=(MainActivity)parent.getContext();TextView button=button(a,label,true,() -> {});
    button.setOnClickListener(v -> {
      try {a.request(v,error,prepare.get(),success);}
      catch(RuntimeException e) {error.setText(e.getMessage());}
    });
    parent.addView(button,new LinearLayout.LayoutParams(-1,-2));
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
    v.setBackground(shape(c, Design.PAPER, color, 12));
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
    input.setBackground(shape(c, Design.PAPER, Design.BORDER, 16));
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
    if (!password && !label.equals("邮箱验证码") && c instanceof MainActivity a) {
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

  /** Empty local errors should not reserve a line in compact review action groups. */
  static void observeError(TextView error) {
    error.addTextChangedListener(new TextWatcher() {
      public void beforeTextChanged(CharSequence s,int st,int count,int after) {}
      public void onTextChanged(CharSequence s,int st,int before,int count) {error.setVisibility(s.length()==0?View.GONE:View.VISIBLE);}
      public void afterTextChanged(Editable s) {}
    });
  }

  public static void empty(LinearLayout l, String title, String message) {
    LinearLayout c = card(l);
    c.addView(title(l.getContext(), title, 24));
    if(!message.isBlank()) {gap(c,8);c.addView(text(l.getContext(),message,14,Design.MUTED));}
  }

  /** Thumbnail variant for list display; local files and full-size remote images pass through. */
  public static String thumb(String path) {
    if (path == null || path.isBlank() || !path.startsWith("/api/")) return path;
    return path.contains("?") ? path + "&size=thumb" : path + "?size=thumb";
  }

  /** Feed cover that starts at a stable ratio, then adopts the decoded image's real ratio. */
  public static View communityImage(Context c,String path,String label,float fallbackHeightRatio) {
    return imageFrame(c,path,label,Math.max(.72f,Math.min(1.45f,fallbackHeightRatio)),true,false);
  }

  public static View image(Context c, String path, String label, boolean wide) {
    return imageFrame(c,path,label,wide?9f/16f:1f,false,wide);
  }

  /** Fixed-ratio cover used by editorial cards that deliberately mix portrait and landscape shapes. */
  public static View ratioImage(Context c,String path,String label,float heightRatio) {
    return imageFrame(c,path,label,Math.max(.65f,Math.min(1.35f,heightRatio)),false,false);
  }

  @android.annotation.SuppressLint("DrawAllocation") // Bitmap decoding below runs in IMAGES, never in onMeasure/draw.
  private static View imageFrame(Context c,String path,String label,float initialRatio,
      boolean adaptive,boolean shortBlank) {
    final float[] heightRatio={initialRatio};
    FrameLayout frame = new FrameLayout(c) {
      @Override protected void onMeasure(int w,int h) {
        int width=MeasureSpec.getSize(w);
        int height=shortBlank&&(path==null||path.isBlank())?dp(c,120):Math.round(width*heightRatio[0]);
        super.onMeasure(w,MeasureSpec.makeMeasureSpec(height,MeasureSpec.EXACTLY));
      }
    };
    frame.setBackgroundColor(Design.ALT);
    ImageView image = new ImageView(c);
    image.setScaleType(ImageView.ScaleType.CENTER_CROP);
    image.setContentDescription(label);
    TextView placeholder=text(c,label,14,Design.MUTED);placeholder.setGravity(Gravity.CENTER);
    placeholder.setPadding(dp(c,16),0,dp(c,16),0);
    frame.addView(image,new FrameLayout.LayoutParams(-1,-1));frame.addView(placeholder,new FrameLayout.LayoutParams(-1,-1));
    if(path!=null&&!path.isBlank() && c instanceof MainActivity a) {
      var user=a.repo.currentUser();String identity=user==null?"anonymous":user.id()+":"+user.role();
      String key=identity+":"+path;int ticket=imageGeneration;
      boolean remoteFull=(path.startsWith("/api/")||path.startsWith("http:")||path.startsWith("https:"))
          && !path.contains("size=thumb");
      boolean cacheable=!remoteFull;
      Bitmap cached=cacheable?IMAGE_CACHE.get(key):null;
      if(cached!=null) {
        if(adaptive&&cached.getWidth()>0) heightRatio[0]=Math.max(.72f,Math.min(1.45f,(float)cached.getHeight()/cached.getWidth()));
        image.setImageBitmap(cached);placeholder.setVisibility(View.GONE);
      }
      else IMAGES.execute(() -> {
        Bitmap bitmap=null;
        try {
          byte[] bytes;
          if(path.startsWith("/api/")||path.startsWith("http:" )||path.startsWith("https:")) bytes=a.repo.loadImage(path);
          else try(InputStream in=path.startsWith("asset:")?c.getAssets().open("images/"+path.substring(6)):c.getContentResolver().openInputStream(Uri.parse(path));var out=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[8192];int count;
            while(in!=null&&(count=in.read(buffer))!=-1) {if(out.size()+count>20*1024*1024) throw new IOException();out.write(buffer,0,count);}
            bytes=out.toByteArray();
          }
          BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
          BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);options.inSampleSize=1;
          while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>1600) options.inSampleSize*=2;
          options.inJustDecodeBounds=false;bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
        } catch(Exception ignored) { }
        Bitmap loaded=bitmap;
        a.runOnUiThread(() -> {
          if(a.isDestroyed()||ticket!=imageGeneration||loaded==null) return;
          if(cacheable) IMAGE_CACHE.put(key,loaded);if(!frame.isAttachedToWindow()) return;
          if(adaptive&&loaded.getWidth()>0) {
            heightRatio[0]=Math.max(.72f,Math.min(1.45f,(float)loaded.getHeight()/loaded.getWidth()));
            frame.requestLayout();
          }
          image.setImageBitmap(loaded);placeholder.setVisibility(View.GONE);
        });
      });
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

  private static final class DoodlePaperDrawable extends Drawable {
    private final Context context;
    private final int seed;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    DoodlePaperDrawable(Context context, int seed) {
      this.context=context;this.seed=Math.abs(seed);
      paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);
      paint.setStrokeWidth(dp(context,1.25f));
    }

    @Override public void draw(Canvas canvas) {
      canvas.drawColor(Design.PAPER);
      Rect bounds=getBounds();int step=dp(context,178);
      for(int y=step/2,index=0;y<bounds.bottom+step;y+=step,index++) {
        boolean right=(index+seed)%2==0;
        float x=right?bounds.right-dp(context,31):dp(context,31);
        float size=dp(context,29);
        paint.setColor(((index+seed)%3==0?Design.GREEN:Design.RED)&0x00ffffff|0x18000000);
        drawMark(canvas,x,y,size,(index+seed)%4);
      }
    }

    private void drawMark(Canvas c,float x,float y,float s,int kind) {
      if(kind==0) {
        c.drawArc(new RectF(x-s,y-s*.08f,x+s,y+s*.72f),0,180,false,paint);
        c.drawLine(x-s*.82f,y+s*.3f,x+s*.82f,y+s*.3f,paint);
        for(int i=-1;i<=1;i++) {
          Path steam=new Path();float sx=x+i*s*.36f;steam.moveTo(sx,y-s*.25f);
          steam.cubicTo(sx-s*.12f,y-s*.48f,sx+s*.16f,y-s*.62f,sx,y-s*.9f);c.drawPath(steam,paint);
        }
      } else if(kind==1) {
        Path leaf=new Path();leaf.moveTo(x-s*.8f,y+s*.45f);leaf.cubicTo(x-s*.55f,y-s*.7f,x+s*.5f,y-s*.75f,x+s*.78f,y-s*.34f);
        leaf.cubicTo(x+s*.55f,y+s*.48f,x-s*.1f,y+s*.78f,x-s*.8f,y+s*.45f);c.drawPath(leaf,paint);
        c.drawLine(x-s*.65f,y+s*.36f,x+s*.58f,y-s*.3f,paint);
      } else if(kind==2) {
        c.drawOval(new RectF(x-s*.9f,y-s*.3f,x+s*.9f,y+s*.32f),paint);
        c.drawCircle(x-s*.28f,y-s*.08f,s*.2f,paint);c.drawCircle(x+s*.25f,y+s*.02f,s*.28f,paint);
        c.drawLine(x+s*.45f,y-s*.82f,x+s*.02f,y+s*.52f,paint);c.drawLine(x+s*.7f,y-s*.75f,x+s*.28f,y+s*.58f,paint);
      } else {
        RectF cup=new RectF(x-s*.72f,y-s*.48f,x+s*.35f,y+s*.5f);c.drawRoundRect(cup,s*.14f,s*.14f,paint);
        c.drawArc(new RectF(x+s*.18f,y-s*.25f,x+s*.78f,y+s*.35f),-80,240,false,paint);
        Path steam=new Path();steam.moveTo(x-s*.28f,y-s*.62f);steam.cubicTo(x-s*.45f,y-s*.82f,x-s*.05f,y-s*.92f,x-s*.2f,y-s*1.12f);c.drawPath(steam,paint);
      }
    }

    @Override public void setAlpha(int alpha) {}
    @Override public void setColorFilter(ColorFilter filter) {}
    @Override public int getOpacity() {return PixelFormat.OPAQUE;}
  }

  private Ui() {}
}
