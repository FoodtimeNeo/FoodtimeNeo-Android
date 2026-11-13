package cn.edu.bjtu.foodtime.domain.model;

import java.util.List;

/** Management-only records; internal notes never enter user receipt models. */
public final class ReportManagement {
  private ReportManagement() {}
  public record Page<T>(List<T> items, long total, int page, int size) {
    public Page { items = List.copyOf(items); }
    public boolean hasMore() { return (long) page * size < total; }
  }
  public record Decision(String decisionId, String reportId, String outcome, String publicReason,
      String targetType, String targetId, String targetUserId, long createdAt) {}
  public record FollowUp(String id, String reportId, String authorRole, String authorName,
      String content, String internalNote, List<String> evidenceUrls, long createdAt) {
    public FollowUp { evidenceUrls = List.copyOf(evidenceUrls); }
  }
  public record Appeal(String id, String decisionId, String appellantId, String appellantName,
      String side, String content, String status, String outcome, String publicReason,
      String internalNote, long revision, long createdAt, long resolvedAt, List<String> evidenceUrls) {
    public Appeal { evidenceUrls = List.copyOf(evidenceUrls); }
  }
}
