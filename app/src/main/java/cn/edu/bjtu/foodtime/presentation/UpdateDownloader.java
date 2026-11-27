package cn.edu.bjtu.foodtime.presentation;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import cn.edu.bjtu.foodtime.BuildConfig;
import cn.edu.bjtu.foodtime.domain.model.Models.AppRelease;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
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
    /** Called once the update mode and download size are known. */
    void plan(boolean patch, long size);
    /** percent < 0 means the total size is unknown. */
    void progress(int percent, String label, long downloaded, long total, long bytesPerSecond);
    void ready(File apk);
    void failed(String message);
  }

  private final Context context;
  private final Executor executor;
  private final Handler main = new Handler(Looper.getMainLooper());
  private Future<?> job;
  private volatile boolean cancelled;
  private int lastPercent = -2;
  private long lastReportAt;

  UpdateDownloader(Context context, Executor executor) {
    this.context = context;
    this.executor = executor;
  }

  void start(AppRelease release, Listener listener) {
    cancel();
    cancelled = false;
    lastPercent = -2;
    lastReportAt = 0;
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
      requireWebUrl(release.apkUrl());
      File directory = new File(context.getCacheDir(), "updates");
      if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("无法创建更新目录");
      File target = new File(directory, "foodtime-" + release.versionCode() + ".apk");
      ReleaseManifest manifest = ReleaseManifest.fetch(release.apkUrl(), release.versionCode());
      if (manifest == null || manifest.apkSha256() == null) {
        throw new IOException("无法获取安装包校验信息，请稍后重试");
      }
      if (target.isFile() && target.length() > 0) {
        if ((manifest.apkSize() <= 0 || target.length() == manifest.apkSize())
            && sha256(target).equals(manifest.apkSha256())) {
          verifyInstallable(target);
          ready(listener, target);
          return;
        }
        target.delete();
      }
      File built = null;
      ReleaseManifest.Patch patch = manifest.patchFor(BuildConfig.VERSION_CODE);
      if (patch != null && ReleaseManifest.isTrustedReleaseUrl(patch.url(), BuildConfig.DEBUG)) {
        post(() -> listener.plan(true, patch.size()));
        try {
          built = fromPatch(release, manifest, patch, directory, listener);
        } catch (IOException deltaFailure) {
          checkCancelled();
          // 补丁下载或应用失败（例如安装包被系统改写）时回退到完整包。
          built = null;
        }
      }
      if (built == null) {
        long fullSize = manifest.apkSize();
        post(() -> listener.plan(false, fullSize));
        File part = new File(directory, "foodtime-" + release.versionCode() + ".apk.part");
        download(release.apkUrl(), part, fullSize,
            0, 90, "正在下载完整安装包", listener);
        if (!sha256(part).equals(manifest.apkSha256())) {
          part.delete();
          throw new IOException("安装包校验失败，请重试");
        }
        checkCancelled();
        if (!part.renameTo(target)) throw new IOException("无法保存更新包");
        built = target;
      }
      verifyInstallable(built);
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
    post(() -> listener.progress(60, "正在应用增量补丁", patch.size(), patch.size(), 0));
    File target = new File(directory, "foodtime-" + release.versionCode() + ".apk");
    File temporary = new File(directory, target.getName() + ".tmp");
    try {
      BsPatch.apply(new File(context.getPackageCodePath()), part, temporary);
      checkCancelled();
      if (!sha256(temporary).equals(manifest.apkSha256())) {
        throw new IOException("增量补丁校验失败");
      }
      if (!temporary.renameTo(target)) throw new IOException("无法保存更新包");
      return target;
    } finally {
      part.delete();
      if (temporary.exists()) temporary.delete();
    }
  }

  /** Resumable download: an interrupted transfer continues from the existing .part file. */
  private void download(String address, File part, long expectedSize, int from, int to,
                        String label, Listener listener) throws IOException {
    long offset = part.isFile() ? part.length() : 0;
    if (expectedSize > 0 && offset > expectedSize) { part.delete(); offset = 0; }
    if (expectedSize > 0 && offset == expectedSize) {
      report(to, label, offset, expectedSize, 0, listener);
      return;
    }
    HttpURLConnection connection = ReleaseManifest.openTrusted(
        address, BuildConfig.DEBUG, 15000, 60000, "application/octet-stream",
        offset > 0 ? "bytes=" + offset + "-" : null);
    try {
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
      long startedAt = SystemClock.elapsedRealtime();
      long startedBytes = offset;
      long total = expectedSize > 0 ? expectedSize
          : connection.getContentLengthLong() > 0 ? offset + connection.getContentLengthLong() : -1;
      final long maxBytes = 200L * 1024 * 1024;
      if (total > maxBytes) throw new IOException("更新包大小异常");
      try (InputStream input = connection.getInputStream();
           var out = new FileOutputStream(part, append)) {
        byte[] buffer = new byte[64 * 1024];
        long written = offset;
        int count;
        while ((count = input.read(buffer)) != -1) {
          checkCancelled();
          written += count;
          if (written > maxBytes) throw new IOException("更新包超出大小限制");
          out.write(buffer, 0, count);
          int percent = total > 0 ? from + (int) ((to - from) * written / total) : -1;
          long elapsed = SystemClock.elapsedRealtime() - startedAt;
          long speed = elapsed >= 300 ? (written - startedBytes) * 1000 / elapsed : 0;
          report(percent, label, written, total, speed, listener);
        }
        if (total > 0 && written != total) throw new IOException("下载不完整，请重试");
        long elapsed = SystemClock.elapsedRealtime() - startedAt;
        long speed = elapsed >= 300 ? (written - startedBytes) * 1000 / elapsed : 0;
        report(to, label, total > 0 ? total : written, total > 0 ? total : 0, speed, listener);
      }
    } finally {
      connection.disconnect();
    }
  }

  private void report(int percent, String label, long downloaded, long total,
                      long bytesPerSecond, Listener listener) {
    long now = SystemClock.elapsedRealtime();
    if (percent == lastPercent && now - lastReportAt < 500) return;
    lastPercent = percent;
    lastReportAt = now;
    post(() -> listener.progress(percent, label, downloaded, total, bytesPerSecond));
  }

  private void ready(Listener listener, File apk) {
    post(() -> listener.ready(apk));
  }

  private void post(Runnable action) {
    main.post(() -> { if (!cancelled) action.run(); });
  }

  private static void requireWebUrl(String address) throws IOException {
    if (!ReleaseManifest.isTrustedReleaseUrl(address, BuildConfig.DEBUG)) {
      throw new IOException("更新地址不安全");
    }
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

  private void verifyInstallable(File apk) throws IOException {
    PackageManager pm = context.getPackageManager();
    if (pm == null) return;
    PackageInfo archive = readArchiveInfo(pm, apk.getAbsolutePath());
    if (archive == null) {
      apk.delete();
      throw new IOException("无法解析安装包信息，请重试");
    }
    PackageInfo installed = readInstalledInfo(pm, context.getPackageName());
    long installedCode = installed != null ? packageVersionCode(installed) : BuildConfig.VERSION_CODE;
    long archiveCode = packageVersionCode(archive);
    try {
      verifyArchiveCompatibility(
          context.getPackageName(),
          installedCode,
          signerDigests(installed),
          archive.packageName,
          archiveCode,
          signerDigests(archive));
    } catch (IOException incompatible) {
      apk.delete();
      throw incompatible;
    }
  }

  static void verifyArchiveCompatibility(
      String installedPackage,
      long installedVersionCode,
      Set<String> installedSigners,
      String archivePackage,
      long archiveVersionCode,
      Set<String> archiveSigners) throws IOException {
    if (archivePackage == null || archivePackage.isBlank()) {
      throw new IOException("无法解析安装包信息，请重试");
    }
    if (!archivePackage.equals(installedPackage)) {
      throw new IOException("当前安装的是独立测试包，无法直接覆盖更新，请前往官网下载正式版安装");
    }
    if (archiveVersionCode > 0 && archiveVersionCode <= installedVersionCode) {
      throw new IOException("下载的安装包版本不高于当前版本，请稍后重试或前往官网下载");
    }
    if (installedSigners != null && !installedSigners.isEmpty()
        && archiveSigners != null && !archiveSigners.isEmpty()
        && !installedSigners.equals(archiveSigners)) {
      throw new IOException("当前已安装版本的签名与正式版不一致，无法直接覆盖更新，请先卸载旧版后前往官网下载完整安装包");
    }
  }

  @SuppressWarnings("deprecation")
  private static PackageInfo readInstalledInfo(PackageManager pm, String packageName) {
    try {
      if (Build.VERSION.SDK_INT >= 33) {
        return pm.getPackageInfo(
            packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES));
      }
      int flags = Build.VERSION.SDK_INT >= 28
          ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
      return pm.getPackageInfo(packageName, flags);
    } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
      return null;
    }
  }

  @SuppressWarnings("deprecation")
  private static PackageInfo readArchiveInfo(PackageManager pm, String apkPath) {
    try {
      if (Build.VERSION.SDK_INT >= 33) {
        return pm.getPackageArchiveInfo(
            apkPath, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES));
      }
      int flags = Build.VERSION.SDK_INT >= 28
          ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
      return pm.getPackageArchiveInfo(apkPath, flags);
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  @SuppressWarnings("deprecation")
  private static long packageVersionCode(PackageInfo info) {
    return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
  }

  @SuppressWarnings("deprecation")
  private static Set<String> signerDigests(PackageInfo info) {
    if (info == null) return Collections.emptySet();
    Signature[] signatures = null;
    if (Build.VERSION.SDK_INT >= 28) {
      SigningInfo signing = info.signingInfo;
      if (signing != null) {
        signatures = signing.getApkContentsSigners();
        if (signatures == null || signatures.length == 0) {
          signatures = signing.getSigningCertificateHistory();
        }
      }
    } else {
      signatures = info.signatures;
    }
    if (signatures == null || signatures.length == 0) return Collections.emptySet();
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      Set<String> digests = new HashSet<>();
      for (Signature sig : signatures) {
        if (sig == null) continue;
        md.reset();
        byte[] hash = md.digest(sig.toByteArray());
        StringBuilder hex = new StringBuilder(hash.length * 2);
        for (byte b : hash) hex.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        digests.add(hex.toString());
      }
      return digests;
    } catch (java.security.NoSuchAlgorithmException impossible) {
      return Collections.emptySet();
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
