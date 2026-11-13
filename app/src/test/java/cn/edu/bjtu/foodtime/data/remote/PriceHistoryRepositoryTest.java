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
public class PriceHistoryRepositoryTest {
  private ServerSocket server;private ExecutorService workers;private RemoteRepository repo;
  private final List<String> paths=new CopyOnWriteArrayList<>();private volatile JSONObject submitted;
  private volatile String actor="actor-a";private volatile boolean restricted,mismatch;private volatile int responsePage=2,responseSize=20;
  private volatile CountDownLatch entered,release;
  @Before public void start()throws Exception{
    server=new ServerSocket(0,50,InetAddress.getByName("127.0.0.1"));workers=Executors.newCachedThreadPool();
    workers.submit(()->{while(!server.isClosed())try{Socket socket=server.accept();workers.submit(()->handle(socket));}catch(IOException stopped){if(!server.isClosed())throw new UncheckedIOException(stopped);}});
    String base="http://127.0.0.1:"+server.getLocalPort()+"/api/v1/";repo=new RemoteRepository(base,new HttpTransport(base));
  }
  @After public void stop()throws Exception{if(release!=null)release.countDown();server.close();workers.shutdownNow();}
  @Test public void priceReadsRequireAnAuthenticatedOwner(){assertThrows(RepositoryException.class,()->repo.priceHistory("dish",30));assertTrue(paths.isEmpty());}
  @Test public void readsACompletePriceSnapshotAndNullablePrice(){repo.restoreSession();var value=repo.priceHistory("dish",30);assertEquals("dish",value.dishId());assertTrue(value.canManage());assertEquals(2,value.totalRecords());assertNull(value.records().get(0).price());assertTrue(value.records().get(0).carriedForward());assertEquals("serving",value.priceUnit());}
  @Test public void correctionsUsePutAndPreserveUnknownAndCallerRevision()throws Exception{repo.restoreSession();repo.correctPriceHistory("dish","1",null,"kg",7);assertTrue(submitted.has("price")&&submitted.isNull("price"));assertEquals("kg",submitted.getString("priceUnit"));assertEquals(7,submitted.getLong("expectedRevision"));repo.deletePriceHistory("dish","2",8);assertTrue(paths.contains("/api/v1/admin/dishes/dish/price-history/2?expectedRevision=8"));}
  @Test public void aDifferentDishCannotReplaceTheRequestedChart(){repo.restoreSession();mismatch=true;assertEquals("INVALID_RESPONSE",assertThrows(RepositoryException.class,()->repo.priceHistory("dish",30)).code());}
  @Test public void latePriceResponseIsRejectedAfterSwitchingAccounts()throws Exception{repo.restoreSession();entered=new CountDownLatch(1);release=new CountDownLatch(1);Future<?> read=workers.submit(()->repo.priceHistory("dish",30));assertTrue(entered.await(3,TimeUnit.SECONDS));actor="actor-b";repo.restoreSession();release.countDown();var failure=assertThrows(ExecutionException.class,()->read.get(3,TimeUnit.SECONDS));assertEquals("ACCOUNT_CHANGED",((RepositoryException)failure.getCause()).code());}
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
    else if(path.contains("/price-history?days=")){if(entered!=null){entered.countDown();release.await(5,TimeUnit.SECONDS);}data=new JSONObject().put("dishId",mismatch?"other":"dish").put("dishName","菜品").put("currentPrice",12).put("priceUnit","serving").put("revision",7).put("windowStart","2026-11-01T00:00:00Z").put("windowEnd","2026-11-13T00:00:00Z").put("totalRecords",2).put("canManage",true).put("records",new JSONArray().put(new JSONObject().put("id","1").put("price",JSONObject.NULL).put("priceUnit","serving").put("recordedAt","2026-10-01T00:00:00Z").put("carriedForward",true)));}
    else if(method.equals("PUT")){submitted=new JSONObject(new String(body,StandardCharsets.UTF_8));}
    byte[] bytes=new JSONObject().put("code",status==200?"OK":"ACCOUNT_DISABLED").put("message","fixture").put("data",data).toString().getBytes(StandardCharsets.UTF_8);
    var out=socket.getOutputStream();out.write(("HTTP/1.1 "+status+" OK\r\nContent-Type: application/json\r\nContent-Length: "+bytes.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));out.write(bytes);out.flush();
  }catch(Exception e){if(!server.isClosed())throw new RuntimeException(e);}}
  private static String line(InputStream input)throws IOException{var bytes=new ByteArrayOutputStream();int value;while((value=input.read())!=-1&&value!='\n')if(value!='\r')bytes.write(value);return value==-1&&bytes.size()==0?null:bytes.toString(StandardCharsets.US_ASCII);}
}
