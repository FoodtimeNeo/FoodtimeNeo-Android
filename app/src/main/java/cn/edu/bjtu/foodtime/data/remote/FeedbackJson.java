package cn.edu.bjtu.foodtime.data.remote;

import cn.edu.bjtu.foodtime.domain.model.FeedbackModels.*;
import cn.edu.bjtu.foodtime.domain.model.FeedbackModels;
import cn.edu.bjtu.foodtime.domain.repository.RepositoryException;
import java.time.Instant;
import java.util.*;
import org.json.*;

final class FeedbackJson {
  static Feedback row(JSONObject data) {
    try {
      String type=string(data,"type"),status=string(data,"status");
      if(!FeedbackModels.TYPES.contains(type)||!List.of("pending","processing","completed").contains(status))throw new IllegalArgumentException();
      return new Feedback(id(data),type,string(data,"content"),status,time(data,"createdAt"),time(data,"updatedAt"),
          string(data,"userDisplayName"),nullable(data,"latestReply"));
    }catch(Exception error){throw invalid();}
  }
  static Page page(JSONObject data,int requested) {
    try {
      long total=metadata(data,requested);JSONArray items=data.getJSONArray("items");List<Feedback> rows=new ArrayList<>();Set<String> seen=new HashSet<>();
      for(int i=0;i<items.length();i++){Feedback row=row(items.getJSONObject(i));if(!seen.add(row.id()))throw new IllegalArgumentException();rows.add(row);}
      checkSize(rows.size(),total,requested);return new Page(rows,total,requested,20);
    }catch(Exception error){throw invalid();}
  }
  static ReplyPage replies(JSONObject data,int requested) {
    try {
      long total=metadata(data,requested);JSONArray items=data.getJSONArray("items");List<Reply> rows=new ArrayList<>();Set<String> seen=new HashSet<>();
      for(int i=0;i<items.length();i++){JSONObject item=items.getJSONObject(i);String id=id(item);if(!seen.add(id))throw new IllegalArgumentException();rows.add(new Reply(id,string(item,"content"),time(item,"createdAt")));}
      checkSize(rows.size(),total,requested);return new ReplyPage(rows,total,requested,20);
    }catch(Exception error){throw invalid();}
  }
  static Export export(JSONObject data) {
    try {
      String name=string(data,"filename"),csv=string(data,"csv");long count=number(data,"count");
      if(count>Integer.MAX_VALUE||!name.endsWith(".csv")||name.contains("/")||name.contains("\\"))throw new IllegalArgumentException();
      return new Export(name,csv,(int)count);
    }catch(Exception error){throw invalid();}
  }
  static ImportResult imported(JSONObject data) {
    try {
      long updated=number(data,"updated"),skipped=number(data,"skipped");
      if(updated>Integer.MAX_VALUE||skipped>Integer.MAX_VALUE)throw new IllegalArgumentException();
      JSONArray items=data.getJSONArray("issues");List<ImportIssue> issues=new ArrayList<>();
      for(int i=0;i<items.length();i++){JSONObject item=items.getJSONObject(i);long row=number(item,"row");if(row<2||row>Integer.MAX_VALUE)throw new IllegalArgumentException();issues.add(new ImportIssue((int)row,string(item,"message")));}
      return new ImportResult((int)updated,(int)skipped,issues);
    }catch(Exception error){throw invalid();}
  }
  private static long metadata(JSONObject data,int requested)throws JSONException {
    if(number(data,"page")!=requested||number(data,"size")!=20)throw new IllegalArgumentException();return number(data,"total");
  }
  private static void checkSize(int size,long total,int page) {
    if(size>20||total<size||size==0&&(page-1L)*20<total)throw new IllegalArgumentException();
  }
  private static String id(JSONObject data)throws JSONException {
    String value=string(data,"id");if(!UUID.fromString(value).toString().equalsIgnoreCase(value))throw new IllegalArgumentException();return value;
  }
  private static long time(JSONObject data,String key)throws JSONException {return Instant.parse(string(data,key)).toEpochMilli();}
  private static String string(JSONObject data,String key)throws JSONException {
    Object raw=data.get(key);if(!(raw instanceof String value))throw new IllegalArgumentException();return value;
  }
  private static String nullable(JSONObject data,String key)throws JSONException {return data.isNull(key)?"":string(data,key);}
  private static long number(JSONObject data,String key)throws JSONException {
    Object raw=data.get(key);if(!(raw instanceof Integer)&&!(raw instanceof Long))throw new IllegalArgumentException();long value=((Number)raw).longValue();if(value<0)throw new IllegalArgumentException();return value;
  }
  private static RepositoryException invalid(){return new RepositoryException(502,"INVALID_FEEDBACK_RESPONSE","反馈数据异常，请重试",0);}
  private FeedbackJson(){}
}
