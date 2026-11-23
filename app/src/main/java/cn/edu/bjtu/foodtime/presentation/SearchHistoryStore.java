package cn.edu.bjtu.foodtime.presentation;

import android.content.Context;
import java.util.*;
import org.json.JSONArray;

/** Local-only keywords, isolated by account; never included in search requests beyond the chosen term. */
final class SearchHistoryStore {
  private static final String FILE="dish-search-history";
  static List<String> read(Context context,String owner){
    if(owner==null||owner.isBlank())return List.of();
    try{JSONArray values=new JSONArray(context.getSharedPreferences(FILE,Context.MODE_PRIVATE).getString(owner,"[]"));List<String> result=new ArrayList<>();
      for(int i=0;i<values.length()&&result.size()<10;i++){String value=values.optString(i,"").strip();if(!value.isBlank()&&value.length()<=50&&!result.contains(value))result.add(value);}return result;
    }catch(org.json.JSONException failure){return List.of();}
  }
  static void remember(Context context,String owner,String term){
    if(owner==null||owner.isBlank()||term==null||term.isBlank()||term.length()>50)return;
    List<String> values=new ArrayList<>(read(context,owner));values.remove(term.strip());values.add(0,term.strip());if(values.size()>10)values=values.subList(0,10);save(context,owner,values);
  }
  static void remove(Context context,String owner,String term){List<String> values=new ArrayList<>(read(context,owner));values.remove(term);save(context,owner,values);}
  static void purge(Context context,String owner){if(owner!=null&&!owner.isBlank())context.getSharedPreferences(FILE,Context.MODE_PRIVATE).edit().remove(owner).apply();}
  private static void save(Context context,String owner,List<String> values){if(owner!=null&&!owner.isBlank())context.getSharedPreferences(FILE,Context.MODE_PRIVATE).edit().putString(owner,new JSONArray(values).toString()).apply();}
}
