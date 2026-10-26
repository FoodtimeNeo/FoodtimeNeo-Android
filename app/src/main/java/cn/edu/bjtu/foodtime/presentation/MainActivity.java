package cn.edu.bjtu.foodtime.presentation;

import android.app.Activity;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import cn.edu.bjtu.foodtime.domain.repository.FoodtimeRepository;
import cn.edu.bjtu.foodtime.domain.repository.RepositoryProvider;
import cn.edu.bjtu.foodtime.domain.repository.ContentRequest;
import cn.edu.bjtu.foodtime.domain.repository.RepositoryException;
import cn.edu.bjtu.foodtime.domain.model.Models.Notice;
import cn.edu.bjtu.foodtime.domain.model.Models.NoticeFeed;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.function.*;

public class MainActivity extends Activity {
  public FoodtimeRepository repo;

  public record Route(String kind, String id) {}

  public interface Screen {
    void show(MainActivity activity, LinearLayout page, Route route);
  }

  final Map<String, Screen> screens = new HashMap<>();
  final Map<String, ArrayList<Route>> stacks = new HashMap<>();
  String tab = "dining";
  LinearLayout root, content, header;
  ScrollView scroll;
  final Bundle drafts = new Bundle();
  final Bundle scrollPositions = new Bundle();
  String pickingKey;
  private Executor worker;
  private Executor inboxWorker;
  private java.util.concurrent.FutureTask<Void> navigationRead;
  private final android.os.Handler navigationHandler=new android.os.Handler(android.os.Looper.getMainLooper());
  private boolean navigationLoading,contentReady,foreground,resumeNavigation,pageFailed;
  private LinearLayout refreshBanner;
  private String displayedActor="";
  private int navigationEpoch,inboxEpoch;
  private boolean inboxInFlight,inboxDialogShowing;
  private android.app.AlertDialog inboxDialog;
  private final Deque<String> pendingNotices=new ArrayDeque<>();
  private int generation;
  private boolean writeInFlight;
  private String displayedLocation="";
  private Toast currentToast;
  private static final OperationStore OPERATIONS=new OperationStore();
  private long pendingOperation;
  private boolean authRegistration;
  private boolean stateSaved;
  private Runnable deferredCompletion;
  private final Map<String,List<ReadListener>> reads=new HashMap<>();
  private final List<Runnable> deferredReads=new ArrayList<>();
  private final android.os.Handler inboxPoll=new android.os.Handler(android.os.Looper.getMainLooper());
  private final java.util.Set<String> inboxSeen=new java.util.HashSet<>();
  private boolean inboxBaseline;
  private final Runnable inboxPollTask=new Runnable() {
    @Override public void run() {
      inboxPoll.removeCallbacks(this);
      if(!foreground||repo==null||repo.currentUser()==null||inboxInFlight) return;
      inboxInFlight=true;int ticket=inboxEpoch;String actor=actor();
      inboxWorker.execute(() -> {
        NoticeFeed feed=null;RuntimeException failure=null;
        try { feed=repo.noticeFeed(0,20); } catch(RuntimeException e) { failure=e; }
        NoticeFeed result=feed;RuntimeException error=failure;
        runOnUiThread(() -> {
          inboxInFlight=false;
          if(isDestroyed()) return;
          if(foreground&&repo.currentUser()==null) {clearNavigation();showAuth(false);message("请重新登录");return;}
          if(!foreground||ticket!=inboxEpoch||!actor.equals(actor())) {if(foreground&&repo.currentUser()!=null)inboxPoll.postDelayed(this,60_000);return;}
          if(repo.currentUser()==null) { clearNavigation();showAuth(false);message("请重新登录");return; }
          if(error==null&&result!=null) {
            List<String> fresh=new ArrayList<>();
            for(Notice item:result.items()) if(!inboxSeen.contains(item.id())) fresh.add(item.id());
            inboxSeen.addAll(fresh);
            boolean first=!inboxBaseline;inboxBaseline=true;
            if(!first&&!fresh.isEmpty()) {
              for(Notice item:result.items())if(fresh.contains(item.id()))pendingNotices.addLast(item.title());
              drainNotices();
            }
          }
          inboxPoll.postDelayed(this,60_000);
        });
      });
    }
  };
  void noteInboxSeen(java.util.Collection<String> ids) { inboxSeen.addAll(ids); }
  private String actor(){var user=repo==null?null:repo.currentUser();return user==null?"":user.id()+":"+user.role();}
  private void drainNotices() {
    if(!foreground||!contentReady||pageFailed||navigationLoading||stateSaved||writeInFlight||inboxDialogShowing||!hasWindowFocus()||pendingNotices.isEmpty()||repo.currentUser()==null)return;
    inboxDialogShowing=true;
    inboxDialog=Ui.confirm(this,"收到新公告",pendingNotices.removeFirst(),()->go("inbox",""));
    inboxDialog.setOnDismissListener(dialog->{inboxDialog=null;inboxDialogShowing=false;inboxPoll.post(this::drainNotices);});
  }
  @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused)drainNotices();}
  private record ReadListener(int generation,View trigger,TextView error,Runnable success,Runnable finished) {}

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    Design.init(this);
    repo = ((RepositoryProvider) getApplication()).repository();
    worker = ((RepositoryProvider) getApplication()).workExecutor();
    inboxWorker = ((RepositoryProvider) getApplication()).backgroundExecutor();
    registerScreens();
    for (String key : List.of("dining", "community", "profile", "admin"))
      stacks.put(key, new ArrayList<>(List.of(new Route(key, ""))));
    if (state != null) {
      pendingOperation=state.getLong("pendingOperation",0);
      authRegistration=state.getBoolean("authRegistration",false);
      tab = state.getString("tab", "dining");
      for (String key : stacks.keySet()) {
        ArrayList<String> saved = state.getStringArrayList(key);
        if (saved != null && !saved.isEmpty()) {
          ArrayList<Route> routes = new ArrayList<>();
          for (String value : saved) {
            String[] p = value.split("\\|", 2);
            routes.add(new Route(p[0], p.length > 1 ? p[1] : ""));
          }
          stacks.put(key, routes);
        }
      }
    }
    if (android.os.Build.VERSION.SDK_INT >= 33)
      getOnBackInvokedDispatcher().registerOnBackInvokedCallback(0, this::back);
    if (state != null) {
      Bundle saved = state.getBundle("drafts");
      if (saved != null) drafts.putAll(saved);
      pickingKey = state.getString("pickingKey");
      Bundle positions = state.getBundle("scrollPositions");
      if (positions != null) scrollPositions.putAll(positions);
    }
    restore();
  }

  public void restore() {
    int ticket=++generation;
    shell(false);content.addView(Ui.text(this,"正在连接…",16,Design.MUTED));
    worker.execute(() -> {
      RuntimeException error=null;try {repo.restoreSession();}catch(RuntimeException e){error=e;}
      RuntimeException failure=error;
      runOnUiThread(() -> {
        if(isDestroyed()||ticket!=generation) return;
        if(failure!=null) {content.removeAllViews();Ui.empty(content,"连接失败",safeMessage(failure));Ui.action(content,"重试",true,this::restore);}
        else if(pendingOperation!=0) recoverOperation();
        else if(repo.currentUser()==null) showAuth(authRegistration);else openApp();
      });
    });
  }

  void registerScreens() {
    screens.put("dining", DiningScreen::halls);
    screens.put("community", CommunityScreen::feed);
    screens.put("community-detail", CommunityScreen::detail);
    screens.put("community-create", CommunityScreen::create);
    screens.put("community-mine", CommunityScreen::collection);
    screens.put("inbox", InboxScreen::inbox);
    screens.put("community-favorites", CommunityScreen::collection);
    screens.put("admin-community", CommunityAdminScreen::queue);
    screens.put("admin-community-detail", CommunityAdminScreen::detail);
    screens.put("stalls", DiningScreen::stalls);
    screens.put("dishes", DishScreen::dishes);
    screens.put("gallery", DishScreen::gallery);
    screens.put("upload-photo", UploadScreen::photo);
    screens.put("submit-dish", UploadScreen::dish);
    screens.put("submit-stall", UploadScreen::stall);
    screens.put("mine-submissions", SubmissionScreen::mine);
    screens.put("profile", ProfileScreen::profile);
    screens.put("profile-edit", ProfileScreen::edit);
    screens.put("mine-reviews", ProfileScreen::reviews);
    screens.put("password", PasswordScreen::show);
    screens.put("detail", DishScreen::detail);
    screens.put("review", ReviewScreen::create);
    screens.put("review-replies", ReviewReplyScreen::thread);
    screens.put("reply-create", ReviewReplyScreen::create);
    screens.put("mine-review-replies", ReviewReplyScreen::mine);
    screens.put("admin", AdminScreen::home);
    screens.put("admin-audit", AdminScreen::audit);
    screens.put("admin-data", AdminScreen::data);
    screens.put("admin-new", AdminScreen::edit);
    screens.put("admin-edit", AdminScreen::edit);
    screens.put("admin-reviews", AdminScreen::reviews);
    screens.put("admin-users", AdminScreen::users);
    screens.put("admin-logs", AdminScreen::logs);
  }

  public void showAuth(boolean register) {
    if(writeInFlight) {message("正在提交，请稍候");return;}
    authRegistration=register;
    cancelNavigation();contentReady=false;
    ++generation;
    shell(false);
    AuthScreen.show(this, content, register);
  }

  public void openApp() {
    if (repo.currentUser() == null) {
      showAuth(false);
      return;
    }
    if (tab.equals("admin") && !repo.currentUser().isAdmin()) tab = "dining";
    inboxPoll.removeCallbacks(inboxPollTask);if(foreground&&!inboxInFlight)inboxPoll.post(inboxPollTask);
    render();
  }

  public void go(String kind, String id) {
    if(writeInFlight) {message("正在提交，请稍候");return;}
    rememberScroll();
    stacks.get(tab).add(new Route(kind, id));
    render();
  }

  void replaceRoute(String kind,String id){
    if(writeInFlight){message("正在提交，请稍候");return;}
    rememberScroll();ArrayList<Route> stack=stacks.get(tab);stack.set(stack.size()-1,new Route(kind,id));render();
  }
  public Route route() {
    ArrayList<Route> s = stacks.get(tab);
    return s.get(s.size() - 1);
  }

  public void home() {
    if(writeInFlight) {message("正在提交，请稍候");return;}
    rememberScroll();
    tab = "dining";
    scrollPositions.remove("dining:dining:");
    stacks.get(tab).clear();
    stacks.get(tab).add(new Route("dining", ""));
    render();
  }

  public void selectTab(String next) {
    if(writeInFlight) {message("正在提交，请稍候");return;}
    if (tab.equals(next)) return;
    rememberScroll();
    tab = next;
    render();
  }

  public void back() {
    if(writeInFlight) {message("正在提交，请稍候");return;}
    if (repo.currentUser() == null) {
      finish();
      return;
    }
    rememberScroll();
    ArrayList<Route> s = stacks.get(tab);
    if (s.size() > 1) {
      s.remove(s.size() - 1);
      render();
    } else if (!tab.equals("dining")) {
      tab = "dining";
      render();
    } else finish();
  }

  @Override
  @android.annotation.SuppressLint(
      "GestureBackNavigation") // Legacy API 26-32 only; API 33+ uses the native
  // OnBackInvokedDispatcher above.
  public void onBackPressed() {
    back();
  }

  @Override
  protected void onSaveInstanceState(Bundle state) {
    super.onSaveInstanceState(state);
    stateSaved=true;
    rememberScroll();
    state.putBundle("drafts", drafts);
    state.putBundle("scrollPositions", scrollPositions);
    state.putString("pickingKey", pickingKey);
    state.putLong("pendingOperation",pendingOperation);
    state.putBoolean("authRegistration",authRegistration);
    state.putString("tab", tab);
    for (String key : stacks.keySet()) {
      ArrayList<String> saved = new ArrayList<>();
      for (Route route : stacks.get(key)) saved.add(route.kind() + "|" + route.id());
      state.putStringArrayList(key, saved);
    }
  }

  public void render() {
    if (repo.currentUser() == null) {
      showAuth(false);
      return;
    }
    if(displayedLocation.equals(location())) rememberScroll();
    cancelNavigation();
    pageFailed=false;
    int ticket=++generation;
    int epoch=navigationEpoch;
    String actor=actor();
    Route target=route();boolean catalog=LoadingScreen.catalog(target);
    boolean retain=catalog&&contentReady&&displayedLocation.equals(location())&&displayedActor.equals(actor);
    ContentRequest query=query(route());
    navigationLoading=true;
    LinearLayout body;
    if(retain) {body=Ui.col(this);refreshBanner=body;content.addView(body,0);TextView refresh=Ui.text(this,"更新中",12,Design.MUTED);body.addView(refresh);}
    else {
      contentReady=false;shell(true);buildHeader();
      body=catalog?LoadingScreen.begin(this,content,target):content;
      if(!catalog)body.addView(Ui.text(this,"加载中…",16,Design.MUTED));
      else navigationHandler.postDelayed(()->{if(ticket==generation&&epoch==navigationEpoch&&navigationLoading)LoadingScreen.skeleton(this,body,target.kind().equals("dishes"));},180);
    }
    navigationHandler.postDelayed(()->{
      if(ticket!=generation||epoch!=navigationEpoch||!navigationLoading)return;
      TextView slow=Ui.text(this,"加载较慢，请稍候",12,Design.MUTED);slow.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);body.addView(slow,0);
    },3000);
    navigationHandler.postDelayed(()->{
      if(ticket!=generation||epoch!=navigationEpoch||!navigationLoading)return;
      cancelNavigation();contentReady=retain;pageFailed=true;
      if(retain){refreshBanner=body;content.addView(body,0);LoadingScreen.failure(this,body);}
      else if(catalog)LoadingScreen.failure(this,body);
      else {content.removeAllViews();Ui.empty(content,"加载失败","");Ui.action(content,"重试",true,this::render);}
    },15000);
    navigationRead=new java.util.concurrent.FutureTask<>(() -> {
      RuntimeException error=null;try {repo.refresh(query);}catch(RuntimeException e){error=e;}
      RuntimeException failure=error;
      runOnUiThread(() -> {
        if(isDestroyed()||ticket!=generation||epoch!=navigationEpoch||!foreground) return;
        cancelNavigation();
        if(repo.currentUser()==null) {clearNavigation();showAuth(false);message("请重新登录");return;}
        if(!actor.equals(actor())){Ui.clearImages();displayedActor="";}
        if(tab.equals("admin")&&!repo.currentUser().isAdmin()) {tab="dining";render();return;}
        if(failure!=null) {contentReady=false;pageFailed=true;if(retain&&(!(failure instanceof RepositoryException r)||r.status()==0||r.status()>=500)){contentReady=true;refreshBanner=body;content.addView(body,0);LoadingScreen.failure(this,body);}else if(retain){content.removeAllViews();LoadingScreen.failure(this,LoadingScreen.begin(this,content,target));}else if(catalog)LoadingScreen.failure(this,body);else {content.removeAllViews();Ui.empty(content,"加载失败",safeMessage(failure));Ui.action(content,"重试",true,this::render);}return;}
        renderContent();
        if(catalog&&!retain&&android.animation.ValueAnimator.areAnimatorsEnabled()){content.setAlpha(.5f);content.animate().alpha(1).setDuration(150).start();}
      });
      return null;
    });
    worker.execute(navigationRead);
  }
  private void cancelNavigation(){navigationEpoch++;navigationHandler.removeCallbacksAndMessages(null);if(navigationRead!=null){navigationRead.cancel(true);navigationRead=null;}navigationLoading=false;if(refreshBanner!=null&&refreshBanner.getParent() instanceof android.view.ViewGroup parent)parent.removeView(refreshBanner);refreshBanner=null;}
  boolean isPageLoading(){return navigationLoading;}

  private ContentRequest query(Route route) {
    ContentRequest.Query q=switch(route.kind()) {
      case "dining" -> ContentRequest.Query.HALLS;
      case "stalls","submit-stall" -> ContentRequest.Query.STALLS;
      case "dishes","submit-dish" -> ContentRequest.Query.DISHES;
      case "detail","gallery","upload-photo","review" -> ContentRequest.Query.DISH_DETAIL;
      case "community" -> ContentRequest.Query.COMMUNITY_FEED;
      case "community-detail" -> ContentRequest.Query.COMMUNITY_DETAIL;
      case "community-mine" -> ContentRequest.Query.MY_COMMUNITY_POSTS;
      case "community-favorites" -> ContentRequest.Query.MY_COMMUNITY_FAVORITES;
      case "inbox" -> ContentRequest.Query.NOTICES;
      case "admin-community" -> ContentRequest.Query.COMMUNITY_MODERATION;
      case "admin-community-detail" -> ContentRequest.Query.COMMUNITY_MODERATION_DETAIL;
      case "mine-reviews" -> ContentRequest.Query.MY_REVIEWS;
      case "review-replies","reply-create" -> ContentRequest.Query.REVIEW_REPLIES;
      case "mine-review-replies" -> ContentRequest.Query.MY_REVIEW_REPLIES;
      case "mine-submissions" -> ContentRequest.Query.MY_SUBMISSIONS;
      case "admin","admin-audit" -> ContentRequest.Query.MODERATION;
      case "admin-data","admin-new" -> ContentRequest.Query.CATALOG;
      case "admin-edit" -> ContentRequest.Query.CATALOG_ITEM;
      case "admin-reviews" -> ContentRequest.Query.ADMIN_REVIEWS;
      case "admin-users" -> ContentRequest.Query.ADMIN_USERS;
      case "admin-logs" -> ContentRequest.Query.AUDITS;
      default -> ContentRequest.Query.PROFILE;
    };
    return new ContentRequest(q,route.id());
  }

  void redraw() { rememberScroll();renderContent(); }

  private void renderContent() {
    shell(true);
    displayedLocation=location();
    displayedActor=actor();contentReady=true;buildHeader();
    try {
      if (stacks.get(tab).size() > 1&&!LoadingScreen.catalog(route())) Ui.decorateSubpage(content, route().kind().hashCode());
      Screen s = screens.get(route().kind());
      if (s == null) content.addView(Ui.title(this, tab.equals("profile") ? "我的" : tab.equals("admin") ? "管理" : tab.equals("community") ? "社区" : "食堂", 30));
      else s.show(this, content, route());
    } catch (RuntimeException e) {
      contentReady=false;Ui.empty(content, "页面加载失败", e.getMessage() == null ? "请重试" : e.getMessage());
      Ui.action(content, "重新加载", true, this::render);
    }
    int offset = scrollPositions.getInt(location(), 0);
    scroll.post(() -> scroll.scrollTo(0, offset));
    inboxPoll.post(this::drainNotices);
  }
  private void buildHeader() {
    LinearLayout bar = Ui.row(this);
    header.addView(bar, new LinearLayout.LayoutParams(-1, -2));
    TextView back = Ui.button(this, "←", false, this::back);
    back.setContentDescription("返回上一级");
    if (stacks.get(tab).size() > 1)
      bar.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
    TextView brand = Ui.title(this, "北交干饭吧", 22);
    brand.setSingleLine(true);
    brand.setEllipsize(android.text.TextUtils.TruncateAt.END);
    brand.setGravity(Gravity.CENTER_VERTICAL);
    bar.addView(brand, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
    TextView home = Ui.button(this, "⌂", false, this::home);
    home.setContentDescription("回到食堂首页");
    bar.addView(home, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
  }

  private void shell(boolean navigation) {
    root = Ui.col(this);
    root.setBackgroundColor(Design.BAR);
    root.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          int left, top, right, bottom;
          if (android.os.Build.VERSION.SDK_INT >= 30) {
            android.graphics.Insets sys =
                insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
            left = sys.left;
            top = sys.top;
            right = sys.right;
            bottom = sys.bottom;
          } else {
            left = insets.getSystemWindowInsetLeft();
            top = insets.getSystemWindowInsetTop();
            right = insets.getSystemWindowInsetRight();
            bottom = insets.getSystemWindowInsetBottom();
          }
          root.setPadding(left, top, right, bottom);
          return insets;
        });
    if (android.os.Build.VERSION.SDK_INT >= 30)
      getWindow()
          .getDecorView()
          .getWindowInsetsController()
          .setSystemBarsAppearance(
              WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                  | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
              WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                  | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
    header = Ui.col(this);
    header.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 8));
    root.addView(header, new LinearLayout.LayoutParams(-1, -2));
    View rule = new View(this);
    rule.setBackgroundColor(Design.BORDER);
    root.addView(rule, new LinearLayout.LayoutParams(-1, Ui.dp(this, 1)));
    scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    content = Ui.col(this);
    content.setBackgroundColor(Design.PAPER);
    content.setPadding(Ui.dp(this, 14), Ui.dp(this, 24), Ui.dp(this, 14), Ui.dp(this, 24));
    scroll.addView(content);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    if (navigation) {
      LinearLayout nav = Ui.row(this);
      nav.setBackground(Ui.shape(this, Design.PAPER, Design.BORDER, 0));
      addTab(nav, "食堂", "dining");
      addTab(nav, "社区", "community");
      if (repo.currentUser().isAdmin()) addTab(nav, "管理", "admin");
      addTab(nav, "我的", "profile");
      root.addView(nav, new LinearLayout.LayoutParams(-1, -2));
    }
    setContentView(root);
    root.requestApplyInsets();
  }

  private void addTab(LinearLayout nav, String label, String key) {
    TextView b = Ui.button(this, label, false, () -> selectTab(key));
    b.setTextColor(tab.equals(key) ? Design.RED : Design.MUTED);
    b.setBackground(Ui.ripple(this, tab.equals(key) ? Design.ALT : Design.PAPER, 0, 0));
    b.setContentDescription(label + (tab.equals(key) ? "，已选中" : ""));
    b.setMinHeight(Ui.dp(this, 56));
    nav.addView(b, new LinearLayout.LayoutParams(0, -2, 1));
  }

  public void heading(LinearLayout page, String index, String title, String note) {
    page.addView(Ui.title(this, title, 30));
    Ui.gap(page, 8);
    if (!note.isBlank()) page.addView(Ui.note(this, note));
    Ui.gap(page, 24);
  }

  private String location() {
    return tab + ":" + route().kind() + ":" + route().id();
  }

  private void rememberScroll() {
    if (scroll != null && repo.currentUser() != null && displayedLocation.equals(location()))
      scrollPositions.putInt(location(), scroll.getScrollY());
  }

  public void message(String text) {
    if(currentToast!=null) currentToast.cancel();
    currentToast=Toast.makeText(this,text,Toast.LENGTH_SHORT);currentToast.show();
  }

  public String draftKey(String label) {
    return route().kind() + ":" + route().id() + ":" + label;
  }

  public void pickImage(String key) {
    pickingKey = key;
    startActivityForResult(imagePickerIntent(), 101);
  }

  public void pickCommunityPhotos() {
    pickingKey=draftKey("photos");
    android.content.Intent intent=imagePickerIntent();intent.putExtra(android.content.Intent.EXTRA_ALLOW_MULTIPLE,true);
    startActivityForResult(intent,102);
  }
  void discardPhoto(String path){worker.execute(()->PhotoImport.discard(this,path));}
  private void applyCommunityPhotos(String prefix,String paths){
    String base=prefix.substring(0,prefix.length()-"photos".length());
    for(String path:paths.split("\n"))for(int i=0;i<9;i++){
      String key=base+"photo:"+i+":image";
      if(drafts.getString(key,"").isBlank()){drafts.putString(key,path);break;}
    }
  }

  static android.content.Intent imagePickerIntent() {
    android.content.Intent intent =
        new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
    intent.setType("image/*");
    intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
    return intent;
  }

  @Override
  protected void onActivityResult(int request, int result, android.content.Intent data) {
    super.onActivityResult(request, result, data);
    if(request==102 && result==RESULT_OK && data!=null && pickingKey!=null){
      String prefix=pickingKey;String base=prefix.substring(0,prefix.length()-"photos".length());
      int count=0;for(int i=0;i<9;i++)if(!drafts.getString(base+"photo:"+i+":image","").isBlank())count++;
      java.util.List<android.net.Uri> selected=new java.util.ArrayList<>();
      if(data.getClipData()!=null)for(int i=0;i<data.getClipData().getItemCount()&&selected.size()<9-count;i++)selected.add(data.getClipData().getItemAt(i).getUri());
      else if(data.getData()!=null&&count<9)selected.add(data.getData());
      if(selected.isEmpty())return;
      startOperation(null,null,"导入社区图片",prefix,()->{
        java.util.List<String> imported=new java.util.ArrayList<>();
        try{for(var uri:selected)imported.add(PhotoImport.prepare(this,uri));return String.join("\n",imported);}
        catch(RuntimeException e){for(String path:imported)PhotoImport.discard(this,path);throw e;}
      },paths->{rememberScroll();applyCommunityPhotos(prefix,paths);renderContent();});
      return;
    }
    if (request == 101
        && result == RESULT_OK
        && data != null
        && data.getData() != null
        && pickingKey != null) {
      String selectedKey=pickingKey;
      android.net.Uri selected=data.getData();
      startOperation(null,null,"导入图片",selectedKey,() -> PhotoImport.prepare(this,selected),path -> {
        rememberScroll();String previous=drafts.getString(selectedKey,"");drafts.putString(selectedKey,path);
        worker.execute(() -> PhotoImport.discard(this,previous));renderContent();
      });
    }
  }

  public void safely(Runnable action) {
    try {
      action.run();
    } catch (RuntimeException e) {
      message(e.getMessage() == null ? "操作失败，请重试" : e.getMessage());
    }
  }

  /** Capture field values before calling. Work runs on the app's serial worker; callbacks on UI. */
  public void request(View trigger,TextView error,Runnable work,Runnable success) {
    String action=trigger instanceof TextView text?text.getText().toString():"";
    startOperation(trigger,error,action,null,() -> {work.run();return null;},ignored -> success.run());
  }
  boolean operationInFlight() { return writeInFlight; }
  /** Coalesce reads per resource, without dropping other floors or blocking navigation/writes. */
  void read(String key,View trigger,TextView error,Runnable work,Runnable success,Runnable finished) {
    if(trigger!=null) trigger.setEnabled(false);
    if(error!=null) error.setText("");
    ReadListener listener=new ReadListener(generation,trigger,error,success,finished);
    List<ReadListener> existing=reads.get(key);
    if(existing!=null) {existing.add(listener);return;}
    List<ReadListener> listeners=new ArrayList<>();listeners.add(listener);reads.put(key,listeners);
    worker.execute(() -> {
      RuntimeException failure=null;try {work.run();}catch(RuntimeException e) {failure=e;}
      RuntimeException result=failure;
      runOnUiThread(() -> {
        if(isDestroyed()) {reads.remove(key);return;}
        Runnable complete=() -> {
          List<ReadListener> waiting=reads.remove(key);
          if(waiting==null) return;
          for(ReadListener target:waiting) {
            if(target.trigger()!=null) target.trigger().setEnabled(true);
            target.finished().run();
            if(target.generation()!=generation) continue;
            if(repo.currentUser()==null) {clearNavigation();showAuth(false);message("登录已失效，请重新登录");break;}
            if(result==null) target.success().run();
            else if(target.error()!=null) target.error().setText(safeMessage(result));
            else message(safeMessage(result));
          }
        };
        if(stateSaved) deferredReads.add(complete);else complete.run();
      });
    });
  }
  private void startOperation(View trigger,TextView error,String action,String imageDraft,Supplier<String> work,Consumer<String> success) {
    if(writeInFlight) return;
    writeInFlight=true;if(trigger!=null) trigger.setEnabled(false);if(error!=null) error.setText("");
    int ticket=generation;boolean loggedIn=repo.currentUser()!=null;
    OperationStore.Task task=OPERATIONS.start(worker,route(),action,imageDraft,work);pendingOperation=task.id();
    task.result().thenAccept(outcome -> {
      RuntimeException failure=outcome.failure();
      runOnUiThread(() -> {
        if(isDestroyed()) {if(isFinishing()) OPERATIONS.forget(task.id());return;}
        Runnable complete=() -> {
          OPERATIONS.forget(task.id());pendingOperation=0;
          writeInFlight=false;if(trigger!=null) trigger.setEnabled(true);
          if(ticket!=generation) return;
          if(failure==null) success.accept(outcome.value());
          else if(loggedIn&&repo.currentUser()==null) {
            clearNavigation();showAuth(false);message("登录已失效，请重新登录");
          } else if(error!=null) error.setText(safeMessage(failure));else message(safeMessage(failure));
        };
        if(stateSaved) deferredCompletion=complete;else complete.run();
      });
    });
  }
  private void recoverOperation() {
    long id=pendingOperation;pendingOperation=0;writeInFlight=false;
    OperationStore.Task task=OPERATIONS.find(id);
    if(task==null) {
      if(repo.currentUser()==null) showAuth(authRegistration);else openApp();
      message("上次操作结果待核对，请先检查记录");return;
    }
    // The shared serial worker runs session restoration after the original operation.
    OperationStore.Outcome outcome=task.result().getNow(null);OPERATIONS.forget(id);
    if(outcome==null) {if(repo.currentUser()==null) showAuth(authRegistration);else openApp();message("请检查上次操作记录");return;}
    if(outcome.failure()!=null) {
      if(repo.currentUser()==null) showAuth(authRegistration);else openApp();message(safeMessage(outcome.failure()));return;
    }
    if(task.action().equals("注册")) {showAuth(false);message("注册成功，请登录");return;}
    if(repo.currentUser()==null) {signedOut();return;}
    if(task.origin().kind().equals("community-detail")&&Set.of("提交评论","提交回复").contains(task.action())){
      String prefix=task.origin().kind()+":"+task.origin().id()+":";
      for(String key:List.of("评论内容","replyRoot","replyTarget","replyName","commentPayload","commentRequestId"))drafts.remove(prefix+key);
      message("已提交审核，通过前仅自己可见");
    }
    if(task.action().equals("发布回复")) {EmbeddedReviewReplies.completeSubmission(this);message("回复已提交，审核通过后公开");}
    if(task.imageDraft()!=null && task.imageDraft().endsWith(":photos")) {applyCommunityPhotos(task.imageDraft(),outcome.value());}
    else if(task.imageDraft()!=null) {
      String previous=drafts.getString(task.imageDraft(),"");drafts.putString(task.imageDraft(),outcome.value());
      worker.execute(() -> PhotoImport.discard(this,previous));
    }
    else if(Set.of("review","upload-photo","submit-dish","submit-stall","community-create","admin-new","admin-edit").contains(task.origin().kind())) {
      clearDraft(task.origin());if(route().equals(task.origin())&&stacks.get(tab).size()>1) stacks.get(tab).remove(stacks.get(tab).size()-1);
    }
    openApp();
  }
  private String safeMessage(RuntimeException e) {
    String message=e.getMessage()==null?"操作失败，请重试":e.getMessage();
    return e instanceof RepositoryException failure&&failure.retryAfterSeconds()>0?message+"；"+failure.retryAfterSeconds()+"秒后重试":message;
  }
  public void clearDraft(Route route) {
    String prefix=route.kind()+":"+route.id()+":";
    for(String key:new HashSet<>(drafts.keySet())) if(key.startsWith(prefix)) {
      if(key.endsWith(":image")) {String photo=drafts.getString(key,"");worker.execute(() -> PhotoImport.discard(this,photo));}
      drafts.remove(key);
    }
  }

  public void logout() {
    request(null,null,repo::logout,() -> {clearNavigation();showAuth(false);});
  }
  public void signedOut() {clearNavigation();showAuth(false);}
  private void clearNavigation() {
    cancelNavigation();contentReady=false;displayedActor="";inboxEpoch++;pendingNotices.clear();if(inboxDialog!=null)inboxDialog.dismiss();
    inboxPoll.removeCallbacks(inboxPollTask);inboxSeen.clear();inboxBaseline=false;
    for(String key:drafts.keySet()) if(key.endsWith(":image")) {
      String photo=drafts.getString(key,"");worker.execute(() -> PhotoImport.discard(this,photo));
    }
    drafts.clear();
    scrollPositions.clear();
    for (String key : stacks.keySet()) {
      stacks.get(key).clear();
      stacks.get(key).add(new Route(key, ""));
    }
    tab = "dining";
    displayedLocation="";
    Ui.clearImages();
  }
  @Override protected void onResume() {
    super.onResume();stateSaved=false;foreground=true;
    Ui.resumeImages();
    if(deferredCompletion!=null) {Runnable callback=deferredCompletion;deferredCompletion=null;callback.run();}
    List<Runnable> callbacks=new ArrayList<>(deferredReads);deferredReads.clear();
    inboxPoll.removeCallbacks(inboxPollTask);
    if(repo!=null&&repo.currentUser()!=null) inboxPoll.postDelayed(inboxPollTask,60_000);
    for(Runnable callback:callbacks) callback.run();
    if(resumeNavigation&&repo!=null&&repo.currentUser()!=null){resumeNavigation=false;render();}
    inboxPoll.post(this::drainNotices);
  }
  @Override protected void onPause() { super.onPause();foreground=false;inboxEpoch++;resumeNavigation=navigationLoading;cancelNavigation();Ui.pauseImages();inboxPoll.removeCallbacks(inboxPollTask); }
  @Override public void onTrimMemory(int level) {super.onTrimMemory(level);if(level>=android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)Ui.clearImages();}
  @Override protected void onDestroy() {++generation;cancelNavigation();inboxPoll.removeCallbacksAndMessages(null);if(inboxDialog!=null)inboxDialog.dismiss();deferredCompletion=null;deferredReads.clear();reads.clear();super.onDestroy();}
}
