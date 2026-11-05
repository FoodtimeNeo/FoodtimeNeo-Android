package cn.edu.bjtu.foodtime.data.remote;

import cn.edu.bjtu.foodtime.domain.repository.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.json.*;
import org.junit.*;
import static org.junit.Assert.*;

/** Real loopback HTTP exercises the production transport/repository. */
public class RecommendationRepositoryTest {
  private ServerSocket server; private ExecutorService workers; private RemoteRepository repo;
  private final AtomicReference<String> actor=new AtomicReference<>("one"),role=new AtomicReference<>("user"),status=new AtomicReference<>("active"),response=new AtomicReference<>();
  private final AtomicInteger responseStatus=new AtomicInteger(200),requests=new AtomicInteger();
  private final AtomicInteger profileStatus=new AtomicInteger(200);
  private volatile CountDownLatch slowStarted,releaseSlow;
  @Before public void start() throws Exception {
    response.set(valid().toString());server=new ServerSocket(0,50,InetAddress.getByName("127.0.0.1"));
    workers=Executors.newCachedThreadPool();
    workers.submit(()->{
      while(!server.isClosed())try {Socket connection=server.accept();workers.submit(()->handle(connection));}
      catch(IOException closed){if(!server.isClosed())throw new UncheckedIOException(closed);}
    });
    String base="http://127.0.0.1:"+server.getLocalPort()+"/api/v1/";repo=new RemoteRepository(base,new HttpTransport(base));
  }
  private void handle(Socket connection) {
    try(connection) {
      connection.setSoTimeout(5000);
      var in=new BufferedReader(new InputStreamReader(connection.getInputStream(),StandardCharsets.UTF_8));
      String first=in.readLine();if(first==null)return;
      String line;while((line=in.readLine())!=null&&!line.isEmpty()){}
      String path=first.split(" ")[1],body="{}";int code=200;
      if(path.equals("/api/v1/users/me/profile")){body=new JSONObject().put("id",actor.get()).put("email","123@bjtu.edu.cn").put("displayName","test").put("role",role.get()).put("status",status.get()).toString();code=profileStatus.get();}
      else if(path.equals("/api/v1/recommendations/daily")) {
        int index=requests.incrementAndGet();body=response.get();code=responseStatus.get();
        if(index==1&&slowStarted!=null){slowStarted.countDown();try{releaseSlow.await(10,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
      } else if(!path.equals("/api/v1/auth/logout")&&!path.equals("/api/v1/auth/csrf"))code=404;
      byte[] bytes=("{\"code\":\""+(code==200?"OK":"DENIED")+"\",\"message\":\"test\",\"data\":"+body+"}").getBytes(StandardCharsets.UTF_8);
      var out=connection.getOutputStream();
      out.write(("HTTP/1.1 "+code+" Test\r\nContent-Type: application/json\r\nContent-Length: "+bytes.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));out.write(bytes);out.flush();
    }catch(IOException | JSONException closed){ /* A canceled request may close the test socket. */ }
  }
  @After public void stop() throws Exception {if(releaseSlow!=null)releaseSlow.countDown();server.close();workers.shutdownNow();}
  private void refresh(){repo.refresh(new ContentRequest(ContentRequest.Query.DAILY_RECOMMENDATIONS,""));}
  static JSONObject valid() throws JSONException {
    return new JSONObject("""
      {"date":"2026-11-04","algorithmVersion":"FT-HybridRec-v2","total":1,"items":[{
      "dishId":"00000000-0000-0000-0000-000000000001","dishName":"真实推荐",
      "diningHallId":"00000000-0000-0000-0000-000000000002","diningHallName":"真实食堂",
      "stallId":"00000000-0000-0000-0000-000000000003","stallName":"真实档口",
      "averageRating":null,"reviewCount":0,"imageUrl":null,"recommendationType":"EXPLORE",
      "recommendationScore":0.83,"reasonCode":"DAILY_DISCOVERY","reason":"今日探索"}]}
      """);
  }
  @Test public void realEndpointAndNullableFields() {
    assertNull(repo.dailyRecommendations());refresh();assertEquals(1,requests.get());
    var data=repo.dailyRecommendations();assertEquals(LocalDate.parse("2026-11-04"),data.date());
    var dish=data.items().get(0);assertNull(dish.averageRating());assertNull(dish.imageUrl());
    assertEquals("真实食堂",dish.diningHallName());assertEquals("真实档口",dish.stallName());
    assertEquals("00000000-0000-0000-0000-000000000001",dish.dishId());
    assertEquals(.83,dish.recommendationScore(),.0001);assertEquals("今日探索",dish.reason());
    assertThrows(UnsupportedOperationException.class,()->data.items().clear());
  }
  @Test public void visitorCanReadAndEmptyIsReal() throws Exception {
    role.set("visitor");response.set(valid().put("total",0).put("items",new JSONArray()).toString());refresh();
    assertTrue(repo.dailyRecommendations().items().isEmpty());assertEquals(1,requests.get());
  }
  @Test public void serverDateIsNotDeviceTimezone() {
    TimeZone before=TimeZone.getDefault();try{TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));refresh();assertEquals("2026-11-04",repo.dailyRecommendations().date().toString());}finally{TimeZone.setDefault(before);}
  }
  @Test public void malformedCannotBecomeFakeData() throws Exception {
    var cases=new ArrayList<JSONObject>();cases.add(valid().put("date","2026-02-30"));cases.add(valid().put("total",2));
    var duplicate=valid();duplicate.getJSONArray("items").put(duplicate.getJSONArray("items").get(0));duplicate.put("total",2);cases.add(duplicate);
    for(String key:new String[]{"dishId","diningHallId","stallId","averageRating","imageUrl","reason"}){var data=valid();data.getJSONArray("items").getJSONObject(0).remove(key);cases.add(data);}
    for(String key:new String[]{"averageRating","reviewCount","recommendationScore"}){var data=valid();data.getJSONArray("items").getJSONObject(0).put(key,-1);cases.add(data);}
    var stringRating=valid();stringRating.getJSONArray("items").getJSONObject(0).put("averageRating","4.5");cases.add(stringRating);
    for(var data:cases)assertEquals("RECOMMENDATION_INVALID_RESPONSE",assertThrows(RepositoryException.class,()->RecommendationJson.parse(data)).code());
  }
  @Test public void errorsClearSnapshotAndRetryRecovers(){refresh();responseStatus.set(503);assertThrows(RepositoryException.class,this::refresh);assertNull(repo.dailyRecommendations());responseStatus.set(200);refresh();assertNotNull(repo.dailyRecommendations());}
  @Test public void deniedProfileClearsPreviousSnapshot(){refresh();profileStatus.set(403);assertThrows(RepositoryException.class,this::refresh);assertNull(repo.dailyRecommendations());assertEquals(1,requests.get());}
  @Test public void logoutClearsSessionAndRecommendations(){refresh();repo.logout();assertNull(repo.currentUser());assertNull(repo.dailyRecommendations());}
  @Test public void unauthorizedClearsSession(){refresh();responseStatus.set(401);assertThrows(RepositoryException.class,this::refresh);assertNull(repo.currentUser());assertNull(repo.dailyRecommendations());}
  @Test public void oldAccountCannotPublish() throws Exception {late(true);}
  @Test public void olderRequestCannotReplaceNewer() throws Exception {late(false);}
  private void late(boolean switchAccount) throws Exception {
    slowStarted=new CountDownLatch(1);releaseSlow=new CountDownLatch(1);var old=workers.submit(this::refresh);assertTrue(slowStarted.await(5,TimeUnit.SECONDS));
    if(switchAccount)actor.set("two");var next=valid();next.getJSONArray("items").getJSONObject(0).put("dishName","最新推荐");response.set(next.toString());refresh();releaseSlow.countDown();
    assertThrows(ExecutionException.class,()->old.get(5,TimeUnit.SECONDS));assertEquals("最新推荐",repo.dailyRecommendations().items().get(0).dishName());
  }
}
