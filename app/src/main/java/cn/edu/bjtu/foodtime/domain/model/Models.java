package cn.edu.bjtu.foodtime.domain.model;

import java.math.BigDecimal;
import java.util.List;

public final class Models {
  /** Public registration capabilities; never contains limits, invitation inventory or account state. */
  public record RegistrationPolicy(String mode, boolean canSendEmailCode, boolean invitationRequired, String revision) {
    public boolean closed() { return "closed".equals(mode); }
    public static RegistrationPolicy open() { return new RegistrationPolicy("open", true, false, "open:email"); }
  }
  /** Server-projected management capabilities for the current account. */
  public record CapabilitySnapshot(List<String> capabilities, String revision) {
    public CapabilitySnapshot { capabilities = List.copyOf(capabilities); }
    public static CapabilitySnapshot empty() { return new CapabilitySnapshot(List.of(), ""); }
    public boolean can(String capability) { return capabilities.contains(capability); }
  }
  /** One-time login challenge image; the answer is never part of this payload. */
  public record CaptchaChallenge(String challengeId, String imageBase64, String expiresAt) {}
  public record DailyRecommendations(java.time.LocalDate date, String algorithmVersion, List<RecommendedDish> items) {
    public DailyRecommendations { items=List.copyOf(items); }
  }
  public record RecommendedDish(String dishId,String dishName,String diningHallId,String diningHallName,
      String stallId,String stallName,BigDecimal averageRating,long reviewCount,String imageUrl,
      String recommendationType,double recommendationScore,String reasonCode,String reason) {}

  public record Hall(String id, String name, String coverImageUrl, boolean active, BigDecimal averageScore, long reviewCount) {
    public Hall(String id,String name,String coverImageUrl,boolean active){this(id,name,coverImageUrl,active,null,0);}
  }

  public record Stall(
      String id, String diningHallId, String name, String coverImageUrl, boolean active, BigDecimal averageScore, long reviewCount) {
    public Stall(String id,String diningHallId,String name,String coverImageUrl,boolean active){this(id,diningHallId,name,coverImageUrl,active,null,0);}
  }

  public record Dish(
      String id,
      String stallId,
      String name,
      BigDecimal price,
      String description,
      boolean active,
      long revision) {}

  public record DishSearchRow(String id, String name, BigDecimal price,
                              String stallName, String diningHallName,
                              BigDecimal averageScore, long reviewCount) {
    public DishSearchRow(String id,String name,BigDecimal price,String stallName,String diningHallName) {
      this(id,name,price,stallName,diningHallName,null,0);
    }
  }

  public record FavoriteGroup(String id, String kind, String name, int sortOrder,
                              long revision, long itemCount) {}

  public record CorrectionProposal(String targetType, String targetId, long baseRevision,
                                   List<String> changedFields, String name, String description,
                                   BigDecimal price, String reason, List<String> evidenceUrls,
                                   String idempotencyKey) {
    public CorrectionProposal { changedFields = List.copyOf(changedFields); evidenceUrls = List.copyOf(evidenceUrls); }
  }

  public record CatalogCorrection(String id, String targetType, String targetId, String targetSummary,
                                  long baseRevision, String originalName, String originalDescription,
                                  BigDecimal originalPrice, String proposedName, String proposedDescription,
                                  BigDecimal proposedPrice, List<String> changedFields, String reason,
                                  String status, String reviewNote, long createdAt, long reviewedAt,
                                  long revision, List<String> evidenceUrls) {
    public CatalogCorrection { changedFields = List.copyOf(changedFields); evidenceUrls = List.copyOf(evidenceUrls); }
  }

  public record CatalogCorrectionPage(List<CatalogCorrection> items, long total, int page, int size) {
    public CatalogCorrectionPage { items = List.copyOf(items); }
    public boolean hasMore() { return (long) page * size < total; }
  }

  public record CatalogCorrectionAdmin(CatalogCorrection correction, String submitterId,
                                       String submitterName, String submitterEmail) {}

  public record CatalogCorrectionAdminPage(List<CatalogCorrectionAdmin> items, long total, int page, int size) {
    public CatalogCorrectionAdminPage { items = List.copyOf(items); }
    public boolean hasMore() { return (long) page * size < total; }
  }

  public record HistorySettings(boolean enabled, long revision, long dishEpoch, long postEpoch) {}

  public record HistoryItem(String kind, String targetId, String diningHallId, String stallId,
                            long visitedAt, boolean available, String title, String subtitle,
                            String imageUrl) {}

  public record HistoryPage(List<HistoryItem> items, long total, int page, int size) {
    public HistoryPage { items = List.copyOf(items); }
    public boolean hasMore() { return (long) page * size < total; }
  }

  public record DishSearchFilters(List<String> diningHallIds, BigDecimal minPrice,
                                  BigDecimal maxPrice, BigDecimal minRating, String sort) {
    public DishSearchFilters { diningHallIds = diningHallIds == null ? List.of() : List.copyOf(diningHallIds); }
    public static DishSearchFilters empty() { return new DishSearchFilters(List.of(), null, null, null, "relevance"); }
  }

  public record DishSearchPage(List<DishSearchRow> items, long total, int page, int size) {
    public DishSearchPage { items = List.copyOf(items); }
    public boolean hasMore() { return (long) page * size < total; }
  }

  public record ReportRow(String id, String type, String targetUserId, String postId,
                          String reason, String excerpt) {}

  public record BlockedUser(String id, String displayName, String avatarUrl) {}
  public record BlockedUserPage(List<BlockedUser> items, long total, int page, int size) {
    public BlockedUserPage { items = List.copyOf(items); }
    public boolean hasMore() { return (long) page * size < total; }
  }

  public record ReportPage(List<ReportRow> items, long total, int page, int size) {
    public ReportPage { items = List.copyOf(items); }
    public boolean hasMore() { return (long) page * size < total; }
  }

  public record User(String id, String email, String displayName, String role, boolean active,
      String avatarUrl, long createdAt, long reviewCount, long followingCount, long followerCount,boolean deleted) {
    public User(String id,String email,String displayName,String role,boolean active,String avatarUrl,long createdAt,long reviewCount,long followingCount,long followerCount){this(id,email,displayName,role,active,avatarUrl,createdAt,reviewCount,followingCount,followerCount,false);}
    public User(String id, String email, String displayName, String role, boolean active,
        String avatarUrl, long createdAt, long reviewCount) {
      this(id,email,displayName,role,active,avatarUrl,createdAt,reviewCount,0,0);
    }
    public User(String id, String email, String displayName, String role, boolean active) {
      this(id,email,displayName,role,active,"",0,0,0,0);
    }
    public boolean isAdmin() {
      return role.equals("admin") || role.equals("superadmin");
    }
  }

  /** Business calendar date; never interpreted in the device's timezone. */
  public record DashboardPoint(java.time.LocalDate day,long totalUsers,long newUsers,long newReviews,long newReplies,long newPosts,long onlinePeak) {}

  public record DashboardAdditions(long newUsers,long newReviews,long newReplies,long newPosts) {}

  /** A successful immutable Backend snapshot. No production preview/fallback values. */
  public record AdminDashboard(long totalUsers,long registeredUsers,long onlineUsers,long reviews,long replies,long posts,
      long halls,long stalls,long dishes,List<DashboardPoint> trend,long generatedAt) {
    public AdminDashboard { trend=List.copyOf(trend); }
    public List<DashboardPoint> trend(int days) {
      if ((days!=1&&days!=7&&days!=30)||trend.size()<days) throw new IllegalArgumentException("统计周期缺少完整数据");
      return trend.subList(trend.size()-days,trend.size());
    }
    public DashboardAdditions additions(int days) {
      long users=0,reviews=0,replies=0,posts=0;
      for (var point:trend(days)) {
        users=Math.addExact(users,point.newUsers());reviews=Math.addExact(reviews,point.newReviews());
        replies=Math.addExact(replies,point.newReplies());posts=Math.addExact(posts,point.newPosts());
      }
      return new DashboardAdditions(users,reviews,replies,posts);
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
      long likeCount, long replyCount, boolean likedByMe, long counterVersion,AiAssessment ai,String userEmail) {
    public Review(String id,String dishId,String userId,String userDisplayName,int rating,String content,long createdAt,
        long likeCount,long replyCount,boolean likedByMe,long counterVersion,AiAssessment ai) {
      this(id,dishId,userId,userDisplayName,rating,content,createdAt,likeCount,replyCount,likedByMe,counterVersion,ai,"");
    }
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
      long createdAt, String context,AiAssessment ai,String userEmail) {
    public Submission(String id,String type,String targetId,String userId,String userDisplayName,String name,String content,
        BigDecimal price,String imageUrl,int rating,String status,String reason,long createdAt,String context,AiAssessment ai) {
      this(id,type,targetId,userId,userDisplayName,name,content,price,imageUrl,rating,status,reason,createdAt,context,ai,"");
    }
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
  public record CatalogImagePage(List<Photo> items, long total, int page, boolean hasMore) {
    public CatalogImagePage { items = List.copyOf(items); }
  }

  /** Published community post returned by the Backend community feed. */
  public record CommunityAuditPage(String type,String status,int page,long total,List<Submission> items){
    public CommunityAuditPage{items=List.copyOf(items);}
    public static CommunityAuditPage empty(){return new CommunityAuditPage("posts","pending",1,0,List.of());}
  }
  public record CommunityAuditDetail(CommunityPost post,CommunityComment comment,CommunityComment rootComment,CommunityComment replyToComment,AiAssessment ai,String userEmail){
    public CommunityAuditDetail(CommunityPost post,CommunityComment comment,CommunityComment rootComment,CommunityComment replyToComment,AiAssessment ai){this(post,comment,rootComment,replyToComment,ai,"");}
  }
  public record CommunityPostPage(List<CommunityPost> items,long total,int page,boolean hasMore){
    public CommunityPostPage{items=List.copyOf(items);}
    public static CommunityPostPage empty(){return new CommunityPostPage(List.of(),0,0,false);}
  }
  public record CommunityImage(String url,int width,int height) {}
  public record LinkedDish(String id, String name) {}
  public record CommunityAuthor(String id, String displayName, String avatarUrl) {}
  public record CommunityFeedSlice(List<CommunityPost> items, long total, String nextCursor) {
    public CommunityFeedSlice { items = List.copyOf(items); }
  }
  public record CommunityPost(String id,String userId,String userDisplayName,String content,List<String> imageUrls,
      long likeCount,long commentCount,int repostCount,String repostOfId,String repostAuthor,String repostContent,
      boolean likedByMe,boolean favoritedByMe,String status,String reviewReason,long createdAt,
      String title,String userAvatarUrl,List<CommunityImage> images,long favoriteCount,String reviewStage,LinkedDish linkedDish,long viewCount) {
    public CommunityPost {imageUrls=imageUrls==null?List.of():List.copyOf(imageUrls);images=images==null?List.of():List.copyOf(images);}
    public CommunityPost(String id,String userId,String userDisplayName,String content,List<String> imageUrls,
        long likeCount,long commentCount,int repostCount,String repostOfId,String repostAuthor,String repostContent,
        boolean likedByMe,boolean favoritedByMe,String status,String reviewReason,long createdAt,
        String title,String userAvatarUrl,List<CommunityImage> images,long favoriteCount,String reviewStage,LinkedDish linkedDish) {
      this(id,userId,userDisplayName,content,imageUrls,likeCount,commentCount,repostCount,repostOfId,repostAuthor,repostContent,
          likedByMe,favoritedByMe,status,reviewReason,createdAt,title,userAvatarUrl,images,favoriteCount,reviewStage,linkedDish,0);
    }
    public CommunityPost(String id,String userId,String userDisplayName,String content,List<String> imageUrls,
        long likeCount,long commentCount,int repostCount,String repostOfId,String repostAuthor,String repostContent,
        boolean likedByMe,boolean favoritedByMe,String status,String reviewReason,long createdAt) {
      this(id,userId,userDisplayName,content,imageUrls,likeCount,commentCount,repostCount,repostOfId,repostAuthor,repostContent,
          likedByMe,favoritedByMe,status,reviewReason,createdAt,CommunityText.headline(content),"",List.of(),0,"",null);
    }
    public CommunityPost(String id,String userId,String userDisplayName,String content,List<String> imageUrls,
        long likeCount,long commentCount,int repostCount,String repostOfId,String repostAuthor,String repostContent,
        boolean likedByMe,boolean favoritedByMe,String status,String reviewReason,long createdAt,
        String title,String userAvatarUrl,List<CommunityImage> images,long favoriteCount,String reviewStage) {
      this(id,userId,userDisplayName,content,imageUrls,likeCount,commentCount,repostCount,repostOfId,repostAuthor,repostContent,
          likedByMe,favoritedByMe,status,reviewReason,createdAt,title,userAvatarUrl,images,favoriteCount,reviewStage,null);
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
  /** One row of a follow / follower list; followedByMe means the signed-in user follows them. */
  /** 后端下发的安卓更新信息；versionCode <= 0 表示未配置。 */
  public record AppRelease(int versionCode, String versionName, String apkUrl, String notes, int minVersionCode) {}

  public record FollowUser(String id, String displayName, String avatarUrl, boolean followedByMe) {}

  public record UserSearchPage(String query,List<FollowUser> items,long total,int page,boolean hasMore) {
    public UserSearchPage { items=List.copyOf(items); }
    public static UserSearchPage empty() { return new UserSearchPage("",List.of(),0,0,false); }
  }
  public record FollowPage(List<FollowUser> items, long total, int page, boolean hasMore) {
    public FollowPage { items=List.copyOf(items); }
    public FollowPage(List<FollowUser> items,long total) { this(items,total,1,false); }
    /** page=0 means not loaded; a successfully loaded empty list has page=1. */
    public static FollowPage empty() { return new FollowPage(List.of(),0,0,false); }
  }
  /** Follow relation state plus the target's follower count. */
  public record FollowState(boolean followed, long followerCount) {}

  public record Notice(String id, String content, long publishedAt,boolean isUpdate) {
    public Notice(String id,String content,long publishedAt){this(id,content,publishedAt,false);}
    /** Compatibility for older test fixtures only; production uses content. */
    public Notice(String id,String title,String body,long publishedAt) {
      this(id,"# "+title+"\n\n"+body,publishedAt);
    }
  }

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
      String refType, String refId, String refCommentId, String refRootCommentId,
      int actorCount, boolean isRead, long createdAt) {
    public Message(String id, String title, String body, String category,
        String refType, String refId, boolean isRead, long createdAt) {
      this(id, title, body, category, refType, refId, "", "", 0, isRead, createdAt);
    }
  }

  /** Paged inbox messages, newest first. */
  public record MessagePage(List<Message> items, long total, boolean hasMore) {
    public MessagePage { items = List.copyOf(items); }
    public static MessagePage empty() { return new MessagePage(List.of(), 0, false); }
  }

  /** 私信会话中的对方用户。 */
  public record ChatUser(String id, String displayName, String avatarUrl) {
    public ChatUser { displayName = displayName == null ? "" : displayName; avatarUrl = avatarUrl == null ? "" : avatarUrl; }
  }

  /** 私信会话摘要。 */
  public record Conversation(String id, ChatUser other, String lastMessage, String lastSenderId,
      long lastMessageAt, long unreadCount, long updatedAt) {
    public Conversation {
      lastMessage = lastMessage == null ? "" : lastMessage;
      lastSenderId = lastSenderId == null ? "" : lastSenderId;
    }
  }

  public record ConversationPage(List<Conversation> items, long total, boolean hasMore) {
    public ConversationPage { items = List.copyOf(items); }
    public static ConversationPage empty() { return new ConversationPage(List.of(), 0, false); }
  }

  /** 一对一私信正文。 */
  public record DirectMessage(String id, String conversationId, String senderId, String content, long createdAt) {}

  public record DirectMessagePage(List<DirectMessage> items, boolean hasMore, String nextCursor) {
    public DirectMessagePage { items = List.copyOf(items); nextCursor = nextCursor == null ? "" : nextCursor; }
    public static DirectMessagePage empty() { return new DirectMessagePage(List.of(), false, ""); }
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
      long createdAt, String type,String actor,String aiModel,String userId,String userDisplayName,String userEmail,
      String name,String content,String imageUrl,long submittedAt) {
    public Audit(String id,String submissionId,String moderatorId,String action,String reason,long createdAt){
      this(id,submissionId,moderatorId,action,reason,createdAt,"","","","","","","","","",0);
    }
  }

  private Models() {}
}
