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

/** Loopback HTTP covers conversation parsing, sending and unread aggregation. */
public class DirectMessageRepositoryTest {
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

  @Test public void listsConversationsAndCombinesUnread() {
    repo.refresh(new ContentRequest(ContentRequest.Query.CONVERSATIONS,""));
    var conversation=repo.conversations().items().get(0);
    assertEquals("c1",conversation.id());
    assertEquals("小红",conversation.other().displayName());
    assertEquals("今晚吃什么",conversation.lastMessage());
    assertEquals(2,conversation.unreadCount());
    assertEquals(2,repo.directUnreadCount());
  }

  @Test public void opensConversationAndSendsMessage() {
    repo.refresh(new ContentRequest(ContentRequest.Query.CONVERSATIONS,"c1"));
    assertEquals("小红",repo.conversation("c1").other().displayName());
    assertEquals(1,repo.conversationMessages("c1").items().size());

    var sent=repo.sendDirectMessage("c1","回复一句","req-1");
    assertEquals("回复一句",sent.content());
    var items=repo.conversationMessages("c1").items();
    assertEquals(2,items.size());
    assertEquals("回复一句",items.get(1).content());
  }

  @Test public void startConversationCachesTheSummary() {
    var conversation=repo.startConversation("u2");
    assertEquals("c1",conversation.id());
    assertEquals("c1",repo.conversation("c1").id());
    assertTrue(calls.stream().anyMatch(path->path.equals("/api/v1/users/me/conversations")));
  }

  private void handle(Socket socket) {
    try(socket) {
      socket.setSoTimeout(5000);
      var reader=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));
      String first=reader.readLine();
      if(first==null)return;
      String line;
      while((line=reader.readLine())!=null&&!line.isEmpty()){}
      String method=first.split(" ")[0],path=first.split(" ")[1];
      calls.add(path);
      Object data;
      if(path.contains("/auth/csrf")) {
        data=new JSONObject().put("headerName","X-CSRF-TOKEN").put("token","local-token");
      } else if(path.equals("/api/v1/users/me/profile")) {
        data=new JSONObject().put("id",id(1)).put("email","20260001@bjtu.edu.cn")
            .put("displayName","我").put("role","user").put("status","active")
            .put("followingCount",0).put("followerCount",0);
      } else if(path.equals("/api/v1/users/me/conversations?page=1&size=20")) {
        data=new JSONObject().put("items",new JSONArray().put(conversation("今晚吃什么",2)))
            .put("total",1).put("page",1).put("size",20);
      } else if(path.equals("/api/v1/users/me/conversations/unread-count")) {
        data=new JSONObject().put("unreadCount",2);
      } else if(path.equals("/api/v1/users/me/conversations/c1")) {
        data=conversation("今晚吃什么",0);
      } else if(path.equals("/api/v1/users/me/conversations")&&method.equals("POST")) {
        data=conversation("",0);
      } else if(path.equals("/api/v1/users/me/conversations/c1/messages?size=30")) {
        data=new JSONObject().put("items",new JSONArray().put(message("m1","u2","你好","2026-11-06T10:00:00Z")))
            .put("hasMore",false).put("nextCursor","");
      } else if(path.equals("/api/v1/users/me/conversations/c1/messages")&&method.equals("POST")) {
        data=message("m2",id(1),"回复一句","2026-11-06T10:01:00Z");
      } else {
        respond(socket,404,new JSONObject());
        return;
      }
      respond(socket,200,data);
    } catch(Exception ignored) {
      // 测试主动断开连接时忽略。
    }
  }

  private static JSONObject conversation(String last,int unread) throws JSONException {
    return new JSONObject().put("id","c1")
        .put("other",new JSONObject().put("id","u2").put("displayName","小红").put("avatarUrl",JSONObject.NULL))
        .put("lastMessage",last).put("lastSenderId","u2")
        .put("lastMessageAt","2026-11-06T10:00:00Z").put("unreadCount",unread)
        .put("updatedAt","2026-11-06T10:00:00Z");
  }

  private static JSONObject message(String id,String sender,String content,String at) throws JSONException {
    return new JSONObject().put("id",id).put("conversationId","c1")
        .put("senderId",sender).put("content",content).put("createdAt",at);
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
