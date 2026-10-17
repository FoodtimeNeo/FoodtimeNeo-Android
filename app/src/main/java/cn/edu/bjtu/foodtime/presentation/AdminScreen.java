package cn.edu.bjtu.foodtime.presentation;

import android.app.AlertDialog;
import android.widget.*;
import cn.edu.bjtu.foodtime.domain.model.Models.*;
import cn.edu.bjtu.foodtime.domain.validation.Validation;
import java.math.BigDecimal;
import java.util.*;

final class AdminScreen {
  static void guard(MainActivity a) {
    if(a.repo.currentUser()==null||!a.repo.currentUser().isAdmin()) throw new IllegalStateException("需要管理员权限");
  }
  static void home(MainActivity a,LinearLayout page,MainActivity.Route route) {
    guard(a);a.heading(page,"","管理","");LinearLayout card=Ui.card(page);
    long pending=a.repo.submissions(null).stream().filter(s -> s.status().equals("pending")).count();
    card.addView(Ui.title(a,pending+" 条待审核",24));Ui.gap(card,16);
    Ui.action(card,"审核投稿",true,() -> a.go("admin-audit",""));Ui.gap(card,12);
    Ui.action(card,"食堂 · 档口 · 菜品",false,() -> a.go("admin-data","halls~"));Ui.gap(card,12);
    Ui.action(card,"评论管理",false,() -> a.go("admin-reviews",""));Ui.gap(card,12);
    Ui.action(card,"审核日志",false,() -> a.go("admin-logs",""));
    if(a.repo.currentUser().role().equals("superadmin")) {Ui.gap(card,12);Ui.action(card,"用户管理",false,() -> a.go("admin-users",""));}
  }
  static void audit(MainActivity a,LinearLayout page,MainActivity.Route route) {
    guard(a);a.heading(page,"","审核投稿","");
    List<Submission> all=a.repo.submissions(null);
    String key=a.draftKey("audit-status");
    String[] states={"pending","approved","rejected"};
    Spinner status=SubmissionScreen.filter(a,page,"审核状态",new String[]{"待审核","已通过","已驳回"},a.drafts.getInt(key,0));
    LinearLayout list=Ui.col(a);page.addView(list);
    Runnable update=() -> {
      int index=status.getSelectedItemPosition();a.drafts.putInt(key,index);list.removeAllViews();int count=0;
      for(Submission s:all) if(s.status().equals(states[index])) {
        count++;LinearLayout card=SubmissionScreen.card(a,list,s);
        if(s.status().equals("pending")) {
          Ui.gap(card,16);Ui.action(card,"通过",false,() -> decision(a,s,true));Ui.gap(card,8);
          Ui.action(card,"驳回",false,() -> decision(a,s,false));
        }
      }
      if(count==0) Ui.empty(list,"暂无"+SubmissionScreen.status(states[index])+"投稿","");
    };
    status.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      public void onNothingSelected(AdapterView<?> parent) {}
      public void onItemSelected(AdapterView<?> parent,android.view.View view,int position,long id) {update.run();}
    });update.run();
  }
  private static void decision(MainActivity a,Submission s,boolean approve) {
    LinearLayout form=Ui.col(a);form.setPadding(Ui.dp(a,20),Ui.dp(a,16),Ui.dp(a,20),Ui.dp(a,8));
    EditText price=approve&&s.type().equals("dish")&&s.price()==null?Ui.field(form,"补齐价格（元）",false,false):null;
    if(price!=null) price.setInputType(8194);
    EditText reason=Ui.field(form,approve?"说明（可选）":"驳回原因",false,true);
    TextView error=Ui.text(a,"",14,Design.RED);form.addView(error);
    AlertDialog dialog=new AlertDialog.Builder(a).setTitle(approve?"通过投稿？":"驳回投稿")
        .setView(form).setNegativeButton("取消",null).setPositiveButton("确认",null).create();
    dialog.setOnShowListener(v -> dialog.getButton(-1).setOnClickListener(w -> {
      try {
        String explanation=reason.getText().toString();
        if(!approve&&explanation.isBlank()) throw new IllegalArgumentException("请填写驳回原因");
        BigDecimal amount=price==null?null:Validation.price(price.getText().toString());
        a.request(w,error,() -> a.repo.moderate(s.id(),approve?"approve":"reject",explanation,amount),
            () -> {dialog.dismiss();a.message(approve?"已通过":"已驳回");a.render();});
      } catch(RuntimeException e) {error.setText(e.getMessage());}
    }));dialog.show();
  }
  static void data(MainActivity a,LinearLayout page,MainActivity.Route route) {
    guard(a);String[] args=route.id().split("~",-1);String table=args[0],parent=args.length>1?args[1]:"";
    String type=label(table);a.heading(page,"",type+"管理","停用后不向用户展示");
    List<AdminEntity> all=a.repo.entities(table,parent);if(all.isEmpty()) Ui.empty(page,"暂无记录","");
    for(AdminEntity item:all) {
      LinearLayout card=Ui.card(page);card.addView(Ui.title(a,item.name(),24));Ui.gap(card,8);
      card.addView(Ui.stamp(a,item.active()?"启用":"停用",item.active()?Design.GREEN:Design.RED));
      if(table.equals("dishes")) {Ui.gap(card,8);card.addView(Ui.text(a,"¥"+item.price(),16,Design.RED));}
      Ui.gap(card,16);Ui.action(card,"编辑",false,() -> a.go("admin-edit",table+"~"+item.id()));
      if(!table.equals("dishes")) {
        Ui.gap(card,8);String child=table.equals("halls")?"stalls":"dishes";
        Ui.action(card,"管理"+label(child),false,() -> a.go("admin-data",child+"~"+item.id()));
      }
    }
    Ui.action(page,"新增"+type,true,() -> a.go("admin-new",table+"~"+parent));
  }
  private static String label(String table) {return table.equals("halls")?"食堂":table.equals("stalls")?"档口":"菜品";}
  static void edit(MainActivity a,LinearLayout page,MainActivity.Route route) {
    guard(a);String[] args=route.id().split("~",-1);String table=args[0],value=args.length>1?args[1]:"";
    boolean creating=route.kind().equals("admin-new");AdminEntity item=creating?null:a.repo.entity(table,value);
    if(!creating&&item==null) throw new IllegalArgumentException("记录不存在");
    a.heading(page,"",(creating?"新增":"编辑")+label(table),"");LinearLayout form=Ui.card(page);
    EditText name=Ui.field(form,"名称",false,false),description=Ui.field(form,"描述（可选）",false,true);
    EditText price=table.equals("dishes")?Ui.field(form,"价格（元）",false,false):null;
    if(price!=null) price.setInputType(8194);
    if(item!=null&&!a.drafts.containsKey(a.draftKey("loaded"))) {
      name.setText(item.name());description.setText(item.description());if(price!=null) price.setText(item.price());
      a.drafts.putBoolean(a.draftKey("loaded"),true);
    }
    Switch enabled=new Switch(a);enabled.setText("启用");enabled.setMinHeight(Ui.dp(a,48));enabled.setTextColor(Design.INK);
    String activeKey=a.draftKey("active");enabled.setChecked(a.drafts.getBoolean(activeKey,item==null||item.active()));
    enabled.setOnCheckedChangeListener((v,checked) -> a.drafts.putBoolean(activeKey,checked));form.addView(enabled);Ui.gap(form,16);
    String imageKey=a.draftKey("image"),image=a.drafts.getString(imageKey,item==null?"":item.coverImageUrl());
    if(!table.equals("dishes")) {
      form.addView(Ui.image(a,image,"暂无封面",true));Ui.gap(form,16);Ui.action(form,"选择封面",false,() -> a.pickImage(imageKey));Ui.gap(form,16);
    }
    TextView error=Ui.text(a,"",14,Design.RED);form.addView(error);Ui.gap(form,12);
    Ui.submit(form,"保存",error,() -> {
      String title=Validation.text(name.getText().toString(),table.equals("dishes")?120:100,"名称"),body=description.getText().toString();
      String amount=price==null?"":price.getText().toString();if(price!=null) Validation.price(amount);boolean active=enabled.isChecked();
      return () -> a.repo.editEntity(table,creating?"":value,creating?value:"",title,body,amount,image,active);
    },() -> {a.clearDraft(route);a.message("已保存");a.back();});
    if(!creating) {
      Ui.gap(form,12);Ui.action(form,"删除",false,() -> Ui.confirm(a,"删除这条记录？","存在关联记录时只能停用",
          () -> a.request(null,error,() -> a.repo.removeEntity(table,value),() -> {a.clearDraft(route);a.back();})));
    }
  }
  static void reviews(MainActivity a,LinearLayout page,MainActivity.Route route) {
    guard(a);a.heading(page,"","评论管理","");List<Review> all=a.repo.reviews(null);
    page.addView(Ui.text(a,"仅展示审核通过的评论；删除须填写原因，评分在每日快照刷新后更新。",14,Design.MUTED));
    EditText search=Ui.field(page,"按用户 ID 查询评论",false,false);
    TextView count=Ui.text(a,"",14,Design.MUTED);count.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);page.addView(count);
    LinearLayout list=Ui.col(a);page.addView(list);
    Runnable filter=() -> {
      String query=search.getText().toString().strip().toLowerCase(Locale.ROOT);list.removeAllViews();int found=0;
      for(Review review:all) if(review.userId().toLowerCase(Locale.ROOT).contains(query)) {
        found++;LinearLayout card=Ui.card(list);card.addView(Ui.text(a,"作者："+(review.userDisplayName().isEmpty()?review.userId():review.userDisplayName()),12,Design.BLUE));Ui.gap(card,8);
        card.addView(Ui.text(a,"评论 ID："+review.id(),12,Design.MUTED));
        card.addView(Ui.text(a,SubmissionScreen.date(review.createdAt()),12,Design.MUTED));
        card.addView(Ui.text(a,a.repo.context(review.dishId()),14,Design.MUTED));
        card.addView(Ui.text(a,"★".repeat(review.rating())+" · "+review.rating()+" / 5",16,Design.RED));card.addView(Ui.text(a,review.content(),16,Design.INK));Ui.gap(card,12);
        Ui.action(card,"删除违规评论",false,() -> removeReview(a,review));
      }
      count.setText("显示 "+found+" / "+all.size()+" 条评论");
      if(found==0) Ui.empty(list,query.isEmpty()?"暂无正式评论":"没有找到该用户的评论","");
    };Ui.observe(search,filter);filter.run();
    Ui.action(page,"清除筛选",false,() -> search.setText(""));
    Ui.gap(page,12);Ui.action(page,"查看投稿审核记录",false,() -> a.go("admin-audit",""));
  }
  private static void removeReview(MainActivity a,Review review) {
    LinearLayout form=Ui.col(a);form.setPadding(Ui.dp(a,20),Ui.dp(a,16),Ui.dp(a,20),Ui.dp(a,8));
    EditText reason=Ui.field(form,"删除原因",false,true);TextView error=Ui.text(a,"",14,Design.RED);form.addView(error);
    AlertDialog dialog=new AlertDialog.Builder(a).setTitle("删除评论？").setView(form).setNegativeButton("取消",null).setPositiveButton("删除",null).create();
    dialog.setOnShowListener(v -> dialog.getButton(-1).setOnClickListener(w -> {
      String value=reason.getText().toString();if(value.isBlank()) {error.setText("请填写删除原因");return;}
      a.request(w,error,() -> a.repo.removeReviewAsAdmin(review.id(),value),() -> {dialog.dismiss();a.render();});
    }));dialog.show();
  }
  static void users(MainActivity a,LinearLayout page,MainActivity.Route route) {
    guard(a);if(!a.repo.currentUser().role().equals("superadmin")) throw new IllegalStateException("需要平台管理员权限");
    a.heading(page,"","用户管理","");EditText search=Ui.field(page,"搜索邮箱或昵称",false,false);
    LinearLayout list=Ui.col(a);page.addView(list);List<User> all=a.repo.users();
    Runnable filter=() -> {
      list.removeAllViews();String query=search.getText().toString().strip().toLowerCase(Locale.ROOT);int count=0;
      for(User user:all) if(user.email().toLowerCase(Locale.ROOT).contains(query)||user.displayName().toLowerCase(Locale.ROOT).contains(query)) {
        count++;LinearLayout card=Ui.card(list);card.addView(Ui.title(a,user.displayName(),24));card.addView(Ui.text(a,user.email(),14,Design.MUTED));
        String role=user.role().equals("superadmin")?"平台管理员":user.isAdmin()?"管理员":"普通用户";
        card.addView(Ui.text(a,role+" · "+(user.active()?"启用":"禁用"),14,Design.BLUE));
        if(user.createdAt()>0) card.addView(Ui.text(a,SubmissionScreen.date(user.createdAt())+" · "+user.reviewCount()+" 条评论",12,Design.MUTED));
        if(!user.id().equals(a.repo.currentUser().id())) {
          Ui.gap(card,12);Ui.action(card,user.active()?"禁用账号":"启用账号",false,() -> Ui.confirm(a,"修改账号状态？",user.email(),
              () -> a.request(null,null,() -> a.repo.updateUser(user.id(),user.role(),!user.active()),a::render)));
          Ui.gap(card,8);Ui.action(card,"修改角色",false,() -> new AlertDialog.Builder(a).setTitle("设置角色")
              .setItems(new String[]{"普通用户","管理员","平台管理员"},(d,n) -> Ui.confirm(a,"修改角色？",user.email(),
                  () -> a.request(null,null,() -> a.repo.updateUser(user.id(),new String[]{"user","admin","superadmin"}[n],user.active()),a::render))).show());
        }
      }
      if(count==0) Ui.empty(list,"未找到用户","");
    };Ui.observe(search,filter);filter.run();
  }
  static void logs(MainActivity a,LinearLayout page,MainActivity.Route route) {
    guard(a);a.heading(page,"","审核日志","");List<Audit> all=a.repo.audits();if(all.isEmpty()) Ui.empty(page,"暂无审核记录","");
    for(Audit audit:all) {
      LinearLayout card=Ui.card(page);card.addView(Ui.stamp(a,audit.action().equals("approve")?"通过":"驳回",audit.action().equals("approve")?Design.GREEN:Design.RED));Ui.gap(card,12);
      card.addView(Ui.text(a,"提交："+audit.submissionId(),12,Design.MUTED));card.addView(Ui.text(a,"管理员："+audit.moderatorId(),12,Design.MUTED));
      card.addView(Ui.text(a,SubmissionScreen.date(audit.createdAt()),12,Design.MUTED));if(!audit.reason().isBlank()) card.addView(Ui.text(a,audit.reason(),16,Design.INK));
    }
  }
}
