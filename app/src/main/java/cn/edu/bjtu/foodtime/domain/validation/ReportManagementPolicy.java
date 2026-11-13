package cn.edu.bjtu.foodtime.domain.validation;

import cn.edu.bjtu.foodtime.domain.model.Models.CapabilitySnapshot;

/** UI projection of server capabilities; no role names or authorization decisions. */
public final class ReportManagementPolicy {
  private ReportManagementPolicy() {}
  public static boolean review(CapabilitySnapshot caps) { return caps.can("report.review"); }
  public static boolean appeals(CapabilitySnapshot caps) { return caps.can("appeal.review"); }
  public static boolean action(CapabilitySnapshot caps,String type) {
    return review(caps) && switch(type) {
      case "remove_post", "remove_comment" -> caps.can("moderation.review");
      case "disable_user", "restore_user" -> caps.can("user.manage");
      default -> false;
    };
  }
}
