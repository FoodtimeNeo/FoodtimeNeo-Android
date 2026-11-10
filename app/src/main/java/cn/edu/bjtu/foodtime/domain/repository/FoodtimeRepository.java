package cn.edu.bjtu.foodtime.domain.repository;

import cn.edu.bjtu.foodtime.domain.model.Models.*;
import java.math.BigDecimal;
import java.util.List;

/** Screen-facing contract. Network implementations should load/cache off the main thread. */
public interface FoodtimeRepository extends AuthRepository, CatalogRepository, VenueRepository {
  /** Run on a worker. Read methods below return immutable cached snapshots. */
  default void restoreSession() {}
  default void refresh(ContentRequest request) {}
  default RatingSummary rating(String dishId) { return new RatingSummary(null,0); }
  default ReviewPage dishReviews(String dishId) {
    List<Review> items=reviews(dishId);return new ReviewPage(items,items.size(),1,false);
  }
  /** Public dish comments are fetched one page at a time, on the worker. */
  default void loadDishReviews(String dishId) { refresh(new ContentRequest(ContentRequest.Query.DISH_DETAIL,dishId)); }
  default void loadMoreDishReviews(String dishId) { throw new UnsupportedOperationException(); }
  default byte[] loadImage(String reference) { throw new UnsupportedOperationException(); }
  default ImageResource loadImageResource(String reference) { return new ImageResource(loadImage(reference),false,0,""); }
  default String imageIdentity() { var user=currentUser();return user==null?"guest":user.id()+":"+user.role(); }
  default long imageCacheBytes() { return 0; }
  default void clearImageCache() {}
  /** Cached, account-scoped superadmin overview; null until a real refresh succeeds. */
  default AdminDashboard adminDashboard() { return null; }
  default DailyRecommendations dailyRecommendations() { return null; }

  default boolean blockedByMe(String userId) { throw new UnsupportedOperationException(); }
  default void setBlocked(String userId, boolean blocked) { throw new UnsupportedOperationException(); }
  default void reportContent(String type, String id, String reason) { throw new UnsupportedOperationException(); }
  default ReportPage reports(int page) { throw new UnsupportedOperationException(); }
  default void reviewReport(String id) { throw new UnsupportedOperationException(); }

  default List<CommunityPost> communityPosts() { return List.of(); }
  default CommunityAuthor communityAuthor(String userId) { throw new UnsupportedOperationException(); }
  default CommunityFeedSlice discoverPosts(String authorId, boolean following, String query, String cursor) {
    throw new UnsupportedOperationException();
  }
  default boolean communityPrototypeEnabled() { return false; }
  default CommunityPost communityPost(String id) { return null; }
  default List<CommunityComment> communityComments(String postId) { return List.of(); }
  default void submitCommunityPost(String content, List<String> images) {
    throw new UnsupportedOperationException("此数据源不支持社区发帖");
  }
  default void submitCommunityPost(String title,String content,List<String> images,String clientRequestId) {throw new UnsupportedOperationException();}
  default void submitCommunityPost(String title,String content,List<String> images,String clientRequestId,String dishId) {
    if (!dishId.isBlank()) throw new UnsupportedOperationException();
    submitCommunityPost(title,content,images,clientRequestId);
  }
  default void loadMoreCommunityPosts(){throw new UnsupportedOperationException();}
  default boolean hasMoreCommunityPosts(){return false;}
  default String submitCommunityComment(String postId, String content) {
    throw new UnsupportedOperationException("此数据源不支持社区评论");
  }
  default CommunityCommentPage communityRoots(String postId){return CommunityCommentPage.empty();}
  default CommunityCommentPage communityReplies(String rootId){return CommunityCommentPage.empty();}
  default void loadMoreCommunityComments(String postId){throw new UnsupportedOperationException();}
  default void loadCommunityReplies(String postId,String rootId,boolean more){throw new UnsupportedOperationException();}
  /** 深链定位：按需加载评论所在楼层与回复，找到目标后返回 true。 */
  default boolean ensureCommunityCommentVisible(String postId,String rootId,String commentId){return false;}
  default String submitCommunityComment(String postId,String content,String rootId,String targetId,String requestId){throw new UnsupportedOperationException();}
  /** 提交后轮询审核结果；返回 null 表示评论已不可见。 */
  default String communityCommentStatus(String commentId) { return null; }
  default void reloadCommunityComments(String postId) {}
  default void reloadCommunityReplies(String postId,String rootId) {}
  default void deleteCommunityComment(String id){throw new UnsupportedOperationException();}
  default CommunityPostPage communityCollection(boolean favorites){return CommunityPostPage.empty();}
  default void loadMoreCommunityCollection(boolean favorites){throw new UnsupportedOperationException();}
  default void deleteCommunityPost(String id){throw new UnsupportedOperationException();}
  default CommunityAction setCommunityLike(String type,String id,boolean active){throw new UnsupportedOperationException();}
  default CommunityAction setCommunityFavorite(String id,boolean active){throw new UnsupportedOperationException();}
  default CommunityAuditPage communityAudit(){return CommunityAuditPage.empty();}
  default CommunityAuditDetail communityAuditDetail(){return null;}
  default void moderateCommunity(String type,String id,String action,String reason){throw new UnsupportedOperationException();}
  default void removeCommunityAsAdmin(String type,String id,String reason){throw new UnsupportedOperationException();}
  default CommunityAction toggleCommunityLike(String targetType, String targetId) {
    throw new UnsupportedOperationException("此数据源不支持点赞");
  }
  default CommunityAction toggleCommunityFavorite(String postId) {
    throw new UnsupportedOperationException("此数据源不支持收藏");
  }
  /** Announcement inbox, newest first. Refreshed by ContentRequest NOTICES on a worker. */
  record AdminNotice(String id,String title,String content) {}
  record AdminNoticePage(List<AdminNotice> items,long total) {}
  default AdminNoticePage adminNotices(int page,String query) { throw new UnsupportedOperationException(); }
  default AdminNotice adminNotice(String id) { throw new UnsupportedOperationException(); }
  default String noticeSetting() { throw new UnsupportedOperationException(); }
  default void setNoticeSetting(String id) { throw new UnsupportedOperationException(); }
  default AdminNotice saveNotice(String id,String content) { throw new UnsupportedOperationException(); }
  default NoticePage notices() { return NoticePage.empty(); }
  default void loadMoreNotices() { throw new UnsupportedOperationException(); }
  /** Personal system messages, newest first. Refreshed by ContentRequest MESSAGES on a worker. */
  /** My follow lists, refreshed by ContentRequest FOLLOWING / FOLLOWERS on a worker. */
  default FollowPage following() { return FollowPage.empty(); }
  default FollowPage followers() { return FollowPage.empty(); }
  default void loadFollows(boolean followers) { throw new UnsupportedOperationException(); }
  default void loadMoreFollows(boolean followers) { throw new UnsupportedOperationException(); }
  default UserSearchPage userSearchResults() { return UserSearchPage.empty(); }
  /** Explicit network read; run on the worker, then consume the identity-scoped snapshot above. */
  default void loadUserSearch(String query,int page) { throw new UnsupportedOperationException(); }
  /** Cached relation state for one author; null when it has not been loaded yet. */
  default FollowState followState(String userId) { return null; }
  default FollowState setFollow(String userId, boolean follow) { throw new UnsupportedOperationException("此数据源不支持关注"); }
  default MessagePage messages() { return MessagePage.empty(); }
  default void loadMoreMessages() { throw new UnsupportedOperationException(); }
  /** Combined unread badge: unread announcements plus unread personal messages. */
  /** 应用内更新检查；返回 null 表示后端未配置或无更新。 */
  default AppRelease appRelease() { return null; }
  /** 访客绑定学校邮箱：先发验证码，再带验证码绑定并刷新当前用户。 */
  default void sendSchoolEmailCode(String email) { throw new UnsupportedOperationException("此数据源不支持绑定学校邮箱"); }
  default void bindSchoolEmail(String email, String code) { throw new UnsupportedOperationException("此数据源不支持绑定学校邮箱"); }
  default long unreadCount() { return 0; }
  /** Incremental announcement feed for background polling; does not reset page caches. */
  default NoticeFeed noticeFeed(long sinceSeq, int limit) { return NoticeFeed.empty(); }
  default void markNoticeRead(String id) {}
  /** Login-only check. Null means no configured announcement or this user opted out. */
  default Notice currentNotice() { return null; }
  default void confirmNotice(String id, boolean dontShowAgain) {}
  default void readMessage(String id) {}
  default void readAllMessages() {}
  /** 一对一私信会话（按最近消息排序）。 */
  default ConversationPage conversations() { return ConversationPage.empty(); }
  default void loadMoreConversations() { throw new UnsupportedOperationException(); }
  default Conversation conversation(String id) { return null; }
  default long directUnreadCount() { return 0; }
  /** 会话消息按时间正序缓存；cursor 用于加载更早的消息。 */
  default DirectMessagePage conversationMessages(String conversationId) { return DirectMessagePage.empty(); }
  default void loadMoreConversationMessages(String conversationId) { throw new UnsupportedOperationException(); }
  default DirectMessage sendDirectMessage(String conversationId, String content, String clientRequestId) { throw new UnsupportedOperationException("此数据源不支持私信"); }
  default Conversation startConversation(String userId) { throw new UnsupportedOperationException("此数据源不支持私信"); }
  default void readConversation(String id) {}
  default void hideConversation(String id) {}
  void submitPhoto(String dishId, String imageUrl);

  void submitReview(String dishId, int rating, String content);
  default LikeState setReviewLike(String reviewId, boolean liked) { throw new UnsupportedOperationException("该数据源不支持评论点赞"); }

  default ReplyPage reviewReplies(String reviewId) { return ReplyPage.empty(); }
  default boolean hasLoadedReviewReplies(String reviewId) { return false; }
  /** Fetch only this thread's first page on a worker, without refreshing the dish. */
  default void loadReviewReplies(String reviewId) { refresh(new ContentRequest(ContentRequest.Query.REVIEW_REPLIES,reviewId)); }
  default ReplyPage myReviewReplies() { return ReplyPage.empty(); }
  default void loadMoreReplies(String reviewId) { throw new UnsupportedOperationException(); }
  default void submitReply(String reviewId,String replyToId,String content,String clientRequestId) { throw new UnsupportedOperationException(); }
  default void deleteReply(String replyId) { throw new UnsupportedOperationException(); }
  default LikeState setReplyLike(String replyId,boolean liked) { throw new UnsupportedOperationException(); }
  default void removeReplyAsAdmin(String replyId,String reason) { throw new UnsupportedOperationException(); }

  void submitDish(
      String stallId, String name, String description, BigDecimal price, String imageUrl);

  void submitStall(String hallId, String name, String description, String imageUrl);

  List<Submission> submissions(String userId);

  String context(String dishId);

  List<Review> myReviews();

  void deleteReview(String reviewId);

  void changePassword(String oldPassword, String newPassword);

  default User updateProfile(String displayName, String avatarReference) {
    throw new UnsupportedOperationException("此数据源不支持修改个人资料");
  }

  void moderate(String submissionId, String action, String reason);
  default void moderate(String submissionId,String action,String reason,BigDecimal price) {
    moderate(submissionId,action,reason);
  }

  List<Audit> audits();

  List<AdminEntity> entities(String table, String parentId);

  AdminEntity entity(String table, String id);

  void editEntity(
      String table,
      String id,
      String parentId,
      String name,
      String description,
      String price,
      String image,
      boolean active);

  void removeReviewAsAdmin(String reviewId);
  default void removeReviewAsAdmin(String reviewId,String reason) { removeReviewAsAdmin(reviewId); }
  default void removeEntity(String table,String id) { throw new UnsupportedOperationException("此数据源不支持删除"); }

  List<User> users();

  void updateUser(String userId, String role, boolean active);
}
