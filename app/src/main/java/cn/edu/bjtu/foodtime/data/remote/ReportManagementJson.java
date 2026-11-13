package cn.edu.bjtu.foodtime.data.remote;

import cn.edu.bjtu.foodtime.domain.model.ReportManagement.*;
import cn.edu.bjtu.foodtime.domain.repository.RepositoryException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.json.JSONArray;
import org.json.JSONObject;

final class ReportManagementJson {
  private ReportManagementJson() {}
  static <T> Page<T> page(JSONObject data, Function<JSONObject,T> mapper) {
    JSONArray rows = data.optJSONArray("items");
    int page = data.optInt("page",0), size = data.optInt("size",0);
    long total = data.optLong("total",-1);
    if (rows == null || page < 1 || size < 1 || total < 0) throw invalid();
    List<T> items = new ArrayList<>();
    for (int i=0;i<rows.length();i++) {
      if (rows.optJSONObject(i) == null) throw invalid();
      items.add(mapper.apply(rows.optJSONObject(i)));
    }
    return new Page<>(items,total,page,size);
  }
  static Decision decision(JSONObject j) {
    return new Decision(required(j,"decisionId"),required(j,"reportId"),text(j,"outcome"),text(j,"publicReason"),
        text(j,"targetType"),text(j,"targetId"),text(j,"targetUserId"),time(j,"createdAt"));
  }
  static FollowUp followUp(JSONObject j) {
    return new FollowUp(required(j,"id"),required(j,"reportId"),text(j,"authorRole"),text(j,"authorName"),
        text(j,"content"),text(j,"internalNote"),evidence(j),time(j,"createdAt"));
  }
  static Appeal appeal(JSONObject j) {
    return new Appeal(required(j,"id"),required(j,"decisionId"),text(j,"appellantId"),text(j,"appellantName"),
        text(j,"side"),text(j,"content"),text(j,"status"),text(j,"outcome"),text(j,"publicReason"),
        text(j,"internalNote"),j.optLong("revision"),time(j,"createdAt"),time(j,"resolvedAt"),evidence(j));
  }
  private static List<String> evidence(JSONObject j) {
    JSONArray data=j.optJSONArray("evidenceUrls");
    List<String> result=new ArrayList<>();
    if(data!=null)for(int i=0;i<data.length();i++) {
      String url=data.optString(i,"");
      if(!url.matches("/api/v1/media/[0-9a-fA-F-]{36}"))throw invalid();
      result.add(url);
    }
    return result;
  }
  private static String text(JSONObject j,String key) { return j.isNull(key)?"":j.optString(key,""); }
  private static String required(JSONObject j,String key) { String value=text(j,key);if(value.isBlank())throw invalid();return value; }
  private static long time(JSONObject j,String key) {
    String value=text(j,key);if(value.isBlank())return 0;
    try{return Instant.parse(value).toEpochMilli();}catch(RuntimeException failure){throw invalid();}
  }
  private static RepositoryException invalid() {return new RepositoryException(502,"INVALID_RESPONSE","管理记录返回格式异常，请重试",0);}
}
