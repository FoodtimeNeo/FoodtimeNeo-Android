package cn.edu.bjtu.foodtime.data.remote;

import cn.edu.bjtu.foodtime.domain.repository.ContentRequest;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;
import org.junit.*;
import static org.junit.Assert.*;

/** Loopback HTTP covers message parsing and deep-link comment loading. */
public class CommunityNotificationRepositoryTest {
  private ServerSocket server;
  private ExecutorService workers;
  private RemoteRepository repo;
  private final List<String> calls=new CopyOnWriteArrayList<>();

  @Before public void start() throws Exception {
    server=new ServerSocket(0,50,InetAddress.getByName("127.0.0.1"));
    workers=Executors.newCachedThreadPool();
    workers.submit(()->{
      while(!server.isClosed())try {
        Socket socket=server.accept();
        workers.submit(()->handle(socket));
      } catch(IOException closed) {
        if(!server.isClosed())throw new UncheckedIOException(closed);
      }
    });
    String base="http://127.0.0.1:"+server.getLocalPort()+"/api/v1/";
    repo=new RemoteRepository(base,new HttpTransport(base));
  }

  @After public void stop() throws Exception {
    server.close();
    workers.shutdownNow();
  }

  @Test public void messagesExposeAggregationAndRootForDeepLinks() {
    repo.refresh(new ContentRequest(ContentRequest.Query.MESSAGES,""));
    var message=repo.messages().items().get(0);
    assertEquals("LIKE_COMMENT",message.category());
    assertEquals("post-1",message.refId());
    assertEquals("comment-1",message.refCommentId());
    assertEquals("root-1",message.refRootCommentId());
    assertEquals(3,message.actorCount());
    assertEquals("小刚 和 小红 等总计 3 人赞了你的评论",message.body());
  }

  @Test public void ensureLoadsRootAndReplyPagesUntilTargetFound() {
    assertTrue(repo.ensureCommunityCommentVisible("post-1","root-2","reply-5"));
    assertTrue(calls.stream().anyMatch(path->path.contains("comments/cursor?size=20&cursor=c1")));
    assertTrue(calls.stream().anyMatch(path->path.contains("/comments/root-2/replies?size=20&cursor=r1")));
  }

  @Test public void ensureStopsWhenTargetCannotBeFound() {
    assertFalse(repo.ensureCommunityCommentVisible("post-1","root-2","missing-reply"));
  }

  private void handle(Socket socket) {
    try(socket) {
      socket.setSoTimeout(5000);
      var reader=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));
      String first=reader.readLine();
      if(first==null)return;
      String line;
      while((line=reader.readLine())!=null&&!line.isEmpty()){}
      String path=first.split(" ")[1];
      calls.add(path);
      Object data;
      if(path.equals("/api/v1/users/me/profile")) {
        data=new JSONObject().put("id",id(1)).put("email","20260001@bjtu.edu.cn")
            .put("displayName","测试用户").put("role","user").put("status","active")
            .put("followingCount",0).put("followerCount",0);
      } else if(path.startsWith("/api/v1/users/me/messages?")) {
        data=new JSONObject().put("items",new JSONArray().put(new JSONObject()
            .put("id","m1").put("title","你的评论收到新的赞")
            .put("body","小刚 和 小红 等总计 3 人赞了你的评论")
            .put("category","LIKE_COMMENT").put("refType","community-post")
            .put("refId","post-1").put("refCommentId","comment-1")
            .put("refRootCommentId","root-1").put("actorCount",3)
            .put("isRead",false).put("createdAt","2026-11-05T10:00:00Z")))
            .put("total",1).put("page",1).put("size",20);
      } else if(path.equals("/api/v1/users/me/messages/unread-count")) {
        data=new JSONObject().put("unreadCount",1);
      } else if(path.equals("/api/v1/users/me/conversations/unread-count")) {
        data=new JSONObject().put("unreadCount",0);
      } else if(path.contains("/comments/cursor?size=20")&&!path.contains("cursor=")) {
        data=page(new JSONArray(),true,"c1");
      } else if(path.contains("/comments/cursor?size=20&cursor=c1")) {
        data=page(new JSONArray().put(comment("root-2",null)),false,"");
      } else if(path.contains("/comments/root-2/replies?size=3")) {
        data=page(new JSONArray()
            .put(comment("reply-1","root-2"))
            .put(comment("reply-2","root-2"))
            .put(comment("reply-3","root-2")),true,"r1");
      } else if(path.contains("/comments/root-2/replies?size=20&cursor=r1")) {
        data=page(new JSONArray()
            .put(comment("reply-4","root-2"))
            .put(comment("reply-5","root-2")),false,"");
      } else {
        respond(socket,404,new JSONObject());
        return;
      }
      respond(socket,200,data);
    } catch(Exception ignored) {
      // 连接被测试主动关闭时忽略。
    }
  }

  private static JSONObject page(JSONArray items,boolean hasMore,String nextCursor) throws JSONException {
    return new JSONObject().put("items",items).put("total",items.length())
        .put("hasMore",hasMore).put("nextCursor",nextCursor);
  }

  private static JSONObject comment(String id,String rootId) throws JSONException {
    return new JSONObject().put("id",id).put("postId","post-1").put("userId",id(9))
        .put("userDisplayName","同学").put("content","内容").put("status","approved")
        .put("createdAt","2026-11-05T10:00:00Z").put("rootCommentId",rootId==null?JSONObject.NULL:rootId);
  }

  private static String id(int n) {
    return String.format(Locale.ROOT,"00000000-0000-4000-8000-%012d",n);
  }

  private static void respond(Socket socket,int code,Object data) throws IOException,JSONException {
    byte[] bytes=new JSONObject().put("code",code==200?"OK":"DENIED")
        .put("message","test").put("data",data).toString().getBytes(StandardCharsets.UTF_8);
    var out=socket.getOutputStream();
    out.write(("HTTP/1.1 "+code+" Test\r\nContent-Type: application/json\r\nContent-Length: "+bytes.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
    out.write(bytes);
    out.flush();
  }
}
