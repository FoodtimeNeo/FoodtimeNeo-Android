package cn.edu.bjtu.foodtime.data.remote;

import cn.edu.bjtu.foodtime.domain.repository.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.json.*;
import org.junit.*;
import static org.junit.Assert.*;

/** Production HTTP/repository with changing server relationships and controllable failures. */
public class FollowRepositoryTest {
  private ServerSocket server;private ExecutorService workers;private RemoteRepository repo;
  private final Set<Integer> following=new ConcurrentSkipListSet<>(),fans=new ConcurrentSkipListSet<>();
  private final List<String> calls=new CopyOnWriteArrayList<>();
  private final AtomicReference<String> actor=new AtomicReference<>(id(1)),malformed=new AtomicReference<>();
  private final AtomicInteger status=new AtomicInteger(200),profileStatus=new AtomicInteger(200);
  private volatile String blockedPath;private volatile CountDownLatch started,release;
  static String id(int n){return String.format(Locale.ROOT,"00000000-0000-4000-8000-%012d",n);}
  static String target(int n){return id(1000+n);}
  @Before public void start()throws Exception {
    for(int i=1;i<=45;i++)following.add(i);for(int i=1;i<=125;i++)fans.add(i);
    server=new ServerSocket(0,50,InetAddress.getByName("127.0.0.1"));workers=Executors.newCachedThreadPool();
    workers.submit(()->{while(!server.isClosed())try{Socket socket=server.accept();workers.submit(()->handle(socket));}catch(IOException closed){if(!server.isClosed())throw new UncheckedIOException(closed);}});
    String base="http://127.0.0.1:"+server.getLocalPort()+"/api/v1/";repo=new RemoteRepository(base,new HttpTransport(base));
  }
  private void handle(Socket socket) {
    try(socket) {
      var reader=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));String first=reader.readLine();if(first==null)return;
      String line;while((line=reader.readLine())!=null&&!line.isEmpty()){}
      String method=first.split(" ")[0],path=first.split(" ")[1];calls.add(method+" "+path);
      int code=200;Object body=new JSONObject();
      if(path.endsWith("/profile")){
        code=profileStatus.get();body=new JSONObject().put("id",actor.get()).put("email","20260001@bjtu.edu.cn").put("displayName","测试用户").put("role","user").put("status","active").put("followingCount",following.size()).put("followerCount",fans.size());
      }else if(path.contains("/auth/csrf"))body=new JSONObject().put("token","local-token");
      else if(path.startsWith("/api/v1/users/search?")) {
        code=status.get();int number=Integer.parseInt(path.split("page=")[1].split("&")[0]);
        String query=URLDecoder.decode(path.split("q=")[1].split("&")[0],StandardCharsets.UTF_8);
        int total=query.equals("没人")?0:25;var items=new JSONArray();
        for(int i=(number-1)*20+1;i<=Math.min(total,number*20);i++)items.put(new JSONObject()
            .put("id",target(i)).put("displayName","食客 "+i).put("avatarUrl",JSONObject.NULL).put("followedByMe",following.contains(i)));
        body=malformed.get()!=null?new JSONObject(malformed.get()):new JSONObject().put("items",items).put("total",total).put("page",number).put("size",20);
      }
      else if(path.contains("/following?")||path.contains("/followers?")) {
        code=status.get();boolean followers=path.contains("/followers?");int page=Integer.parseInt(path.split("page=")[1].split("&")[0]);
        var values=new ArrayList<>(followers?fans:following);values.sort(Comparator.reverseOrder());var items=new JSONArray();
        for(int i=(page-1)*20;i<Math.min(values.size(),page*20);i++){int value=values.get(i);items.put(new JSONObject().put("id",target(value)).put("displayName","食客 "+value).put("avatarUrl",JSONObject.NULL).put("followedByMe",following.contains(value)));}
        body=malformed.get()!=null?new JSONObject(malformed.get()):new JSONObject().put("items",items).put("total",values.size()).put("page",page).put("size",20);
      }else if(path.endsWith("/follow")) {
        code=status.get();String userId=path.split("/users/")[1].split("/")[0];int value=Integer.parseInt(userId.substring(userId.lastIndexOf('-')+1))-1000;
        if(code==200){if(method.equals("PUT"))following.add(value);else following.remove(value);}
        body=new JSONObject().put("followed",method.equals("PUT")).put("followerCount",8);
      }else if(!path.contains("/auth/logout"))code=404;
      if(path.equals(blockedPath)){blockedPath=null;started.countDown();release.await(8,TimeUnit.SECONDS);}
      byte[] bytes=new JSONObject().put("code",code==200?"OK":"UNAVAILABLE").put("message","本地测试错误").put("data",body).toString().getBytes(StandardCharsets.UTF_8);
      var out=socket.getOutputStream();out.write(("HTTP/1.1 "+code+" Test\r\nContent-Type: application/json\r\nContent-Length: "+bytes.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));out.write(bytes);out.flush();
    }catch(Exception canceled){ /* Canceled network requests may disconnect. */ }
  }
  @After public void stop()throws Exception{if(release!=null)release.countDown();server.close();workers.shutdownNow();}
  private void load(boolean fans){repo.refresh(new ContentRequest(fans?ContentRequest.Query.FOLLOWERS:ContentRequest.Query.FOLLOWING,""));}
  @Test public void requestsCurrentUuidAndTwentyRows(){load(false);assertTrue(calls.contains("GET /api/v1/users/"+id(1)+"/following?page=1&size=20"));assertFalse(calls.stream().anyMatch(s->s.contains("/me/follow")));assertEquals(20,repo.following().items().size());assertEquals(45,repo.following().total());assertEquals(1,repo.following().page());assertTrue(repo.following().hasMore());}
  @Test public void switchingListsRetainsLoadedPages(){load(false);repo.loadMoreFollows(false);var before=repo.following();load(true);load(false);assertSame(before,repo.following());assertEquals(40,before.items().size());assertEquals(20,repo.followers().items().size());}
  @Test public void loadsAll125FollowersWithoutOldHundredLimit(){load(true);for(int i=0;i<6;i++)repo.loadMoreFollows(true);assertEquals(125,repo.followers().items().size());assertEquals(125,repo.followers().items().stream().map(r->r.id()).distinct().count());assertFalse(repo.followers().hasMore());int before=calls.size();repo.loadMoreFollows(true);assertEquals(before+1,calls.size());}
  @Test public void unfollowThenAppendDoesNotSkipShiftedRows(){load(false);repo.loadMoreFollows(false);repo.setFollow(target(45),false);assertEquals(39,repo.following().items().size());assertEquals(44,repo.currentUser().followingCount());repo.loadMoreFollows(false);assertEquals(44,repo.following().items().size());assertEquals(44,repo.following().total());assertFalse(repo.following().hasMore());Set<String> ids=new HashSet<>();for(var row:repo.following().items())ids.add(row.id());for(int i=1;i<=44;i++)assertTrue(ids.contains(target(i)));assertFalse(ids.contains(target(45)));}
  @Test public void followBackAndIdempotenceUpdateCountsWithoutRemovingFans(){load(true);repo.setFollow(target(125),true);assertEquals(46,repo.currentUser().followingCount());assertTrue(repo.followers().items().get(0).followedByMe());assertEquals(125,repo.followers().total());repo.setFollow(target(125),true);assertEquals(46,repo.currentUser().followingCount());repo.setFollow(target(125),false);assertEquals(45,repo.currentUser().followingCount());assertFalse(repo.followers().items().get(0).followedByMe());assertEquals(20,repo.followers().items().size());}
  @Test public void failedWritesLeaveRelationshipAndCountUnchanged(){load(true);var before=repo.followers();status.set(429);assertThrows(RepositoryException.class,()->repo.setFollow(target(125),true));assertSame(before,repo.followers());assertEquals(45,repo.currentUser().followingCount());}
  @Test public void ownCountComesFromServerWhenAnotherClientAlreadyFollowed(){load(true);following.add(125);repo.restoreSession();assertEquals(46,repo.currentUser().followingCount());repo.setFollow(target(125),true);assertEquals(46,repo.currentUser().followingCount());assertTrue(repo.followers().items().get(0).followedByMe());}
  @Test public void successfulWriteIsNotReportedFailedWhenCountReadIsUnavailable(){load(true);profileStatus.set(503);assertTrue(repo.setFollow(target(125),true).followed());assertEquals(46,repo.currentUser().followingCount());assertTrue(repo.followers().items().get(0).followedByMe());}
  @Test public void failedAppendRetainsPageAndCanRetrySamePage(){load(false);var before=repo.following();status.set(503);assertThrows(RepositoryException.class,()->repo.loadMoreFollows(false));assertSame(before,repo.following());status.set(200);repo.loadMoreFollows(false);assertEquals(2,repo.following().page());assertEquals(40,repo.following().items().size());}
  @Test public void failedRefreshRetainsLoadedRows(){load(true);var before=repo.followers();malformed.set("{\"total\":125,\"page\":1,\"size\":20}");assertEquals(502,assertThrows(RepositoryException.class,()->repo.loadFollows(true)).status());assertSame(before,repo.followers());}
  @Test public void firstFailureIsNotAnEmptySuccess(){status.set(503);assertThrows(RepositoryException.class,()->load(true));assertEquals(0,repo.followers().page());}
  @Test public void realEmptyListIsAStoredSuccess(){fans.clear();load(true);assertEquals(1,repo.followers().page());assertTrue(repo.followers().items().isEmpty());int before=calls.size();load(true);assertEquals(before+1,calls.size());}
  @Test public void logoutAndAccountChangeClearBothListsAndFollowStates(){load(true);repo.setFollow(target(125),true);repo.logout();assertNull(repo.currentUser());assertEquals(0,repo.followers().page());assertNull(repo.followState(target(125)));load(false);actor.set(id(2));repo.restoreSession();assertEquals(0,repo.following().page());}
  @Test public void deniedProfileClearsCachedPrivateLists(){load(true);profileStatus.set(403);assertThrows(RepositoryException.class,()->repo.loadMoreFollows(true));assertEquals(0,repo.followers().page());}
  private void block(String path){blockedPath=path;started=new CountDownLatch(1);release=new CountDownLatch(1);}
  @Test public void lateAppendCannotOverwriteRefresh()throws Exception{load(false);block("/api/v1/users/"+id(1)+"/following?page=2&size=20");var late=workers.submit(()->repo.loadMoreFollows(false));assertTrue(started.await(5,TimeUnit.SECONDS));repo.loadFollows(false);release.countDown();assertThrows(ExecutionException.class,()->late.get(5,TimeUnit.SECONDS));assertEquals(1,repo.following().page());assertEquals(20,repo.following().items().size());}
  @Test public void lateAccountResponseCannotPublishOldList()throws Exception{block("/api/v1/users/"+id(1)+"/following?page=1&size=20");var late=workers.submit(()->load(false));assertTrue(started.await(5,TimeUnit.SECONDS));actor.set(id(2));load(true);release.countDown();assertThrows(ExecutionException.class,()->late.get(5,TimeUnit.SECONDS));assertEquals(0,repo.following().page());assertEquals(1,repo.followers().page());}

  @Test public void userSearchUsesTwentyRowsAndSnapshotReadsAreLocal() {
    repo.restoreSession();repo.loadUserSearch(" 食客 ",1);
    var result=repo.userSearchResults();
    assertEquals("食客",result.query());assertEquals(20,result.items().size());assertEquals(25,result.total());assertTrue(result.hasMore());
    assertTrue(calls.contains("GET /api/v1/users/search?q="+URLEncoder.encode("食客",StandardCharsets.UTF_8)+"&page=1&size=20"));
    int before=calls.size();repo.userSearchResults();assertEquals(before,calls.size());
    assertFalse(calls.stream().anyMatch(path->path.contains("/following?")));
  }
  @Test public void followingWithoutLoadedListsUsesServerCount() {
    repo.restoreSession();repo.setFollow(target(125),true);
    assertEquals(46,repo.currentUser().followingCount());
    assertTrue(repo.followState(target(125)).followed());
  }
  @Test public void userSearchPaginationAndBlankQueriesDoNotMixResults() {
    repo.restoreSession();repo.loadUserSearch("食客",1);repo.loadUserSearch("食客",2);
    assertEquals(25,repo.userSearchResults().items().size());assertFalse(repo.userSearchResults().hasMore());
    assertEquals(25,repo.userSearchResults().items().stream().map(row->row.id()).distinct().count());
    repo.loadUserSearch("没人",1);assertTrue(repo.userSearchResults().items().isEmpty());
    int before=calls.size();repo.loadUserSearch(" ",1);assertEquals(before,calls.size());assertEquals(0,repo.userSearchResults().page());
  }
  @Test public void followingFromSearchUpdatesTheSameSnapshotWithoutRepeatingTheSearch() {
    repo.restoreSession();repo.loadUserSearch("食客",1);long count=calls.stream().filter(path->path.contains("/users/search?")).count();
    repo.setFollow(target(1),false);assertFalse(repo.userSearchResults().items().get(0).followedByMe());
    assertEquals(count,calls.stream().filter(path->path.contains("/users/search?")).count());
  }
  @Test public void failedUserSearchAppendPreservesThePreviousPage() {
    repo.restoreSession();repo.loadUserSearch("食客",1);var before=repo.userSearchResults();status.set(503);
    assertThrows(RepositoryException.class,()->repo.loadUserSearch("食客",2));assertSame(before,repo.userSearchResults());
    status.set(200);repo.loadUserSearch("食客",2);assertEquals(25,repo.userSearchResults().items().size());
  }
  @Test public void lateUserSearchCannotRestoreAnotherAccountsResults()throws Exception {
    repo.restoreSession();block("/api/v1/users/search?q="+URLEncoder.encode("食客",StandardCharsets.UTF_8)+"&page=1&size=20");
    var late=workers.submit(()->repo.loadUserSearch("食客",1));assertTrue(started.await(5,TimeUnit.SECONDS));
    actor.set(id(2));repo.restoreSession();release.countDown();
    assertThrows(ExecutionException.class,()->late.get(5,TimeUnit.SECONDS));assertEquals(0,repo.userSearchResults().page());
  }
}
