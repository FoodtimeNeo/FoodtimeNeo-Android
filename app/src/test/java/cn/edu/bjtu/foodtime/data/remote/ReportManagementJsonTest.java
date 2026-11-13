package cn.edu.bjtu.foodtime.data.remote;

import cn.edu.bjtu.foodtime.domain.repository.RepositoryException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class ReportManagementJsonTest {
  @Test public void decisionsKeepReportAndTargetIdentitySeparate() throws Exception {
    var value=ReportManagementJson.decision(new JSONObject().put("decisionId","decision").put("reportId","report").put("targetId","content").put("targetUserId","account").put("publicReason","公开理由").put("createdAt","2026-11-13T00:00:00Z"));
    assertEquals("report",value.reportId());assertEquals("decision",value.decisionId());assertEquals("account",value.targetUserId());assertEquals("content",value.targetId());assertTrue(value.createdAt()>0);
  }
  @Test public void staffFollowUpSeparatesInternalNoteAndEvidence() throws Exception {
    var value=ReportManagementJson.followUp(new JSONObject().put("id","reply").put("reportId","report").put("content","公开答复").put("internalNote","内部备注").put("evidenceUrls",new JSONArray().put("/api/v1/media/00000000-0000-4000-8000-000000000001")));
    assertEquals("公开答复",value.content());assertEquals("内部备注",value.internalNote());assertEquals(1,value.evidenceUrls().size());assertThrows(UnsupportedOperationException.class,()->value.evidenceUrls().add("x"));
  }
  @Test public void appealCarriesExpectedRevisionAndBothOutcomeTypes() throws Exception {
    var value=ReportManagementJson.appeal(new JSONObject().put("id","appeal").put("decisionId","decision").put("side","target").put("status","under_review").put("revision",7).put("outcome","rejected"));
    assertEquals(7,value.revision());assertEquals("target",value.side());assertEquals("under_review",value.status());assertEquals("rejected",value.outcome());assertEquals(0,value.resolvedAt());
  }
  @Test public void invalidPagesAreRejectedInsteadOfEmptySuccessfulLists() throws Exception {
    for(JSONObject data:new JSONObject[]{new JSONObject(),new JSONObject().put("items",new JSONArray()).put("page",0).put("size",20).put("total",1),new JSONObject().put("items",new JSONArray().put("bad-row")).put("page",1).put("size",20).put("total",1)})
      assertEquals("INVALID_RESPONSE",assertThrows(RepositoryException.class,()->ReportManagementJson.page(data,ReportManagementJson::decision)).code());
  }
  @Test public void paginationRetainsTotalAndStopsAtLastPage() throws Exception {
    var value=ReportManagementJson.page(new JSONObject().put("items",new JSONArray().put(new JSONObject().put("id","appeal").put("decisionId","decision"))).put("page",2).put("size",20).put("total",21),ReportManagementJson::appeal);
    assertEquals(21,value.total());assertEquals(2,value.page());assertFalse(value.hasMore());
  }
  @Test public void externalEvidenceAndMalformedTimeAreRejected() throws Exception {
    assertThrows(RepositoryException.class,()->ReportManagementJson.followUp(new JSONObject().put("id","reply").put("reportId","report").put("evidenceUrls",new JSONArray().put("https://example.test/private"))));
    assertThrows(RepositoryException.class,()->ReportManagementJson.decision(new JSONObject().put("decisionId","decision").put("reportId","report").put("createdAt","bad-time")));
  }
}
