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

  public record User(String id, String email, String displayName, String role, boolean active) {
    public boolean isAdmin() {
      return role.equals("admin") || role.equals("superadmin");
    }
  }

  public record Review(
      String id, String dishId, String userId, int rating, String content, long createdAt) {}

  public record Submission(
      String id,
      String type,
      String targetId,
      String userId,
      String name,
      String content,
      BigDecimal price,
      String imageUrl,
      int rating,
      String status,
      String reason,
      long createdAt) {}

  public record Photo(String id, String dishId, String imageUrl, String userId) {}

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
