package cn.edu.bjtu.foodtime.domain.repository;

import cn.edu.bjtu.foodtime.domain.model.ReportManagement.*;
import java.util.List;

/** Independent management port; authorization and transport remain replaceable. */
public interface ReportManagementRepository {
  default void decideReport(String id, String outcome, String publicReason, String internalNote) { throw new UnsupportedOperationException(); }
  default Page<Decision> adminReportDecisions(int page) { throw new UnsupportedOperationException(); }
  default Page<FollowUp> adminReportFollowUps(String report, int page) { throw new UnsupportedOperationException(); }
  default void replyToReport(String report, String content, String internalNote, List<String> evidence, String requestId) { throw new UnsupportedOperationException(); }
  default void performReportAction(String report, String action, String note) { throw new UnsupportedOperationException(); }
  default Page<Appeal> adminAppeals(String status, int page) { throw new UnsupportedOperationException(); }
  default Appeal adminAppeal(String id) { throw new UnsupportedOperationException(); }
  default Appeal startAppealReview(String id, long revision) { throw new UnsupportedOperationException(); }
  default Appeal resolveAppeal(String id, String outcome, String reason, String note, long revision) { throw new UnsupportedOperationException(); }
}
