package cn.edu.bjtu.foodtime.data.remote;

import android.app.Instrumentation;
import android.os.Bundle;
import android.graphics.Bitmap;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Real device HTTP/disk cache contract checks; uses a test-owned loopback server only. */
public final class ImageCacheRunner extends Instrumentation {
  private int checks;
  private final AtomicInteger publicReads=new AtomicInteger(),privateReads=new AtomicInteger(),noStoreReads=new AtomicInteger(),transferred=new AtomicInteger();
  private volatile boolean deny;
  private final CountDownLatch slowStarted=new CountDownLatch(1),releaseSlow=new CountDownLatch(1);
  private void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
  @Override public void onCreate(Bundle args){super.onCreate(args);start();}
  @Override public void onStart(){
    Bundle report=new Bundle();ExecutorService serverWorkers=Executors.newCachedThreadPool();File folder=new File(getTargetContext().getCacheDir(),"cache-contract-"+UUID.randomUUID());
    try(ServerSocket server=new ServerSocket(0,10,InetAddress.getByName("127.0.0.1"))) {
      Bitmap bitmap=Bitmap.createBitmap(64,48,Bitmap.Config.ARGB_8888);bitmap.eraseColor(0xffc9341e);
      ByteArrayOutputStream image=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.PNG,100,image);bitmap.recycle();byte[] png=image.toByteArray();
      serverWorkers.submit(()->{while(!server.isClosed())try{Socket socket=server.accept();serverWorkers.submit(()->{
        try(socket){
          var reader=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));String path=reader.readLine().split(" ")[1];Map<String,String> headers=new HashMap<>();String line;
          while((line=reader.readLine())!=null&&!line.isEmpty()){int colon=line.indexOf(':');headers.put(line.substring(0,colon).toLowerCase(Locale.ROOT),line.substring(colon+1).trim());}
          boolean conditional=headers.containsKey("if-none-match");int status=200;String policy="no-store",extra="";
          if(path.contains("public")){publicReads.incrementAndGet();policy="public, max-age=31536000, immutable";}
          if(path.contains("private")){privateReads.incrementAndGet();policy="private, no-cache";if(deny)status=404;else if(conditional)status=304;}
          if(path.contains("nostore"))noStoreReads.incrementAndGet();
          if(path.contains("slow")){slowStarted.countDown();releaseSlow.await(5,TimeUnit.SECONDS);extra="Set-Cookie: FOODTIME_SESSION=late; Path=/\r\n";}
          byte[] bytes=status==200?png:new byte[0];transferred.addAndGet(bytes.length);
          socket.getOutputStream().write(("HTTP/1.1 "+status+" Result\r\nContent-Type: image/png\r\nCache-Control: "+policy+"\r\nETag: \""+(path.contains("thumb")?"thumb":"full")+"\"\r\n"+extra+"Content-Length: "+bytes.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));socket.getOutputStream().write(bytes);
        }catch(Exception ignored){}
      });}catch(IOException closed){break;}});
      String base="http://127.0.0.1:"+server.getLocalPort()+"/api/v1/";
      HttpTransport transport=new HttpTransport(base);RemoteRepository repo=new RemoteRepository(base,transport).withImageCache(folder);
      byte[] first=repo.loadImage("/api/v1/media/public?size=thumb");int coldBytes=transferred.get();
      for(int i=0;i<3;i++)check(Arrays.equals(first,repo.loadImage("/api/v1/media/public?size=thumb")),"Public hot bytes preserved");
      check(publicReads.get()==1,"Fresh public cache avoids HTTP");
      RemoteRepository restarted=new RemoteRepository(base,new HttpTransport(base)).withImageCache(folder);
      check(Arrays.equals(first,restarted.loadImage("/api/v1/media/public?size=thumb")),"Restart reuses public disk bytes");check(publicReads.get()==1,"Restart avoids body transfer");
      repo.loadImage("/api/v1/media/public?size=full");check(publicReads.get()==2,"Full and thumbnail remain distinct");
      byte[] privateFirst=repo.loadImage("/api/v1/media/private");int before=transferred.get();
      check(Arrays.equals(privateFirst,repo.loadImage("/api/v1/media/private")),"Private 304 reuses validated bytes");check(privateReads.get()==2,"Private image always validates");check(transferred.get()==before,"Private revalidation transfers no body");
      deny=true;boolean rejected=false;try{repo.loadImage("/api/v1/media/private");}catch(RuntimeException expected){rejected=true;}check(rejected,"Revoked image never falls back to stale bytes");
      long size=repo.imageCacheBytes();repo.loadImage("/api/v1/media/nostore");repo.loadImage("/api/v1/media/nostore");check(noStoreReads.get()==2,"No-store is fetched each time");check(repo.imageCacheBytes()==size,"No-store never touches persistent cache");
      Future<?> slow=serverWorkers.submit(()->{try{transport.bytes(base+"media/slow");throw new AssertionError("Late response accepted");}catch(IOException expected){}});
      check(slowStarted.await(3,TimeUnit.SECONDS),"Slow image request started");long started=System.currentTimeMillis();transport.request("GET","echo",null,null);check(System.currentTimeMillis()-started<1000,"Slow image does not hold business transport lock");
      transport.clearSession();releaseSlow.countDown();slow.get(5,TimeUnit.SECONDS);check(transport.cookieManager().getCookieStore().getCookies().isEmpty(),"Late response cannot restore signed-out cookies");
      repo.clearImageCache();check(repo.imageCacheBytes()==0,"Image clear removes only owned cache");
      report.putString("stream","Image cache device PASS: "+checks+" checks; public coldBytes="+coldBytes+", hotBodyBytes=0; private revalidationBodyBytes=0\n");finish(-1,report);
    }catch(Throwable error){StringWriter stack=new StringWriter();error.printStackTrace(new PrintWriter(stack));report.putString("stream","Image cache FAIL after "+checks+" checks\n"+stack);finish(1,report);}
    finally{releaseSlow.countDown();serverWorkers.shutdownNow();new ImageDiskCache(folder,200L*1024*1024).clear(false);folder.delete();}
  }
}
