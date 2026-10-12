package cn.edu.bjtu.foodtime;

import cn.edu.bjtu.foodtime.Models.*;
import java.math.BigDecimal;
import java.util.List;
import org.json.JSONObject;

/** Screen-facing contract. Network implementations should load/cache off the main thread. */
public interface FoodtimeRepository extends AuthRepository, CatalogRepository {
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

  List<Audit> audits();

  List<JSONObject> entities(String table, String parentId);

  JSONObject entity(String table, String id);

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

  List<User> users();

  void updateUser(String userId, String role, boolean active);
}
