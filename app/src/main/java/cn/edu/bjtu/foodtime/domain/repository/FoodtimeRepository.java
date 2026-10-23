package cn.edu.bjtu.foodtime.domain.repository;

import cn.edu.bjtu.foodtime.domain.model.Models.*;
import java.math.BigDecimal;
import java.util.List;

/** Screen-facing contract. Network implementations should load/cache off the main thread. */
public interface FoodtimeRepository extends AuthRepository, CatalogRepository {
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

  default List<CommunityPost> communityPosts() { return List.of(); }
  default boolean communityPrototypeEnabled() { return false; }
  default CommunityPost communityPost(String id) { return null; }
  default List<CommunityComment> communityComments(String postId) { return List.of(); }
  default void submitCommunityPost(String content, List<String> images) {
    throw new UnsupportedOperationException("此数据源不支持社区发帖");
  }
  default void submitCommunityPost(String title,String content,List<String> images,String clientRequestId) {throw new UnsupportedOperationException();}
  default void loadMoreCommunityPosts(){throw new UnsupportedOperationException();}
  default boolean hasMoreCommunityPosts(){return false;}
  default void submitCommunityComment(String postId, String content) {
    throw new UnsupportedOperationException("此数据源不支持社区评论");
  }
  default CommunityAction toggleCommunityLike(String targetType, String targetId) {
    throw new UnsupportedOperationException("此数据源不支持点赞");
  }
  default CommunityAction toggleCommunityFavorite(String postId) {
    throw new UnsupportedOperationException("此数据源不支持收藏");
  }
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
