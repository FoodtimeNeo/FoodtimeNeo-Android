package cn.edu.bjtu.foodtime.data.remote;
import org.json.*;
import org.junit.Test;
import cn.edu.bjtu.foodtime.domain.repository.RepositoryException;
import static org.junit.Assert.*;
public class NotificationJsonTest {
  private JSONObject row(String category,boolean enabled,long revision)throws Exception{return new JSONObject().put("category",category).put("enabled",enabled).put("revision",revision);}
  @Test public void preservesExplicitOffAndServerRevision()throws Exception{var value=NotificationJson.preference(row("message",false,8));assertFalse(value.enabled());assertEquals(8,value.revision());}
  @Test public void rejectsMissingOrAmbiguousChoices()throws Exception{
    for(var data:new JSONObject[]{new JSONObject().put("category","message").put("revision",1),new JSONObject().put("category","message").put("enabled","false").put("revision",1),row("message",true,0)})assertThrows(RepositoryException.class,()->NotificationJson.preference(data));
  }
  @Test public void rejectsDuplicateOrAbsentMessageButAllowsNewCategories()throws Exception{
    assertThrows(RepositoryException.class,()->NotificationJson.preferences(new JSONObject().put("items",new JSONArray().put(row("message",true,1)).put(row("message",false,2)))));
    assertThrows(RepositoryException.class,()->NotificationJson.preferences(new JSONObject().put("items",new JSONArray())));
    var value=NotificationJson.preferences(new JSONObject().put("items",new JSONArray().put(row("message",true,2)).put(row("future",false,1))));assertEquals(2,value.items().size());
  }
}
