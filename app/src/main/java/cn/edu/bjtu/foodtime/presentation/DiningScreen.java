package cn.edu.bjtu.foodtime.presentation;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.widget.*;
import cn.edu.bjtu.foodtime.domain.model.Models.*;
import java.util.List;

final class DiningScreen {
  private static final float CARD_COVER_RATIO = .72f;

  static void halls(MainActivity a, LinearLayout page, MainActivity.Route route) {
    page.setBackground(new FoodDoodleDrawable(a));
    page.setPadding(Ui.dp(a, 14), Ui.dp(a, 12), Ui.dp(a, 14), Ui.dp(a, 24));

    if (a.repo.halls().isEmpty()) {
      if (a.isPageLoading()) Ui.empty(page, "正在准备食堂…", "热乎的档口马上就来。");
      else if (a.pageFailed()) {
        Ui.empty(page, "食堂暂时无法加载", "检查网络后重试。");
        Ui.action(page, "重新加载", true, a::render);
      } else Ui.empty(page, "暂无食堂", "");
      return;
    }

    // 顶部发现流搜索栏：直达全局统一搜索 + 右侧紧凑地图入口
    LinearLayout searchRow = Ui.row(a);
    searchRow.setGravity(Gravity.CENTER_VERTICAL);

    LinearLayout searchPill = Ui.row(a);
    searchPill.setGravity(Gravity.CENTER_VERTICAL);
    searchPill.setFocusable(true);
    searchPill.setClickable(true);
    searchPill.setTag("dining-global-search");
    searchPill.setContentDescription("搜索菜品、档口或笔记");
    searchPill.setBackground(Ui.ripple(a, Design.ALT, Design.BORDER, 22));
    searchPill.setPadding(Ui.dp(a, 14), Ui.dp(a, 4), Ui.dp(a, 5), Ui.dp(a, 4));
    ImageView searchIcon = new ImageView(a);
    searchIcon.setImageResource(cn.edu.bjtu.foodtime.R.drawable.ic_community_search);
    searchIcon.setImageTintList(android.content.res.ColorStateList.valueOf(Design.MUTED));
    searchIcon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    searchPill.addView(searchIcon, new LinearLayout.LayoutParams(Ui.dp(a, 18), Ui.dp(a, 18)));
    TextView searchHint = Ui.text(a, "搜菜品、档口或食记", 14, Design.MUTED);
    searchHint.setSingleLine(true);
    searchHint.setEllipsize(android.text.TextUtils.TruncateAt.END);
    LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(0, -2, 1);
    hintLp.leftMargin = Ui.dp(a, 8);
    hintLp.rightMargin = Ui.dp(a, 8);
    searchPill.addView(searchHint, hintLp);
    TextView searchCta = Ui.text(a, "搜索", 13, Design.ON_RED);
    searchCta.setTypeface(Typeface.create(Design.body(), Typeface.BOLD));
    searchCta.setGravity(Gravity.CENTER);
    searchCta.setPadding(Ui.dp(a, 12), Ui.dp(a, 6), Ui.dp(a, 12), Ui.dp(a, 6));
    searchCta.setBackground(Ui.shape(a, Design.RED, 0, 18));
    searchPill.addView(searchCta, new LinearLayout.LayoutParams(-2, Ui.dp(a, 34)));
    searchPill.setOnClickListener(v -> a.go("dish-search", ""));
    searchRow.addView(searchPill, new LinearLayout.LayoutParams(0, Ui.dp(a, 44), 1));

    LinearLayout mapBtn = Ui.row(a);
    mapBtn.setGravity(Gravity.CENTER);
    mapBtn.setFocusable(true);
    mapBtn.setClickable(true);
    mapBtn.setContentDescription("食堂地图");
    mapBtn.setBackground(Ui.ripple(a, Design.PAPER, Design.BORDER, 22));
    mapBtn.setPadding(Ui.dp(a, 12), 0, Ui.dp(a, 12), 0);
    ImageView mapIcon = new ImageView(a);
    mapIcon.setImageResource(cn.edu.bjtu.foodtime.R.drawable.ic_campus_map);
    mapIcon.setImageTintList(android.content.res.ColorStateList.valueOf(Design.RED));
    mapBtn.addView(mapIcon, new LinearLayout.LayoutParams(Ui.dp(a, 18), Ui.dp(a, 18)));
    TextView mapText = Ui.text(a, "地图", 13, Design.INK);
    mapText.setTypeface(Typeface.create(Design.body(), Typeface.BOLD));
    LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(-2, -2);
    textLp.leftMargin = Ui.dp(a, 4);
    mapBtn.addView(mapText, textLp);
    mapBtn.setOnClickListener(v -> a.go("campus-map", ""));
    LinearLayout.LayoutParams mapLp = new LinearLayout.LayoutParams(-2, Ui.dp(a, 44));
    mapLp.leftMargin = Ui.dp(a, 8);
    searchRow.addView(mapBtn, mapLp);

    page.addView(searchRow, new LinearLayout.LayoutParams(-1, -2));
    Ui.gap(page, 16);

    // 今日推荐紧凑横向发现条
    RecommendationSection.show(a, page);

    // 全部食堂标题 + 校区轻筛选胶囊
    LinearLayout sectionHeader = Ui.row(a);
    sectionHeader.setGravity(Gravity.CENTER_VERTICAL);
    TextView count = Ui.title(a, "", 20);
    count.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    sectionHeader.addView(count, new LinearLayout.LayoutParams(0, -2, 1));
    LinearLayout areaChips = Ui.row(a);
    sectionHeader.addView(areaChips, new LinearLayout.LayoutParams(-2, -2));
    page.addView(sectionHeader, new LinearLayout.LayoutParams(-1, -2));
    Ui.gap(page, 12);

    LinearLayout list = Ui.col(a);
    page.addView(list);

    List<Hall> allHalls = a.repo.halls();
    String[] areas = {"全部", "主校区", "东区"};
    String areaKey = a.draftKey("area-filter");
    String[] activeArea = {a.drafts.getString(areaKey, "全部")};
    Runnable[] renderHalls = {null};
    renderHalls[0] = () -> {
      areaChips.removeAllViews();
      for (String area : areas) {
        boolean chosen = area.equals(activeArea[0]);
        TextView chip = Ui.text(a, area, 12, chosen ? Design.RED : Design.MUTED);
        chip.setTypeface(chosen ? Typeface.create(Design.body(), Typeface.BOLD) : Design.body());
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(Ui.dp(a, 10), Ui.dp(a, 5), Ui.dp(a, 10), Ui.dp(a, 5));
        chip.setMinHeight(Ui.dp(a, 30));
        chip.setBackground(Ui.ripple(a, chosen ? Design.RED_SOFT : Design.ALT, chosen ? Design.RED : 0, 15));
        chip.setFocusable(true);
        chip.setClickable(true);
        chip.setOnClickListener(v -> {
          activeArea[0] = area;
          a.drafts.putString(areaKey, area);
          renderHalls[0].run();
        });
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, -2);
        clp.leftMargin = Ui.dp(a, 6);
        areaChips.addView(chip, clp);
      }

      list.removeAllViews();
      List<Hall> filtered = new java.util.ArrayList<>();
      for (Hall hall : allHalls) {
        boolean isEast = hall.name().contains("东区");
        if ("全部".equals(activeArea[0])
            || ("东区".equals(activeArea[0]) && isEast)
            || ("主校区".equals(activeArea[0]) && !isEast)) {
          filtered.add(hall);
        }
      }
      count.setText("全部食堂 (" + filtered.size() + ")");
      if (filtered.isEmpty()) {
        Ui.empty(list, "该区域暂无食堂", "切换“全部”查看校内所有食堂。");
      } else {
        hallGallery(a, list, filtered);
      }
    };
    renderHalls[0].run();
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
    } else if (name.contains("学四") || name.contains("学苑") || name.contains("留园")) {
      location = "主校区";
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

  private static void hallGallery(MainActivity a, LinearLayout page, List<Hall> halls) {
    for (int i = 0; i < halls.size(); i += 2) {
      LinearLayout row = Ui.row(a);
      row.setGravity(Gravity.TOP);
      LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(0, -1, 1);
      leftLp.rightMargin = Ui.dp(a, 5);
      row.addView(hallCard(a, halls.get(i), i), leftLp);
      LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(0, -1, 1);
      rightLp.leftMargin = Ui.dp(a, 5);
      if (i + 1 < halls.size()) {
        row.addView(hallCard(a, halls.get(i + 1), i + 1), rightLp);
      } else {
        row.addView(new Space(a), rightLp);
      }
      LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
      rowLp.bottomMargin = Ui.dp(a, 10);
      page.addView(row, rowLp);
    }
  }

  private static LinearLayout hallCard(MainActivity a, Hall hall, int index) {
    LinearLayout card = Ui.col(a);
    card.setBackground(Ui.ripple(a, Design.PAPER, Design.BORDER, 14));
    card.setClipToOutline(true);
    card.setElevation(Ui.dp(a, 1));
    String image = hall.coverImageUrl();
    if (image == null || image.isBlank()) image = getHallFallbackImage(hall, index);
    FrameLayout cover = new FrameLayout(a);
    cover.addView(Ui.ratioImage(a, Ui.thumb(image), hall.name(), CARD_COVER_RATIO),
        new FrameLayout.LayoutParams(-1, -2));
    if (hall.averageScore() != null) {
      TextView scorePill = Ui.text(a,
          "★ " + hall.averageScore().stripTrailingZeros().toPlainString(), 11, 0xffffffff);
      scorePill.setTypeface(Typeface.create(Design.body(), Typeface.BOLD));
      scorePill.setPadding(Ui.dp(a, 7), Ui.dp(a, 3), Ui.dp(a, 7), Ui.dp(a, 3));
      scorePill.setBackground(Ui.shape(a, 0xcc2b1c14, 0, 10));
      FrameLayout.LayoutParams pillLp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
      pillLp.setMargins(0, Ui.dp(a, 8), Ui.dp(a, 8), 0);
      cover.addView(scorePill, pillLp);
    }
    card.addView(cover, new LinearLayout.LayoutParams(-1, -2));
    LinearLayout body = Ui.col(a);
    body.setPadding(Ui.dp(a, 10), Ui.dp(a, 10), Ui.dp(a, 10), Ui.dp(a, 12));
    TextView name = Ui.title(a, hall.name(), 16);
    name.setMaxLines(1);
    name.setEllipsize(android.text.TextUtils.TruncateAt.END);
    body.addView(name);
    Ui.gap(body, 4);
    TextView meta = Ui.text(a, hallMetaLabel(a, hall), 12, Design.MUTED);
    meta.setMaxLines(1);
    meta.setEllipsize(android.text.TextUtils.TruncateAt.END);
    body.addView(meta);
    card.addView(body, new LinearLayout.LayoutParams(-1, -2));
    card.setFocusable(true);
    card.setClickable(true);
    card.setTag("dining-hall:" + hall.id());
    card.setContentDescription(hall.name() + "，查看档口和菜品");
    card.setOnClickListener(v -> a.go("stalls", hall.id()));
    return card;
  }

  static void stalls(MainActivity a, LinearLayout page, MainActivity.Route route) {
    Hall hall=a.repo.hall(route.id());
    if(hall==null||!hall.active())throw new IllegalArgumentException("食堂不存在或已停用");
    page.setPadding(0,0,0,0);
    LinearLayout heading=Ui.col(a);
    heading.setBackgroundColor(Design.PAPER);
    heading.setPadding(Ui.dp(a,14),Ui.dp(a,12),Ui.dp(a,14),Ui.dp(a,10));

    LinearLayout headerCard=Ui.row(a);
    headerCard.setGravity(Gravity.CENTER_VERTICAL);
    headerCard.setPadding(Ui.dp(a,12),Ui.dp(a,12),Ui.dp(a,12),Ui.dp(a,12));
    headerCard.setBackground(Ui.shape(a,Design.ALT,Design.BORDER,14));

    String coverUrl=hall.coverImageUrl();
    if(coverUrl!=null&&!coverUrl.isBlank()){
      View thumb=Ui.image(a,Ui.thumb(coverUrl),hall.name(),false);
      thumb.setBackground(Ui.shape(a,Design.PAPER,0,10));
      thumb.setClipToOutline(true);
      LinearLayout.LayoutParams tlp=new LinearLayout.LayoutParams(Ui.dp(a,56),Ui.dp(a,56));
      tlp.rightMargin=Ui.dp(a,12);
      headerCard.addView(thumb,tlp);
    }

    LinearLayout metaCol=Ui.col(a);
    TextView titleView=Ui.title(a,hall.name(),20);
    titleView.setMaxLines(1);
    titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
    metaCol.addView(titleView);
    Ui.gap(metaCol,4);

    LinearLayout subRow=Ui.row(a);
    subRow.setGravity(Gravity.CENTER_VERTICAL);
    if(hall.averageScore()!=null){
      TextView score=Ui.text(a,"★ "+hall.averageScore().stripTrailingZeros().toPlainString(),12,Design.GOLD);
      score.setTypeface(Typeface.create(Design.body(),Typeface.BOLD));
      subRow.addView(score);
      TextView dot=Ui.text(a," · ",12,Design.MUTED);
      subRow.addView(dot);
    }
    subRow.addView(Ui.text(a,a.repo.stalls(hall.id()).size()+" 个档口",12,Design.MUTED));
    metaCol.addView(subRow);
    headerCard.addView(metaCol,new LinearLayout.LayoutParams(0,-2,1));

    LinearLayout actionsCol=Ui.col(a);
    actionsCol.setGravity(Gravity.END);
    TextView reviews=Ui.text(a,"评价 ›",12,Design.RED);
    reviews.setTypeface(Typeface.create(Design.body(),Typeface.BOLD));
    reviews.setGravity(Gravity.CENTER);
    reviews.setMinHeight(Ui.dp(a,32));
    reviews.setPadding(Ui.dp(a,10),Ui.dp(a,4),Ui.dp(a,10),Ui.dp(a,4));
    reviews.setBackground(Ui.ripple(a,Design.RED_SOFT,0,16));
    reviews.setFocusable(true);reviews.setClickable(true);
    reviews.setOnClickListener(v->a.go("venue-reviews","hall~"+hall.id()+"~all"));
    actionsCol.addView(reviews);
    Ui.gap(actionsCol,6);
    TextView info=Ui.text(a,"信息 ⌄",12,Design.MUTED);
    info.setGravity(Gravity.CENTER);
    info.setMinHeight(Ui.dp(a,28));
    info.setPadding(Ui.dp(a,10),Ui.dp(a,2),Ui.dp(a,10),Ui.dp(a,2));
    info.setBackground(Ui.ripple(a,Design.PAPER,Design.BORDER,14));
    info.setFocusable(true);info.setClickable(true);
    info.setContentDescription("食堂信息");
    info.setOnClickListener(v->{
      LinearLayout body=Ui.col(a);body.setPadding(Ui.dp(a,20),Ui.dp(a,12),Ui.dp(a,20),Ui.dp(a,20));
      body.addView(Ui.text(a,VenueScreen.scoreLabel(hall.averageScore(),hall.reviewCount()),14,Design.MUTED));Ui.gap(body,12);
      CatalogQuickActions.show(a,body,"halls",hall.id());
      Ui.action(body,"投稿档口",false,()->a.go("submit-stall",hall.id()));
      new android.app.AlertDialog.Builder(a).setTitle(hall.name()).setView(body).setNegativeButton("关闭",null).show();
    });
    actionsCol.addView(info);
    headerCard.addView(actionsCol,new LinearLayout.LayoutParams(-2,-2));

    heading.addView(headerCard,new LinearLayout.LayoutParams(-1,-2));
    page.addView(heading);
    View topRule=new View(a);topRule.setBackgroundColor(Design.BORDER);
    page.addView(topRule,new LinearLayout.LayoutParams(-1,Ui.dp(a,1)));
    if(a.repo.stalls(hall.id()).isEmpty()){Ui.empty(page,"暂无档口","");return;}
    new CanteenMenu(a,page,a.repo.stalls(hall.id()));
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
