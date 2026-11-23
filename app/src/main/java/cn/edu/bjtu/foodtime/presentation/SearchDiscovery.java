package cn.edu.bjtu.foodtime.presentation;

import android.view.*;
import android.widget.*;
import cn.edu.bjtu.foodtime.domain.model.Models.*;
import cn.edu.bjtu.foodtime.domain.repository.ContentRequest;
import java.util.*;

/** Search discovery reuses the daily stall response and existing stall navigation. */
final class SearchDiscovery {
  private final MainActivity activity;
  private final LinearLayout body;
  private final TextView error, retry;
  private final String requestKey="search-discovery:"+UUID.randomUUID();

  SearchDiscovery(MainActivity activity,LinearLayout parent) {
    this.activity=activity;
    body=Ui.col(activity);parent.addView(body);
    error=Ui.text(activity,"",13,Design.RED);Ui.observeError(error);parent.addView(error);
    retry=Ui.button(activity,"重试",false,this::load);retry.setTag("search-discovery-retry");parent.addView(retry);
    load();
  }

  private void load() {
    error.setText("");retry.setVisibility(View.GONE);
    render(activity.repo.dailyRecommendations());
    activity.read(requestKey,retry,error,
        ()->activity.repo.refresh(new ContentRequest(ContentRequest.Query.DAILY_RECOMMENDATIONS,"")),
        ()->render(activity.repo.dailyRecommendations()),
        ()->retry.setVisibility(error.length()>0?View.VISIBLE:View.GONE));
  }

  private void render(DailyRecommendations data) {
    body.removeAllViews();Ui.gap(body,18);
    if(data==null){body.addView(Ui.text(activity,"正在读取推荐…",14,Design.MUTED));return;}
    if(data.items().isEmpty()){body.addView(Ui.text(activity,"今日暂无推荐",14,Design.MUTED));return;}
    body.addView(Ui.title(activity,"发现",20));Ui.gap(body,10);
    int maxWidth=Ui.dp(activity,activity.getResources().getConfiguration().screenWidthDp-32),used=0;
    LinearLayout row=Ui.row(activity);body.addView(row);
    for(RecommendedStall item:data.items()) {
      TextView chip=Ui.button(activity,item.stallName(),false,()->open(item));chip.setTextSize(14);
      chip.setBackground(Ui.ripple(activity,Design.ALT,0,18));chip.setTag("search-discovery-chip:"+item.stallId());
      chip.setMaxWidth(maxWidth);chip.measure(View.MeasureSpec.makeMeasureSpec(maxWidth,View.MeasureSpec.AT_MOST),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
      int width=chip.getMeasuredWidth()+Ui.dp(activity,8);
      if(used>0&&used+width>maxWidth){Ui.gap(body,6);row=Ui.row(activity);body.addView(row);used=0;}
      LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.rightMargin=Ui.dp(activity,8);row.addView(chip,lp);used+=width;
    }
    Ui.gap(body,20);body.addView(Ui.title(activity,"猜你想搜",20));Ui.gap(body,12);
    int columns=activity.getResources().getConfiguration().fontScale>1.4f?1:2;
    LinearLayout cards=null;
    for(int i=0;i<data.items().size();i++) {
      if(i%columns==0){cards=Ui.row(activity);cards.setGravity(Gravity.TOP);body.addView(cards);Ui.gap(body,12);}
      RecommendedStall item=data.items().get(i);LinearLayout card=Ui.col(activity);
      card.setTag("search-discovery-card:"+item.stallId());card.setBackground(Ui.ripple(activity,Design.PAPER,Design.BORDER,14));card.setClipToOutline(true);
      card.addView(Ui.ratioImage(activity,Ui.thumb(item.imageUrl()),item.stallName(),1f));
      LinearLayout copy=Ui.col(activity);copy.setPadding(Ui.dp(activity,10),Ui.dp(activity,10),Ui.dp(activity,10),Ui.dp(activity,12));
      TextView name=Ui.text(activity,item.stallName(),16,Design.INK);name.setTypeface(Design.body(),android.graphics.Typeface.BOLD);name.setMaxLines(2);copy.addView(name);
      Ui.gap(copy,6);TextView location=Ui.text(activity,item.diningHallName(),12,Design.MUTED);location.setMaxLines(2);copy.addView(location);
      if(!item.reason().isBlank()){Ui.gap(copy,5);TextView reason=Ui.text(activity,item.reason(),12,Design.MUTED);reason.setMaxLines(2);copy.addView(reason);}
      card.addView(copy);card.setFocusable(true);card.setClickable(true);card.setContentDescription(item.diningHallName()+"，"+item.stallName()+"，查看档口菜品");card.setOnClickListener(v->open(item));
      LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);if(i%columns!=0)lp.leftMargin=Ui.dp(activity,10);cards.addView(card,lp);
      if(i==data.items().size()-1&&columns==2&&i%2==0){View spacer=new View(activity);LinearLayout.LayoutParams blank=new LinearLayout.LayoutParams(0,1,1);blank.leftMargin=Ui.dp(activity,10);cards.addView(spacer,blank);}
    }
  }

  private void open(RecommendedStall item){activity.go("dishes",item.stallId());}
}
