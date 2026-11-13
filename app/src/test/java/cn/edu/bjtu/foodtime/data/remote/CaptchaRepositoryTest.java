package cn.edu.bjtu.foodtime.data.remote;

import cn.edu.bjtu.foodtime.domain.repository.RepositoryException;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.json.*;
import org.junit.*;
import static org.junit.Assert.*;

/** CAPTCHA image fetching and optional login challenge transport. */
public class CaptchaRepositoryTest {
  private ServerSocket server;
  private ExecutorService workers;
  private RemoteRepository repo;
  private final AtomicReference<String> loginBody = new AtomicReference<>();
  private final AtomicReference<String> captchaJson = new AtomicReference<>();
  private final String userId = "00000000-0000-4000-8000-000000000001";

  @Before public void start() throws Exception {
    captchaJson.set(new JSONObject()
        .put("challengeId", "00000000-0000-4000-8000-000000000001")
        .put("imageBase64", "iVBORw0KGgo=").put("expiresAt", "2026-11-12T00:02:00Z").toString());
    server = new ServerSocket(0, 20, InetAddress.getByName("127.0.0.1"));
    workers = Executors.newCachedThreadPool();
    workers.submit(() -> {
      while (!server.isClosed()) {
        try { Socket socket = server.accept(); workers.submit(() -> handle(socket)); }
        catch (IOException closed) { if (!server.isClosed()) throw new UncheckedIOException(closed); }
      }
    });
    String base = "http://127.0.0.1:" + server.getLocalPort() + "/api/v1/";
    repo = new RemoteRepository(base, new HttpTransport(base));
  }

  private void handle(Socket socket) {
    try (socket) {
      var reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
      String first = reader.readLine();
      if (first == null) return;
      int length = 0;
      String line;
      while ((line = reader.readLine()) != null && !line.isEmpty()) {
        if (line.toLowerCase(Locale.ROOT).startsWith("content-length:")) {
          length = Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
        }
      }
      var body = new StringBuilder();
      for (int i = 0; i < length; i++) body.append((char) reader.read());
      String path = first.split(" ")[1];
      JSONObject data = new JSONObject();
      if (path.endsWith("/auth/csrf")) {
        data.put("token", "local-token");
      } else if (path.endsWith("/auth/captcha")) {
        data = new JSONObject(captchaJson.get());
      } else if (path.endsWith("/auth/login")) {
        loginBody.set(body.toString());
      } else if (path.endsWith("/users/me/profile")) {
        data.put("id", userId).put("email", "user@bjtu.edu.cn").put("displayName", "用户")
            .put("role", "user").put("status", "active").put("createdAt", "2026-11-12T00:00:00Z");
      } else {
        send(socket, 404, new JSONObject());
        return;
      }
      send(socket, 200, data);
    } catch (Exception canceled) {
      // Canceled requests may close the socket before a response is written.
    }
  }

  private static void send(Socket socket, int status, JSONObject data) throws Exception {
    byte[] bytes = new JSONObject().put("code", status == 200 ? "OK" : "NOT_FOUND")
        .put("message", "local").put("data", data).toString().getBytes(StandardCharsets.UTF_8);
    var out = socket.getOutputStream();
    out.write(("HTTP/1.1 " + status + " Test\r\nContent-Type: application/json\r\nContent-Length: "
        + bytes.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
    out.write(bytes);
    out.flush();
  }

  @After public void stop() throws Exception {
    server.close();
    workers.shutdownNow();
  }

  @Test public void fetchesTheChallengeImageAndExpiry() {
    var challenge = repo.captcha();
    assertEquals("00000000-0000-4000-8000-000000000001", challenge.challengeId());
    assertEquals("iVBORw0KGgo=", challenge.imageBase64());
    assertEquals("2026-11-12T00:02:00Z", challenge.expiresAt());
  }

  @Test public void loginSendsTheOptionalChallengeFields() throws Exception {
    repo.login("20260001@bjtu.edu.cn", "Foodtime2026!", "00000000-0000-4000-8000-000000000001", " 7 ");
    var body = new JSONObject(loginBody.get());
    assertEquals("00000000-0000-4000-8000-000000000001", body.getString("challengeId"));
    assertEquals("7", body.getString("challengeAnswer"));
    assertEquals("20260001@bjtu.edu.cn", body.getString("account"));
  }

  @Test public void loginWithoutChallengeKeepsTheLegacyRequest() throws Exception {
    repo.login("20260001@bjtu.edu.cn", "Foodtime2026!");
    var body = new JSONObject(loginBody.get());
    assertFalse(body.has("challengeId"));
    assertFalse(body.has("challengeAnswer"));
  }

  @Test public void malformedChallengePayloadIsRejected() throws Exception {
    captchaJson.set(new JSONObject().put("challengeId", "not-a-uuid")
        .put("imageBase64", "not-base64").put("expiresAt", "bad").toString());
    var failure = assertThrows(RepositoryException.class, () -> repo.captcha());
    assertEquals("INVALID_RESPONSE", failure.code());
  }
}
