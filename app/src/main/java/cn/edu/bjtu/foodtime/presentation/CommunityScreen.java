package cn.edu.bjtu.foodtime.presentation;

import android.graphics.Color;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import cn.edu.bjtu.foodtime.domain.model.CommunityText;
import cn.edu.bjtu.foodtime.domain.model.Models.*;
import cn.edu.bjtu.foodtime.domain.validation.Validation;
import cn.edu.bjtu.foodtime.domain.validation.CommunityPermissions;
import java.util.*;

/** Native two-column campus food community, backed by the Backend P3 community contract. */
final class CommunityScreen {
  static void feed(MainActivity a,LinearLayout page,MainActivity.Route route) {
    a.heading(page,"02","校园食记 · 社区动态","随手拍、聊美味，看看同学们今天都在打卡什么好吃的。");

    LinearLayout toolbar=Ui.row(a);
    toolbar.setGravity(Gravity.CENTER_VERTICAL);
    LinearLayout sketches=Ui.row(a);
    sketches.addView(Ui.foodDoodle(a,5,Design.RED),new LinearLayout.LayoutParams(Ui.dp(a,48),Ui.dp(a,42)));
    sketches.addView(Ui.foodDoodle(a,6,Design.GREEN),new LinearLayout.LayoutParams(Ui.dp(a,48),Ui.dp(a,42)));
    toolbar.addView(sketches,new LinearLayout.LayoutParams(0,-2,1));
    TextView create=Ui.button(a,"＋ 我来记一笔",true,() -> a.go("community-create",""));
    toolbar.addView(create,new LinearLayout.LayoutParams(-2,-2));
    page.addView(toolbar,new LinearLayout.LayoutParams(-1,-2));
    Ui.gap(page,18);

    LinearLayout personal=Ui.row(a);personal.addView(Ui.button(a,"我的食记",false,()->a.go("community-mine","")));personal.addView(Ui.button(a,"我的收藏",false,()->a.go("community-favorites","")));
    TextView refresh=Ui.button(a,"↻ 刷新",false,()->a.refreshCurrent());
    refresh.setContentDescription("刷新社区食记");
    personal.addView(refresh);page.addView(personal);Ui.gap(page,12);
    List<CommunityPost> posts=a.repo.communityPosts();
    if(posts.isEmpty()) {
      if(a.isPageLoading()) Ui.empty(page,"正在加载校园食记…","马上就好。");
      else if(a.pageFailed()) {
        Ui.empty(page,"社区暂时无法加载","检查网络后重试。");
        Ui.action(page,"重新加载",true,a::render);
      } else {
        Ui.empty(page,"社区还没有公开食记","发一篇真实的校园美食记录，审核通过后就会出现在这里。");
        Ui.action(page,"写第一篇食记",true,() -> a.go("community-create",""));
      }
      return;
    }

    waterfall(a,page,posts);
    if(a.repo.hasMoreCommunityPosts()){
      TextView more=Ui.button(a,"加载更多食记",false,()->{});page.addView(more);
      more.setOnClickListener(v->a.read("community-more",v,null,a.repo::loadMoreCommunityPosts,a::redraw,()->{}));
    }
  }

  static void collection(MainActivity a,LinearLayout page,MainActivity.Route route){
    boolean favorites=route.kind().equals("community-favorites");var result=a.repo.communityCollection(favorites);
    a.heading(page,"02",favorites?"我的收藏":"我的食记",favorites?"收藏的公开食记都在这里。":"查看发布记录与审核进度；食记发布后不可编辑。");
    if(result.items().isEmpty())Ui.empty(page,favorites?"还没有收藏食记":"还没有发布食记",favorites?"在食记详情点击收藏，方便下次再找。":"发布一篇校园美食记录吧。");
    else waterfall(a,page,result.items());
    if(result.hasMore()){
      TextView more=Ui.button(a,"加载更多",false,()->{});page.addView(more);
      more.setOnClickListener(v->a.read("community-collection:"+favorites,v,null,()->a.repo.loadMoreCommunityCollection(favorites),a::redraw,()->{}));
    }
  }
  private static void waterfall(MainActivity a,LinearLayout page,List<CommunityPost> posts){
    LinearLayout waterfall=Ui.row(a);waterfall.setGravity(Gravity.TOP);
    LinearLayout left=Ui.col(a),right=Ui.col(a);
    LinearLayout.LayoutParams leftLp=new LinearLayout.LayoutParams(0,-2,1);leftLp.rightMargin=Ui.dp(a,5);
    LinearLayout.LayoutParams rightLp=new LinearLayout.LayoutParams(0,-2,1);rightLp.leftMargin=Ui.dp(a,5);
    waterfall.addView(left,leftLp);waterfall.addView(right,rightLp);
    double[] heights={0,0};
    for(CommunityPost post:posts) {
      int column=heights[0]<=heights[1]?0:1;
      postCard(a,column==0?left:right,post);
      heights[column]+=estimatedHeight(post);
    }
    page.addView(waterfall,new LinearLayout.LayoutParams(-1,-2));
  }

  private static double estimatedHeight(CommunityPost post) {
    double image=post.imageUrls().isEmpty()?.72:coverRatio(post);
    return image+Math.min(1.0,.34+post.content().length()/100.0);
  }

  private static void postCard(MainActivity a,LinearLayout column,CommunityPost post) {
    LinearLayout card=Ui.card(column);card.setPadding(0,0,0,Ui.dp(a,4));
    LinearLayout.LayoutParams cardLp=(LinearLayout.LayoutParams)card.getLayoutParams();cardLp.bottomMargin=Ui.dp(a,10);
    String cover=post.imageUrls().isEmpty()?"":post.imageUrls().get(0);
    FrameLayout media=(FrameLayout)Ui.communityImage(a,Ui.thumb(cover),post.title(),coverRatio(post));
    if(post.imageUrls().size()>1) {
      TextView badge=Ui.text(a,"▣ "+post.imageUrls().size(),11,Color.WHITE);
      badge.setGravity(Gravity.CENTER);badge.setPadding(Ui.dp(a,7),Ui.dp(a,3),Ui.dp(a,7),Ui.dp(a,3));
      badge.setBackground(Ui.shape(a,0xB3000000,0,12));
      FrameLayout.LayoutParams badgeLp=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.END);
      badgeLp.setMargins(0,Ui.dp(a,8),Ui.dp(a,8),0);media.addView(badge,badgeLp);
    }
    card.addView(media,new LinearLayout.LayoutParams(-1,-2));

    LinearLayout body=Ui.col(a);body.setPadding(Ui.dp(a,10),Ui.dp(a,10),Ui.dp(a,10),Ui.dp(a,9));
    TextView title=Ui.text(a,post.title(),15,Design.INK);
    title.setTypeface(Design.title());title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);
    body.addView(title,new LinearLayout.LayoutParams(-1,-2));Ui.gap(body,8);
    if(!post.status().equals("approved")){body.addView(Ui.text(a,post.status().equals("rejected")?"未通过："+post.reviewReason():post.reviewStage().equals("manual_pending")?"待人工审核":"AI审核中",12,Design.RED));Ui.gap(body,6);}
    LinearLayout meta=Ui.row(a);
    String author=post.userDisplayName().isBlank()?"校园干饭人":post.userDisplayName();
    TextView name=Ui.text(a,"@"+author,12,Design.MUTED);name.setSingleLine(true);name.setEllipsize(TextUtils.TruncateAt.END);
    meta.addView(name,new LinearLayout.LayoutParams(0,-2,1));
    meta.addView(Ui.text(a,"♡ "+post.likeCount(),12,Design.MUTED));body.addView(meta);
    card.addView(body,new LinearLayout.LayoutParams(-1,-2));
    card.setClickable(true);card.setFocusable(true);
    card.setContentDescription(post.title()+"，作者"+author+"，查看详情");
    card.setOnClickListener(v -> a.go("community-detail",post.id()));
  }

  static void detail(MainActivity a,LinearLayout page,MainActivity.Route route) {
    CommunityPost post=a.repo.communityPost(route.id());
    if(post==null) throw new IllegalArgumentException("动态不存在或暂不可见");
    if(post.status().equals("pending")) {
      TextView notice = Ui.text(a, "这篇食记正在审核，通过前仅自己可见。", 14, Design.RED);
      notice.setPadding(Ui.dp(a, 14), Ui.dp(a, 10), Ui.dp(a, 14), Ui.dp(a, 10));
      page.addView(notice);
    }
    CommunityGallery gallery = null;
    if (!post.imageUrls().isEmpty()) {
      gallery = new CommunityGallery(a, post.imageUrls(), post.images());
      gallery.setPost(post);
      page.addView(gallery);
    }
    LinearLayout copy = Ui.col(a);
    copy.setPadding(Ui.dp(a, 14), Ui.dp(a, 14), Ui.dp(a, 14), Ui.dp(a, 14));
    page.addView(copy);
    copy.addView(Ui.title(a, post.title(), 22));
    Ui.gap(copy, 6);
    copy.addView(Ui.text(a, post.content(), 16, Design.INK));
    if (post.linkedDish() != null) {
      var dish = post.linkedDish();
      Ui.gap(copy, 8);
      Ui.action(copy, "菜品 · " + dish.name(), false, () -> a.go("detail", dish.id()));
    }
    Ui.gap(copy, 8);
    copy.addView(Ui.text(a, format(post.createdAt()), 12, Design.MUTED));

    if(!post.repostOfId().isBlank()) {
      Ui.gap(copy,10);LinearLayout quoted=Ui.card(copy);
      quoted.addView(Ui.text(a,"转自 @"+(post.repostAuthor().isBlank()?"校园干饭人":post.repostAuthor()),13,Design.MUTED));
      Ui.gap(quoted,6);quoted.addView(Ui.text(a,post.repostContent(),14,Design.INK));
    }

    if(post.status().equals("rejected")){Ui.gap(copy,12);copy.addView(Ui.text(a,"未通过审核："+post.reviewReason(),14,Design.RED));}
    if(CommunityPermissions.canRemove(a.repo.currentUser(),post.userId()))
      Ui.action(copy,"删除这篇食记",false,()->CommunityRemoval.confirm(a,"posts",post.id(),post.userId(),post.title(),a::back));
    if (post.status().equals("approved")) {
      CommunityDiscussion discussion = new CommunityDiscussion(a, page, post);
      TextView postLike = discussion.show();
      if (gallery != null) gallery.setPostActions(new CommunityPhotoDialog.PostActions(
          () -> sharePost(a, post.id()), discussion::scrollToComments, postLike));
    }
  }

  private static void sharePost(MainActivity activity, String id) {
    android.content.Intent send = new android.content.Intent(android.content.Intent.ACTION_SEND);
    send.setType("text/plain");
    send.putExtra(android.content.Intent.EXTRA_TEXT,
        "https://bjtufoodtime.top/community/posts/" + android.net.Uri.encode(id));
    activity.startActivity(android.content.Intent.createChooser(send, "分享帖子"));
  }

  static void detailHeader(MainActivity a, LinearLayout header, CommunityPost post) {
    LinearLayout bar = Ui.row(a);
    bar.setGravity(Gravity.CENTER_VERTICAL);
    header.addView(bar);
    TextView back = CommunityDiscussion.action(a, "‹", a::back);
    back.setTextSize(27);
    back.setContentDescription("返回上一级");
    bar.addView(back);
    if (post == null) {
      bar.addView(Ui.title(a, "社区食记", 18), new LinearLayout.LayoutParams(0, -2, 1));
      return;
    }
    String name = post.userDisplayName().isBlank() ? "校园干饭人" : post.userDisplayName();
    bar.addView(CommunityDiscussion.avatar(a, post.userAvatarUrl(), name, 32));
    TextView author = Ui.text(a, name, 16, Design.INK);
    author.setPadding(Ui.dp(a, 8), 0, Ui.dp(a, 6), 0);
    author.setSingleLine();
    author.setEllipsize(TextUtils.TruncateAt.END);
    bar.addView(author, new LinearLayout.LayoutParams(0, -2, 1));
    User viewer = a.repo.currentUser();
    if (viewer == null || post.userId().isBlank() || post.userId().equals(viewer.id())) return;

    FollowState state = a.repo.followState(post.userId());
    TextView follow = CommunityDiscussion.action(a, "", () -> {});
    paintFollow(follow, state != null && state.followed());
    follow.setOnClickListener(v -> {
      boolean next = !follow.isSelected();
      FollowState[] result = {null};
      a.request(follow, null, () -> result[0] = a.repo.setFollow(post.userId(), next),
          () -> paintFollow(follow, result[0].followed()));
    });
    bar.addView(follow);
    TextView chat = CommunityDiscussion.action(a, "私信", () -> {});
    chat.setContentDescription("给作者发私信");
    chat.setOnClickListener(v -> {
      Conversation[] started = {null};
      a.read("start-chat:" + post.userId(), v, null,
          () -> started[0] = a.repo.startConversation(post.userId()),
          () -> { if (started[0] != null) a.go("direct-chat", started[0].id()); }, () -> {});
    });
    bar.addView(chat);
    if (post.status().equals("approved")) {
      bar.addView(CommunityDiscussion.action(a, "举报", () -> ContentSafety.report(a, "post", post.id())));
    }
  }

  private static void paintFollow(TextView button, boolean followed) {
    button.setSelected(followed);
    button.setText(followed ? "已关注" : "关注");
    button.setTextColor(followed ? Design.MUTED : Design.RED);
    button.setContentDescription(followed ? "已关注作者，点击取消" : "关注作者");
  }

  private static float coverRatio(CommunityPost post){
    if(!post.images().isEmpty()&&post.images().get(0).width()>0)return Math.max(.72f,Math.min(1.45f,(float)post.images().get(0).height()/post.images().get(0).width()));
    return 1f;
  }
  private static String photoKey(MainActivity a,int i){return a.draftKey("photo:"+i+":image");}
  private static List<String> selectedPhotos(MainActivity a){
    List<String> images=new ArrayList<>();for(int i=0;i<9;i++){String path=a.drafts.getString(photoKey(a,i),"");if(!path.isBlank())images.add(path);}return images;
  }
  private static void savePhotos(MainActivity a,List<String> images){for(int i=0;i<9;i++)a.drafts.putString(photoKey(a,i),i<images.size()?images.get(i):"");a.redraw();}
  static void create(MainActivity a,LinearLayout page,MainActivity.Route route) {
    a.heading(page,"02.2","写一页美食手账","分享真实的校园美食体验，审核通过后公开。");
    List<String> selected=selectedPhotos(a);
    LinearLayout photo=Ui.card(page);photo.addView(Ui.title(a,"01 · 美食实拍 "+selected.size()+"/9",18));Ui.gap(photo,10);
    for(int i=0;i<selected.size();i++){
      int index=i;String path=selected.get(i);LinearLayout item=Ui.col(a);
      item.addView(Ui.image(a,path,(i==0?"封面":"配图 "+(i+1)),true),new LinearLayout.LayoutParams(-1,-2));
      LinearLayout controls=Ui.row(a);
      if(i>0)controls.addView(Ui.button(a,"前移",false,()->{List<String> order=new ArrayList<>(selected);Collections.swap(order,index,index-1);savePhotos(a,order);}));
      controls.addView(Ui.button(a,"移除",false,()->{List<String> order=new ArrayList<>(selected);order.remove(index);a.discardPhoto(path);savePhotos(a,order);}));
      item.addView(controls);photo.addView(item);Ui.gap(photo,8);
    }
    if(selected.size()<9)Ui.action(photo,"选择照片（可多选）",false,a::pickCommunityPhotos);
    photo.addView(Ui.text(a,"1–9张图片，首张为封面；可前移调整顺序。",12,Design.MUTED));
    LinearLayout form=Ui.card(page);form.addView(Ui.title(a,"02 · 标题与心得",18));Ui.gap(form,12);
    EditText title=Ui.field(form,"食记标题",false,false);title.setHint("标题，最多50字");title.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(50)});
    EditText content=Ui.field(form,"动态正文",false,true);content.setHint("正文，最多2000字");content.setMinLines(5);content.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(2000)});
    String dishId = a.drafts.getString(a.draftKey("linkedDishId"), "");
    if (!dishId.isBlank()) {
      form.addView(Ui.text(a, "关联菜品 · " + a.drafts.getString(a.draftKey("linkedDishName"), ""), 15, Design.INK));
      Ui.action(form, "移除关联", false, () -> {
        a.drafts.remove(a.draftKey("linkedDishId"));
        a.drafts.remove(a.draftKey("linkedDishName"));
        a.redraw();
      });
    }
    Ui.action(form, dishId.isBlank() ? "选择关联菜品（可选）" : "更换关联菜品", false,
        () -> a.go("dish-pick", route.id()));
    TextView error=Ui.text(a,"",13,Design.RED);form.addView(error);MainActivity.Route origin=a.route();
    Ui.submit(form,"盖章发布",error,()->{
      String heading=Validation.text(title.getText().toString(),50,"标题"),body=Validation.text(content.getText().toString(),2000,"正文");
      List<String> photos=selectedPhotos(a);if(photos.isEmpty())throw new IllegalArgumentException("请添加至少1张图片");
      String fingerprint=heading+"\n"+body+"\n"+String.join("|",photos)
          +(dishId.isBlank()?"":"\n"+dishId),key=a.draftKey("requestId");
      if(!fingerprint.equals(a.drafts.getString(a.draftKey("payload"),""))){a.drafts.putString(key,UUID.randomUUID().toString());a.drafts.putString(a.draftKey("payload"),fingerprint);}
      String requestId=a.drafts.getString(key);
      return ()->a.repo.submitCommunityPost(heading,body,photos,requestId,dishId);
    },()->{a.clearDraft(origin);a.back();a.message("食记已提交审核，可在我的食记查看进度");});
  }

  private static String format(long value) {
    return CommunityText.publishedAt(value);
  }

  private CommunityScreen() {}
}
