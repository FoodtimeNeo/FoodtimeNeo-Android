package cn.edu.bjtu.foodtime.data.remote;

import cn.edu.bjtu.foodtime.domain.model.Models.*;
import cn.edu.bjtu.foodtime.domain.repository.RepositoryException;
import java.util.*;
import org.json.*;

/** Missing list data is an error, never an invented empty relationship list. */
final class FollowJson {
  static final int SIZE=20;
  static FollowPage page(JSONObject data,int requested) {
    try {
      if(number(data,"page")!=requested||number(data,"size")!=SIZE)throw new IllegalArgumentException();
      long total=number(data,"total");JSONArray items=data.getJSONArray("items");
      if(items.length()>SIZE)throw new IllegalArgumentException();
      List<FollowUser> rows=new ArrayList<>();Set<String> ids=new HashSet<>();
      for(int i=0;i<items.length();i++) {
        JSONObject row=items.getJSONObject(i);Object rawId=row.get("id"),flag=row.get("followedByMe");
        if(!(rawId instanceof String id)||!UUID.fromString(id).toString().equalsIgnoreCase(id)||!ids.add(id)||!(flag instanceof Boolean followed))throw new IllegalArgumentException();
        Object name=row.get("displayName"),avatar=row.opt("avatarUrl");
        if(!(name instanceof String)||avatar!=null&&avatar!=JSONObject.NULL&&!(avatar instanceof String))throw new IllegalArgumentException();
        rows.add(new FollowUser(id,(String)name,avatar instanceof String text?text:"",followed));
      }
      if(total<rows.size()||rows.isEmpty()&&(requested-1L)*SIZE<total)throw new IllegalArgumentException();
      return new FollowPage(rows,total,requested,requested*(long)SIZE<total);
    } catch(Exception invalid) {throw new RepositoryException(502,"INVALID_FOLLOW_RESPONSE","关注列表响应异常，请重试",0);}
  }
  static FollowState state(JSONObject data) {
    try {
      Object flag=data.get("followed");if(!(flag instanceof Boolean followed))throw new IllegalArgumentException();
      return new FollowState(followed,number(data,"followerCount"));
    }catch(Exception invalid){throw new RepositoryException(502,"INVALID_FOLLOW_RESPONSE","关注状态响应异常，请刷新核对",0);}
  }
  private static long number(JSONObject data,String field)throws JSONException {
    Object raw=data.get(field);if(!(raw instanceof Integer)&&!(raw instanceof Long))throw new IllegalArgumentException();
    long n=((Number)raw).longValue();if(n<0)throw new IllegalArgumentException();return n;
  }
  private FollowJson(){}
}
