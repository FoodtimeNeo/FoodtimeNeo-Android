package cn.edu.bjtu.foodtime.data.remote;

import cn.edu.bjtu.foodtime.domain.repository.RepositoryException;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class FeedbackJsonTest {
  private JSONObject row()throws Exception {
    return new JSONObject().put("id","00000000-0000-4000-8000-000000000001").put("type","function").put("status","processing")
        .put("content","反馈😊\n第二行").put("createdAt","2026-11-11T00:00:00Z").put("updatedAt","2026-11-11T00:00:00Z")
        .put("userDisplayName","测试用户").put("latestReply",JSONObject.NULL);
  }
  @Test public void parsesFeedbackAndKeepsNullableReplySeparateFromMissingRecords()throws Exception {
    var row=FeedbackJson.row(row());assertEquals("processing",row.status());assertEquals("",row.latestReply());assertTrue(row.content().contains("😊"));
  }
  @Test public void rejectsUnknownTypeStateAndMalformedIdentifiers()throws Exception {
    assertThrows(RepositoryException.class,()->FeedbackJson.row(row().put("type","unknown")));
    assertThrows(RepositoryException.class,()->FeedbackJson.row(row().put("status","unknown")));
    assertThrows(RepositoryException.class,()->FeedbackJson.row(row().put("id","invalid")));
  }
  @Test public void validatesPaginationAndCannotTurnMalformedDataIntoAnEmptyList()throws Exception {
    JSONObject data=new JSONObject().put("page",1).put("size",20).put("total",1).put("items",new JSONArray().put(row()));
    assertEquals(1,FeedbackJson.page(data,1).items().size());
    assertThrows(RepositoryException.class,()->FeedbackJson.page(data,2));
    assertThrows(RepositoryException.class,()->FeedbackJson.page(data.put("items",new JSONArray()),1));
  }
  @Test public void exportAndPartialImportResultPreserveChineseAndIssueRows()throws Exception {
    var export=FeedbackJson.export(new JSONObject().put("filename","反馈.csv").put("csv","\uFEFF反馈编号,反馈类型,反馈内容\r\n").put("count",0));assertTrue(export.csv().startsWith("\uFEFF"));
    var result=FeedbackJson.imported(new JSONObject().put("updated",1).put("skipped",1).put("issues",new JSONArray().put(new JSONObject().put("row",3).put("message","原内容已修改"))));
    assertEquals(1,result.updated());assertEquals(3,result.issues().get(0).row());
  }
}
