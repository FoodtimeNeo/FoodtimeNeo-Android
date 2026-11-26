package cn.edu.bjtu.foodtime.presentation;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.*;
import android.os.*;
import java.util.function.Consumer;

/** Shared foreground location helper with short-lived cache for home/search/detail and live tracking for campus navigation. */
final class SearchLocation {
  static final int REQUEST_CODE = 1093;
  private static final long CACHE_TTL_MS = 300_000L;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private Consumer<Location> success;
  private Consumer<String> failure;
  private LocationManager manager;
  private LocationListener listener;
  private LocationListener trackingListener;
  private LocationManager trackingManager;
  private Consumer<Location> trackingCallback;
  private Location cachedLocation;
  private long cachedAtMs;
  private final Runnable timeout = () -> finish(null, "定位超时，请到开阔处重试");

  boolean hasPermission(MainActivity a) {
    return a.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        || a.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
  }

  Location cachedFix() {
    if (cachedLocation == null) return null;
    if (SystemClock.elapsedRealtime() - cachedAtMs > CACHE_TTL_MS) return null;
    return cachedLocation;
  }

  void requestOrCached(MainActivity a, Consumer<Location> onSuccess, Consumer<String> onFailure) {
    Location cached = cachedFix();
    if (cached != null) {
      onSuccess.accept(cached);
      return;
    }
    request(a, onSuccess, onFailure);
  }

  void request(MainActivity a, Consumer<Location> success, Consumer<String> failure) {
    Consumer<String> superseded = this.failure;
    clear();
    if (superseded != null) superseded.accept("已发起新的定位请求");
    this.success = success;
    this.failure = failure;
    if (!hasPermission(a)) {
      a.requestPermissions(new String[]{
          Manifest.permission.ACCESS_COARSE_LOCATION,
          Manifest.permission.ACCESS_FINE_LOCATION
      }, REQUEST_CODE);
    } else {
      start(a);
    }
  }

  void result(MainActivity a) {
    if (success == null) return;
    if (hasPermission(a)) start(a);
    else finish(null, "定位未授权，可继续浏览或在系统设置中授权");
  }

  @android.annotation.SuppressLint("MissingPermission")
  private void start(MainActivity a) {
    manager = a.getSystemService(LocationManager.class);
    if (manager == null) { finish(null, "当前设备无法定位"); return; }

    // Try a very fresh last-known fix first (covers emulator geo fix and instant warm GPS)
    Location bestLast = bestLastKnown(a, manager, 60_000L);
    if (bestLast != null) {
      remember(bestLast);
      finish(bestLast, "");
      return;
    }

    listener = location -> {
      if (!isAcceptable(location)) return;
      remember(location);
      finish(location, "");
    };
    try {
      int enabled = 0;
      for (String provider : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
        if (provider.equals(LocationManager.GPS_PROVIDER)
            && a.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) continue;
        if (manager.isProviderEnabled(provider)) {
          manager.requestLocationUpdates(provider, 0, 0, listener, Looper.getMainLooper());
          enabled++;
        }
      }
      if (enabled == 0) { finish(null, "请先开启系统定位"); return; }
      handler.postDelayed(timeout, 12000);
    } catch (SecurityException | IllegalArgumentException e) {
      finish(null, "当前定位不可用，请重试");
    }
  }

  /** Continuous foreground updates while the campus map navigation screen is visible. */
  @android.annotation.SuppressLint("MissingPermission")
  boolean startTracking(MainActivity a, Consumer<Location> onFix) {
    stopTracking();
    if (!hasPermission(a)) return false;
    trackingManager = a.getSystemService(LocationManager.class);
    if (trackingManager == null) return false;
    trackingCallback = onFix;
    Location recent = cachedFix();
    if (recent == null) recent = bestLastKnown(a, trackingManager, 120_000L);
    if (recent != null) {
      remember(recent);
      onFix.accept(recent);
    }
    trackingListener = location -> {
      if (!isAcceptable(location)) return;
      remember(location);
      if (trackingCallback != null) trackingCallback.accept(location);
    };
    try {
      int enabled = 0;
      for (String provider : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
        if (provider.equals(LocationManager.GPS_PROVIDER)
            && a.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) continue;
        if (trackingManager.isProviderEnabled(provider)) {
          trackingManager.requestLocationUpdates(provider, 2500L, 2f, trackingListener, Looper.getMainLooper());
          enabled++;
        }
      }
      return enabled > 0;
    } catch (SecurityException | IllegalArgumentException e) {
      stopTracking();
      return false;
    }
  }

  void stopTracking() {
    if (trackingManager != null && trackingListener != null) {
      try { trackingManager.removeUpdates(trackingListener); } catch (SecurityException ignored) {}
    }
    trackingManager = null;
    trackingListener = null;
    trackingCallback = null;
  }

  @android.annotation.SuppressLint("MissingPermission")
  private Location bestLastKnown(MainActivity a, LocationManager lm, long maxAgeMs) {
    Location best = null;
    for (String provider : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
      if (provider.equals(LocationManager.GPS_PROVIDER)
          && a.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) continue;
      try {
        Location cand = lm.getLastKnownLocation(provider);
        if (cand == null) continue;
        long ageMs = (SystemClock.elapsedRealtimeNanos() - cand.getElapsedRealtimeNanos()) / 1_000_000L;
        if (ageMs < 0 || ageMs > maxAgeMs) continue;
        if (cand.hasAccuracy() && cand.getAccuracy() > 300) continue;
        if (best == null || cand.getElapsedRealtimeNanos() > best.getElapsedRealtimeNanos()) best = cand;
      } catch (SecurityException | IllegalArgumentException ignored) {}
    }
    return best;
  }

  private boolean isAcceptable(Location location) {
    if (location == null) return false;
    long age = SystemClock.elapsedRealtimeNanos() - location.getElapsedRealtimeNanos();
    return age >= 0 && age <= 30_000_000_000L && (!location.hasAccuracy() || location.getAccuracy() <= 1000);
  }

  private void remember(Location location) {
    cachedLocation = new Location(location);
    cachedAtMs = SystemClock.elapsedRealtime();
  }

  private void finish(Location location, String message) {
    var accepted = success;
    var rejected = failure;
    clear();
    if (location != null) {
      if (accepted != null) accepted.accept(location);
    } else if (rejected != null) {
      rejected.accept(message);
    }
  }

  void pause() {
    stopTracking();
    if (listener != null) finish(null, "定位已暂停，返回后可重试");
  }

  void clear() {
    handler.removeCallbacks(timeout);
    if (manager != null && listener != null) {
      try { manager.removeUpdates(listener); } catch (SecurityException ignored) {}
    }
    manager = null;
    listener = null;
    success = null;
    failure = null;
  }
}
