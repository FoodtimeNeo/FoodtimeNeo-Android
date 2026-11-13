package cn.edu.bjtu.foodtime.domain.validation;

import cn.edu.bjtu.foodtime.domain.model.Models.CapabilitySnapshot;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class ReportManagementPolicyTest {
  private CapabilitySnapshot caps(String... names){return new CapabilitySnapshot(List.of(names),"test");}
  @Test public void managementDoesNotDependOnRoleNames(){assertTrue(ReportManagementPolicy.review(caps("report.review")));assertTrue(ReportManagementPolicy.appeals(caps("appeal.review")));}
  @Test public void emptyOrUnrelatedCapabilitiesDoNotExposeManagement(){assertFalse(ReportManagementPolicy.review(caps()));assertFalse(ReportManagementPolicy.appeals(caps("report.review")));assertFalse(ReportManagementPolicy.review(caps("user.manage")));}
  @Test public void punishmentRequiresReportAndSpecificActionCapability(){
    assertFalse(ReportManagementPolicy.action(caps("report.review"),"disable_user"));
    assertFalse(ReportManagementPolicy.action(caps("user.manage"),"disable_user"));
    assertTrue(ReportManagementPolicy.action(caps("report.review","user.manage"),"restore_user"));
    assertFalse(ReportManagementPolicy.action(caps("report.review","user.manage"),"remove_post"));
    assertTrue(ReportManagementPolicy.action(caps("report.review","moderation.review"),"remove_comment"));
    assertFalse(ReportManagementPolicy.action(caps("report.review","user.manage"),"unknown"));
  }
}
