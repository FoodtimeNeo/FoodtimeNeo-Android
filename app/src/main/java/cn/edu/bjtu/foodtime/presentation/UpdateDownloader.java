package cn.edu.bjtu.foodtime.presentation;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import cn.edu.bjtu.foodtime.BuildConfig;
import cn.edu.bjtu.foodtime.domain.model.Models.AppRelease;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

/**
 * Downloads an update in-app. When GitHub publishes a bsdiff patch for the installed
 * version it downloads only the patch; otherwise it falls back to the full APK.
 * Both paths verify SHA-256 before the installer is launched.
 */
final class UpdateDownloader {
  interface Listener {
    /** percent < 0 means the total size is unknown. */
    void progress(int percent, String label);
    void ready(File apk);
    void failed(String message);
  }

  private final Context context;
  private final Executor executor;
  private final Handler main = new Handler(Looper.getMainLooper());
  private Future<?> job;
  private volatile boolean cancelled;
  private int lastPercent = -2;

  UpdateDownloader(Context context, Executor executor) {
    this.context = context;
    this.executor = executor;
  }

  void start(AppRelease release, Listener listener) {
    cancel();
    cancelled = false;
    lastPercent = -2;
    FutureTask<Void> task = new FutureTask<>(() -> { run(release, listener); return null; });
    job = task;
    executor.execute(task);
  }

  void cancel() {
    cancelled = true;
    Future<?> running = job;
    job = null;
    if (running != null) running.cancel(true);
  }

  private void run(AppRelease release, Listener listener) throws IOException {
    try {
      File directory = new File(context.getCacheDir(), "updates");
      if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("无法创建更新目录");
      File target = new File(directory, "foodtime-" + release.versionCode() + ".apk");
      ReleaseManifest manifest = ReleaseManifest.fetch(release.apkUrl(), release.versionCode());
      if (target.isFile() && target.length() > 0) {
        if (manifest == null || target.length() == manifest.apkSize()
            && sha256(target).equals(manifest.apkSha256())) {
          ready(listener, target);
          return;
        }
        target.delete();
      }
      File built = null;
      ReleaseManifest.Patch patch = manifest == null || manifest.apkSha256() == null
          ? null : manifest.patchFor(BuildConfig.VERSION_CODE);
      if (patch != null) {
        try {
          built = fromPatch(release, manifest, patch, directory, listener);
        } catch (IOException deltaFailure) {
          checkCancelled();
          // 补丁下载或应用失败（例如安装包被系统改写）时回退到完整包。
          built = null;
        }
      }
      if (built == null) {
        File part = new File(directory, "foodtime-" + release.versionCode() + ".apk.part");
        download(release.apkUrl(), part, manifest == null ? 0 : manifest.apkSize(),
            0, 90, "正在下载完整安装包", listener);
        if (manifest != null && manifest.apkSha256() != null
            && !sha256(part).equals(manifest.apkSha256())) {
          part.delete();
          throw new IOException("安装包校验失败，请重试");
        }
        checkCancelled();
        if (!part.renameTo(target)) throw new IOException("无法保存更新包");
        built = target;
      }
      ready(listener, built);
    } catch (Exception failure) {
      checkCancelled();
      String message = failure.getMessage();
      post(() -> listener.failed(message == null || message.trim().isEmpty()
          ? "更新失败，请稍后重试" : message));
    }
  }

  private File fromPatch(AppRelease release, ReleaseManifest manifest, ReleaseManifest.Patch patch,
                         File directory, Listener listener) throws IOException {
    File part = new File(directory, "patch-" + release.versionCode() + ".part");
    download(patch.url(), part, patch.size(), 0, 55, "正在下载增量补丁", listener);
    checkCancelled();
    if (!sha256(part).equals(patch.sha256())) {
      part.delete();
      throw new IOException("增量补丁校验失败");
    }
    post(() -> listener.progress(60, "正在应用增量补丁"));
    File target = new File(directory, "foodtime-" + release.versionCode() + ".apk");
    File temporary = new File(directory, target.getName() + ".tmp");
    BsPatch.apply(new File(context.getPackageCodePath()), part, temporary);
    part.delete();
    checkCancelled();
    if (!sha256(temporary).equals(manifest.apkSha256())) {
      temporary.delete();
      throw new IOException("增量补丁校验失败");
    }
    if (!temporary.renameTo(target)) throw new IOException("无法保存更新包");
    return target;
  }

  /** Resumable download: an interrupted transfer continues from the existing .part file. */
  private void download(String address, File part, long expectedSize, int from, int to,
                        String label, Listener listener) throws IOException {
    long offset = part.isFile() ? part.length() : 0;
    if (expectedSize > 0 && offset > expectedSize) { part.delete(); offset = 0; }
    if (expectedSize > 0 && offset == expectedSize) {
      listener.progress(to, label);
      return;
    }
    HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
    try {
      connection.setConnectTimeout(15000);
      connection.setReadTimeout(60000);
      connection.setRequestProperty("Accept", "application/octet-stream");
      if (offset > 0) connection.setRequestProperty("Range", "bytes=" + offset + "-");
      int status = connection.getResponseCode();
      boolean append;
      if (status == 206 && offset > 0) {
        append = true;
      } else if (status == 200) {
        append = false;
        offset = 0;
      } else {
        throw new IOException("下载失败（HTTP " + status + "）");
      }
      long total = expectedSize > 0 ? expectedSize
          : connection.getContentLengthLong() > 0 ? offset + connection.getContentLengthLong() : -1;
      try (InputStream input = connection.getInputStream();
           var out = new FileOutputStream(part, append)) {
        byte[] buffer = new byte[64 * 1024];
        long written = offset;
        int count;
        while ((count = input.read(buffer)) != -1) {
          checkCancelled();
          out.write(buffer, 0, count);
          written += count;
          int percent = total > 0 ? from + (int) ((to - from) * written / total) : -1;
          report(percent, label, listener);
        }
        if (total > 0 && written != total) throw new IOException("下载不完整，请重试");
        report(to, label, listener);
      }
    } finally {
      connection.disconnect();
    }
  }

  private void report(int percent, String label, Listener listener) {
    if (percent == lastPercent) return;
    lastPercent = percent;
    post(() -> listener.progress(percent, label));
  }

  private void ready(Listener listener, File apk) {
    post(() -> listener.ready(apk));
  }

  private void post(Runnable action) {
    main.post(() -> { if (!cancelled) action.run(); });
  }

  private static void checkCancelled() throws IOException {
    if (Thread.currentThread().isInterrupted()) throw new IOException("更新已取消");
  }

  private static String sha256(File file) throws IOException {
    try (InputStream input = new java.io.FileInputStream(file)) {
      MessageDigest digest;
      try {
        digest = MessageDigest.getInstance("SHA-256");
      } catch (java.security.NoSuchAlgorithmException impossible) {
        throw new IOException("设备不支持 SHA-256");
      }
      byte[] buffer = new byte[64 * 1024];
      int count;
      while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
      StringBuilder value = new StringBuilder();
      for (byte item : digest.digest()) value.append(String.format(java.util.Locale.ROOT, "%02x", item & 0xff));
      return value.toString();
    }
  }

  static void install(Context context, File apk) {
    Uri uri = ApkProvider.uriFor(context, apk);
    Intent intent = new Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, "application/vnd.android.package-archive")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
    context.startActivity(intent);
  }
}
