package cn.edu.bjtu.foodtime.data.remote;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

public class PublicCatalogCacheTest {
  @Rule public TemporaryFolder temporary=new TemporaryFolder();
  private ServerSocket server;
  private ExecutorService worker;
  private String base,etag="\"catalog-v1-"+"a".repeat(64)+"\"";
  private volatile String data="[{\"id\":\"public-hall\",\"coverImageUrl\":\"/api/v1/media/00000000-0000-4000-8000-000000000001\"}]";
  private volatile int forced=0;
  private volatile boolean marked=true;
  private final List<String> conditions=Collections.synchronizedList(new ArrayList<>());
  @Before public void start()throws Exception{
    server=new ServerSocket();server.bind(new InetSocketAddress("127.0.0.1",0));
    worker=Executors.newSingleThreadExecutor();
    worker.submit(()->{
      while(!server.isClosed())try(Socket socket=server.accept()){
        var input=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));
        input.readLine();String line,condition="";
        while((line=input.readLine())!=null&&!line.isEmpty())
          if(line.toLowerCase(Locale.ROOT).startsWith("if-none-match:"))condition=line.substring(14).trim();
        conditions.add(condition);
        int status=forced!=0?forced:etag.equals(condition)?304:200;
        byte[] body=(status==304?"":status==200?"{\"code\":\"OK\",\"data\":"+data+"}":"{\"code\":\"ERROR\",\"data\":null}").getBytes(StandardCharsets.UTF_8);
        String headers=marked?"X-Foodtime-Public-Catalog: 1\r\nETag: "+etag+"\r\n":"";
        var output=socket.getOutputStream();
        output.write(("HTTP/1.1 "+status+" Result\r\n"+headers+"Cache-Control: private, no-cache\r\nContent-Length: "+body.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(body);output.flush();
      }catch(IOException failure){if(!server.isClosed())throw new IllegalStateException(failure);}
    });
    base="http://127.0.0.1:"+server.getLocalPort()+"/api/v1/";
  }
  @After public void stop()throws IOException{server.close();worker.shutdownNow();}
  private HttpTransport.Response read(PublicCatalogCache cache,String path)throws IOException{
    return cache.request(new HttpTransport(base),"GET",path,null,null);
  }
  @Test public void restartRevalidatesBeforeUsingReplicaAndOnlyReplacesChangedResource()throws Exception{
    File directory=temporary.newFolder();var cache=new PublicCatalogCache(base,directory);
    assertTrue(read(cache,"dining-halls").body().contains("public-hall"));
    var restarted=new PublicCatalogCache(base,directory);
    assertEquals(200,read(restarted,"dining-halls").status());
    assertEquals(List.of("",etag),conditions);
    assertTrue(restarted.isCatalogMedia("/api/v1/media/00000000-0000-4000-8000-000000000001?size=thumb"));
    etag="\"catalog-v1-"+"b".repeat(64)+"\"";data="[{\"id\":\"changed\"}]";
    assertTrue(read(restarted,"dining-halls").body().contains("changed"));
    assertTrue(read(restarted,"dining-halls").body().contains("changed"));
    assertEquals(etag,conditions.get(3));
  }
  @Test public void failuresNeverFallBackToStaleAndPrivateEndpointsNeverPersist()throws Exception{
    File directory=temporary.newFolder();var cache=new PublicCatalogCache(base,directory);
    read(cache,"dining-halls");
    for(int status:new int[]{401,403,404,500}){
      forced=status;assertEquals(status,read(cache,"dining-halls").status());
      assertEquals(0,Objects.requireNonNull(directory.listFiles()).length);
      forced=0;read(cache,"dining-halls");
    }
    int size=Objects.requireNonNull(directory.listFiles()).length;
    for(String path:List.of("users/me/profile","community/posts","admin/dining-halls","feedback")){
      read(cache,path);assertEquals("",conditions.get(conditions.size()-1));
    }
    assertEquals(size,Objects.requireNonNull(directory.listFiles()).length);
    server.close();
    worker.shutdown();
    assertTrue("Fixture must fully stop before simulating offline",worker.awaitTermination(5,TimeUnit.SECONDS));
    try {
      // HttpURLConnection may report a closed peer as status -1 or throw IOException,
      // depending on the JDK. Neither may be converted into a cached 200 response.
      var offline=read(cache,"dining-halls");
      assertNotEquals("Offline transport must not return cached success: closed="+server.isClosed()+", requests="+conditions.size()+", etag="+offline.header("ETag")+", body="+offline.body(),200,offline.status());
      assertFalse(offline.body().contains("public-hall"));
    } catch(IOException expected) { /* The platform reported the connection failure directly. */ }
  }
  @Test public void corruptReplicaAndOldServerRecoverWithoutCachingPrivateOrMalformedBodies()throws Exception{
    File directory=temporary.newFolder();var cache=new PublicCatalogCache(base,directory);
    read(cache,"dining-halls");
    File stored=Objects.requireNonNull(directory.listFiles())[0];
    try(var file=new RandomAccessFile(stored,"rw")){file.seek(file.length()-1);file.write(0);}
    read(cache,"dining-halls");assertEquals("",conditions.get(1));
    marked=false;read(cache,"dining-halls");
    assertEquals(0,Objects.requireNonNull(directory.listFiles()).length);
    read(cache,"dining-halls");assertEquals("",conditions.get(3));
  }
  @Test public void diskBudgetIsBoundedAndDoesNotTouchOtherFiles()throws Exception{
    File directory=temporary.newFolder();var store=new CatalogDiskCache(directory,1600);
    store.put("first",new CatalogDiskCache.Entry("x".repeat(600),etag));
    for(File file:Objects.requireNonNull(directory.listFiles()))file.setLastModified(1);
    store.put("second",new CatalogDiskCache.Entry("y".repeat(600),etag));
    store.put("third",new CatalogDiskCache.Entry("z".repeat(600),etag));
    assertNull(store.get("first"));assertNotNull(store.get("third"));
    assertTrue(Arrays.stream(Objects.requireNonNull(directory.listFiles())).mapToLong(File::length).sum()<=1600);
  }
}
