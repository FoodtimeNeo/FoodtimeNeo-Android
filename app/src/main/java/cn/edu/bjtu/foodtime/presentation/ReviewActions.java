package cn.edu.bjtu.foodtime.presentation;

import android.view.View;
import android.widget.*;
import cn.edu.bjtu.foodtime.domain.model.Models.LikeState;
import java.util.function.Function;

/** Uses the write response directly: no per-like reload of dish, media or rating endpoints. */
final class ReviewActions {
  static void like(MainActivity a,LinearLayout parent,String target,LikeState initial,Function<Boolean,LikeState> save) {
    LikeState[] state={initial}, result={null};
    TextView button=Ui.button(a,"",false,() -> {}), error=Ui.text(a,"",14,Design.RED);
    error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    Runnable paint=() -> {
      button.setText((state[0].likedByMe()?"已赞 ":"赞 ")+state[0].likeCount());
      button.setSelected(state[0].likedByMe());
      button.setContentDescription((state[0].likedByMe()?"取消":"")+target+"点赞，"+state[0].likeCount()+"个赞");
    };
    paint.run();
    button.setOnClickListener(v -> a.request(button,error,() -> result[0]=save.apply(!state[0].likedByMe()),() -> {
      if(result[0].counterVersion()>=state[0].counterVersion()) state[0]=result[0];
      paint.run();
    }));
    parent.addView(button);parent.addView(error);
  }
}
