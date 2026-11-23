package cn.edu.bjtu.foodtime.presentation;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.widget.*;
import cn.edu.bjtu.foodtime.domain.model.Models.*;
import java.util.*;

final class ProfileScreen {
  static void profile(MainActivity a, LinearLayout page, MainActivity.Route route) {
    User user=a.repo.currentUser();
    page.setBackgroundColor(Design.PAPER);
    LinearLayout top=Ui.row(a);
    top.addView(Ui.title(a,"我的",26),new LinearLayout.LayoutParams(0,-2,1));
    FrameLayout inbox=new FrameLayout(a);inbox.setTag("profile-inbox-button");
    View bell=CommunityScreen.iconButton(a,cn.edu.bjtu.foodtime.R.drawable.ic_hub_inbox,"消息信箱",()->a.go("inbox",""));
    inbox.addView(bell,new FrameLayout.LayoutParams(-1,-1));
    TextView badge=Ui.text(a,"",10,Design.ON_RED);badge.setTag("profile-inbox-badge");badge.setGravity(Gravity.CENTER);
    badge.setBackground(Ui.shape(a,Design.RED,0,999));badge.setMinWidth(Ui.dp(a,18));
    inbox.addView(badge,new FrameLayout.LayoutParams(-2,Ui.dp(a,18),Gravity.TOP|Gravity.END));
    top.addView(inbox,new LinearLayout.LayoutParams(Ui.dp(a,44),Ui.dp(a,44)));page.addView(top);
    Ui.gap(page,20);
    LinearLayout person=Ui.row(a);person.setGravity(Gravity.CENTER_VERTICAL);
    View avatar=Ui.image(a,Ui.thumb(user.avatarUrl()),user.displayName().isEmpty()?"我":user.displayName().substring(0,1),false);
    avatar.setBackground(Ui.shape(a,Design.ALT,Design.BORDER,999));avatar.setClipToOutline(true);avatar.setContentDescription("个人头像");
    person.addView(avatar,new LinearLayout.LayoutParams(Ui.dp(a,72),Ui.dp(a,72)));
    LinearLayout copy=Ui.col(a);LinearLayout.LayoutParams copyLp=new LinearLayout.LayoutParams(0,-2,1);copyLp.leftMargin=Ui.dp(a,14);
    copy.addView(Ui.title(a,user.displayName(),24));Ui.gap(copy,5);
    String role=switch(user.role()){case "superadmin"->"超级管理员";case "admin"->"管理员";case "user"->"认证用户";default->"用户";};
    copy.addView(Ui.text(a,role,12,Design.RED));Ui.gap(copy,6);copy.addView(Ui.note(a,"认真吃饭，也认真生活"));
    person.addView(copy,copyLp);person.addView(CommunityScreen.iconButton(a,cn.edu.bjtu.foodtime.R.drawable.ic_hub_edit,"编辑资料",()->a.go("profile-edit","")),new LinearLayout.LayoutParams(Ui.dp(a,44),Ui.dp(a,44)));
    page.addView(person);Ui.gap(page,20);
    LinearLayout stats=Ui.row(a);
    stat(a,stats,"关注",user.followingCount(),"following","profile-following-count");
    stat(a,stats,"粉丝",user.followerCount(),"followers","profile-follower-count");
    page.addView(stats);Ui.gap(page,18);
    grid(a,page,"常用",new String[][]{{"想吃清单","favorite-dishes","dish"},{"帖子收藏","community-favorites","star"},{"浏览历史","history","clock"},{"发帖草稿","community-create","edit"}});
    grid(a,page,"我的记录",new String[][]{{"我的食记","community-mine","note"},{"菜品点评","mine-reviews","comment"},{"餐厅点评","venue-mine","dish"},{"我的回复","mine-review-replies","reply"},{"我的提交","mine-submissions","upload"}});
    grid(a,page,"服务",new String[][]{{"消息信箱","inbox","inbox"},{"建议与反馈","feedback","feedback"},{"我的纠错","my-corrections","correct"},{"举报与申诉","report-receipts","shield"}});
    LinearLayout account=section(a,page,"账号与隐私");
    listEntry(a,account,"黑名单",icon("block"),()->a.go("blocked-users",""));
    listEntry(a,account,"提醒设置",icon("inbox"),()->a.go("notification-settings",""));
    listEntry(a,account,"修改密码",icon("lock"),()->a.go("password",""));
    if(!user.isAdmin()){
      listEntry(a,account,"账号安全",icon("shield"),()->a.go("account-lifecycle",""));
      if("visitor".equals(user.role()))listEntry(a,account,"学校邮箱认证",icon("mail"),()->a.go("bind-school-email",""));
    }
    listEntry(a,account,"账号信息",icon("note"),()->new android.app.AlertDialog.Builder(a).setTitle("账号信息").setMessage(user.email()+"\nID："+user.id()).setPositiveButton("关闭",null).show());
    listEntry(a,account,"检查更新",icon("upload"),()->a.checkAppRelease(true));
    TextView cacheError=Ui.text(a,"",12,Design.RED);account.addView(cacheError);
    long[] usage={0};TextView cache=listEntry(a,account,"清理图片缓存",icon("clock"),()->a.request(null,cacheError,a.repo::clearImageCache,()->{Ui.clearImages();Ui.resumeImages();a.message("图片缓存已清理");}));
    a.read("image-cache-size",null,cacheError,()->usage[0]=a.repo.imageCacheBytes(),()->cache.setText(String.format(java.util.Locale.CHINA,"清理图片缓存 · %.1f MiB",usage[0]/1048576.0)),()->{});
    Ui.gap(page,12);roundedAction(a,page,"退出登录",false,()->Ui.confirm(a,"退出登录？","",a::logout));
    TextView version=Ui.text(a,"v"+cn.edu.bjtu.foodtime.domain.model.ReleaseVersion.display(cn.edu.bjtu.foodtime.BuildConfig.VERSION_NAME),12,Design.MUTED);version.setGravity(Gravity.CENTER);Ui.gap(page,16);page.addView(version);
    updateUnreadBadge(a);
  }
  private static void stat(MainActivity a,LinearLayout row,String label,long count,String route,String tag){
    TextView button=Ui.button(a,count+"\n"+label,false,()->a.go(route,""));button.setTag(tag);button.setTextSize(18);button.setBackground(Ui.ripple(a,Design.PAPER,0,10));button.setContentDescription(label+"，"+count+"人");
    row.addView(button,new LinearLayout.LayoutParams(0,-2,1));
  }
  private static int icon(String name){return switch(name){
    case "dish"->cn.edu.bjtu.foodtime.R.drawable.ic_hub_dish;case "star"->cn.edu.bjtu.foodtime.R.drawable.ic_hub_star;
    case "clock"->cn.edu.bjtu.foodtime.R.drawable.ic_hub_clock;case "edit"->cn.edu.bjtu.foodtime.R.drawable.ic_hub_edit;
    case "comment"->cn.edu.bjtu.foodtime.R.drawable.ic_hub_comment;case "reply"->cn.edu.bjtu.foodtime.R.drawable.ic_hub_reply;
    case "upload"->cn.edu.bjtu.foodtime.R.drawable.ic_hub_upload;case "inbox"->cn.edu.bjtu.foodtime.R.drawable.ic_hub_inbox;
    case "feedback"->cn.edu.bjtu.foodtime.R.drawable.ic_hub_feedback;case "correct"->cn.edu.bjtu.foodtime.R.drawable.ic_hub_correct;
    case "shield"->cn.edu.bjtu.foodtime.R.drawable.ic_hub_shield;case "lock"->cn.edu.bjtu.foodtime.R.drawable.ic_hub_lock;
    case "block"->cn.edu.bjtu.foodtime.R.drawable.ic_hub_block;case "mail"->cn.edu.bjtu.foodtime.R.drawable.ic_hub_mail;
    default->cn.edu.bjtu.foodtime.R.drawable.ic_hub_note;};}
  private static LinearLayout section(MainActivity a,LinearLayout page,String title){
    LinearLayout group=Ui.col(a);group.setTag("profile-group:"+title);group.setPadding(0,Ui.dp(a,18),0,Ui.dp(a,8));
    View line=new View(a);line.setBackgroundColor(Design.BORDER);group.addView(line,new LinearLayout.LayoutParams(-1,Ui.dp(a,1)));Ui.gap(group,16);group.addView(Ui.title(a,title,20));Ui.gap(group,12);page.addView(group);return group;
  }
  private static void grid(MainActivity a,LinearLayout page,String title,String[][] entries){
    LinearLayout group=section(a,page,title);int columns=a.getResources().getConfiguration().screenWidthDp<360||a.getResources().getConfiguration().fontScale>1.3f?3:4;
    LinearLayout row=null;
    for(int i=0;i<entries.length;i++){
      if(i%columns==0){row=Ui.row(a);row.setGravity(Gravity.TOP);group.addView(row);}
      String[] e=entries[i];LinearLayout cell=Ui.col(a);cell.setGravity(Gravity.CENTER_HORIZONTAL);cell.setPadding(Ui.dp(a,3),Ui.dp(a,12),Ui.dp(a,3),Ui.dp(a,12));cell.setBackground(Ui.ripple(a,Design.PAPER,0,12));
      ImageView glyph=new ImageView(a);glyph.setImageResource(icon(e[2]));glyph.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);cell.addView(glyph,new LinearLayout.LayoutParams(Ui.dp(a,28),Ui.dp(a,28)));Ui.gap(cell,10);
      TextView label=Ui.text(a,e[0],13,Design.INK);label.setGravity(Gravity.CENTER);label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);cell.addView(label,new LinearLayout.LayoutParams(-1,-2));
      cell.setFocusable(true);cell.setClickable(true);cell.setContentDescription(e[0]);cell.setTag("profile-entry:"+e[1]);cell.setOnClickListener(v->a.go(e[1],e[1].equals("mine-review-replies")?"1":""));row.addView(cell,new LinearLayout.LayoutParams(0,-2,1));
    }
    if(row!=null)for(int i=entries.length%columns;i>0&&i<columns;i++)row.addView(new View(a),new LinearLayout.LayoutParams(0,1,1));
  }
  private static TextView listEntry(MainActivity a,LinearLayout group,String label,int icon,Runnable action){
    LinearLayout row=Ui.row(a);row.setPadding(Ui.dp(a,8),Ui.dp(a,10),Ui.dp(a,8),Ui.dp(a,10));row.setMinimumHeight(Ui.dp(a,52));row.setBackground(Ui.ripple(a,Design.PAPER,0,10));row.setClickable(true);row.setFocusable(true);row.setContentDescription(label);row.setOnClickListener(v->action.run());
    ImageView glyph=new ImageView(a);glyph.setImageResource(icon);row.addView(glyph,new LinearLayout.LayoutParams(Ui.dp(a,22),Ui.dp(a,22)));
    TextView text=Ui.text(a,label,15,Design.INK);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.leftMargin=Ui.dp(a,14);row.addView(text,lp);row.addView(Ui.text(a,"›",22,Design.MUTED));group.addView(row);return text;
  }
  private static void roundedAction(MainActivity a,LinearLayout panel,String label,boolean primary,Runnable run){TextView button=Ui.button(a,label,primary,run);button.setBackground(Ui.ripple(a,Design.PAPER,Design.BORDER,18));panel.addView(button,new LinearLayout.LayoutParams(-1,-2));}

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
