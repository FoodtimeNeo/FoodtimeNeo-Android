package cn.edu.bjtu.foodtime.presentation;

import android.app.*;
import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import cn.edu.bjtu.foodtime.data.remote.*;
import cn.edu.bjtu.foodtime.domain.repository.*;
import java.io.*;
import java.lang.reflect.Field;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import org.json.*;

/** Real Android JSON/HTTP plus native pagination UI against an in-process loopback server. */
public final class DishReviewPagingRunner extends Instrumentation {
  private final List<String> requests=new CopyOnWriteArrayList<>();
  private final AtomicBoolean failNext=new AtomicBoolean();
  private volatile boolean malformed,liked;
  private volatile int total=45;
  private volatile String role="user";
  private MainActivity activity;
  private int checks;
  private void check(boolean condition,String label) {if(!condition) throw new AssertionError(label);checks++;}
  private TextView find(View view,String value) {
    if(view.getVisibility()!=View.VISIBLE) return null;
    if(view instanceof TextView t&&t.getText().toString().equals(value)) return t;
    if(view instanceof ViewGroup g) for(int i=0;i<g.getChildCount();i++) {TextView t=find(g.getChildAt(i),value);if(t!=null) return t;}
    return null;
  }
  private EditText editor(View view) {
    if(view.getVisibility()!=View.VISIBLE) return null;
    if(view instanceof EditText e) return e;
    if(view instanceof ViewGroup g) for(int i=0;i<g.getChildCount();i++) {EditText e=editor(g.getChildAt(i));if(e!=null) return e;}
    return null;
  }
  private void click(String label) {runOnMainSync(() -> {TextView v=find(activity.root,label);if(v==null) throw new AssertionError("Missing "+label);v.performClick();});}
  private void await(BooleanSupplier ready) throws Exception {
    long end=System.currentTimeMillis()+10000;
    while(System.currentTimeMillis()<end) {waitForIdleSync();if(ready.getAsBoolean()) return;Thread.sleep(30);}
    throw new AssertionError("Timed out waiting for pagination");
  }
  private Object data(String path) throws Exception {
    if(path.equals("users/me/profile")) return new JSONObject("{\"id\":\"user\",\"email\":\"20260001@bjtu.edu.cn\",\"displayName\":\"测试用户\",\"status\":\"active\"}").put("role",role);
    if(path.equals("auth/csrf")) return new JSONObject().put("token","test");
    if(path.equals("dining-halls")) return new JSONArray("[{\"id\":\"hall\",\"name\":\"测试食堂\"}]");
    if(path.equals("dining-halls/hall/stalls")) return new JSONArray("[{\"id\":\"stall\",\"name\":\"测试档口\"}]");
    if(path.equals("dishes/dish")) return new JSONObject("{\"id\":\"dish\",\"name\":\"分页测试菜品\",\"stallId\":\"stall\",\"diningHallId\":\"hall\",\"price\":10}");
    if(path.equals("dishes/dish/rating")) return new JSONObject().put("averageScore",total==0?JSONObject.NULL:4).put("reviewCount",total);
    if(path.startsWith("dishes/dish/images?")) return new JSONObject().put("items",new JSONArray()).put("total",0);
    if(path.equals("reviews/r1/like")) {liked=true;return new JSONObject().put("likedByMe",true).put("likeCount",1).put("counterVersion",1);}
    if(path.equals("reviews/r1/replies?size=20")) return new JSONObject().put("items",new JSONArray()).put("total",0).put("hasMore",false).put("nextCursor","");
    if(path.startsWith("admin/catalog/")) return new JSONObject().put("items",new JSONArray()).put("total",0);
    if(path.startsWith("admin/moderation?")) {
      JSONArray rows=new JSONArray();
      if(path.contains("status=pending")) {
        rows.put(new JSONObject().put("id","submission1").put("type","dish-review-reply").put("targetId","dish").put("userId","author").put("name","待审核回复").put("content","待审正文").put("status","pending").put("aiRiskLevel","HIGH").put("aiConfidence",0.95).put("aiLabels",new JSONArray().put("广告").put("辱骂")).put("aiReason","请结合上下文检查。".repeat(12)).put("aiCheckedAt","2026-10-21T08:02:03Z"));
        rows.put(new JSONObject().put("id","submission2").put("type","review").put("targetId","dish").put("name","无分诊结论").put("status","pending").put("aiRiskLevel",JSONObject.NULL).put("aiConfidence",JSONObject.NULL).put("aiLabels",JSONObject.NULL));
      }
      return new JSONObject().put("items",rows).put("total",rows.length());
    }
    if(path.startsWith("admin/reviews?")) return new JSONObject().put("total",1).put("items",new JSONArray().put(new JSONObject().put("id","published").put("dishId","dish").put("userId","author").put("rating",4).put("content","已审核正文").put("aiRiskLevel","MEDIUM").put("aiConfidence",0).put("aiLabels",new JSONArray()).put("aiReason","需要复核")));
    if(path.startsWith("dishes/dish/reviews?")) {
      if(malformed) return new JSONObject().put("total",total);
      Map<String,String> query=new HashMap<>();for(String part:path.substring(path.indexOf('?')+1).split("&")) {String[] pair=part.split("=");query.put(pair[0],pair[1]);}
      int page=Integer.parseInt(query.get("page")),size=Integer.parseInt(query.get("size"));
      JSONArray rows=new JSONArray();
      for(int i=(page-1)*size+1;i<=Math.min(total,page*size);i++) rows.put(new JSONObject().put("id","r"+i).put("dishId","dish").put("userId","author"+i).put("userDisplayName","作者"+i).put("content","评论"+i).put("rating",4).put("createdAt","2026-10-21T00:00:00Z").put("likeCount",i==1&&liked?1:0).put("likedByMe",i==1&&liked).put("replyCount",0).put("counterVersion",i==1&&liked?1:0));
      return new JSONObject().put("items",rows).put("total",total).put("page",page).put("size",size);
    }
    throw new IllegalArgumentException("Unexpected path "+path);
  }
  private void serve(ServerSocket server) {
    while(!server.isClosed()) try(Socket socket=server.accept()) {
      var in=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));
      String[] first=in.readLine().split(" ");String path=first[1].substring("/api/v1/".length());requests.add(path);
      int length=0;String line;while((line=in.readLine())!=null&&!line.isEmpty()) if(line.toLowerCase(Locale.ROOT).startsWith("content-length:")) length=Integer.parseInt(line.substring(line.indexOf(':')+1).trim());
      for(int i=0;i<length;i++) in.read();
      boolean failure=path.contains("/reviews?page=2")&&failNext.getAndSet(false);
      byte[] bytes=(failure?new JSONObject().put("code","TEST_FAILURE").put("message","测试分页失败"):new JSONObject().put("code","OK").put("data",data(path))).toString().getBytes(StandardCharsets.UTF_8);
      var out=socket.getOutputStream();out.write(((failure?"HTTP/1.1 503 Service Unavailable":"HTTP/1.1 200 OK")+"\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: "+bytes.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));out.write(bytes);out.flush();
    } catch(Exception error) {if(!server.isClosed()) throw new IllegalStateException(error);}
  }
  @Override public void onCreate(Bundle args) {super.onCreate(args);start();}
  @Override public void onStart() {
    Bundle report=new Bundle();Object app=getTargetContext().getApplicationContext();Field field=null;Object original=null;
    ExecutorService pool=Executors.newSingleThreadExecutor();
    try(ServerSocket server=new ServerSocket(0,50,InetAddress.getByName("127.0.0.1"))) {
      pool.execute(() -> serve(server));String base="http://127.0.0.1:"+server.getLocalPort()+"/api/v1/";
      RemoteRepository repo=new RemoteRepository(base,new HttpTransport(base));repo.refresh(new ContentRequest(ContentRequest.Query.DISH_DETAIL,"dish"));
      check(repo.reviews("dish").size()==20&&repo.dishReviews("dish").total()==45,"Detail fetches only the first 20 public reviews");
      check(requests.stream().filter(p -> p.startsWith("dishes/dish/reviews?")).count()==1&&requests.contains("dishes/dish/reviews?page=1&size=20"),"No automatic next-page requests or size=100");
      repo.setReviewLike("r1",true);failNext.set(true);
      try {repo.loadMoreDishReviews("dish");throw new AssertionError("Expected failed page");}catch(RepositoryException expected) {check(expected.status()==503,"Page errors propagate");}
      check(repo.dishReviews("dish").page()==1&&repo.reviews("dish").size()==20,"Failed append preserves page number and existing comments");
      repo.loadMoreDishReviews("dish");check(repo.reviews("dish").size()==40&&repo.dishReviews("dish").page()==2,"Retry loads the same second page exactly once");
      check(repo.reviews("dish").get(0).likedByMe(),"Appending preserves authoritative cached likes");
      repo.loadMoreDishReviews("dish");check(repo.reviews("dish").size()==45&&!repo.dishReviews("dish").hasMore(),"Final page stops at the reported total");
      int before=requests.size();repo.loadMoreDishReviews("dish");check(requests.size()==before,"No extra network request after the final page");
      malformed=true;try {repo.loadDishReviews("dish");throw new AssertionError("Expected invalid page");}catch(RepositoryException expected) {check(repo.reviews("dish").size()==45,"Malformed refresh preserves visible cached data");}malformed=false;

      field=app.getClass().getDeclaredField("repository");field.setAccessible(true);original=field.get(app);field.set(app,repo);
      activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));await(() -> activity.root!=null);
      runOnMainSync(() -> {activity.repo=repo;activity.go("detail","dish");});await(() -> find(activity.root,"已显示 20 / 45 条评论")!=null);
      check(find(activity.root,"评论21")==null,"Native screen renders only requested comments");
      click("回复 · 0");await(() -> find(activity.root,"写下你的回复…")!=null);click("写下你的回复…");runOnMainSync(() -> editor(activity.root).setText("分页时保留的草稿"));
      failNext.set(true);click("加载更多评论");await(() -> find(activity.root,"测试分页失败")!=null);
      check(find(activity.root,"评论20")!=null&&find(activity.root,"评论21")==null,"Failed page leaves the original list usable");
      click("加载更多评论");await(() -> find(activity.root,"已显示 40 / 45 条评论")!=null);
      check(editor(activity.root)!=null&&editor(activity.root).getText().toString().equals("分页时保留的草稿"),"Appending does not remount existing reply editors");
      click("加载更多评论");await(() -> find(activity.root,"已显示 45 / 45 条评论")!=null);
      check(find(activity.root,"加载更多评论")==null,"Native load-more control disappears at the end");
      total=0;click("刷新评论");await(() -> find(activity.root,"暂无评论")!=null);
      check(find(activity.root,"暂无评分")!=null&&find(activity.root,"0 条评价 · 实时统计")!=null,"Refreshing resets the page and updates server-derived score/count");
      check(find(activity.root,"已显示 0 / 0 条评论")!=null,"Empty page has a stable count and refresh action");
      role="admin";runOnMainSync(() -> activity.selectTab("admin"));await(() -> find(activity.root,"审核投稿")!=null);
      runOnMainSync(() -> activity.go("admin-audit",""));await(() -> find(activity.root,"AI 分诊：高风险 · 置信度 95%")!=null);
      check(find(activity.root,"标签：广告、辱骂")!=null&&find(activity.root,"诊断时间：2026-10-21 16:02")!=null,"Moderation maps AI labels and converts diagnosis time to Shanghai");
      check(find(activity.root,"AI 分诊：暂无结论")!=null,"Null verdict remains an explicit unavailable state");
      check(find(activity.root,"通过")!=null&&find(activity.root,"驳回")!=null,"AI assessment leaves manual decisions available");
      check(repo.submissions(null).stream().filter(s -> s.id().equals("submission1")).findFirst().orElseThrow().ai().labels().size()==2,"Real HTTP JSON populates the structured AI model");
      runOnMainSync(() -> activity.go("admin-reviews",""));await(() -> find(activity.root,"AI 分诊：需复核 · 置信度 0%")!=null);
      check(repo.reviews(null).get(0).ai().confidence().signum()==0,"Published-review AI retains zero confidence instead of treating it as missing");
      role="user";total=1;runOnMainSync(() -> {activity.selectTab("dining");activity.go("detail","dish");});await(() -> find(activity.root,"评论1")!=null);
      check(find(activity.root,"AI 分诊：需复核 · 置信度 0%")==null&&find(activity.root,"AI 分诊：暂无结论")==null,"Public dish cards do not render moderation-only AI controls");
      report.putString("stream","Dish review paging PASS: "+checks+" native HTTP/UI checks; no real Backend writes\n");finish(0,report);
    } catch(Throwable error) {
      StringWriter stack=new StringWriter();error.printStackTrace(new PrintWriter(stack));report.putString("stream","Dish review paging FAIL after "+checks+" checks\n"+stack);finish(1,report);
    } finally {
      pool.shutdownNow();
      if(activity!=null) runOnMainSync(activity::finish);
      if(field!=null) try {field.set(app,original);}catch(IllegalAccessException ignored) {}
    }
  }
}
