package cn.edu.bjtu.foodtime.presentation;

import android.animation.ValueAnimator;
import android.content.Context;
import android.view.*;
import android.widget.*;

/** Friendly, non-blocking loading states shared by every page. */
final class LoadingScreen {
  static boolean catalog(MainActivity.Route route){return route.kind().equals("stalls")||route.kind().equals("dishes");}
  static String title(MainActivity a,MainActivity.Route route) {
    if(route.kind().equals("stalls")){var hall=a.repo.hall(route.id());return hall==null?"档口":hall.name();}
    var stall=a.repo.stall(route.id());return stall==null?"菜品":stall.name();
  }
  static LinearLayout begin(MainActivity a,LinearLayout page,MainActivity.Route route) {
    page.addView(Ui.title(a,title(a,route),30));Ui.gap(page,16);
    LinearLayout body=Ui.col(a);page.addView(body,new LinearLayout.LayoutParams(-1,-2));return body;
  }
  static LinearLayout general(MainActivity a,LinearLayout page,MainActivity.Route route) {
    LinearLayout body=Ui.col(a);body.setGravity(Gravity.CENTER);
    body.setMinimumHeight(Ui.dp(a,360));body.setContentDescription("正在准备页面内容");
    LinearLayout card=Ui.col(a);card.setGravity(Gravity.CENTER);
    card.setPadding(Ui.dp(a,24),Ui.dp(a,28),Ui.dp(a,24),Ui.dp(a,28));
    card.setBackground(Ui.shape(a,Design.ALT,0,28));
    View doodle=Ui.foodDoodle(a,doodleFor(route),0x99c9341e);
    card.addView(doodle,new LinearLayout.LayoutParams(Ui.dp(a,116),Ui.dp(a,84)));
    Ui.gap(card,14);
    TextView title=Ui.title(a,"正在翻开新一页",24);title.setGravity(Gravity.CENTER);card.addView(title);
    Ui.gap(card,8);
    TextView note=Ui.text(a,loadingNote(route),14,Design.MUTED);note.setGravity(Gravity.CENTER);card.addView(note);
    LinearLayout.LayoutParams cardLp=new LinearLayout.LayoutParams(-1,-2);cardLp.leftMargin=Ui.dp(a,8);cardLp.rightMargin=Ui.dp(a,8);
    body.addView(card,cardLp);page.addView(body,new LinearLayout.LayoutParams(-1,-2));
    pulse(doodle);return body;
  }
  static void skeleton(MainActivity a,LinearLayout body,boolean dishes) {
    body.removeAllViews();body.setContentDescription(dishes?"正在加载菜品":"正在加载档口");
    body.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
    LinearLayout hello=Ui.row(a);hello.setGravity(Gravity.CENTER_VERTICAL);
    View doodle=Ui.foodDoodle(a,dishes?5:0,0x88c9341e);hello.addView(doodle,new LinearLayout.LayoutParams(Ui.dp(a,68),Ui.dp(a,52)));
    TextView warm=Ui.text(a,dishes?"正在看看今天有哪些好菜":"正在找寻热乎的档口",14,Design.MUTED);
    LinearLayout.LayoutParams warmLp=new LinearLayout.LayoutParams(0,-2,1);warmLp.leftMargin=Ui.dp(a,10);hello.addView(warm,warmLp);
    body.addView(hello);Ui.gap(body,12);pulse(doodle);
    View search=block(a,52,100);search.setBackground(Ui.shape(a,Design.ALT,0,24));body.addView(search);Ui.gap(body,8);
    LinearLayout count=Ui.row(a);count.addView(block(a,12,100),new LinearLayout.LayoutParams(Ui.dp(a,80),Ui.dp(a,12)));body.addView(count);Ui.gap(body,16);
    LinearLayout grid=Ui.row(a);grid.setGravity(Gravity.TOP);
    for(int column=0;column<2;column++) {
      LinearLayout stack=Ui.col(a);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);
      if(column==0)lp.rightMargin=Ui.dp(a,6);else lp.leftMargin=Ui.dp(a,6);grid.addView(stack,lp);
      for(int row=0;row<2;row++) {
        LinearLayout card=Ui.col(a);card.setBackground(Ui.shape(a,Design.PAPER,Design.BORDER,20));card.setClipToOutline(true);card.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        float ratio=dishes?new float[]{.65f,.65f,.72f,.8f}[row*2+column]:new float[]{.72f,.94f,.8f,1.02f}[row*2+column];
        View image=new View(a){@Override protected void onMeasure(int w,int h){super.onMeasure(w,MeasureSpec.makeMeasureSpec(Math.round(MeasureSpec.getSize(w)*ratio),MeasureSpec.EXACTLY));}};
        image.setBackgroundColor(Design.KRAFT_SOFT);card.addView(image,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout copy=Ui.col(a);copy.setPadding(Ui.dp(a,12),Ui.dp(a,12),Ui.dp(a,12),Ui.dp(a,14));
        copy.addView(block(a,18,100),new LinearLayout.LayoutParams(-1,Ui.dp(a,18)));
        if(dishes){Ui.gap(copy,7);copy.addView(block(a,15,100),new LinearLayout.LayoutParams(Ui.dp(a,54),Ui.dp(a,15)));}
        card.addView(copy);LinearLayout.LayoutParams cardLp=new LinearLayout.LayoutParams(-1,-2);cardLp.bottomMargin=Ui.dp(a,12);stack.addView(card,cardLp);
      }
    }
    body.addView(grid);
    if(ValueAnimator.areAnimatorsEnabled()) {
      ValueAnimator pulse=ValueAnimator.ofFloat(1f,.64f);pulse.setDuration(850);pulse.setRepeatCount(ValueAnimator.INFINITE);pulse.setRepeatMode(ValueAnimator.REVERSE);pulse.addUpdateListener(animation->grid.setAlpha((float)animation.getAnimatedValue()));
      grid.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View v){pulse.start();}public void onViewDetachedFromWindow(View v){pulse.cancel();}});
    }
  }
  private static View block(Context c,int height,int radius) {View view=new View(c);view.setBackground(Ui.shape(c,Design.KRAFT_SOFT,0,radius==100?3:radius));view.setLayoutParams(new LinearLayout.LayoutParams(-1,Ui.dp(c,height)));return view;}
  private static int doodleFor(MainActivity.Route route) {
    return switch(route.kind()) {case "community","community-detail","community-mine","community-favorites" -> 1;case "profile","profile-edit","password","mine-reviews","mine-review-replies","mine-submissions","inbox" -> 3;case "review","review-replies","reply-review" -> 7;default -> 0;};
  }
  private static String loadingNote(MainActivity.Route route) {
    return switch(route.kind()) {case "community","community-detail","community-mine","community-favorites" -> "校园里的新鲜食记马上就来";case "profile","profile-edit","password","mine-reviews","mine-review-replies","mine-submissions","inbox" -> "正在整理你的校园味道";case "admin","admin-community" -> "正在整理最新内容";default -> "热乎的内容马上端上来";};
  }
  private static void pulse(View view) {
    if(!ValueAnimator.areAnimatorsEnabled())return;
    ValueAnimator animator=ValueAnimator.ofFloat(0f,1f);animator.setDuration(900);animator.setRepeatCount(ValueAnimator.INFINITE);animator.setRepeatMode(ValueAnimator.REVERSE);
    animator.addUpdateListener(value->{float phase=(float)value.getAnimatedValue();view.setTranslationY(-Ui.dp(view.getContext(),4)*phase);view.setAlpha(.72f+.28f*phase);});
    view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View v){animator.start();}public void onViewDetachedFromWindow(View v){animator.cancel();}});
    if(view.isAttachedToWindow())animator.start();
  }
  static void failure(MainActivity a,LinearLayout body) {
    body.removeAllViews();body.setContentDescription(null);Ui.gap(body,40);
    TextView error=Ui.text(a,"加载失败",16,Design.MUTED);error.setGravity(Gravity.CENTER);error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);body.addView(error);Ui.gap(body,18);
    LinearLayout row=Ui.row(a);row.setGravity(Gravity.CENTER);row.addView(Ui.button(a,"重试",true,a::render));body.addView(row);
  }
  private LoadingScreen(){}
}
