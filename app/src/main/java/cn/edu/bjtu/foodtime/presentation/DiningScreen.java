package cn.edu.bjtu.foodtime.presentation;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.widget.*;
import cn.edu.bjtu.foodtime.domain.model.Models.*;
import java.util.List;

final class DiningScreen {
  private static final float[] COVER_RATIOS={1.06f,.74f,.86f,1.18f,.78f,1.02f};
  private static final float[] STALL_RATIOS={.72f,.94f,.8f,1.02f,.76f,.9f};

  static void halls(MainActivity a, LinearLayout page, MainActivity.Route route) {
    page.setBackground(new FoodDoodleDrawable(a));
    a.heading(page,"01","食堂手账","");

    if (a.repo.halls().isEmpty()) {
      if (a.isPageLoading()) Ui.empty(page, "正在准备食堂…", "热乎的档口马上就来。");
      else if (a.pageFailed()) {
        Ui.empty(page, "食堂暂时无法加载", "检查网络后重试。");
        Ui.action(page, "重新加载", true, a::render);
      } else Ui.empty(page, "暂无食堂", "");
      return;
    }

    // 顶部紧凑搜索行：搜索框 + 右侧紧凑“食堂地图”图标按钮
    LinearLayout searchRow = Ui.row(a);
    searchRow.setGravity(Gravity.CENTER_VERTICAL);
    EditText search = new EditText(a);
    search.setId(View.generateViewId());
    search.setContentDescription("想去哪家食堂？");
    search.setHint("想去哪家食堂？");
    search.setTextSize(15);
    search.setTypeface(Design.body());
    search.setSingleLine(true);
    search.setFilters(new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(100)});
    search.setPadding(Ui.dp(a, 16), 0, Ui.dp(a, 16), 0);
    search.setBackground(Ui.shape(a, Design.PAPER, Design.BORDER, 22));
    search.setTextColor(Design.INK);
    search.setHintTextColor(Design.MUTED);
    search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
    String key = a.draftKey("search");
    search.setText(a.drafts.getString(key, ""));
    searchRow.addView(search, new LinearLayout.LayoutParams(0, Ui.dp(a, 46), 1));

    TextView clear = Ui.button(a, "清空", false, () -> {
      search.setText("");
      search.requestFocus();
    });
    clear.setBackground(Ui.ripple(a, Design.ALT, 0, 22));
    clear.setContentDescription("清除搜索关键词");
    clear.setVisibility(search.getText().length() == 0 ? View.GONE : View.VISIBLE);
    LinearLayout.LayoutParams clearLp = new LinearLayout.LayoutParams(-2, Ui.dp(a, 46));
    clearLp.leftMargin = Ui.dp(a, 6);
    searchRow.addView(clear, clearLp);

    LinearLayout mapBtn = Ui.row(a);
    mapBtn.setGravity(Gravity.CENTER);
    mapBtn.setFocusable(true);
    mapBtn.setClickable(true);
    mapBtn.setContentDescription("食堂地图");
    mapBtn.setBackground(Ui.ripple(a, Design.PAPER, Design.BORDER, 22));
    mapBtn.setPadding(Ui.dp(a, 12), 0, Ui.dp(a, 12), 0);
    ImageView mapIcon = new ImageView(a);
    mapIcon.setImageResource(cn.edu.bjtu.foodtime.R.drawable.ic_campus_map);
    mapIcon.setImageTintList(android.content.res.ColorStateList.valueOf(Design.INK));
    mapBtn.addView(mapIcon, new LinearLayout.LayoutParams(Ui.dp(a, 18), Ui.dp(a, 18)));
    TextView mapText = Ui.text(a, "食堂地图", 13, Design.INK);
    mapText.setTypeface(Design.body());
    LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(-2, -2);
    textLp.leftMargin = Ui.dp(a, 5);
    mapBtn.addView(mapText, textLp);
    mapBtn.setOnClickListener(v -> a.go("campus-map", ""));
    LinearLayout.LayoutParams mapLp = new LinearLayout.LayoutParams(-2, Ui.dp(a, 46));
    mapLp.leftMargin = Ui.dp(a, 8);
    searchRow.addView(mapBtn, mapLp);

    page.addView(searchRow, new LinearLayout.LayoutParams(-1, -2));
    Ui.gap(page, 14);

    // 今日推荐横向卡片
    RecommendationSection.show(a, page);

    // 底部食堂瀑布流区域
    TextView count = Ui.text(a, "", 14, Design.MUTED);
    count.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    page.addView(count);
    Ui.gap(page, 12);
    LinearLayout list = Ui.col(a);
    page.addView(list);

    List<Hall> allHalls = a.repo.halls();
    Runnable filter = () -> {
      String query = search.getText().toString();
      a.drafts.putString(key, query);
      clear.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
      list.removeAllViews();
      List<Hall> filtered = new java.util.ArrayList<>();
      for (Hall hall : allHalls) {
        if (cn.edu.bjtu.foodtime.domain.model.CatalogSearch.matches(hall.name(), query)) {
          filtered.add(hall);
        }
      }
      int found = filtered.size();
      count.setText(query.isBlank()
          ? "全部食堂 (" + allHalls.size() + ")"
          : "全部食堂 (" + found + ")");
      if (found == 0) {
        Ui.empty(list, "没有找到相关食堂", "换个关键词搜搜看吧。");
      } else {
        hallGallery(a, list, filtered);
      }
    };
    Ui.observe(search, filter);
    search.setOnEditorActionListener((view, action, event) -> {
      if (action != android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) return false;
      ((android.view.inputmethod.InputMethodManager) a.getSystemService(android.content.Context.INPUT_METHOD_SERVICE))
          .hideSoftInputFromWindow(search.getWindowToken(), 0);
      return true;
    });
    filter.run();
  }

  static String getHallFallbackImage(Hall hall, int index) {
    String name = hall.name();
    if (name.contains("学苑")) return "asset:hall_xueyuan.png";
    if (name.contains("明湖") && name.contains("1楼")) return "asset:hall_1.webp";
    if (name.contains("明湖") && name.contains("2楼")) return "asset:hall_2.webp";
    if (name.contains("明湖") && name.contains("3楼")) return "asset:hall_3.webp";
    if (name.contains("学活") && name.contains("一楼")) return "asset:hall_4.webp";
    if (name.contains("学活") && name.contains("二楼")) return "asset:stall_1.webp";
    if (name.contains("东区")) return "asset:hall_5.webp";
    if (name.contains("学四")) return "asset:hall_6.webp";
    if (name.contains("留园")) return "asset:stall_2.webp";
    return "asset:hall_" + (index % 6 + 1) + ".webp";
  }

  static String hallMetaLabel(MainActivity a, Hall hall) {
    String name = hall.name();
    String location = "主校区";
    if (name.contains("东区")) {
      location = "东区";
    } else if (name.contains("明湖")) {
      if (name.contains("1楼")) location = "明湖 · 1层";
      else if (name.contains("2楼")) location = "明湖 · 2层";
      else if (name.contains("3楼")) location = "明湖 · 3层";
      else location = "明湖餐厅";
    } else if (name.contains("学活")) {
      if (name.contains("一楼")) location = "学活 · 1层";
      else if (name.contains("二楼")) location = "学活 · 2层";
      else location = "学生活动中心";
    } else if (name.contains("学四")) {
      location = "主区核心";
    } else if (name.contains("学苑")) {
      location = "主校区";
    } else if (name.contains("留园")) {
      location = "留学生区";
    }

    List<Stall> stalls = (a != null && a.repo != null) ? a.repo.stalls(hall.id()) : null;
    int count = (stalls != null) ? stalls.size() : 0;
    if (count > 0) {
      return location + " · " + count + "个档口";
    }
    int knownCount = switch (name) {
      case "东区学生餐厅" -> 18;
      case "明湖餐厅1楼" -> 12;
      case "明湖餐厅2楼" -> 10;
      case "明湖餐厅3楼" -> 8;
      case "学活一楼" -> 14;
      case "学活二楼" -> 9;
      case "学四食堂" -> 16;
      case "学苑餐厅" -> 11;
      case "留园食堂", "留园餐厅" -> 6;
      default -> 0;
    };
    if (knownCount > 0) {
      return location + " · " + knownCount + "个档口";
    }
    return location;
  }

  private static void hallGallery(MainActivity a,LinearLayout page,List<Hall> halls) {
    LinearLayout waterfall=Ui.row(a);waterfall.setGravity(Gravity.TOP);
    LinearLayout left=Ui.col(a),right=Ui.col(a);
    LinearLayout.LayoutParams leftLp=new LinearLayout.LayoutParams(0,-2,1);leftLp.rightMargin=Ui.dp(a,6);
    LinearLayout.LayoutParams rightLp=new LinearLayout.LayoutParams(0,-2,1);rightLp.leftMargin=Ui.dp(a,6);
    waterfall.addView(left,leftLp);waterfall.addView(right,rightLp);
    float[] heights={0,0};
    for(int i=0;i<halls.size();i++) {
      int column=heights[0]<=heights[1]?0:1;
      float ratio=COVER_RATIOS[i%COVER_RATIOS.length];
      hallCard(a,column==0?left:right,halls.get(i),i,ratio);
      heights[column]+=ratio+.58f;
    }
    page.addView(waterfall,new LinearLayout.LayoutParams(-1,-2));
  }

  private static void hallCard(MainActivity a,LinearLayout column,Hall hall,int index,float ratio) {
    LinearLayout card=Ui.col(a);
    card.setBackground(Ui.ripple(a,Design.PAPER,Design.BORDER,18));
    card.setClipToOutline(true);card.setElevation(Ui.dp(a,2));
    String image=hall.coverImageUrl();
    if(image==null||image.isBlank()) image=getHallFallbackImage(hall, index);
    card.addView(Ui.ratioImage(a,Ui.thumb(image),hall.name(),ratio),new LinearLayout.LayoutParams(-1,-2));
    LinearLayout body=Ui.col(a);body.setPadding(Ui.dp(a,12),Ui.dp(a,12),Ui.dp(a,12),Ui.dp(a,14));
    TextView number=Ui.text(a,String.format("第 %02d 站",index+1),11,index%3==1?Design.GREEN:Design.RED);
    number.setLetterSpacing(.08f);body.addView(number);Ui.gap(body,6);
    TextView name=Ui.title(a,hall.name(),20);name.setMaxLines(2);body.addView(name);
    if (hall.averageScore() != null) {
      Ui.gap(body, 4);
      body.addView(Ui.text(a,VenueScreen.scoreLabel(hall.averageScore(),hall.reviewCount()),12,Design.RED));
    }
    Ui.gap(body,8);body.addView(Ui.text(a,hallMetaLabel(a, hall),12,Design.MUTED));
    card.addView(body,new LinearLayout.LayoutParams(-1,-2));
    card.setFocusable(true);card.setClickable(true);
    card.setContentDescription(hall.name()+"，查看档口");
    card.setOnClickListener(v -> a.go("stalls",hall.id()));
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=Ui.dp(a,12);
    column.addView(card,lp);
  }

  static void stalls(MainActivity a, LinearLayout page, MainActivity.Route route) {
    Hall hall = a.repo.hall(route.id());
    if (hall == null || !hall.active()) throw new IllegalArgumentException("食堂不存在或已停用");
    a.heading(page, "01.1", hall.name(), "");
    CatalogQuickActions.show(a,page,"halls",hall.id());
    VenueScreen.summary(a,page,"hall",hall.id());
    if (a.repo.stalls(hall.id()).isEmpty()) {
      Ui.empty(page, "暂无档口", "");
      Ui.action(page, "投稿一个新档口", true, () -> a.go("submit-stall", hall.id()));
      return;
    }
    CatalogList.showGallery(a,page,"搜索档口","个档口",a.repo.stalls(hall.id()),Stall::name,
        (list,stalls) -> stallGallery(a,list,stalls));
    Ui.gap(page,8);
    Ui.action(page, "投稿一个新档口", true, () -> a.go("submit-stall", hall.id()));
  }

  private static void stallGallery(MainActivity a,LinearLayout page,List<Stall> stalls) {
    LinearLayout waterfall=Ui.row(a);waterfall.setGravity(Gravity.TOP);
    LinearLayout left=Ui.col(a),right=Ui.col(a);
    LinearLayout.LayoutParams ll=new LinearLayout.LayoutParams(0,-2,1);ll.rightMargin=Ui.dp(a,6);
    LinearLayout.LayoutParams rl=new LinearLayout.LayoutParams(0,-2,1);rl.leftMargin=Ui.dp(a,6);
    waterfall.addView(left,ll);waterfall.addView(right,rl);
    float[] height={0,0};
    for(int i=0;i<stalls.size();i++) {
      int column=height[0]<=height[1]?0:1;float ratio=STALL_RATIOS[i%STALL_RATIOS.length];
      stallCard(a,column==0?left:right,stalls.get(i),i,ratio);height[column]+=ratio+.62f;
    }
    page.addView(waterfall,new LinearLayout.LayoutParams(-1,-2));
  }

  private static void stallCard(MainActivity a,LinearLayout column,Stall stall,int index,float ratio) {
    LinearLayout card=Ui.col(a);card.setBackground(Ui.ripple(a,index%3==1?Design.ALT:Design.PAPER,Design.BORDER,20));
    card.setClipToOutline(true);card.setElevation(Ui.dp(a,2));
    String cover = stall.coverImageUrl();
    if(cover!=null&&!cover.isBlank()) {
      card.addView(Ui.ratioImage(a,Ui.thumb(cover),stall.name(),ratio));
    } else {
      String fallback = "asset:stall_" + (index % 2 + 1) + ".webp";
      card.addView(Ui.ratioImage(a,fallback,stall.name(),ratio));
    }
    LinearLayout body=Ui.col(a);body.setPadding(Ui.dp(a,12),Ui.dp(a,12),Ui.dp(a,12),Ui.dp(a,14));
    TextView name=Ui.title(a,stall.name(),19);name.setMaxLines(2);body.addView(name);Ui.gap(body,7);
    if (stall.averageScore() != null) {
      body.addView(Ui.text(a,VenueScreen.scoreLabel(stall.averageScore(),stall.reviewCount()),12,Design.RED));
    }
    card.addView(body);card.setClickable(true);card.setFocusable(true);card.setContentDescription(stall.name()+"，查看菜品");
    card.setOnClickListener(v -> a.go("dishes",stall.id()));
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=Ui.dp(a,12);column.addView(card,lp);
  }

  static void cover(MainActivity a, LinearLayout page, String name, String image, Runnable action) {
    LinearLayout card = Ui.card(page);
    card.setOrientation(LinearLayout.HORIZONTAL);
    card.setGravity(Gravity.CENTER_VERTICAL);
    card.setPadding(0, 0, 0, Ui.dp(a, 4));
    if (image != null && !image.isBlank())
      card.addView(Ui.image(a, Ui.thumb(image), name, false), new LinearLayout.LayoutParams(Ui.dp(a, 80), Ui.dp(a, 80)));
    LinearLayout label = Ui.row(a);
    label.setPadding(Ui.dp(a, 16), Ui.dp(a, 12), Ui.dp(a, 16), Ui.dp(a, 12));
    label.setMinimumHeight(Ui.dp(a, 64));
    label.addView(Ui.title(a, name, 22), new LinearLayout.LayoutParams(0, -2, 1));
    card.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
    card.setFocusable(true);
    card.setClickable(true);
    card.setContentDescription(name + "，查看");
    card.setOnClickListener(v -> action.run());
  }

  /** Quiet food-line doodles behind the dining page; decorative only and intentionally low contrast. */
  private static final class FoodDoodleDrawable extends Drawable {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int paper,red,green,step;

    FoodDoodleDrawable(MainActivity a) {
      paper=Design.PAPER;red=withAlpha(Design.RED,24);green=withAlpha(Design.GREEN,20);
      step=Ui.dp(a,190);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Ui.dp(a,1.25f));
      paint.setStrokeCap(Paint.Cap.ROUND);
    }

    @Override public void draw(Canvas canvas) {
      canvas.drawColor(paper);Rect b=getBounds();
      for(int y=step/2;y<b.bottom+step;y+=step) {
        int row=y/step;float x=row%2==0?b.right-step*.24f:step*.22f;
        paint.setColor(row%3==0?red:green);
        if(row%2==0) bowl(canvas,x,y,step*.18f); else snack(canvas,x,y,step*.16f);
      }
    }

    private void bowl(Canvas c,float x,float y,float size) {
      RectF bowl=new RectF(x-size,y-size*.15f,x+size,y+size*.7f);
      c.drawArc(bowl,0,180,false,paint);c.drawLine(x-size*.86f,y+size*.28f,x+size*.86f,y+size*.28f,paint);
      for(int i=-1;i<=1;i++) {
        Path steam=new Path();steam.moveTo(x+i*size*.38f,y-size*.3f);
        steam.cubicTo(x+i*size*.55f,y-size*.55f,x+i*size*.18f,y-size*.68f,x+i*size*.35f,y-size*.94f);
        c.drawPath(steam,paint);
      }
    }

    private void snack(Canvas c,float x,float y,float size) {
      RectF plate=new RectF(x-size,y-size*.24f,x+size,y+size*.24f);c.drawOval(plate,paint);
      c.drawCircle(x-size*.28f,y-size*.08f,size*.18f,paint);
      c.drawCircle(x+size*.22f,y+size*.02f,size*.24f,paint);
      c.drawLine(x+size*.55f,y-size*.9f,x+size*.08f,y+size*.55f,paint);
      c.drawLine(x+size*.78f,y-size*.82f,x+size*.3f,y+size*.62f,paint);
    }

    private static int withAlpha(int color,int alpha) {return (color&0x00ffffff)|(alpha<<24);}
    @Override public void setAlpha(int alpha) {}
    @Override public void setColorFilter(android.graphics.ColorFilter filter) {}
    @Override public int getOpacity() {return android.graphics.PixelFormat.OPAQUE;}
  }
}
