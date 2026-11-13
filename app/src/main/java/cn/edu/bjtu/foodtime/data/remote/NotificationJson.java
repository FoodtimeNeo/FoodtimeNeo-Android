package cn.edu.bjtu.foodtime.data.remote;

import cn.edu.bjtu.foodtime.domain.model.Models.NotificationPreference;
import cn.edu.bjtu.foodtime.domain.model.Models.NotificationPreferences;
import cn.edu.bjtu.foodtime.domain.repository.RepositoryException;
import java.util.ArrayList;
import java.util.HashSet;
import org.json.JSONObject;

final class NotificationJson {
  private NotificationJson(){}
  static NotificationPreference preference(JSONObject data){
    if(data==null||data.optString("category","").isBlank()||!(data.opt("enabled") instanceof Boolean)||data.optLong("revision",0)<1)throw invalid();
    return new NotificationPreference(data.optString("category"),data.optBoolean("enabled"),data.optLong("revision"));
  }
  static NotificationPreferences preferences(JSONObject data){
    var rows=data.optJSONArray("items");if(rows==null)throw invalid();
    var items=new ArrayList<NotificationPreference>();var names=new HashSet<String>();
    for(int i=0;i<rows.length();i++){var item=preference(rows.optJSONObject(i));if(!names.add(item.category()))throw invalid();items.add(item);}
    if(!names.contains("message"))throw invalid();
    return new NotificationPreferences(items);
  }
  static RepositoryException invalid(){return new RepositoryException(502,"INVALID_RESPONSE","提醒设置响应异常，请重试",0);}
}
