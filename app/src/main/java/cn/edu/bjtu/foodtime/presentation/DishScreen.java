package cn.edu.bjtu.foodtime.presentation;

import android.view.*;
import android.widget.*;
import cn.edu.bjtu.foodtime.domain.model.Models.*;
import java.util.*;

final class DishScreen {

  static void dishes(MainActivity a, LinearLayout page, MainActivity.Route route) {
    Stall stall = a.repo.stall(route.id());
    if (stall == null || !stall.active()) throw new IllegalArgumentException("档口不存在或已停用");
    a.heading(page, "01.2", stall.name(), "");
    List<Dish> all = a.repo.dishes(stall.id());
    if (all.isEmpty()) Ui.empty(page, "还没有菜品", "");
    else CatalogList.showGallery(a,page,"搜索菜品","道菜品",all,Dish::name,
        (list,dishes) -> dishGrid(a,list,dishes));
    Ui.gap(page, 16);
    Ui.action(page, "投稿一道新菜", true, () -> a.go("submit-dish", stall.id()));
  }

  private static void dishGrid(MainActivity a,LinearLayout page,List<Dish> dishes) {
    LinearLayout grid=Ui.row(a);grid.setGravity(Gravity.TOP);
    LinearLayout left=Ui.col(a),right=Ui.col(a);
    LinearLayout.LayoutParams ll=new LinearLayout.LayoutParams(0,-2,1);ll.rightMargin=Ui.dp(a,6);
    LinearLayout.LayoutParams rl=new LinearLayout.LayoutParams(0,-2,1);rl.leftMargin=Ui.dp(a,6);
    grid.addView(left,ll);grid.addView(right,rl);
    float[] height={0,0};
    for(int i=0;i<dishes.size();i++) {
      Dish dish=dishes.get(i);int column=height[0]<=height[1]?0:1;
      dishCard(a,column==0?left:right,dish);height[column]+=1f+Math.min(2,dish.name().length()/8)*.18f;
    }
    page.addView(grid,new LinearLayout.LayoutParams(-1,-2));
  }

  private static void dishCard(MainActivity a,LinearLayout column,Dish dish) {
    LinearLayout card=Ui.col(a);card.setBackground(Ui.ripple(a,Design.PAPER,Design.BORDER,20));
    card.setClipToOutline(true);card.setElevation(Ui.dp(a,2));
    LinearLayout body=Ui.col(a);body.setPadding(Ui.dp(a,12),Ui.dp(a,11),Ui.dp(a,12),Ui.dp(a,13));
    TextView name=Ui.title(a,dish.name(),18);name.setMaxLines(2);body.addView(name);Ui.gap(body,7);
    LinearLayout meta=Ui.row(a);TextView price=Ui.title(a,"¥"+dish.price().stripTrailingZeros().toPlainString(),17);
    price.setTextColor(Design.RED);meta.addView(price,new LinearLayout.LayoutParams(0,-2,1));
    body.addView(meta);card.addView(body);
    card.setClickable(true);card.setFocusable(true);card.setContentDescription(dish.name()+"，查看详情");
    card.setOnClickListener(v -> a.go("detail",dish.id()));
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=Ui.dp(a,12);column.addView(card,lp);
  }

  static void detail(MainActivity a, LinearLayout page, MainActivity.Route route) {
    Dish d = a.repo.dish(route.id());
    if (d == null || !d.active()) throw new IllegalArgumentException("菜品不存在或已下架");
    Stall s = a.repo.stall(d.stallId());
    Hall h = a.repo.hall(s.diningHallId());
    a.heading(page, "01.3", d.name(), h.name() + " · " + s.name());
    LinearLayout hero = Ui.card(page);
    hero.setPadding(0, 0, 0, Ui.dp(a, 4));
    FrameLayout photo = detailPhoto(a,d);
    hero.addView(photo);
    TextView upload = Ui.button(a, "+", false, () -> a.go("upload-photo", d.id()));
    upload.setTextSize(24);
    upload.setPadding(0, 0, 0, 0);
    upload.setContentDescription("上传菜品图片");
    FrameLayout.LayoutParams up =
        new FrameLayout.LayoutParams(
            Ui.dp(a, 48), Ui.dp(a, 48), android.view.Gravity.TOP | android.view.Gravity.END);
    up.setMargins(0, Ui.dp(a, 8), Ui.dp(a, 8), 0);
    photo.addView(upload, up);
    TextView album = Ui.button(a, "图册", false, () -> a.go("gallery", d.id()));
    album.setContentDescription("查看菜品图册");
    FrameLayout.LayoutParams al =
        new FrameLayout.LayoutParams(
            -2, Ui.dp(a, 48), android.view.Gravity.BOTTOM | android.view.Gravity.END);
    al.setMargins(0, 0, Ui.dp(a, 8), Ui.dp(a, 8));
    photo.addView(album, al);
    LinearLayout heroText = Ui.row(a);
    heroText.setPadding(Ui.dp(a, 16), Ui.dp(a, 12), Ui.dp(a, 16), Ui.dp(a, 8));
    heroText.addView(
        Ui.title(a, "¥" + d.price().setScale(2), 25), new LinearLayout.LayoutParams(0, -2, 1));
    hero.setOnClickListener(v -> a.go("gallery", d.id()));
    hero.addView(heroText);
    if(!d.description().isBlank()) {
      TextView description=Ui.text(a,d.description(),16,Design.MUTED);
      description.setPadding(Ui.dp(a,16),0,Ui.dp(a,16),Ui.dp(a,16));hero.addView(description);
    }
    LinearLayout score = Ui.card(page);
    score.addView(Ui.title(a, "评分", 22));
    Ui.gap(score, 8);
    TextView scoreValue=Ui.text(a,"",22,Design.RED),scoreCount=Ui.text(a,"",14,Design.MUTED);
    score.addView(scoreValue);score.addView(scoreCount);
    Runnable paintRating=() -> {
      RatingSummary summary=a.repo.rating(d.id());
      float average=summary.averageScore()==null?0:summary.averageScore().floatValue();
      scoreValue.setText(summary.averageScore()==null?"暂无评分":"★".repeat(Math.round(average))+"  "+String.format(Locale.ROOT,"%.1f / 5",average));
      scoreValue.setTextColor(summary.averageScore()==null?Design.MUTED:Design.RED);
      scoreCount.setText(summary.reviewCount()+" 条评价");
    };
    paintRating.run();
    LinearLayout action = Ui.row(a);
    action.setGravity(android.view.Gravity.CENTER);
    action.addView(
        Ui.button(a, "写点评", true, () -> a.go("review", d.id())),
        new LinearLayout.LayoutParams(0, Ui.dp(a, 48), 1));
    page.addView(action);
    Ui.gap(page, 20);
    page.addView(Ui.title(a, "评论", 25));
    Ui.gap(page, 12);
    new DishReviewSection(a,page,d.id(),paintRating);
  }

  private static FrameLayout detailPhoto(MainActivity a,Dish dish) {
    List<Photo> photos=a.repo.photos(dish.id());
    if(!photos.isEmpty()) return (FrameLayout)Ui.image(a,photos.get(0).imageUrl(),dish.name()+"图片",true);
    FrameLayout art=new FrameLayout(a);art.setBackgroundColor(Design.ALT);
    art.setContentDescription("暂无菜品图片，敬请期待");
    View doodle=Ui.foodDoodle(a,5,0x99c9341e);
    FrameLayout.LayoutParams doodleLayout=new FrameLayout.LayoutParams(Ui.dp(a,132),Ui.dp(a,88),Gravity.CENTER);
    doodleLayout.bottomMargin=Ui.dp(a,22);art.addView(doodle,doodleLayout);
    TextView coming=Ui.note(a,"敬请期待");coming.setTextSize(22);coming.setTextColor(Design.RED);coming.setGravity(Gravity.CENTER);
    FrameLayout.LayoutParams textLayout=new FrameLayout.LayoutParams(-1,Ui.dp(a,52),Gravity.BOTTOM);
    textLayout.setMargins(Ui.dp(a,16),0,Ui.dp(a,16),Ui.dp(a,12));art.addView(coming,textLayout);
    art.setLayoutParams(new LinearLayout.LayoutParams(-1,Ui.dp(a,210)));
    return art;
  }

  static void gallery(MainActivity a, LinearLayout page, MainActivity.Route route) {
    Dish d = a.repo.dish(route.id());
    a.heading(page, "01.4", d.name() + " · 图册", "");
    List<Photo> photos = a.repo.photos(d.id());
    if (photos.isEmpty()) Ui.empty(page, "暂无图片", "");
    for (Photo p : photos) {
      LinearLayout c = Ui.card(page);
      c.setPadding(0, 0, 0, Ui.dp(a, 4));
      c.addView(Ui.image(a, p.imageUrl(), "菜品图片", true));
    }
    Ui.action(page, "上传菜品图片", true, () -> a.go("upload-photo", d.id()));
  }
}
