package cn.edu.bjtu.foodtime.presentation;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class ReleaseManifestTest {
  private static final String SHA = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

  @Test
  public void parsesPatchesAndSelectsTheInstalledBaseVersion() {
    String json = "{\"versionCode\":10,\"versionName\":\"1.3.0\","
        + "\"apk\":{\"url\":\"https://example.com/app-release.apk\",\"sha256\":\"" + SHA + "\",\"size\":1234},"
        + "\"patches\":["
        + "{\"baseVersionCode\":9,\"url\":\"https://example.com/p9.patch\",\"sha256\":\"" + SHA + "\",\"size\":100},"
        + "{\"baseVersionCode\":8,\"url\":\"https://example.com/p8.patch\",\"sha256\":\"" + SHA + "\",\"size\":120}"
        + "]}";
    ReleaseManifest manifest = ReleaseManifest.parse(json, 10);
    assertNotNull(manifest);
    assertEquals(SHA, manifest.apkSha256());
    assertEquals(1234, manifest.apkSize());
    assertNotNull(manifest.patchFor(9));
    assertNull(manifest.patchFor(7));
    assertEquals("https://example.com/p8.patch", manifest.patchFor(8).url());
  }

  @Test
  public void ignoresManifestsForAnotherVersionOrWithUnsafeUrls() {
    String wrongVersion = "{\"versionCode\":11,\"apk\":{\"sha256\":\"" + SHA + "\"},\"patches\":[]}";
    assertNull(ReleaseManifest.parse(wrongVersion, 10));

    String httpPatch = "{\"versionCode\":10,\"apk\":{\"sha256\":\"" + SHA + "\"},"
        + "\"patches\":[{\"baseVersionCode\":9,\"url\":\"http://example.com/p.patch\",\"sha256\":\"" + SHA + "\"}]}";
    ReleaseManifest manifest = ReleaseManifest.parse(httpPatch, 10);
    assertNotNull(manifest);
    assertNull(manifest.patchFor(9));

    String badSha = "{\"versionCode\":10,\"apk\":{\"sha256\":\"nope\"},"
        + "\"patches\":[{\"baseVersionCode\":9,\"url\":\"https://example.com/p.patch\",\"sha256\":\"nope\"}]}";
    ReleaseManifest invalid = ReleaseManifest.parse(badSha, 10);
    assertNotNull(invalid);
    assertNull(invalid.apkSha256());
    assertNull(invalid.patchFor(9));
  }

  @Test
  public void resolvesTheManifestBesideTheApkAsset() {
    assertEquals("https://github.com/o/r/releases/download/v1.3.0/manifest.json",
        ReleaseManifest.manifestUrl("https://github.com/o/r/releases/download/v1.3.0/app-release.apk"));
    assertEquals("https://example.com/releases/manifest.json",
        ReleaseManifest.manifestUrl("https://example.com/releases/app-release.apk?x=1"));
  }

  @Test
  public void allowsOnlyTrustedHttpsReleaseHosts() {
    org.junit.Assert.assertTrue(ReleaseManifest.isTrustedReleaseUrl(
        "https://bjtufoodtime.top/downloads/FoodtimeNeo-Android.apk", false));
    org.junit.Assert.assertTrue(ReleaseManifest.isTrustedReleaseUrl(
        "https://github.com/o/r/releases/download/v2.1.8/app.apk", false));
    org.junit.Assert.assertFalse(ReleaseManifest.isTrustedReleaseUrl(
        "http://bjtufoodtime.top/downloads/FoodtimeNeo-Android.apk", false));
    org.junit.Assert.assertFalse(ReleaseManifest.isTrustedReleaseUrl(
        "https://evil.example/app.apk", false));
    org.junit.Assert.assertFalse(ReleaseManifest.isTrustedReleaseUrl(
        "http://127.0.0.1:8080/app.apk", false));
    org.junit.Assert.assertTrue(ReleaseManifest.isTrustedReleaseUrl(
        "http://127.0.0.1:8080/app.apk", true));
  }
}
