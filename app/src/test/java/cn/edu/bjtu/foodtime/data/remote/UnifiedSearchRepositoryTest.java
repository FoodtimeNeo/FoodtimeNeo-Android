package cn.edu.bjtu.foodtime.data.remote;
import cn.edu.bjtu.foodtime.domain.model.SearchModels.Query;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;
public class UnifiedSearchRepositoryTest {
  private static String line(InputStream input)throws IOException {
    var bytes=new ByteArrayOutputStream();int value;
    while((value=input.read())!=-1){if(value==10)break;if(value!=13)bytes.write(value);}
    return bytes.toString(StandardCharsets.UTF_8);
  }
  @Test public void locationUsesPostBodyAndParsesGroupedDishImagesWithoutExtraCalls()throws Exception {
    var request=new AtomicReference<String>();var uri=new AtomicReference<String>();
    try(var server=new ServerSocket(0,2,InetAddress.getByName("127.0.0.1"))) {
      server.setSoTimeout(10000);var executor=Executors.newSingleThreadExecutor();
      try {
        var served=executor.submit(()->{for(int i=0;i<2;i++)try(var socket=server.accept()){
          socket.setSoTimeout(10000);var input=socket.getInputStream();String path=line(input);int length=0;
          for(String header;(header=line(input))!=null&&!header.isEmpty();)if(header.toLowerCase(java.util.Locale.ROOT).startsWith("content-length:"))length=Integer.parseInt(header.substring(15).strip());
          String payload;
          if(path.contains("auth/csrf"))payload="{\"token\":\"test\"}";
          else {request.set(new String(input.readNBytes(length),StandardCharsets.UTF_8));uri.set(path);payload="""
            {"items":[{"id":"stall","name":"面馆","diningHallId":"hall","diningHallName":"明湖","distanceMeters":120,"matchCount":8,"dishes":[{"id":"dish","name":"面","price":12,"priceUnit":"bowl","imageUrl":"/media/dish.webp"}]}],"total":1,"page":1,"size":10}
            """;}
          byte[] body=("{\"code\":\"OK\",\"data\":"+payload+"}").getBytes(StandardCharsets.UTF_8);
          var output=socket.getOutputStream();output.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: "+body.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));output.write(body);
        }return true;});
        String base="http://127.0.0.1:"+server.getLocalPort()+"/api/v1/";var repository=new RemoteRepository(base,new HttpTransport(base));
        var data=repository.searchStalls(new Query("牛肉",List.of("hall"),null,null,null,"distance","all",39.95,116.34,4),1);
        assertTrue(served.get(10,TimeUnit.SECONDS));assertEquals("POST /api/v1/search/stalls/query HTTP/1.1",uri.get());assertFalse(uri.get().contains("39.95"));
        var body=new JSONObject(request.get());assertEquals(39.95,body.getDouble("latitude"),0.001);assertEquals(4,body.getInt("previewSize"));assertEquals(8,data.items().get(0).matchCount());assertEquals("/media/dish.webp",data.items().get(0).dishes().get(0).imageUrl());assertEquals(120,data.items().get(0).distanceMeters(),0.001);
      }finally{executor.shutdownNow();}
    }
  }
}
