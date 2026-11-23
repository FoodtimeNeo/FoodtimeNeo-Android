package cn.edu.bjtu.foodtime.presentation;

import android.view.View;
import android.widget.*;

/** The first frame is a neutral catalog shell, never a cached private account or a connection splash. */
final class StartupScreen {
  static LinearLayout show(MainActivity a) {
    TextView brand = Ui.title(a, "北交干饭吧", 22);
    brand.setPadding(Ui.dp(a,8),Ui.dp(a,12),Ui.dp(a,8),Ui.dp(a,12));
    a.header.addView(brand);
    a.content.setTag("startup-catalog-shell");
    a.content.addView(Ui.title(a,"食堂手账",30));
    Ui.gap(a.content,12);
    a.content.addView(Ui.note(a,"今天也要好好吃饭"));
    Ui.gap(a.content,20);
    LinearLayout status=Ui.col(a);status.setTag("startup-session-status");
    status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    a.content.addView(status);
    LinearLayout placeholders=Ui.row(a);
    for(int i=0;i<2;i++) {
      LinearLayout card=Ui.col(a);card.setBackground(Ui.shape(a,Design.ALT,Design.BORDER,18));
      card.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
      LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,Ui.dp(a,185),1);if(i>0)lp.leftMargin=Ui.dp(a,12);
      placeholders.addView(card,lp);
    }
    Ui.gap(a.content,10);a.content.addView(placeholders);
    return status;
  }
  static void failed(MainActivity a,LinearLayout status,String message) {
    status.removeAllViews();status.addView(Ui.text(a,"无法完成启动，请检查网络后重试。",14,Design.MUTED));
    if(!message.isBlank())status.addView(Ui.text(a,message,13,Design.RED));
    Ui.gap(status,10);Ui.action(status,"重试",true,a::restore);
  }
  private StartupScreen() {}
}
