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
  default byte[] loadImage(String reference) { throw new UnsupportedOperationException(); }

  default List<CommunityPost> communityPosts() { return List.of(); }
  default boolean communityPrototypeEnabled() { return false; }
  default CommunityPost communityPost(String id) { return null; }
  default List<CommunityComment> communityComments(String postId) { return List.of(); }
  default void submitCommunityPost(String content, List<String> images) {
    throw new UnsupportedOperationException("此数据源不支持社区发帖");
  }
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

  void submitDish(
      String stallId, String name, String description, BigDecimal price, String imageUrl);

  void submitStall(String hallId, String name, String description, String imageUrl);

  List<Submission> submissions(String userId);

  String context(String dishId);

  List<Review> myReviews();

  void deleteReview(String reviewId);

  void changePassword(String oldPassword, String newPassword);

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
