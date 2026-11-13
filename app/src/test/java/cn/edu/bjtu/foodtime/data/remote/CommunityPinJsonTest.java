package cn.edu.bjtu.foodtime.data.remote;

import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
import cn.edu.bjtu.foodtime.domain.repository.RepositoryException;

public class CommunityPinJsonTest {
  private JSONObject row(String id,int position,String status)throws Exception{return new JSONObject().put("id",id).put("title","食记").put("userDisplayName","同学").put("status",status).put("position",position);}
  private JSONObject snapshot(JSONArray rows)throws Exception{return new JSONObject().put("revision",7).put("items",rows).put("canManage",true);}
  @Test public void hiddenEntriesKeepTheirPositionAndCapacity()throws Exception{var result=CommunityPinJson.parse(snapshot(new JSONArray().put(row("a",1,"pending")).put(row("b",2,"approved"))));assertEquals(2,result.items().size());assertEquals("pending",result.items().get(0).status());assertEquals(7,result.revision());}
  @Test public void duplicateSlotsAndIdsCannotProduceAnEditableSnapshot()throws Exception{assertThrows(RepositoryException.class,()->CommunityPinJson.parse(snapshot(new JSONArray().put(row("a",1,"approved")).put(row("b",1,"approved")))));assertThrows(RepositoryException.class,()->CommunityPinJson.parse(snapshot(new JSONArray().put(row("a",1,"approved")).put(row("a",2,"approved")))));}
  @Test public void malformedBoundsAndMissingMetadataAreRejected()throws Exception{assertThrows(RepositoryException.class,()->CommunityPinJson.parse(snapshot(new JSONArray().put(row("a",4,"approved")))));var missing=snapshot(new JSONArray());missing.remove("revision");assertThrows(RepositoryException.class,()->CommunityPinJson.parse(missing));}
}
