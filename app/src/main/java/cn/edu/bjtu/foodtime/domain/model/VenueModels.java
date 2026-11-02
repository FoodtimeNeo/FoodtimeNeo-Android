package cn.edu.bjtu.foodtime.domain.model;

import cn.edu.bjtu.foodtime.domain.model.Models.LikeState;
import java.math.BigDecimal;
import java.util.List;

public final class VenueModels {
  private VenueModels() {}

  public record Target(
      String type,
      String targetId,
      String name,
      String diningHallId,
      String stallId,
      BigDecimal averageScore,
      long reviewCount) {}

  public record Row(
      String id,
      String reviewId,
      String submissionId,
      String userId,
      String userDisplayName,
      String targetType,
      String targetId,
      String name,
      int rating,
      String content,
      String status,
      String reviewReason,
      long createdAt,
      long updatedAt,
      boolean ownedByMe,
      LikeState likes,
      long replyCount,
      String replyToId,
      String replyToUserDisplayName,
      boolean replyToDeleted) {}

  public record Page(List<Row> items, long total, boolean hasMore, String nextCursor) {
    public Page {
      items = List.copyOf(items);
    }

    public static Page empty() {
      return new Page(List.of(), 0, false, "");
    }
  }

  public record Snapshot(
      Target target, Page published, Row publicVersion, Row privateSubmission, String rating) {}

  public record AuditDetail(Row publicVersion, Row submission) {}
}
