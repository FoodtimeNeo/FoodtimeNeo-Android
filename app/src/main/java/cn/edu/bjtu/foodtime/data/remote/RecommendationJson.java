package cn.edu.bjtu.foodtime.data.remote;

import cn.edu.bjtu.foodtime.domain.model.Models.*;
import cn.edu.bjtu.foodtime.domain.repository.RepositoryException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.json.JSONObject;

final class RecommendationJson {
  static DailyRecommendations parse(JSONObject data) {
    try {
      String date=text(data,"date");
      if(!date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))throw new IllegalArgumentException();
      LocalDate day=LocalDate.parse(date);
      String version=text(data,"algorithmVersion");
      var rows=data.getJSONArray("items");
      if(count(data,"total")!=rows.length())throw new IllegalArgumentException();
      var items=new ArrayList<RecommendedDish>();var seen=new HashSet<String>();
      for(int i=0;i<rows.length();i++) {
        var row=rows.getJSONObject(i);String id=id(row,"dishId");
        if(!seen.add(id))throw new IllegalArgumentException();
        Object raw=row.get("averageRating");BigDecimal rating=null;
        if(raw!=JSONObject.NULL) {double value=number(raw);if(value<1||value>5)throw new IllegalArgumentException();rating=BigDecimal.valueOf(value);}
        Object image=row.get("imageUrl");if(image!=JSONObject.NULL&&!(image instanceof String))throw new IllegalArgumentException();
        String type=text(row,"recommendationType"),reason=text(row,"reasonCode");
        if(!Set.of("QUALITY","EXPLORE").contains(type)||!Set.of("GOOD_REPUTATION","RECENTLY_POPULAR","DAILY_DISCOVERY","LESS_EXPOSED").contains(reason))throw new IllegalArgumentException();
        double score=number(row.get("recommendationScore"));if(score<0||score>1)throw new IllegalArgumentException();
        items.add(new RecommendedDish(id,text(row,"dishName"),id(row,"diningHallId"),text(row,"diningHallName"),
            id(row,"stallId"),text(row,"stallName"),rating,count(row,"reviewCount"),image==JSONObject.NULL?null:(String)image,
            type,score,reason,text(row,"reason")));
      }
      return new DailyRecommendations(day,version,items);
    }catch(Exception invalid){throw new RepositoryException(502,"RECOMMENDATION_INVALID_RESPONSE","今日推荐数据异常，请重试",0);}
  }
  private static String text(JSONObject row,String key)throws org.json.JSONException {
    Object raw=row.get(key);if(!(raw instanceof String s)||s.isBlank())throw new IllegalArgumentException();return s;
  }
  private static String id(JSONObject row,String key)throws org.json.JSONException {
    String s=text(row,key);if(!s.matches("(?i)[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}"))throw new IllegalArgumentException();return s;
  }
  private static double number(Object raw) {
    if(!(raw instanceof Number n)||!Double.isFinite(n.doubleValue()))throw new IllegalArgumentException();return n.doubleValue();
  }
  private static long count(JSONObject row,String key)throws org.json.JSONException {
    Object raw=row.get(key);if(!(raw instanceof Integer)&&!(raw instanceof Long))throw new IllegalArgumentException();
    long value=((Number)raw).longValue();if(value<0)throw new IllegalArgumentException();return value;
  }
  private RecommendationJson(){}
}
