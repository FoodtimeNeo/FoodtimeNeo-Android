package cn.edu.bjtu.foodtime.presentation;

import android.graphics.Color;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import cn.edu.bjtu.foodtime.domain.model.CommunityText;
import cn.edu.bjtu.foodtime.domain.model.Models.*;
import cn.edu.bjtu.foodtime.domain.validation.Validation;
import java.text.SimpleDateFormat;
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

    LinearLayout personal=Ui.row(a);personal.addView(Ui.button(a,"我的食记",false,()->a.go("community-mine","")));personal.addView(Ui.button(a,"我的收藏",false,()->a.go("community-favorites","")));page.addView(personal);Ui.gap(page,12);
    List<CommunityPost> posts=a.repo.communityPosts();
    if(posts.isEmpty()) {
      Ui.empty(page,"社区还没有公开食记","发一篇真实的校园美食记录，审核通过后就会出现在这里。");
      Ui.action(page,"写第一篇食记",true,() -> a.go("community-create",""));
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
    a.heading(page,"02.1","社区食记","");
    if(post.status().equals("pending")) {
      LinearLayout notice=Ui.card(page);notice.addView(Ui.text(a,"这篇食记正在审核，通过前仅自己可见。",14,Design.RED));
    }
    page.addView(new CommunityGallery(a,post.imageUrls()),new LinearLayout.LayoutParams(-1,-2));
    Ui.gap(page,12);
    LinearLayout author=Ui.row(a);
    String authorName=post.userDisplayName().isBlank()?"校园干饭人":post.userDisplayName();
    if(!post.userAvatarUrl().isBlank()){View avatar=Ui.image(a,post.userAvatarUrl(),"作者头像",false);avatar.setBackground(Ui.shape(a,Design.ALT,0,20));avatar.setClipToOutline(true);LinearLayout.LayoutParams avatarLp=new LinearLayout.LayoutParams(Ui.dp(a,36),Ui.dp(a,36));avatarLp.rightMargin=Ui.dp(a,8);author.addView(avatar,avatarLp);}
    author.addView(Ui.title(a,authorName,18),new LinearLayout.LayoutParams(0,-2,1));
    author.addView(Ui.text(a,format(post.createdAt()),12,Design.MUTED));page.addView(author);
    Ui.gap(page,16);
    page.addView(Ui.title(a,post.title(),24));Ui.gap(page,10);
    page.addView(Ui.text(a,post.content(),16,Design.INK));

    if(!post.repostOfId().isBlank()) {
      Ui.gap(page,14);LinearLayout quoted=Ui.card(page);
      quoted.addView(Ui.text(a,"转自 @"+(post.repostAuthor().isBlank()?"校园干饭人":post.repostAuthor()),13,Design.MUTED));
      Ui.gap(quoted,6);quoted.addView(Ui.text(a,post.repostContent(),14,Design.INK));
    }

    if(post.status().equals("rejected")){Ui.gap(page,12);page.addView(Ui.text(a,"未通过审核："+post.reviewReason(),14,Design.RED));}
    if(a.repo.currentUser()!=null&&post.userId().equals(a.repo.currentUser().id()))Ui.action(page,"删除这篇食记",false,()->Ui.confirm(a,"删除食记？","删除后无法恢复，帖子将从社区隐藏。",()->a.request(null,null,()->a.repo.deleteCommunityPost(post.id()),a::back)));
    if(!post.status().equals("approved"))return;
    Ui.gap(page,18);LinearLayout actions=Ui.row(a);
    TextView like=Ui.button(a,(post.likedByMe()?"♥ 已赞 ":"♡ 点赞 ")+post.likeCount(),false,() -> {});
    like.setOnClickListener(v -> a.request(v,null,() -> a.repo.setCommunityLike("post",post.id(),!post.likedByMe()),a::redraw));
    TextView favorite=Ui.button(a,(post.favoritedByMe()?"★ 已收藏 ":"☆ 收藏 ")+post.favoriteCount(),false,() -> {});
    favorite.setOnClickListener(v -> a.request(v,null,() -> a.repo.setCommunityFavorite(post.id(),!post.favoritedByMe()),a::redraw));
    LinearLayout.LayoutParams actionLp=new LinearLayout.LayoutParams(0,-2,1);actionLp.rightMargin=Ui.dp(a,6);
    actions.addView(like,actionLp);LinearLayout.LayoutParams favoriteLp=new LinearLayout.LayoutParams(0,-2,1);favoriteLp.leftMargin=Ui.dp(a,6);
    actions.addView(favorite,favoriteLp);page.addView(actions);

    if(!post.status().equals("approved"))return;
    Ui.gap(page,28);LinearLayout threadList=Ui.card(page);threadList.addView(Ui.title(a,"同学们的回应 · "+post.commentCount(),22));Ui.gap(threadList,14);
    var roots=a.repo.communityRoots(post.id());
    if(roots.items().isEmpty())Ui.empty(threadList,"还没有评论","分享真实、有用的美食体验；审核后公开。");
    for(CommunityComment root:roots.items()){
      comment(a,threadList,root,root);
      var replies=a.repo.communityReplies(root.id());
      if(!replies.items().isEmpty()){
        LinearLayout floor=Ui.col(a);floor.setPadding(Ui.dp(a,10),0,Ui.dp(a,10),0);floor.setBackground(Ui.shape(a,Design.ALT,0,8));
        LinearLayout.LayoutParams floorLp=new LinearLayout.LayoutParams(-1,-2);floorLp.leftMargin=Ui.dp(a,42);threadList.addView(floor,floorLp);
        for(CommunityComment reply:replies.items())comment(a,floor,reply,root);
      }
      if(replies.hasMore()){
        TextView more=Ui.button(a,"展开更多回复",false,()->{});threadList.addView(more);
        more.setOnClickListener(v->a.read("community-replies:"+root.id(),v,null,()->a.repo.loadCommunityReplies(post.id(),root.id(),true),a::redraw,()->{}));
      }
      Ui.gap(threadList,12);
    }
    if(roots.hasMore()){
      TextView more=Ui.button(a,"加载更多评论",false,()->{});threadList.addView(more);
      more.setOnClickListener(v->a.read("community-roots:"+post.id(),v,null,()->a.repo.loadMoreCommunityComments(post.id()),a::redraw,()->{}));
    }
    LinearLayout composer=Ui.card(page);
    String rootId=a.drafts.getString(a.draftKey("replyRoot"),""),targetId=a.drafts.getString(a.draftKey("replyTarget"),"");
    if(!rootId.isBlank()){
      composer.addView(Ui.text(a,"回复 "+a.drafts.getString(a.draftKey("replyName"),"同学"),14,Design.RED));
      Ui.action(composer,"取消回复",false,()->{a.drafts.remove(a.draftKey("replyRoot"));a.drafts.remove(a.draftKey("replyTarget"));a.redraw();});
    }
    EditText input=Ui.field(composer,"评论内容",false,true);input.setHint("分享美食体验（最多500字）");
    input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(500)});
    TextView error=Ui.text(a,"",13,Design.RED);composer.addView(error);
    Ui.submit(composer,rootId.isBlank()?"提交评论":"提交回复",error,()->{
      String body=Validation.text(input.getText().toString(),500,"评论"),key=a.draftKey("commentRequestId");
      String fingerprint=rootId+"|"+targetId+"|"+body;
      if(!fingerprint.equals(a.drafts.getString(a.draftKey("commentPayload"),""))){a.drafts.putString(key,UUID.randomUUID().toString());a.drafts.putString(a.draftKey("commentPayload"),fingerprint);}
      String requestId=a.drafts.getString(key);
      return ()->a.repo.submitCommunityComment(post.id(),body,rootId,targetId,requestId);
    },()->{for(String key:List.of("评论内容","replyRoot","replyTarget","replyName","commentPayload","commentRequestId"))a.drafts.remove(a.draftKey(key));a.message("已提交审核，通过前仅自己可见");a.render();});
  }

  private static void comment(MainActivity a,LinearLayout page,CommunityComment item,CommunityComment root){
    LinearLayout line=Ui.row(a);line.setGravity(Gravity.TOP);LinearLayout avatar=Ui.col(a);
    if(!item.userAvatarUrl().isBlank()&&!item.deleted())avatar.addView(Ui.image(a,item.userAvatarUrl(),"头像",false),new LinearLayout.LayoutParams(Ui.dp(a,32),Ui.dp(a,32)));
    else{TextView initial=Ui.text(a,item.deleted()?"·":item.userDisplayName().isBlank()?"同":item.userDisplayName().substring(0,1),13,Design.MUTED);initial.setGravity(Gravity.CENTER);initial.setBackground(Ui.shape(a,Design.ALT,0,16));avatar.addView(initial,new LinearLayout.LayoutParams(Ui.dp(a,32),Ui.dp(a,32)));}
    LinearLayout.LayoutParams avatarLp=new LinearLayout.LayoutParams(Ui.dp(a,32),-2);avatarLp.rightMargin=Ui.dp(a,10);line.addView(avatar,avatarLp);
    LinearLayout body=Ui.col(a);line.addView(body,new LinearLayout.LayoutParams(0,-2,1));page.addView(line);Ui.gap(page,10);
    body.addView(Ui.text(a,item.deleted()?"原评论已删除":item.userDisplayName(),13,Design.MUTED));Ui.gap(body,4);
    String prefix=item.rootCommentId().isBlank()?"":"回复 "+item.replyToDisplayName()+"：";
    body.addView(Ui.text(a,item.deleted()?"这条评论已删除，楼内回复仍可查看。":prefix+item.content(),15,item.deleted()?Design.MUTED:Design.INK));
    body.addView(Ui.text(a,format(item.createdAt())+(item.status().equals("pending")?" · 审核中，仅自己可见":item.status().equals("rejected")?" · 未通过："+item.reviewReason():""),11,Design.MUTED));
    LinearLayout actions=Ui.row(a);body.addView(actions);
    if(!item.deleted()&&item.status().equals("approved")){
      TextView like=Ui.button(a,(item.likedByMe()?"♥ ":"♡ ")+item.likeCount(),false,()->{});actions.addView(like);
      like.setContentDescription((item.likedByMe()?"取消点赞":"点赞")+"，当前"+item.likeCount()+"赞");
      like.setOnClickListener(v->a.request(v,null,()->a.repo.setCommunityLike("comment",item.id(),!item.likedByMe()),a::redraw));
    }
    if(!item.deleted()&&item.status().equals("approved")&&!root.deleted())actions.addView(Ui.button(a,"回复",false,()->{
      a.drafts.putString(a.draftKey("replyRoot"),root.id());a.drafts.putString(a.draftKey("replyTarget"),item.id());a.drafts.putString(a.draftKey("replyName"),item.userDisplayName());a.redraw();
    }));
    if(!item.deleted()&&a.repo.currentUser()!=null&&item.userId().equals(a.repo.currentUser().id())){
      TextView remove=Ui.button(a,"删除",false,()->{});actions.addView(remove);
      remove.setOnClickListener(v->new android.app.AlertDialog.Builder(a).setTitle("删除评论").setMessage("删除后无法恢复，已通过的楼内回复会保留。").setNegativeButton("取消",null).setPositiveButton("删除",(dialog,which)->a.request(v,null,()->a.repo.deleteCommunityComment(item.id()),a::render)).show());
    }
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
    TextView error=Ui.text(a,"",13,Design.RED);form.addView(error);MainActivity.Route origin=a.route();
    Ui.submit(form,"盖章发布",error,()->{
      String heading=Validation.text(title.getText().toString(),50,"标题"),body=Validation.text(content.getText().toString(),2000,"正文");
      List<String> photos=selectedPhotos(a);if(photos.isEmpty())throw new IllegalArgumentException("请添加至少1张图片");
      String fingerprint=heading+"\n"+body+"\n"+String.join("|",photos),key=a.draftKey("requestId");
      if(!fingerprint.equals(a.drafts.getString(a.draftKey("payload"),""))){a.drafts.putString(key,UUID.randomUUID().toString());a.drafts.putString(a.draftKey("payload"),fingerprint);}
      String requestId=a.drafts.getString(key);
      return ()->a.repo.submitCommunityPost(heading,body,photos,requestId);
    },()->{a.clearDraft(origin);a.back();a.message("食记已提交审核，可在我的食记查看进度");});
  }

  private static String format(long value) {
    if(value<=0) return "";
    return new SimpleDateFormat("MM月dd日 HH:mm",Locale.CHINA).format(new Date(value));
  }

  private CommunityScreen() {}
}
