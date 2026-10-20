package cn.edu.bjtu.foodtime.data.remote;

import cn.edu.bjtu.foodtime.domain.model.Models.*;
import cn.edu.bjtu.foodtime.domain.repository.*;
import cn.edu.bjtu.foodtime.domain.validation.Validation;
import org.json.*;
import java.io.*;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** Network writes/refreshes run on a worker; screen-facing reads never initiate requests. */
public final class RemoteRepository implements FoodtimeRepository {
  private final HttpTransport http;
  private final URI origin;
  private volatile String csrf;
  private volatile User user;
  private volatile List<Hall> hallList=List.of();
  private final Map<String,Hall> halls=new ConcurrentHashMap<>();
  private final Map<String,Stall> stalls=new ConcurrentHashMap<>();
  private final Map<String,Dish> dishes=new ConcurrentHashMap<>();
  private final Map<String,List<Stall>> stallLists=new ConcurrentHashMap<>();
  private final Map<String,List<Dish>> dishLists=new ConcurrentHashMap<>();
  private final Map<String,List<Review>> reviews=new ConcurrentHashMap<>();
  private final Map<String,List<Photo>> photos=new ConcurrentHashMap<>();
  private final Map<String,RatingSummary> ratings=new ConcurrentHashMap<>();
  private final Map<String,String> contexts=new ConcurrentHashMap<>();
  private final Map<String,List<AdminEntity>> catalog=new ConcurrentHashMap<>();
  private final Map<String,AdminEntity> entities=new ConcurrentHashMap<>();
  private final Map<String,String> parents=new ConcurrentHashMap<>();
  private final Map<String,ReplyPage> replyPages=new ConcurrentHashMap<>();
  private volatile ReplyPage ownReplies=ReplyPage.empty();
  private volatile List<Review> ownReviews=List.of();
  private volatile List<Submission> ownSubmissions=List.of(),queue=List.of();
  private volatile List<Audit> auditList=List.of();
  private volatile List<User> userList=List.of();

  public RemoteRepository(String baseUrl,HttpTransport http) {
    this.http=http;
    URI base=URI.create(baseUrl); origin=URI.create(base.getScheme()+"://"+base.getAuthority()+"/");
  }
  @Override public User currentUser() { return user; }
  @Override public void restoreSession() {
    try { profile(); }
    catch(RepositoryException e) { if(e.status()!=401) throw e; }
  }
  private void profile() { user=person(call("GET","users/me/profile",null)); }
  @Override public User login(String account,String password) {
    Validation.email(account); Validation.password(password,false);
    csrf=null; token();
    call("POST","auth/login",json("account",account.strip(),"password",password));
    csrf=null; token(); profile(); return user;
  }
  @Override public String sendVerificationCode(String email) {
    Validation.email(email);
    call("POST","auth/register/email-code",json("email",email.strip()));
    return ""; // Verification codes only arrive by email; never return or invent a client code.
  }
  @Override public void register(String email,String password,String code) {
    Validation.email(email); Validation.password(password,true);
    call("POST","auth/register",json("email",email.strip(),"password",password,"verificationCode",code));
  }
  @Override public void logout() {
    try { call("POST","auth/logout",null); }
    finally { clear(); }
  }
  @Override public void changePassword(String oldPassword,String newPassword) {
    Validation.password(newPassword,true);
    call("PUT","users/me/password",json("oldPassword",oldPassword,"newPassword",newPassword)); clear();
  }

  @Override public void refresh(ContentRequest request) {
    profile(); // Restore current server role/status on every screen load.
    switch(request.query()) {
      case HALLS -> fetchHalls();
      case STALLS -> { fetchHalls(); fetchStalls(request.id()); }
      case DISHES -> { ensureStall(request.id()); fetchDishes(request.id()); }
      case DISH_DETAIL -> fetchDetail(request.id());
      case MY_REVIEWS -> ownReviews=reviewPage("users/me/reviews");
      case MY_SUBMISSIONS -> fetchOwnSubmissions();
      case REVIEW_REPLIES -> {
        String root=request.id().split("~",-1)[0]; replyPages.put(root,fetchReplyPage(root,""));
      }
      case MY_REVIEW_REPLIES -> {
        int number=request.id().isBlank()?1:Integer.parseInt(request.id());
        JSONObject data=call("GET","users/me/review-replies?page="+number+"&size=20",null);
        ownReplies=new ReplyPage(replyItems(data),data.optLong("total"),data.optLong("total")>number*20L,"");
      }
      case MODERATION -> {
        fetchAdminContext();
        List<Submission> items=new ArrayList<>();
        for(String status:List.of("pending","approved","rejected"))
          items.addAll(page("admin/moderation?status="+status,j -> submission(j,text(j,"type"),true)));
        items.sort(Comparator.comparingLong(Submission::createdAt).reversed()); queue=List.copyOf(items);
      }
      case CATALOG -> {
        String[] parts=request.id().split("~",-1); fetchCatalog(parts[0],parts.length>1?parts[1]:"");
      }
      case CATALOG_ITEM -> {
        String[] parts=request.id().split("~",-1);
        JSONObject item=call("GET","admin/catalog/"+table(parts[0])+"/"+parts[1],null);
        cacheEntity(parts[0],item);
      }
      case ADMIN_REVIEWS -> reviews.put("admin",reviewPage("admin/reviews"));
      case ADMIN_USERS -> userList=page("admin/users",this::person);
      case AUDITS -> auditList=page("admin/moderation/logs",j -> new Audit(text(j,"id"),text(j,"submissionId"),text(j,"moderatorId"),text(j,"action"),text(j,"reason"),time(j,"createdAt")));
      case PROFILE -> { }
    }
  }
  private void fetchHalls() {
    var items=list("dining-halls",j -> new Hall(text(j,"id"),text(j,"name"),text(j,"coverImageUrl"),true));
    halls.clear(); for(Hall h:items) halls.put(h.id(),h); hallList=items;
  }
  private void fetchStalls(String id) {
    var items=list("dining-halls/"+id+"/stalls",j -> new Stall(text(j,"id"),id,text(j,"name"),text(j,"coverImageUrl"),true));
    stalls.entrySet().removeIf(e -> e.getValue().diningHallId().equals(id));
    for(Stall s:items) stalls.put(s.id(),s); stallLists.put(id,items);
  }
  private void ensureStall(String id) {
    fetchHalls();
    Stall cached=stalls.get(id);
    if(cached!=null && halls.containsKey(cached.diningHallId())) fetchStalls(cached.diningHallId());
    else for(Hall h:hallList) fetchStalls(h.id());
    if(!stalls.containsKey(id)) throw missing("档口不存在或已停用");
  }
  private void fetchDishes(String id) {
    var items=list("stalls/"+id+"/dishes",j -> new Dish(text(j,"id"),id,text(j,"name"),decimal(j,"price"),"",true));
    dishes.entrySet().removeIf(e -> e.getValue().stallId().equals(id));
    for(Dish d:items) dishes.put(d.id(),d); dishLists.put(id,items);
  }
  private void fetchDetail(String id) {
    JSONObject j=call("GET","dishes/"+id,null);
    String stall=text(j,"stallId");
    fetchHalls(); fetchStalls(text(j,"diningHallId"));
    dishes.put(id,new Dish(id,stall,text(j,"name"),decimal(j,"price"),text(j,"description"),true));
    contexts.put(id,joined(j,true));
    reviews.put(id,reviewPage("dishes/"+id+"/reviews"));
    photos.put(id,page("dishes/"+id+"/images",p -> new Photo(text(p,"id"),id,text(p,"imageUrl"),"")));
    JSONObject rating=call("GET","dishes/"+id+"/rating",null);
    ratings.put(id,new RatingSummary(decimal(rating,"averageScore"),rating.optLong("reviewCount")));
  }
  private List<Review> reviewPage(String path) {
    return page(path,j -> {
      contexts.put(text(j,"dishId"),joined(j,true));
      return new Review(text(j,"id"),text(j,"dishId"),text(j,"userId"),text(j,"userDisplayName"),j.optInt("rating"),text(j,"content"),time(j,"createdAt"),j.optLong("likeCount"),j.optLong("replyCount"),j.optBoolean("likedByMe"),j.optLong("counterVersion"));
    });
  }
  private void fetchOwnSubmissions() {
    List<Submission> all=new ArrayList<>();
    for(String type:List.of("review","dish","stall","photo")) {
      String path="users/me/"+(type.equals("photo")?"image":type)+"-submissions";
      all.addAll(page(path,j -> submission(j,type,false)));
    }
    all.sort(Comparator.comparingLong(Submission::createdAt).reversed()); ownSubmissions=List.copyOf(all);
  }

  @Override public LikeState setReviewLike(String reviewId, boolean liked) {
    JSONObject result=call(liked?"PUT":"DELETE","reviews/"+reviewId+"/like",null);
    return new LikeState(result.optBoolean("likedByMe"),result.optLong("likeCount"),result.optLong("counterVersion"));
  }
  @Override public ReplyPage reviewReplies(String reviewId) { return replyPages.getOrDefault(reviewId,ReplyPage.empty()); }
  @Override public ReplyPage myReviewReplies() { return ownReplies; }
  private ReplyPage fetchReplyPage(String root,String cursor) {
    if(!cursor.matches("[A-Za-z0-9_-]*")) throw new RepositoryException(502,"INVALID_RESPONSE","回复游标响应异常",0);
    String suffix=cursor.isBlank()?"":"&cursor="+cursor;
    JSONObject data=call("GET","reviews/"+root+"/replies?size=20"+suffix,null);
    return new ReplyPage(replyItems(data),data.optLong("total"),data.optBoolean("hasMore"),text(data,"nextCursor"));
  }
  private List<ReviewReply> replyItems(JSONObject data) {
    JSONArray rows=data.optJSONArray("items");
    if(rows==null) throw new RepositoryException(502,"INVALID_RESPONSE","回复列表响应异常",0);
    List<ReviewReply> result=new ArrayList<>();
    for(int i=0;i<rows.length();i++) {
      JSONObject j=rows.optJSONObject(i);
      result.add(new ReviewReply(text(j,"id"),text(j,"reviewId"),text(j,"replyToId"),text(j,"userDisplayName"),
          text(j,"content"),text(j,"status"),text(j,"reviewReason"),time(j,"createdAt"),j.optBoolean("replyToDeleted"),
          text(j,"replyToUserDisplayName"),j.optBoolean("ownedByMe"),text(j,"dishId"),text(j,"dishName"),j.optBoolean("reviewDeleted"),
          new LikeState(j.optBoolean("likedByMe"),j.optLong("likeCount"),j.optLong("counterVersion"))));
    }
    return List.copyOf(result);
  }
  @Override public void loadMoreReplies(String root) {
    ReplyPage old=reviewReplies(root);if(!old.hasMore()) return;
    ReplyPage next=fetchReplyPage(root,old.nextCursor());
    Map<String,ReviewReply> merged=new LinkedHashMap<>();
    for(ReviewReply row:old.items()) merged.put(row.id(),row);
    for(ReviewReply row:next.items()) merged.put(row.id(),row);
    replyPages.put(root,new ReplyPage(List.copyOf(merged.values()),next.total(),next.hasMore(),next.nextCursor()));
  }
  @Override public void submitReply(String root,String target,String content,String requestId) {
    call("POST","reviews/"+root+"/replies",json("content",content,"replyToId",target.isBlank()?null:target,"clientRequestId",requestId));
  }
  @Override public void deleteReply(String id) { call("DELETE","review-replies/"+id,null); }
  @Override public LikeState setReplyLike(String id,boolean liked) {
    JSONObject result=call(liked?"PUT":"DELETE","review-replies/"+id+"/like",null);
    LikeState state=new LikeState(result.optBoolean("likedByMe"),result.optLong("likeCount"),result.optLong("counterVersion"));
    replyPages.replaceAll((root,page) -> new ReplyPage(List.copyOf(page.items().stream().map(row -> {
      if(!row.id().equals(id)||row.likes().counterVersion()>state.counterVersion()) return row;
      return new ReviewReply(row.id(),row.reviewId(),row.replyToId(),row.userDisplayName(),row.content(),row.status(),row.reviewReason(),row.createdAt(),row.replyToDeleted(),row.replyToUserDisplayName(),row.ownedByMe(),row.dishId(),row.dishName(),row.reviewDeleted(),state);
    }).collect(java.util.stream.Collectors.toList())),page.total(),page.hasMore(),page.nextCursor()));
    return state;
  }
  @Override public void removeReplyAsAdmin(String id,String reason) { call("DELETE","admin/review-replies/"+id,json("reason",reason)); }

  private Submission submission(JSONObject j,String type,boolean admin) {
    String target=admin?text(j,"targetId"):text(j,type.equals("dish")?"stallId":type.equals("stall")?"diningHallId":"dishId");
    String context=admin?adminContext(type,target):joined(j,type.equals("review")||type.equals("photo"));
    String name=text(j,admin||type.equals("dish")||type.equals("stall")?"name":"dishName");
    return new Submission(text(j,"id"),type,target,admin?text(j,"userId"):user.id(),admin?text(j,"userDisplayName"):"",
        name,text(j,admin||type.equals("review")?"content":"description"),decimal(j,"price"),
        text(j,type.equals("stall")&&!admin?"coverImageUrl":"imageUrl"),j.optInt("rating"),text(j,"status"),
        text(j,admin?"reason":"reviewReason"),time(j,type.equals("review")&&!admin?"submittedAt":"createdAt"),context);
  }
  private void fetchAdminContext() {
    fetchCatalog("halls",""); fetchCatalog("stalls",""); fetchCatalog("dishes","");
  }
  private String adminContext(String type,String id) {
    String table=type.equals("stall")?"halls":type.equals("dish")?"stalls":"dishes";
    String result="";
    for(int i=0;i<3;i++) {
      String key=table+":"+id; AdminEntity e=entities.get(key);
      if(e==null) break;
      result=e.name()+(result.isEmpty()?"":" · "+result);
      id=parents.getOrDefault(key,""); if(table.equals("halls")) break;
      table=table.equals("dishes")?"stalls":"halls";
    }
    return result;
  }
  private void fetchCatalog(String type,String parent) {
    String suffix=parent.isEmpty()?"":"?parentId="+parent;
    catalog.put(type+":"+parent,page("admin/catalog/"+table(type)+suffix,j -> cacheEntity(type,j)));
  }
  private AdminEntity cacheEntity(String type,JSONObject j) {
    BigDecimal amount=decimal(j,"price");String price=amount==null?"":amount.setScale(2).toPlainString();
    AdminEntity e=new AdminEntity(text(j,"id"),text(j,"name"),text(j,"description"),price,text(j,"coverImageUrl"),text(j,"status").equals("active"));
    String key=type+":"+e.id(); entities.put(key,e); parents.put(key,text(j,"parentId")); return e;
  }

  @Override public List<Hall> halls() { return hallList; }
  @Override public Hall hall(String id) { return halls.get(id); }
  @Override public Stall stall(String id) { return stalls.get(id); }
  @Override public Dish dish(String id) { return dishes.get(id); }
  @Override public List<Stall> stalls(String id) { return stallLists.getOrDefault(id,List.of()); }
  @Override public List<Dish> dishes(String id) { return dishLists.getOrDefault(id,List.of()); }
  @Override public List<Review> reviews(String id) { return reviews.getOrDefault(id==null?"admin":id,List.of()); }
  @Override public List<Photo> photos(String id) { return photos.getOrDefault(id,List.of()); }
  @Override public RatingSummary rating(String id) { return ratings.getOrDefault(id,new RatingSummary(null,0)); }
  @Override public List<Review> myReviews() { return ownReviews; }
  @Override public List<Submission> submissions(String userId) { return userId==null?queue:ownSubmissions; }
  @Override public List<Audit> audits() { return auditList; }
  @Override public List<User> users() { return userList; }
  @Override public String context(String id) { return contexts.getOrDefault(id,""); }
  @Override public List<AdminEntity> entities(String type,String parent) { return catalog.getOrDefault(type+":"+parent,List.of()); }
  @Override public AdminEntity entity(String type,String id) { return entities.get(type+":"+id); }

  @Override public void submitReview(String dish,int rating,String content) {
    call("POST","dishes/"+dish+"/reviews",json("rating",rating,"content",content));
  }
  @Override public void submitPhoto(String dish,String image) {
    call("POST","dishes/"+dish+"/image-submissions",json("imageUrl",upload(image)));
  }
  @Override public void submitDish(String stall,String name,String description,BigDecimal price,String image) {
    call("POST","stalls/"+stall+"/submissions",json("name",name,"description",description,"price",price,"imageUrl",upload(image)));
  }
  @Override public void submitStall(String hall,String name,String description,String image) {
    call("POST","dining-halls/"+hall+"/submissions",json("name",name,"description",description,"coverImageUrl",upload(image)));
  }
  @Override public void deleteReview(String id) { call("DELETE","users/me/reviews/"+id,null); }
  @Override public void moderate(String id,String action,String reason) { moderate(id,action,reason,null); }
  @Override public void moderate(String id,String action,String reason,BigDecimal price) {
    Submission target=queue.stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow(() -> missing("投稿不存在，请刷新"));
    call("POST","admin/moderation/"+target.type()+"/"+id,json("action",action,"reason",reason,"price",price));
  }
  @Override public void editEntity(String type,String id,String parent,String name,String description,String price,String image,boolean active) {
    String reference=image==null||image.isBlank()?null:image.startsWith("file:")?upload(image):image;
    String path="admin/catalog/"+table(type)+(id.isBlank()?"":"/"+id);
    call(id.isBlank()?"POST":"PUT",path,json("parentId",parent.isBlank()?null:parent,"name",name,"description",description,
        "price",type.equals("dishes")?Validation.price(price):null,"coverImageUrl",reference,"status",active?"active":"inactive"));
  }
  @Override public void removeEntity(String type,String id) { call("DELETE","admin/catalog/"+table(type)+"/"+id,null); }
  @Override public void removeReviewAsAdmin(String id) { throw new IllegalArgumentException("请填写删除原因"); }
  @Override public void removeReviewAsAdmin(String id,String reason) { call("DELETE","admin/reviews/"+id,json("reason",reason)); }
  @Override public void updateUser(String id,String role,boolean active) { call("PUT","admin/users/"+id,json("role",role,"status",active?"active":"disabled")); }

  private String upload(String reference) {
    if(reference==null||reference.isBlank()) throw new IllegalArgumentException("请选择图片");
    if(reference.startsWith("/api/v1/media/")) return reference;
    try {
      URI uri=URI.create(reference); if(!uri.getScheme().equals("file")) throw new IllegalArgumentException("请重新选择图片");
      File file=new File(uri); if(file.length()>10*1024*1024) throw new IllegalArgumentException("图片不能超过10MB");
      byte[] bytes;
      try(var in=new FileInputStream(file);var buffer=new ByteArrayOutputStream()) {
        byte[] block=new byte[8192];int length;
        while((length=in.read(block))!=-1) {
          if(buffer.size()+length>10*1024*1024) throw new IllegalArgumentException("图片不能超过10MB");
          buffer.write(block,0,length);
        }
        bytes=buffer.toByteArray();
      }
      String boundary="foodtime"+UUID.randomUUID(); var out=new ByteArrayOutputStream();
      out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"photo.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
      out.write(bytes); out.write(("\r\n--"+boundary+"--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
      return text(interpret(http.send("POST","uploads/images",out.toByteArray(),"multipart/form-data; boundary="+boundary,token())),"imageUrl");
    } catch(IOException e) { throw unavailable(); }
  }
  @Override public byte[] loadImage(String reference) {
    URI uri=origin.resolve(reference);
    boolean sameOrigin=origin.getScheme().equals(uri.getScheme())&&origin.getAuthority().equals(uri.getAuthority());
    if(!sameOrigin&&!"https".equals(uri.getScheme())) throw new IllegalArgumentException("图片地址不合法");
    try {
      // Legacy external HTTPS images receive no application cookies or authentication headers.
      HttpTransport loader=sameOrigin?http:new HttpTransport(uri.getScheme()+"://"+uri.getAuthority()+"/",
          new java.net.CookieManager(null,java.net.CookiePolicy.ACCEPT_NONE));
      var response=loader.bytes(uri.toString());
      if(response.status()!=200) throw new RepositoryException(response.status(),"IMAGE_UNAVAILABLE","图片暂不可用",0);
      return response.body();
    } catch(IOException e) { throw unavailable(); }
  }
  private JSONObject call(String method,String path,JSONObject body) {
    try { return interpret(http.request(method,path,body==null?null:body.toString(),method.equals("GET")?null:token())); }
    catch(IOException e) { throw unavailable(); }
  }
  private String token() {
    if(csrf==null) csrf=text(call("GET","auth/csrf",null),"token");
    return csrf;
  }
  private JSONObject interpret(HttpTransport.Response response) {
    JSONObject envelope=envelope(response);
    JSONObject data=envelope.optJSONObject("data"); return data==null?new JSONObject():data;
  }
  private JSONObject envelope(HttpTransport.Response response) {
    JSONObject envelope;
    try { envelope=new JSONObject(response.body()); }
    catch(JSONException e) { throw new RepositoryException(response.status(),"INVALID_RESPONSE","服务响应异常，请重试",0); }
    if(response.status()<200||response.status()>=300||!text(envelope,"code").equals("OK")) {
      if(response.status()==401) clear();
      long retry=0; try { retry=Long.parseLong(response.header("Retry-After")); } catch(NumberFormatException ignored) { }
      throw new RepositoryException(response.status(),text(envelope,"code"),text(envelope,"message"),retry);
    }
    return envelope;
  }
  private <T> List<T> list(String path,Function<JSONObject,T> mapper) {
    try {
      JSONArray data=envelope(http.request("GET",path,null,null)).optJSONArray("data");
      if(data==null) throw new RepositoryException(502,"INVALID_RESPONSE","列表响应异常",0);
      List<T> result=new ArrayList<>();for(int i=0;i<data.length();i++) result.add(mapper.apply(data.optJSONObject(i)));
      return List.copyOf(result);
    } catch(IOException e) { throw unavailable(); }
  }
  private <T> List<T> page(String path,Function<JSONObject,T> mapper) {
    List<T> result=new ArrayList<>(); int page=1;
    while(true) {
      JSONObject data=call("GET",path+(path.contains("?")?"&":"?")+"page="+page+"&size=100",null);
      JSONArray items=data.optJSONArray("items"); if(items==null) throw new RepositoryException(502,"INVALID_RESPONSE","列表响应异常",0);
      for(int i=0;i<items.length();i++) result.add(mapper.apply(items.optJSONObject(i)));
      if(items.length()==0||result.size()>=data.optLong("total")) return List.copyOf(result);
      page++;
    }
  }
  private User person(JSONObject j) { return new User(text(j,"id"),text(j,"email"),text(j,"displayName"),text(j,"role"),!text(j,"status").equals("disabled"),text(j,"avatarUrl"),time(j,"createdAt"),j.optLong("reviewCount")); }
  private String joined(JSONObject j,boolean dish) {
    List<String> names=new ArrayList<>();
    for(String key: dish?List.of("diningHallName","stallName","dishName"):List.of("diningHallName","stallName")) {
      String name=text(j,key); if(!name.isBlank()) names.add(name);
    }
    return String.join(" · ",names);
  }
  private static String text(JSONObject j,String key) { return j==null||j.isNull(key)?"":j.optString(key,""); }
  private static BigDecimal decimal(JSONObject j,String key) { String v=text(j,key); return v.isBlank()?null:new BigDecimal(v); }
  private static long time(JSONObject j,String key) { String v=text(j,key); return v.isBlank()?0:Instant.parse(v).toEpochMilli(); }
  private static JSONObject json(Object... values) {
    JSONObject j=new JSONObject();
    try { for(int i=0;i<values.length;i+=2) j.put((String)values[i],values[i+1]==null?JSONObject.NULL:values[i+1]); }
    catch(JSONException e) { throw new IllegalArgumentException("请求参数不合法",e); } return j;
  }
  private static String table(String type) { return switch(type) { case "halls" -> "dining-halls"; case "stalls","dishes" -> type; default -> throw new IllegalArgumentException("目录类型不合法"); }; }
  private RepositoryException missing(String message) { return new RepositoryException(404,"NOT_FOUND",message,0); }
  private RepositoryException unavailable() { return new RepositoryException(0,"NETWORK_UNAVAILABLE","连接失败，请检查服务后重试",0); }
  private void clear() {
    user=null;csrf=null;http.clearSession(); hallList=List.of();halls.clear();stalls.clear();dishes.clear();
    stallLists.clear();dishLists.clear();reviews.clear();photos.clear();ratings.clear();contexts.clear();catalog.clear();entities.clear();parents.clear();
    replyPages.clear();ownReplies=ReplyPage.empty();ownReviews=List.of();ownSubmissions=List.of();queue=List.of();auditList=List.of();userList=List.of();
  }
}
