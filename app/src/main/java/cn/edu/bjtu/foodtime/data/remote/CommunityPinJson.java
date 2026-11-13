package cn.edu.bjtu.foodtime.data.remote;

import cn.edu.bjtu.foodtime.domain.model.CommunityPins;
import cn.edu.bjtu.foodtime.domain.repository.RepositoryException;
import java.util.ArrayList;
import org.json.*;

final class CommunityPinJson {
  static CommunityPins.Snapshot parse(JSONObject value) {
    try {
      if(value==null||!(value.opt("revision") instanceof Number)||!(value.opt("canManage") instanceof Boolean))throw new IllegalArgumentException();
      JSONArray items=value.getJSONArray("items");var rows=new ArrayList<CommunityPins.Entry>();
      for(int i=0;i<items.length();i++){var row=items.getJSONObject(i);rows.add(new CommunityPins.Entry(row.getString("id"),row.getString("title"),row.optString("userDisplayName",""),row.getString("status"),row.getInt("position")));}
      return new CommunityPins.Snapshot(value.getLong("revision"),rows,value.getBoolean("canManage"));
    }catch(JSONException|IllegalArgumentException invalid){throw new RepositoryException(502,"INVALID_RESPONSE","置顶列表响应异常，请刷新后重试",0);}
  }
  private CommunityPinJson(){}
}
