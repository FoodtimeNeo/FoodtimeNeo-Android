package cn.edu.bjtu.foodtime.presentation;

import android.view.*;
import android.widget.*;
import cn.edu.bjtu.foodtime.domain.model.Models.*;
import java.util.*;

/** Append requested comment pages without remounting existing reply editors or pending likes. */
final class DishReviewSection {
  private final MainActivity a;
  private final String dish;
  private final LinearLayout list;
  private final TextView status,error,refresh,more;
  private final Runnable refreshed;
  private final Set<String> shown=new HashSet<>();
  private final Map<View,Boolean> disabled=new IdentityHashMap<>();
  private boolean loading;
  DishReviewSection(MainActivity activity,LinearLayout parent,String dishId,Runnable onRefresh) {
    a=activity;dish=dishId;refreshed=onRefresh;
    status=Ui.text(a,"",14,Design.MUTED);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);parent.addView(status);
    list=Ui.col(a);parent.addView(list);
    error=Ui.text(a,"",14,Design.RED);error.setVisibility(View.GONE);Ui.observeError(error);
    error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);parent.addView(error);
    ReviewActionRow actions=new ReviewActionRow(a);parent.addView(actions);
    refresh=ReviewActions.button(a,"刷新评论",() -> load(false));actions.addView(refresh);
    more=ReviewActions.button(a,"加载更多评论",() -> load(true));actions.addView(more);
    paint(true);
  }
  private void paint(boolean reset) {
    if(reset) {list.removeAllViews();shown.clear();}
    ReviewPage page=a.repo.dishReviews(dish);
    if(reset&&page.items().isEmpty()) Ui.empty(list,"暂无评论","");
    for(Review review:page.items()) if(shown.add(review.id())) card(review);
    status.setText("已显示 "+shown.size()+" / "+page.total()+" 条评论"+(loading?" · 正在加载…":""));
    more.setVisibility(page.hasMore()?View.VISIBLE:View.GONE);
    more.setEnabled(!loading);refresh.setEnabled(!loading);
  }
  private void load(boolean append) {
    if(loading) return;
    if(a.operationInFlight()) {a.message("正在提交，请稍候");return;}
    loading=true;refresh.setEnabled(false);more.setEnabled(false);
    status.setText("正在加载评论…");
    if(!append) disable(list);
    a.read("dish-reviews:"+dish,append?more:refresh,error,
        () -> {if(append) a.repo.loadMoreDishReviews(dish);else a.repo.loadDishReviews(dish);},
        () -> {paint(!append);if(!append) refreshed.run();},
        () -> {loading=false;disabled.forEach(View::setEnabled);disabled.clear();
          refresh.setEnabled(true);more.setEnabled(true);ReviewPage page=a.repo.dishReviews(dish);
          status.setText("已显示 "+shown.size()+" / "+page.total()+" 条评论");});
  }
  private void disable(View view) {
    disabled.put(view,view.isEnabled());view.setEnabled(false);
    if(view instanceof ViewGroup group) for(int i=0;i<group.getChildCount();i++) disable(group.getChildAt(i));
  }
  private void card(Review r) {
    LinearLayout card=Ui.card(list),head=Ui.row(a);card.addView(head);
    TextView author=Ui.text(a,r.userDisplayName().isEmpty()?"校园干饭人":r.userDisplayName(),16,Design.INK);
    author.setTypeface(Design.body(),android.graphics.Typeface.BOLD);head.addView(author,new LinearLayout.LayoutParams(0,-2,1));
    head.addView(Ui.stamp(a,r.rating()+" / 5",Design.RED));Ui.gap(card,5);
    card.addView(Ui.text(a,r.content(),16,Design.INK));Ui.gap(card,8);
    EmbeddedReviewReplies.attach(a,card,r);
  }
}
