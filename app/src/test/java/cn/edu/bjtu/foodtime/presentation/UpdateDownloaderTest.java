package cn.edu.bjtu.foodtime.presentation;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.Set;
import org.junit.Test;

public class UpdateDownloaderTest {
  private static final String OFFICIAL_CERT =
      "94e7db3a30937a0cd8bc8596f80666122e3bd3f3323e55bfef14f72e9c454b92";
  private static final String DEBUG_CERT =
      "1111111111111111111111111111111111111111111111111111111111111111";

  @Test
  public void allowsMatchingPackageSignerAndHigherVersionCode() throws IOException {
    UpdateDownloader.verifyArchiveCompatibility(
        "cn.edu.bjtu.foodtime",
        27,
        Set.of(OFFICIAL_CERT),
        "cn.edu.bjtu.foodtime",
        36,
        Set.of(OFFICIAL_CERT));
  }

  @Test
  public void rejectsSignatureMismatchWithClearUninstallHint() {
    try {
      UpdateDownloader.verifyArchiveCompatibility(
          "cn.edu.bjtu.foodtime",
          35,
          Set.of(DEBUG_CERT),
          "cn.edu.bjtu.foodtime",
          36,
          Set.of(OFFICIAL_CERT));
      fail("Expected IOException for signer mismatch");
    } catch (IOException expected) {
      assertTrue(expected.getMessage().contains("签名与正式版不一致"));
      assertTrue(expected.getMessage().contains("卸载旧版"));
    }
  }

  @Test
  public void rejectsDifferentPackageNameForDebugInstall() {
    try {
      UpdateDownloader.verifyArchiveCompatibility(
          "cn.edu.bjtu.foodtime.debug",
          35,
          Set.of(DEBUG_CERT),
          "cn.edu.bjtu.foodtime",
          36,
          Set.of(OFFICIAL_CERT));
      fail("Expected IOException for package mismatch");
    } catch (IOException expected) {
      assertTrue(expected.getMessage().contains("独立测试包"));
    }
  }

  @Test
  public void rejectsSameOrLowerVersionCode() {
    try {
      UpdateDownloader.verifyArchiveCompatibility(
          "cn.edu.bjtu.foodtime",
          36,
          Set.of(OFFICIAL_CERT),
          "cn.edu.bjtu.foodtime",
          36,
          Set.of(OFFICIAL_CERT));
      fail("Expected IOException for non-increasing versionCode");
    } catch (IOException expected) {
      assertTrue(expected.getMessage().contains("版本不高于当前版本"));
    }
  }
}
