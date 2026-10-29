package cn.edu.bjtu.foodtime.data.remote;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** HTTP protocol only. Callers execute requests off the UI thread and interpret API JSON. */
public final class HttpTransport {
  private static final java.util.concurrent.ScheduledExecutorService CANCELLATIONS=java.util.concurrent.Executors.newSingleThreadScheduledExecutor(run->{Thread thread=new Thread(run,"image-request-cancellation");thread.setDaemon(true);return thread;});
  public record Response(int status, String body, Map<String, List<String>> headers) {
    public String header(String name) {
      List<String> values = headers.get(name);
      return values == null || values.isEmpty() ? "" : values.get(0);
    }
  }
  public record BinaryResponse(int status,byte[] body,Map<String,List<String>> headers) {}

  private final URI base;
  private final CookieManager cookies;
  private long sessionGeneration;

  public HttpTransport(String baseUrl) {
    this(baseUrl, new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER));
  }

  public HttpTransport(String baseUrl, CookieManager cookies) {
    base = URI.create(baseUrl.endsWith("/") ? baseUrl : baseUrl + "/");
    if (base.getHost() == null || !(base.getScheme().equals("http") || base.getScheme().equals("https"))) {
      throw new IllegalArgumentException("Invalid API base URL");
    }
    this.cookies = cookies;
  }

  public Response request(String method, String path, String json, String csrf)
      throws IOException {
    return send(method, path, json == null ? null : json.getBytes(StandardCharsets.UTF_8),
        "application/json; charset=utf-8", csrf);
  }

  public Response send(String method, String path, byte[] payload, String contentType,
      String csrf) throws IOException {
    BinaryResponse raw=exchange(method,path,payload,contentType,csrf,Map.of());
    return new Response(raw.status(),new String(raw.body(),StandardCharsets.UTF_8),raw.headers());
  }
  public BinaryResponse bytes(String path) throws IOException {
    return bytes(path,Map.of());
  }
  public BinaryResponse bytes(String path,Map<String,String> headers) throws IOException {
    return exchange("GET",path,null,null,null,headers);
  }
  private BinaryResponse exchange(String method,String path,byte[] payload,String contentType,String csrf,Map<String,String> extra) throws IOException {
    URI target = base.resolve(path.startsWith("/") ? path.substring(1) : path);
    if (!base.getScheme().equals(target.getScheme()) || !base.getAuthority().equals(target.getAuthority())) {
      throw new IllegalArgumentException("Requests must remain on the configured API origin");
    }
    HttpURLConnection connection = (HttpURLConnection) target.toURL().openConnection();
    Thread caller=Thread.currentThread();
    var cancellation=CANCELLATIONS.scheduleWithFixedDelay(()->{if(caller.isInterrupted())connection.disconnect();},50,50,java.util.concurrent.TimeUnit.MILLISECONDS);
    long ticket;
    try {
      connection.setInstanceFollowRedirects(false);
      connection.setConnectTimeout(5000);
      connection.setReadTimeout(10000);
      connection.setRequestMethod(method);
      connection.setRequestProperty("Accept", "application/json");
      synchronized(cookies) {
        ticket=sessionGeneration;
        for (Map.Entry<String, List<String>> entry : cookies.get(target, Map.of()).entrySet()) {
          if (!entry.getValue().isEmpty()) {
            connection.setRequestProperty(entry.getKey(), String.join("; ", entry.getValue()));
          }
        }
      }
      for(var header:extra.entrySet())if(header.getKey().equalsIgnoreCase("If-None-Match"))connection.setRequestProperty(header.getKey(),header.getValue());
      if (csrf != null && !csrf.isEmpty()) connection.setRequestProperty("X-CSRF-TOKEN", csrf);
      if (payload != null) {
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", contentType);
        connection.setFixedLengthStreamingMode(payload.length);
        try (var out = connection.getOutputStream()) { out.write(payload); }
      }
      int status = connection.getResponseCode();
      synchronized(cookies) {if(ticket==sessionGeneration&&!caller.isInterrupted())cookies.put(target, connection.getHeaderFields());}
      Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
      connection.getHeaderFields().forEach((name, values) -> {
        if (name != null && values != null) headers.put(name, List.copyOf(values));
      });
      InputStream stream = status == 304 ? null : status >= 400 ? connection.getErrorStream() : connection.getInputStream();
      byte[] body = new byte[0];
      if (stream != null) {
        try (stream; var out = new ByteArrayOutputStream()) {
          byte[] buffer = new byte[8192];
          int count;
          while ((count = stream.read(buffer)) != -1) {
            if (out.size() + count > 20 * 1024 * 1024) throw new IOException("Response exceeds limit");
            out.write(buffer, 0, count);
          }
          body = out.toByteArray();
        }
      }
      synchronized(cookies) {if(ticket!=sessionGeneration)throw new IOException("Session changed during request");}
      if(Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException("Request cancelled");
      return new BinaryResponse(status, body, Collections.unmodifiableMap(headers));
    } finally { cancellation.cancel(false);connection.disconnect(); }
  }

  public void clearSession() { synchronized(cookies){sessionGeneration++;cookies.getCookieStore().removeAll();} }
  public String sessionKey() {
    synchronized(cookies) {
      StringBuilder value=new StringBuilder();
      cookies.getCookieStore().getCookies().stream().filter(c->!c.hasExpired()).sorted(java.util.Comparator.comparing(java.net.HttpCookie::getName)).forEach(c->value.append(c.getName()).append('=').append(c.getValue()).append(';'));
      return ImageDiskCache.hash(value.toString());
    }
  }
  public CookieManager cookieManager() { return cookies; }
}
