package cn.edu.bjtu.foodtime.data.prototype;

import cn.edu.bjtu.foodtime.domain.model.Models.*;
import java.util.*;

/** In-memory community demo used only when the build explicitly enables prototype mode. */
public final class CommunityPrototype {
  private final Map<String,CommunityPost> posts=new LinkedHashMap<>();
  private final Map<String,List<CommunityComment>> comments=new HashMap<>();

  public CommunityPrototype() {
    long now=System.currentTimeMillis();
    add(sample("prototype-1","北交干饭达人","学一餐厅瓦罐汤！入秋后的第一碗热汤绝了。",
        List.of("asset:dish_1.webp"),128,18,now-12*60_000L));
    add(sample("prototype-2","交大辣椒人","明湖餐厅酸汤牛肉粉！酸辣开胃，午休前来一碗太满足了。",
        List.of("asset:dish_2.webp"),96,12,now-38*60_000L));
    add(sample("prototype-3","铁板专家小王","留园铁板烧！滋滋冒油的幸福感，鸡排和时蔬都很入味。",
        List.of("asset:dish_3.webp","asset:dish_4.webp"),203,31,now-75*60_000L));
    add(sample("prototype-4","自习室常客","东区二楼手工水饺，皮薄馅大，复习周的安慰餐。",
        List.of("asset:dish_4.webp"),67,9,now-2*60*60_000L));
    add(sample("prototype-5","清真食堂迷","清真餐厅手抓羊肉大盘鸡！聚餐首选，四个人吃刚刚好。",
        List.of("asset:dish_5.webp"),156,24,now-4*60*60_000L));
    add(sample("prototype-6","甜品研究所","学苑餐厅西点房蛋挞出炉！酥到掉渣，下午三点还有。",
        List.of("asset:dish_6.webp"),88,15,now-7*60*60_000L));
    add(sample("prototype-7","交大早八人","早八课前的能量早餐，热乎、出餐快，五分钟解决战斗。",
        List.of("asset:dish_2.webp"),42,6,now-24*60*60_000L));
    add(sample("prototype-8","校园干饭人","今天不想排队，发现这个窗口人少味道却很惊喜，推荐加一份青菜。",
        List.of("asset:dish_1.webp","asset:dish_3.webp","asset:dish_6.webp"),74,11,now-30*60*60_000L));

    comments.put("prototype-1",new ArrayList<>(List.of(
        comment("comment-1","prototype-1","明湖小队长","这个汤口味会不会很咸？",3,now-8*60_000L),
        comment("comment-2","prototype-1","北交干饭达人","我这碗刚好，窗口阿姨还可以帮忙少盐。",8,now-5*60_000L))));
    comments.put("prototype-3",new ArrayList<>(List.of(
        comment("comment-3","prototype-3","南门夜归人","请问几点去不用排长队？",5,now-48*60_000L))));
  }

  public synchronized List<CommunityPost> posts() {
    return List.copyOf(posts.values());
  }

  public synchronized CommunityPost post(String id) {
    return posts.get(id);
  }

  public synchronized List<CommunityComment> comments(String postId) {
    return List.copyOf(comments.getOrDefault(postId,List.of()));
  }

  public synchronized CommunityPost publish(User author,String content,List<String> images) {
    String id="prototype-user-"+UUID.randomUUID();
    CommunityPost post=new CommunityPost(id,userId(author),displayName(author),content,images,0,0,0,
        "","","",false,false,"approved","",System.currentTimeMillis());
    LinkedHashMap<String,CommunityPost> reordered=new LinkedHashMap<>();
    reordered.put(id,post);reordered.putAll(posts);posts.clear();posts.putAll(reordered);
    return post;
  }

  public synchronized CommunityComment comment(User author,String postId,String content) {
    CommunityPost post=required(postId);
    CommunityComment value=comment("prototype-comment-"+UUID.randomUUID(),postId,displayName(author),content,0,System.currentTimeMillis());
    List<CommunityComment> list=comments.computeIfAbsent(postId,key -> new ArrayList<>());
    list.add(value);
    replace(post,new CommunityPost(post.id(),post.userId(),post.userDisplayName(),post.content(),post.imageUrls(),
        post.likeCount(),post.commentCount()+1,post.repostCount(),post.repostOfId(),post.repostAuthor(),
        post.repostContent(),post.likedByMe(),post.favoritedByMe(),post.status(),post.reviewReason(),post.createdAt()));
    return value;
  }

  public synchronized CommunityAction toggleLike(String postId) {
    CommunityPost post=required(postId);boolean active=!post.likedByMe();
    int count=Math.max(0,post.likeCount()+(active?1:-1));
    replace(post,new CommunityPost(post.id(),post.userId(),post.userDisplayName(),post.content(),post.imageUrls(),
        count,post.commentCount(),post.repostCount(),post.repostOfId(),post.repostAuthor(),post.repostContent(),
        active,post.favoritedByMe(),post.status(),post.reviewReason(),post.createdAt()));
    return new CommunityAction(active,count);
  }

  public synchronized CommunityAction toggleFavorite(String postId) {
    CommunityPost post=required(postId);boolean active=!post.favoritedByMe();
    replace(post,new CommunityPost(post.id(),post.userId(),post.userDisplayName(),post.content(),post.imageUrls(),
        post.likeCount(),post.commentCount(),post.repostCount(),post.repostOfId(),post.repostAuthor(),
        post.repostContent(),post.likedByMe(),active,post.status(),post.reviewReason(),post.createdAt()));
    return new CommunityAction(active,0);
  }

  private void add(CommunityPost post) { posts.put(post.id(),post); }
  private void replace(CommunityPost oldPost,CommunityPost newPost) { posts.put(oldPost.id(),newPost); }
  private CommunityPost required(String id) {
    CommunityPost post=posts.get(id);
    if(post==null) throw new IllegalArgumentException("动态不存在或暂不可见");
    return post;
  }
  private static CommunityPost sample(String id,String author,String content,List<String> images,
      int likes,int commentCount,long createdAt) {
    return new CommunityPost(id,"prototype-user-"+id,author,content,images,likes,commentCount,0,
        "","","",false,false,"approved","",createdAt);
  }
  private static CommunityComment comment(String id,String postId,String author,String content,int likes,long createdAt) {
    return new CommunityComment(id,postId,"prototype-user-"+author,author,content,likes,"approved",createdAt);
  }
  private static String userId(User user) { return user==null?"prototype-current-user":user.id(); }
  private static String displayName(User user) {
    return user==null||user.displayName().isBlank()?"校园干饭人":user.displayName();
  }
}
