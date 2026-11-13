package cn.edu.bjtu.foodtime.data.remote;

import cn.edu.bjtu.foodtime.data.prototype.CommunityPrototype;
import cn.edu.bjtu.foodtime.domain.model.CommunityText;
import cn.edu.bjtu.foodtime.domain.model.Models.*;
import cn.edu.bjtu.foodtime.domain.model.ReportManagement;
import cn.edu.bjtu.foodtime.domain.model.FeedbackModels;
import cn.edu.bjtu.foodtime.domain.model.NoticeSchedule;
import cn.edu.bjtu.foodtime.domain.model.AccountLifecycle;
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
  private final VenueRemote venues;
  private final URI origin;
  private ImageDiskCache imageCache;
  private final java.util.concurrent.atomic.AtomicLong imageEpoch=new java.util.concurrent.atomic.AtomicLong();
  private final CommunityPrototype communityPrototype;
  private volatile String csrf;
  private volatile User user;
  private volatile CapabilitySnapshot capabilities=CapabilitySnapshot.empty();
  private volatile String sessionMode="normal";
  private volatile HistorySettings historySettings;
  private String capabilitiesOwner="";
  private final Object dashboardLock=new Object();
  private long dashboardRequest;
  private DashboardSnapshot dashboardSnapshot;
  private record DashboardSnapshot(String owner, AdminDashboard value) {}
  private long recommendationRequest;
  private DailyRecommendations recommendations;
  private String recommendationActor="";
  private String recommendationOwner() {return user!=null&&user.active()?user.id()+":"+user.role()+":"+http.sessionKey():"";}
  @Override public DailyRecommendations dailyRecommendations() {
    synchronized(dashboardLock){return recommendationActor.equals(recommendationOwner())?recommendations:null;}
  }
  private void clearRecommendations(){recommendations=null;recommendationActor="";recommendationRequest++;}
  private void fetchRecommendations() {
    final String owner;final long request;
    synchronized(dashboardLock){owner=recommendationOwner();request=++recommendationRequest;recommendations=null;recommendationActor="";}
    if(owner.isEmpty())throw new RepositoryException(401,"UNAUTHENTICATED","请重新登录",0);
    DailyRecommendations result;
    try {result=RecommendationJson.parse(call("GET","recommendations/daily",null));}
    catch(RepositoryException failure){synchronized(dashboardLock){if(request==recommendationRequest)clearRecommendations();}throw failure;}
    synchronized(dashboardLock){
      if(Thread.currentThread().isInterrupted()||request!=recommendationRequest||!owner.equals(recommendationOwner()))throw new RepositoryException(0,"STALE_RESPONSE","请求已失效，请重试",0);
      recommendations=result;recommendationActor=owner;
    }
  }
  private volatile List<Hall> hallList=List.of();
  private final Map<String,Hall> halls=new ConcurrentHashMap<>();
  private final Map<String,Stall> stalls=new ConcurrentHashMap<>();
  private final Map<String,Dish> dishes=new ConcurrentHashMap<>();
  private final Map<String,List<Stall>> stallLists=new ConcurrentHashMap<>();
  private final Map<String,List<Dish>> dishLists=new ConcurrentHashMap<>();
  private final Map<String,List<Review>> reviews=new ConcurrentHashMap<>();
  private final Map<String,ReviewPage> dishReviewPages=new ConcurrentHashMap<>();
  private final Map<String,List<Photo>> photos=new ConcurrentHashMap<>();
  private final Map<String,CommunityPost> communityPostCache=new ConcurrentHashMap<>();
  private final Map<String,List<CommunityComment>> communityCommentCache=new ConcurrentHashMap<>();
  private final Map<String,CommunityCommentPage> communityRoots=new ConcurrentHashMap<>(),communityReplies=new ConcurrentHashMap<>();
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
  private volatile List<CommunityPost> communityFeed=List.of();
  private volatile cn.edu.bjtu.foodtime.domain.model.CommunityPins.Snapshot pinSnapshot=cn.edu.bjtu.foodtime.domain.model.CommunityPins.Snapshot.empty();
  private String pinOwner="";
  private volatile CommunityPostPage ownCommunityPosts=CommunityPostPage.empty(),ownCommunityFavorites=CommunityPostPage.empty();
  private volatile CommunityFeedSlice followingFeed = new CommunityFeedSlice(List.of(), 0, "");
  private volatile CommunityAuditPage communityAudit=CommunityAuditPage.empty();
  private volatile CommunityAuditDetail communityAuditDetail;
  private volatile String communityCursor="";
  private volatile FollowPage followingPage=FollowPage.empty(),followersPage=FollowPage.empty();
  private UserSearchPage userSearchPage=UserSearchPage.empty();
  private long userSearchRequest;
  // Share the identity lock: account changes and private-list publication are atomic.
  private final Object followLock=dashboardLock;
  private String followOwner="";
  private long followEpoch,followingRequest,followersRequest;
  private boolean followingNeedsRebase;
  private final Map<String,FollowState> followStates=new ConcurrentHashMap<>();
  private volatile NoticePage noticePage=NoticePage.empty();
  private volatile int noticeNumber=1;
  private volatile MessagePage messagePage=MessagePage.empty();
  private volatile int messageNumber=1;
  private volatile ConversationPage conversationPage=ConversationPage.empty();
  private volatile int conversationNumber=1;
  private final Map<String,Conversation> conversationCache=new ConcurrentHashMap<>();
  private final Map<String,DirectMessagePage> chatCache=new ConcurrentHashMap<>();
  private volatile long noticeUnread=0,messageUnread=0,directUnread=0;
  private volatile NoticeFeed lastFeed=NoticeFeed.empty();
  private volatile boolean communityHasMore;
  private final Map<String,String> communityUploads=new ConcurrentHashMap<>();
  private final Map<String,String> catalogUploads=new ConcurrentHashMap<>();
  private final Map<String,String> avatarUploads=new ConcurrentHashMap<>();

  public RemoteRepository(String baseUrl,HttpTransport http) {
    this(baseUrl,http,false);
  }
  public RemoteRepository(String baseUrl,HttpTransport http,boolean communityPrototypeEnabled) {
    this.http=http;
    this.venues=new VenueRemote(this::call,()->user==null?"guest:"+http.sessionKey():user.id()+":"+user.role()+":"+http.sessionKey());
    this.communityPrototype=communityPrototypeEnabled?new CommunityPrototype():null;
    URI base=URI.create(baseUrl); origin=URI.create(base.getScheme()+"://"+base.getAuthority()+"/");
  }
  @Override public cn.edu.bjtu.foodtime.domain.model.VenueModels.Target venueSummary(String type,String id){return venues.summary(type,id);}
  @Override public void loadVenueSummary(String type,String id){venues.loadSummary(type,id);}
  @Override public cn.edu.bjtu.foodtime.domain.model.VenueModels.Snapshot venue(String type,String id){return venues.snapshot(type,id);}
  @Override public void loadVenue(String type,String id,String rating,boolean more){venues.load(type,id,rating,more);}
  @Override public cn.edu.bjtu.foodtime.domain.model.VenueModels.Row submitVenue(String type,String id,int rating,String content,String request){return venues.submit(type,id,rating,content,request);}
  @Override public cn.edu.bjtu.foodtime.domain.model.VenueModels.Page venueReplies(String review,boolean mine){return venues.replies(review,mine);}
  @Override public void loadVenueReplies(String review,boolean more,boolean mineMore){venues.loadReplies(review,more,mineMore);}
  @Override public cn.edu.bjtu.foodtime.domain.model.VenueModels.Row submitVenueReply(String review,String content,String target,String request){return venues.submitReply(review,content,target,request);}
  @Override public void refreshVenuePending(String type,String id){venues.refreshPending(type,id);}
  @Override public void refreshVenueRepliesPending(String id){venues.refreshReplies(id);}
  @Override public LikeState likeVenue(String id,boolean liked,boolean reply){return venues.like(id,liked,reply);}
  @Override public void deleteVenue(String id,boolean reply,boolean admin,String reason){venues.delete(id,reply,admin,reason);}
  @Override public cn.edu.bjtu.foodtime.domain.model.VenueModels.Page myVenueReviews(){return venues.personal();}
  @Override public void loadMyVenueReviews(String type,boolean replies,int page){venues.loadPersonal(type,replies,page);}
  @Override public cn.edu.bjtu.foodtime.domain.model.VenueModels.AuditDetail venueAuditDetail(String type,String id){return venues.auditDetail(type,id);}
  @Override public User currentUser() { return user; }
  public RemoteRepository withImageCache(File directory) { imageCache=new ImageDiskCache(directory,200L*1024*1024);return this; }
  private String imageScope() {
    var actor=user;
    return origin+":"+(actor==null?"guest":actor.id()+":"+actor.role())+":"+http.sessionKey();
  }
  @Override public String imageIdentity() {return imageScope()+":"+imageEpoch.get();}
  @Override public long imageCacheBytes() {return imageCache==null?0:imageCache.size();}
  @Override public void clearImageCache() {
    synchronized(imageEpoch){imageEpoch.incrementAndGet();if(imageCache!=null)imageCache.clear(false);}
  }
  @Override public void restoreSession() {
    try {
      profile();
      sessionMode="normal";
      fetchCapabilitiesQuietly();
    } catch(RepositoryException failure) {
      if(failure.status()==401) return;
      if(failure.status()!=403) throw failure;
      // A disabled account's normal profile endpoint is blocked; /auth/me remains allowed.
      JSONObject data=call("GET","auth/me",null);
      if(!"appeal_only".equals(text(data,"sessionMode"))) throw failure;
      sessionMode="appeal_only";
      user=new User(text(data,"id"),text(data,"email"),text(data,"displayName"),text(data,"role"),
          false,"",0,0);
      clearCapabilities();
    }
  }
  @Override public String sessionMode() { return sessionMode; }
  private void profile() {
    try {
      User next=person(call("GET","users/me/profile",null));
      synchronized(dashboardLock) {
        if(user==null||!user.id().equals(next.id())||!user.role().equals(next.role())||user.active()!=next.active()) {
          clearRecommendations();
          clearCapabilities();
        }
        if(!dashboardOwner(user).equals(dashboardOwner(next))) {dashboardSnapshot=null;dashboardRequest++;}
        user=next;
        syncFollowOwner();
      }
    } catch(RepositoryException denied) {
      if(denied.status()==401||denied.status()==403){invalidateDashboard();synchronized(followLock){resetFollows();clearRecommendations();}}
      throw denied;
    }
  }
  private static String dashboardOwner(User actor) {
    return actor!=null&&actor.active()&&actor.role().equals("superadmin")?actor.id()+":"+actor.role():"";
  }
  @Override public CapabilitySnapshot capabilities() {
    synchronized(dashboardLock) {
      return capabilitiesOwner.equals(capabilityOwner(user)) ? capabilities : CapabilitySnapshot.empty();
    }
  }
  @Override public void loadCapabilities() { fetchCapabilities(); }
  private String capabilityOwner(User actor) {
    return actor!=null&&actor.active()?actor.id()+":"+actor.role()+":"+http.sessionKey():"";
  }
  private void clearCapabilities() {
    capabilities=CapabilitySnapshot.empty();
    capabilitiesOwner="";
  }
  private void fetchCapabilitiesQuietly() {
    try { fetchCapabilities(); }
    catch(RepositoryException failure) {
      clearCapabilities();
      if(failure.status()==401) throw failure;
    }
  }
  private void fetchCapabilities() {
    final String owner;
    synchronized(dashboardLock) {owner=capabilityOwner(user);}
    if(owner.isEmpty())throw new RepositoryException(401,"UNAUTHENTICATED","请重新登录",0);
    JSONObject data=call("GET","users/me/capabilities",null);
    JSONArray items=data.optJSONArray("capabilities");
    String revision=text(data,"revision");
    if(items==null||revision.isBlank())throw invalidCapabilityResponse();
    List<String> names=new ArrayList<>();
    for(int i=0;i<items.length();i++) {
      Object value=items.opt(i);
      if(!(value instanceof String name)||name.isBlank())throw invalidCapabilityResponse();
      names.add(name);
    }
    synchronized(dashboardLock) {
      if(!owner.equals(capabilityOwner(user)))throw new RepositoryException(0,"STALE_RESPONSE","请求已失效，请重试",0);
      capabilities=new CapabilitySnapshot(names,revision);
      capabilitiesOwner=owner;
    }
  }
  private static RepositoryException invalidCapabilityResponse() {
    return new RepositoryException(0,"INVALID_RESPONSE","能力响应异常，请稍后重试",0);
  }
  @Override public AdminDashboard adminDashboard() {
    synchronized(dashboardLock) {
      return dashboardSnapshot!=null&&dashboardSnapshot.owner().equals(dashboardOwner(user))?dashboardSnapshot.value():null;
    }
  }
  private void invalidateDashboard() {
    synchronized(dashboardLock) {dashboardSnapshot=null;dashboardRequest++;}
  }
  private void fetchDashboard() {
    final long request;
    final String owner;
    synchronized(dashboardLock) {
      owner=dashboardOwner(user);
      if(owner.isEmpty()) {dashboardSnapshot=null;dashboardRequest++;throw new RepositoryException(403,"ACCESS_DENIED","需要平台管理员权限",0);}
      request=++dashboardRequest;
    }
    try {
      AdminDashboard data=DashboardJson.parse(call("GET","admin/dashboard?days=30",null),30);
      synchronized(dashboardLock) {
        if(Thread.currentThread().isInterrupted()||request!=dashboardRequest||!owner.equals(dashboardOwner(user)))
          throw new RepositoryException(0,"STALE_RESPONSE","请求已失效，请重试",0);
        dashboardSnapshot=new DashboardSnapshot(owner,data);
      }
    } catch(RepositoryException failure) {
      if(failure.status()==401||failure.status()==403)invalidateDashboard();
      throw failure;
    }
  }
  @Override public User login(String account,String password) {
    return login(account,password,null,null);
  }
  @Override public User login(String account,String password,String challengeId,String challengeAnswer) {
    Validation.accountEmail(account); Validation.password(password,false);
    csrf=null; token();
    JSONObject body=json("account",account.strip(),"password",password);
    if(challengeId!=null&&!challengeId.isBlank()&&challengeAnswer!=null&&!challengeAnswer.isBlank()) {
      try { body.put("challengeId",challengeId); body.put("challengeAnswer",challengeAnswer.strip()); }
      catch(JSONException failure) { throw new IllegalArgumentException("验证码参数不合法",failure); }
    }
    JSONObject data=call("POST","auth/login",body);
    csrf=null; token();
    String mode=text(data,"sessionMode");
    if("appeal_only".equals(mode)) {
      JSONObject row=data.optJSONObject("user");
      sessionMode="appeal_only";
      user=new User(text(row,"id"),text(row,"email"),text(row,"displayName"),text(row,"role"),
          false,"",0,0);
      clearCapabilities();
      return user;
    }
    sessionMode="normal"; profile(); fetchCapabilitiesQuietly(); return user;
  }
  @Override public RegistrationPolicy registrationPolicy() {
    JSONObject data=call("GET","auth/registration-policy",null);
    String mode=text(data,"mode");
    if(!mode.equals("open")&&!mode.equals("invitation")&&!mode.equals("closed"))
      throw new RepositoryException(0,"INVALID_RESPONSE","注册策略响应异常，请稍后重试",0);
    String revision=text(data,"revision");
    if(revision.isBlank())throw new RepositoryException(0,"INVALID_RESPONSE","注册策略响应异常，请稍后重试",0);
    return new RegistrationPolicy(mode,data.optBoolean("canSendEmailCode",false),
        data.optBoolean("invitationRequired",false),revision);
  }
  @Override public CaptchaChallenge captcha() {
    JSONObject data=call("GET","auth/captcha",null);
    String challengeId=text(data,"challengeId"),image=text(data,"imageBase64"),expires=text(data,"expiresAt");
    if(!challengeId.matches("[0-9a-fA-F-]{36}")||image.isBlank())
      throw new RepositoryException(0,"INVALID_RESPONSE","验证码响应异常，请重试",0);
    try {
      java.util.Base64.getDecoder().decode(image);
      Instant.parse(expires);
    } catch(RuntimeException failure) {
      throw new RepositoryException(0,"INVALID_RESPONSE","验证码响应异常，请重试",0);
    }
    return new CaptchaChallenge(challengeId,image,expires);
  }
  @Override public String sendVerificationCode(String email) {
    Validation.email(email);
    call("POST","auth/register/email-code",json("email",email.strip()));
    return ""; // Verification codes only arrive by email; never return or invent a client code.
  }
  @Override public void register(String email,String password,String code) {
    register(email,password,code,null);
  }
  @Override public void register(String email,String password,String code,String invitationCode) {
    Validation.email(email); Validation.password(password,true);
    JSONObject body=json("email",email.strip(),"password",password,"verificationCode",code);
    String invite=invitationCode==null?"":invitationCode.strip();
    if(!invite.isEmpty()) {
      try { body.put("invitationCode",invite); }
      catch(JSONException failure) { throw new IllegalArgumentException("邀请码不合法",failure); }
    }
    call("POST","auth/register",body);
  }
  @Override public long sendPasswordResetCode(String email) {
    var response=call("POST","auth/password-reset/email-code",json("email",Validation.accountEmail(email)));
    return response.optLong("resendAfterSeconds",60);
  }
  @Override public cn.edu.bjtu.foodtime.domain.model.PasswordResetGrant verifyPasswordResetCode(String email,String code) {
    if(!code.matches("[0-9]{6}"))throw new IllegalArgumentException("验证码须为6位数字");
    var response=call("POST","auth/password-reset/verify",json("email",Validation.accountEmail(email),"verificationCode",code));
    String grant=text(response,"resetToken");
    try {
      if(!grant.matches("[A-Za-z0-9_-]{43}"))throw new IllegalArgumentException();
      long expires=java.time.Instant.parse(text(response,"expiresAt")).toEpochMilli();
      return new cn.edu.bjtu.foodtime.domain.model.PasswordResetGrant(grant,expires);
    }catch(RuntimeException e){throw new RepositoryException(0,"INVALID_RESPONSE","验证响应异常，请重新验证邮箱",0);}
  }
  @Override public void resetPassword(String grant,String newPassword) {
    Validation.password(newPassword,true);
    if(!grant.matches("[A-Za-z0-9_-]{43}"))throw new IllegalArgumentException("验证已失效，请重新验证邮箱");
    call("POST","auth/password-reset",json("resetToken",grant,"newPassword",newPassword));clear();
  }
  @Override public void logout() {
    try { call("POST","auth/logout",null); }
    finally { clear(); }
  }
  private static RepositoryException invalidAccount(){return new RepositoryException(502,"INVALID_ACCOUNT_RESPONSE","账号状态响应异常，请重新读取后再操作",0);}
  private static String accountInstant(JSONObject row,String key){String value=text(row,key);try{Instant.parse(value);return value;}catch(RuntimeException invalid){throw invalidAccount();}}
  private User accountActor(){User actor=user;if(actor==null)throw new RepositoryException(401,"UNAUTHENTICATED","请重新登录",0);if(actor.isAdmin())throw new RepositoryException(403,"ACCOUNT_ACTION_FORBIDDEN","管理员和超级管理员不能注销、绑定或解绑学校邮箱",0);return actor;}
  private void sameAccount(User actor){if(user==null||!user.id().equals(actor.id())||!user.role().equals(actor.role()))throw new RepositoryException(409,"SESSION_CHANGED","账号状态已变化，请重新读取",0);}
  @Override public AccountLifecycle.Summary accountLifecycle(){
    JSONObject row=call("GET","users/me/account-lifecycle",null);
    if(text(row,"verificationEmail").isBlank()||!(row.opt("canDelete") instanceof Boolean)||!(row.opt("canUnbind") instanceof Boolean))throw invalidAccount();
    String next=text(row,"deletionCodeAvailableAt");if(!next.isBlank())accountInstant(row,"deletionCodeAvailableAt");
    return new AccountLifecycle.Summary(text(row,"verificationEmail"),text(row,"schoolEmail"),row.optBoolean("canDelete"),row.optBoolean("canUnbind"),next);
  }
  @Override public AccountLifecycle.Sent sendAccountActionCode(boolean deletion){
    User actor=accountActor();JSONObject row=call("POST","users/me/account-lifecycle/"+(deletion?"deletion-code":"unbind-code"),null);sameAccount(actor);
    if(text(row,"verificationEmail").isBlank())throw invalidAccount();
    return new AccountLifecycle.Sent(accountInstant(row,"expiresAt"),accountInstant(row,"nextSendAt"),text(row,"verificationEmail"));
  }
  private static String actionCode(String input){if(input==null||!input.strip().matches("[0-9]{6}"))throw new IllegalArgumentException("请填写 6 位邮箱验证码");return input.strip();}
  @Override public String unbindSchoolEmail(String code){
    User actor=accountActor();JSONObject row=call("POST","users/me/account-lifecycle/unbind",json("verificationCode",actionCode(code)));sameAccount(actor);
    JSONObject profile=row.optJSONObject("profile");if(profile==null||!text(profile,"id").equals(actor.id())||!Set.of("visitor","user").contains(text(profile,"role"))||!profile.isNull("schoolEmail")||!Boolean.FALSE.equals(profile.opt("schoolEmailVerified")))throw invalidAccount();
    String reusable=accountInstant(row,"schoolEmailReusableAt");synchronized(dashboardLock){user=person(profile);clearRecommendations();}return reusable;
  }
  @Override public String deleteAccount(String code){
    User actor=accountActor();JSONObject row=call("POST","users/me/account-lifecycle/delete",json("verificationCode",actionCode(code)));
    if(user!=null&&!user.id().equals(actor.id()))throw new RepositoryException(409,"SESSION_CHANGED","账号状态已变化，请重新读取",0);
    if(!Boolean.TRUE.equals(row.opt("deleted")))throw invalidAccount();String reusable=accountInstant(row,"emailsReusableAt");clear();return reusable;
  }
  @Override public void changePassword(String oldPassword,String newPassword) {
    Validation.password(newPassword,true);
    call("PUT","users/me/password",json("oldPassword",oldPassword,"newPassword",newPassword)); clear();
  }
  @Override public User updateProfile(String displayName,String avatarReference) {
    String name=Validation.text(displayName,50,"昵称");
    User actor=user;if(actor==null)throw new RepositoryException(401,"UNAUTHENTICATED","请重新登录",0);
    String avatar=avatarReference==null||avatarReference.isBlank()?null:
        avatarReference.startsWith("file:")?avatarUploads.computeIfAbsent(actor.id()+":"+avatarReference,key->upload(avatarReference,true)):avatarReference;
    if(user==null||!user.id().equals(actor.id()))throw new RepositoryException(401,"UNAUTHENTICATED","登录状态已变化，请重新登录",0);
    user=person(call("PUT","users/me/profile",json("displayName",name,"avatarUrl",avatar)));
    return user;
  }

  @Override public void refresh(ContentRequest request) {
    // Community reads are independently authorized; a profile round trip must not delay them.
    switch(request.query()) {
      case COMMUNITY_FEED, COMMUNITY_FOLLOWING, MY_COMMUNITY_POSTS, MY_COMMUNITY_FAVORITES, COMMUNITY_POST -> {}
      default -> profile();
    }
    switch(request.query()) {
      case DAILY_RECOMMENDATIONS -> fetchRecommendations();
      case VENUE_REVIEWS -> {String[] p=request.id().split("~",-1);venues.load(p[0],p[1],p.length>2&&!p[2].isBlank()?p[2]:"all",false);}
      case VENUE_PERSONAL -> {String[] p=request.id().split("~",-1);venues.loadPersonal(p.length>0?p[0]:"",p.length>1&&p[1].equals("replies"),p.length>2?Integer.parseInt(p[2]):1);}
      case HALLS -> fetchHalls();
      case HALL_TARGET -> { if(!halls.containsKey(request.id()))fetchHalls(); }
      case STALLS -> { fetchHalls(); fetchStalls(request.id()); }
      case STALL_TARGET -> ensureStall(request.id());
      case DISHES -> { ensureStall(request.id()); fetchDishes(request.id()); }
      case DISH_DETAIL -> fetchDetail(request.id());
      case DISH_TARGET -> fetchDishTarget(request.id());
      case DISH_IMAGES -> { fetchDishTarget(request.id()); fetchDishImages(request.id()); }
      case COMMUNITY_FEED -> {
        if(communityPrototype==null) loadCommunityFeed(false);
        else syncPrototypeFeed();
      }
      case COMMUNITY_MODERATION -> {
        String[] parts=request.id().split("~",-1);String type=parts[0],status=parts.length>1?parts[1]:"pending";int number=parts.length>2?Integer.parseInt(parts[2]):1;
        JSONObject data=call("GET","admin/community/"+type+"?status="+status+"&page="+number+"&size=20",null);
        List<Submission> rows=new ArrayList<>();JSONArray items=data.optJSONArray("items");
        if(items!=null)for(int i=0;i<items.length();i++){
          JSONObject item=items.optJSONObject(i);rows.add(new Submission(text(item,"id"),text(item,"type"),text(item,"targetId"),text(item,"userId"),text(item,"userDisplayName"),text(item,"name"),text(item,"content"),null,text(item,"imageUrl"),0,text(item,"status"),text(item,"reason"),time(item,"createdAt"),"",assessment(item),text(item,"userEmail")));
        }
        communityAudit=new CommunityAuditPage(type,status,number,data.optLong("total"),rows);
      }
      case COMMUNITY_MODERATION_DETAIL -> {
        String[] parts=request.id().split("~",-1);JSONObject data=call("GET","admin/community/"+parts[0]+"/"+parts[1],null);
        JSONObject comment=data.optJSONObject("comment"),root=data.optJSONObject("rootComment"),target=data.optJSONObject("replyToComment");
        AiAssessment ai=communityAudit.items().stream().filter(item->item.id().equals(parts[1])).map(Submission::ai).findFirst().orElse(assessment(data.optJSONObject("post")));
        communityAuditDetail=new CommunityAuditDetail(cacheCommunityPost(data.optJSONObject("post")),comment==null?null:communityComment(comment),root==null?null:communityComment(root),target==null?null:communityComment(target),ai,text(data,"userEmail"));
      }
      case COMMUNITY_FOLLOWING -> loadCommunityFollowing(false);
      case MY_COMMUNITY_POSTS -> loadCommunityCollection(false,false);
      case MY_COMMUNITY_FAVORITES -> loadCommunityCollection(true,false);
      case COMMUNITY_POST -> loadCommunityPost(request.id());
      case COMMUNITY_DETAIL -> {
        CommunityPost post=loadCommunityPost(request.id());
        if(communityPrototype==null) {
          loadCommunityAuthorFollow(post.userId());
          if(post.status().equals("approved"))reloadCommunityComments(post.id());
        }
      }
      case FOLLOWING -> ensureFollows(false);
      case FOLLOWERS -> ensureFollows(true);
      case MY_REVIEWS -> ownReviews=reviewPage("users/me/reviews");
      case MY_SUBMISSIONS -> fetchOwnSubmissions();
      case REVIEW_REPLIES -> {
        loadReviewReplies(request.id().split("~",-1)[0]);
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
      case ADMIN_SUMMARY -> queue=page("admin/moderation?status=pending",j -> submission(j,text(j,"type"),true));
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
      case ADMIN_DASHBOARD -> fetchDashboard();
      case AUDITS -> auditList=page("admin/moderation/logs",j -> new Audit(text(j,"id"),text(j,"submissionId"),text(j,"moderatorId"),text(j,"action"),text(j,"reason"),time(j,"createdAt"),text(j,"type"),text(j,"actor"),text(j,"aiModel"),text(j,"userId"),text(j,"userDisplayName"),text(j,"userEmail"),text(j,"name"),text(j,"content"),text(j,"imageUrl"),time(j,"submittedAt")));
      case NOTICE_FEED -> {
        NoticeFeed polled=getNoticeFeed(0,20); lastFeed=polled; noticeUnread=polled.unreadCount();
      }
      case NOTICES -> {
        loadNoticePage(1);
        noticeUnread=call("GET","users/me/notices/unread-count",null).optLong("unreadCount"); messageUnread=getUnreadCount();
        directUnread=getDirectUnreadCount();
      }
      case MESSAGES -> { loadMessagePage(1); messageUnread=getUnreadCount(); }
      case CONVERSATIONS -> {
        if(request.id().isBlank()) { loadConversations(1); directUnread=getDirectUnreadCount(); }
        else {
          loadConversationDetail(request.id());
          loadConversationMessages(request.id(),"");
        }
      }
      case PROFILE -> { }
    }
  }
  private void fetchHalls() {
    var items=list("dining-halls",j -> new Hall(text(j,"id"),text(j,"name"),text(j,"coverImageUrl"),true,j.isNull("averageScore")?null:new BigDecimal(j.opt("averageScore").toString()),j.optLong("reviewCount")));
    halls.clear(); for(Hall h:items) halls.put(h.id(),h); hallList=items;
  }
  private void fetchStalls(String id) {
    var items=list("dining-halls/"+id+"/stalls",j -> new Stall(text(j,"id"),id,text(j,"name"),text(j,"coverImageUrl"),true,j.isNull("averageScore")?null:new BigDecimal(j.opt("averageScore").toString()),j.optLong("reviewCount")));
    stalls.entrySet().removeIf(e -> e.getValue().diningHallId().equals(id));
    for(Stall s:items) stalls.put(s.id(),s); stallLists.put(id,items);
  }
  private void ensureStall(String id) {
    Stall known=stalls.get(id);
    if(known!=null&&halls.containsKey(known.diningHallId())) return;
    fetchHalls();
    Stall cached=stalls.get(id);
    if(cached!=null && halls.containsKey(cached.diningHallId())) fetchStalls(cached.diningHallId());
    else for(Hall h:hallList) fetchStalls(h.id());
    if(!stalls.containsKey(id)) throw missing("档口不存在或已停用");
  }
  private void fetchDishes(String id) {
    var items=list("stalls/"+id+"/dishes",j -> new Dish(text(j,"id"),id,text(j,"name"),decimal(j,"price"),"",true,j.optLong("revision",0),text(j,"priceUnit")));
    dishes.entrySet().removeIf(e -> e.getValue().stallId().equals(id));
    // The stall page only needs the catalog rows. Loading every dish gallery here turns one
    // navigation into an N+1 request burst, so media stays lazy until DISH_DETAIL is opened.
    for(Dish d:items) dishes.put(d.id(),d);
    dishLists.put(id,items);
  }
  @Override public List<Hall> loadHalls() {
    fetchHalls();
    return halls();
  }

  @Override public List<Stall> loadStalls(String hallId) {
    fetchStalls(hallId);
    return stalls(hallId);
  }

  @Override public List<Dish> loadDishes(String stallId) {
    fetchDishes(stallId);
    return dishes(stallId);
  }

  @Override public DishSearchPage searchDishes(String query, int page) {
    return searchDishes(query,page,DishSearchFilters.empty());
  }
  @Override public DishSearchPage searchDishes(String query, int page, DishSearchFilters filters) {
    StringBuilder path=new StringBuilder("dishes/search?q=").append(encode(query))
        .append("&page=").append(page).append("&size=20&sort=")
        .append(encode(filters.sort()==null||filters.sort().isBlank()?"relevance":filters.sort()));
    if(!filters.diningHallIds().isEmpty()) {
      List<String> encoded=new ArrayList<>();
      for(String hall:filters.diningHallIds()) encoded.add(encode(hall));
      path.append("&diningHallIds=").append(String.join(",",encoded));
    }
    if(filters.minPrice()!=null)path.append("&minPrice=").append(filters.minPrice().toPlainString());
    if(filters.maxPrice()!=null)path.append("&maxPrice=").append(filters.maxPrice().toPlainString());
    if(filters.minRating()!=null)path.append("&minRating=").append(filters.minRating().toPlainString());
    return dishRows(path.toString(),page);
  }

  @Override public DishSearchPage favoriteDishes(int page) {
    return favoriteDishes(page,null,false);
  }
  @Override public DishSearchPage favoriteDishes(int page,String groupId,boolean unfiled) {
    StringBuilder path=new StringBuilder("users/me/dish-favorites?page=").append(page).append("&size=20");
    if(groupId!=null&&!groupId.isBlank())path.append("&groupId=").append(encode(groupId));
    else if(unfiled)path.append("&unfiled=true");
    return dishRows(path.toString(),page);
  }
  @Override public List<FavoriteGroup> favoriteGroups(String kind) {
    return list("users/me/favorite-groups?kind=" + encode(kind), this::favoriteGroup);
  }
  @Override public FavoriteGroup createFavoriteGroup(String kind,String name) {
    return favoriteGroup(call("POST","users/me/favorite-groups",json("kind",kind,"name",name)));
  }
  @Override public FavoriteGroup updateFavoriteGroup(String id,String name,Integer sortOrder,Long expectedRevision) {
    return favoriteGroup(call("PUT","users/me/favorite-groups/"+encode(id),
        json("name",name,"sortOrder",sortOrder,"expectedRevision",expectedRevision)));
  }
  @Override public void deleteFavoriteGroup(String id) {
    call("DELETE","users/me/favorite-groups/"+encode(id),null);
  }
  @Override public List<String> favoriteGroupMembership(String kind,String targetId) {
    return groupIds(call("GET","users/me/favorite-groups/memberships?kind="+encode(kind)+"&targetId="+encode(targetId),null));
  }
  @Override public List<String> addFavoriteGroupMember(String groupId,String targetId) {
    return groupIds(call("PUT","users/me/favorite-groups/"+encode(groupId)+"/members/"+encode(targetId),null));
  }
  @Override public List<String> removeFavoriteGroupMember(String groupId,String targetId) {
    return groupIds(call("DELETE","users/me/favorite-groups/"+encode(groupId)+"/members/"+encode(targetId),null));
  }
  private FavoriteGroup favoriteGroup(JSONObject row) {
    String id=text(row,"id"),kind=text(row,"kind"),name=text(row,"name");
    if(id.isBlank()||name.isBlank())throw new RepositoryException(0,"INVALID_RESPONSE","分组响应异常，请重试",0);
    return new FavoriteGroup(id,kind,name,row.optInt("sortOrder",0),row.optLong("revision",0),row.optLong("itemCount",0));
  }
  private static List<String> groupIds(JSONObject data) {
    JSONArray rows=data.optJSONArray("groupIds");
    if(rows==null)throw new RepositoryException(0,"INVALID_RESPONSE","分组响应异常，请重试",0);
    List<String> ids=new ArrayList<>();
    for(int i=0;i<rows.length();i++){String id=rows.optString(i,"");if(!id.isBlank())ids.add(id);}
    return List.copyOf(ids);
  }
  @Override public CatalogCorrectionPage myCorrections(int page) {
    JSONObject data=call("GET","users/me/catalog-corrections?page="+page+"&size=20",null);
    JSONArray rows=data.optJSONArray("items");
    if(rows==null)throw new RepositoryException(0,"INVALID_RESPONSE","纠错列表响应异常，请重试",0);
    List<CatalogCorrection> items=new ArrayList<>();
    for(int i=0;i<rows.length();i++)items.add(correction(rows.optJSONObject(i)));
    return new CatalogCorrectionPage(items,data.optLong("total"),data.optInt("page",page),data.optInt("size",20));
  }
  @Override public CatalogCorrection submitCorrection(CorrectionProposal proposal) {
    boolean name=proposal.changedFields().contains("name");
    boolean description=proposal.changedFields().contains("description");
    boolean price=proposal.changedFields().contains("price");
    JSONObject body=json("targetType",proposal.targetType(),"targetId",proposal.targetId(),
        "baseRevision",proposal.baseRevision(),"changedFields",array(proposal.changedFields()),
        "name",name?proposal.name():null,"description",description?proposal.description():null,
        "price",price?proposal.price():null,"priceUnit",proposal.priceUnit(),"reason",proposal.reason(),
        "evidenceUrls",array(proposal.evidenceUrls()),"idempotencyKey",proposal.idempotencyKey());
    return correction(call("POST","users/me/catalog-corrections",body));
  }
  @Override public NotificationPreferences notificationPreferences() {
    return NotificationJson.preferences(accountCall("GET","users/me/notification-preferences",null));
  }
  @Override public NotificationPreference updateNotificationPreference(String category,boolean enabled,long expectedRevision) {
    var result=NotificationJson.preference(accountCall("PUT","users/me/notification-preferences/"+encode(category),json("enabled",enabled,"expectedRevision",expectedRevision)));
    if(!category.equals(result.category()))throw NotificationJson.invalid();return result;
  }
  @Override public NotificationPreference adoptLegacyMessagePreference(boolean enabled) {
    var result=NotificationJson.preference(accountCall("POST","users/me/notification-preferences/legacy-message",json("enabled",enabled)));
    if(!"message".equals(result.category())||result.revision()<2)throw NotificationJson.invalid();return result;
  }
  @Override public NotificationEventPage notificationEvents(long afterSeq,int limit) {
    JSONObject data=accountCall("GET","users/me/notification-events?afterSeq="+afterSeq+"&limit="+limit,null);
    JSONArray rows=data.optJSONArray("items");
    if(rows==null)throw new RepositoryException(0,"INVALID_RESPONSE","提醒事件响应异常，请重试",0);
    List<NotificationEvent> items=new ArrayList<>();
    for(int i=0;i<rows.length();i++) {
      JSONObject row=rows.optJSONObject(i);
      items.add(new NotificationEvent(text(row,"eventId"),row.optLong("seq"),text(row,"category"),
          text(row,"title"),text(row,"body"),text(row,"refType"),text(row,"refId"),
          time(row,"createdAt"),text(row,"deliveryStatus")));
    }
    return new NotificationEventPage(items,data.optLong("nextSeq",afterSeq));
  }
  @Override public void markNotificationDelivered(String eventId) {
    accountCall("POST","users/me/notification-events/"+encode(eventId)+"/delivered",null);
  }

  @Override public ReportFollowUpPage reportFollowUps(String reportId,int page) {
    JSONObject data=call("GET","users/me/reports/"+encode(reportId)+"/follow-ups?page="+page+"&size=20",null);
    JSONArray rows=data.optJSONArray("items");
    if(rows==null)throw new RepositoryException(0,"INVALID_RESPONSE","追问响应异常，请重试",0);
    List<ReportFollowUp> items=new ArrayList<>();
    for(int i=0;i<rows.length();i++)items.add(reportFollowUp(rows.optJSONObject(i)));
    return new ReportFollowUpPage(items,data.optLong("total"),data.optInt("page",page),data.optInt("size",20));
  }
  @Override public ReportFollowUp addReportFollowUp(String reportId,String content,String requestId) {
    return addReportFollowUp(reportId,content,requestId,List.of());
  }
  @Override public ReportFollowUp addReportFollowUp(String reportId,String content,String requestId,
      List<String> evidenceUrls) {
    return reportFollowUp(call("POST","users/me/reports/"+encode(reportId)+"/follow-ups",
        json("content",content,"evidenceUrls",array(evidenceUrls),"clientRequestId",requestId)));
  }
  @Override public String uploadEvidence(String filePath) {
    return upload(filePath);
  }
  @Override public List<ReportAction> decisionActions(String decisionId) {
    String owner=appealOwner();var result=list("users/me/moderation-decisions/"+encode(decisionId)+"/actions",RemoteRepository::reportAction);
    requireAppealOwner(owner);return result;
  }
  private static ReportAction reportAction(JSONObject j) {
    return new ReportAction(text(j,"id"),text(j,"decisionId"),text(j,"actionType"),text(j,"targetType"),
        text(j,"targetId"),text(j,"performedBy"),text(j,"note"),time(j,"createdAt"));
  }
  @Override public void submitAppeal(String decisionId,String content,String requestId) {
    submitAppeal(decisionId,content,requestId,List.of());
  }
  @Override public void submitAppeal(String decisionId,String content,String requestId,
      List<String> evidenceUrls) {
    appealCall("POST","users/me/moderation-decisions/"+encode(decisionId)+"/appeals",
        json("content",content,"evidenceUrls",array(evidenceUrls),"clientRequestId",requestId));
  }
  private static ReportFollowUp reportFollowUp(JSONObject j) {
    return new ReportFollowUp(text(j,"id"),text(j,"reportId"),text(j,"authorRole"),text(j,"authorName"),
        text(j,"content"),time(j,"createdAt"),strings(j,"evidenceUrls"));
  }

  @Override public MyReportPage myReports(int page) {
    JSONObject data=call("GET","users/me/reports?page="+page+"&size=20",null);
    JSONArray rows=data.optJSONArray("items");
    if(rows==null)throw new RepositoryException(0,"INVALID_RESPONSE","举报回执响应异常，请重试",0);
    List<MyReport> items=new ArrayList<>();
    for(int i=0;i<rows.length();i++)items.add(myReport(rows.optJSONObject(i)));
    return new MyReportPage(items,data.optLong("total"),data.optInt("page",page),data.optInt("size",20));
  }
  @Override public ModerationDecisionPage myModerationDecisions(int page) {
    JSONObject data=appealCall("GET","users/me/moderation-decisions?page="+page+"&size=20",null);
    JSONArray rows=data.optJSONArray("items");
    if(rows==null||data.optInt("page",0)!=page||data.optInt("size",0)<1||data.optLong("total",-1)<rows.length())throw new RepositoryException(502,"INVALID_RESPONSE","处理决定分页响应异常，请重试",0);
    List<ModerationDecision> items=new ArrayList<>();
    for(int i=0;i<rows.length();i++)items.add(moderationDecision(rows.optJSONObject(i)));
    return new ModerationDecisionPage(items,data.optLong("total"),data.optInt("page",page),data.optInt("size",20));
  }
  @Override public ModerationDecision myModerationDecision(String id){
    var result=moderationDecision(appealCall("GET","users/me/moderation-decisions/"+encode(id),null));
    if(!id.equals(result.decisionId()))throw new RepositoryException(502,"INVALID_RESPONSE","处理决定编号不匹配",0);return result;
  }
  private String appealOwner(){var actor=user;if(actor==null)throw new RepositoryException(401,"AUTH_REQUIRED","请先登录",0);return actor.id()+":"+sessionMode+":"+http.sessionKey();}
  private void requireAppealOwner(String owner){if(!owner.equals(appealOwner()))throw new RepositoryException(409,"ACCOUNT_CHANGED","账号已变化，请重新打开申诉",0);}
  private JSONObject appealCall(String method,String path,JSONObject body){
    String owner=appealOwner();
    var result=call(method,path,body);
    requireAppealOwner(owner);
    return result;
  }
  private static MyReport myReport(JSONObject j) {
    return new MyReport(text(j,"id"),text(j,"targetType"),text(j,"targetId"),text(j,"postId"),
        text(j,"reason"),text(j,"excerpt"),text(j,"status"),time(j,"createdAt"),text(j,"decisionId"),
        text(j,"outcome"),text(j,"publicReason"),time(j,"decidedAt"));
  }
  private static ModerationDecision moderationDecision(JSONObject j) {
    return new ModerationDecision(text(j,"decisionId"),text(j,"reportId"),text(j,"side"),text(j,"targetType"),
        text(j,"outcome"),text(j,"publicReason"),time(j,"deliveredAt"),time(j,"appealDeadline"),
        text(j,"appealId"),text(j,"appealStatus"),text(j,"appealOutcome"));
  }

  @Override public HistorySettings historySettings() {
    if(historySettings==null) historySettings=historySettings(call("GET","users/me/browsing-history/settings",null));
    return historySettings;
  }
  @Override public HistorySettings refreshHistorySettings() {
    historySettings=null;
    return historySettings();
  }
  @Override public HistorySettings updateHistorySettings(boolean enabled,long expectedRevision) {
    historySettings=historySettings(call("PUT","users/me/browsing-history/settings",
        json("enabled",enabled,"expectedRevision",expectedRevision)));
    return historySettings;
  }
  @Override public HistoryPage history(String kind,int page) {
    JSONObject data=call("GET","users/me/browsing-history?kind="+encode(kind)+"&page="+page+"&size=50",null);
    JSONArray rows=data.optJSONArray("items");
    if(rows==null)throw new RepositoryException(0,"INVALID_RESPONSE","浏览历史响应异常，请重试",0);
    List<HistoryItem> items=new ArrayList<>();
    for(int i=0;i<rows.length();i++)items.add(historyItem(rows.optJSONObject(i)));
    return new HistoryPage(items,data.optLong("total"),data.optInt("page",page),data.optInt("size",50));
  }
  @Override public void recordHistory(String kind,String targetId) {
    try {
      HistorySettings settings=historySettings();
      if(!settings.enabled())return;
      long epoch="dish".equals(kind)?settings.dishEpoch():settings.postEpoch();
      String eventId=UUID.randomUUID().toString();
      try {
        call("PUT","users/me/browsing-history/"+encode(kind)+"/"+encode(targetId),
            json("epoch",epoch,"visitEventId",eventId));
      } catch(RepositoryException stale) {
        if(!"HISTORY_EPOCH_STALE".equals(stale.code()))return;
        historySettings=null;
        settings=historySettings();
        if(!settings.enabled())return;
        call("PUT","users/me/browsing-history/"+encode(kind)+"/"+encode(targetId),
            json("epoch","dish".equals(kind)?settings.dishEpoch():settings.postEpoch(),"visitEventId",eventId));
      }
    } catch(RuntimeException ignored) {
      // History is best-effort and must never turn a detail view into an error.
    }
  }
  @Override public void deleteHistory(String kind,String targetId) {
    call("DELETE","users/me/browsing-history/"+encode(kind)+"/"+encode(targetId),null);
    historySettings=null;
  }
  @Override public void clearHistory(String kind) {
    call("DELETE","users/me/browsing-history?kind="+encode(kind),null);
    historySettings=null;
  }
  private static HistorySettings historySettings(JSONObject j) {
    return new HistorySettings(j.optBoolean("enabled",true),j.optLong("revision"),
        j.optLong("dishEpoch"),j.optLong("postEpoch"));
  }
  private static HistoryItem historyItem(JSONObject j) {
    return new HistoryItem(text(j,"kind"),text(j,"targetId"),text(j,"diningHallId"),text(j,"stallId"),
        time(j,"visitedAt"),j.optBoolean("available"),text(j,"title"),text(j,"subtitle"),text(j,"imageUrl"));
  }

  @Override public CatalogCorrectionAdminPage adminCorrections(String status,int page) {
    StringBuilder path=new StringBuilder("admin/catalog-corrections?page=").append(page).append("&size=20");
    if(status!=null&&!status.isBlank())path.append("&status=").append(encode(status));
    JSONObject data=call("GET",path.toString(),null);
    JSONArray rows=data.optJSONArray("items");
    if(rows==null)throw new RepositoryException(0,"INVALID_RESPONSE","纠错审核列表响应异常，请重试",0);
    List<CatalogCorrectionAdmin> items=new ArrayList<>();
    for(int i=0;i<rows.length();i++)items.add(adminCorrection(rows.optJSONObject(i)));
    return new CatalogCorrectionAdminPage(items,data.optLong("total"),data.optInt("page",page),data.optInt("size",20));
  }
  @Override public CatalogCorrectionAdmin decideCorrection(String id,String decision,String note,long expectedRevision) {
    return adminCorrection(call("POST","admin/catalog-corrections/"+encode(id)+"/decision",
        json("decision",decision,"reviewNote",note,"expectedRevision",expectedRevision)));
  }
  private CatalogCorrectionAdmin adminCorrection(JSONObject j) {
    if(j==null||j.optJSONObject("correction")==null)throw new RepositoryException(0,"INVALID_RESPONSE","纠错审核响应异常，请重试",0);
    return new CatalogCorrectionAdmin(correction(j.optJSONObject("correction")),text(j,"submitterId"),
        text(j,"submitterName"),text(j,"submitterEmail"));
  }
  private static JSONArray array(List<String> values) {
    JSONArray array=new JSONArray();for(String value:values)array.put(value);return array;
  }
  private CatalogCorrection correction(JSONObject j) {
    if(j==null||text(j,"id").isBlank())throw new RepositoryException(0,"INVALID_RESPONSE","纠错响应异常，请重试",0);
    return new CatalogCorrection(text(j,"id"),text(j,"targetType"),text(j,"targetId"),text(j,"targetSummary"),
        j.optLong("baseRevision"),text(j,"originalName"),text(j,"originalDescription"),decimal(j,"originalPrice"),
        text(j,"proposedName"),text(j,"proposedDescription"),decimal(j,"proposedPrice"),
        strings(j,"changedFields"),text(j,"reason"),text(j,"status"),text(j,"reviewNote"),
        time(j,"createdAt"),time(j,"reviewedAt"),j.optLong("revision"),strings(j,"evidenceUrls"),text(j,"originalPriceUnit"),text(j,"proposedPriceUnit"));
  }
  private static List<String> strings(JSONObject j,String key) {
    JSONArray rows=j==null?null:j.optJSONArray(key);
    if(rows==null)return List.of();
    List<String> values=new ArrayList<>();
    for(int i=0;i<rows.length();i++){String value=rows.optString(i,"");if(!value.isBlank())values.add(value);}
    return List.copyOf(values);
  }

  @Override public boolean dishFavorite(String dishId) {
    return call("GET", "users/me/dish-favorites/" + encode(dishId), null).optBoolean("saved");
  }

  @Override public boolean setDishFavorite(String dishId, boolean saved) {
    return call(saved ? "PUT" : "DELETE", "users/me/dish-favorites/" + encode(dishId), null).optBoolean("saved");
  }

  private DishSearchPage dishRows(String path, int page) {
    JSONObject data = call("GET", path, null);
    List<DishSearchRow> items = new ArrayList<>();
    JSONArray rows = data.optJSONArray("items");
    int size = data.optInt("size");
    long total = data.optLong("total", -1);
    if (rows == null || size < 1 || total < 0 || data.optInt("page") != page) {
      throw new IllegalStateException("菜品列表返回格式异常，请重试");
    }
    for (int i = 0; i < rows.length(); i++) {
      JSONObject row = rows.optJSONObject(i);
      String id = text(row, "id");
      if (id.isBlank()) throw new IllegalStateException("菜品列表返回格式异常，请重试");
      items.add(new DishSearchRow(id, text(row, "name"), decimal(row, "price"),
          text(row, "stallName"), text(row, "diningHallName"),
          decimal(row, "averageScore"), row.optLong("reviewCount", 0)));
    }
    return new DishSearchPage(items, total, page, size);
  }

  private void fetchDetail(String id) {
    fetchDishTarget(id);
    dishReviewPages.put(id,fetchDishReviewPage(id,1));
    fetchDishImages(id);
    JSONObject rating=call("GET","dishes/"+id+"/rating",null);
    ratings.put(id,new RatingSummary(decimal(rating,"averageScore"),rating.optLong("reviewCount")));
  }
  private void fetchDishTarget(String id) {
    JSONObject j=call("GET","dishes/"+id,null);
    String stall=text(j,"stallId");
    String hall=text(j,"diningHallId");
    if(!halls.containsKey(hall)) fetchHalls();
    if(!stalls.containsKey(stall)) fetchStalls(hall);
    dishes.put(id,new Dish(id,stall,text(j,"name"),decimal(j,"price"),text(j,"description"),true,j.optLong("revision",0),text(j,"priceUnit")));
    contexts.put(id,joined(j,true));
  }
  private void fetchDishImages(String id) {
    photos.put(id,page("dishes/"+id+"/images",p -> new Photo(text(p,"id"),id,text(p,"imageUrl"),"")));
  }
  private List<Review> reviewPage(String path) {
    return page(path,this::review);
  }
  private Review review(JSONObject j) {
    contexts.put(text(j,"dishId"),joined(j,true));
    return new Review(text(j,"id"),text(j,"dishId"),text(j,"userId"),text(j,"userDisplayName"),j.optInt("rating"),text(j,"content"),time(j,"createdAt"),j.optLong("likeCount"),j.optLong("replyCount"),j.optBoolean("likedByMe"),j.optLong("counterVersion"),assessment(j),text(j,"userEmail"));
  }
  private ReviewPage fetchDishReviewPage(String dishId,int number) {
    JSONObject data=call("GET","dishes/"+dishId+"/reviews?page="+number+"&size=20",null);
    JSONArray rows=data.optJSONArray("items");
    if(rows==null) throw new RepositoryException(502,"INVALID_RESPONSE","评论列表响应异常",0);
    List<Review> items=new ArrayList<>();
    for(int i=0;i<rows.length();i++) {
      JSONObject row=rows.optJSONObject(i);
      if(row==null) throw new RepositoryException(502,"INVALID_RESPONSE","评论列表响应异常",0);
      items.add(review(row));
    }
    long total=data.optLong("total");
    return new ReviewPage(items,total,number,!items.isEmpty()&&number*20L<total);
  }
  @Override public ReviewPage dishReviews(String id) { return dishReviewPages.getOrDefault(id,ReviewPage.empty()); }
  @Override public void loadDishReviews(String id) {
    ReviewPage first=fetchDishReviewPage(id,1);
    JSONObject rating=call("GET","dishes/"+id+"/rating",null);
    dishReviewPages.put(id,first);
    ratings.put(id,new RatingSummary(decimal(rating,"averageScore"),rating.optLong("reviewCount")));
  }
  @Override public void loadMoreDishReviews(String id) {
    ReviewPage old=dishReviews(id);if(!old.hasMore()) return;
    ReviewPage next=fetchDishReviewPage(id,old.page()+1);
    Map<String,Review> merged=new LinkedHashMap<>();
    for(Review row:old.items()) merged.put(row.id(),row);
    for(Review row:next.items()) {
      Review previous=merged.get(row.id());
      if(previous==null||previous.counterVersion()<=row.counterVersion()) merged.put(row.id(),row);
    }
    dishReviewPages.put(id,new ReviewPage(List.copyOf(merged.values()),next.total(),next.page(),next.hasMore()));
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
    LikeState state=new LikeState(result.optBoolean("likedByMe"),result.optLong("likeCount"),result.optLong("counterVersion"));
    dishReviewPages.replaceAll((id,page) -> new ReviewPage(page.items().stream().map(row -> {
      if(!row.id().equals(reviewId)||row.counterVersion()>state.counterVersion()) return row;
      return new Review(row.id(),row.dishId(),row.userId(),row.userDisplayName(),row.rating(),row.content(),row.createdAt(),state.likeCount(),row.replyCount(),state.likedByMe(),state.counterVersion(),row.ai(),row.userEmail());
    }).collect(java.util.stream.Collectors.toList()),page.total(),page.page(),page.hasMore()));
    return state;
  }
  @Override public ReplyPage reviewReplies(String reviewId) { return replyPages.getOrDefault(reviewId,ReplyPage.empty()); }
  @Override public boolean hasLoadedReviewReplies(String reviewId) { return replyPages.containsKey(reviewId); }
  @Override public void loadReviewReplies(String reviewId) { replyPages.put(reviewId,fetchReplyPage(reviewId,"")); }
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
        text(j,admin?"reason":"reviewReason"),time(j,type.equals("review")&&!admin?"submittedAt":"createdAt"),context,admin?assessment(j):AiAssessment.empty(),admin?text(j,"userEmail"):"");
  }
  private AiAssessment assessment(JSONObject j) {
    JSONArray raw=j.optJSONArray("aiLabels");List<String> labels=new ArrayList<>();
    if(raw!=null) for(int i=0;i<raw.length();i++) labels.add(raw.optString(i));
    return new AiAssessment(text(j,"aiRiskLevel"),decimal(j,"aiConfidence"),labels,text(j,"aiReason"),time(j,"aiCheckedAt"));
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
    AdminEntity e=new AdminEntity(text(j,"id"),text(j,"name"),text(j,"description"),price,text(j,"coverImageUrl"),text(j,"status").equals("active"),text(j,"priceUnit"));
    String key=type+":"+e.id(); entities.put(key,e); parents.put(key,text(j,"parentId")); return e;
  }

  private CommunityPost cacheCommunityPost(JSONObject j) {
    CommunityPost post = parseCommunityPost(j);
    communityPostCache.put(post.id(),post);
    return post;
  }

  private CommunityPost parseCommunityPost(JSONObject j) {
    List<String> images=new ArrayList<>();
    JSONArray imageValues=j.optJSONArray("imageUrls");
    if(imageValues!=null) for(int i=0;i<imageValues.length();i++) {
      String image=imageValues.optString(i,"");if(!image.isBlank()) images.add(image);
    }
    List<CommunityImage> details=new ArrayList<>();JSONArray detailValues=j.optJSONArray("images");
    if(detailValues!=null)for(int i=0;i<detailValues.length();i++){
      JSONObject item=detailValues.optJSONObject(i);if(item!=null)details.add(new CommunityImage(text(item,"url"),item.optInt("width"),item.optInt("height")));
    }
    JSONObject repost=j.optJSONObject("repost");
    JSONObject linked=j.optJSONObject("linkedDish");
    LinkedDish dish = text(linked,"id").isBlank() ? null : new LinkedDish(text(linked,"id"),text(linked,"name"));
    return new CommunityPost(text(j,"id"),text(j,"userId"),text(j,"userDisplayName"),
        text(j,"content"),images,j.optLong("likeCount"),j.optLong("commentCount"),j.optInt("repostCount"),
        text(j,"repostOfId"),text(repost,"userDisplayName"),text(repost,"content"),
        j.optBoolean("likedByMe"),j.optBoolean("favoritedByMe"),text(j,"status"),
        text(j,"reviewReason"),time(j,"createdAt"),text(j,"title"),text(j,"userAvatarUrl"),details,j.optLong("favoriteCount"),text(j,"reviewStage"),dish,Math.max(0,j.optLong("viewCount")));
  }

  private CommunityComment communityComment(JSONObject j) {
    return new CommunityComment(text(j,"id"),text(j,"postId"),text(j,"userId"),
        text(j,"userDisplayName"),text(j,"content"),j.optLong("likeCount"),
        text(j,"status"),time(j,"createdAt"),text(j,"rootCommentId"),text(j,"replyToCommentId"),text(j,"replyToUserId"),
        text(j,"replyToDisplayName"),text(j,"userAvatarUrl"),j.optLong("replyCount"),j.optBoolean("likedByMe"),j.optBoolean("deleted"),text(j,"reviewStage"),text(j,"reviewReason"));
  }

  private void updateCommunityPostState(String id,Boolean liked,Long likeCount,Boolean favorited,Long favoriteCount) {
    CommunityPost current=communityPostCache.get(id);if(current==null) return;
    CommunityPost updated=new CommunityPost(current.id(),current.userId(),current.userDisplayName(),
        current.content(),current.imageUrls(),likeCount==null?current.likeCount():likeCount,
        current.commentCount(),current.repostCount(),current.repostOfId(),current.repostAuthor(),
        current.repostContent(),liked==null?current.likedByMe():liked,
        favorited==null?current.favoritedByMe():favorited,current.status(),current.reviewReason(),current.createdAt(),current.title(),current.userAvatarUrl(),current.images(),favoriteCount==null?current.favoriteCount():favoriteCount,current.reviewStage(),current.linkedDish(),current.viewCount());
    communityPostCache.put(id,updated);
    List<CommunityPost> feed=new ArrayList<>(communityFeed);
    for(int i=0;i<feed.size();i++) if(feed.get(i).id().equals(id)) feed.set(i,updated);
    communityFeed=List.copyOf(feed);
  }

  private void syncPrototypeFeed() {
    communityFeed=communityPrototype.posts();
    communityPostCache.clear();
    for(CommunityPost post:communityFeed) communityPostCache.put(post.id(),post);
  }

  @Override public List<Hall> halls() { return hallList; }
  @Override public Hall hall(String id) { return halls.get(id); }
  @Override public Stall stall(String id) { return stalls.get(id); }
  @Override public Dish dish(String id) { return dishes.get(id); }
  @Override public List<Stall> stalls(String id) { return stallLists.getOrDefault(id,List.of()); }
  @Override public List<Dish> dishes(String id) { return dishLists.getOrDefault(id,List.of()); }
  @Override public List<Review> reviews(String id) { return id==null||id.equals("admin")?reviews.getOrDefault("admin",List.of()):dishReviews(id).items(); }
  @Override public List<Photo> photos(String id) { return photos.getOrDefault(id,List.of()); }
  @Override public List<CommunityPost> communityPosts() { return communityFeed; }
  @Override public cn.edu.bjtu.foodtime.domain.model.CommunityPins.Snapshot communityPins(){synchronized(dashboardLock){return pinOwner.equals(capabilityOwner(user))?pinSnapshot:cn.edu.bjtu.foodtime.domain.model.CommunityPins.Snapshot.empty();}}
  @Override public cn.edu.bjtu.foodtime.domain.model.CommunityPins.Snapshot managedPins(){return CommunityPinJson.parse(accountCall("GET","admin/community/pins",null));}
  @Override public cn.edu.bjtu.foodtime.domain.model.CommunityPins.Snapshot pinPost(String id,long revision){return CommunityPinJson.parse(accountCall("PUT","admin/community/pins/"+encode(id),json("expectedRevision",revision)));}
  @Override public cn.edu.bjtu.foodtime.domain.model.CommunityPins.Snapshot unpinPost(String id,long revision){return CommunityPinJson.parse(accountCall("DELETE","admin/community/pins/"+encode(id)+"?expectedRevision="+revision,null));}
  @Override public cn.edu.bjtu.foodtime.domain.model.CommunityPins.Snapshot reorderPins(List<String> ids,long revision){return CommunityPinJson.parse(accountCall("PUT","admin/community/pins/order",json("postIds",array(ids),"expectedRevision",revision)));}
  @Override public boolean communityPrototypeEnabled() { return communityPrototype!=null; }
  @Override public CommunityPost communityPost(String id) { return communityPostCache.get(id); }
  @Override public List<CommunityComment> communityComments(String id) { return communityCommentCache.getOrDefault(id,List.of()); }
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
  private void loadCommunityFeed(boolean more) {
    String owner=capabilityOwner(user);boolean append=more;JSONObject data;
    try {data=accountCall("GET","community/feed/cursor?size=20"+(append?"&cursor="+encode(communityCursor)+"&pinRevision="+pinSnapshot.revision():""),null);}
    catch(RepositoryException changed){if(!append||!changed.code().equals("PIN_SET_CHANGED"))throw changed;append=false;data=accountCall("GET","community/feed/cursor?size=20",null);}
    var pins=data.optJSONObject("pins")==null?cn.edu.bjtu.foodtime.domain.model.CommunityPins.Snapshot.empty():CommunityPinJson.parse(data.optJSONObject("pins"));
    synchronized(dashboardLock){
      if(owner.isEmpty()||!owner.equals(capabilityOwner(user)))throw new RepositoryException(409,"ACCOUNT_CHANGED","账号已变化，请刷新",0);
      List<CommunityPost> result=new ArrayList<>(append?communityFeed:List.of());JSONArray values=data.optJSONArray("items");
      if(values!=null)for(int i=0;i<values.length();i++)result.add(cacheCommunityPost(values.optJSONObject(i)));
      communityFeed=List.copyOf(result);communityCursor=text(data,"nextCursor");communityHasMore=data.optBoolean("hasMore");pinSnapshot=pins;pinOwner=owner;
    }
  }
  @Override public void loadMoreCommunityPosts(){if(communityHasMore)loadCommunityFeed(true);}
  @Override public CommunityFeedSlice communityFollowing() { return followingFeed; }
  @Override public void loadMoreCommunityFollowing() { loadCommunityFollowing(true); }

  private void loadCommunityFollowing(boolean append) {
    CommunityFeedSlice prior = followingFeed;
    if (append && prior.nextCursor().isEmpty()) return;
    CommunityFeedSlice page = discoverPosts("", true, "", append ? prior.nextCursor() : "");
    List<CommunityPost> items = new ArrayList<>(append ? prior.items() : List.of());
    items.addAll(page.items());
    followingFeed = new CommunityFeedSlice(items, page.total(), page.nextCursor());
  }
  @Override public CommunityAuthor communityAuthor(String userId) {
    JSONObject profile = call("GET", "community/authors/" + encode(userId), null);
    return new CommunityAuthor(text(profile, "id"), text(profile, "displayName"), text(profile, "avatarUrl"));
  }

  @Override public CommunityFeedSlice discoverPosts(String authorId, boolean following, String query, String cursor) {
    String path = authorId.isEmpty() ? "community/feed/cursor" : "community/authors/" + encode(authorId) + "/posts";
    path += "?size=20";
    if (following) path += "&following=true";
    if (!query.isBlank()) path += "&q=" + encode(query);
    if (!cursor.isBlank()) path += "&cursor=" + encode(cursor);
    JSONObject data = call("GET", path, null);
    JSONArray items = data.optJSONArray("items");
    if (items == null) throw new IllegalStateException("食记列表返回格式异常");
    List<CommunityPost> posts = new ArrayList<>();
    for (int i = 0; i < items.length(); i++) posts.add(parseCommunityPost(items.optJSONObject(i)));
    return new CommunityFeedSlice(posts, data.optLong("total"), text(data, "nextCursor"));
  }
  @Override public boolean hasMoreCommunityPosts(){return communityHasMore;}
  @Override public void submitCommunityPost(String content,List<String> images) {
    submitCommunityPost(CommunityText.headline(content),content,images,UUID.randomUUID().toString());
  }
  @Override public void submitCommunityPost(String title,String content,List<String> images,String requestId) {
    submitCommunityPost(title,content,images,requestId,"");
  }
  @Override public void submitCommunityPost(String title,String content,List<String> images,String requestId,String dishId) {
    String heading=Validation.text(title,50,"标题"),body=Validation.text(content,2000,"动态正文");
    List<String> selected=images==null?List.of():images;
    if(selected.isEmpty()||selected.size()>9)throw new IllegalArgumentException("请添加1到9张图片");
    if(communityPrototype!=null){communityPrototype.publish(user,body,selected);syncPrototypeFeed();return;}
    JSONArray uploaded=new JSONArray();
    for(String image:selected)uploaded.put(communityUploads.computeIfAbsent(image,this::upload));
    call("POST","community/posts",json("title",heading,"content",body,"imageUrls",uploaded,
        "clientRequestId",requestId,"dishId",dishId.isBlank()?null:dishId));
  }
  private CommunityCommentPage commentPage(String path,CommunityCommentPage prior){
    JSONObject data=call("GET",path,null);JSONArray values=data.optJSONArray("items");
    List<CommunityComment> items=new ArrayList<>(prior==null?List.of():prior.items());
    if(values!=null)for(int i=0;i<values.length();i++)items.add(communityComment(values.optJSONObject(i)));
    return new CommunityCommentPage(items,data.optLong("total"),data.optBoolean("hasMore"),text(data,"nextCursor"));
  }
  private void loadCommunityRoots(String postId,boolean more){
    CommunityCommentPage prior=communityRoots(postId);
    CommunityCommentPage result=commentPage("community/posts/"+postId+"/comments/cursor?size=20"+(more?"&cursor="+prior.nextCursor():""),more?prior:null);
    communityRoots.put(postId,result);communityCommentCache.put(postId,result.items());
    int first=more?prior.items().size():0;
    for(int i=first;i<result.items().size();i++){var root=result.items().get(i);if(root.status().equals("approved"))loadCommunityReplies(postId,root.id(),false);}
  }
  @Override public CommunityCommentPage communityRoots(String postId){return communityRoots.getOrDefault(postId,CommunityCommentPage.empty());}
  @Override public CommunityCommentPage communityReplies(String rootId){return communityReplies.getOrDefault(rootId,CommunityCommentPage.empty());}
  @Override public void loadMoreCommunityComments(String postId){if(communityRoots(postId).hasMore())loadCommunityRoots(postId,true);}
  @Override public void loadCommunityReplies(String postId,String rootId,boolean more){
    var prior=communityReplies(rootId);if(more&&!prior.hasMore())return;
    communityReplies.put(rootId,commentPage("community/posts/"+postId+"/comments/"+rootId+"/replies?size="+(more?20:3)+(more?"&cursor="+prior.nextCursor():""),more?prior:null));
  }
  @Override public boolean ensureCommunityCommentVisible(String postId,String rootId,String commentId){
    if(rootId==null||rootId.isBlank())return false;
    if(communityRoots(postId).items().isEmpty())loadCommunityRoots(postId,false);
    int guard=0;
    while(!containsComment(communityRoots(postId).items(),rootId)&&communityRoots(postId).hasMore()&&guard++<10)
      loadCommunityRoots(postId,true);
    if(!containsComment(communityRoots(postId).items(),rootId))return false;
    if(rootId.equals(commentId))return true;
    if(communityReplies(rootId).items().isEmpty())loadCommunityReplies(postId,rootId,false);
    guard=0;
    while(!containsComment(communityReplies(rootId).items(),commentId)&&communityReplies(rootId).hasMore()&&guard++<20)
      loadCommunityReplies(postId,rootId,true);
    return containsComment(communityReplies(rootId).items(),commentId);
  }
  private static boolean containsComment(List<CommunityComment> items,String id){
    for(CommunityComment item:items)if(item.id().equals(id))return true;
    return false;
  }
  @Override public String submitCommunityComment(String postId,String content){return submitCommunityComment(postId,content,"","",UUID.randomUUID().toString());}
  @Override public String submitCommunityComment(String postId,String content,String rootId,String targetId,String requestId){
    String body=Validation.text(content,500,"评论");
    JSONObject created=call("POST","community/posts/"+postId+"/comments",json("content",body,"clientRequestId",requestId,
        "rootCommentId",rootId.isBlank()?null:rootId,"replyToCommentId",targetId.isBlank()?null:targetId));
    // 立即刷新本地缓存：服务端会返回本人尚未通过的评论，无需关闭帖子。
    if(rootId.isBlank()) loadCommunityRoots(postId,false); else loadCommunityReplies(postId,rootId,false);
    return text(created,"id");
  }
  @Override public AppRelease appRelease(){
    JSONObject data=call("GET","system/app-release",null);
    int code=data.optInt("versionCode");
    if(code<=0) return null;
    String url=text(data,"apkUrl");
    if(url.startsWith("/")) url=origin.resolve(url).toString();
    return new AppRelease(code,text(data,"versionName"),url,text(data,"notes"),data.optInt("minVersionCode"));
  }
  @Override public void sendSchoolEmailCode(String email){
    String value=Validation.schoolEmail(email);
    call("POST","auth/bind-email/email-code",json("email",value));
  }
  @Override public void bindSchoolEmail(String email,String code){
    String value=Validation.schoolEmail(email);
    String verification=code.trim();
    if(!verification.matches("[0-9]{6}")) throw new IllegalArgumentException("验证码须为6位数字");
    call("POST","users/me/bind-email",json("email",value,"verificationCode",verification));
    profile();
  }
  @Override public String communityCommentStatus(String commentId){
    try { return text(call("GET","community/comments/"+commentId+"/status",null),"status"); }
    catch(RepositoryException missing){ return null; }
  }
  @Override public void reloadCommunityComments(String postId){
    if(communityPrototype==null)loadCommunityRoots(postId,false);
  }

  private CommunityPost loadCommunityPost(String id) {
    try {
      if(communityPrototype!=null) {
        CommunityPost post=communityPrototype.post(id);
        if(post==null)throw missing("动态不存在或暂不可见");
        communityPostCache.put(id,post);
        communityCommentCache.put(id,communityPrototype.comments(id));
        return post;
      }
      CommunityPost post=cacheCommunityPost(call("GET","community/posts/"+id,null));
      if(!post.status().equals("approved")) {
        communityRoots.remove(id);
        communityCommentCache.remove(id);
      }
      return post;
    } catch(RepositoryException failure) {
      if(failure.status()==403||failure.status()==404) {
        communityPostCache.remove(id);
        communityRoots.remove(id);
        communityCommentCache.remove(id);
      }
      throw failure;
    }
  }

  @Override public void loadCommunityAuthorFollow(String userId) {
    User viewer=user;
    if(communityPrototype==null&&viewer!=null&&!userId.isBlank()&&!userId.equals(viewer.id()))
      followStates.put(userId,fetchFollowState(userId));
  }
  @Override public void reloadCommunityReplies(String postId,String rootId){ loadCommunityReplies(postId,rootId,false); }
  @Override public void deleteCommunityComment(String id){call("DELETE","community/comments/"+id,null);}
  @Override public CommunityAuditPage communityAudit(){return communityAudit;}
  @Override public CommunityAuditDetail communityAuditDetail(){return communityAuditDetail;}
  @Override public void moderateCommunity(String type,String id,String action,String reason){call("POST","admin/community/"+type+"/"+id,json("action",action,"reason",reason));}
  @Override public void removeCommunityAsAdmin(String type,String id,String reason){call("DELETE","admin/community/"+type+"/"+id,reason==null||reason.isBlank()?null:json("reason",reason));}
  private void loadCommunityCollection(boolean favorites,boolean more){
    CommunityPostPage prior=communityCollection(favorites);int number=more?prior.page()+1:1;
    JSONObject data=call("GET","users/me/community-"+(favorites?"favorites":"posts")+"?page="+number+"&size=20",null);
    List<CommunityPost> items=new ArrayList<>(more?prior.items():List.of());JSONArray rows=data.optJSONArray("items");
    if(rows!=null)for(int i=0;i<rows.length();i++)items.add(cacheCommunityPost(rows.optJSONObject(i)));
    CommunityPostPage page=new CommunityPostPage(items,data.optLong("total"),number,number*20L<data.optLong("total"));
    if(favorites)ownCommunityFavorites=page;else ownCommunityPosts=page;
  }
  @Override public CommunityPostPage communityCollection(boolean favorites){return favorites?ownCommunityFavorites:ownCommunityPosts;}
  @Override public void loadMoreCommunityCollection(boolean favorites){if(communityCollection(favorites).hasMore())loadCommunityCollection(favorites,true);}
  @Override public void deleteCommunityPost(String id){call("DELETE","community/posts/"+id,null);}
  @Override public CommunityAction toggleCommunityLike(String type,String id){
    CommunityPost post=communityPostCache.get(id);if(!type.equals("post")||post==null)throw missing("请刷新后操作");
    return setCommunityLike(type,id,!post.likedByMe());
  }
  @Override public CommunityAction toggleCommunityFavorite(String id){CommunityPost post=communityPostCache.get(id);if(post==null)throw missing("请刷新后操作");return setCommunityFavorite(id,!post.favoritedByMe());}
  @Override public CommunityAction setCommunityLike(String type,String id,boolean active){
    if(!List.of("post","comment").contains(type))throw new IllegalArgumentException("点赞目标不合法");
    JSONObject result=call(active?"PUT":"DELETE","community/"+(type.equals("post")?"posts/":"comments/")+id+"/like",null);
    CommunityAction state=new CommunityAction(result.optBoolean("liked"),result.optLong("likeCount"));
    if(type.equals("post"))updateCommunityPostState(id,state.active(),state.count(),null,null);
    else{
      for(var pages:List.of(communityRoots,communityReplies))pages.replaceAll((key,page)->new CommunityCommentPage(page.items().stream().map(item->item.id().equals(id)?item.withLike(state.active(),state.count()):item).collect(java.util.stream.Collectors.toList()),page.total(),page.hasMore(),page.nextCursor()));
    }
    return state;
  }
  @Override public CommunityAction setCommunityFavorite(String id,boolean active){
    JSONObject result=call(active?"PUT":"DELETE","community/posts/"+id+"/favorite",null);
    CommunityAction state=new CommunityAction(result.optBoolean("favorited"),result.optLong("favoriteCount"));
    updateCommunityPostState(id,null,null,state.active(),state.count());return state;
  }
  public NoticeFeed getNoticeFeed(long sinceSeq,int limit) {
    JSONObject data=call("GET","users/me/notices/feed?since_seq="+sinceSeq+"&limit="+limit,null);
    JSONArray rows=data.optJSONArray("items");
    if(rows==null) throw new RepositoryException(502,"INVALID_RESPONSE","公告列表响应异常",0);
    List<Notice> items=new ArrayList<>();
    for(int i=0;i<rows.length();i++) items.add(notice(rows.optJSONObject(i)));
    return new NoticeFeed(items,data.optLong("unreadCount"));
  }
  public NoticePage getNotices(int page,int size) {
    JSONObject data=call("GET","users/me/notices?page="+page+"&size="+size,null);
    JSONArray rows=data.optJSONArray("items");
    if(rows==null) throw new RepositoryException(502,"INVALID_RESPONSE","公告列表响应异常",0);
    List<Notice> items=new ArrayList<>();
    for(int i=0;i<rows.length();i++) items.add(notice(rows.optJSONObject(i)));
    long total=data.optLong("total");
    return new NoticePage(items,total,page*(long)size<total);
  }
  @Override public NoticePage notices() { return noticePage; }
  @Override public void loadMoreNotices() { if(noticePage.hasMore()) loadNoticePage(noticeNumber+1); }
  private void loadNoticePage(int number) {
    NoticePage page=getNotices(number,20);
    if(number<=1) noticePage=page;
    else {
      List<Notice> merged=new ArrayList<>(noticePage.items());
      Set<String> seen=new HashSet<>();
      for(Notice item:merged) seen.add(item.id());
      for(Notice item:page.items()) if(seen.add(item.id())) merged.add(item);
      noticePage=new NoticePage(merged,page.total(),page.hasMore());
    }
    noticeNumber=number;
  }
  @Override public void markNoticeRead(String id) {
    call("POST","users/me/notices/"+id+"/read",null);
    noticeUnread=call("GET","users/me/notices/unread-count",null).optLong("unreadCount");
  }
  @Override public Notice currentNotice() {
    JSONObject data=call("GET","users/me/notices/current",null);
    if(data.length()==0||data.optBoolean("dontShowAgain"))return null;
    if(text(data,"id").isEmpty()||!data.has("content"))throw new RepositoryException(502,"INVALID_RESPONSE","公告响应异常",0);
    return notice(data);
  }
  @Override public void confirmNotice(String id,boolean dontShowAgain) {
    call("POST","users/me/notices/"+id+"/confirm",json("dontShowAgain",dontShowAgain));
    // Counts are refreshed independently; a failed badge read must not fail a saved confirmation.
    try {noticeUnread=call("GET","users/me/notices/unread-count",null).optLong("unreadCount");}catch(RepositoryException ignored){}
  }
  @Override public MessagePage messages() { return messagePage; }
  @Override public void loadMoreMessages() { if(messagePage.hasMore()) loadMessagePage(messageNumber+1); }
  private void loadMessagePage(int number) {
    MessagePage page=getMessages(number,20);
    if(number<=1) messagePage=page;
    else {
      List<Message> merged=new ArrayList<>(messagePage.items());
      Set<String> seen=new HashSet<>();
      for(Message item:merged) seen.add(item.id());
      for(Message item:page.items()) if(seen.add(item.id())) merged.add(item);
      messagePage=new MessagePage(merged,page.total(),page.hasMore());
    }
    messageNumber=number;
  }
  private void syncFollowOwner() {
    String owner=user!=null&&user.active()?user.id()+":"+user.role():"";
    if(!owner.equals(followOwner)) {resetFollows();followOwner=owner;}
  }
  private void resetFollows() {
    userSearchPage=UserSearchPage.empty();userSearchRequest++;
    followOwner="";followEpoch++;followingRequest++;followersRequest++;followingNeedsRebase=false;
    followingPage=FollowPage.empty();followersPage=FollowPage.empty();followStates.clear();
  }
  @Override public FollowPage following() { synchronized(followLock){syncFollowOwner();return followingPage;} }
  @Override public FollowPage followers() { synchronized(followLock){syncFollowOwner();return followersPage;} }
  @Override public FollowState followState(String userId) { synchronized(followLock){syncFollowOwner();return userId==null?null:followStates.get(userId);} }
  @Override public UserSearchPage userSearchResults() {
    synchronized(followLock){syncFollowOwner();return userSearchPage;}
  }
  @Override public void loadUserSearch(String query,int page) {
    String keyword=query==null?"":query.strip();
    if(keyword.length()>50||page<1)throw new RepositoryException(400,"VALIDATION_ERROR","用户名最多50字，页码必须大于0",0);
    final String owner;final long epoch,ticket;final UserSearchPage before;
    synchronized(followLock) {
      syncFollowOwner();owner=followOwner;epoch=followEpoch;ticket=++userSearchRequest;before=userSearchPage;
      if(owner.isEmpty())throw new RepositoryException(401,"AUTH_REQUIRED","请先登录",0);
      if(keyword.isEmpty()){userSearchPage=UserSearchPage.empty();return;}
      if(page>1&&(!keyword.equals(before.query())||before.page()!=page-1))throw staleFollow();
    }
    FollowPage response=FollowJson.page(call("GET","users/search?q="+encode(keyword)+"&page="+page+"&size=20",null),page);
    var rows=new LinkedHashMap<String,FollowUser>();
    if(page>1)for(var row:before.items())rows.put(row.id(),row);
    for(var row:response.items())rows.put(row.id(),row);
    synchronized(followLock) {
      syncFollowOwner();
      if(Thread.currentThread().isInterrupted()||epoch!=followEpoch||!owner.equals(followOwner)||ticket!=userSearchRequest)throw staleFollow();
      userSearchPage=new UserSearchPage(keyword,new ArrayList<>(rows.values()),response.total(),page,response.hasMore());
    }
  }
  private void ensureFollows(boolean fans) {
    boolean needed;synchronized(followLock){syncFollowOwner();needed=(fans?followersPage:followingPage).page()==0||!fans&&followingNeedsRebase;}
    if(needed)fetchFollows(fans,false,true);
  }
  @Override public void loadFollows(boolean fans) {profile();fetchFollows(fans,false,false);}
  @Override public void loadMoreFollows(boolean fans) {
    profile();FollowPage page=fans?followers():following();
    if(page.page()==0)fetchFollows(fans,false,false);else if(page.hasMore())fetchFollows(fans,true,false);
  }
  private void fetchFollows(boolean fans,boolean append,boolean keepPages) {
    final String owner,id;final long ticket,epoch;final int last,first;final FollowPage before;
    synchronized(followLock) {
      syncFollowOwner();owner=followOwner;epoch=followEpoch;
      if(owner.isEmpty())throw new RepositoryException(401,"AUTH_REQUIRED","请先登录",0);
      id=user.id();before=fans?followersPage:followingPage;
      ticket=fans?++followersRequest:++followingRequest;
      last=append?before.page()+1:keepPages?Math.max(1,before.page()):1;
      first=append&&!(!fans&&followingNeedsRebase)?last:1;
    }
    var rows=new LinkedHashMap<String,FollowUser>();
    if(first>1)for(var row:before.items())rows.put(row.id(),row);
    FollowPage response=null;
    for(int number=first;number<=last;number++) {
      response=FollowJson.page(call("GET","users/"+id+(fans?"/followers":"/following")+"?page="+number+"&size="+FollowJson.SIZE,null),number);
      for(var row:response.items())rows.put(row.id(),row);
      if(!response.hasMore())break;
    }
    synchronized(followLock) {
      syncFollowOwner();
      if(Thread.currentThread().isInterrupted()||epoch!=followEpoch||!owner.equals(followOwner)||ticket!=(fans?followersRequest:followingRequest))throw staleFollow();
      var next=new FollowPage(new ArrayList<>(rows.values()),response.total(),response.page(),response.hasMore());
      if(fans)followersPage=next;else{followingPage=next;followingNeedsRebase=false;}
      for(var row:next.items())followStates.computeIfPresent(row.id(),(key,known)->new FollowState(row.followedByMe(),known.followerCount()));
      updateFollowCount(fans,next.total());
    }
  }
  private RepositoryException staleFollow(){return new RepositoryException(0,"STALE_RESPONSE","请求已失效，请重试",0);}
  private void updateFollowCount(boolean fans,long count) {
    User u=user;if(u!=null)user=new User(u.id(),u.email(),u.displayName(),u.role(),u.active(),u.avatarUrl(),u.createdAt(),u.reviewCount(),fans?u.followingCount():count,fans?count:u.followerCount());
  }
  @Override public FollowState setFollow(String userId,boolean follow) {
    final String owner;final long epoch;final Boolean previous;
    synchronized(followLock){
      syncFollowOwner();owner=followOwner;epoch=followEpoch;
      FollowState known=followStates.get(userId);
      FollowUser row=java.util.stream.Stream.concat(followingPage.items().stream(),followersPage.items().stream()).filter(item->item.id().equals(userId)).findFirst().orElse(null);
      if(row==null)row=userSearchPage.items().stream().filter(item->item.id().equals(userId)).findFirst().orElse(null);
      previous=row!=null?Boolean.valueOf(row.followedByMe()):known==null?null:Boolean.valueOf(known.followed());
      if(owner.isEmpty())throw new RepositoryException(401,"AUTH_REQUIRED","请先登录",0);
    }
    FollowState result=FollowJson.state(call(follow?"PUT":"DELETE","users/"+userId+"/follow",null));
    // The write response only carries the target's fan count. Re-read our own canonical count
    // without reloading either list; a transient read failure must not report a successful write as failed.
    boolean counted=false;
    try{profile();counted=true;}catch(RepositoryException failure){if(failure.status()==401||failure.status()==403)throw failure;}
    synchronized(followLock) {
      syncFollowOwner();if(epoch!=followEpoch||!owner.equals(followOwner))throw staleFollow();
      followingRequest++;followersRequest++;followingNeedsRebase=true;
      followStates.put(userId,result);
      if(!counted&&previous!=null&&previous!=result.followed())updateFollowCount(false,Math.max(0,user.followingCount()+(result.followed()?1:-1)));
      List<FollowUser> rows=new ArrayList<>(followingPage.items());
      if(!result.followed())rows.removeIf(row->row.id().equals(userId));
      else if(rows.stream().noneMatch(row->row.id().equals(userId))&&followingPage.page()>0) {
        followersPage.items().stream().filter(row->row.id().equals(userId)).findFirst().ifPresent(row->rows.add(0,new FollowUser(row.id(),row.displayName(),row.avatarUrl(),true)));
      }
      followingPage=new FollowPage(rows,user.followingCount(),followingPage.page(),followingPage.hasMore()||rows.size()<user.followingCount());
      followersPage=withFollowState(followersPage,userId,result.followed());
      userSearchRequest++;
      userSearchPage=new UserSearchPage(userSearchPage.query(),userSearchPage.items().stream().map(item->
          item.id().equals(userId)?new FollowUser(item.id(),item.displayName(),item.avatarUrl(),result.followed()):item).collect(java.util.stream.Collectors.toList()),
          userSearchPage.total(),userSearchPage.page(),userSearchPage.hasMore());
    }
    return result;
  }
  @Override public long unreadCount() { return noticeUnread+messageUnread+directUnread; }
  @Override public NoticeFeed noticeFeed(long sinceSeq,int limit) {
    NoticeFeed feed=getNoticeFeed(sinceSeq,limit); lastFeed=feed; noticeUnread=feed.unreadCount(); return feed;
  }
  public MessagePage getMessages(int page,int size) {
    JSONObject data=call("GET","users/me/messages?page="+page+"&size="+size,null);
    JSONArray rows=data.optJSONArray("items");
    if(rows==null) throw new RepositoryException(502,"INVALID_RESPONSE","消息列表响应异常",0);
    List<Message> items=new ArrayList<>();
    for(int i=0;i<rows.length();i++) items.add(message(rows.optJSONObject(i)));
    long total=data.optLong("total");
    return new MessagePage(items,total,page*(long)size<total);
  }
  public long getUnreadCount() {
    return call("GET","users/me/messages/unread-count",null).optLong("unreadCount");
  }

  @Override public long directUnreadCount(){return directUnread;}
  @Override public long refreshDirectUnread() {
    directUnread = getDirectUnreadCount();
    return directUnread;
  }
  public long getDirectUnreadCount() {
    return call("GET","users/me/conversations/unread-count",null).optLong("unreadCount");
  }
  @Override public ConversationPage conversations(){return conversationPage;}
  @Override public void loadMoreConversations(){if(conversationPage.hasMore())loadConversations(conversationNumber+1);}
  private void loadConversations(int page){
    JSONObject data=call("GET","users/me/conversations?page="+page+"&size=20",null);
    JSONArray rows=data.optJSONArray("items");
    if(rows==null)throw new RepositoryException(502,"INVALID_RESPONSE","私信列表响应异常",0);
    List<Conversation> items=new ArrayList<>();
    for(int i=0;i<rows.length();i++)items.add(conversation(rows.optJSONObject(i)));
    long total=data.optLong("total");
    boolean hasMore=page*20L<total;
    if(page<=1)conversationPage=new ConversationPage(items,total,hasMore);
    else {
      Map<String,Conversation> merged=new LinkedHashMap<>();
      for(Conversation item:conversationPage.items())merged.put(item.id(),item);
      for(Conversation item:items)merged.put(item.id(),item);
      conversationPage=new ConversationPage(new ArrayList<>(merged.values()),total,hasMore);
    }
    conversationNumber=page;
    for(Conversation item:items)conversationCache.put(item.id(),item);
  }
  @Override public Conversation conversation(String id){return conversationCache.get(id);}
  private void loadConversationDetail(String id){
    conversationCache.put(id,conversation(call("GET","users/me/conversations/"+id,null)));
  }
  @Override public DirectMessagePage conversationMessages(String id){
    return chatCache.getOrDefault(id,DirectMessagePage.empty());
  }
  private void loadConversationMessages(String id,String cursor){
    JSONObject data=call("GET","users/me/conversations/"+id+"/messages?size=30"+(cursor.isBlank()?"":"&cursor="+encode(cursor)),null);
    JSONArray rows=data.optJSONArray("items");
    if(rows==null)throw new RepositoryException(502,"INVALID_RESPONSE","私信内容响应异常",0);
    DirectMessagePage prior=conversationMessages(id);
    Map<String,DirectMessage> merged=new LinkedHashMap<>();
    for(DirectMessage item:prior.items())merged.put(item.id(),item);
    for(int i=0;i<rows.length();i++){
      DirectMessage item=directMessage(rows.optJSONObject(i));
      merged.put(item.id(),item);
    }
    List<DirectMessage> items=new ArrayList<>(merged.values());
    items.sort(Comparator.comparingLong(DirectMessage::createdAt).thenComparing(DirectMessage::id));
    String next=cursor.isBlank()&&!prior.nextCursor().isBlank()?prior.nextCursor():text(data,"nextCursor");
    boolean more=data.optBoolean("hasMore")||(cursor.isBlank()&&prior.hasMore());
    chatCache.put(id,new DirectMessagePage(items,more,next));
  }
  @Override public void loadMoreConversationMessages(String id){
    DirectMessagePage page=conversationMessages(id);
    if(page.hasMore()&&!page.nextCursor().isBlank())loadConversationMessages(id,page.nextCursor());
  }
  @Override public DirectMessage sendDirectMessage(String conversationId,String content,String clientRequestId){
    DirectMessage message=directMessage(call("POST","users/me/conversations/"+conversationId+"/messages",
        json("content",content,"clientRequestId",clientRequestId)));
    DirectMessagePage prior=conversationMessages(conversationId);
    Map<String,DirectMessage> merged=new LinkedHashMap<>();
    for(DirectMessage item:prior.items())merged.put(item.id(),item);
    merged.put(message.id(),message);
    List<DirectMessage> items=new ArrayList<>(merged.values());
    items.sort(Comparator.comparingLong(DirectMessage::createdAt).thenComparing(DirectMessage::id));
    chatCache.put(conversationId,new DirectMessagePage(items,prior.hasMore(),prior.nextCursor()));
    return message;
  }
  @Override public Conversation startConversation(String userId){
    Conversation result=conversation(call("POST","users/me/conversations",json("userId",userId)));
    conversationCache.put(result.id(),result);
    return result;
  }
  @Override public void readConversation(String id){
    call("POST","users/me/conversations/"+id+"/read",null);
    Conversation prior=conversationCache.get(id);
    if(prior!=null&&prior.unreadCount()>0){
      directUnread=Math.max(0,directUnread-prior.unreadCount());
      conversationCache.put(id,new Conversation(prior.id(),prior.other(),prior.lastMessage(),prior.lastSenderId(),
          prior.lastMessageAt(),0,prior.updatedAt()));
      List<Conversation> items=new ArrayList<>();
      for(Conversation item:conversationPage.items())items.add(item.id().equals(id)
          ?new Conversation(item.id(),item.other(),item.lastMessage(),item.lastSenderId(),item.lastMessageAt(),0,item.updatedAt()):item);
      conversationPage=new ConversationPage(items,conversationPage.total(),conversationPage.hasMore());
    }
  }
  @Override public void hideConversation(String id){
    call("DELETE","users/me/conversations/"+id,null);
    Conversation prior=conversationCache.remove(id);
    if(prior!=null)directUnread=Math.max(0,directUnread-prior.unreadCount());
    chatCache.remove(id);
    List<Conversation> items=new ArrayList<>();
    for(Conversation item:conversationPage.items())if(!item.id().equals(id))items.add(item);
    conversationPage=new ConversationPage(items,Math.max(0,conversationPage.total()-1),conversationPage.hasMore());
  }
  private Conversation conversation(JSONObject j){
    JSONObject other=j.optJSONObject("other");
    return new Conversation(text(j,"id"),
        new ChatUser(text(other,"id"),text(other,"displayName"),text(other,"avatarUrl")),
        text(j,"lastMessage"),text(j,"lastSenderId"),time(j,"lastMessageAt"),
        j.optLong("unreadCount"),time(j,"updatedAt"));
  }
  private DirectMessage directMessage(JSONObject j){
    return new DirectMessage(text(j,"id"),text(j,"conversationId"),text(j,"senderId"),
        text(j,"content"),time(j,"createdAt"));
  }
  @Override public void readMessage(String id) { call("POST","users/me/messages/"+id+"/read",null); markMessageRead(id); }
  private void markMessageRead(String id) {
    List<Message> items=new ArrayList<>();
    boolean changed=false;
    for(Message item:messagePage.items()) {
      if(item.id().equals(id)&&!item.isRead()) { items.add(withRead(item)); changed=true; }
      else items.add(item);
    }
    if(changed) {
      messagePage=new MessagePage(items,messagePage.total(),messagePage.hasMore());
      if(messageUnread>0) messageUnread--;
    }
  }
  private static Message withRead(Message item) {
    return item.isRead()?item:new Message(item.id(),item.title(),item.body(),item.category(),
        item.refType(),item.refId(),item.refCommentId(),item.refRootCommentId(),item.actorCount(),true,item.createdAt());
  }
  @Override public void readAllMessages() {
    call("POST","users/me/messages/read-all",null);
    List<Message> items=new ArrayList<>();
    for(Message item:messagePage.items()) items.add(withRead(item));
    messagePage=new MessagePage(items,messagePage.total(),messagePage.hasMore());
    messageUnread=0;
  }
  @Override public boolean blockedByMe(String userId) {
    return call("GET", "users/" + encode(userId) + "/block", null).optBoolean("blocked");
  }

  @Override public BlockedUserPage blockedUsers(int page) {
    if(page < 1) throw new IllegalArgumentException("页码无效");
    final String owner;
    synchronized(dashboardLock){owner=recommendationOwner();}
    if(owner.isEmpty())throw new RepositoryException(401,"AUTH_REQUIRED","请先登录",0);
    JSONObject result=call("GET", "users/me/blocks?page="+page+"&size=20", null);
    synchronized(dashboardLock){
      if(!owner.equals(recommendationOwner()))
        throw new RepositoryException(409,"ACCOUNT_CHANGED","账号已变更，请重新打开黑名单",0);
    }
    return BlockedUsersJson.page(result,page);
  }

  @Override public void setBlocked(String userId, boolean blocked) {
    call(blocked ? "PUT" : "DELETE", "users/" + encode(userId) + "/block", null);
  }

  @Override public void reportContent(String type, String id, String reason) {
    call("POST", "reports", json("targetType", type, "targetId", id, "reason", reason));
  }

  @Override public ReportPage reports(int page) {
    JSONObject result = accountCall("GET", "admin/reports?page=" + page + "&size=20", null);
    JSONArray rows = result.optJSONArray("items");
    if (rows == null) throw new IllegalStateException("举报列表返回格式异常");
    List<ReportRow> items = new ArrayList<>();
    for (int i = 0; i < rows.length(); i++) {
      JSONObject row = rows.optJSONObject(i);
      items.add(new ReportRow(text(row, "id"), text(row, "targetType"), text(row, "targetUserId"),
          text(row, "postId"), text(row, "reason"), text(row, "excerpt")));
    }
    return new ReportPage(items, result.optLong("total"), page, result.optInt("size", 20));
  }

  @Override public void reviewReport(String id) {
    call("PUT", "admin/reports/" + encode(id) + "/review", null);
  }

  private JSONObject accountCall(String method,String path,JSONObject body) {
    final String owner;
    synchronized(dashboardLock){owner=capabilityOwner(user);}
    if(owner.isEmpty())throw new RepositoryException(401,"AUTH_REQUIRED","请先登录",0);
    JSONObject result=call(method,path,body);
    synchronized(dashboardLock){if(!owner.equals(capabilityOwner(user)))throw new RepositoryException(409,"ACCOUNT_CHANGED","账号已变化，请重新打开管理记录",0);}
    return result;
  }
  @Override public void decideReport(String id,String outcome,String reason,String note) {
    accountCall("POST","admin/reports/"+encode(id)+"/decision",json("outcome",outcome,"publicReason",reason,"internalNote",note));
  }
  @Override public ReportManagement.Page<ReportManagement.Decision> adminReportDecisions(int page) {
    return ReportManagementJson.page(accountCall("GET","admin/reports/decisions?page="+page+"&size=20",null),ReportManagementJson::decision);
  }
  @Override public ReportManagement.Page<ReportManagement.FollowUp> adminReportFollowUps(String report,int page) {
    return ReportManagementJson.page(accountCall("GET","admin/reports/"+encode(report)+"/follow-ups?page="+page+"&size=20",null),ReportManagementJson::followUp);
  }
  @Override public void replyToReport(String report,String content,String note,List<String> evidence,String requestId) {
    accountCall("POST","admin/reports/"+encode(report)+"/follow-ups",json("content",content,"internalNote",note,"evidenceUrls",array(evidence),"clientRequestId",requestId));
  }
  @Override public void performReportAction(String report,String action,String note) {
    accountCall("POST","admin/reports/"+encode(report)+"/actions",json("actionType",action,"note",note));
  }
  @Override public ReportManagement.Page<ReportManagement.Appeal> adminAppeals(String status,int page) {
    return ReportManagementJson.page(accountCall("GET","admin/appeals?page="+page+"&size=20"+(status.isBlank()?"":"&status="+encode(status)),null),ReportManagementJson::appeal);
  }
  @Override public ReportManagement.Appeal adminAppeal(String id) {
    return ReportManagementJson.appeal(accountCall("GET","admin/appeals/"+encode(id),null));
  }
  @Override public ReportManagement.Appeal startAppealReview(String id,long revision) {
    return ReportManagementJson.appeal(accountCall("POST","admin/appeals/"+encode(id)+"/start?expectedRevision="+revision,null));
  }
  @Override public ReportManagement.Appeal resolveAppeal(String id,String outcome,String reason,String note,long revision) {
    return ReportManagementJson.appeal(accountCall("POST","admin/appeals/"+encode(id)+"/resolve",json("outcome",outcome,"publicReason",reason,"internalNote",note,"expectedRevision",revision)));
  }

  private JSONObject feedbackCall(boolean admin,String method,String path,JSONObject body) {
    final String owner;
    synchronized(dashboardLock){
      owner=recommendationOwner();
      if(owner.isEmpty())throw new RepositoryException(401,"AUTH_REQUIRED","请先登录",0);
      if(admin&&!"superadmin".equals(user.role()))throw new RepositoryException(403,"ACCESS_DENIED","仅超级管理员可管理反馈",0);
    }
    JSONObject result=call(method,(admin?"admin/feedback":"users/me/feedback")+path,body);
    synchronized(dashboardLock){if(!owner.equals(recommendationOwner()))throw new RepositoryException(409,"ACCOUNT_CHANGED","账号已变化，请重新打开反馈记录",0);}
    return result;
  }
  @Override public FeedbackModels.Page feedback(boolean admin,boolean archived,int page) {
    return FeedbackJson.page(feedbackCall(admin,"GET","?archived="+archived+"&page="+page+"&size=20",null),page);
  }
  @Override public FeedbackModels.Feedback feedbackDetail(String id,boolean admin) {
    return FeedbackJson.row(feedbackCall(admin,"GET","/"+encode(id),null));
  }
  @Override public FeedbackModels.ReplyPage feedbackReplies(String id,boolean admin,int page) {
    return FeedbackJson.replies(feedbackCall(admin,"GET","/"+encode(id)+"/replies?page="+page+"&size=20",null),page);
  }
  @Override public FeedbackModels.Feedback submitFeedback(String type,String content,String requestId) {
    return FeedbackJson.row(feedbackCall(false,"POST","",json("type",type,"content",content,"clientRequestId",requestId)));
  }
  @Override public FeedbackModels.Feedback replyToFeedback(String id,String reply,boolean completed,String requestId) {
    return FeedbackJson.row(feedbackCall(true,"PUT","/"+encode(id),json("reply",reply,"status",completed?"completed":JSONObject.NULL,"clientRequestId",requestId)));
  }
  @Override public FeedbackModels.Export exportFeedback() {return FeedbackJson.export(feedbackCall(true,"POST","/export",null));}
  @Override public FeedbackModels.ImportResult importFeedback(String csv) {return FeedbackJson.imported(feedbackCall(true,"POST","/import",json("csv",csv)));}

  private static String encode(String value) { try { return java.net.URLEncoder.encode(value,"UTF-8"); } catch(java.io.UnsupportedEncodingException e) { throw new IllegalStateException(e); } }
  private AdminNotice adminNoticeRow(JSONObject j) {
    return new AdminNotice(text(j,"id"),text(j,"title"),text(j,"content"),new NoticeSchedule(text(j,"publishedAt"),text(j,"displayStartsAt"),
        text(j,"displayEndsAt"),text(j,"expiresOn"),j.optBoolean("isUpdate"),j.optBoolean("published",true),j.optBoolean("enabled")),text(j,"status"));
  }
  @Override public AdminNoticePage adminNotices(int page,String query) {
    JSONObject result=call("GET","admin/notices/search?page="+page+"&size=6&q="+encode(query),null);
    JSONArray rows=result.optJSONArray("items");List<AdminNotice> items=new ArrayList<>();
    if(rows!=null) for(int i=0;i<rows.length();i++) items.add(adminNoticeRow(rows.optJSONObject(i)));
    return new AdminNoticePage(List.copyOf(items),result.optLong("total"));
  }
  @Override public AdminNotice adminNotice(String id) { return adminNoticeRow(call("GET","admin/notices/"+id,null)); }
  @Override public String noticeSetting() { return text(call("GET","admin/notices/settings",null),"noticeId"); }
  @Override public void setNoticeSetting(String id) { call("PUT","admin/notices/settings",json("noticeId",id==null||id.isEmpty()?JSONObject.NULL:id)); }
  @Override public AdminNotice saveNotice(String id,String content) {
    return adminNoticeRow(call(id==null||id.isEmpty()?"POST":"PUT",id==null||id.isEmpty()?"admin/notices/markdown":"admin/notices/"+id+"/markdown",json("content",content)));
  }
  private static Object nullable(String text){return text==null||text.isBlank()?JSONObject.NULL:text;}
  private static JSONObject scheduleJson(NoticeSchedule value){return json("publishedAt",nullable(value.publishedAt()),"displayStartsAt",nullable(value.displayStartsAt()),
      "displayEndsAt",nullable(value.displayEndsAt()),"expiresOn",nullable(value.expiresOn()),"isUpdate",value.isUpdate(),"published",value.published(),"enabled",value.enabled());}
  @Override public AdminNotice saveNotice(String id,String content,NoticeSchedule schedule) {
    return adminNoticeRow(call(id==null||id.isBlank()?"POST":"PUT",id==null||id.isBlank()?"admin/notices/markdown":"admin/notices/"+encode(id)+"/markdown",json("content",content,"schedule",scheduleJson(schedule))));
  }
  @Override public AdminNotice saveNoticeSchedule(String id,NoticeSchedule schedule){return adminNoticeRow(call("PUT","admin/notices/"+encode(id)+"/schedule",scheduleJson(schedule)));}
  @Override public List<Notice> currentNotices(){
    List<Notice> result=list("users/me/notices/current-set",this::notice);
    if(result.size()>2||result.stream().anyMatch(item->item.id().isBlank()||item.content().isBlank())||result.stream().map(Notice::id).distinct().count()!=result.size())throw new RepositoryException(502,"INVALID_NOTICE_RESPONSE","公告响应异常，请重试",0);
    return result;
  }
  private Notice notice(JSONObject j) {
    return new Notice(text(j,"id"),text(j,"content"),time(j,j.has("publishedAt")?"publishedAt":"createdTime"),j.optBoolean("isUpdate"));
  }
  private Message message(JSONObject j) {
    return new Message(text(j,"id"),text(j,"title"),text(j,"body"),text(j,"category"),
        text(j,"refType"),text(j,"refId"),text(j,"refCommentId"),text(j,"refRootCommentId"),
        j.optInt("actorCount"),j.optBoolean("isRead"),time(j,"createdAt"));
  }
  @Override public void deleteReview(String id) { call("DELETE","users/me/reviews/"+id,null); }
  @Override public void moderate(String id,String action,String reason) { moderate(id,action,reason,null); }
  @Override public void moderate(String id,String action,String reason,BigDecimal price) {
    Submission target=queue.stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow(() -> missing("投稿不存在，请刷新"));
    call("POST","admin/moderation/"+target.type()+"/"+id,json("action",action,"reason",reason,"price",price));
  }
  @Override public cn.edu.bjtu.foodtime.domain.model.PriceHistory.Snapshot priceHistory(String dish,int days){
    var j=accountCall("GET","dishes/"+encode(dish)+"/price-history?days="+days,null);
    if(!dish.equals(text(j,"dishId"))||j.optJSONArray("records")==null||time(j,"windowEnd")<=0)throw new RepositoryException(502,"INVALID_RESPONSE","价格记录响应异常",0);
    var rows=new ArrayList<cn.edu.bjtu.foodtime.domain.model.PriceHistory.Entry>();var array=j.optJSONArray("records");
    for(int i=0;i<array.length();i++){var row=array.optJSONObject(i);if(row==null||time(row,"recordedAt")<=0||text(row,"id").isBlank())throw new RepositoryException(502,"INVALID_RESPONSE","价格记录响应异常",0);rows.add(new cn.edu.bjtu.foodtime.domain.model.PriceHistory.Entry(text(row,"id"),decimal(row,"price"),text(row,"priceUnit"),time(row,"recordedAt"),text(row,"origin"),row.optLong("revision"),row.optBoolean("carriedForward")));}
    return new cn.edu.bjtu.foodtime.domain.model.PriceHistory.Snapshot(dish,text(j,"dishName"),decimal(j,"currentPrice"),text(j,"priceUnit"),j.optLong("revision"),time(j,"windowStart"),time(j,"windowEnd"),j.optLong("totalRecords"),j.optBoolean("canManage"),rows);
  }
  @Override public void correctPriceHistory(String dish,String entry,BigDecimal price,String unit,long revision){accountCall("PUT","admin/dishes/"+encode(dish)+"/price-history/"+encode(entry),json("price",price,"priceUnit",unit,"expectedRevision",revision));}
  @Override public void deletePriceHistory(String dish,String entry,long revision){accountCall("DELETE","admin/dishes/"+encode(dish)+"/price-history/"+encode(entry)+"?expectedRevision="+revision,null);}
  @Override public void editEntity(String type,String id,String parent,String name,String description,String price,String image,boolean active) {
    editEntity(type,id,parent,name,description,price,image,active,null);
  }
  @Override public void editEntity(String type,String id,String parent,String name,String description,String price,String image,boolean active,String unit) {
    String reference=image==null||image.isBlank()?null:image.startsWith("file:")?upload(image):image;
    String path="admin/catalog/"+table(type)+(id.isBlank()?"":"/"+id);
    call(id.isBlank()?"POST":"PUT",path,json("parentId",parent.isBlank()?null:parent,"name",name,"description",description,
        "price",type.equals("dishes")?Validation.price(price):null,"priceUnit",unit,"coverImageUrl",reference,"status",active?"active":"inactive"));
  }
  @Override public void removeEntity(String type,String id) { call("DELETE","admin/catalog/"+table(type)+"/"+id,null); }
  @Override public long recordCommunityPostView(String postId,String requestId) {
    JSONObject result=call("POST","community/posts/"+postId+"/views",json("clientRequestId",requestId));
    Object count=result.opt("viewCount");
    if(!(count instanceof Number)||((Number)count).longValue()<0) throw new RepositoryException(0,"INVALID_RESPONSE","浏览量响应异常",0);
    return ((Number)count).longValue();
  }
  private String managedImagePath(String type,String id) { return "admin/catalog/"+table(type)+"/"+id+"/images"; }
  @Override public CatalogImagePage managedImages(String type,String id,int page) {
    JSONObject data=call("GET",managedImagePath(type,id)+"?page="+page+"&size=12",null);
    List<Photo> items=new ArrayList<>();
    JSONArray rows=data.optJSONArray("items");
    if(rows==null) throw new RepositoryException(0,"INVALID_RESPONSE","图片列表响应异常",0);
    for(int i=0;i<rows.length();i++) { JSONObject row=rows.optJSONObject(i); if(row==null) throw new RepositoryException(0,"INVALID_RESPONSE","图片列表响应异常",0); items.add(new Photo(text(row,"id"),id,text(row,"imageUrl"),"")); }
    long total=data.optLong("total");
    return new CatalogImagePage(items,total,page,(long)page*12<total);
  }
  @Override public void addManagedImage(String type,String id,String file) {
    String key=type+":"+id+":"+file;
    String reference=catalogUploads.computeIfAbsent(key,ignored -> upload(file));
    call("POST",managedImagePath(type,id),json("imageUrl",reference));
    catalogUploads.remove(key,reference);
  }
  @Override public void removeManagedImage(String type,String id,String imageId) {
    call("DELETE",managedImagePath(type,id)+"/"+imageId,null);
  }
  @Override public void removeReviewAsAdmin(String id) { throw new IllegalArgumentException("请填写删除原因"); }
  @Override public void removeReviewAsAdmin(String id,String reason) { call("DELETE","admin/reviews/"+id,json("reason",reason)); }
  @Override public void updateUser(String id,String role,boolean active) { call("PUT","admin/users/"+id,json("role",role,"status",active?"active":"disabled")); }

  private String upload(String reference) {return upload(reference,false);}
  private String upload(String reference,boolean avatar) {
    if(reference==null||reference.isBlank()) throw new IllegalArgumentException("请选择图片");
    if(reference.startsWith("/api/v1/media/")) return reference;
    try {
      URI uri=URI.create(reference); if(!uri.getScheme().equals("file")) throw new IllegalArgumentException("请重新选择图片");
      long limit=avatar?cn.edu.bjtu.foodtime.domain.validation.AvatarCropGeometry.MAX_BYTES:50L*1024*1024;
      File file=new File(uri);if(file.length()>limit)throw new IllegalArgumentException(avatar?"头像不能超过10MB":"图片不能超过50MB");
      String image=text(interpret(http.sendFile(avatar?"uploads/avatars":"uploads/images",file,token())),"imageUrl");
      if(avatar&&!image.matches("/api/v1/media/[0-9a-fA-F-]{36}"))throw new RepositoryException(0,"INVALID_RESPONSE","头像上传响应异常，请重试",0);
      return image;

    } catch(IOException e) { throw unavailable(); }
  }
  @Override public byte[] loadImage(String reference) {
    return loadImageResource(reference).bytes();
  }
  @Override public ImageResource loadImageResource(String reference) {
    long epoch=imageEpoch.get();String scope=imageScope();
    URI uri=origin.resolve(reference);
    boolean sameOrigin=origin.getScheme().equals(uri.getScheme())&&origin.getAuthority().equals(uri.getAuthority());
    if(!sameOrigin&&!"https".equals(uri.getScheme())) throw new IllegalArgumentException("图片地址不合法");
    if(uri.getUserInfo()!=null||uri.getFragment()!=null)throw new IllegalArgumentException("图片地址不合法");
    // Keep the original epoch-zero key format on disk; the in-memory epoch only cancels old work.
    String publicKey="p-"+ImageDiskCache.hash(uri.toString()),privateKey="s-"+ImageDiskCache.hash(scope+":0:"+uri);
    ImageDiskCache.Entry cached=imageCache==null?null:imageCache.get(publicKey);
    if(cached==null&&imageCache!=null)cached=imageCache.get(privateKey);
    if(cached!=null&&cached.freshUntil()>System.currentTimeMillis()) {
      if(epoch!=imageEpoch.get()||!scope.equals(imageScope())||Thread.currentThread().isInterrupted())throw unavailable();
      return new ImageResource(cached.bytes(),true,cached.freshUntil(),cached.etag());
    }
    try {
      // Legacy external HTTPS images receive no application cookies or authentication headers.
      HttpTransport loader=sameOrigin?http:new HttpTransport(uri.getScheme()+"://"+uri.getAuthority()+"/",
          new java.net.CookieManager(null,java.net.CookiePolicy.ACCEPT_NONE));
      var response=loader.bytes(uri.toString(),cached==null||cached.etag().isBlank()?Map.of():Map.of("If-None-Match",cached.etag()));
      if(epoch!=imageEpoch.get()||!scope.equals(imageScope())||Thread.currentThread().isInterrupted())throw unavailable();
      String policy=header(response,"Cache-Control").toLowerCase(Locale.ROOT),type=header(response,"Content-Type"),etag=header(response,"ETag");
      if(response.status()!=200&&response.status()!=304) {
        if(imageCache!=null&&response.status()>=400&&response.status()<500&&response.status()!=429){
          imageCache.remove(publicKey);imageCache.remove(privateKey);
        }
        if(response.status()==401)clear();
        throw new RepositoryException(response.status(),"IMAGE_UNAVAILABLE","图片暂不可用",0);
      }
      byte[] bytes=response.status()==304&&cached!=null?cached.bytes():response.body();
      if(response.status()==304&&cached==null)throw unavailable();
      if(type.isBlank()&&cached!=null)type=cached.contentType();
      if(!type.toLowerCase(Locale.ROOT).startsWith("image/")||bytes.length==0)throw unavailable();
      if(etag.isBlank()&&cached!=null)etag=cached.etag();
      boolean reusable=!policy.contains("no-store")&&(policy.contains("private")||policy.contains("public"));
      long until=0;
      boolean shared=policy.contains("public")&&!policy.contains("private");
      if(reusable&&!policy.contains("no-cache")) {
        var match=java.util.regex.Pattern.compile("(?:^|[,\\s])max-age=(\\d+)").matcher(policy);
        if(match.find())try {
          long age=Long.parseLong(header(response,"Age").isBlank()?"0":header(response,"Age"));
          long maxAge=Math.min(shared?31536000L:300L,Long.parseLong(match.group(1)));
          if(age>=0&&age<maxAge)until=System.currentTimeMillis()+(maxAge-age)*1000;
        }catch(NumberFormatException ignored){}
      }
      synchronized(imageEpoch) {
        if(epoch!=imageEpoch.get()||!scope.equals(imageScope())||Thread.currentThread().isInterrupted())throw unavailable();
        if(imageCache!=null) {
          String key=shared&&until>0?publicKey:privateKey;
          imageCache.remove(key.equals(publicKey)?privateKey:publicKey);
          if(reusable)imageCache.put(key,new ImageDiskCache.Entry(bytes,etag,type,until));
          else imageCache.remove(key);
        }
      }
      return new ImageResource(bytes,reusable,until,etag.isBlank()?ImageDiskCache.contentHash(bytes):etag);
    } catch(IOException e) { throw unavailable(); }
  }
  private static String header(HttpTransport.BinaryResponse response,String name) {var values=response.headers().get(name);return values==null||values.isEmpty()?"":values.get(0);}
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
  /** 只替换本地缓存中的关注标记，避免为一次切换重新拉取整页列表。 */
  private static FollowPage withFollowState(FollowPage page,String userId,boolean followed) {
    List<FollowUser> rows=new ArrayList<>(page.items());
    for(int i=0;i<rows.size();i++) {
      FollowUser row=rows.get(i);
      if(row.id().equals(userId))
        rows.set(i,new FollowUser(row.id(),row.displayName(),row.avatarUrl(),followed));
    }
    return new FollowPage(rows,page.total(),page.page(),page.hasMore());
  }

  private FollowState fetchFollowState(String userId) {
    JSONObject state=call("GET","users/"+userId+"/follow-status",null);
    return new FollowState(state.optBoolean("followed"),state.optLong("followerCount"));
  }

  private User person(JSONObject j) { return new User(text(j,"id"),text(j,"email"),text(j,"displayName"),text(j,"role"),!Set.of("disabled","deleted").contains(text(j,"status")),text(j,"avatarUrl"),time(j,"createdAt"),j.optLong("reviewCount"),j.optLong("followingCount"),j.optLong("followerCount"),text(j,"status").equals("deleted")); }
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
    catalogUploads.clear();
    venues.clear();
    synchronized(imageEpoch){imageEpoch.incrementAndGet();if(imageCache!=null)imageCache.clear(true);}
    synchronized(dashboardLock) {user=null;sessionMode="normal";dashboardSnapshot=null;dashboardRequest++;resetFollows();clearRecommendations();clearCapabilities();historySettings=null;}
    avatarUploads.clear();csrf=null;http.clearSession(); hallList=List.of();halls.clear();stalls.clear();dishes.clear();

    stallLists.clear();dishLists.clear();reviews.clear();photos.clear();ratings.clear();contexts.clear();catalog.clear();entities.clear();parents.clear();
    followingFeed = new CommunityFeedSlice(List.of(), 0, "");
    communityPostCache.clear();communityCommentCache.clear();communityFeed=List.of();pinSnapshot=cn.edu.bjtu.foodtime.domain.model.CommunityPins.Snapshot.empty();pinOwner="";ownCommunityPosts=CommunityPostPage.empty();ownCommunityFavorites=CommunityPostPage.empty();communityAudit=CommunityAuditPage.empty();communityAuditDetail=null;communityUploads.clear();communityRoots.clear();communityReplies.clear();communityCursor="";communityHasMore=false;
    dishReviewPages.clear();replyPages.clear();ownReplies=ReplyPage.empty();
    ownReviews=List.of();ownSubmissions=List.of();queue=List.of();auditList=List.of();userList=List.of();
    noticePage=NoticePage.empty();noticeNumber=1;messagePage=MessagePage.empty();messageNumber=1;
    conversationPage=ConversationPage.empty();conversationNumber=1;conversationCache.clear();chatCache.clear();
    noticeUnread=0;messageUnread=0;directUnread=0;lastFeed=NoticeFeed.empty();
  }
}
