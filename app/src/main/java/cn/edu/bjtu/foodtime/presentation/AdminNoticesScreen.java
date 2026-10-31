package cn.edu.bjtu.foodtime.presentation;
import android.app.AlertDialog;
import android.text.InputFilter;
import android.widget.*;
import cn.edu.bjtu.foodtime.domain.repository.FoodtimeRepository.AdminNotice;
import cn.edu.bjtu.foodtime.domain.repository.FoodtimeRepository.AdminNoticePage;

/** Separate creation and settings pages. Confirmation lives outside the scrolling page. */
final class AdminNoticesScreen {
  private static final String KEY="notice-management:";
  private static void guard(MainActivity a) {
    AdminScreen.guard(a);
    if(!a.repo.currentUser().role().equals("superadmin")) throw new IllegalStateException("需要平台管理员权限");
  }
  private static void select(MainActivity a,AdminNotice item) {
    a.drafts.putString(KEY+"selected",item==null?"":item.id());
    a.drafts.putString(KEY+"title",item==null?"暂不显示公告":item.title());
    a.drafts.putString(KEY+"content",item==null?"":item.content());
  }
  static void create(MainActivity a,LinearLayout page,MainActivity.Route route) {
    guard(a);a.heading(page,"","创建公告","保存后在设置公告内确认启用");
    String id=a.drafts.getString(KEY+"edit","");
    EditText input=Ui.field(page,"Markdown 内容",false,true);
    input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(20000)});
    input.setMinLines(12);input.setText(a.drafts.getString(KEY+"draft",""));
    Ui.observe(input,()->a.drafts.putString(KEY+"draft",input.getText().toString()));
    TextView error=Ui.text(a,"",14,Design.RED);page.addView(error);
    Ui.action(page,"预览",false,()->preview(a,input.getText().toString()));
    Ui.gap(page,12);
    TextView save=Ui.button(a,id.isEmpty()?"保存公告":"保存修改",true,()->{});page.addView(save);
    save.setOnClickListener(v->{
      String content=input.getText().toString();
      if(content.isBlank()){error.setText("内容不能为空");return;}
      AdminNotice[] saved={null};
      a.request(save,error,()->saved[0]=a.repo.saveNotice(id,content),()->{
        select(a,saved[0]);a.drafts.remove(KEY+"draft");a.drafts.remove(KEY+"edit");
        a.message("公告已保存，请确认设置");a.replaceRoute("admin-notices","");
      });
    });
    if(!id.isEmpty()) Ui.action(page,"取消编辑",false,()->{a.drafts.remove(KEY+"edit");a.drafts.remove(KEY+"draft");a.replaceRoute("admin-notices","");});
  }
  static void settings(MainActivity a,LinearLayout page,MainActivity.Route route) {
    guard(a);a.heading(page,"","设置公告","");
    Ui.action(page,"创建公告",false,()->a.go("admin-notice-create",""));
    TextView current=Ui.text(a,"正在读取当前公告…",16,Design.INK);page.addView(current);
    EditText search=Ui.field(page,"搜索标题",false,false);
    search.setFilters(new InputFilter[]{new InputFilter.LengthFilter(200)});
    search.setText(a.drafts.getString(KEY+"query",""));
    TextView error=Ui.text(a,"",14,Design.RED);page.addView(error);
    LinearLayout list=Ui.col(a);page.addView(list);
    LinearLayout pager=Ui.row(a);page.addView(pager);
    LinearLayout footer=Ui.col(a);a.pinNoticeActions(footer);page.removeView(error);footer.addView(error);
    TextView pending=Ui.text(a,"",16,Design.INK);pending.setMaxLines(2);pending.setEllipsize(android.text.TextUtils.TruncateAt.END);footer.addView(pending);
    LinearLayout actions=Ui.row(a);footer.addView(actions);
    TextView view=Ui.button(a,"查看全文",false,()->preview(a,a.drafts.getString(KEY+"content","")));actions.addView(view,new LinearLayout.LayoutParams(0,-2,1));
    TextView edit=Ui.button(a,"编辑",false,()->{a.drafts.putString(KEY+"draft",a.drafts.getString(KEY+"content",""));a.drafts.putString(KEY+"edit",a.drafts.getString(KEY+"selected",""));a.go("admin-notice-create","");});actions.addView(edit,new LinearLayout.LayoutParams(0,-2,1));
    TextView apply=Ui.button(a,"确认设置",true,()->{});footer.addView(apply);
    String[] active={""};boolean[] ready={false};
    Runnable update=()->{
      String selected=a.drafts.getString(KEY+"selected","");
      pending.setText("待设置："+a.drafts.getString(KEY+"title","暂不显示公告"));
      view.setEnabled(!selected.isEmpty());edit.setEnabled(!selected.isEmpty());apply.setEnabled(ready[0]&&!selected.equals(active[0]));
    };
    apply.setOnClickListener(v->{String selected=a.drafts.getString(KEY+"selected","");String selectedTitle=a.drafts.getString(KEY+"title","暂不显示公告");
      a.request(apply,error,()->a.repo.setNoticeSetting(selected),()->{active[0]=selected;current.setText("当前公告："+selectedTitle);a.message("公告设置已保存");update.run();});
    });
    boolean[] loading={false};
    Runnable[] load={null};
    load[0]=()->{
      if(loading[0])return;loading[0]=true;
      int number=a.drafts.getInt(KEY+"page",1);String query=a.drafts.getString(KEY+"query","");
      AdminNoticePage[] result={null};
      a.read("admin-notices:"+System.identityHashCode(page)+":"+number+":"+query,null,error,()->result[0]=a.repo.adminNotices(number,query),()->{
        if(number!=a.drafts.getInt(KEY+"page",1)||!query.equals(a.drafts.getString(KEY+"query","")))return;
        list.removeAllViews();pager.removeAllViews();RadioGroup radios=new RadioGroup(a);list.addView(radios);
        RadioButton off=new RadioButton(a);off.setText("暂不显示公告");off.setMinHeight(Ui.dp(a,48));radios.addView(off);off.setChecked(a.drafts.getString(KEY+"selected","").isEmpty());off.setOnClickListener(v->{select(a,null);update.run();});
        for(AdminNotice item:result[0].items()) {
          RadioButton row=new RadioButton(a);row.setText(item.title()+(item.id().equals(active[0])?" · 当前公告":""));row.setTextColor(Design.INK);row.setMinHeight(Ui.dp(a,56));radios.addView(row);row.setChecked(item.id().equals(a.drafts.getString(KEY+"selected","")));
          row.setOnClickListener(v->{select(a,item);update.run();});
        }
        if(result[0].items().isEmpty())list.addView(Ui.text(a,"没有匹配的公告",16,Design.MUTED));
        TextView previous=Ui.button(a,"上一页",false,()->{a.drafts.putInt(KEY+"page",number-1);load[0].run();});previous.setEnabled(number>1);pager.addView(previous);
        pager.addView(Ui.text(a,number+" / "+Math.max(1,(result[0].total()+5)/6)+" 页",14,Design.INK),new LinearLayout.LayoutParams(0,-2,1));
        TextView next=Ui.button(a,"下一页",false,()->{a.drafts.putInt(KEY+"page",number+1);load[0].run();});next.setEnabled(number*6L<result[0].total());pager.addView(next);
      },()->{loading[0]=false;if(number!=a.drafts.getInt(KEY+"page",1)||!query.equals(a.drafts.getString(KEY+"query","")))load[0].run();});
    };
    Ui.action(page,"搜索 / 重试查询",false,()->{a.drafts.putString(KEY+"query",search.getText().toString());a.drafts.putInt(KEY+"page",1);load[0].run();});
    boolean[] initializing={false};
    Runnable initialize=()->{
      if(initializing[0])return;initializing[0]=true;
      AdminNotice[] item={null};String[] configured={""};
      a.read("notice-settings:"+System.identityHashCode(page),null,error,()->{configured[0]=a.repo.noticeSetting();if(!configured[0].isEmpty())item[0]=a.repo.adminNotice(configured[0]);},()->{
        active[0]=configured[0];ready[0]=true;current.setText("当前公告："+(item[0]==null?"暂不显示公告":item[0].title()));
        if(!a.drafts.containsKey(KEY+"selected"))select(a,item[0]);update.run();load[0].run();
      },()->initializing[0]=false);
    };
    Ui.action(page,"重新读取当前设置",false,initialize);update.run();initialize.run();
  }
  private static void preview(MainActivity a,String content) {
    TextView text=Ui.text(a,"",16,Design.INK);NoticeMarkdown.render(text,content);
    ScrollView scroll=new ScrollView(a);scroll.setPadding(Ui.dp(a,16),Ui.dp(a,16),Ui.dp(a,16),Ui.dp(a,16));scroll.addView(text);
    new AlertDialog.Builder(a).setTitle("公告预览").setView(scroll).setPositiveButton("关闭",null).show();
  }
  private AdminNoticesScreen() {}
}
