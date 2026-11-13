package cn.edu.bjtu.foodtime.presentation;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.edu.bjtu.foodtime.BuildConfig;
import cn.edu.bjtu.foodtime.domain.model.Models.ModerationDecision;
import cn.edu.bjtu.foodtime.domain.model.Models.ModerationDecisionPage;
import cn.edu.bjtu.foodtime.domain.model.Models.ReportAction;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** Minimal disabled-account surface: own decisions, one appeal, logout. */
final class AppealOnlyScreen {
  private final MainActivity activity;
  private final LinearLayout page, rows;
  private final TextView status, error, refresh, logout;
  private final NumberedPagination pagination;
  private final String actor,scope;
  private ModerationDecisionPage data;
  private int number,attempted;
  private long ticket;
  private boolean loading,busy;
  private final String requestKey = "appeal-only:" + UUID.randomUUID();

  private AppealOnlyScreen(MainActivity activity, LinearLayout page) {
    this.activity = activity;
    this.page = page;
    actor=activity.repo.currentUser().id();scope=new AppealDraftStore(activity,BuildConfig.API_BASE_URL,actor).key("restricted-view");
    number=Math.max(1,activity.drafts.getInt(scope+":page",1));attempted=number;
    rows = Ui.col(activity);
    status = Ui.text(activity, "正在加载…", 14, Design.MUTED);
    error = Ui.text(activity, "", 14, Design.RED);
    Ui.observeError(error);
    refresh=Ui.button(activity,"刷新本页",false,()->load(attempted));refresh.setTag("restricted-refresh");
    logout=Ui.button(activity,"退出登录",false,activity::logout);
    pagination=new NumberedPagination(activity,value->{if(!loading&&!busy)load(value);});
    error.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){}public void afterTextChanged(Editable value){if(value.length()>0)refresh.setText("重试第 "+attempted+" 页");}});
  }

  static void show(MainActivity activity, LinearLayout page) {
    activity.heading(page, "受限", "处理决定与申诉", "仅可查看本人的决定并提交一次申诉");
    page.addView(Ui.text(activity,
        "会话在登录后45分钟到期；未提交的申诉内容会在重新登录后恢复。", 12, Design.MUTED));
    AppealOnlyScreen screen = new AppealOnlyScreen(activity, page);
    page.addView(screen.logout);
    page.addView(screen.status);
    page.addView(screen.error);
    page.addView(screen.refresh);
    page.addView(screen.pagination);
    page.addView(screen.rows);
    screen.load(screen.number);
  }

  private boolean current(){var user=activity.repo.currentUser();return page.isAttachedToWindow()&&user!=null&&actor.equals(user.id())&&"appeal_only".equals(activity.repo.sessionMode());}
  private void controls(){
    boolean enabled=!loading&&!busy;
    refresh.setEnabled(enabled);refresh.setAlpha(enabled?1f:.4f);logout.setEnabled(!busy);logout.setAlpha(busy?.4f:1f);
    pagination.setVisibility(data==null||data.total()==0?View.GONE:View.VISIBLE);
    int pages=data==null?1:(int)Math.min(Integer.MAX_VALUE,Math.max(1,(data.total()-1)/data.size()+1));
    pagination.state(number,pages,enabled);enabled(rows,enabled);
  }
  private static void enabled(View view,boolean value){view.setEnabled(value);if(view instanceof android.view.ViewGroup group)for(int i=0;i<group.getChildCount();i++)enabled(group.getChildAt(i),value);}
  private void busy(boolean value){busy=value;controls();}
  private void load(int requested) {
    if(loading||busy||requested<1)return;
    long attempt=++ticket;attempted=requested;loading=true;error.setText("");
    status.setText("正在读取第 "+requested+" 页…");controls();
    AtomicReference<ModerationDecisionPage> result = new AtomicReference<>();
    activity.read(requestKey+":"+attempt, null, error,
        () -> result.set(activity.repo.myModerationDecisions(requested)), () -> {
          if(!current()||ticket!=attempt)return;
          var next=result.get();int last=(int)Math.min(Integer.MAX_VALUE,Math.max(1,(next.total()-1)/next.size()+1));
          if(next.items().isEmpty()&&requested>last){load(last);return;}
          data=next;number=next.page();activity.drafts.putInt(scope+":page",number);paint();
        }, () -> {if(current()&&ticket==attempt){loading=false;status.setText(data==null?"":summary());refresh.setText("刷新本页");controls();}});
  }
  private String summary(){return data.items().isEmpty()?"当前没有可查看的处理决定。":"共 "+data.total()+" 条 · 第 "+number+" / "+Math.max(1,(data.total()-1)/data.size()+1)+" 页";}
  private void paint(){rows.removeAllViews();status.setText(summary());for(var item:data.items())card(item);controls();}

  private void card(ModerationDecision item) {
    LinearLayout card = Ui.card(rows);
    card.setTag("restricted-decision:"+item.decisionId());
    card.addView(Ui.title(activity, outcomeLabel(item.outcome()), 18));
    card.addView(Ui.text(activity, "公开理由：" + item.publicReason(), 14, Design.INK));
    card.addView(Ui.text(activity, "入箱时间：" + SubmissionScreen.date(item.deliveredAt()), 12, Design.MUTED));
    card.addView(Ui.text(activity, "申诉截止：" + SubmissionScreen.date(item.appealDeadline()), 12, Design.MUTED));
    Ui.link(card, "查看处理动作", () -> showActions(item));
    if (!item.appealStatus().isBlank()) {
      card.addView(Ui.text(activity, "申诉状态：" + statusLabel(item.appealStatus())
          + (item.appealOutcome().isBlank() ? "" : " · " + outcomeLabel(item.appealOutcome())),
          13, Design.MUTED));
      return;
    }
    if (item.appealDeadline() <= System.currentTimeMillis()) {
      card.addView(Ui.text(activity, "申诉期限已过。", 13, Design.MUTED));
      return;
    }
    String expanded=activity.drafts.getString(scope+":expanded","");boolean open=item.decisionId().equals(expanded);
    TextView toggle=Ui.button(activity,open?"收起编辑":"提交申诉",false,()->{
      if(loading||busy||activity.operationInFlight())return;
      activity.drafts.putString(scope+":expanded",open?"":item.decisionId());paint();
    });toggle.setTag("restricted-open:"+item.decisionId());card.addView(toggle);
    if(open)AppealForm.show(activity,card,item,()->load(number),this::busy);
  }

  private void showActions(ModerationDecision item) {
    AtomicReference<java.util.List<ReportAction>> result = new AtomicReference<>();
    activity.read("appeal-actions:" + item.decisionId(), null, error,
        () -> result.set(activity.repo.decisionActions(item.decisionId())), () -> {
          StringBuilder text = new StringBuilder();
          if (result.get().isEmpty()) text.append("尚未记录实际处理动作。");
          for (ReportAction action : result.get()) {
            if (text.length() > 0) text.append((char) 10);
            text.append(action.actionType()).append(" · ").append(action.targetType())
                .append(" · ").append(SubmissionScreen.date(action.createdAt()));
          }
          new android.app.AlertDialog.Builder(activity).setTitle("处理动作").setMessage(text)
              .setPositiveButton("关闭", null).show();
        }, () -> {});
  }

  private static String outcomeLabel(String outcome) {
    return "upheld".equals(outcome) ? "处理成立" : "处理不成立";
  }

  private static String statusLabel(String status) {
    return switch (status) {
      case "under_review" -> "复核中";
      case "resolved" -> "已复核";
      default -> "已提交";
    };
  }
}
