package cn.edu.bjtu.foodtime.presentation;

import android.animation.ValueAnimator;
import android.content.Context;
import android.view.*;
import android.widget.*;

/** Quiet placeholders matching the two catalog routes. No decorative loading copy. */
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
  static void skeleton(MainActivity a,LinearLayout body,boolean dishes) {
    body.removeAllViews();body.setContentDescription(dishes?"正在加载菜品":"正在加载档口");
    body.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
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
  static void failure(MainActivity a,LinearLayout body) {
    body.removeAllViews();body.setContentDescription(null);Ui.gap(body,40);
    TextView error=Ui.text(a,"加载失败",16,Design.MUTED);error.setGravity(Gravity.CENTER);error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);body.addView(error);Ui.gap(body,18);
    LinearLayout row=Ui.row(a);row.setGravity(Gravity.CENTER);row.addView(Ui.button(a,"重试",true,a::render));body.addView(row);
  }
  private LoadingScreen(){}
}
