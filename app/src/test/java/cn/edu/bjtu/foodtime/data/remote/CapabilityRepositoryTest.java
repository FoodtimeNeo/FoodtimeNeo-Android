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

/** Capability projection is fetched from the server, scoped to the session and cleared on logout. */
public class CapabilityRepositoryTest {
  private ServerSocket server;
  private ExecutorService workers;
  private RemoteRepository repo;
  private final AtomicReference<String> capabilitiesJson = new AtomicReference<>();
  private final String userId = "00000000-0000-4000-8000-000000000001";

  @Before public void start() throws Exception {
    capabilitiesJson.set(new JSONObject().put("capabilities",
        new JSONArray().put("catalog.edit").put("moderation.review")).put("revision", "default-v1").toString());
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
      for (int i = 0; i < length; i++) reader.read();
      String path = first.split(" ")[1];
      JSONObject data = new JSONObject();
      if (path.endsWith("/users/me/profile")) {
        data.put("id", userId).put("email", "admin@bjtu.edu.cn").put("displayName", "管理员")
            .put("role", "admin").put("status", "active").put("createdAt", "2026-11-12T00:00:00Z");
      } else if (path.endsWith("/users/me/capabilities")) {
        data = new JSONObject(capabilitiesJson.get());
      } else if (path.endsWith("/auth/csrf")) {
        data.put("token", "local-token");
      } else if (path.endsWith("/auth/logout")) {
        data = new JSONObject();
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

  @Test public void restoreFetchesCapabilitiesForTheSession() {
    repo.restoreSession();
    assertEquals("admin", repo.currentUser().role());
    var snapshot = repo.capabilities();
    assertTrue(snapshot.can("catalog.edit"));
    assertTrue(snapshot.can("moderation.review"));
    assertFalse(snapshot.can("report.review"));
    assertEquals("default-v1", snapshot.revision());
  }

  @Test public void explicitRefreshReplacesTheSnapshotAndRevision() throws Exception {
    repo.restoreSession();
    capabilitiesJson.set(new JSONObject().put("capabilities",
        new JSONArray().put("report.reply")).put("revision", "policy-v2").toString());
    repo.loadCapabilities();
    var snapshot = repo.capabilities();
    assertTrue(snapshot.can("report.reply"));
    assertFalse(snapshot.can("catalog.edit"));
    assertEquals("policy-v2", snapshot.revision());
  }

  @Test public void malformedCapabilityPayloadIsRejected() throws Exception {
    repo.restoreSession();
    capabilitiesJson.set(new JSONObject().put("capabilities", "not-an-array")
        .put("revision", "bad").toString());
    var failure = assertThrows(RepositoryException.class, () -> repo.loadCapabilities());
    assertEquals("INVALID_RESPONSE", failure.code());
  }

  @Test public void logoutClearsCapabilities() {
    repo.restoreSession();
    assertTrue(repo.capabilities().can("catalog.edit"));
    repo.logout();
    assertTrue(repo.capabilities().capabilities().isEmpty());
    assertNull(repo.currentUser());
  }
}
