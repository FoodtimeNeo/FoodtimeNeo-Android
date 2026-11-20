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
    profileHeader(a,page);
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
    LinearLayout stats=Ui.row(a);
    TextView followingCount=Ui.button(a,"关注 "+user.followingCount(),false,() -> a.go("following",""));
    followingCount.setTag("profile-following-count");
    followingCount.setContentDescription("我的关注，"+user.followingCount()+"人");
    followingCount.setBackground(Ui.ripple(a,Design.PAPER,Design.BORDER,19));
    followingCount.setTextColor(Design.INK);
    stats.addView(followingCount,new LinearLayout.LayoutParams(0,-2,1));
    TextView followerCount=Ui.button(a,"粉丝 "+user.followerCount(),false,() -> a.go("followers",""));
    followerCount.setTag("profile-follower-count");
    followerCount.setContentDescription("关注我的，"+user.followerCount()+"人");
    followerCount.setBackground(Ui.ripple(a,Design.PAPER,Design.BORDER,19));
    followerCount.setTextColor(Design.INK);
    LinearLayout.LayoutParams followerLp=new LinearLayout.LayoutParams(0,-2,1);followerLp.leftMargin=Ui.dp(a,10);
    stats.addView(followerCount,followerLp);
    identity.addView(stats);Ui.gap(identity,12);
    LinearLayout identityFoot=Ui.row(a);
    TextView motto=Ui.note(a,"认真吃饭，也认真生活");motto.setTextSize(17);
    identity.addView(motto);Ui.gap(identity,10);
    TextView edit=Ui.button(a,"编辑资料",false,() -> a.go("profile-edit",""));edit.setTextSize(13);edit.setMinHeight(Ui.dp(a,44));
    edit.setBackground(Ui.ripple(a,Design.PAPER,Design.RED,19));edit.setTextColor(Design.RED);identityFoot.addView(edit,new LinearLayout.LayoutParams(0,-2,1));
    TextView update=Ui.button(a,"检查更新",false,() -> a.checkAppRelease(true));
    update.setTextSize(13);update.setMinHeight(Ui.dp(a,44));
    update.setBackground(Ui.ripple(a,Design.PAPER,Design.BORDER,19));update.setTextColor(Design.INK);
    update.setContentDescription("检查应用更新，当前版本"+cn.edu.bjtu.foodtime.domain.model.ReleaseVersion.display(cn.edu.bjtu.foodtime.BuildConfig.VERSION_NAME));
    LinearLayout.LayoutParams updateLp=new LinearLayout.LayoutParams(0,-2,1);updateLp.leftMargin=Ui.dp(a,8);
    identityFoot.addView(update,updateLp);
    identity.addView(identityFoot);
    identity.addView(Ui.text(a,"当前版本 "+cn.edu.bjtu.foodtime.domain.model.ReleaseVersion.display(cn.edu.bjtu.foodtime.BuildConfig.VERSION_NAME),12,Design.MUTED));
    page.addView(identity,new LinearLayout.LayoutParams(-1,-2));Ui.gap(page,20);
    if("visitor".equals(user.role())) {
      LinearLayout guest=Ui.col(a);guest.setPadding(Ui.dp(a,18),Ui.dp(a,16),Ui.dp(a,18),Ui.dp(a,18));
      guest.setBackground(Ui.shape(a,Design.KRAFT_SOFT,Design.RED,22));
      guest.addView(Ui.title(a,"学校邮箱认证",20));Ui.gap(guest,6);
      guest.addView(Ui.text(a,"学校邮箱绑定为可选认证；当前可用功能以账号权限为准。",14,Design.INK));
      Ui.gap(guest,12);
      TextView unlock=Ui.button(a,"去绑定学校邮箱",true,() -> a.go("bind-school-email",""));
      unlock.setBackground(Ui.ripple(a,Design.RED,0,18));
      guest.addView(unlock,new LinearLayout.LayoutParams(-1,-2));
      page.addView(guest,new LinearLayout.LayoutParams(-1,-2));Ui.gap(page,20);
    }

    LinearLayout saved=section(a,page,"收藏与回访");
    entry(a,saved,"想吃清单","favorite-dishes","");
    entry(a,saved,"帖子收藏","community-favorites","");
    entry(a,saved,"浏览历史","history","");
    LinearLayout records=section(a,page,"我的记录");
    entry(a,records,"我的食记","community-mine","");
    entry(a,records,"发帖草稿","community-create","");
    entry(a,records,"菜品点评","mine-reviews","");
    entry(a,records,"餐厅与档口点评","venue-mine","");
    entry(a,records,"我的回复","mine-review-replies","1");
    entry(a,records,"我的提交","mine-submissions","");
    LinearLayout messages=section(a,page,"消息与处理");
    entry(a,messages,"消息信箱","inbox","");
    entry(a,messages,"建议与反馈","feedback","");
    entry(a,messages,"目录纠错","my-corrections","");
    entry(a,messages,"举报与处理决定","report-receipts","");
    LinearLayout account=section(a,page,"账号与隐私");
    entry(a,account,"修改密码","password","");
    entry(a,account,"黑名单","blocked-users","");
    entry(a,account,"提醒设置","notification-settings","");
    if(!user.isAdmin())entry(a,account,"账号安全与注销","account-lifecycle","");
    TextView cache=Ui.text(a,"图片缓存",14,Design.MUTED);account.addView(cache);
    TextView cacheError=Ui.text(a,"",12,Design.RED);account.addView(cacheError);
    long[] usage={0};
    a.read("image-cache-size",null,cacheError,()->usage[0]=a.repo.imageCacheBytes(),()->cache.setText(String.format(java.util.Locale.CHINA,"图片缓存 %.1f MiB",usage[0]/1048576.0)),()->{});
    roundedAction(a,account,"清理图片缓存",false,()->a.request(null,cacheError,a.repo::clearImageCache,()->{Ui.clearImages();Ui.resumeImages();cache.setText("图片缓存 0 MiB");a.message("图片缓存已清理");}));
    Ui.gap(page,12);roundedAction(a,page,"退出登录",false,()->Ui.confirm(a,"退出登录？","",a::logout));Ui.gap(page,24);
    LinearLayout doodles=Ui.row(a);doodles.setGravity(Gravity.CENTER);
    doodles.addView(Ui.foodDoodle(a,7,0x554f7250),new LinearLayout.LayoutParams(Ui.dp(a,74),Ui.dp(a,58)));
    doodles.addView(Ui.foodDoodle(a,8,0x55c9341e),new LinearLayout.LayoutParams(Ui.dp(a,74),Ui.dp(a,58)));page.addView(doodles);
  }

  private static void profileHeader(MainActivity a,LinearLayout page) {
    LinearLayout header=Ui.row(a);header.setGravity(Gravity.TOP);
    LinearLayout copy=Ui.col(a);copy.addView(Ui.title(a,"我的手账",30));Ui.gap(copy,8);
    copy.addView(Ui.note(a,"把喜欢的味道、写下的点评，都收进这一页。"));
    header.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
    FrameLayout inbox=new FrameLayout(a);inbox.setClickable(true);inbox.setFocusable(true);
    inbox.setTag("profile-inbox-button");
    inbox.setClipChildren(false);inbox.setClipToPadding(false);
    inbox.setBackground(Ui.ripple(a,Design.ALT,0,24));
    TextView bell=Ui.text(a,"🔔",23,Design.INK);bell.setGravity(Gravity.CENTER);
    inbox.addView(bell,new FrameLayout.LayoutParams(-1,-1));
    TextView badge=Ui.text(a,"",10,Design.ON_RED);badge.setGravity(Gravity.CENTER);badge.setTypeface(Design.body(),android.graphics.Typeface.BOLD);
    badge.setTag("profile-inbox-badge");
    badge.setBackground(Ui.shape(a,Design.RED,0,999));badge.setMinWidth(Ui.dp(a,20));badge.setMinHeight(Ui.dp(a,20));
    FrameLayout.LayoutParams badgeLp=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.END);badgeLp.topMargin=-Ui.dp(a,3);badgeLp.rightMargin=-Ui.dp(a,3);inbox.addView(badge,badgeLp);
    inbox.setOnClickListener(v->a.go("inbox",""));
    LinearLayout.LayoutParams inboxLp=new LinearLayout.LayoutParams(Ui.dp(a,48),Ui.dp(a,48));inboxLp.leftMargin=Ui.dp(a,12);header.addView(inbox,inboxLp);
    page.addView(header,new LinearLayout.LayoutParams(-1,-2));Ui.gap(page,24);
    updateUnreadBadge(a);
  }

  static void updateUnreadBadge(MainActivity activity) {
    if (activity.root == null) return;
    View inbox = activity.root.findViewWithTag("profile-inbox-button");
    TextView badge = activity.root.findViewWithTag("profile-inbox-badge");
    if (inbox == null || badge == null) return;
    long unread = activity.repo.unreadCount();
    inbox.setContentDescription(unread > 0 ? "通知信箱，" + unread + "条未读" : "通知信箱");
    badge.setVisibility(unread > 0 ? View.VISIBLE : View.GONE);
    badge.setText(unread > 99 ? "99+" : String.valueOf(unread));
  }

  private static LinearLayout section(MainActivity a,LinearLayout page,String title) {
    LinearLayout group=Ui.card(page);group.setTag("profile-group:"+title);
    TextView heading=Ui.title(a,title,21);group.addView(heading);Ui.gap(group,14);return group;
  }
  private static void entry(MainActivity a,LinearLayout group,String label,String kind,String id) {
    TextView button=Ui.button(a,label,false,()->a.go(kind,id));button.setTag("profile-entry:"+kind);
    button.setBackground(Ui.ripple(a,Design.PAPER,Design.BORDER,16));
    group.addView(button,new LinearLayout.LayoutParams(-1,-2));Ui.gap(group,10);
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
    TextView choose=Ui.button(a,"选择新头像",false,() -> a.pickAvatar(imageKey));
    choose.setBackground(Ui.ripple(a,Design.PAPER,Design.RED,18));choose.setTextColor(Design.RED);card.addView(choose);
    Ui.gap(card,8);card.addView(Ui.text(a,"JPEG / PNG，原图不超过10MB。选择后进行1:1方形裁剪。",13,Design.MUTED));
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
    Ui.action(page,"餐厅与档口点评及回复",false,()->a.go("venue-mine","~reviews~1"));
    Ui.gap(page,16);
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
