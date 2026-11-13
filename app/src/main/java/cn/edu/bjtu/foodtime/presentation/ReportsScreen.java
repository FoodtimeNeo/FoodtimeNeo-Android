package cn.edu.bjtu.foodtime.presentation;

import android.widget.LinearLayout;
import android.widget.TextView;
import cn.edu.bjtu.foodtime.domain.model.Models.ReportPage;
import cn.edu.bjtu.foodtime.domain.model.Models.ReportRow;
import cn.edu.bjtu.foodtime.domain.model.ReportManagement.Decision;
import cn.edu.bjtu.foodtime.domain.model.ReportManagement.Page;
import cn.edu.bjtu.foodtime.domain.validation.ReportManagementPolicy;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** Explicit decisions, follow-ups and actual actions remain separate operations. */
final class ReportsScreen {
  private final MainActivity activity;
  private final LinearLayout rows, feedback;
  private final TextView refresh, pendingTab, decidedTab;
  private final String actor, viewPrefix, key="report-management:"+UUID.randomUUID();
  private boolean decided;
  private int pendingPage=1, decidedPage=1;
  private long ticket;
  static void show(MainActivity a,LinearLayout page,MainActivity.Route route) {
    a.heading(page,"","举报处理","公开决定、追问回复与实际动作分别留档");
    var caps=a.repo.capabilities();
    if(ReportManagementPolicy.appeals(caps)) {
      Ui.action(page,"申诉复核",false,() -> a.go("admin-appeals",""));Ui.gap(page,12);
    }
    if(!ReportManagementPolicy.review(caps)) {page.addView(Ui.text(a,"当前账号没有举报处理权限。",14,Design.MUTED));return;}
    new ReportsScreen(a,page,"decided".equals(route.id()));
  }
  private ReportsScreen(MainActivity a,LinearLayout page,boolean completed) {
    activity=a;actor=a.repo.currentUser().id();viewPrefix=a.draftKey("report-management");
    decided=completed||a.drafts.getBoolean(viewPrefix+":decided",false);
    pendingPage=a.drafts.getInt(viewPrefix+":pending-page",1);decidedPage=a.drafts.getInt(viewPrefix+":decided-page",1);
    LinearLayout tabs=Ui.row(a);
    pendingTab=Ui.button(a,"待处理",true,() -> switchMode(false));
    decidedTab=Ui.button(a,"已处理",false,() -> switchMode(true));
    var first=new LinearLayout.LayoutParams(0,-2,1);first.rightMargin=Ui.dp(a,12);
    tabs.addView(pendingTab,first);tabs.addView(decidedTab,new LinearLayout.LayoutParams(0,-2,1));page.addView(tabs);
    Ui.gap(page,12);refresh=Ui.button(a,"刷新记录",false,this::load);page.addView(refresh);
    feedback=Ui.col(a);page.addView(feedback);rows=Ui.col(a);page.addView(rows);
    page.post(() -> {if(page.isAttachedToWindow())load();});
  }
  private void switchMode(boolean value) {if(decided==value)return;decided=value;activity.drafts.putBoolean(viewPrefix+":decided",value);rows.removeAllViews();load();}
  private void load() {
    long current=++ticket;boolean completed=decided;int number=completed?decidedPage:pendingPage;
    activity.drafts.putInt(viewPrefix+":pending-page",pendingPage);activity.drafts.putInt(viewPrefix+":decided-page",decidedPage);
    pendingTab.setSelected(!completed);decidedTab.setSelected(completed);
    pendingTab.setTextColor(completed?Design.INK:Design.ON_RED);
    pendingTab.setBackground(Ui.ripple(activity,completed?Design.PAPER:Design.RED,Design.INK,16));
    decidedTab.setTextColor(completed?Design.ON_RED:Design.INK);
    decidedTab.setBackground(Ui.ripple(activity,completed?Design.RED:Design.PAPER,Design.INK,16));
    refresh.setEnabled(false);feedback.removeAllViews();
    TextView status=Ui.text(activity,"正在读取…",13,Design.MUTED),error=Ui.text(activity,"",14,Design.RED);
    Ui.observeError(error);feedback.addView(status);feedback.addView(error);
    AtomicReference<ReportPage> pending=new AtomicReference<>();AtomicReference<Page<Decision>> decisions=new AtomicReference<>();
    activity.read(key+":"+current,null,error,() -> {
      ReportManagementUi.requireActor(activity,actor);
      if(completed)decisions.set(activity.repo.adminReportDecisions(number));else pending.set(activity.repo.reports(number));
    },() -> {
      if(current!=ticket)return;rows.removeAllViews();
      if(completed) {
        Page<Decision> data=decisions.get();status.setText("共 "+data.total()+" 条已处理决定");
        for(Decision row:data.items())decision(row);paging(number,data.hasMore(),true);
      } else {
        ReportPage data=pending.get();status.setText("共 "+data.total()+" 条待处理举报");
        for(ReportRow row:data.items())pending(row,data);paging(number,data.hasMore(),false);
      }
    },() -> {if(current==ticket){refresh.setEnabled(true);status.setText("");}});
  }
  private void paging(int number,boolean more,boolean completed) {
    Ui.gap(rows,12);
    if(number>1){Ui.action(rows,"上一页",false,() -> {if(completed)decidedPage--;else pendingPage--;load();});Ui.gap(rows,12);}
    if(more)Ui.action(rows,"下一页",false,() -> {if(completed)decidedPage++;else pendingPage++;load();});
  }
  private void pending(ReportRow row,ReportPage result) {
    LinearLayout card=Ui.card(rows);
    card.addView(Ui.title(activity,ReportManagementUi.type(row.type()),18));
    card.addView(Ui.text(activity,"举报原因："+row.reason(),14,Design.INK));
    card.addView(Ui.text(activity,row.excerpt(),15,Design.INK));
    card.addView(Ui.text(activity,"被举报用户："+row.targetUserId(),12,Design.MUTED));
    if(!row.postId().isEmpty()){Ui.gap(card,12);Ui.action(card,"查看原帖",false,() -> activity.go("community-detail",row.postId()));}
    Ui.gap(card,12);Ui.action(card,"填写处理决定",true,() -> decide(row,result));thread(card,row.id());
  }
  private void thread(LinearLayout card,String report) {Ui.gap(card,12);Ui.action(card,"查看／回复追问",false,() -> activity.go("admin-report-thread",report));}
  private void decide(ReportRow row,ReportPage result) {
    LinearLayout form=Ui.col(activity);
    var outcome=SubmissionScreen.filter(activity,form,"举报结论",new String[]{"成立","不成立"},0);
    var reason=ReportManagementUi.field(activity,form,"公开理由（必填，最多500字）",500);
    var note=ReportManagementUi.field(activity,form,"内部备注（不公开，可选）",2000);
    reason.setText("");note.setText("");
    TextView error=Ui.text(activity,"",14,Design.RED);Ui.observeError(error);form.addView(error);
    var dialog=ReportManagementUi.form(activity,"处理举报",form);
    dialog.getButton(-1).setOnClickListener(v -> {
      String publicReason=reason.getText().toString().strip(),internal=ReportManagementUi.nullable(note);
      String value=outcome.getSelectedItemPosition()==0?"upheld":"not_upheld";
      if(publicReason.isEmpty()){error.setText("请填写公开理由");return;}
      activity.request(v,error,() -> {ReportManagementUi.requireActor(activity,actor);activity.repo.decideReport(row.id(),value,publicReason,internal);},() -> {
        dialog.dismiss();pendingPage=result.items().size()==1?Math.max(1,pendingPage-1):pendingPage;load();activity.message("决定已保存，实际动作需另行确认");
      });
    });
  }
  private void decision(Decision row) {
    LinearLayout card=Ui.card(rows);
    card.addView(Ui.title(activity,ReportManagementUi.type(row.targetType())+" · "+ReportManagementUi.outcome(row.outcome()),18));
    card.addView(Ui.text(activity,"公开理由："+row.publicReason(),14,Design.INK));
    card.addView(Ui.text(activity,"决定编号："+row.decisionId()+"\n被举报用户："+row.targetUserId(),12,Design.MUTED));
    card.addView(Ui.text(activity,SubmissionScreen.date(row.createdAt()),12,Design.MUTED));thread(card,row.reportId());
    if("post".equals(row.targetType()))action(card,row,"remove_post","下架帖子");
    if("comment".equals(row.targetType()))action(card,row,"remove_comment","下架评论");
    action(card,row,"disable_user","停用被举报用户");action(card,row,"restore_user","恢复被举报用户");
  }
  private void action(LinearLayout card,Decision row,String type,String label) {
    if(!ReportManagementPolicy.action(activity.repo.capabilities(),type))return;
    Ui.gap(card,12);TextView button=Ui.button(activity,label,false,() -> {});card.addView(button);
    TextView error=Ui.text(activity,"",13,Design.RED);Ui.observeError(error);card.addView(error);
    button.setOnClickListener(v -> {
      if(activity.operationInFlight())return;
      Ui.confirm(activity,"确认"+label,"被举报用户："+row.targetUserId()+"\n公开理由："+row.publicReason()+"\n此操作会立即生效。恢复不会仅因申诉成立而自动执行。",() ->
          activity.request(button,error,() -> {ReportManagementUi.requireActor(activity,actor);activity.repo.performReportAction(row.reportId(),type,"由决定 "+row.decisionId()+" 触发");},() -> {activity.message(label+"已完成");load();}));
    });
  }
}
