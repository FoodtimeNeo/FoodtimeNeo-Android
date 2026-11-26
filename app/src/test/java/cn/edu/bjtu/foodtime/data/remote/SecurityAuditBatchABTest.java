package cn.edu.bjtu.foodtime.data.remote;

import cn.edu.bjtu.foodtime.domain.validation.Validation;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class SecurityAuditBatchABTest {
  @Rule public final TemporaryFolder temp = new TemporaryFolder();

  @Test
  public void safeRemoteImageReferenceRejectsLocalSchemes() {
    assertEquals("/api/v1/media/1234", Validation.safeRemoteImageReference("/api/v1/media/1234"));
    assertEquals("https://bjtufoodtime.top/a.png", Validation.safeRemoteImageReference("https://bjtufoodtime.top/a.png"));
    assertEquals("", Validation.safeRemoteImageReference("file:///data/data/cn.edu.bjtu.foodtime/shared_prefs/x.xml"));
    assertEquals("", Validation.safeRemoteImageReference("content://com.example/secret"));
    assertEquals("", Validation.safeRemoteImageReference("javascript:alert(1)"));
    assertEquals("", Validation.safeRemoteImageReference("http://insecure.example/a.png"));
  }

  @Test
  public void uploadRejectsFilesOutsideConfiguredPhotoDirectory() throws Exception {
    File allowedDir = temp.newFolder("files");
    File outsideFile = temp.newFile("secret.xml");
    Files.write(outsideFile.toPath(), "<secret/>".getBytes(StandardCharsets.UTF_8));
    File wrongPrefix = new File(allowedDir, "other.jpg");
    Files.write(wrongPrefix.toPath(), "x".getBytes(StandardCharsets.UTF_8));

    var http = new HttpTransport("https://bjtufoodtime.top/api/v1/",
        new CookieManager(null, CookiePolicy.ACCEPT_NONE), false);
    var repo = new RemoteRepository("https://bjtufoodtime.top/api/v1/", http)
        .withUploadDirectory(allowedDir);

    assertThrows(IllegalArgumentException.class,
        () -> repo.upload(outsideFile.toURI().toString(), true));
    assertThrows(IllegalArgumentException.class,
        () -> repo.upload(wrongPrefix.toURI().toString(), false));
  }
}
