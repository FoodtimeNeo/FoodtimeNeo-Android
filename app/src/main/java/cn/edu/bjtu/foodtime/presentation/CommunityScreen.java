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

    List<CommunityPost> posts=a.repo.communityPosts();
    if(posts.isEmpty()) {
      Ui.empty(page,"社区还没有公开食记","发一篇真实的校园美食记录，审核通过后就会出现在这里。");
      Ui.action(page,"写第一篇食记",true,() -> a.go("community-create",""));
      return;
    }

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
    if(a.repo.hasMoreCommunityPosts()){
      TextView more=Ui.button(a,"加载更多食记",false,()->{});page.addView(more);
      more.setOnClickListener(v->a.read("community-more",v,null,a.repo::loadMoreCommunityPosts,a::redraw,()->{}));
    }
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
    if(post.imageUrls().isEmpty()) {
      page.addView(Ui.communityImage(a,"","这篇食记没有配图",.72f),new LinearLayout.LayoutParams(-1,-2));
    } else {
      for(int i=0;i<post.imageUrls().size();i++) {
        String image=post.imageUrls().get(i);
        page.addView(Ui.communityImage(a,image,"食记图片 "+(i+1)+" / "+post.imageUrls().size(),
            i==0?coverRatio(post):1f),new LinearLayout.LayoutParams(-1,-2));
        Ui.gap(page,8);
      }
    }
    Ui.gap(page,12);
    LinearLayout author=Ui.row(a);
    String authorName=post.userDisplayName().isBlank()?"校园干饭人":post.userDisplayName();
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

    Ui.gap(page,18);LinearLayout actions=Ui.row(a);
    TextView like=Ui.button(a,(post.likedByMe()?"♥ 已赞 ":"♡ 点赞 ")+post.likeCount(),false,() -> {});
    like.setOnClickListener(v -> a.request(v,null,() -> a.repo.toggleCommunityLike("post",post.id()),a::render));
    TextView favorite=Ui.button(a,post.favoritedByMe()?"★ 已收藏":"☆ 收藏",false,() -> {});
    favorite.setOnClickListener(v -> a.request(v,null,() -> a.repo.toggleCommunityFavorite(post.id()),a::render));
    LinearLayout.LayoutParams actionLp=new LinearLayout.LayoutParams(0,-2,1);actionLp.rightMargin=Ui.dp(a,6);
    actions.addView(like,actionLp);LinearLayout.LayoutParams favoriteLp=new LinearLayout.LayoutParams(0,-2,1);favoriteLp.leftMargin=Ui.dp(a,6);
    actions.addView(favorite,favoriteLp);page.addView(actions);

    Ui.gap(page,28);page.addView(Ui.title(a,"同学们的回应 · "+post.commentCount(),22));Ui.gap(page,14);
    if(a.repo.communityComments(post.id()).isEmpty()) Ui.empty(page,"还没有公开评论","来聊聊口感、排队时间或你的推荐吃法吧。");
    else for(CommunityComment comment:a.repo.communityComments(post.id())) comment(a,page,comment);

    LinearLayout composer=Ui.card(page);EditText input=Ui.field(composer,"评论内容",false,true);
    input.setHint("说点真实、有用的校园美食体验（最多500字）");
    TextView error=Ui.text(a,"",13,Design.RED);composer.addView(error);
    String key=a.draftKey("评论内容");
    Ui.submit(composer,"提交评论",error,() -> {
      String content=Validation.text(input.getText().toString(),500,"评论");
      return () -> a.repo.submitCommunityComment(post.id(),content);
    },() -> {a.drafts.remove(key);a.message("评论已提交审核");a.render();});
  }

  private static void comment(MainActivity a,LinearLayout page,CommunityComment comment) {
    LinearLayout card=Ui.card(page);LinearLayout head=Ui.row(a);
    head.addView(Ui.title(a,comment.userDisplayName().isBlank()?"校园干饭人":comment.userDisplayName(),16),new LinearLayout.LayoutParams(0,-2,1));
    head.addView(Ui.text(a,format(comment.createdAt()),11,Design.MUTED));card.addView(head);Ui.gap(card,8);
    card.addView(Ui.text(a,comment.content(),15,Design.INK));
    if(comment.likeCount()>0) {Ui.gap(card,8);card.addView(Ui.text(a,"♡ "+comment.likeCount(),12,Design.MUTED));}
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
