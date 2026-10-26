package cn.edu.bjtu.foodtime.domain.model;

import java.math.BigDecimal;
import java.util.List;

public final class Models {
  public record Hall(String id, String name, String coverImageUrl, boolean active) {}

  public record Stall(
      String id, String diningHallId, String name, String coverImageUrl, boolean active) {}

  public record Dish(
      String id,
      String stallId,
      String name,
      BigDecimal price,
      String description,
      boolean active) {}

  public record User(String id, String email, String displayName, String role, boolean active,
      String avatarUrl, long createdAt, long reviewCount) {
    public User(String id, String email, String displayName, String role, boolean active) {
      this(id,email,displayName,role,active,"",0,0);
    }
    public boolean isAdmin() {
      return role.equals("admin") || role.equals("superadmin");
    }
  }

  public record LikeState(boolean likedByMe, long likeCount, long counterVersion) {}

  public record AiAssessment(String riskLevel,BigDecimal confidence,java.util.List<String> labels,String reason,long checkedAt) {
    public AiAssessment { labels=labels==null?java.util.List.of():java.util.List.copyOf(labels); }
    public static AiAssessment empty() { return new AiAssessment("",null,java.util.List.of(),"",0); }
  }

  public record ReviewPage(java.util.List<Review> items,long total,int page,boolean hasMore) {
    public ReviewPage { items=java.util.List.copyOf(items); }
    public static ReviewPage empty() { return new ReviewPage(java.util.List.of(),0,0,false); }
  }

  public record ReviewReply(String id,String reviewId,String replyToId,String userDisplayName,String content,
      String status,String reviewReason,long createdAt,boolean replyToDeleted,String replyToUserDisplayName,
      boolean ownedByMe,String dishId,String dishName,boolean reviewDeleted,LikeState likes) {}

  public record ReplyPage(java.util.List<ReviewReply> items,long total,boolean hasMore,String nextCursor) {
    public static ReplyPage empty() { return new ReplyPage(java.util.List.of(),0,false,""); }
  }

  public record Review(
      String id, String dishId, String userId, String userDisplayName, int rating, String content, long createdAt,
      long likeCount, long replyCount, boolean likedByMe, long counterVersion,AiAssessment ai) {
    public Review(String id,String dishId,String userId,String userDisplayName,int rating,String content,long createdAt,
        long likeCount,long replyCount,boolean likedByMe,long counterVersion) {
      this(id,dishId,userId,userDisplayName,rating,content,createdAt,likeCount,replyCount,likedByMe,counterVersion,AiAssessment.empty());
    }
    public Review(String id, String dishId, String userId, String userDisplayName, int rating, String content, long createdAt) {
      this(id,dishId,userId,userDisplayName,rating,content,createdAt,0,0,false,0);
    }
    public Review(String id, String dishId, String userId, int rating, String content, long createdAt) {
      this(id, dishId, userId, "", rating, content, createdAt);
    }
  }

  public record Submission(
      String id,
      String type,
      String targetId,
      String userId,
      String userDisplayName,
      String name,
      String content,
      BigDecimal price,
      String imageUrl,
      int rating,
      String status,
      String reason,
      long createdAt, String context,AiAssessment ai) {
    public Submission(String id,String type,String targetId,String userId,String userDisplayName,String name,String content,
        BigDecimal price,String imageUrl,int rating,String status,String reason,long createdAt,String context) {
      this(id,type,targetId,userId,userDisplayName,name,content,price,imageUrl,rating,status,reason,createdAt,context,AiAssessment.empty());
    }
    public Submission(String id,String type,String targetId,String userId,String name,String content,
        BigDecimal price,String imageUrl,int rating,String status,String reason,long createdAt) {
      this(id,type,targetId,userId,"",name,content,price,imageUrl,rating,status,reason,createdAt,"");
    }
    public Submission(String id,String type,String targetId,String userId,String userDisplayName,String name,String content,
        BigDecimal price,String imageUrl,int rating,String status,String reason,long createdAt) {
      this(id,type,targetId,userId,userDisplayName,name,content,price,imageUrl,rating,status,reason,createdAt,"");
    }
  }

  public record Photo(String id, String dishId, String imageUrl, String userId) {}

  /** Published community post returned by the Backend community feed. */
  public record CommunityAuditPage(String type,String status,int page,long total,List<Submission> items){
    public CommunityAuditPage{items=List.copyOf(items);}
    public static CommunityAuditPage empty(){return new CommunityAuditPage("posts","pending",1,0,List.of());}
  }
  public record CommunityAuditDetail(CommunityPost post,CommunityComment comment,CommunityComment rootComment,CommunityComment replyToComment,AiAssessment ai){}
  public record CommunityPostPage(List<CommunityPost> items,long total,int page,boolean hasMore){
    public CommunityPostPage{items=List.copyOf(items);}
    public static CommunityPostPage empty(){return new CommunityPostPage(List.of(),0,0,false);}
  }
  public record CommunityImage(String url,int width,int height) {}
  public record CommunityPost(String id,String userId,String userDisplayName,String content,List<String> imageUrls,
      long likeCount,long commentCount,int repostCount,String repostOfId,String repostAuthor,String repostContent,
      boolean likedByMe,boolean favoritedByMe,String status,String reviewReason,long createdAt,
      String title,String userAvatarUrl,List<CommunityImage> images,long favoriteCount,String reviewStage) {
    public CommunityPost {imageUrls=imageUrls==null?List.of():List.copyOf(imageUrls);images=images==null?List.of():List.copyOf(images);}
    public CommunityPost(String id,String userId,String userDisplayName,String content,List<String> imageUrls,
        long likeCount,long commentCount,int repostCount,String repostOfId,String repostAuthor,String repostContent,
        boolean likedByMe,boolean favoritedByMe,String status,String reviewReason,long createdAt) {
      this(id,userId,userDisplayName,content,imageUrls,likeCount,commentCount,repostCount,repostOfId,repostAuthor,repostContent,
          likedByMe,favoritedByMe,status,reviewReason,createdAt,CommunityText.headline(content),"",List.of(),0,"");
    }
  }

  /** Approved comment shown below a community post. */
  public record CommunityComment(String id,String postId,String userId,String userDisplayName,String content,
      long likeCount,String status,long createdAt,String rootCommentId,String replyToCommentId,String replyToUserId,
      String replyToDisplayName,String userAvatarUrl,long replyCount,boolean likedByMe,boolean deleted,String reviewStage,String reviewReason) {
    public CommunityComment withLike(boolean liked,long count){return new CommunityComment(id,postId,userId,userDisplayName,content,count,status,createdAt,rootCommentId,replyToCommentId,replyToUserId,replyToDisplayName,userAvatarUrl,replyCount,liked,deleted,reviewStage,reviewReason);}
    public CommunityComment(String id,String postId,String userId,String name,String content,long likes,String status,long time){
      this(id,postId,userId,name,content,likes,status,time,"","","","","",0,false,false,"","");
    }
  }
  public record CommunityCommentPage(List<CommunityComment> items,long total,boolean hasMore,String nextCursor){
    public CommunityCommentPage{items=List.copyOf(items);}
    public static CommunityCommentPage empty(){return new CommunityCommentPage(List.of(),0,false,"");}
  }

  /** Announcement broadcast to all users; seq is a polling cursor only, never an identity. */
  public record Notice(String id, String title, String body, long publishedAt) {}

  /** Paged announcements, newest first. */
  public record NoticePage(List<Notice> items, long total, boolean hasMore) {
    public NoticePage { items = List.copyOf(items); }
    public static NoticePage empty() { return new NoticePage(List.of(), 0, false); }
  }

  /** Incremental announcement feed in publish order, plus the unread badge count. */
  public record NoticeFeed(List<Notice> items, long unreadCount) {
    public NoticeFeed { items = List.copyOf(items); }
    public static NoticeFeed empty() { return new NoticeFeed(List.of(), 0); }
  }

  /** Personal inbox message. */
  public record Message(String id, String title, String body, String category,
      String refType, String refId, boolean isRead, long createdAt) {}

  /** Paged inbox messages, newest first. */
  public record MessagePage(List<Message> items, long total, boolean hasMore) {
    public MessagePage { items = List.copyOf(items); }
    public static MessagePage empty() { return new MessagePage(List.of(), 0, false); }
  }

  /** Latest state returned by a like/favorite toggle. */
  public record CommunityAction(boolean active, long count) {}

  /** The server's transactionally maintained rating summary; average is null when no score is available. */
  public record RatingSummary(BigDecimal averageScore,long reviewCount) {}

  /** Editable catalog item exposed to administration screens without leaking storage JSON. */
  public record AdminEntity(
      String id,
      String name,
      String description,
      String price,
      String coverImageUrl,
      boolean active) {}

  public record Audit(
      String id,
      String submissionId,
      String moderatorId,
      String action,
      String reason,
      long createdAt) {}

  private Models() {}
}
