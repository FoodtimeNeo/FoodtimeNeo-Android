package cn.edu.bjtu.foodtime.presentation;

import android.widget.LinearLayout;
import android.widget.TextView;
import cn.edu.bjtu.foodtime.domain.model.Models.ModerationDecision;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** Normal-session editor loads the exact recipient projection, including its server deadline. */
final class AppealComposeScreen {
  static void show(MainActivity a,LinearLayout page,MainActivity.Route route){
    a.heading(page,"04.3","提交申诉","文字和最多3张图片，失败后可在本页重试。");
    TextView error=Ui.text(a,"",14,Design.RED);error.setTag("appeal-error");Ui.observeError(error);page.addView(error);
    TextView refresh=Ui.button(a,"重新读取决定",false,()->{});page.addView(refresh);
    LinearLayout body=Ui.col(a);page.addView(body);
    Runnable load=()->{
      body.removeAllViews();var result=new AtomicReference<ModerationDecision>();
      a.read("appeal-compose:"+route.id()+":"+UUID.randomUUID(),refresh,error,()->result.set(a.repo.myModerationDecision(route.id())),()->{
        var item=result.get();body.addView(Ui.text(a,"公开理由："+item.publicReason(),15,Design.INK));
        body.addView(Ui.text(a,"申诉截止："+SubmissionScreen.date(item.appealDeadline()),13,Design.MUTED));
        AppealForm.show(a,body,item,()->{a.back();a.refreshCurrent();});
      },()->{});
    };refresh.setOnClickListener(v->load.run());load.run();
  }
  private AppealComposeScreen(){}
}
