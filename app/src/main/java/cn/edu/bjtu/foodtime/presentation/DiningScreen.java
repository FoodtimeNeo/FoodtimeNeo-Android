package cn.edu.bjtu.foodtime.presentation;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.widget.*;
import cn.edu.bjtu.foodtime.domain.model.Models.*;
import java.util.List;

final class DiningScreen {
  private static final float[] COVER_RATIOS={1.06f,.74f,.86f,1.18f,.78f,1.02f};
  private static final String[] CARD_NOTES={
      "热气腾腾的一站","下课顺路去尝尝","今天也要好好吃饭",
      "闻着香味慢慢逛","一餐一味，都是日常","把校园装进味蕾"
  };

  static void halls(MainActivity a, LinearLayout page, MainActivity.Route route) {
    page.setBackground(new FoodDoodleDrawable(a));
    a.heading(page,"01","食堂手账","今天的好心情，从一顿热饭开始。");
    hero(a,page);
    if (a.repo.halls().isEmpty()) {
      Ui.empty(page, "暂无食堂", "");
      return;
    }
    CatalogList.showGallery(a,page,"想去哪家食堂？","个食堂",a.repo.halls(),Hall::name,
        (list,halls) -> hallGallery(a,list,halls));
  }

  private static void hero(MainActivity a,LinearLayout page) {
    LinearLayout hero=Ui.col(a);
    hero.setPadding(Ui.dp(a,18),Ui.dp(a,18),Ui.dp(a,18),Ui.dp(a,16));
    hero.setBackground(Ui.shape(a,Design.ALT,0,22));
    LinearLayout top=Ui.row(a);top.setGravity(Gravity.TOP);
    TextView callout=Ui.note(a,"循着香气走，\n总有一餐在等你");
    callout.setTextColor(Design.RED);callout.setTextSize(25);callout.setLineSpacing(Ui.dp(a,5),1);
    callout.setGravity(Gravity.CENTER_VERTICAL);
    top.addView(callout,new LinearLayout.LayoutParams(0,Ui.dp(a,88),1));
    TextView bowl=Ui.text(a,"♨",36,Design.GREEN);bowl.setGravity(Gravity.CENTER);
    top.addView(bowl,new LinearLayout.LayoutParams(Ui.dp(a,58),Ui.dp(a,58)));
    hero.addView(top);Ui.gap(hero,14);
    LinearLayout sketches=Ui.row(a);sketches.setGravity(Gravity.CENTER_HORIZONTAL);
    int[] sketchKinds={0,3,4};
    for(int i=0;i<sketchKinds.length;i++) {
      LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,Ui.dp(a,42),1);
      if(i>0) lp.leftMargin=Ui.dp(a,12);
      sketches.addView(Ui.foodDoodle(a,sketchKinds[i],i==1?Design.GREEN:Design.RED),lp);
    }
    hero.addView(sketches);
    page.addView(hero,new LinearLayout.LayoutParams(-1,-2));
    Ui.gap(page,20);
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
      if(i==1&&halls.size()>3) {
        int sloganColumn=heights[0]<=heights[1]?0:1;
        sloganCard(a,sloganColumn==0?left:right);
        heights[sloganColumn]+=.62f;
      }
    }
    page.addView(waterfall,new LinearLayout.LayoutParams(-1,-2));
  }

  private static void hallCard(MainActivity a,LinearLayout column,Hall hall,int index,float ratio) {
    LinearLayout card=Ui.col(a);
    card.setBackground(Ui.ripple(a,Design.PAPER,Design.BORDER,18));
    card.setClipToOutline(true);card.setElevation(Ui.dp(a,2));
    String image=hall.coverImageUrl();
    if(image==null||image.isBlank()) image="asset:hall_"+(index%6+1)+".webp";
    card.addView(Ui.ratioImage(a,Ui.thumb(image),hall.name(),ratio),new LinearLayout.LayoutParams(-1,-2));
    LinearLayout body=Ui.col(a);body.setPadding(Ui.dp(a,12),Ui.dp(a,12),Ui.dp(a,12),Ui.dp(a,14));
    TextView number=Ui.text(a,String.format("第 %02d 站",index+1),11,index%3==1?Design.GREEN:Design.RED);
    number.setLetterSpacing(.08f);body.addView(number);Ui.gap(body,6);
    TextView name=Ui.title(a,hall.name(),20);name.setMaxLines(2);body.addView(name);
    Ui.gap(body,8);body.addView(Ui.text(a,CARD_NOTES[index%CARD_NOTES.length],12,Design.MUTED));
    card.addView(body,new LinearLayout.LayoutParams(-1,-2));
    card.setFocusable(true);card.setClickable(true);
    card.setContentDescription(hall.name()+"，查看档口");
    card.setOnClickListener(v -> a.go("stalls",hall.id()));
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=Ui.dp(a,12);
    column.addView(card,lp);
  }

  private static void sloganCard(MainActivity a,LinearLayout column) {
    LinearLayout card=Ui.col(a);
    card.setPadding(Ui.dp(a,14),Ui.dp(a,18),Ui.dp(a,14),Ui.dp(a,18));
    card.setBackground(Ui.shape(a,Design.KRAFT_SOFT,0,18));
    TextView mark=Ui.note(a,"“");mark.setTextSize(32);mark.setTextColor(Design.RED);card.addView(mark);
    TextView words=Ui.note(a,"认真吃饭，\n就是今日份的小确幸。");
    words.setTextColor(Design.INK);words.setTextSize(21);card.addView(words);
    Ui.gap(card,10);card.addView(Ui.text(a,"— 北交干饭指南",11,Design.MUTED));
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=Ui.dp(a,12);
    column.addView(card,lp);
  }

  static void stalls(MainActivity a, LinearLayout page, MainActivity.Route route) {
    Hall hall = a.repo.hall(route.id());
    if (hall == null || !hall.active()) throw new IllegalArgumentException("食堂不存在或已停用");
    a.heading(page, "01.1", hall.name(), "");
    if (a.repo.stalls(hall.id()).isEmpty()) {
      Ui.empty(page, "暂无档口", "");
      Ui.action(page, "投稿一个新档口", true, () -> a.go("submit-stall", hall.id()));
      return;
    }
    CatalogList.show(a, page, "搜索档口", "当前食堂的档口名称", "个档口", a.repo.stalls(hall.id()), Stall::name,
        (list, stall) -> cover(a, list, stall.name(), stall.coverImageUrl(), () -> a.go("dishes", stall.id())));
    Ui.action(page, "投稿一个新档口", true, () -> a.go("submit-stall", hall.id()));
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
