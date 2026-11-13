package cn.edu.bjtu.foodtime.presentation;

import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.edu.bjtu.foodtime.domain.model.ReportManagement.FollowUp;
import cn.edu.bjtu.foodtime.domain.model.ReportManagement.Page;
import cn.edu.bjtu.foodtime.domain.validation.ReportManagementPolicy;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

final class AdminReportThreadScreen {
  private final MainActivity activity;
  private final LinearLayout rows,feedback;
  private final EditText content,note;
  private final TextView submit,error;
  private final String report,actor,prefix,key="staff-followup:"+UUID.randomUUID();
  private int page=1;
  private long ticket;

  static void show(MainActivity a,LinearLayout parent,MainActivity.Route route) {
    a.heading(parent,"","举报追问","公开回复与内部备注分别保存");
    if(!ReportManagementPolicy.review(a.repo.capabilities()))return;
    new AdminReportThreadScreen(a,parent,route.id());
  }
  private AdminReportThreadScreen(MainActivity a,LinearLayout parent,String report) {
    activity=a;this.report=report;actor=a.repo.currentUser().id();prefix="staff-evidence:"+actor+":"+report;
    if(!a.drafts.containsKey(prefix+":request"))a.drafts.putString(prefix+":request",UUID.randomUUID().toString());
    Ui.action(parent,"刷新追问",false,() -> load(page,false));
    feedback=Ui.col(a);parent.addView(feedback);rows=Ui.col(a);parent.addView(rows);Ui.gap(parent,16);
    content=ReportManagementUi.field(a,parent,"公开回复（最多500字）",500);
    note=ReportManagementUi.field(a,parent,"内部备注（不公开，最多2000字）",2000);
    EvidenceSlots.show(a,parent,prefix);Ui.gap(parent,12);
    error=Ui.text(a,"",14,Design.RED);Ui.observeError(error);parent.addView(error);
    submit=Ui.button(a,"提交回复",true,this::submit);parent.addView(submit);
    boolean latest=a.drafts.getBoolean(prefix+":latest",false);a.drafts.remove(prefix+":latest");page=a.drafts.getInt(prefix+":page",1);load(page,latest);
  }
  private void load(int number,boolean latest) {
    long current=++ticket;feedback.removeAllViews();
    TextView status=Ui.text(activity,"正在读取…",13,Design.MUTED),failure=Ui.text(activity,"",14,Design.RED);
    Ui.observeError(failure);feedback.addView(status);feedback.addView(failure);
    AtomicReference<Page<FollowUp>> result=new AtomicReference<>();
    activity.read(key+":"+current,null,failure,() -> {
      ReportManagementUi.requireActor(activity,actor);
      Page<FollowUp> data=activity.repo.adminReportFollowUps(report,number);
      int last=Math.max(1,(int)((data.total()+data.size()-1)/data.size()));
      if(latest&&last!=data.page())data=activity.repo.adminReportFollowUps(report,last);
      result.set(data);
    },() -> {
      if(current!=ticket)return;
      var data=result.get();page=data.page();activity.drafts.putInt(prefix+":page",page);status.setText("共 "+data.total()+" 条追问与回复");rows.removeAllViews();
      for(FollowUp item:data.items()) {
        var card=Ui.card(rows);
        card.addView(Ui.title(activity,"staff".equals(item.authorRole())?"管理员回复":"举报人追问",16));
        card.addView(Ui.text(activity,item.content(),15,Design.INK));
        if(!item.internalNote().isBlank())card.addView(Ui.text(activity,"内部备注（不公开）："+item.internalNote(),13,Design.MUTED));
        ReportManagementUi.evidence(activity,card,item.evidenceUrls());
        card.addView(Ui.text(activity,SubmissionScreen.date(item.createdAt()),12,Design.MUTED));
      }
      if(page>1){Ui.action(rows,"上一页",false,() -> load(page-1,false));Ui.gap(rows,12);}
      if(data.hasMore())Ui.action(rows,"下一页",false,() -> load(page+1,false));
    },() -> {if(current==ticket)status.setText("");});
  }
  private void submit() {
    String text=content.getText().toString().strip(),internal=ReportManagementUi.nullable(note);
    if(text.isEmpty()){error.setText("请填写公开回复");return;}
    String request=activity.drafts.getString(prefix+":request");
    activity.request(submit,error,() -> {
      ReportManagementUi.requireActor(activity,actor);
      var evidence=EvidenceSlots.upload(activity,prefix);
      ReportManagementUi.requireActor(activity,actor);
      activity.repo.replyToReport(report,text,internal,evidence,request);
    },() -> {
      content.setText("");note.setText("");
      for(int i=0;i<3;i++)activity.drafts.remove(prefix+":"+i);
      activity.drafts.putString(prefix+":request",UUID.randomUUID().toString());
      activity.drafts.putBoolean(prefix+":latest",true);
      activity.message("回复已提交");activity.redraw();
    });
  }
}
