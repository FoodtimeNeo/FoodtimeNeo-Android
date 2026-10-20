package cn.edu.bjtu.foodtime.domain.model;

import java.math.BigDecimal;

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

  public record ReviewReply(String id,String reviewId,String replyToId,String userDisplayName,String content,
      String status,String reviewReason,long createdAt,boolean replyToDeleted,String replyToUserDisplayName,
      boolean ownedByMe,String dishId,String dishName,boolean reviewDeleted,LikeState likes) {}

  public record ReplyPage(java.util.List<ReviewReply> items,long total,boolean hasMore,String nextCursor) {
    public static ReplyPage empty() { return new ReplyPage(java.util.List.of(),0,false,""); }
  }

  public record Review(
      String id, String dishId, String userId, String userDisplayName, int rating, String content, long createdAt,
      long likeCount, long replyCount, boolean likedByMe, long counterVersion) {
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
      long createdAt, String context) {
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
