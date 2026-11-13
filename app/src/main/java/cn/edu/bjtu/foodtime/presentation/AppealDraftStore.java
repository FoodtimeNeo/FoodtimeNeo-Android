package cn.edu.bjtu.foodtime.presentation;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.UUID;

/** Account and service ownership is immutable for each draft store instance. */
final class AppealDraftStore {
  private final SharedPreferences preferences;
  private final String prefix;

  AppealDraftStore(Context context, String service, String actor) {
    if (service == null || service.isBlank() || actor == null || actor.isBlank())
      throw new IllegalArgumentException("申诉草稿需要服务地址和账号");
    preferences = context.getSharedPreferences("appeal-drafts", Context.MODE_PRIVATE);
    prefix = "v2:" + service.length() + ":" + service + ":" + actor + ":";
  }

  String key(String decision) { return prefix + decision; }

  String read(String decision) { return preferences.getString(key(decision), ""); }

  void save(String decision, String content) {
    preferences.edit().putString(key(decision), content).apply();
  }

  String requestId(String decision) {
    String key = key(decision) + ":request";
    String request = preferences.getString(key, "");
    if (request.isBlank()) {
      request = UUID.randomUUID().toString();
      preferences.edit().putString(key, request).apply();
    }
    return request;
  }

  void clear(String decision) {
    var edit=preferences.edit().remove(key(decision)).remove(key(decision) + ":request");
    for(int i=0;i<3;i++)edit.remove(key(decision)+":evidence:"+i);edit.apply();
  }
  String evidence(String decision,int index){return preferences.getString(key(decision)+":evidence:"+index,"");}
  void evidence(String decision,int index,String path){preferences.edit().putString(key(decision)+":evidence:"+index,path).apply();}
  synchronized void uploaded(String decision,String previous,String uploaded){
    var edit=preferences.edit();for(int i=0;i<3;i++)if(previous.equals(evidence(decision,i)))edit.putString(key(decision)+":evidence:"+i,uploaded);
    edit.apply();
  }
}
