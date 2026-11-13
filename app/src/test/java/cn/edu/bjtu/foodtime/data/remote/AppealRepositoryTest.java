package cn.edu.bjtu.foodtime.data.remote;

import cn.edu.bjtu.foodtime.domain.repository.RepositoryException;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;
import org.junit.*;
import static org.junit.Assert.*;

/** Real HTTP contracts for recipient projections, disabled sessions and late account responses. */
public class AppealRepositoryTest {
  private ServerSocket server;private ExecutorService workers;private RemoteRepository repo;
  private final List<String> paths=new CopyOnWriteArrayList<>();private volatile JSONObject submitted;
  private volatile String actor="actor-a";private volatile boolean restricted,mismatch;
  private volatile CountDownLatch entered,release;
  @Before public void start()throws Exception{
    server=new ServerSocket(0,50,InetAddress.getByName("127.0.0.1"));workers=Executors.newCachedThreadPool();
    workers.submit(()->{while(!server.isClosed())try{Socket socket=server.accept();workers.submit(()->handle(socket));}catch(IOException stopped){if(!server.isClosed())throw new UncheckedIOException(stopped);}});
    String base="http://127.0.0.1:"+server.getLocalPort()+"/api/v1/";repo=new RemoteRepository(base,new HttpTransport(base));
  }
  @After public void stop()throws Exception{if(release!=null)release.countDown();server.close();workers.shutdownNow();}
  @Test public void recipientCallsRequireAnAuthenticatedOwner(){assertThrows(RepositoryException.class,()->repo.myModerationDecision("decision"));assertTrue(paths.isEmpty());}
  @Test public void readsTheExactDecisionAndActionArray(){repo.restoreSession();var value=repo.myModerationDecision("decision");assertEquals("decision",value.decisionId());assertEquals("target",value.side());assertTrue(value.appealDeadline()>0);assertEquals(1,repo.decisionActions("decision").size());}
  @Test public void disabledAppealSessionCanReadItsSecondPage(){restricted=true;repo.restoreSession();assertFalse(repo.currentUser().active());assertEquals("appeal_only",repo.sessionMode());var page=repo.myModerationDecisions(2);assertEquals(2,page.page());assertEquals(21,page.total());assertTrue(paths.contains("/api/v1/users/me/moderation-decisions?page=2&size=20"));}
  @Test public void submissionRetainsEvidenceAndCallerRequestId()throws Exception{repo.restoreSession();var evidence=List.of("/api/v1/media/00000000-0000-4000-8000-000000000001");repo.submitAppeal("decision","申诉内容","same-request",evidence);assertEquals("same-request",submitted.getString("clientRequestId"));assertEquals(evidence.get(0),submitted.getJSONArray("evidenceUrls").getString(0));}
  @Test public void mismatchedDecisionCannotOpenAnEditor(){repo.restoreSession();mismatch=true;assertEquals("INVALID_RESPONSE",assertThrows(RepositoryException.class,()->repo.myModerationDecision("decision")).code());}
  @Test public void lateProjectionIsRejectedAfterAnAccountChange()throws Exception{
    repo.restoreSession();entered=new CountDownLatch(1);release=new CountDownLatch(1);
    Future<?> read=workers.submit(()->repo.myModerationDecision("decision"));assertTrue(entered.await(3,TimeUnit.SECONDS));
    actor="actor-b";repo.restoreSession();release.countDown();
    var failure=assertThrows(ExecutionException.class,()->read.get(3,TimeUnit.SECONDS));assertTrue(failure.getCause() instanceof RepositoryException);assertEquals("ACCOUNT_CHANGED",((RepositoryException)failure.getCause()).code());
  }
  private void handle(Socket socket){try(socket){
    socket.setSoTimeout(5000);var input=socket.getInputStream();String first=line(input);if(first==null)return;
    String method=first.split(" ")[0],path=first.split(" ")[1];paths.add(path);int length=0;String line;
    while((line=line(input))!=null&&!line.isEmpty())if(line.toLowerCase(Locale.ROOT).startsWith("content-length:"))length=Integer.parseInt(line.substring(15).strip());
    byte[] body=input.readNBytes(length);
    int status=200;Object data=new JSONObject();
    if(path.endsWith("/auth/csrf"))data=new JSONObject().put("token","fixture");
    else if(path.endsWith("/users/me/profile")&&restricted)status=403;
    else if(path.endsWith("/users/me/profile")||path.endsWith("/auth/me"))data=new JSONObject().put("id",actor).put("email","fixture@example.test").put("role","user").put("status","active").put("sessionMode",restricted?"appeal_only":"normal");
    else if(path.endsWith("/capabilities"))data=new JSONObject().put("capabilities",new JSONArray()).put("revision","fixture");
    else if(path.endsWith("/decision/actions"))data=new JSONArray().put(new JSONObject().put("id","action").put("decisionId","decision").put("actionType","hide_post"));
    else if(path.contains("/moderation-decisions?"))data=new JSONObject().put("items",new JSONArray().put(decision())).put("page",2).put("size",20).put("total",21);
    else if(path.endsWith("/decision")){if(entered!=null){entered.countDown();release.await(5,TimeUnit.SECONDS);}data=decision();}
    else if(method.equals("POST")&&path.endsWith("/appeals")){submitted=new JSONObject(new String(body,StandardCharsets.UTF_8));data=new JSONObject().put("id","appeal").put("status","submitted");}
    byte[] bytes=new JSONObject().put("code",status==200?"OK":"ACCOUNT_DISABLED").put("message","fixture").put("data",data).toString().getBytes(StandardCharsets.UTF_8);
    var out=socket.getOutputStream();out.write(("HTTP/1.1 "+status+" OK\r\nContent-Type: application/json\r\nContent-Length: "+bytes.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));out.write(bytes);out.flush();
  }catch(Exception e){if(!server.isClosed())throw new RuntimeException(e);}}
  private JSONObject decision()throws JSONException{return new JSONObject().put("decisionId",mismatch?"another":"decision").put("side","target").put("targetType","post").put("outcome","upheld").put("publicReason","本人决定").put("deliveredAt","2026-11-13T00:00:00Z").put("appealDeadline","2099-01-01T00:00:00Z");}
  private static String line(InputStream input)throws IOException{var bytes=new ByteArrayOutputStream();int value;while((value=input.read())!=-1&&value!='\n')if(value!='\r')bytes.write(value);return value==-1&&bytes.size()==0?null:bytes.toString(StandardCharsets.US_ASCII);}
}
