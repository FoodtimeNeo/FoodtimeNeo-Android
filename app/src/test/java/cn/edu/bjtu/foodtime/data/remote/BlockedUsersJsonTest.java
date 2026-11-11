package cn.edu.bjtu.foodtime.data.remote;

import cn.edu.bjtu.foodtime.domain.repository.RepositoryException;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class BlockedUsersJsonTest {
  private JSONObject row()throws Exception {
    return new JSONObject().put("id","00000000-0000-4000-8000-000000000001")
        .put("displayName","测试用户").put("avatarUrl",JSONObject.NULL);
  }
  private JSONObject page(int page,long total,JSONArray rows)throws Exception {
    return new JSONObject().put("page",page).put("size",20).put("total",total).put("items",rows);
  }
  @Test public void parsesRowsAndPaginationAndAllowsNullableAvatar()throws Exception {
    var result=BlockedUsersJson.page(page(1,21,new JSONArray().put(row())),1);
    assertEquals("测试用户",result.items().get(0).displayName());assertEquals("",result.items().get(0).avatarUrl());
    assertTrue(result.hasMore());assertEquals(21,result.total());
  }
  @Test public void emptyPageAfterConcurrentRemovalIsValid()throws Exception {
    assertTrue(BlockedUsersJson.page(page(2,20,new JSONArray()),2).items().isEmpty());
  }
  @Test public void rejectsMissingOrWrongPageMetadataAndDuplicateIds()throws Exception {
    assertThrows(RepositoryException.class,()->BlockedUsersJson.page(new JSONObject(),1));
    assertThrows(RepositoryException.class,()->BlockedUsersJson.page(page(2,21,new JSONArray().put(row())),1));
    assertThrows(RepositoryException.class,()->BlockedUsersJson.page(page(1,2,new JSONArray().put(row()).put(row())),1));
  }
  @Test public void rejectsFalseEmptySuccessAndMalformedRows()throws Exception {
    assertThrows(RepositoryException.class,()->BlockedUsersJson.page(page(1,1,new JSONArray()),1));
    JSONObject invalid=row().put("id","not-a-user-id");
    assertThrows(RepositoryException.class,()->BlockedUsersJson.page(page(1,1,new JSONArray().put(invalid)),1));
  }
}
