package cn.edu.bjtu.foodtime.presentation;

import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import cn.edu.bjtu.foodtime.domain.model.Models.LikeState;
import java.util.function.Function;

/** Uses the write response directly: no per-like reload of dish, media or rating endpoints. */
final class ReviewActions {
  static TextView button(MainActivity a,String label,Runnable action) {
    Button v=new Button(a);
    v.setText(label);v.setAllCaps(false);v.setTextSize(14);v.setTextColor(Design.MUTED);v.setTypeface(Design.body());
    v.setMinWidth(Ui.dp(a,48));v.setMinimumWidth(Ui.dp(a,48));v.setMinHeight(Ui.dp(a,48));
    v.setPadding(Ui.dp(a,8),Ui.dp(a,8),Ui.dp(a,8),Ui.dp(a,8));
    v.setBackground(Ui.ripple(a,android.graphics.Color.TRANSPARENT,0,4));
    v.setOnClickListener(w -> action.run());return v;
  }
  static void like(MainActivity a,ViewGroup parent,String target,LikeState initial,Function<Boolean,LikeState> save) {
    LikeState[] state={initial}, result={null};
    LinearLayout group=Ui.col(a);
    TextView button=button(a,"",() -> {}), error=Ui.text(a,"",14,Design.RED);
    error.setVisibility(View.GONE);
    Ui.observeError(error);
    error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    Runnable paint=() -> {
      button.setText((state[0].likedByMe()?"已赞 ":"赞 ")+state[0].likeCount());
      button.setSelected(state[0].likedByMe());
      button.setTextColor(state[0].likedByMe()?Design.RED:Design.MUTED);
      button.setContentDescription((state[0].likedByMe()?"取消":"")+target+"点赞，"+state[0].likeCount()+"个赞");
    };
    paint.run();
    button.setOnClickListener(v -> a.request(button,error,() -> result[0]=save.apply(!state[0].likedByMe()),() -> {
      if(result[0].counterVersion()>=state[0].counterVersion()) state[0]=result[0];
      paint.run();
    }));
    group.addView(button);group.addView(error);parent.addView(group);
  }
}
