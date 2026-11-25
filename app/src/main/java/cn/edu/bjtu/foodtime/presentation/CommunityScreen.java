package cn.edu.bjtu.foodtime.presentation;

import android.graphics.Color;
import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import cn.edu.bjtu.foodtime.R;
import cn.edu.bjtu.foodtime.domain.model.CommunityText;
import cn.edu.bjtu.foodtime.domain.model.CommunityShareLink;
import cn.edu.bjtu.foodtime.domain.model.Models.*;
import cn.edu.bjtu.foodtime.domain.validation.Validation;
import cn.edu.bjtu.foodtime.domain.validation.CommunityPermissions;
import java.util.*;

/** Native two-column campus food community, backed by the Backend P3 community contract. */
final class CommunityScreen {
  static ImageButton iconButton(MainActivity a, int icon, String label, Runnable action) {
    ImageButton button = new ImageButton(a);
    button.setImageResource(icon);
    button.setImageTintList(ColorStateList.valueOf(Design.INK));
    button.setBackground(Ui.ripple(a, Design.BAR, 0, 24));
    button.setContentDescription(label);
    button.setOnClickListener(view -> action.run());
    return button;
  }

  static View createButton(MainActivity a) {
    ImageButton button = iconButton(a, R.drawable.ic_community_add, "我来记一笔",
        () -> a.go("community-create", ""));
    button.setImageTintList(ColorStateList.valueOf(Design.ON_RED));
    button.setBackground(Ui.ripple(a, Design.RED, 0, 28));
    button.setElevation(Ui.dp(a, 5));
    return button;
  }

  static void feed(MainActivity a,LinearLayout page,MainActivity.Route route) {
    a.onContentRefresh(buildFeed(a, page, route.kind(), false));
  }

  /** Renders cached content immediately and returns the update for the committed page. */
  static Runnable buildFeed(MainActivity a, LinearLayout page, String section, boolean preview) {
    Banner banner = switch (section) {
      case "community-following" -> new Banner("关注", "看看关注的同学最近都在吃啥，顺便给下一顿找点灵感。");
      case "community-favorites" -> new Banner("收藏", "把喜欢的校园味道收在这里，馋了就翻一翻，下次再去吃。");
      case "community-mine" -> new Banner("我的", "把三餐里的小欢喜记下来，回头翻翻，都是自己的校园味道。");
      default -> new Banner("社区动态", "随手拍、聊美味，看看同学们今天都在打卡什么好吃的。");
    };
    page.addView(Ui.title(a, "校园食记 · " + banner.heading(), 26));
    Ui.gap(page, 4);
    TextView intro = Ui.note(a, banner.intro());
    intro.setTextSize(15);
    page.addView(intro);
    LinearLayout sketches = Ui.row(a);
    sketches.addView(Ui.foodDoodle(a, 5, Design.RED), new LinearLayout.LayoutParams(Ui.dp(a, 37), Ui.dp(a, 29)));
    sketches.addView(Ui.foodDoodle(a, 6, Design.GREEN), new LinearLayout.LayoutParams(Ui.dp(a, 32), Ui.dp(a, 29)));
    page.addView(sketches);
    Ui.gap(page, 8);
    if(section.equals("community-favorites")){
      FavoritePostsScreen favorites=new FavoritePostsScreen(a,page);
      if(!preview)page.post(()->{if(page.isAttachedToWindow())favorites.refresh();});
      return favorites::refresh;
    }
    LinearLayout body = Ui.col(a);
    page.addView(body);
    renderFeed(a, body, section, preview);
    return () -> renderFeed(a, body, section, false);
  }

  private record Banner(String heading, String intro) {}

  private record FeedBody(List<CommunityPost> posts, boolean hasMore, boolean loading, boolean failed, java.util.List<cn.edu.bjtu.foodtime.domain.model.CommunityPins.Entry> pins) {}

  private static void renderFeed(MainActivity a, LinearLayout body, String section, boolean preview) {
    boolean following = section.equals("community-following");
    boolean favorites = section.equals("community-favorites");
    boolean collection = favorites || section.equals("community-mine");
    List<CommunityPost> posts;
    boolean hasMore;
    Runnable loadMore;
    if (following) {
      var feed = a.repo.communityFollowing();
      posts = feed.items();
      hasMore = !feed.nextCursor().isEmpty();
      loadMore = a.repo::loadMoreCommunityFollowing;
    } else if (collection) {
      var feed = a.repo.communityCollection(favorites);
      posts = feed.items();
      hasMore = feed.hasMore();
      loadMore = () -> a.repo.loadMoreCommunityCollection(favorites);
    } else {
      posts = a.repo.communityPosts();
      hasMore = a.repo.hasMoreCommunityPosts();
      loadMore = a.repo::loadMoreCommunityPosts;
    }
    FeedBody next = new FeedBody(posts, hasMore, posts.isEmpty() && (preview || a.isPageLoading()),
        !preview && posts.isEmpty() && a.pageFailed(), section.equals("community") ? a.repo.communityPins().items() : List.of());
    if (next.equals(body.getTag())) return;
    body.setTag(next);
    body.removeAllViews();
    if(section.equals("community")){if(CommunityPinScreen.allowed(a))pinManagement(a,body,"");renderPins(a,body,next.pins());}
    if (next.loading()) {
      Ui.empty(body, "正在加载食记…", "");
    } else if (next.failed()) {
      Ui.empty(body, "暂时无法加载食记", "检查网络后再试。");
      Ui.action(body, "重新加载", false, a::refreshCurrent);
    } else if (posts.isEmpty()) {
      String title = switch (section) {
        case "community-following" -> "关注的人还没有公开食记";
        case "community-favorites" -> "还没有收藏食记";
        case "community-mine" -> "还没有发布食记";
        default -> "社区还没有公开食记";
      };
      if(!next.pins().isEmpty())title="暂时没有其他食记";
      String description = switch (section) {
        case "community-following" -> "可以在食记或用户搜索里关注感兴趣的同学。";
        case "community-favorites" -> "收藏的食记会出现在这里。";
        case "community-mine" -> "发布记录和审核进度会出现在这里。";
        default -> "审核通过的校园食记会出现在这里。";
      };
      body.addView(Ui.title(a, title, 17));
      Ui.gap(body, 5);
      body.addView(Ui.text(a, description, 14, Design.MUTED));
    } else {
      waterfall(a, body, posts);
      if (hasMore) {
        TextView more = Ui.button(a, "加载更多食记", false, () -> {});
        body.addView(more);
        more.setOnClickListener(view -> {
          if (!a.isPageLoading()) a.read("community-more:" + section, more, null, loadMore,
              () -> renderFeed(a, body, section, false), () -> {});
        });
      }
    }
  }

  private static void pinManagement(MainActivity a,LinearLayout body,String id){TextView button=Ui.button(a,"置顶管理",false,()->a.go("community-pins",id));button.setTag("community-pins-open");body.addView(button,new LinearLayout.LayoutParams(-1,-2));Ui.gap(body,8);}

  private static void renderPins(MainActivity a,LinearLayout body,List<cn.edu.bjtu.foodtime.domain.model.CommunityPins.Entry> pins){
    if(pins.isEmpty())return;
    LinearLayout card=Ui.col(a);card.setTag("community-pins");
    card.setPadding(Ui.dp(a,12),Ui.dp(a,10),Ui.dp(a,12),Ui.dp(a,8));
    card.setBackground(Ui.shape(a,Design.ALT,Design.BORDER,14));
    LinearLayout.LayoutParams cardLp=new LinearLayout.LayoutParams(-1,-2);cardLp.bottomMargin=Ui.dp(a,12);
    body.addView(card,cardLp);
    TextView heading=Ui.title(a,"置顶食记",14);heading.setTextColor(Design.MUTED);card.addView(heading);
    Ui.gap(card,4);
    for(int i=0;i<pins.size();i++){
      var pin=pins.get(i);
      if(i>0){View sep=new View(a);sep.setBackgroundColor(Design.BORDER);card.addView(sep,new LinearLayout.LayoutParams(-1,Ui.dp(a,1)));}
      LinearLayout row=Ui.row(a);row.setGravity(Gravity.CENTER_VERTICAL);
      row.setPadding(Ui.dp(a,2),Ui.dp(a,8),Ui.dp(a,2),Ui.dp(a,8));row.setMinimumHeight(Ui.dp(a,46));
      TextView badge=Ui.text(a,"置顶",11,Design.RED);
      badge.setTypeface(android.graphics.Typeface.create(Design.body(),android.graphics.Typeface.BOLD));
      badge.setPadding(Ui.dp(a,7),Ui.dp(a,2),Ui.dp(a,7),Ui.dp(a,2));
      badge.setBackground(Ui.shape(a,Design.RED_SOFT,0,8));
      LinearLayout.LayoutParams blp=new LinearLayout.LayoutParams(-2,-2);blp.rightMargin=Ui.dp(a,8);
      row.addView(badge,blp);
      LinearLayout copy=Ui.col(a);
      TextView title=Ui.text(a,pin.title(),14,Design.INK);title.setTypeface(android.graphics.Typeface.create(Design.body(),android.graphics.Typeface.BOLD));title.setMaxLines(1);title.setEllipsize(TextUtils.TruncateAt.END);copy.addView(title);
      copy.addView(Ui.text(a,"@"+pin.userDisplayName(),11,Design.MUTED));
      row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
      row.setBackground(Ui.ripple(a,Color.TRANSPARENT,0,8));row.setFocusable(true);row.setClickable(true);
      row.setTag("community-pinned-post:"+pin.id());
      row.setContentDescription("置顶食记："+pin.title()+"，作者"+pin.userDisplayName());
      row.setOnClickListener(v->a.go("community-detail",pin.id()));
      card.addView(row,new LinearLayout.LayoutParams(-1,-2));
    }
  }

  static void collection(MainActivity a,LinearLayout page,MainActivity.Route route){
    if (a.isCommunityHub()) { feed(a, page, route); return; }
    boolean favorites=route.kind().equals("community-favorites");
    a.heading(page,"02",favorites?"我的收藏":"我的食记",favorites?"收藏的公开食记都在这里。":"查看发布记录与审核进度；可在详情修改自己的食记。");
    if(favorites){FavoritePostsScreen screen=new FavoritePostsScreen(a,page);page.post(()->{if(page.isAttachedToWindow())screen.refresh();});return;}
    var result=a.repo.communityCollection(false);
    if(result.items().isEmpty())Ui.empty(page,favorites?"还没有收藏食记":"还没有发布食记",favorites?"在食记详情点击收藏，方便下次再找。":"发布一篇校园美食记录吧。");
    else waterfall(a,page,result.items());
    if(result.hasMore()){
      TextView more=Ui.button(a,"加载更多",false,()->{});page.addView(more);
      more.setOnClickListener(v->a.read("community-collection:"+favorites,v,null,()->a.repo.loadMoreCommunityCollection(favorites),a::redraw,()->{}));
    }
  }
  static void waterfall(MainActivity a,LinearLayout page,List<CommunityPost> posts){
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

  static void postCard(MainActivity a,LinearLayout column,CommunityPost post) {
    postCard(a,column,post,"");
  }
  static void postCard(MainActivity a,LinearLayout column,CommunityPost post,String keyword) {
    LinearLayout card=Ui.card(column);card.setTag("community-post-card:"+post.id());card.setPadding(0,0,0,Ui.dp(a,4));
    card.setBackground(Ui.ripple(a, Design.PAPER, Design.BORDER, 14));
    card.setElevation(Ui.dp(a, 1));
    LinearLayout.LayoutParams cardLp=(LinearLayout.LayoutParams)card.getLayoutParams();cardLp.bottomMargin=Ui.dp(a,10);
    String cover=post.imageUrls().isEmpty()?"":post.imageUrls().get(0);
    if(!cover.isBlank()){
      FrameLayout media=(FrameLayout)Ui.communityImage(a,Ui.thumb(cover),post.title(),coverRatio(post));
      if(post.imageUrls().size()>1) {
        TextView badge=Ui.text(a,"▣ "+post.imageUrls().size(),11,Color.WHITE);
        badge.setGravity(Gravity.CENTER);badge.setPadding(Ui.dp(a,7),Ui.dp(a,3),Ui.dp(a,7),Ui.dp(a,3));
        badge.setBackground(Ui.shape(a,0xB3000000,0,12));
        FrameLayout.LayoutParams badgeLp=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.END);
        badgeLp.setMargins(0,Ui.dp(a,8),Ui.dp(a,8),0);media.addView(badge,badgeLp);
      }
      card.addView(media,new LinearLayout.LayoutParams(-1,-2));
    }else{
      LinearLayout notePad=Ui.col(a);
      notePad.setBackgroundColor(Design.ALT);
      notePad.setPadding(Ui.dp(a,12),Ui.dp(a,14),Ui.dp(a,12),Ui.dp(a,14));
      notePad.setMinimumHeight(Ui.dp(a,96));
      String rawBody=post.content()==null?"":post.content().trim();
      if(!rawBody.isEmpty()){
        TextView quote=Ui.text(a,"“",20,Design.RED);quote.setLineSpacing(0,1);notePad.addView(quote);
        TextView excerpt=Ui.text(a,rawBody,12,Design.MUTED);excerpt.setMaxLines(4);excerpt.setEllipsize(TextUtils.TruncateAt.END);notePad.addView(excerpt);
      }else{
        notePad.setGravity(Gravity.CENTER);
        notePad.addView(Ui.foodDoodle(a,Math.abs(post.id().hashCode()),Design.MUTED),new LinearLayout.LayoutParams(Ui.dp(a,48),Ui.dp(a,40)));
      }
      card.addView(notePad,new LinearLayout.LayoutParams(-1,-2));
    }

    LinearLayout body=Ui.col(a);body.setPadding(Ui.dp(a,10),Ui.dp(a,10),Ui.dp(a,10),Ui.dp(a,9));
    TextView title=Ui.text(a,post.title(),14,Design.INK);
    title.setText(SearchText.highlight(post.title(),keyword));
    title.setTypeface(android.graphics.Typeface.create(Design.body(),android.graphics.Typeface.BOLD));title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);
    body.addView(title,new LinearLayout.LayoutParams(-1,-2));Ui.gap(body,7);
    if(!post.status().equals("approved")){body.addView(Ui.text(a,post.status().equals("rejected")?"未通过："+post.reviewReason():post.reviewStage().equals("manual_pending")?"待人工审核":"AI审核中",12,Design.RED));Ui.gap(body,6);}
    LinearLayout meta=Ui.row(a);
    meta.setGravity(Gravity.CENTER_VERTICAL);
    String author=post.userDisplayName().isBlank()?"校园干饭人":post.userDisplayName();
    TextView name=Ui.text(a,"@"+author,12,Design.MUTED);name.setSingleLine(true);name.setEllipsize(TextUtils.TruncateAt.END);
    meta.addView(name,new LinearLayout.LayoutParams(0,-2,1));
    int likeColor=post.likedByMe()?Design.RED:Design.MUTED;
    TextView like=Ui.text(a,CommunityDiscussion.compactCount(post.likeCount()),12,likeColor);
    like.setLineSpacing(0,1);
    like.setGravity(Gravity.CENTER_VERTICAL);
    like.setCompoundDrawablePadding(Ui.dp(a,4));
    CommunityDiscussion.setStartIcon(a,like,post.likedByMe()?R.drawable.ic_photo_liked:R.drawable.ic_photo_like,15,likeColor);
    LinearLayout.LayoutParams likeLp=new LinearLayout.LayoutParams(-2,-2);
    likeLp.leftMargin=Ui.dp(a,6);
    meta.addView(like,likeLp);body.addView(meta);
    card.addView(body,new LinearLayout.LayoutParams(-1,-2));
    card.setClickable(true);card.setFocusable(true);
    card.setContentDescription(post.title()+"，作者"+author+"，查看详情");
    card.setOnClickListener(v -> a.go("community-detail",post.id()));
  }

  static void detail(MainActivity a,LinearLayout page,MainActivity.Route route) {
    CommunityPost post=a.repo.communityPost(route.id());
    if(post==null) throw new IllegalArgumentException("动态不存在或暂不可见");
    if(post.status().equals("approved")) a.recordVisit("post", post.id());
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
    LinearLayout metadata=Ui.row(a); metadata.setGravity(Gravity.CENTER_VERTICAL);
    metadata.addView(Ui.text(a,format(post.createdAt()),12,Design.MUTED));
    android.view.View eye=new android.view.View(a) {
      private final android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
      @Override protected void onDraw(android.graphics.Canvas canvas) {
        super.onDraw(canvas); paint.setColor(Design.MUTED); paint.setStrokeWidth(Ui.dp(a,1)); paint.setStyle(android.graphics.Paint.Style.STROKE);
        canvas.drawOval(getWidth()*.08f,getHeight()*.25f,getWidth()*.92f,getHeight()*.75f,paint);
        canvas.drawCircle(getWidth()/2f,getHeight()/2f,getWidth()*.13f,paint);
      }
    };
    eye.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    LinearLayout.LayoutParams eyeLayout=new LinearLayout.LayoutParams(Ui.dp(a,16),Ui.dp(a,16));eyeLayout.leftMargin=Ui.dp(a,12); metadata.addView(eye,eyeLayout);
    String viewKey=a.draftKey("view-request"),recordedKey=a.draftKey("view-recorded"),countKey=a.draftKey("view-count");
    String requestId=a.drafts.getString(viewKey,"");
    if(requestId.isBlank()) { requestId=java.util.UUID.randomUUID().toString(); a.drafts.putString(viewKey,requestId); }
    TextView viewCount=Ui.text(a,viewLabel(Math.max(post.viewCount(),a.drafts.getLong(countKey))),12,Design.MUTED);
    LinearLayout.LayoutParams countLayout=new LinearLayout.LayoutParams(-2,-2); countLayout.leftMargin=Ui.dp(a,4);
    metadata.addView(viewCount,countLayout); copy.addView(metadata);
    if(post.editedAt()>0)copy.addView(Ui.text(a,"修改于 "+format(post.editedAt()),12,Design.MUTED));
    if(!a.drafts.getBoolean(recordedKey)) {
      String openedRequest=requestId; long[] count={post.viewCount()};
      a.recordPostView("post-view:"+openedRequest,
          () -> count[0]=a.repo.recordCommunityPostView(post.id(),openedRequest),() -> {
            a.drafts.putBoolean(recordedKey,true);a.drafts.putLong(countKey,count[0]);
            viewCount.setText(viewLabel(Math.max(post.viewCount(),count[0])));
          });
    }

    if(!post.repostOfId().isBlank()) {
      Ui.gap(copy,10);LinearLayout quoted=Ui.card(copy);
      quoted.addView(Ui.text(a,"转自 @"+(post.repostAuthor().isBlank()?"校园干饭人":post.repostAuthor()),13,Design.MUTED));
      Ui.gap(quoted,6);quoted.addView(Ui.text(a,post.repostContent(),14,Design.INK));
    }

    if(post.status().equals("rejected")){Ui.gap(copy,12);copy.addView(Ui.text(a,"未通过审核："+post.reviewReason(),14,Design.RED));}
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
    send.putExtra(android.content.Intent.EXTRA_TEXT, CommunityShareLink.postUrl(android.net.Uri.encode(id)));
    activity.startActivity(android.content.Intent.createChooser(send, "分享帖子"));
  }

  private static String viewLabel(long count) {
    return java.text.NumberFormat.getIntegerInstance(java.util.Locale.CHINA).format(count)+" 次浏览";
  }

  static void detailHeader(MainActivity a, LinearLayout header, CommunityPost post) {
    LinearLayout bar = Ui.row(a);
    bar.setGravity(Gravity.CENTER_VERTICAL);
    header.addView(bar, new LinearLayout.LayoutParams(-1, -2));
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
    if (!post.userId().isBlank()) {
      author.setFocusable(true);
      author.setContentDescription("查看 " + name + " 的作者主页");
      author.setOnClickListener(view -> a.go("community-author", post.userId()));
    }
    bar.addView(author, new LinearLayout.LayoutParams(0, -2, 1));
    User viewer = a.repo.currentUser();

    // 关注按钮（非作者本人时显示）
    if (viewer != null && !post.userId().isBlank() && !post.userId().equals(viewer.id())) {
      FollowState state = a.repo.followState(post.userId());
      TextView follow = CommunityDiscussion.action(a, "", () -> {});
      paintFollow(a, follow, state != null && state.followed());
      follow.setOnClickListener(v -> {
        boolean next = !follow.isSelected();
        FollowState[] result = {null};
        a.request(follow, null, () -> result[0] = a.repo.setFollow(post.userId(), next),
            () -> paintFollow(a, follow, result[0].followed()));
      });
      LinearLayout.LayoutParams followLp = new LinearLayout.LayoutParams(-2, -2);
      followLp.rightMargin = Ui.dp(a, 4);
      bar.addView(follow, followLp);
    }

    // 右上角更多操作按钮（紧凑三点矢量图标）
    ImageView more = CommunityDiscussion.moreIconButton(a, 44, 44, 20, Design.INK, () -> showPostMenu(a, post));
    more.setTag("post-more-actions");
    more.setContentDescription("食记更多操作");
    bar.addView(more, new LinearLayout.LayoutParams(Ui.dp(a, 44), Ui.dp(a, 44)));

    // 代理锚点放在标题栏之外，避免占用标题栏空间把“···”挤离最右侧。
    if (CommunityPinScreen.allowed(a)) {
      View pinProxy = new View(a);
      pinProxy.setTag("community-pins-open");
      pinProxy.setFocusable(true);
      pinProxy.setClickable(true);
      pinProxy.setContentDescription("置顶管理");
      pinProxy.setOnClickListener(v -> a.go("community-pins", post.id()));
      header.addView(pinProxy, new LinearLayout.LayoutParams(0, 0));
      pinProxy.setAlpha(0.01f);
    }
    if (PostEditScreen.allowed(a, post)) {
      View editProxy = new View(a);
      editProxy.setTag("post-edit-open");
      editProxy.setFocusable(true);
      editProxy.setClickable(true);
      editProxy.setContentDescription("修改食记");
      editProxy.setOnClickListener(v -> {
        a.requireFreshContent("community-edit", post.id());
        a.go("community-edit", post.id());
      });
      header.addView(editProxy, new LinearLayout.LayoutParams(0, 0));
      editProxy.setAlpha(0.01f);
    }
  }

  static void showPostMenu(MainActivity a, CommunityPost post) {
    User viewer = a.repo.currentUser();
    int sheetTheme = AppTheme.dark(a)
        ? android.R.style.Theme_Material_Dialog_NoActionBar
        : android.R.style.Theme_Material_Light_Dialog_NoActionBar;
    android.app.Dialog sheet = new android.app.Dialog(a, sheetTheme);
    sheet.setCanceledOnTouchOutside(true);
    android.view.Window window = sheet.getWindow();
    if (window != null) {
      window.setGravity(Gravity.BOTTOM);
      window.setLayout(-1, -2);
      window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
      window.setWindowAnimations(android.R.style.Animation_InputMethod);
    }
    LinearLayout panel = Ui.col(a);
    panel.setBackground(Ui.shape(a, Design.PAPER, Design.BORDER, 24));
    panel.setPadding(Ui.dp(a, 20), Ui.dp(a, 12), Ui.dp(a, 20), Ui.dp(a, 20));

    View handle = new View(a);
    handle.setBackground(Ui.shape(a, Design.BORDER, 0, 4));
    LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(Ui.dp(a, 36), Ui.dp(a, 4));
    handleLp.gravity = Gravity.CENTER_HORIZONTAL;
    handleLp.bottomMargin = Ui.dp(a, 14);
    panel.addView(handle, handleLp);

    TextView title = Ui.title(a, "食记操作", 17);
    title.setGravity(Gravity.CENTER);
    panel.addView(title);
    Ui.gap(panel, 14);

    // 0. 设置收藏分组
    if (post.status().equals("approved")) {
      addMenuItem(a, panel, "设置收藏分组", Design.INK, () -> {
        sheet.dismiss();
        CommunityDiscussion.openFavoriteGroups(a, post);
      });
    }

    // 1. 置顶管理
    if (CommunityPinScreen.allowed(a)) {
      addMenuItem(a, panel, "置顶管理", Design.INK, () -> {
        sheet.dismiss();
        a.go("community-pins", post.id());
      });
    }

    // 2. 修改食记
    if (PostEditScreen.allowed(a, post)) {
      addMenuItem(a, panel, "修改食记", Design.INK, () -> {
        sheet.dismiss();
        a.requireFreshContent("community-edit", post.id());
        a.go("community-edit", post.id());
      });
    }

    // 3. 私信作者
    if (viewer != null && !post.userId().isBlank() && !post.userId().equals(viewer.id())) {
      addMenuItem(a, panel, "私信作者", Design.INK, () -> {
        sheet.dismiss();
        Conversation[] started = {null};
        a.read("start-chat:" + post.userId(), null, null,
            () -> started[0] = a.repo.startConversation(post.userId()),
            () -> { if (started[0] != null) a.go("direct-chat", started[0].id()); }, () -> {});
      });
    }

    // 4. 举报食记
    if (viewer != null && !post.userId().isBlank() && !post.userId().equals(viewer.id()) && post.status().equals("approved")) {
      addMenuItem(a, panel, "举报这篇食记", Design.RED, () -> {
        sheet.dismiss();
        ContentSafety.report(a, "post", post.id());
      });
    }

    // 5. 分享食记
    addMenuItem(a, panel, "分享食记", Design.INK, () -> {
      sheet.dismiss();
      sharePost(a, post.id());
    });

    // 6. 删除这篇食记
    if (CommunityPermissions.canRemove(viewer, post.userId())) {
      addMenuItem(a, panel, "删除这篇食记", Design.RED, () -> {
        sheet.dismiss();
        CommunityRemoval.confirm(a, "posts", post.id(), post.userId(), post.title(), a::back);
      });
    }

    Ui.gap(panel, 8);
    TextView cancel = Ui.button(a, "取消", false, sheet::dismiss);
    cancel.setBackground(Ui.ripple(a, Design.ALT, 0, 16));
    panel.addView(cancel, new LinearLayout.LayoutParams(-1, -2));

    sheet.setContentView(panel);
    sheet.show();
    // Dialog windows apply their theme dimensions during show(). Set the
    // width afterwards so the action sheet fills the available screen width
    // instead of being measured to the longest child.
    android.view.Window shownWindow = sheet.getWindow();
    if (shownWindow != null) {
      shownWindow.setGravity(Gravity.BOTTOM);
      shownWindow.setLayout(-1, -2);
    }
  }

  private static void addMenuItem(MainActivity a, LinearLayout parent, String label, int textColor, Runnable action) {
    TextView item = Ui.text(a, label, 16, textColor);
    item.setGravity(Gravity.CENTER);
    item.setMinimumHeight(Ui.dp(a, 48));
    item.setPadding(Ui.dp(a, 16), Ui.dp(a, 12), Ui.dp(a, 16), Ui.dp(a, 12));
    item.setBackground(Ui.ripple(a, Design.PAPER, 0, 14));
    item.setFocusable(true);
    item.setClickable(true);
    item.setOnClickListener(v -> action.run());
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.bottomMargin = Ui.dp(a, 6);
    parent.addView(item, lp);
  }

  private static void paintFollow(MainActivity a, TextView button, boolean followed) {
    button.setSelected(followed);
    button.setText(followed ? "已关注" : "关注");
    button.setTextSize(13);
    button.setTypeface(followed ? Design.body() : android.graphics.Typeface.create(Design.body(), android.graphics.Typeface.BOLD));
    button.setTextColor(followed ? Design.MUTED : Design.RED);
    button.setMinWidth(Ui.dp(a, 56));
    button.setMinHeight(Ui.dp(a, 32));
    button.setPadding(Ui.dp(a, 12), Ui.dp(a, 4), Ui.dp(a, 12), Ui.dp(a, 4));
    button.setBackground(Ui.ripple(a, followed ? Design.ALT : Design.RED_SOFT, followed ? Design.BORDER : 0, 16));
    button.setContentDescription(followed ? "已关注作者，点击取消" : "关注作者");
  }

  private static float coverRatio(CommunityPost post){
    if(!post.images().isEmpty()&&post.images().get(0).width()>0)return Math.max(.72f,Math.min(1.45f,(float)post.images().get(0).height()/post.images().get(0).width()));
    return 1f;
  }
  static String photoKey(MainActivity a,int i){return a.draftKey("photo:"+i+":image");}
  static List<String> selectedPhotos(MainActivity a){
    List<String> images=new ArrayList<>();for(int i=0;i<9;i++){String path=a.drafts.getString(photoKey(a,i),"");if(!path.isBlank())images.add(path);}return images;
  }
  static void savePhotos(MainActivity a,List<String> images){for(int i=0;i<9;i++)a.drafts.putString(photoKey(a,i),i<images.size()?images.get(i):"");a.redraw();}
  static void create(MainActivity a,LinearLayout page,MainActivity.Route route) {
    a.heading(page,"02.2","写一页美食手账","分享真实的校园美食体验，审核通过后公开。");
    String ownerId=a.repo.currentUser()==null?"":a.repo.currentUser().id();
    String sessionKey=a.draftKey("draft-session");
    PostDraftStore.Draft restored=a.drafts.getBoolean(sessionKey,false)||ownerId.isBlank()
        ?null:PostDraftStore.load(a,ownerId);
    a.drafts.putBoolean(sessionKey,true);
    if(restored!=null){
      a.drafts.putString(a.draftKey("食记标题"),restored.title());
      a.drafts.putString(a.draftKey("动态正文"),restored.content());
      a.drafts.putString(a.draftKey("linkedDishId"),restored.linkedDishId());
      for(int i=0;i<9;i++)a.drafts.putString(photoKey(a,i),i<restored.images().size()?restored.images().get(i):"");
    }
    if(restored!=null){
      LinearLayout notice=Ui.card(page);
      notice.addView(Ui.text(a,"已恢复草稿",14,Design.MUTED));
      Ui.link(notice,"删除本机草稿",()->{PostDraftStore.delete(a,ownerId);a.clearDraft(a.route());a.redraw();});
    }
    photoEditor(a,page,false);
    LinearLayout form=Ui.card(page);form.addView(Ui.title(a,"02 · 标题与心得",18));Ui.gap(form,12);
    EditText title=Ui.field(form,"食记标题",false,false);title.setHint("标题，最多50字");title.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(50)});
    EditText content=Ui.field(form,"动态正文",false,true);content.setHint("正文，最多2000字");content.setMinLines(5);content.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(2000)});
    DishPickerScreen.show(a, form);
    TextView error=Ui.text(a,"",13,Design.RED);form.addView(error);MainActivity.Route origin=a.route();
    Ui.submit(form,"盖章发布",error,()->{
      String dishId = a.drafts.getString(a.draftKey("linkedDishId"), "");
      String heading=Validation.text(title.getText().toString(),50,"标题"),body=Validation.text(content.getText().toString(),2000,"正文");
      List<String> photos=selectedPhotos(a);if(photos.isEmpty())throw new IllegalArgumentException("请添加至少1张图片");
      String fingerprint=heading+"\n"+body+"\n"+String.join("|",photos)
          +(dishId.isBlank()?"":"\n"+dishId),key=a.draftKey("requestId");
      if(!fingerprint.equals(a.drafts.getString(a.draftKey("payload"),""))){a.drafts.putString(key,UUID.randomUUID().toString());a.drafts.putString(a.draftKey("payload"),fingerprint);}
      String requestId=a.drafts.getString(key);
      return ()->a.repo.submitCommunityPost(heading,body,photos,requestId,dishId);
    },()->{if(!ownerId.isBlank())PostDraftStore.delete(a,ownerId);a.clearDraft(origin);a.back();a.message("食记已提交审核，可在我的食记查看进度");});
  }

  static void photoEditor(MainActivity a,LinearLayout page,boolean optional) {
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
    photo.addView(Ui.text(a,optional?"转发帖可不配图，最多9张；首张为封面。":"1–9张图片，首张为封面；可前移调整顺序。",12,Design.MUTED));
  }

  private static String format(long value) {
    return CommunityText.publishedAt(value);
  }

  private CommunityScreen() {}
}
