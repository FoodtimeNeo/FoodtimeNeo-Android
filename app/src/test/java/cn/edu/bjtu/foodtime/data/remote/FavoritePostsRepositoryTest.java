package cn.edu.bjtu.foodtime.data.remote;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class FavoritePostsRepositoryTest {
  @Test public void defaultAndCustomViewsUseIndependentFiltersAndServerTotals()throws Exception {
    try(var server=new ServerSocket(0,5,InetAddress.getByName("127.0.0.1"))){
      var worker=Executors.newSingleThreadExecutor();
      try{
        Future<java.util.List<String>> requests=worker.submit(()->{
          var paths=new java.util.ArrayList<String>();
          for(int i=0;i<2;i++)try(var socket=server.accept()){
            socket.setSoTimeout(5000);var reader=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));
            paths.add(reader.readLine());String line;while((line=reader.readLine())!=null&&!line.isEmpty()){}
            byte[] body="{\"code\":\"OK\",\"data\":{\"items\":[],\"total\":41,\"page\":2,\"size\":20}}".getBytes(StandardCharsets.UTF_8);
            var out=socket.getOutputStream();out.write(("HTTP/1.1 200 OK\r\nContent-Length: "+body.length+"\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));out.write(body);
          }return paths;
        });
        String base="http://127.0.0.1:"+server.getLocalPort()+"/api/v1/";var repo=new RemoteRepository(base,new HttpTransport(base));
        var page=repo.favoritePosts(2,null,true);assertEquals(41,page.total());assertEquals(2,page.page());assertTrue(page.hasMore());
        repo.favoritePosts(1,"group & one",false);
        assertEquals(java.util.List.of("GET /api/v1/users/me/community-favorites?page=2&size=20&unfiled=true HTTP/1.1",
            "GET /api/v1/users/me/community-favorites?page=1&size=20&groupId=group+%26+one HTTP/1.1"),requests.get(5,TimeUnit.SECONDS));
      }finally{worker.shutdownNow();}
    }
  }
}
