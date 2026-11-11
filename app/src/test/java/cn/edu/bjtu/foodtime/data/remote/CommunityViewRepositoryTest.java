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

public class CommunityViewRepositoryTest {
  private ServerSocket server;
  private ExecutorService worker;
  private RemoteRepository repo;
  private final List<String> requests=new CopyOnWriteArrayList<>();
  private volatile String requestId="";
  @Before public void start() throws Exception {
    server=new ServerSocket(0,50,InetAddress.getByName("127.0.0.1"));worker=Executors.newSingleThreadExecutor();
    worker.submit(() -> {while(!server.isClosed()) try{handle(server.accept());}catch(Exception failure){if(!server.isClosed())throw new RuntimeException(failure);}});
    String base="http://127.0.0.1:"+server.getLocalPort()+"/api/v1/";repo=new RemoteRepository(base,new HttpTransport(base));
  }
  @After public void stop() throws Exception {server.close();worker.shutdownNow();}
  @Test public void readingDoesNotRecordViewAndLikeUpdatesPreserveTheCounter() {
    repo.refresh(new ContentRequest(ContentRequest.Query.COMMUNITY_POST,"post"));
    assertEquals(41,repo.communityPost("post").viewCount());
    assertFalse(requests.stream().anyMatch(path -> path.endsWith("/views")));
    String id=UUID.randomUUID().toString(); assertEquals(42,repo.recordCommunityPostView("post",id));
    assertEquals(id,requestId);
    repo.setCommunityLike("post","post",true);
    assertEquals(41,repo.communityPost("post").viewCount());
    assertTrue(repo.communityPost("post").likedByMe());
  }
  private void handle(Socket socket) throws Exception {
    try(socket) {
      socket.setSoTimeout(5000);var reader=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));
      String first=reader.readLine(),line;int size=0;
      while((line=reader.readLine())!=null&&!line.isEmpty()) if(line.toLowerCase(Locale.ROOT).startsWith("content-length:"))size=Integer.parseInt(line.substring(line.indexOf(':')+1).trim());
      StringBuilder body=new StringBuilder();for(int i=0;i<size;i++){int value=reader.read();if(value<0)throw new EOFException();body.append((char)value);}
      String path=first.split(" ")[1];requests.add(first.split(" ")[0]+" "+path);
      JSONObject data=new JSONObject();
      if(path.endsWith("/auth/csrf"))data.put("token","test").put("headerName","X-CSRF-TOKEN");
      else if(path.endsWith("/views")){requestId=new JSONObject(body.toString()).getString("clientRequestId");data.put("viewCount",42);}
      else if(path.endsWith("/like"))data.put("liked",true).put("likeCount",2);
      else data.put("id","post").put("userId","author").put("title","食记").put("content","今天吃面").put("status","approved").put("viewCount",41);
      byte[] bytes=new JSONObject().put("code","OK").put("data",data).toString().getBytes(StandardCharsets.UTF_8);
      var out=socket.getOutputStream();out.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: "+bytes.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));out.write(bytes);out.flush();
    }
  }
}
