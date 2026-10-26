package cn.edu.bjtu.foodtime.presentation;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.widget.*;
import cn.edu.bjtu.foodtime.domain.model.Models.*;
import java.util.*;

final class ProfileScreen {
  static void profile(MainActivity a, LinearLayout page, MainActivity.Route route) {
    User user = a.repo.currentUser();
    page.setBackground(new ProfileDoodleDrawable(a));
    a.heading(page, "03", "我的手账", "把喜欢的味道、写下的点评，都收进这一页。");
    LinearLayout identity = Ui.col(a);
    identity.setPadding(Ui.dp(a,18),Ui.dp(a,18),Ui.dp(a,18),Ui.dp(a,18));
    identity.setBackground(Ui.shape(a,Design.ALT,0,24));
    LinearLayout person=Ui.row(a);person.setGravity(Gravity.CENTER_VERTICAL);
    android.view.View avatar=Ui.image(a,Ui.thumb(user.avatarUrl()),user.displayName().isEmpty()?"用户":user.displayName().substring(0,1),false);
    avatar.setBackground(Ui.shape(a,Design.ALT,Design.RED,999));avatar.setClipToOutline(true);
    avatar.setContentDescription("个人头像");
    person.addView(avatar, new LinearLayout.LayoutParams(Ui.dp(a,72),Ui.dp(a,72)));
    LinearLayout copy=Ui.col(a);LinearLayout.LayoutParams copyLp=new LinearLayout.LayoutParams(0,-2,1);copyLp.leftMargin=Ui.dp(a,16);
    copy.addView(Ui.title(a,user.displayName(),26));Ui.gap(copy,6);copy.addView(Ui.text(a,user.email(),13,Design.MUTED));
    person.addView(copy,copyLp);identity.addView(person);Ui.gap(identity,16);
    LinearLayout identityFoot=Ui.row(a);
    TextView motto=Ui.note(a,"认真吃饭，也认真生活");motto.setTextSize(17);
    identityFoot.addView(motto,new LinearLayout.LayoutParams(0,-2,1));
    TextView edit=Ui.button(a,"编辑资料",false,() -> a.go("profile-edit",""));edit.setTextSize(13);edit.setMinHeight(Ui.dp(a,38));
    edit.setBackground(Ui.ripple(a,Design.PAPER,Design.RED,19));edit.setTextColor(Design.RED);identityFoot.addView(edit);
    identity.addView(identityFoot);
    page.addView(identity,new LinearLayout.LayoutParams(-1,-2));Ui.gap(page,20);

    TextView section=Ui.note(a,"翻一翻我的校园味道");section.setTextSize(22);section.setTextColor(Design.RED);page.addView(section);Ui.gap(page,12);
    LinearLayout tabs=Ui.row(a);tabs.setPadding(Ui.dp(a,4),Ui.dp(a,4),Ui.dp(a,4),Ui.dp(a,4));
    tabs.setBackground(Ui.shape(a,Design.KRAFT_SOFT,0,24));
    LinearLayout panel=Ui.col(a);panel.setPadding(Ui.dp(a,18),Ui.dp(a,20),Ui.dp(a,18),Ui.dp(a,20));
    panel.setBackground(Ui.shape(a,Design.PAPER,Design.BORDER,22));
    String[] labels={"评论","提交","账号","退出"};TextView[] tabViews=new TextView[labels.length];int[] selected={0};
    Runnable[] refresh=new Runnable[1];
    refresh[0]=() -> {
      for(int i=0;i<tabViews.length;i++) {
        boolean active=i==selected[0];tabViews[i].setTextColor(active?Design.ON_RED:Design.INK);
        tabViews[i].setBackground(Ui.ripple(a,active?Design.RED:Color.TRANSPARENT,0,20));
      }
      renderPanel(a,panel,user,selected[0]);
    };
    for(int i=0;i<labels.length;i++) {
      int index=i;TextView tab=Ui.text(a,labels[i],14,Design.INK);tab.setGravity(Gravity.CENTER);tab.setMinHeight(Ui.dp(a,42));
      tab.setClickable(true);tab.setFocusable(true);tab.setOnClickListener(v -> {selected[0]=index;refresh[0].run();});
      tabViews[i]=tab;tabs.addView(tab,new LinearLayout.LayoutParams(0,-2,1));
    }
    page.addView(tabs,new LinearLayout.LayoutParams(-1,-2));Ui.gap(page,12);page.addView(panel,new LinearLayout.LayoutParams(-1,-2));
    refresh[0].run();Ui.gap(page,24);
    LinearLayout doodles=Ui.row(a);doodles.setGravity(Gravity.CENTER);
    doodles.addView(Ui.foodDoodle(a,7,0x554f7250),new LinearLayout.LayoutParams(Ui.dp(a,74),Ui.dp(a,58)));
    doodles.addView(Ui.foodDoodle(a,8,0x55c9341e),new LinearLayout.LayoutParams(Ui.dp(a,74),Ui.dp(a,58)));page.addView(doodles);
  }

  private static void renderPanel(MainActivity a,LinearLayout panel,User user,int selected) {
    panel.removeAllViews();String title;String action;
    if(selected==0) {title="我写下的味道";action="查看我的评论";}
    else if(selected==1) {title="我的投稿";action="查看审核记录";}
    else if(selected==2) {title="账号管理";action="编辑头像与名称";}
    else {title="退出账号";action="退出登录";}
    panel.addView(Ui.title(a,title,22));Ui.gap(panel,16);
    if(selected==0) {
      roundedAction(a,panel,action,false,() -> a.go("mine-reviews",""));Ui.gap(panel,10);
      roundedAction(a,panel,"查看我的回复",false,() -> a.go("mine-review-replies","1"));
      roundedAction(a,panel,"我的社区食记",false,()->a.go("community-mine",""));
      roundedAction(a,panel,"我的社区收藏",false,()->a.go("community-favorites",""));
      Ui.gap(panel,10);
      long inboxUnread=a.repo.unreadCount();
      roundedAction(a,panel,inboxUnread>0?"通知信箱 · "+inboxUnread+"条未读":"通知信箱",false,()->a.go("inbox",""));
    }
    else if(selected==1) roundedAction(a,panel,action,false,() -> a.go("mine-submissions",""));
    else if(selected==2) {
      roundedAction(a,panel,action,false,() -> a.go("profile-edit",""));Ui.gap(panel,10);
      roundedAction(a,panel,"修改密码",false,() -> a.go("password",""));
    } else roundedAction(a,panel,action,true,() -> Ui.confirm(a,"退出登录？","",a::logout));
  }

  private static void roundedAction(MainActivity a,LinearLayout panel,String label,boolean primary,Runnable run) {
    TextView button=Ui.button(a,label,primary,run);
    button.setBackground(Ui.ripple(a,primary?Design.RED:Design.PAPER,Design.INK,18));
    panel.addView(button,new LinearLayout.LayoutParams(-1,-2));
  }

  static void edit(MainActivity a,LinearLayout page,MainActivity.Route route) {
    User user=a.repo.currentUser();a.heading(page,"03.1","编辑资料","");
    LinearLayout card=Ui.col(a);card.setPadding(Ui.dp(a,18),Ui.dp(a,20),Ui.dp(a,18),Ui.dp(a,20));
    card.setBackground(Ui.shape(a,Design.ALT,0,24));
    String imageKey=a.draftKey("image");
    if(!a.drafts.containsKey(imageKey)) a.drafts.putString(imageKey,user.avatarUrl());
    String image=a.drafts.getString(imageKey,"");
    FrameLayout avatarFrame=new FrameLayout(a);avatarFrame.setBackground(Ui.shape(a,Design.PAPER,Design.RED,999));
    avatarFrame.setClipToOutline(true);
    avatarFrame.addView(Ui.image(a,Ui.thumb(image),"头像预览",false),new FrameLayout.LayoutParams(-1,-1));
    LinearLayout avatarRow=Ui.row(a);avatarRow.setGravity(Gravity.CENTER);
    avatarRow.addView(avatarFrame,new LinearLayout.LayoutParams(Ui.dp(a,112),Ui.dp(a,112)));
    card.addView(avatarRow);Ui.gap(card,12);
    TextView choose=Ui.button(a,image.isBlank()?"选择头像":"更换头像",false,() -> a.pickImage(imageKey));
    choose.setBackground(Ui.ripple(a,Design.PAPER,Design.RED,18));choose.setTextColor(Design.RED);card.addView(choose);
    Ui.gap(card,18);
    String nameKey=a.draftKey("昵称");
    if(!a.drafts.containsKey(nameKey)) a.drafts.putString(nameKey,user.displayName());
    EditText name=Ui.field(card,"昵称",false,false);name.setBackground(Ui.shape(a,Design.PAPER,Design.BORDER,16));
    TextView error=Ui.text(a,"",14,Design.RED);error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    card.addView(error);Ui.gap(card,8);
    TextView save=Ui.button(a,"保存资料",true,() -> {});save.setBackground(Ui.ripple(a,Design.RED,0,18));
    save.setOnClickListener(v -> {
      String displayName=name.getText().toString();String selected=a.drafts.getString(imageKey,"");
      a.request(v,error,() -> a.repo.updateProfile(displayName,selected),() -> {
        Ui.clearImages();a.clearDraft(route);a.message("个人资料已更新");a.back();
      });
    });
    card.addView(save,new LinearLayout.LayoutParams(-1,-2));page.addView(card,new LinearLayout.LayoutParams(-1,-2));Ui.gap(page,18);
    LinearLayout marks=Ui.row(a);marks.setGravity(Gravity.CENTER);
    marks.addView(Ui.foodDoodle(a,2,0x665b7a3a),new LinearLayout.LayoutParams(Ui.dp(a,74),Ui.dp(a,56)));
    marks.addView(Ui.foodDoodle(a,6,0x66c9341e),new LinearLayout.LayoutParams(Ui.dp(a,74),Ui.dp(a,56)));
    page.addView(marks);
  }

  static void reviews(MainActivity a, LinearLayout page, MainActivity.Route route) {
    a.heading(page, "02.1", "我的评论", "");
    List<Review> reviews = a.repo.myReviews();
    if (reviews.isEmpty()) {
      Ui.empty(page, "暂无已发布评论", "刚提交的点评需要先审核，可在“我的提交”查看进度和驳回原因。");
      Ui.action(page, "查看点评审核记录", true, () -> a.go("mine-submissions", ""));
    }
    for (Review r : reviews) {
      LinearLayout card = Ui.card(page);
      card.addView(Ui.text(a, SubmissionScreen.date(r.createdAt()), 12, Design.MUTED));
      Ui.gap(card, 12);
      card.addView(Ui.text(a, a.repo.context(r.dishId()), 14, Design.BLUE));
      Ui.gap(card, 8);
      card.addView(Ui.text(a, "★".repeat(r.rating()) + " / " + r.rating() + "分", 16, Design.RED));
      card.addView(Ui.text(a, r.content(), 16, Design.INK));
      Ui.gap(card, 12);
      LinearLayout actions = Ui.row(a);
      actions.addView(
          Ui.button(a, "查看菜品", false, () -> a.go("detail", r.dishId())),
          new LinearLayout.LayoutParams(0, -2, 1));
      TextView del =
          Ui.button(
              a,
              "删除",
              false,
              () ->
                  Ui.confirm(
                      a,
                      "删除这条评论？",
                      "删除后不可恢复",
                      () -> a.request(null,null,() -> a.repo.deleteReview(r.id()),() -> {a.message("评论已删除");a.render();})));
      del.setTextColor(Design.RED);
      actions.addView(del, new LinearLayout.LayoutParams(0, -2, 1));
      card.addView(actions);
    }
  }

  private static final class ProfileDoodleDrawable extends Drawable {
    final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);final int paper;final float unit;
    ProfileDoodleDrawable(MainActivity a) {paper=Design.PAPER;unit=Ui.dp(a,110);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Ui.dp(a,1.2f));p.setStrokeCap(Paint.Cap.ROUND);}
    @Override public void draw(Canvas c) {c.drawColor(paper);Rect b=getBounds();p.setColor(0x16c9341e);
      float rx=b.right-unit*.35f,top=unit*.9f;
      c.drawCircle(rx,top,unit*.18f,p);c.drawLine(rx-unit*.22f,top+unit*.18f,rx+unit*.22f,top+unit*.18f,p);
      float lx=unit*.34f,mid=unit*3.7f;Path leaf=new Path();leaf.moveTo(lx-unit*.24f,mid+unit*.12f);
      leaf.cubicTo(lx-unit*.12f,mid-unit*.26f,lx+unit*.22f,mid-unit*.3f,lx+unit*.28f,mid-unit*.12f);
      leaf.cubicTo(lx+unit*.14f,mid+unit*.2f,lx-unit*.08f,mid+unit*.25f,lx-unit*.24f,mid+unit*.12f);c.drawPath(leaf,p);
      float bx=b.right-unit*.38f,bottom=unit*6.5f;
      c.drawArc(new RectF(bx-unit*.28f,bottom-unit*.14f,bx+unit*.28f,bottom+unit*.18f),180,180,false,p);
      c.drawLine(bx-unit*.28f,bottom+unit*.02f,bx+unit*.28f,bottom+unit*.02f,p);
    }
    @Override public void setAlpha(int alpha) {} @Override public void setColorFilter(android.graphics.ColorFilter filter) {}
    @Override public int getOpacity() {return PixelFormat.OPAQUE;}
  }
}
