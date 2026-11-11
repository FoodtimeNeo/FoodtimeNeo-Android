package cn.edu.bjtu.foodtime.presentation;

import android.app.AlertDialog;
import android.text.*;
import android.widget.*;
import android.view.View;
import android.view.ViewGroup;
import cn.edu.bjtu.foodtime.domain.model.NoticeSchedule;
import cn.edu.bjtu.foodtime.domain.model.NoticeTimes;
import cn.edu.bjtu.foodtime.domain.repository.FoodtimeRepository.AdminNotice;
import cn.edu.bjtu.foodtime.domain.repository.FoodtimeRepository.AdminNoticePage;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

final class AdminNoticesScreen {
  private static void guard(MainActivity a){if(a.repo.currentUser()==null||!"superadmin".equals(a.repo.currentUser().role()))throw new IllegalStateException("需要平台管理员权限");}
  static void create(MainActivity a,LinearLayout page,MainActivity.Route route){
    guard(a);a.heading(page,"",route.id().isBlank()?"创建公告":"编辑公告","每条公告独立启停和排期，草稿不会发布或弹出。");
    LinearLayout body=Ui.col(a);page.addView(body);TextView error=error(a,page);
    if(route.id().isBlank()){editor(a,body,route,null);return;}
    Ui.action(page,"重新读取公告",false,()->readEditor(a,body,error,route));readEditor(a,body,error,route);
  }
  private static void readEditor(MainActivity a,LinearLayout body,TextView error,MainActivity.Route route){
    AtomicReference<AdminNotice> result=new AtomicReference<>();
    a.read("notice-editor:"+System.identityHashCode(body)+":"+UUID.randomUUID(),null,error,()->result.set(a.repo.adminNotice(route.id())),()->{body.removeAllViews();editor(a,body,route,result.get());},()->{});
  }
  private static TextView error(MainActivity a,LinearLayout page){TextView error=Ui.text(a,"",14,Design.RED);Ui.observeError(error);page.addView(error);return error;}
  private static void editor(MainActivity a,LinearLayout body,MainActivity.Route route,AdminNotice row){
    NoticeSchedule value=row==null?new NoticeSchedule("","","","",false,false,true):row.schedule();
    NoticeScheduleFields fields=new NoticeScheduleFields(a,body,value);
    String contentKey=a.draftKey("Markdown 内容");if(!a.drafts.containsKey(contentKey))a.drafts.putString(contentKey,row==null?"":row.content());
    EditText content=Ui.field(body,"Markdown 内容",false,true);content.setFilters(new InputFilter[]{new InputFilter.LengthFilter(20000)});content.setMinLines(10);
    Ui.action(body,"预览公告",false,()->preview(a,content.getText().toString()));
    View previous=a.root.findViewWithTag("notice-editor-actions");if(previous!=null&&previous.getParent() instanceof ViewGroup parent)parent.removeView(previous);
    LinearLayout footer=Ui.col(a);footer.setTag("notice-editor-actions");a.pinNoticeActions(footer);TextView failure=error(a,footer);
    List<TextView> buttons=new ArrayList<>();
    Runnable enable=()->{fields.setEnabled(true);content.setEnabled(true);for(TextView button:buttons)button.setEnabled(true);};
    failure.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){if(s.length()>0)enable.run();}public void afterTextChanged(Editable text){}});
    for(boolean publish:value.published()?new boolean[]{true}:new boolean[]{true,false}){
      TextView save=Ui.button(a,publish?(value.published()?"保存修改":"发布或定时发布"):"保存草稿",publish,()->{});buttons.add(save);footer.addView(save);
      save.setOnClickListener(view->{
        if(a.operationInFlight())return;String markdown=content.getText().toString();NoticeSchedule schedule;
        try{if(markdown.isBlank())throw new IllegalArgumentException("内容不能为空");schedule=fields.value(publish);}catch(RuntimeException invalid){failure.setText(invalid.getMessage());return;}
        fields.setEnabled(false);content.setEnabled(false);for(TextView button:buttons)button.setEnabled(false);
        a.request(save,failure,()->a.repo.saveNotice(route.id(),markdown,schedule),()->{
          a.clearDraft(route);a.clearDraft(new MainActivity.Route("admin-notices",""));a.message(publish?"发布计划已保存":"草稿已保存，不会发布或弹出");a.replaceRoute("admin-notices","");
        });
      });
    }
  }
  static void settings(MainActivity a,LinearLayout page,MainActivity.Route route){guard(a);new NoticeList(a,page);}
  private static final class NoticeList {
    final MainActivity a;final EditText query;final TextView error,status,refresh;final LinearLayout rows,paging;
    final List<TextView> actions=new ArrayList<>();final String key="notice-list:"+UUID.randomUUID();boolean loading,writing;int number=1;
    NoticeList(MainActivity a,LinearLayout page){
      this.a=a;a.heading(page,"","公告管理","每类只弹最新有效一条，先更新公告，再普通公告；过期公告仍在信箱留档。时间为 UTC+8。");
      Ui.action(page,"创建公告",false,()->a.go("admin-notice-create",""));query=Ui.field(page,"搜索标题",false,false);
      refresh=Ui.button(a,"搜索 / 刷新",false,()->load(1));page.addView(refresh);error=error(a,page);status=Ui.text(a,"",14,Design.MUTED);page.addView(status);
      error.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){if(s.length()>0){writing=false;controls();}}public void afterTextChanged(Editable text){}});
      rows=Ui.col(a);page.addView(rows);paging=Ui.row(a);page.addView(paging);page.post(()->{if(page.isAttachedToWindow())load(1);});
    }
    void controls(){boolean enabled=!loading&&!writing;refresh.setEnabled(enabled);query.setEnabled(enabled);for(TextView action:actions)action.setEnabled(enabled);}
    void load(int page){
      if(loading||writing||a.operationInFlight())return;loading=true;controls();status.setText("加载中…");String search=query.getText().toString();AtomicReference<AdminNoticePage> result=new AtomicReference<>();
      a.read(key,refresh,error,()->result.set(a.repo.adminNotices(page,search)),()->{number=page;paint(result.get());},()->{loading=false;controls();});
    }
    void paint(AdminNoticePage data){
      rows.removeAllViews();paging.removeAllViews();actions.clear();if(data.items().isEmpty())Ui.empty(rows,"没有匹配的公告","");
      for(AdminNotice row:data.items()){
        LinearLayout card=Ui.card(rows);card.addView(Ui.title(a,row.title(),20));NoticeSchedule plan=row.schedule();
        card.addView(Ui.text(a,(plan.isUpdate()?"更新公告":"普通公告")+" · "+NoticeTimes.status(row.status()),14,Design.RED));
        card.addView(Ui.text(a,"发布时间："+NoticeTimes.display(plan.publishedAt()),13,Design.MUTED));
        card.addView(Ui.text(a,"弹窗开始："+NoticeTimes.display(plan.displayStartsAt().isBlank()?plan.publishedAt():plan.displayStartsAt()),13,Design.MUTED));
        card.addView(Ui.text(a,"弹窗截止："+(!plan.expiresOn().isBlank()?plan.expiresOn()+" 当天结束（UTC+8）":plan.displayEndsAt().isBlank()?"不设截止时间":NoticeTimes.display(plan.displayEndsAt())),13,Design.MUTED));
        action(card,"编辑",()->a.go("admin-notice-create",row.id()));action(card,"查看全文",()->preview(a,row.content()));
        if(plan.published())action(card,plan.enabled()?"停用弹窗":"启用弹窗",()->toggle(row));
        else action(card,"设置发布计划",()->a.go("admin-notice-create",row.id()));
      }
      if(number>1)action(paging,"上一页",()->load(number-1));
      status.setText("第 "+number+" 页 · "+data.total()+" 条");if(number*6L<data.total())action(paging,"下一页",()->load(number+1));controls();
    }
    void action(LinearLayout parent,String label,Runnable run){TextView button=Ui.button(a,label,false,run);parent.addView(button);actions.add(button);}
    void toggle(AdminNotice row){
      if(loading||writing||a.operationInFlight())return;writing=true;controls();NoticeSchedule old=row.schedule();
      NoticeSchedule next=new NoticeSchedule(old.publishedAt(),old.displayStartsAt(),old.displayEndsAt(),old.expiresOn(),old.isUpdate(),true,!old.enabled());
      // Date-only metadata already contains the resolved instant; send just the original calendar day.
      if(!next.expiresOn().isBlank())next=new NoticeSchedule(next.publishedAt(),next.displayStartsAt(),"",next.expiresOn(),next.isUpdate(),next.published(),next.enabled());
      NoticeSchedule saved=next;a.request(null,error,()->a.repo.saveNoticeSchedule(row.id(),saved),()->{writing=false;a.message(old.enabled()?"弹窗已停用，信箱保留已发布公告":"弹窗已启用，将按排期生效");load(number);});
    }
  }
  private static void preview(MainActivity a,String content){
    TextView text=Ui.text(a,"",16,Design.INK);NoticeMarkdown.render(text,content);ScrollView scroll=new ScrollView(a);scroll.setPadding(Ui.dp(a,16),Ui.dp(a,16),Ui.dp(a,16),Ui.dp(a,16));scroll.addView(text);
    new AlertDialog.Builder(a).setTitle("公告预览").setView(scroll).setPositiveButton("关闭",null).show();
  }
  private AdminNoticesScreen(){}
}
