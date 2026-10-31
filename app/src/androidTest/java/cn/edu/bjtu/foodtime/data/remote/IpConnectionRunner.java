package cn.edu.bjtu.foodtime.data.remote;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import cn.edu.bjtu.foodtime.BuildConfig;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import org.json.JSONObject;

/** Opt-in online smoke check: GET only, isolated anonymous session, no account credentials. */
public final class IpConnectionRunner extends Instrumentation {
  @Override public void onCreate(Bundle arguments) {
    super.onCreate(arguments);
    start();
  }

  @Override public void onStart() {
    Bundle result = new Bundle();
    HttpTransport transport = null;
    try {
      String base = BuildConfig.API_BASE_URL;
      require(base.equals("https://154.9.252.201/api/v1/"), "Unexpected API endpoint");
      require(BuildConfig.APPLICATION_ID.endsWith(".ipcheck"), "Use isolated .ipcheck package");
      var policy = android.security.NetworkSecurityPolicy.getInstance();
      require(!policy.isCleartextTrafficPermitted("154.9.252.201"), "Cloud HTTP must be blocked");
      require(!policy.isCleartextTrafficPermitted("example.com"), "Arbitrary HTTP must be blocked");
      require(policy.isCleartextTrafficPermitted("127.0.0.1"), "Debug loopback integration must remain available");
      var store = new EncryptedCookieStore(getTargetContext(), base, "ip-connection-smoke");
      store.removeAll();
      var cookies = new CookieManager(store, CookiePolicy.ACCEPT_ORIGINAL_SERVER);
      transport = new HttpTransport(base, cookies);
      JSONObject ping = get(transport, "system/ping");
      require(ping.getString("status").equals("UP"), "Backend not UP");
      String token = get(transport, "auth/csrf").getString("token");
      require(!token.isEmpty(), "Missing CSRF token");
      require(store.getCookies().stream().anyMatch(c ->
          c.getName().equals("FOODTIME_SESSION") && c.getSecure() && c.isHttpOnly()),
          "Missing secure session cookie");
      require(cookies.get(URI.create(base + "auth/csrf"), java.util.Map.of()).values()
          .stream().flatMap(java.util.Collection::stream)
          .anyMatch(value -> value.contains("FOODTIME_SESSION=")), "Cookie not sent to IP");
      String session = transport.sessionKey();
      get(transport, "auth/csrf");
      require(session.equals(transport.sessionKey()), "Anonymous session was replaced");
      result.putString("stream", "Cleartext policy, IP TLS, API JSON, encrypted Secure cookie and session reuse passed\n");
      finish(Activity.RESULT_OK, result);
    } catch (Exception | AssertionError failure) {
      result.putString("stream", "IP connection failed: " + failure + "\n");
      finish(Activity.RESULT_CANCELED, result);
    } finally {
      if (transport != null) transport.clearSession();
    }
  }

  private static JSONObject get(HttpTransport transport, String path) throws Exception {
    var response = transport.request("GET", path, null, null);
    require(response.status() == 200, path + " returned HTTP " + response.status());
    JSONObject envelope = new JSONObject(response.body());
    require(envelope.getString("code").equals("OK"), "API rejected " + path);
    return envelope.getJSONObject("data");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
