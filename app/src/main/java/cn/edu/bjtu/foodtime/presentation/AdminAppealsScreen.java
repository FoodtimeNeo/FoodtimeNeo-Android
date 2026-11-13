package cn.edu.bjtu.foodtime.presentation;

import android.view.View;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.edu.bjtu.foodtime.domain.model.ReportManagement.Appeal;
import cn.edu.bjtu.foodtime.domain.model.ReportManagement.Page;
import cn.edu.bjtu.foodtime.domain.validation.ReportManagementPolicy;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

final class AdminAppealsScreen {
  static void show(MainActivity a,LinearLayout parent,MainActivity.Route route) {
    a.heading(parent,"","申诉复核","复核结论不会自动处罚或恢复账号");
    if(!ReportManagementPolicy.appeals(a.repo.capabilities()))return;
    if(route.id().isBlank())new Queue(a,parent);else new Detail(a,parent,route.id());
  }
  private static String status(String value) {return switch(value){case "submitted" -> "已提交";case "under_review" -> "复核中";case "resolved" -> "已复核";default -> value;};}
  private static final class Queue {
    final MainActivity a;final LinearLayout rows,feedback;final String actor,key="appeal-queue:"+UUID.randomUUID();
    String filter="submitted";int page=1;long ticket;
    Queue(MainActivity a,LinearLayout parent) {
      this.a=a;actor=a.repo.currentUser().id();
      var choice=SubmissionScreen.filter(a,parent,"处理状态",new String[]{"已提交","复核中","已复核","全部"},0);
      choice.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
        public void onNothingSelected(AdapterView<?> p){}
        public void onItemSelected(AdapterView<?> p,View view,int index,long id){
          String value=new String[]{"submitted","under_review","resolved",""}[index];
          if(!filter.equals(value)){filter=value;load(1);}
        }
      });
      Ui.action(parent,"刷新申诉",false,() -> load(page));feedback=Ui.col(a);parent.addView(feedback);rows=Ui.col(a);parent.addView(rows);load(1);
    }
    void load(int number) {
      long current=++ticket;String selected=filter;page=number;feedback.removeAllViews();
      var progress=Ui.text(a,"正在读取…",13,Design.MUTED);var error=Ui.text(a,"",14,Design.RED);Ui.observeError(error);feedback.addView(progress);feedback.addView(error);
      AtomicReference<Page<Appeal>> result=new AtomicReference<>();
      a.read(key+":"+current,null,error,() -> {ReportManagementUi.requireActor(a,actor);result.set(a.repo.adminAppeals(selected,number));},() -> {
        if(current!=ticket)return;rows.removeAllViews();var data=result.get();progress.setText("共 "+data.total()+" 条申诉");
        for(Appeal item:data.items()) {
          var card=Ui.card(rows);
          card.addView(Ui.title(a,("reporter".equals(item.side())?"举报人":"被举报人")+"申诉 · "+status(item.status()),17));
          card.addView(Ui.text(a,item.content(),14,Design.INK));
          Ui.gap(card,12);Ui.action(card,"查看申诉详情",false,() -> a.go("admin-appeals",item.id()));
        }
        if(number>1){Ui.action(rows,"上一页",false,() -> load(page-1));Ui.gap(rows,12);}
        if(data.hasMore())Ui.action(rows,"下一页",false,() -> load(page+1));
      },() -> {if(current==ticket)progress.setText("");});
    }
  }
  private static final class Detail {
    final MainActivity a;final LinearLayout body;final TextView error;final String id,actor,key="appeal-detail:"+UUID.randomUUID();
    Appeal item;long ticket;
    Detail(MainActivity a,LinearLayout parent,String id) {
      this.a=a;this.id=id;actor=a.repo.currentUser().id();
      Ui.action(parent,"刷新详情",false,this::load);
      error=Ui.text(a,"",14,Design.RED);Ui.observeError(error);parent.addView(error);body=Ui.col(a);parent.addView(body);load();
    }
    void load(){
      long current=++ticket;
      error.setText("");
      AtomicReference<Appeal> result=new AtomicReference<>();
      a.read(key+":"+current,null,error,() -> {ReportManagementUi.requireActor(a,actor);result.set(a.repo.adminAppeal(id));},() -> {if(current==ticket){error.setText("");item=result.get();render();}},() -> {});
    }
    void render(){
      body.removeAllViews();
      body.addView(Ui.title(a,item.appellantName()+" · "+("reporter".equals(item.side())?"举报人":"被举报人"),18));
      body.addView(Ui.text(a,"当前状态："+status(item.status()),14,Design.MUTED));
      body.addView(Ui.text(a,"决定编号："+item.decisionId(),12,Design.MUTED));
      body.addView(Ui.text(a,item.content(),15,Design.INK));
      ReportManagementUi.evidence(a,body,item.evidenceUrls());
      if(!item.publicReason().isBlank())body.addView(Ui.text(a,"复核理由："+item.publicReason(),14,Design.INK));
      if(!item.internalNote().isBlank())body.addView(Ui.text(a,"内部备注（不公开）："+item.internalNote(),13,Design.MUTED));
      if("submitted".equals(item.status())) {
        TextView start=Ui.button(a,"开始复核",true,() -> {});Ui.gap(body,12);body.addView(start);
        long revision=item.revision();
        start.setOnClickListener(v -> {AtomicReference<Appeal> result=new AtomicReference<>();a.request(v,error,() -> {ReportManagementUi.requireActor(a,actor);result.set(a.repo.startAppealReview(id,revision));},() -> {item=result.get();render();});});
      } else if("under_review".equals(item.status())) {
        var outcome=SubmissionScreen.filter(a,body,"复核结论",new String[]{"申诉成立","申诉不成立"},0);
        var reason=ReportManagementUi.field(a,body,"公开复核理由（必填，最多500字）",500);
        var note=ReportManagementUi.field(a,body,"内部复核备注（不公开，可选）",2000);
        TextView resolve=Ui.button(a,"保存复核结论",true,() -> {});body.addView(resolve);
        long revision=item.revision();
        resolve.setOnClickListener(v -> {
          String value=outcome.getSelectedItemPosition()==0?"upheld":"rejected",text=reason.getText().toString().strip(),internal=ReportManagementUi.nullable(note);
          if(text.isEmpty()){error.setText("请填写公开复核理由");return;}
          AtomicReference<Appeal> result=new AtomicReference<>();
          a.request(v,error,() -> {ReportManagementUi.requireActor(a,actor);result.set(a.repo.resolveAppeal(id,value,text,internal,revision));},() -> {item=result.get();render();a.message("复核结论已保存");});
        });
      } else if("resolved".equals(item.status())) {
        body.addView(Ui.text(a,"复核结果："+ReportManagementUi.outcome(item.outcome()),15,Design.RED));
        body.addView(Ui.text(a,"如需执行恢复，请在已处理举报中核对决定编号并单独确认。",13,Design.MUTED));
        Ui.gap(body,12);Ui.action(body,"打开已处理举报",false,() -> a.go("admin-reports","decided"));
      }
    }
  }
}
