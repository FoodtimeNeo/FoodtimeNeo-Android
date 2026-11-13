package cn.edu.bjtu.foodtime.presentation;

import android.app.AlertDialog;
import android.text.*;
import android.view.View;
import android.widget.*;
import cn.edu.bjtu.foodtime.domain.model.FeedbackModels;
import cn.edu.bjtu.foodtime.domain.model.FeedbackModels.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

final class FeedbackScreen {
  static void list(MainActivity a,LinearLayout page,MainActivity.Route route) {
    boolean admin=route.kind().equals("admin-feedback");if(!allowed(a,admin))return;
    new ListScreen(a,page,admin);
  }
  static void detail(MainActivity a,LinearLayout page,MainActivity.Route route) {
    boolean admin=route.kind().equals("admin-feedback-detail");if(!allowed(a,admin))return;
    new DetailScreen(a,page,admin,route.id());
  }
  private static boolean allowed(MainActivity a,boolean admin){return a.repo.currentUser()!=null&&(!admin||"superadmin".equals(a.repo.currentUser().role()));}
  private static String time(long value){return java.time.Instant.ofEpochMilli(value).atOffset(java.time.ZoneOffset.ofHours(8)).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));}
  private static TextView error(MainActivity a,LinearLayout parent){TextView value=Ui.text(a,"",14,Design.RED);Ui.observeError(value);parent.addView(value);return value;}
  private static void resetRequest(MainActivity a,String key){a.drafts.putString(key,UUID.randomUUID().toString());}
  private static String request(MainActivity a,String key){if(!a.drafts.containsKey(key))resetRequest(a,key);return a.drafts.getString(key);}
  private static void track(EditText field,Runnable changed){field.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){changed.run();}public void afterTextChanged(Editable s){}});}

  private static final class ListScreen {
    final MainActivity a;final boolean admin;final LinearLayout rows,paging;final TextView error,refresh,status;
    final List<TextView> controls=new ArrayList<>();final String key="feedback-list:"+UUID.randomUUID();
    boolean archived,busy;Page current;
    ListScreen(MainActivity a,LinearLayout page,boolean admin){
      this.a=a;this.admin=admin;a.heading(page,"",admin?"建议与反馈管理":"建议与反馈",admin?"仅超级管理员可查看全部反馈和回复。":"当前账号每天 "+("visitor".equals(a.repo.currentUser().role())?2:5)+" 条反馈；同设备每天合计 10 条，每天 UTC+8 00:00 重置。");
      if(admin){
        Ui.action(page,"导出未完成反馈（CSV）",false,()->a.feedbackDocuments.export());
        page.addView(Ui.text(a,"保留反馈编号、反馈类型、反馈内容，新增回复、处理状态两列。状态填写待处理、处理中或已完成，空白不修改。处理中由导出设置。保存为 CSV UTF-8。",14,Design.MUTED));
        Ui.action(page,"导入回复与状态",false,()->a.feedbackDocuments.importCsv());
      }else form(page);
      LinearLayout tabs=Ui.row(a);
      for(boolean archive:new boolean[]{false,true}){
        TextView tab=Ui.button(a,archive?"已完成":"未完成",false,()->selectArchive(archive));tabs.addView(tab,new LinearLayout.LayoutParams(0,-2,1));controls.add(tab);
      }
      page.addView(tabs);refresh=Ui.button(a,"刷新记录",false,()->load(current==null?1:current.page()));page.addView(refresh);
      error=error(a,page);status=Ui.text(a,"",14,Design.MUTED);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);page.addView(status);
      rows=Ui.col(a);page.addView(rows);paging=Ui.col(a);page.addView(paging);
      page.post(()->{if(page.isAttachedToWindow())load(1);});
    }
    void form(LinearLayout page){
      LinearLayout card=Ui.card(page);String typeKey=a.draftKey("feedback-type"),requestKey=a.draftKey("feedback-request");
      TextView type=Ui.button(a,"",false,()->{});
      Runnable label=()->type.setText("反馈类型："+FeedbackModels.typeLabel(a.drafts.getString(typeKey,"function")));label.run();
      type.setOnClickListener(v->new AlertDialog.Builder(a).setTitle("反馈类型").setItems(FeedbackModels.TYPES.stream().map(FeedbackModels::typeLabel).toArray(String[]::new),(dialog,index)->{a.drafts.putString(typeKey,FeedbackModels.TYPES.get(index));resetRequest(a,requestKey);label.run();}).show());card.addView(type);
      EditText content=Ui.field(card,"反馈内容",false,true);content.setFilters(new InputFilter[]{new InputFilter.LengthFilter(5000)});content.setMinLines(4);track(content,()->resetRequest(a,requestKey));
      TextView failure=error(a,card),submit=Ui.button(a,"提交反馈",true,()->{});card.addView(submit);
      submit.setOnClickListener(v->{
        String text=content.getText().toString();if(text.isBlank()){failure.setText("请填写反馈内容");return;}
        String selected=a.drafts.getString(typeKey,"function"),id=request(a,requestKey);AtomicReference<Feedback> result=new AtomicReference<>();
        a.request(submit,failure,()->result.set(a.repo.submitFeedback(selected,text,id)),()->{a.clearDraft(a.route());a.message("反馈已提交");a.go("feedback-detail",result.get().id());});
      });
    }
    void busy(boolean value){busy=value;refresh.setEnabled(!value);for(TextView control:controls)control.setEnabled(!value);}
    void selectArchive(boolean archive){if(!busy&&!a.operationInFlight()){archived=archive;current=null;rows.removeAllViews();paging.removeAllViews();load(1);}}
    void load(int number){
      if(busy||a.operationInFlight())return;busy(true);status.setText("加载中…");AtomicReference<Page> result=new AtomicReference<>();
      a.read(key,refresh,error,()->result.set(a.repo.feedback(admin,archived,number)),()->{
        Page next=result.get();if(next.items().isEmpty()&&number>1){load(Math.max(1,(int)((next.total()+19)/20)));return;}
        current=next;paint();
      },()->{busy(false);status.setText(current==null?"":(archived?"已完成":"未完成")+" · "+current.total()+" 条");});
    }
    void paint(){
      rows.removeAllViews();paging.removeAllViews();while(controls.size()>2)controls.remove(controls.size()-1);
      if(current.items().isEmpty())Ui.empty(rows,archived?"暂无已完成反馈":"暂无未完成反馈","");
      for(Feedback row:current.items()){
        LinearLayout card=Ui.card(rows);card.addView(Ui.title(a,FeedbackModels.typeLabel(row.type())+" · "+FeedbackModels.statusLabel(row.status()),18));
        card.addView(Ui.text(a,time(row.createdAt()),12,Design.MUTED));if(admin)card.addView(Ui.text(a,"提交人："+row.userDisplayName(),13,Design.MUTED));
        TextView content=Ui.text(a,row.content(),15,Design.INK);content.setMaxLines(4);content.setEllipsize(TextUtils.TruncateAt.END);card.addView(content);
        if(!row.latestReply().isBlank()){TextView reply=Ui.text(a,"最新回复："+row.latestReply(),14,Design.MUTED);reply.setMaxLines(3);reply.setEllipsize(TextUtils.TruncateAt.END);card.addView(reply);}
        Ui.action(card,"查看详情与回复历史",false,()->a.go(admin?"admin-feedback-detail":"feedback-detail",row.id()));
      }
      if(current.page()>1)pageButton("上一页",current.page()-1);
      if(current.hasMore())pageButton("下一页",current.page()+1);
      status.setText((archived?"已完成":"未完成")+" · "+current.total()+" 条 · 第 "+current.page()+" 页");
    }
    void pageButton(String text,int number){TextView button=Ui.button(a,text,false,()->load(number));paging.addView(button);controls.add(button);}
  }
  private static final class DetailScreen {
    final MainActivity a;final boolean admin;final String id,key="feedback-detail:"+UUID.randomUUID();
    final LinearLayout body,history;final TextView error,retry,replyError;
    boolean detailLoading,replyLoading;
    DetailScreen(MainActivity a,LinearLayout page,boolean admin,String id){
      this.a=a;this.admin=admin;this.id=id;a.heading(page,"","反馈详情","");error=error(a,page);
      retry=Ui.button(a,"刷新详情",false,this::load);page.addView(retry);body=Ui.col(a);page.addView(body);
      page.addView(Ui.title(a,"回复历史",20));replyError=error(a,page);Ui.action(page,"刷新回复",false,()->replies(1));history=Ui.col(a);page.addView(history);
      page.post(()->{if(page.isAttachedToWindow())load();});
    }
    void load(){
      if(detailLoading)return;detailLoading=true;
      AtomicReference<Feedback> result=new AtomicReference<>();
      a.read(key,retry,error,()->result.set(a.repo.feedbackDetail(id,admin)),()->{paint(result.get());replies(1);},()->detailLoading=false);
    }
    void paint(Feedback row){
      body.removeAllViews();body.addView(Ui.title(a,FeedbackModels.typeLabel(row.type())+" · "+FeedbackModels.statusLabel(row.status()),20));
      body.addView(Ui.text(a,time(row.createdAt()),12,Design.MUTED));if(admin)body.addView(Ui.text(a,"提交人："+row.userDisplayName(),14,Design.MUTED));
      TextView content=Ui.text(a,row.content(),16,Design.INK);content.setTextIsSelectable(true);body.addView(content);
      if(!admin||row.status().equals("completed"))return;
      String requestKey=a.draftKey("feedback-reply-request");
      EditText reply=Ui.field(body,"回复内容",false,true);reply.setFilters(new InputFilter[]{new InputFilter.LengthFilter(5000)});reply.setMinLines(4);track(reply,()->resetRequest(a,requestKey));
      CheckBox complete=new CheckBox(a);complete.setText("标为已完成，移入归档并从下次导出中排除");complete.setTextColor(Design.INK);complete.setMinHeight(Ui.dp(a,48));body.addView(complete);
      complete.setOnCheckedChangeListener((view,checked)->resetRequest(a,requestKey));TextView failure=error(a,body),submit=Ui.button(a,"保存回复与状态",true,()->{});body.addView(submit);
      submit.setOnClickListener(v->{
        String text=reply.getText().toString();boolean done=complete.isChecked();if(text.isBlank()&&!done){failure.setText("请填写回复或勾选已完成");return;}
        String request=request(a,requestKey);a.request(submit,failure,()->a.repo.replyToFeedback(id,text,done,request),()->{reply.setText("");complete.setChecked(false);a.clearDraft(a.route());submit.setEnabled(false);a.message("处理结果已保存");load();});
      });
    }
    void replies(int number){
      if(replyLoading)return;replyLoading=true;
      AtomicReference<ReplyPage> result=new AtomicReference<>();
      a.read(key+":replies",null,replyError,()->result.set(a.repo.feedbackReplies(id,admin,number)),()->{
        history.removeAllViews();ReplyPage page=result.get();if(page.items().isEmpty())Ui.empty(history,"暂无管理员回复","");
        for(Reply row:page.items()){LinearLayout card=Ui.card(history);card.addView(Ui.text(a,time(row.createdAt()),12,Design.MUTED));TextView text=Ui.text(a,row.content(),15,Design.INK);text.setTextIsSelectable(true);card.addView(text);}
        if(page.page()>1)Ui.action(history,"上一页回复",false,()->replies(page.page()-1));
        if(page.hasMore())Ui.action(history,"下一页回复",false,()->replies(page.page()+1));
      },()->replyLoading=false);
    }
  }
  private FeedbackScreen(){}
}
