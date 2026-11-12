package cn.edu.bjtu.foodtime.data.remote;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class HttpTransportTest {
  @org.junit.Test public void logoutPreservesOnlyDeviceQuotaIdentityAndDeviceChangesDoNotChangeTheSessionKey()throws Exception {
    String base="http://127.0.0.1:18086/api/v1/";
    var manager=new java.net.CookieManager(null,java.net.CookiePolicy.ACCEPT_ALL);
    var client=new HttpTransport(base,manager);var uri=java.net.URI.create(base);
    var session=new java.net.HttpCookie("FOODTIME_SESSION","first-session");session.setPath("/");manager.getCookieStore().add(uri,session);
    String key=client.sessionKey();
    var device=new java.net.HttpCookie("foodtime_device","signed-device");device.setPath("/");device.setMaxAge(86400);manager.getCookieStore().add(uri,device);
    assertEquals(key,client.sessionKey());client.clearSession();
    assertNotEquals(key,client.sessionKey());assertEquals(1,manager.getCookieStore().getCookies().size());
    assertEquals("foodtime_device",manager.getCookieStore().getCookies().get(0).getName());
    assertEquals("signed-device",manager.getCookieStore().getCookies().get(0).getValue());
  }
  private ServerSocket server;
  private ExecutorService worker;
  private HttpTransport client;

  @Before public void setup() throws Exception {
    server = new ServerSocket();
    server.bind(new InetSocketAddress("127.0.0.1", 0));
    worker = Executors.newSingleThreadExecutor();
    worker.submit(() -> {
      while (!server.isClosed()) {
        try (Socket socket = server.accept()) {
          var in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
          String[] request = in.readLine().split(" ");
          var headers = new HashMap<String, String>();
          String line;
          while (!(line = in.readLine()).isEmpty()) {
            int colon = line.indexOf(':');
            headers.put(line.substring(0, colon).toLowerCase(Locale.ROOT), line.substring(colon + 1).trim());
          }
          int length = Integer.parseInt(headers.getOrDefault("content-length", "0"));
          for (int i = 0; i < length; i++) in.read();
          boolean limited = request[1].endsWith("/limited");
          boolean csrf = request[1].endsWith("/csrf");
          String body = limited ? "rate limited" : csrf ? "token" : request[0] + ":" + headers.get("cookie") + ":" + headers.get("x-csrf-token");
          byte[] response = body.getBytes(StandardCharsets.UTF_8);
          String extra = limited ? "Retry-After: 60\r\n" : csrf ? "Set-Cookie: FOODTIME_SESSION=opaque; Path=/; HttpOnly\r\n" : "";
          var out = socket.getOutputStream();
          out.write(((limited ? "HTTP/1.1 429 Too Many Requests\r\n" : "HTTP/1.1 200 OK\r\n")
              + extra + "Content-Length: " + response.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
          out.write(response); out.flush();
        } catch (Exception failure) {
          if (!server.isClosed()) throw new IllegalStateException(failure);
        }
      }
    });
    client = new HttpTransport("http://127.0.0.1:" + server.getLocalPort() + "/api/v1");
  }

  @After public void cleanup() throws Exception { server.close(); worker.shutdownNow(); }

  @Test public void cookiesAndCsrfAreSentToTheSameLoopbackOrigin() throws Exception {
    assertEquals("token", client.request("GET", "/csrf", null, null).body());
    String response = client.request("POST", "/echo", "{}", "csrf-token").body();
    assertTrue(response.contains("POST:FOODTIME_SESSION=opaque:csrf-token"));
    client.clearSession();
    assertTrue(client.request("GET", "/echo", null, null).body().contains("GET:null:null"));
  }

  @Test public void errorResponsesPreserveStatusAndRetryAfterWithoutRetryingWrites() throws Exception {
    var response = client.request("POST", "/limited", "{}", null);
    assertEquals(429, response.status());
    assertEquals("60", response.header("retry-after"));
    assertEquals("rate limited", response.body());
  }

  @Test(expected = IllegalArgumentException.class)
  public void absoluteUrlsCannotSendSessionCookiesToAnotherOrigin() throws Exception {
    client.request("GET", "http://example.test/steal", null, null);
  }

  /** release 默认 fail-closed：公网 http 必须拒绝，回环联调地址仍可用。 */
  @Test public void cleartextPublicIpIsRejectedByDefault() {
    assertThrows(IllegalArgumentException.class,
        () -> new HttpTransport("http://154.9.252.201/api/v1/"));
  }

  @Test public void loopbackCleartextIsAllowedForLocalDev() {
    new HttpTransport("http://127.0.0.1:8080/api/v1/");
    new HttpTransport("http://localhost:8080/api/v1/");
    new HttpTransport("http://10.0.2.2:8080/api/v1/");
  }

  @Test public void debugFlagExplicitlyAllowsLanCleartext() {
    new HttpTransport("http://154.9.252.201/api/v1/",
        new java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ORIGINAL_SERVER), true);
  }

  @Test public void nonHttpSchemeIsRejected() {
    assertThrows(IllegalArgumentException.class,
        () -> new HttpTransport("ftp://154.9.252.201/api/v1/"));
  }

  @Test public void loopbackDetection() {
    assertTrue(HttpTransport.isLoopback("localhost"));
    assertTrue(HttpTransport.isLoopback("127.0.0.1"));
    assertTrue(HttpTransport.isLoopback("10.0.2.2"));
  }
}
