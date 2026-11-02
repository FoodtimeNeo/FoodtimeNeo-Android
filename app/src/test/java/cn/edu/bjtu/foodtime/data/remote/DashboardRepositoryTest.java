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
public class DashboardRepositoryTest {
  private ServerSocket server; private ExecutorService workers; private RemoteRepository repo;
  private final AtomicReference<String> actor=new AtomicReference<>("one"),role=new AtomicReference<>("superadmin"),status=new AtomicReference<>("active"),response=new AtomicReference<>();
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
      else if(path.equals("/api/v1/admin/dashboard?days=7")) {
        int index=requests.incrementAndGet();body=response.get();code=responseStatus.get();
        if(index==1&&slowStarted!=null){slowStarted.countDown();try{releaseSlow.await(10,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
      } else code=404;
      byte[] bytes=("{\"code\":\""+(code==200?"OK":"DENIED")+"\",\"message\":\"test\",\"data\":"+body+"}").getBytes(StandardCharsets.UTF_8);
      var out=connection.getOutputStream();
      out.write(("HTTP/1.1 "+code+" Test\r\nContent-Type: application/json\r\nContent-Length: "+bytes.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));out.write(bytes);out.flush();
    }catch(IOException | JSONException closed){ /* A canceled request may close the test socket. */ }
  }
  @After public void stop() throws Exception {if(releaseSlow!=null)releaseSlow.countDown();server.close();workers.shutdownNow();}
  private void refresh(){repo.refresh(new ContentRequest(ContentRequest.Query.ADMIN_DASHBOARD,""));}
  static JSONObject valid() throws JSONException {
    var data=new JSONObject();for(String f:new String[]{"registeredUsers","onlineUsers","reviews","replies","posts","halls","stalls","dishes"})data.put(f,271);
    data.put("generatedAt","2026-11-02T03:04:05.123456Z");var trend=new JSONArray();
    for(int i=0;i<7;i++)trend.put(new JSONObject().put("day",LocalDate.parse("2026-10-27").plusDays(i).toString()).put("newUsers",i).put("newReviews",i+2).put("newReplies",i+3).put("newPosts",i+4).put("onlinePeak",i+5));
    return data.put("trend",trend);
  }
  @Test public void readsTheEndpointAndAllFieldsWithoutPreview(){
    assertNull(repo.adminDashboard());refresh();var d=repo.adminDashboard();assertEquals(1,requests.get());
    assertEquals(271,d.registeredUsers());assertEquals(271,d.onlineUsers());assertEquals(271,d.reviews());assertEquals(271,d.replies());assertEquals(271,d.posts());assertEquals(271,d.halls());assertEquals(271,d.stalls());assertEquals(271,d.dishes());
    assertEquals(LocalDate.parse("2026-11-02"),d.trend().get(6).day());assertEquals(11,d.trend().get(6).onlinePeak());assertEquals(Instant.parse("2026-11-02T03:04:05.123456Z").toEpochMilli(),d.generatedAt());
  }
  @Test public void timezoneCannotMoveBusinessDate(){TimeZone old=TimeZone.getDefault();try{TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));refresh();assertEquals("2026-11-02",repo.adminDashboard().trend().get(6).day().toString());}finally{TimeZone.setDefault(old);}}
  @Test public void actualZeroValuesRemainZero() throws Exception {var d=valid();for(String f:new String[]{"registeredUsers","onlineUsers","reviews","replies","posts","halls","stalls","dishes"})d.put(f,0);response.set(d.toString());refresh();assertEquals(0,repo.adminDashboard().registeredUsers());}
  @Test public void malformedFieldsNeverBecomeZeroes() throws Exception {
    var invalid=new ArrayList<JSONObject>();var missing=valid();missing.remove("registeredUsers");invalid.add(missing);
    invalid.add(valid().put("onlineUsers",-1));invalid.add(valid().put("posts",1.5));invalid.add(valid().put("halls","2"));invalid.add(valid().put("generatedAt","bad"));invalid.add(valid().put("trend",new JSONArray()));
    var duplicate=valid();duplicate.getJSONArray("trend").getJSONObject(1).put("day","2026-10-27");invalid.add(duplicate);
    var badDay=valid();badDay.getJSONArray("trend").getJSONObject(0).put("day","2026-02-30");invalid.add(badDay);
    for(var d:invalid)assertEquals("DASHBOARD_INVALID_RESPONSE",assertThrows(RepositoryException.class,()->DashboardJson.parse(d,7)).code());
  }
  @Test public void failedRefreshRetainsOnlyTheLastSuccessfulSnapshot(){refresh();var old=repo.adminDashboard();responseStatus.set(503);assertThrows(RepositoryException.class,this::refresh);assertSame(old,repo.adminDashboard());}
  @Test public void firstFailureHasNoSnapshot(){responseStatus.set(404);assertThrows(RepositoryException.class,this::refresh);assertNull(repo.adminDashboard());}
  @Test public void forbiddenClearsSnapshot(){refresh();responseStatus.set(403);assertEquals(403,assertThrows(RepositoryException.class,this::refresh).status());assertNull(repo.adminDashboard());}
  @Test public void deniedProfileClearsSnapshotBeforeDashboardRequest(){refresh();profileStatus.set(403);assertEquals(403,assertThrows(RepositoryException.class,this::refresh).status());assertNull(repo.adminDashboard());assertEquals(1,requests.get());}
  @Test public void roleAndStatusChangesClearSnapshot(){refresh();role.set("admin");assertThrows(RepositoryException.class,this::refresh);assertNull(repo.adminDashboard());assertEquals(1,requests.get());role.set("superadmin");status.set("disabled");assertThrows(RepositoryException.class,this::refresh);assertNull(repo.adminDashboard());}
  @Test public void unauthorizedClearsSession(){refresh();responseStatus.set(401);assertThrows(RepositoryException.class,this::refresh);assertNull(repo.currentUser());assertNull(repo.adminDashboard());}
  @Test public void oldAccountReplyCannotReplaceNewAccount() throws Exception {lateReply(true);}
  @Test public void oldRefreshCannotReplaceNewerRefresh() throws Exception {lateReply(false);}
  private void lateReply(boolean switchAccount) throws Exception {
    slowStarted=new CountDownLatch(1);releaseSlow=new CountDownLatch(1);var old=workers.submit(this::refresh);assertTrue(slowStarted.await(5,TimeUnit.SECONDS));
    if(switchAccount)actor.set("two");response.set(valid().put("registeredUsers",999).toString());refresh();releaseSlow.countDown();assertThrows(ExecutionException.class,()->old.get(5,TimeUnit.SECONDS));assertEquals(999,repo.adminDashboard().registeredUsers());
  }
}
