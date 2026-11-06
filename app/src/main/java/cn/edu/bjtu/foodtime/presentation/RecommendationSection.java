package cn.edu.bjtu.foodtime.presentation;

import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import cn.edu.bjtu.foodtime.domain.model.Models.*;
import cn.edu.bjtu.foodtime.domain.repository.ContentRequest;

/** Independent section: recommendation failures leave the dining catalog usable. */
final class RecommendationSection {
  private static final int CARD_IMAGE_HEIGHT_DP=154;
  private static final int CARD_HEIGHT_DP=342;

  static void show(MainActivity a,LinearLayout page) {
    LinearLayout section=Ui.col(a);section.setTag("daily-recommendations");
    header(a,section);Ui.gap(section,10);
    LinearLayout body=Ui.col(a);section.addView(body);
    TextView error=Ui.text(a,"",13,Design.RED);
    error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);section.addView(error);
    TextView retry=Ui.button(a,"再试一次",false,()->{});
    retry.setTag("daily-recommendation-retry");section.addView(retry);retry.setVisibility(View.GONE);
    Runnable load=()->{
      body.removeAllViews();error.setText("");retry.setVisibility(View.GONE);
      DailyRecommendations cached=a.repo.dailyRecommendations();
      View loading=null;
      if(cached!=null)render(a,body,cached);
      else {loading=loading(a);body.addView(loading,new LinearLayout.LayoutParams(-1,-2));}
      final View pending=loading;
      a.read("daily-recommendations",retry,error,
          ()->a.repo.refresh(new ContentRequest(ContentRequest.Query.DAILY_RECOMMENDATIONS,"")),
          ()->{retry.setVisibility(View.GONE);body.removeAllViews();render(a,body,a.repo.dailyRecommendations());},
          ()->{if(pending!=null)body.removeView(pending);retry.setVisibility(View.VISIBLE);});
    };
    retry.setOnClickListener(v->load.run());
    page.addView(section,new LinearLayout.LayoutParams(-1,-2));Ui.gap(page,24);load.run();
  }

  private static void header(MainActivity a,LinearLayout section) {
    LinearLayout row=Ui.row(a);row.setGravity(Gravity.CENTER_VERTICAL);
    LinearLayout words=Ui.col(a);words.addView(Ui.title(a,"今日推荐",25));
    TextView note=Ui.note(a,"今天吃点不一样的");note.setTextSize(17);note.setTextColor(Design.RED);
    words.addView(note);row.addView(words,new LinearLayout.LayoutParams(0,-2,1));
    View doodle=Ui.foodDoodle(a,1,Design.GREEN);
    row.addView(doodle,new LinearLayout.LayoutParams(Ui.dp(a,64),Ui.dp(a,48)));
    section.addView(row,new LinearLayout.LayoutParams(-1,-2));
  }

  private static View loading(MainActivity a) {
    LinearLayout card=Ui.row(a);card.setGravity(Gravity.CENTER_VERTICAL);
    card.setPadding(Ui.dp(a,14),Ui.dp(a,12),Ui.dp(a,14),Ui.dp(a,12));
    card.setBackground(Ui.shape(a,Design.ALT,0,18));
    View doodle=Ui.foodDoodle(a,0,Design.RED);
    card.addView(doodle,new LinearLayout.LayoutParams(Ui.dp(a,58),Ui.dp(a,46)));
    TextView text=Ui.text(a,"今日菜单正在翻页…",14,Design.MUTED);
    text.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.leftMargin=Ui.dp(a,10);
    card.addView(text,lp);return card;
  }

  private static void render(MainActivity a,LinearLayout body,DailyRecommendations data) {
    if(data==null){body.addView(Ui.text(a,"今日推荐已失效，请重新进入食堂页",14,Design.MUTED));return;}
    if(data.items().isEmpty()){empty(a,body);return;}
    HorizontalScrollView scroll=new HorizontalScrollView(a);scroll.setHorizontalScrollBarEnabled(false);
    scroll.setClipToPadding(false);scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
    LinearLayout cards=Ui.row(a);cards.setGravity(Gravity.TOP);
    cards.setPadding(0,Ui.dp(a,8),Ui.dp(a,18),Ui.dp(a,12));
    int visibleCount=Math.min(5,data.items().size());
    for(int i=0;i<visibleCount;i++)card(a,cards,data.items().get(i),i);
    scroll.addView(cards,new HorizontalScrollView.LayoutParams(-2,-2));
    body.addView(scroll,new LinearLayout.LayoutParams(-1,-2));
  }

  private static void card(MainActivity a,LinearLayout cards,RecommendedDish dish,int index) {
    LinearLayout card=Ui.col(a);int fill=index%3==1?Design.ALT:Design.PAPER;
    card.setBackground(Ui.ripple(a,fill,Design.BORDER,20));card.setClipToOutline(true);
    card.setElevation(Ui.dp(a,3));card.setTag("daily-recommendation-card:"+dish.dishId());
    if(dish.imageUrl()!=null&&!dish.imageUrl().isBlank()) {
      View image=Ui.ratioImage(a,Ui.thumb(dish.imageUrl()),dish.dishName(),.68f);
      card.addView(image,new LinearLayout.LayoutParams(-1,Ui.dp(a,CARD_IMAGE_HEIGHT_DP)));
    } else {
      FrameLayout sketch=new FrameLayout(a);sketch.setBackgroundColor(index%2==0?Design.KRAFT_SOFT:Design.ALT);
      View mark=Ui.foodDoodle(a,index+4,index%2==0?Design.RED:Design.GREEN);
      sketch.addView(mark,new FrameLayout.LayoutParams(Ui.dp(a,92),Ui.dp(a,70),Gravity.CENTER));
      TextView waiting=Ui.note(a,"敬请期待");waiting.setTextSize(16);
      waiting.setGravity(Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
      sketch.addView(waiting,new FrameLayout.LayoutParams(-1,Ui.dp(a,42),Gravity.BOTTOM));
      card.addView(sketch,new LinearLayout.LayoutParams(-1,Ui.dp(a,CARD_IMAGE_HEIGHT_DP)));
    }

    LinearLayout text=Ui.col(a);text.setPadding(Ui.dp(a,14),Ui.dp(a,12),Ui.dp(a,14),Ui.dp(a,14));
    LinearLayout nameRow=Ui.row(a);nameRow.setGravity(Gravity.CENTER_VERTICAL);
    TextView name=Ui.title(a,dish.dishName(),20);name.setMaxLines(2);
    nameRow.addView(name,new LinearLayout.LayoutParams(0,-2,1));
    text.addView(nameRow);Ui.gap(text,8);
    TextView where=Ui.text(a,dish.diningHallName()+" · "+dish.stallName(),12,Design.MUTED);
    where.setMaxLines(1);text.addView(where);Ui.gap(text,7);
    if(dish.reviewCount()>0) {
      LinearLayout chips=Ui.row(a);chips.setGravity(Gravity.START);
      chips.addView(chip(a,dish.reviewCount()+"条评价",Design.GREEN));
      text.addView(chips);Ui.gap(text,10);
    }
    text.addView(new Space(a),new LinearLayout.LayoutParams(1,0,1));
    TextView open=Ui.note(a,"查看详情");open.setTextSize(16);open.setTextColor(Design.BLUE);open.setGravity(Gravity.END);
    text.addView(open);card.addView(text,new LinearLayout.LayoutParams(-1,0,1));
    card.setFocusable(true);card.setClickable(true);
    card.setContentDescription(dish.dishName()+"，"+dish.diningHallName()+"，"+dish.stallName()+"，查看菜品详情");
    card.setOnClickListener(v->a.go("detail",dish.dishId()));
    int width=Ui.dp(a,Math.min(250,Math.max(224,a.getResources().getConfiguration().screenWidthDp-70)));
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(width,Ui.dp(a,CARD_HEIGHT_DP));
    lp.rightMargin=Ui.dp(a,12);lp.topMargin=Ui.dp(a,index%2==0?0:10);cards.addView(card,lp);
  }

  private static TextView chip(MainActivity a,String label,int color) {
    TextView chip=Ui.text(a,label,11,color);chip.setSingleLine(true);
    chip.setPadding(Ui.dp(a,8),Ui.dp(a,4),Ui.dp(a,8),Ui.dp(a,4));
    GradientDrawable background=Ui.shape(a,Design.PAPER,color,12);chip.setBackground(background);return chip;
  }

  private static void empty(MainActivity a,LinearLayout body) {
    LinearLayout card=Ui.col(a);card.setGravity(Gravity.CENTER);
    card.setPadding(0,Ui.dp(a,18),0,Ui.dp(a,18));card.setBackground(Ui.shape(a,Design.ALT,0,20));
    View doodle=Ui.foodDoodle(a,8,Design.GREEN);
    card.addView(doodle,new LinearLayout.LayoutParams(Ui.dp(a,92),Ui.dp(a,62)));
    card.addView(Ui.title(a,"今日暂无推荐",19));Ui.gap(card,6);
    TextView note=Ui.note(a,"今日清单还在冒热气，先逛逛食堂吧。");note.setTextSize(16);card.addView(note);
    body.addView(card,new LinearLayout.LayoutParams(-1,-2));
  }

  private RecommendationSection(){}
}
