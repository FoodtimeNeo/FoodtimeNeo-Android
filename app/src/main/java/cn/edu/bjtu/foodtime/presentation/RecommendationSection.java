package cn.edu.bjtu.foodtime.presentation;

import android.view.*;
import android.widget.*;
import cn.edu.bjtu.foodtime.domain.model.Models.*;
import cn.edu.bjtu.foodtime.domain.repository.ContentRequest;

/** Independent section: recommendation failures leave the dining catalog usable. */
final class RecommendationSection {
  static void show(MainActivity a,LinearLayout page) {
    LinearLayout section=Ui.col(a);section.setTag("daily-recommendations");
    section.addView(Ui.title(a,"今日推荐",24));Ui.gap(section,8);
    LinearLayout body=Ui.col(a);section.addView(body);
    TextView error=Ui.text(a,"",14,Design.RED);error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);section.addView(error);
    TextView retry=Ui.button(a,"重试今日推荐",false,()->{});retry.setTag("daily-recommendation-retry");section.addView(retry);retry.setVisibility(View.GONE);
    Runnable load=()->{
      body.removeAllViews();retry.setVisibility(View.GONE);
      DailyRecommendations cached=a.repo.dailyRecommendations();
      TextView loading=null;
      if(cached!=null)render(a,body,cached);
      else {
        loading=Ui.text(a,"正在加载今日推荐…",14,Design.MUTED);
        loading.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);body.addView(loading);
      }
      final TextView pending=loading;
      a.read("daily-recommendations",retry,error,
          ()->a.repo.refresh(new ContentRequest(ContentRequest.Query.DAILY_RECOMMENDATIONS,"")),
          ()->{retry.setVisibility(View.GONE);body.removeAllViews();render(a,body,a.repo.dailyRecommendations());},
          ()->{if(pending!=null)body.removeView(pending);retry.setVisibility(View.VISIBLE);});
    };
    retry.setOnClickListener(v->load.run());
    page.addView(section,new LinearLayout.LayoutParams(-1,-2));Ui.gap(page,24);load.run();
  }
  private static void render(MainActivity a,LinearLayout body,DailyRecommendations data) {
    if(data==null){body.addView(Ui.text(a,"今日推荐已失效，请重新进入食堂页",14,Design.MUTED));return;}
    if(data.items().isEmpty()){Ui.empty(body,"今日暂无推荐","先逛逛食堂吧，有可推荐的菜品后会在这里展示。");return;}
    body.addView(Ui.text(a,data.date().toString()+" · 向左滑动查看更多",12,Design.MUTED));Ui.gap(body,10);
    HorizontalScrollView scroll=new HorizontalScrollView(a);scroll.setHorizontalScrollBarEnabled(false);
    LinearLayout cards=Ui.row(a);cards.setGravity(Gravity.TOP);
    int width=Ui.dp(a,Math.min(270,Math.max(220,a.getResources().getConfiguration().screenWidthDp-52)));
    for(RecommendedDish dish:data.items()) {
      LinearLayout card=Ui.col(a);card.setBackground(Ui.ripple(a,Design.PAPER,Design.BORDER,18));card.setClipToOutline(true);
      card.setTag("daily-recommendation-card:"+dish.dishId());
      card.addView(Ui.ratioImage(a,Ui.thumb(dish.imageUrl()),dish.imageUrl()==null||dish.imageUrl().isBlank()?"暂未上传图片":dish.dishName(),.55f),new LinearLayout.LayoutParams(-1,-2));
      LinearLayout text=Ui.col(a);text.setPadding(Ui.dp(a,14),Ui.dp(a,12),Ui.dp(a,14),Ui.dp(a,14));
      text.addView(Ui.title(a,dish.dishName(),20));Ui.gap(text,6);
      text.addView(Ui.text(a,VenueScreen.scoreLabel(dish.averageRating(),dish.reviewCount()),14,Design.RED));Ui.gap(text,6);
      text.addView(Ui.text(a,dish.diningHallName()+" · "+dish.stallName(),14,Design.INK));Ui.gap(text,6);
      text.addView(Ui.text(a,dish.reason(),14,Design.MUTED));card.addView(text,new LinearLayout.LayoutParams(-1,-2));
      card.setFocusable(true);card.setClickable(true);card.setContentDescription(dish.dishName()+"，"+dish.diningHallName()+"，"+dish.stallName()+"，查看菜品详情");
      card.setOnClickListener(v->a.go("detail",dish.dishId()));
      LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(width,-2);lp.rightMargin=Ui.dp(a,12);cards.addView(card,lp);
    }
    scroll.addView(cards);body.addView(scroll,new LinearLayout.LayoutParams(-1,-2));
  }
  private RecommendationSection(){}
}
