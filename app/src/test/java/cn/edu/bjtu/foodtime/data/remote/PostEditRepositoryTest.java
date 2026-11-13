package cn.edu.bjtu.foodtime.data.remote;

import cn.edu.bjtu.foodtime.domain.repository.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;
import org.junit.*;
import static org.junit.Assert.*;

/** Real transport checks upload reuse, account boundaries and review-version forwarding. */
public class PostEditRepositoryTest {
  private ServerSocket server;private ExecutorService workers;private RemoteRepository repo;
  private volatile String actor="owner";private volatile JSONObject submitted;
  private volatile int uploads,writes;private volatile boolean fail;
  private CountDownLatch entered,release;
  @Before public void start()throws Exception{
    server=new ServerSocket(0,50,InetAddress.getByName("127.0.0.1"));workers=Executors.newCachedThreadPool();
    workers.submit(()->{while(!server.isClosed())try{Socket socket=server.accept();workers.submit(()->handle(socket));}catch(IOException e){if(!server.isClosed())throw new UncheckedIOException(e);}});
    String base="http://127.0.0.1:"+server.getLocalPort()+"/api/v1/";repo=new RemoteRepository(base,new HttpTransport(base));
  }
  @After public void stop()throws Exception{if(release!=null)release.countDown();server.close();workers.shutdownNow();}
  private PostEditRepository.Edit input(List<String> images){return new PostEditRepository.Edit("新标题","新正文",images,"",4,"request-1");}
  @Test public void writesRequireAnAccount(){assertThrows(RepositoryException.class,()->repo.editCommunityPost("post",input(List.of())));assertEquals(0,writes);}
  @Test public void forwardsRevisionAndClearAssociationWithoutChangingRepostSource()throws Exception{
    repo.restoreSession();repo.editCommunityPost("post",input(List.of("/api/v1/media/old")));
    assertEquals(4,submitted.getLong("expectedRevision"));assertEquals("request-1",submitted.getString("clientRequestId"));
    assertEquals("新标题",submitted.getString("title"));assertTrue(submitted.isNull("dishId"));assertFalse(submitted.has("repostOfId"));assertEquals(0,uploads);
  }
  @Test public void failedEditReusesUploadedImageAndExactRequest()throws Exception{
    repo.restoreSession();File file=File.createTempFile("foodtime-edit-",".png");
    try{Files.write(file.toPath(),new byte[]{1,2,3});var input=input(List.of(file.toURI().toString()));fail=true;
      assertThrows(RepositoryException.class,()->repo.editCommunityPost("post",input));String first=submitted.toString();
      fail=false;repo.editCommunityPost("post",input);assertEquals(first,submitted.toString());assertEquals(1,uploads);assertEquals(2,writes);
    }finally{Files.deleteIfExists(file.toPath());}
  }
  @Test public void lateUploadCannotAssociateWithAnotherAccount()throws Exception{
    repo.restoreSession();File file=File.createTempFile("foodtime-edit-",".png");entered=new CountDownLatch(1);release=new CountDownLatch(1);
    try{Files.write(file.toPath(),new byte[]{1,2,3});var task=workers.submit(()->repo.editCommunityPost("post",input(List.of(file.toURI().toString()))));
      assertTrue(entered.await(3,TimeUnit.SECONDS));actor="second";repo.restoreSession();release.countDown();
      var failure=assertThrows(ExecutionException.class,()->task.get(5,TimeUnit.SECONDS));assertEquals("ACCOUNT_CHANGED",((RepositoryException)failure.getCause()).code());assertEquals(0,writes);
    }finally{Files.deleteIfExists(file.toPath());}
  }
  @Test public void postRevisionSurvivesLikeUpdatesAndIsSentWithManualReview()throws Exception{
    repo.restoreSession();repo.refresh(new ContentRequest(ContentRequest.Query.COMMUNITY_POST,"post"));
    assertEquals(4,repo.communityPost("post").contentRevision());assertTrue(repo.communityPost("post").editedAt()>0);
    repo.setCommunityLike("post","post",true);assertEquals(4,repo.communityPost("post").contentRevision());
    repo.moderateCommunity("posts","post","approve","核验",4);assertEquals(4,submitted.getLong("contentRevision"));
  }
  private void handle(Socket socket){try(socket){
    var in=socket.getInputStream();String first=line(in),line;int size=0;
    while((line=line(in))!=null&&!line.isEmpty())if(line.toLowerCase(Locale.ROOT).startsWith("content-length:"))size=Integer.parseInt(line.substring(line.indexOf(':')+1).trim());
    byte[] bytes=in.readNBytes(size);String path=first.split(" ")[1],method=first.split(" ")[0];JSONObject data=new JSONObject();int status=200;String code="OK";
    if(path.endsWith("/auth/csrf"))data.put("token","fixture");
    else if(path.endsWith("/users/me/profile"))data.put("id",actor).put("email","fixture@example.test").put("role","user");
    else if(path.endsWith("/capabilities"))data.put("capabilities",new JSONArray()).put("revision","fixture");
    else if(path.endsWith("/uploads/images")){uploads++;if(entered!=null){entered.countDown();release.await(5,TimeUnit.SECONDS);}data.put("imageUrl","/api/v1/media/uploaded");}
    else if(method.equals("PUT")||path.contains("/admin/community/posts/")){writes++;submitted=bytes.length==0?new JSONObject():new JSONObject(new String(bytes,StandardCharsets.UTF_8));if(fail){status=503;code="UNAVAILABLE";}data.put("liked",true).put("likeCount",2);}
    else data.put("id","post").put("userId","owner").put("title","标题").put("content","正文").put("status","approved").put("contentRevision",4).put("editedAt","2026-11-13T00:00:00Z");
    byte[] response=new JSONObject().put("code",code).put("message","fixture").put("data",data).toString().getBytes(StandardCharsets.UTF_8);var out=socket.getOutputStream();out.write(("HTTP/1.1 "+status+" OK\r\nContent-Type: application/json\r\nContent-Length: "+response.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));out.write(response);out.flush();
  }catch(Exception e){if(!server.isClosed())throw new RuntimeException(e);}}
  private static String line(InputStream in)throws IOException{var out=new ByteArrayOutputStream();int value;while((value=in.read())!=-1&&value!='\n')if(value!='\r')out.write(value);return value==-1&&out.size()==0?null:out.toString(StandardCharsets.US_ASCII);}
}
