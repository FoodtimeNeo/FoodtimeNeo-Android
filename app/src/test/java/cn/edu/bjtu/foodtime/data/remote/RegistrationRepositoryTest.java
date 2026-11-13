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

/** Registration policy parsing and invitation-code transport against the real transport. */
public class RegistrationRepositoryTest {
  private ServerSocket server;
  private ExecutorService workers;
  private RemoteRepository repo;
  private final List<String> calls = new CopyOnWriteArrayList<>();
  private final AtomicReference<String> registerBody = new AtomicReference<>();
  private volatile String mode = "invitation";
  private volatile boolean canSend = true;
  private volatile boolean required = true;
  private volatile String revision = "invitation:email";

  @Before public void start() throws Exception {
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
      calls.add(first.split(" ")[0] + " " + path);
      JSONObject data = new JSONObject();
      if (path.endsWith("/auth/csrf")) {
        data.put("token", "local-token");
      } else if (path.endsWith("/auth/registration-policy")) {
        data.put("mode", mode).put("canSendEmailCode", canSend)
            .put("invitationRequired", required).put("revision", revision);
      } else if (path.endsWith("/auth/register")) {
        registerBody.set(body.toString());
      } else {
        send(socket, 404, new JSONObject());
        return;
      }
      send(socket, 200, data);
    } catch (Exception canceled) {
      // A canceled request may close the socket before a response is written.
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

  @Test public void parsesThePublicPolicy() {
    var policy = repo.registrationPolicy();
    assertEquals("invitation", policy.mode());
    assertTrue(policy.canSendEmailCode());
    assertTrue(policy.invitationRequired());
    assertEquals("invitation:email", policy.revision());
    assertTrue(calls.contains("GET /api/v1/auth/registration-policy"));
  }

  @Test public void invalidPolicyResponseFailsClosed() {
    mode = "surprise";
    var failure = assertThrows(RepositoryException.class, () -> repo.registrationPolicy());
    assertEquals("INVALID_RESPONSE", failure.code());
  }

  @Test public void invitationCodeIsSentTrimmedAndOptional() throws Exception {
    repo.register("20260001@bjtu.edu.cn", "Foodtime2026!", "012345", "  abcd2345efgh  ");
    var body = new JSONObject(registerBody.get());
    assertEquals("abcd2345efgh", body.getString("invitationCode"));
    assertEquals("20260001@bjtu.edu.cn", body.getString("email"));

    repo.register("20260002@bjtu.edu.cn", "Foodtime2026!", "012345");
    var legacy = new JSONObject(registerBody.get());
    assertFalse(legacy.has("invitationCode"));
  }
}
