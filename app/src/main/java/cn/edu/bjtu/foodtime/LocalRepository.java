package cn.edu.bjtu.foodtime;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.util.*;
import java.math.BigDecimal;
import cn.edu.bjtu.foodtime.Models.*;

/** Offline simulator. Only approved content reaches public catalog reads. */
public class LocalRepository implements CatalogRepository {
    protected final SharedPreferences prefs;
    protected JSONObject db;
    public LocalRepository(Context context) {
        prefs=context.getSharedPreferences("foodtime_mock_v1",Context.MODE_PRIVATE);
        try { db=new JSONObject(prefs.getString("data", "{}")); }
        catch(JSONException e) { throw new IllegalStateException("本地数据无法读取",e); }
        if(!db.has("halls")) seed();
    }
    protected JSONArray rows(String key) { return db.optJSONArray(key); }
    protected static JSONObject obj(Object... pairs) {
        JSONObject o=new JSONObject();
        try { for(int i=0;i<pairs.length;i+=2) o.put((String)pairs[i],pairs[i+1]); }
        catch(JSONException e) { throw new IllegalStateException(e); } return o;
    }
    protected void put(String key,Object value) { try { db.put(key,value); } catch(JSONException e) { throw new IllegalStateException(e); } }
    protected void save() {
        if(!prefs.edit().putString("data",db.toString()).commit()) throw new IllegalStateException("保存失败，请重试");
    }
    protected static String id() { return UUID.randomUUID().toString(); }
    protected JSONObject find(String table,String id) {
        JSONArray a=rows(table); for(int i=0;i<a.length();i++) if(a.optJSONObject(i).optString("id").equals(id)) return a.optJSONObject(i);
        return null;
    }
    protected static boolean active(JSONObject o) { return o!=null && o.optBoolean("active",true); }
    private void seed() {
        for(String name:List.of("halls","stalls","dishes","reviews","photos","submissions","users","audits","favorites","likes")) put(name,new JSONArray());
        String[] names={"学一餐厅","明湖餐厅","东快餐厅","学二餐厅","学生三餐厅","清真餐厅","学四餐厅","学四风味餐厅","留园餐厅","教工餐厅","学苑大伙餐厅","学苑清真餐厅","学苑西餐厅","东区餐厅","东区清真餐厅","益民餐厅"};
        for(int i=0;i<names.length;i++) {
            String h=UUID.nameUUIDFromBytes(("hall"+i).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
            rows("halls").put(obj("id",h,"name",names[i],"coverImageUrl","asset:hall_"+(i%6+1)+".webp","active",true));
            String[] stalls={"西北面食","费大叔湘菜","家常小炒"};
            for(int j=0;j<3;j++) {
                String s=UUID.nameUUIDFromBytes((h+j).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
                rows("stalls").put(obj("id",s,"diningHallId",h,"name",stalls[j],"coverImageUrl","asset:stall_"+(j%2+1)+".webp","active",true));
                String[][] dishes={{"红烧牛肉面","酸汤米线","油泼扯面"},{"黑椒鸡腿饭","番茄肥牛饭","小炒黄牛肉饭"},{"牛肉煲仔饭","香菇滑鸡饭","宫保鸡丁盖饭"}};
                for(int k=0;k<3;k++) {
                    String d=UUID.nameUUIDFromBytes((s+k).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
                    rows("dishes").put(obj("id",d,"stallId",s,"name",dishes[j][k],"price",12+j*3+k*2,"description","","active",true));
                    if(k<2) rows("photos").put(obj("id",id(),"dishId",d,"imageUrl","asset:dish_"+(j*2+k+1)+".webp","userId","sample-student"));
                    if(k==0) rows("reviews").put(obj("id",id(),"dishId",d,"userId","sample-student","rating",5,"content","味道很扎实，分量也足，午饭又多了一个选择。","createdAt",System.currentTimeMillis()-86400000));
                }
            }
        }
        save();
    }
    public List<Hall> halls() { List<Hall> list=new ArrayList<>(); JSONArray a=rows("halls"); for(int i=0;i<a.length();i++) { Hall h=hall(a.optJSONObject(i).optString("id")); if(h.active()) list.add(h); } return list; }
    public Hall hall(String id) { JSONObject o=find("halls",id); return o==null?null:new Hall(id,o.optString("name"),o.optString("coverImageUrl"),active(o)); }
    public Stall stall(String id) { JSONObject o=find("stalls",id); return o==null?null:new Stall(id,o.optString("diningHallId"),o.optString("name"),o.optString("coverImageUrl"),active(o)); }
    public Dish dish(String id) { JSONObject o=find("dishes",id); return o==null?null:new Dish(id,o.optString("stallId"),o.optString("name"),new BigDecimal(o.optString("price","0")),o.optString("description"),active(o)); }
    public List<Stall> stalls(String hallId) {
        if(!active(find("halls",hallId))) throw new IllegalArgumentException("食堂不存在或已停用");
        List<Stall> list=new ArrayList<>(); JSONArray a=rows("stalls");
        for(int i=0;i<a.length();i++) { Stall s=stall(a.optJSONObject(i).optString("id")); if(s.diningHallId().equals(hallId)&&s.active()) list.add(s); } return list;
    }
    public List<Dish> dishes(String stallId) {
        Stall s=stall(stallId); if(s==null||!s.active()||!active(find("halls",s.diningHallId()))) throw new IllegalArgumentException("档口不存在或已停用");
        List<Dish> list=new ArrayList<>(); JSONArray a=rows("dishes");
        for(int i=0;i<a.length();i++) { Dish d=dish(a.optJSONObject(i).optString("id")); if(d.stallId().equals(stallId)&&d.active()) list.add(d); } return list;
    }
    public List<Review> reviews(String dishId) {
        List<Review> list=new ArrayList<>(); JSONArray a=rows("reviews");
        for(int i=0;i<a.length();i++) { JSONObject o=a.optJSONObject(i); if(dishId==null||o.optString("dishId").equals(dishId)) list.add(new Review(o.optString("id"),o.optString("dishId"),o.optString("userId"),o.optInt("rating"),o.optString("content"),o.optLong("createdAt"))); }
        list.sort(Comparator.comparingLong(Review::createdAt).reversed()); return list;
    }
    public List<Photo> photos(String dishId) {
        List<Photo> list=new ArrayList<>(); JSONArray a=rows("photos");
        for(int i=0;i<a.length();i++) { JSONObject o=a.optJSONObject(i); if(o.optString("dishId").equals(dishId)) list.add(new Photo(o.optString("id"),dishId,o.optString("imageUrl"),o.optString("userId"))); } return list;
    }
}
