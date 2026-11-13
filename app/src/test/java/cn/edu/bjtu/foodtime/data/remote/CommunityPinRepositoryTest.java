package cn.edu.bjtu.foodtime.data.remote;

import cn.edu.bjtu.foodtime.domain.repository.ContentRequest;
import cn.edu.bjtu.foodtime.domain.repository.RepositoryException;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;
import org.junit.*;
import static org.junit.Assert.*;

/** Actual loopback requests verify snapshot generations, mutation contracts and account isolation. */
public class CommunityPinRepositoryTest {
  private ServerSocket server;private ExecutorService workers;private RemoteRepository repo;
  private final List<String> paths=new CopyOnWriteArrayList<>();private volatile JSONObject submitted;
  private volatile String actor="owner";private volatile boolean restarted;private volatile int firstLoads;
  private volatile CountDownLatch entered,release;
  @Before public void start()throws Exception{
    server=new ServerSocket(0,50,InetAddress.getByName("127.0.0.1"));workers=Executors.newCachedThreadPool();
    workers.submit(()->{while(!server.isClosed())try{Socket socket=server.accept();workers.submit(()->handle(socket));}catch(IOException stopped){if(!server.isClosed())throw new UncheckedIOException(stopped);}});
    String base="http://127.0.0.1:"+server.getLocalPort()+"/api/v1/";repo=new RemoteRepository(base,new HttpTransport(base));
  }
  @After public void stop()throws Exception{if(release!=null)release.countDown();server.close();workers.shutdownNow();}
  @Test public void managementRequiresAnAccountBeforeAnyRequest(){assertThrows(RepositoryException.class,()->repo.managedPins());assertTrue(paths.isEmpty());}
  @Test public void readsHiddenEntriesAsReservedManagementSlots(){repo.restoreSession();var value=repo.managedPins();assertEquals(7,value.revision());assertEquals("pending",value.items().get(0).status());assertTrue(value.canManage());}
  @Test public void managementPreservesIdsOrderAndCallerRevision()throws Exception{repo.restoreSession();repo.pinPost("c",7);assertEquals(7,submitted.getLong("expectedRevision"));repo.reorderPins(List.of("b","a"),8);assertEquals("b",submitted.getJSONArray("postIds").getString(0));assertEquals(8,submitted.getLong("expectedRevision"));repo.unpinPost("a",9);assertTrue(paths.contains("/api/v1/admin/community/pins/a?expectedRevision=9"));}
  @Test public void changedPinsRestartPaginationInsteadOfAppendingTheNewFirstPage(){repo.restoreSession();repo.refresh(new ContentRequest(ContentRequest.Query.COMMUNITY_FEED,""));assertEquals("before",repo.communityPosts().get(0).id());repo.loadMoreCommunityPosts();assertEquals(2,firstLoads);assertEquals(1,repo.communityPosts().size());assertEquals("after",repo.communityPosts().get(0).id());assertTrue(paths.stream().anyMatch(value->value.contains("pinRevision=7")));assertEquals(8,repo.communityPins().revision());}
  @Test public void lateManagementResponseCannotCrossAccounts()throws Exception{repo.restoreSession();entered=new CountDownLatch(1);release=new CountDownLatch(1);var read=workers.submit(()->repo.managedPins());assertTrue(entered.await(3,TimeUnit.SECONDS));actor="other";repo.restoreSession();release.countDown();var failure=assertThrows(ExecutionException.class,()->read.get(3,TimeUnit.SECONDS));assertEquals("ACCOUNT_CHANGED",((RepositoryException)failure.getCause()).code());}
  private JSONObject pins(long revision)throws JSONException{return new JSONObject().put("revision",revision).put("canManage",true).put("items",new JSONArray().put(new JSONObject().put("id","a").put("title","保留名额").put("userDisplayName","同学").put("status","pending").put("position",1)));}
  private void handle(Socket socket){try(socket){socket.setSoTimeout(5000);var input=socket.getInputStream();String first=line(input);if(first==null)return;String method=first.split(" ")[0],path=first.split(" ")[1];paths.add(path);int length=0;String header;while((header=line(input))!=null&&!header.isEmpty())if(header.toLowerCase(Locale.ROOT).startsWith("content-length:"))length=Integer.parseInt(header.substring(15).strip());byte[] bytes=input.readNBytes(length);int status=200;String code="OK";Object data=new JSONObject();
    if(path.endsWith("/auth/csrf"))data=new JSONObject().put("token","fixture");
    else if(path.endsWith("/users/me/profile"))data=new JSONObject().put("id",actor).put("email","fixture@example.test").put("role","superadmin");
    else if(path.endsWith("/capabilities"))data=new JSONObject().put("capabilities",new JSONArray().put("community.pin.manage")).put("revision","fixture");
    else if(path.contains("/admin/community/pins")){data=pins(7);if(method.equals("PUT"))submitted=new JSONObject(new String(bytes,StandardCharsets.UTF_8));if(method.equals("GET")&&entered!=null){entered.countDown();release.await(5,TimeUnit.SECONDS);}}
    else if(path.contains("/community/feed/cursor")){if(path.contains("cursor=")){status=409;code="PIN_SET_CHANGED";restarted=true;}else{firstLoads++;data=new JSONObject().put("items",new JSONArray().put(new JSONObject().put("id",restarted?"after":"before").put("title","食记").put("status","approved").put("createdAt","2026-11-13T00:00:00Z"))).put("total",1).put("hasMore",!restarted).put("nextCursor",restarted?JSONObject.NULL:"next").put("pins",pins(restarted?8:7));}}
    byte[] response=new JSONObject().put("code",code).put("message","fixture").put("data",data).toString().getBytes(StandardCharsets.UTF_8);var out=socket.getOutputStream();out.write(("HTTP/1.1 "+status+" OK\r\nContent-Type: application/json\r\nContent-Length: "+response.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));out.write(response);out.flush();
  }catch(Exception failure){if(!server.isClosed())throw new RuntimeException(failure);}}
  private static String line(InputStream input)throws IOException{var out=new ByteArrayOutputStream();int value;while((value=input.read())!=-1&&value!='\n')if(value!='\r')out.write(value);return value==-1&&out.size()==0?null:out.toString(StandardCharsets.US_ASCII);}
}
