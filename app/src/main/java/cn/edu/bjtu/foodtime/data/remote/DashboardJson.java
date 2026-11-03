package cn.edu.bjtu.foodtime.data.remote;

import cn.edu.bjtu.foodtime.domain.model.Models.AdminDashboard;
import cn.edu.bjtu.foodtime.domain.model.Models.DashboardPoint;
import cn.edu.bjtu.foodtime.domain.repository.RepositoryException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONObject;

/** Strict mapping: unknown/malformed statistics must never become fabricated zeroes. */
final class DashboardJson {
  static AdminDashboard parse(JSONObject data, int days) {
    try {
      if (data == null || days < 1 || days > 31) throw new IllegalArgumentException();
      Object generated = data.get("generatedAt");
      if (!(generated instanceof String text)) throw new IllegalArgumentException();
      long generatedAt = Instant.parse(text).toEpochMilli();
      JSONArray rows = data.getJSONArray("trend");
      if (rows.length() != days) throw new IllegalArgumentException();
      var points = new ArrayList<DashboardPoint>();
      LocalDate previous = null;
      Long previousTotal = null;
      for (int i = 0; i < rows.length(); i++) {
        JSONObject row = rows.getJSONObject(i);
        Object rawDay = row.get("day");
        if (!(rawDay instanceof String dayText) || !dayText.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new IllegalArgumentException();
        LocalDate day = LocalDate.parse(dayText);
        if (previous != null && !previous.plusDays(1).equals(day)) throw new IllegalArgumentException();
        long total=count(row,"totalUsers"),added=count(row,"newUsers");
        if(total<added||(previousTotal!=null&&total!=Math.addExact(previousTotal,added)))throw new IllegalArgumentException();
        points.add(new DashboardPoint(day,total,added,count(row,"newReviews"),
            count(row,"newReplies"), count(row,"newPosts"), count(row,"onlinePeak")));
        previous = day;
        previousTotal = total;
      }
      long total=count(data,"totalUsers"),active=count(data,"registeredUsers");
      if(total<active||points.get(points.size()-1).totalUsers()!=total)throw new IllegalArgumentException();
      return new AdminDashboard(total,active,count(data,"onlineUsers"),
          count(data,"reviews"), count(data,"replies"), count(data,"posts"),
          count(data,"halls"), count(data,"stalls"), count(data,"dishes"), points, generatedAt);
    } catch (Exception invalid) {
      throw new RepositoryException(502,"DASHBOARD_INVALID_RESPONSE","统计响应不完整或格式异常，请重试",0);
    }
  }

  private static long count(JSONObject data, String field) throws org.json.JSONException {
    Object raw = data.get(field);
    if (!(raw instanceof Integer) && !(raw instanceof Long)) throw new IllegalArgumentException();
    long value = ((Number) raw).longValue();
    if (value < 0) throw new IllegalArgumentException();
    return value;
  }
  private DashboardJson() { }
}
