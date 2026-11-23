package cn.edu.bjtu.foodtime.data.remote;

import java.io.*;
import java.util.*;
import org.json.*;

/** Access-driven revalidation only: no startup work, prefetch, private data or offline stale fallback. */
final class PublicCatalogCache {
  private static final String ID="[0-9a-fA-F-]{36}";
  private final String namespace;
  private final CatalogDiskCache store;
  private final Map<String,Boolean> media=Collections.synchronizedMap(new LinkedHashMap<>(16,.75f,true){
    @Override protected boolean removeEldestEntry(Map.Entry<String,Boolean> entry){return size()>4096;}
  });
  PublicCatalogCache(String namespace,File directory){this.namespace=namespace;store=new CatalogDiskCache(directory,32L*1024*1024);}
  static boolean eligible(String method,String path){
    if(!"GET".equals(method)||path.contains("#")||path.contains("\\"))return false;
    String resource=path.split("\\?",2)[0];
    return resource.matches("dining-halls|dining-halls/"+ID+"/stalls|stalls/"+ID+"(?:/dishes)?|dishes/search|dishes/"+ID+"(?:/images)?");
  }
  HttpTransport.Response request(HttpTransport transport,String method,String path,String body,String csrf)throws IOException{
    if(!eligible(method,path))return transport.request(method,path,body,csrf);
    String key=namespace+":"+path;
    var cached=store.get(key);
    var response=transport.request(method,path,body,csrf,cached==null?Map.of():Map.of("If-None-Match",cached.etag()));
    if(response.status()==304){
      if(cached!=null&&marked(response)&&cached.etag().equals(response.header("ETag"))){
        try{
          Object data=new JSONTokener(cached.data()).nextValue();
          registerMedia(data);
          return new HttpTransport.Response(200,new JSONObject().put("code","OK").put("data",data).toString(),response.headers());
        }catch(JSONException failure){store.remove(key);}
      }
      // A missing/invalid replica never turns into an empty success or an unbounded retry.
      response=transport.request(method,path,body,csrf);
    }
    if(response.status()==200&&marked(response)){
      try{
        var envelope=new JSONObject(response.body());
        if("OK".equals(envelope.optString("code"))&&envelope.has("data")){
          Object data=envelope.get("data");registerMedia(data);
          store.put(key,new CatalogDiskCache.Entry(data.toString(),response.header("ETag")));
          return response;
        }
      }catch(JSONException ignored){/* Existing protocol handling reports malformed responses. */}
    }
    store.remove(key);
    return response;
  }
  private static boolean marked(HttpTransport.Response response){
    String etag=response.header("ETag");
    return "1".equals(response.header("X-Foodtime-Public-Catalog"))&&etag.matches("\"catalog-v1-[0-9a-f]{64}\"")
        &&!response.header("Cache-Control").toLowerCase(Locale.ROOT).contains("no-store");
  }
  private void registerMedia(Object data){
    if(data instanceof JSONObject object){var keys=object.keys();while(keys.hasNext())registerMedia(object.opt(keys.next()));}
    else if(data instanceof JSONArray array){for(int i=0;i<array.length();i++)registerMedia(array.opt(i));}
    else if(data instanceof String value&&value.matches("/api/v1/media/"+ID+"(?:\\?size=(?:thumb|full))?"))media.put(value.split("\\?")[0],true);
  }
  boolean isCatalogMedia(String path){return media.containsKey(path.split("\\?",2)[0]);}
}
