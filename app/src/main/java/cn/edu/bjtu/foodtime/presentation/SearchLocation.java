package cn.edu.bjtu.foodtime.presentation;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.*;
import android.os.*;
import java.util.function.Consumer;

/** One foreground fix per explicit action; no tracking, storage, or navigation coordinates. */
final class SearchLocation {
  static final int REQUEST_CODE=1093;
  private final Handler handler=new Handler(Looper.getMainLooper());
  private Consumer<Location> success;
  private Consumer<String> failure;
  private LocationManager manager;
  private LocationListener listener;
  private final Runnable timeout=()->finish(null,"定位超时，请到开阔处重试");
  void request(MainActivity a,Consumer<Location> success,Consumer<String> failure) {
    clear();this.success=success;this.failure=failure;
    if(a.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED
        && a.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
      a.requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION},REQUEST_CODE);
    } else start(a);
  }
  void result(MainActivity a) {
    if(success==null)return;
    if(a.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED
        || a.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED) start(a);
    else finish(null,"定位未授权，可继续搜索或在系统设置中授权");
  }
  @android.annotation.SuppressLint("MissingPermission") // Only entered after the runtime checks above; revocation is handled below.
  private void start(MainActivity a) {
    manager=a.getSystemService(LocationManager.class);
    if(manager==null){finish(null,"当前设备无法定位");return;}
    listener=location->{
      long age=SystemClock.elapsedRealtimeNanos()-location.getElapsedRealtimeNanos();
      if(age<0||age>30_000_000_000L||!location.hasAccuracy()||location.getAccuracy()>1000)return;
      finish(location,"");
    };
    try {
      int enabled=0;
      for(String provider:new String[]{LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER}) {
        if(provider.equals(LocationManager.GPS_PROVIDER)
            && a.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)continue;
        if(manager.isProviderEnabled(provider)){manager.requestLocationUpdates(provider,0,0,listener,Looper.getMainLooper());enabled++;}
      }
      if(enabled==0){finish(null,"请先开启系统定位");return;}
      handler.postDelayed(timeout,12000);
    } catch(SecurityException|IllegalArgumentException e){finish(null,"当前定位不可用，请重试");}
  }
  private void finish(Location location,String message) {
    var accepted=success;var rejected=failure;clear();
    if(location!=null){if(accepted!=null)accepted.accept(location);}else if(rejected!=null)rejected.accept(message);
  }
  void pause() { if(listener!=null)finish(null,"定位已暂停，返回后可重试"); }
  void clear() {
    handler.removeCallbacks(timeout);
    if(manager!=null&&listener!=null)try{manager.removeUpdates(listener);}catch(SecurityException ignored){}
    manager=null;listener=null;success=null;failure=null;
  }
}
