package cn.edu.bjtu.foodtime.data.remote;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.*;
import java.net.*;
import java.security.KeyStore;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** Persist opaque cookies with an Android Keystore key. No passwords, roles or user profiles. */
@android.annotation.SuppressLint("ApplySharedPref") // Worker requests check durable writes before reporting login/logout success.
public final class EncryptedCookieStore implements CookieStore {
  private final CookieStore memory=new CookieManager().getCookieStore();
  private final SharedPreferences preferences;
  private final URI origin;
  private final String keyAlias;
  private final Map<String,Long> expires=new HashMap<>();
  public EncryptedCookieStore(Context context,String baseUrl) {
    this(context,baseUrl,"foodtime-session");
  }
  public EncryptedCookieStore(Context context,String baseUrl,String namespace) {
    URI base=URI.create(baseUrl);origin=URI.create(base.getScheme()+"://"+base.getAuthority()+"/");
    keyAlias=namespace+"-key";
    preferences=context.getSharedPreferences(namespace,Context.MODE_PRIVATE);
    restore();
  }
  private SecretKey key() throws Exception {
    KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
    if(store.containsAlias(keyAlias)) return (SecretKey)store.getKey(keyAlias,null);
    KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
    generator.init(new KeyGenParameterSpec.Builder(keyAlias,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
    return generator.generateKey();
  }
  private void restore() {
    String value=preferences.getString("cookies",null);if(value==null) return;
    try {
      JSONObject sealed=new JSONObject(value);
      Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(sealed.getString("iv"),Base64.NO_WRAP)));
      JSONArray cookies=new JSONArray(new String(cipher.doFinal(Base64.decode(sealed.getString("data"),Base64.NO_WRAP)),java.nio.charset.StandardCharsets.UTF_8));
      long now=System.currentTimeMillis();
      for(int i=0;i<cookies.length();i++) {
        JSONObject j=cookies.getJSONObject(i);long deadline=j.getLong("expires");if(deadline<=now) continue;
        HttpCookie cookie=new HttpCookie(j.getString("name"),j.getString("value"));
        cookie.setDomain(j.optString("domain",null));cookie.setPath(j.optString("path","/"));
        cookie.setSecure(j.optBoolean("secure"));cookie.setHttpOnly(j.optBoolean("httpOnly"));cookie.setVersion(j.optInt("version"));
        cookie.setMaxAge(Math.max(1,(deadline-now)/1000));memory.add(origin,cookie);expires.put(identity(cookie),deadline);
      }
    } catch(Exception invalid) {
      memory.removeAll();expires.clear();preferences.edit().remove("cookies").commit();
    }
  }
  private void persist() {
    try {
      JSONArray list=new JSONArray();
      for(HttpCookie cookie:memory.getCookies()) {
        long deadline=expires.getOrDefault(identity(cookie),0L);if(cookie.hasExpired()||deadline<=System.currentTimeMillis()) continue;
        JSONObject j=new JSONObject();j.put("name",cookie.getName());j.put("value",cookie.getValue());j.put("domain",cookie.getDomain());
        j.put("path",cookie.getPath());j.put("secure",cookie.getSecure());j.put("httpOnly",cookie.isHttpOnly());j.put("version",cookie.getVersion());j.put("expires",deadline);list.put(j);
      }
      if(list.length()==0) { if(!preferences.edit().remove("cookies").commit()) throw new IllegalStateException();return; }
      Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());
      byte[] sealed=cipher.doFinal(list.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
      JSONObject envelope=new JSONObject();envelope.put("iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP));envelope.put("data",Base64.encodeToString(sealed,Base64.NO_WRAP));
      if(!preferences.edit().putString("cookies",envelope.toString()).commit()) throw new IllegalStateException();
    } catch(Exception failure) { throw new IllegalStateException("登录信息无法安全保存，请重试",failure); }
  }
  private static String identity(HttpCookie c) { return c.getName()+"|"+c.getDomain()+"|"+c.getPath(); }
  @Override public synchronized void add(URI uri,HttpCookie cookie) {
    memory.add(uri,cookie);
    long seconds=cookie.getMaxAge()<0?7*24*60*60:Math.min(cookie.getMaxAge(),7*24*60*60);
    expires.put(identity(cookie),System.currentTimeMillis()+seconds*1000);persist();
  }
  @Override public synchronized List<HttpCookie> get(URI uri) { return memory.get(uri); }
  @Override public synchronized List<HttpCookie> getCookies() { return memory.getCookies(); }
  @Override public synchronized List<URI> getURIs() { return memory.getURIs(); }
  @Override public synchronized boolean remove(URI uri,HttpCookie cookie) { boolean removed=memory.remove(uri,cookie);expires.remove(identity(cookie));persist();return removed; }
  @Override public synchronized boolean removeAll() { boolean changed=memory.removeAll();expires.clear();persist();return changed; }
}
