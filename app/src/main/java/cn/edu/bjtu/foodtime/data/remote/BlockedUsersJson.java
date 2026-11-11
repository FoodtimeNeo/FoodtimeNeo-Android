package cn.edu.bjtu.foodtime.data.remote;

import cn.edu.bjtu.foodtime.domain.model.Models.*;
import cn.edu.bjtu.foodtime.domain.repository.RepositoryException;
import java.util.*;
import org.json.*;

final class BlockedUsersJson {
  static BlockedUserPage page(JSONObject data,int requested) {
    try {
      if(number(data,"page")!=requested||number(data,"size")!=20)throw new IllegalArgumentException();
      long total=number(data,"total");JSONArray items=data.getJSONArray("items");
      if(items.length()>20)throw new IllegalArgumentException();
      List<BlockedUser> rows=new ArrayList<>();Set<String> ids=new HashSet<>();
      for(int i=0;i<items.length();i++) {
        JSONObject row=items.getJSONObject(i);String id=row.getString("id");
        if(!UUID.fromString(id).toString().equalsIgnoreCase(id)||!ids.add(id))throw new IllegalArgumentException();
        Object name=row.get("displayName"),avatar=row.opt("avatarUrl");
        if(!(name instanceof String)||avatar!=null&&avatar!=JSONObject.NULL&&!(avatar instanceof String))throw new IllegalArgumentException();
        rows.add(new BlockedUser(id,(String)name,avatar instanceof String text?text:""));
      }
      if(total<rows.size()||rows.isEmpty()&&(requested-1L)*20<total)throw new IllegalArgumentException();
      return new BlockedUserPage(rows,total,requested,20);
    }catch(Exception invalid){throw new RepositoryException(502,"INVALID_BLOCKLIST_RESPONSE","黑名单响应异常，请重试",0);}
  }
  private static long number(JSONObject data,String field)throws JSONException {
    Object raw=data.get(field);
    if(!(raw instanceof Integer)&&!(raw instanceof Long))throw new IllegalArgumentException();
    long n=((Number)raw).longValue();if(n<0)throw new IllegalArgumentException();return n;
  }
  private BlockedUsersJson(){}
}
