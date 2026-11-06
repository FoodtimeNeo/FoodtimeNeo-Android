package cn.edu.bjtu.foodtime.presentation;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Optional release manifest published next to the APK; enables delta downloads and checksums. */
final class ReleaseManifest {
  private static final int MAX_BYTES = 256 * 1024;

  record Patch(int baseVersionCode, String url, String sha256, long size) { }

  private final int versionCode;
  private final String apkSha256;
  private final long apkSize;
  private final List<Patch> patches;

  private ReleaseManifest(int versionCode, String apkSha256, long apkSize, List<Patch> patches) {
    this.versionCode = versionCode;
    this.apkSha256 = apkSha256;
    this.apkSize = apkSize;
    this.patches = patches;
  }

  int versionCode() { return versionCode; }
  String apkSha256() { return apkSha256; }
  long apkSize() { return apkSize; }

  Patch patchFor(int installedVersionCode) {
    for (Patch patch : patches) if (patch.baseVersionCode() == installedVersionCode) return patch;
    return null;
  }

  /** The manifest is published as a sibling of the APK asset. */
  static String manifestUrl(String apkUrl) {
    try {
      return new URL(new URL(apkUrl), "manifest.json").toString();
    } catch (IOException invalid) {
      return null;
    }
  }

  static ReleaseManifest fetch(String apkUrl, int expectedVersionCode) {
    String address = manifestUrl(apkUrl);
    if (address == null || !webUrl(address)) return null;
    HttpURLConnection connection = null;
    try {
      connection = (HttpURLConnection) new URL(address).openConnection();
      connection.setConnectTimeout(5000);
      connection.setReadTimeout(10000);
      connection.setRequestProperty("Accept", "application/json");
      if (connection.getResponseCode() != 200) return null;
      try (InputStream input = connection.getInputStream(); var out = new ByteArrayOutputStream()) {
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
          if (out.size() > MAX_BYTES) return null;
          out.write(buffer, 0, count);
        }
        return parse(new String(out.toByteArray(), StandardCharsets.UTF_8), expectedVersionCode);
      }
    } catch (IOException | RuntimeException unavailable) {
      return null;
    } finally {
      if (connection != null) connection.disconnect();
    }
  }

  /** Returns null instead of throwing: a missing or malformed manifest simply disables delta updates. */
  static ReleaseManifest parse(String json, int expectedVersionCode) {
    try {
      JSONObject root = new JSONObject(json);
      int versionCode = root.optInt("versionCode", 0);
      if (versionCode != expectedVersionCode) return null;
      JSONObject apk = root.optJSONObject("apk");
      if (apk == null) return null;
      String apkSha = sha(apk.optString("sha256", ""));
      long apkSize = apk.optLong("size", 0);
      List<Patch> patches = new ArrayList<>();
      JSONArray list = root.optJSONArray("patches");
      if (list != null) {
        for (int i = 0; i < list.length(); i++) {
          JSONObject item = list.optJSONObject(i);
          if (item == null) continue;
          int base = item.optInt("baseVersionCode", 0);
          String url = item.optString("url", "");
          String digest = sha(item.optString("sha256", ""));
          long size = item.optLong("size", 0);
          if (base <= 0 || digest == null || !secure(url)) continue;
          patches.add(new Patch(base, url, digest, Math.max(0, size)));
        }
      }
      return new ReleaseManifest(versionCode, apkSha, Math.max(0, apkSize),
          java.util.Collections.unmodifiableList(new ArrayList<>(patches)));
    } catch (Exception malformed) {
      return null;
    }
  }

  private static boolean webUrl(String url) {
    try {
      String scheme = new URL(url).getProtocol();
      return "https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme);
    } catch (IOException invalid) {
      return false;
    }
  }

  private static boolean secure(String url) {
    try {
      URL parsed = new URL(url);
      return "https".equalsIgnoreCase(parsed.getProtocol()) && parsed.getHost() != null;
    } catch (IOException invalid) {
      return false;
    }
  }

  private static String sha(String value) {
    String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    return normalized.matches("[0-9a-f]{64}") ? normalized : null;
  }
}
