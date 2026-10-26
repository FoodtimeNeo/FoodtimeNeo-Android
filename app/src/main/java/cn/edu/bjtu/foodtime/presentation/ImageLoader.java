package cn.edu.bjtu.foodtime.presentation;

import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import cn.edu.bjtu.foodtime.domain.repository.ImageResource;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Visible-first, cancellable native image pipeline; disk/HTTP policy remains in the repository. */
final class ImageLoader {
  private record Decoded(Bitmap bitmap,String version,long freshUntil) {}
  private static final Handler MAIN=new Handler(Looper.getMainLooper());
  private static final android.util.LruCache<String,Decoded> MEMORY=new android.util.LruCache<>((int)Math.min(32L*1024*1024,Runtime.getRuntime().maxMemory()/8)) {
    @Override protected int sizeOf(String key,Decoded image){return image.bitmap().getByteCount();}
  };
  private static final ThreadPoolExecutor WORK=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new LinkedBlockingDeque<Runnable>() {
    @Override public boolean offer(Runnable task){return offerFirst(task);}
  });
  private static final Map<String,Task> TASKS=new HashMap<>();
  private static final Map<View,Runnable[]> BINDINGS=new HashMap<>();
  private static boolean paused;
  private static int epoch;
  private static final class Task {
    final String key;final List<Consumer<Bitmap>> listeners=new ArrayList<>();Future<?> future;
    Task(String key){this.key=key;}
  }
  static void clear() {
    epoch++;MEMORY.evictAll();
    for(Task task:TASKS.values())if(task.future!=null)task.future.cancel(true);
    TASKS.clear();WORK.purge();
    for(Runnable[] binding:List.copyOf(BINDINGS.values()))binding[0].run();
  }
  static void pause(){paused=true;clear();}
  static void resume(){paused=false;for(Runnable[] binding:List.copyOf(BINDINGS.values()))binding[1].run();}
  private static Runnable subscribe(MainActivity a,String path,int bound,Consumer<Bitmap> listener) {
    String identity=a.repo.imageIdentity(),key=identity+":"+path+":"+bound;int ticket=epoch;
    Decoded hit=MEMORY.get(key);
    if(hit!=null&&hit.freshUntil()>System.currentTimeMillis()){listener.accept(hit.bitmap());return ()->{};}
    Task task=TASKS.get(key);
    if(task==null) {
      task=new Task(key);TASKS.put(key,task);Task current=task;
      current.listeners.add(listener);
      current.future=WORK.submit(()->{
        Bitmap bitmap=null;ImageResource resource=null;
        try {
          if(path.startsWith("/api/")||path.startsWith("http:")||path.startsWith("https:"))resource=a.repo.loadImageResource(path);
          else {
            try(InputStream in=path.startsWith("asset:")?a.getAssets().open("images/"+path.substring(6)):a.getContentResolver().openInputStream(Uri.parse(path));var out=new ByteArrayOutputStream()) {
              byte[] block=new byte[8192];int count;
              while(in!=null&&(count=in.read(block))!=-1){if(out.size()+count>20*1024*1024)throw new IOException();out.write(block,0,count);}
              resource=new ImageResource(out.toByteArray(),false,0,path);
            }
          }
          if(Thread.currentThread().isInterrupted())return;
          Decoded decoded=MEMORY.get(key);
          if(resource.reusable()&&decoded!=null&&decoded.version().equals(resource.version()))bitmap=decoded.bitmap();
          else {
            byte[] bytes=resource.bytes();BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
            BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);options.inSampleSize=1;
            while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>bound*2)options.inSampleSize*=2;
            options.inJustDecodeBounds=false;bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
          }
        } catch(Exception|OutOfMemoryError ignored) { }
        Bitmap result=bitmap;ImageResource loaded=resource;
        MAIN.post(()->{
          if(TASKS.get(key)!=current||ticket!=epoch||!identity.equals(a.repo.imageIdentity()))return;
          TASKS.remove(key);
          if(result!=null&&loaded!=null&&loaded.reusable())MEMORY.put(key,new Decoded(result,loaded.version(),loaded.freshUntil()));
          else MEMORY.remove(key);
          for(Consumer<Bitmap> callback:List.copyOf(current.listeners))callback.accept(result);
        });
      });
    } else task.listeners.add(listener);
    Task current=task;
    return ()->{current.listeners.remove(listener);if(current.listeners.isEmpty()&&TASKS.get(key)==current){TASKS.remove(key);current.future.cancel(true);WORK.purge();}};
  }
  static void bind(MainActivity a,FrameLayout frame,ImageView image,TextView placeholder,String path,Consumer<Bitmap> display) {
    final Runnable[] release={null};final boolean[] completed={false},failed={false};final int[] generation={0};
    Runnable check=()->{
      boolean visible=!paused&&frame.isAttachedToWindow()&&frame.getWindowVisibility()==View.VISIBLE&&frame.getGlobalVisibleRect(new Rect())&&frame.getWidth()>0;
      if(!visible){if(release[0]!=null&&!completed[0]){generation[0]++;release[0].run();release[0]=null;}return;}
      if(release[0]!=null||failed[0])return;
      int bound=128;while(bound<frame.getWidth()&&bound<1600)bound*=2;bound=Math.min(1600,bound);
      int ticket=++generation[0];
      release[0]=subscribe(a,path,bound,bitmap->{
        if(a.isDestroyed()||ticket!=generation[0]||!frame.isAttachedToWindow())return;
        if(bitmap==null){failed[0]=true;placeholder.setText("图片加载失败，点此重试");placeholder.setVisibility(View.VISIBLE);return;}
        completed[0]=true;image.setImageBitmap(bitmap);placeholder.setVisibility(View.GONE);display.accept(bitmap);
      });
    };
    placeholder.setOnClickListener(v->{if(!failed[0])return;failed[0]=false;completed[0]=false;if(release[0]!=null)release[0].run();release[0]=null;check.run();});
    ViewTreeObserver.OnScrollChangedListener scroll=check::run;
    ViewTreeObserver.OnGlobalLayoutListener layout=check::run;
    Runnable reset=()->{generation[0]++;if(release[0]!=null)release[0].run();release[0]=null;completed[0]=false;failed[0]=false;image.setImageDrawable(null);placeholder.setVisibility(View.VISIBLE);};
    frame.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){
      @Override public void onViewAttachedToWindow(View v){BINDINGS.put(frame,new Runnable[]{reset,check});frame.getViewTreeObserver().addOnScrollChangedListener(scroll);frame.getViewTreeObserver().addOnGlobalLayoutListener(layout);frame.post(check);}
      @Override public void onViewDetachedFromWindow(View v){BINDINGS.remove(frame);if(frame.getViewTreeObserver().isAlive()){frame.getViewTreeObserver().removeOnScrollChangedListener(scroll);frame.getViewTreeObserver().removeOnGlobalLayoutListener(layout);}reset.run();}
    });
  }
  private ImageLoader(){}
}
