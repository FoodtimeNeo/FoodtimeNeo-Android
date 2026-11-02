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
  /** Streams one image file as multipart without buffering the whole payload in memory. */
  public Response sendFile(String path, java.io.File file, String csrf) throws IOException {
    String boundary="foodtime"+java.util.UUID.randomUUID();
    byte[] head=("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"photo.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n").getBytes(StandardCharsets.UTF_8);
    byte[] tail=("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8);
    long total=head.length+file.length()+tail.length;
    Exchange exchange=open("POST",path,"multipart/form-data; boundary="+boundary,csrf,Map.of());
    try {
      var connection=exchange.connection();
      connection.setDoOutput(true);
      connection.setConnectTimeout(15000);
      connection.setReadTimeout(180000);
      connection.setFixedLengthStreamingMode(total);
      try(var out=connection.getOutputStream();var in=new java.io.FileInputStream(file)) {
        out.write(head);
        byte[] buffer=new byte[65536];int count;
        while((count=in.read(buffer))!=-1)out.write(buffer,0,count);
        out.write(tail);
      }
      BinaryResponse raw=read(exchange);
      return new Response(raw.status(),new String(raw.body(),StandardCharsets.UTF_8),raw.headers());
    } finally { exchange.cancellation().cancel(false);exchange.connection().disconnect(); }
  }
  public BinaryResponse bytes(String path) throws IOException {
    return bytes(path,Map.of());
  }
  public BinaryResponse bytes(String path,Map<String,String> headers) throws IOException {
    return exchange("GET",path,null,null,null,headers);
  }
  private record Exchange(HttpURLConnection connection, URI target, Thread caller, long ticket,
                          java.util.concurrent.ScheduledFuture<?> cancellation) { }
  private Exchange open(String method,String path,String contentType,String csrf,Map<String,String> extra) throws IOException {
    URI target = base.resolve(path.startsWith("/") ? path.substring(1) : path);
    if (!base.getScheme().equals(target.getScheme()) || !base.getAuthority().equals(target.getAuthority())) {
      throw new IllegalArgumentException("Requests must remain on the configured API origin");
    }
    HttpURLConnection connection = (HttpURLConnection) target.toURL().openConnection();
    Thread caller=Thread.currentThread();
    var cancellation=CANCELLATIONS.scheduleWithFixedDelay(()->{if(caller.isInterrupted())connection.disconnect();},50,50,java.util.concurrent.TimeUnit.MILLISECONDS);
    connection.setInstanceFollowRedirects(false);
    connection.setConnectTimeout(5000);
    connection.setReadTimeout(10000);
    connection.setRequestMethod(method);
    connection.setRequestProperty("Accept", "application/json");
    // 后端按注册路径识别官方客户端；未知客户端在 enforce 模式下会被拒绝。
    connection.setRequestProperty("X-Foodtime-Client", "android");
    long ticket;
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
    if (contentType != null) connection.setRequestProperty("Content-Type", contentType);
    return new Exchange(connection,target,caller,ticket,cancellation);
  }
  private BinaryResponse read(Exchange exchange) throws IOException {
    HttpURLConnection connection=exchange.connection();URI target=exchange.target();
    Thread caller=exchange.caller();long ticket=exchange.ticket();
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
  }
  private BinaryResponse exchange(String method,String path,byte[] payload,String contentType,String csrf,Map<String,String> extra) throws IOException {
    Exchange exchange=open(method,path,contentType,csrf,extra);
    try {
      if (payload != null) {
        exchange.connection().setDoOutput(true);
        exchange.connection().setFixedLengthStreamingMode(payload.length);
        try (var out = exchange.connection().getOutputStream()) { out.write(payload); }
      }
      return read(exchange);
    } finally { exchange.cancellation().cancel(false);exchange.connection().disconnect(); }
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
