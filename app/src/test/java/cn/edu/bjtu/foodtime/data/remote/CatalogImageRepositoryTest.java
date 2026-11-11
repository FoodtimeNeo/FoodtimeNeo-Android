package cn.edu.bjtu.foodtime.data.remote;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;
import org.junit.*;
import static org.junit.Assert.*;

public class CatalogImageRepositoryTest {
  private ServerSocket server;
  private ExecutorService worker;
  private RemoteRepository repo;
  private final List<String> calls=new CopyOnWriteArrayList<>();
  private int additions;
  @Before public void start() throws Exception {
    server=new ServerSocket(0,50,InetAddress.getByName("127.0.0.1")); worker=Executors.newSingleThreadExecutor();
    worker.submit(() -> { while(!server.isClosed()) try { handle(server.accept()); } catch(Exception failure) { if(!server.isClosed()) throw new RuntimeException(failure); } });
    String base="http://127.0.0.1:"+server.getLocalPort()+"/api/v1/";
    repo=new RemoteRepository(base,new HttpTransport(base));
  }
  @After public void stop() throws Exception { server.close(); worker.shutdownNow(); }
  @Test public void readsOnlyRequestedPageAndDeletesOnlySelectedParentAndImage() {
    var page=repo.managedImages("halls","hall",2);
    assertEquals(13,page.total()); assertEquals(2,page.page()); assertFalse(page.hasMore());
    assertEquals("image",page.items().get(0).id());
    repo.removeManagedImage("stalls","stall","image");
    assertTrue(calls.contains("GET /api/v1/admin/catalog/dining-halls/hall/images?page=2&size=12"));
    assertTrue(calls.contains("DELETE /api/v1/admin/catalog/stalls/stall/images/image"));
  }
  @Test public void retriesAssociationWithoutUploadingTheSameFileAgain() throws Exception {
    File file=File.createTempFile("catalog-image-test-",".jpg");
    try {
      Files.write(file.toPath(),new byte[]{1,2,3});
      assertThrows(RuntimeException.class,() -> repo.addManagedImage("dishes","dish",file.toURI().toString()));
      repo.addManagedImage("dishes","dish",file.toURI().toString());
      assertEquals(1,calls.stream().filter(path -> path.equals("POST /api/v1/uploads/images")).count());
      assertEquals(2,additions);
    } finally { Files.deleteIfExists(file.toPath()); }
  }
  private void handle(Socket socket) throws Exception {
    try(socket) {
      socket.setSoTimeout(5000);
      var reader=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.ISO_8859_1));
      String first=reader.readLine(),line; int size=0;
      while((line=reader.readLine())!=null&&!line.isEmpty()) if(line.toLowerCase(Locale.ROOT).startsWith("content-length:")) size=Integer.parseInt(line.substring(line.indexOf(':')+1).trim());
      for(int index=0;index<size;index++) if(reader.read()<0) throw new EOFException();
      String method=first.split(" ")[0],path=first.split(" ")[1]; calls.add(method+" "+path);
      JSONObject data=new JSONObject(); int status=200;
      if(path.endsWith("/auth/csrf")) data.put("token","test-csrf").put("headerName","X-CSRF-TOKEN");
      else if(path.equals("/api/v1/uploads/images")) data.put("imageUrl","/api/v1/media/uploaded");
      else if(method.equals("POST")) { additions++; if(additions==1) status=503; }
      else if(method.equals("GET")) data.put("items",new JSONArray().put(new JSONObject().put("id","image").put("imageUrl","/api/v1/media/photo"))).put("total",13);
      byte[] bytes=new JSONObject().put("code",status==200?"OK":"UNAVAILABLE").put("message",status==200?"OK":"retry").put("data",data).toString().getBytes(StandardCharsets.UTF_8);
      var out=socket.getOutputStream();out.write(("HTTP/1.1 "+status+" OK\r\nContent-Type: application/json\r\nContent-Length: "+bytes.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));out.write(bytes);out.flush();
    }
  }
}
