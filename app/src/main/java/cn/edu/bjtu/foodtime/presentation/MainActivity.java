package cn.edu.bjtu.foodtime.presentation;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import cn.edu.bjtu.foodtime.domain.repository.FoodtimeRepository;
import cn.edu.bjtu.foodtime.domain.repository.RepositoryProvider;
import cn.edu.bjtu.foodtime.domain.repository.ContentRequest;
import cn.edu.bjtu.foodtime.domain.repository.RepositoryException;
import cn.edu.bjtu.foodtime.domain.model.Models.AppRelease;
import cn.edu.bjtu.foodtime.domain.model.Models.Conversation;
import cn.edu.bjtu.foodtime.domain.model.Models.CommunityPost;
import cn.edu.bjtu.foodtime.domain.model.Models.Notice;
import cn.edu.bjtu.foodtime.domain.model.CommunityShareLink;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.function.*;

public class MainActivity extends Activity {
  public FoodtimeRepository repo;
  FeedbackDocuments feedbackDocuments;
  AlertDialog accountActionConfirmation;
  private boolean releaseChecked, releaseChecking, updateCheckPending;
  private AlertDialog updatePrompt, updateProgress;
  private UpdateDownloader activeDownloader;

  public record Route(String kind, String id) {}

  public interface Screen {
    void show(MainActivity activity, LinearLayout page, Route route);
  }

  final Map<String, Screen> screens = new HashMap<>();
  final Map<String, ArrayList<Route>> stacks = new HashMap<>();
  String tab = "dining";
  LinearLayout root, content, header;
  ScrollView scroll;
  private CommunityPager communityPager;
  final Bundle drafts = new Bundle();
  final Bundle scrollPositions = new Bundle();
  String pickingKey;
  private String avatarPickingActor="";
  private Executor worker;
  private Executor inboxWorker;
  // Best-effort statistics must not hold up comments, navigation or sending a message.
  private final java.util.concurrent.ExecutorService statisticsWorker=java.util.concurrent.Executors.newSingleThreadExecutor();
  // A cancelled socket can still be unwinding. Leave a second slot for the new screen.
  private final java.util.concurrent.ThreadPoolExecutor navigationWorker =
      new java.util.concurrent.ThreadPoolExecutor(2,2,0,java.util.concurrent.TimeUnit.SECONDS,
          new java.util.concurrent.LinkedBlockingQueue<>());
  private java.util.concurrent.FutureTask<Void> navigationRead;
  private final android.os.Handler navigationHandler=new android.os.Handler(android.os.Looper.getMainLooper());
  private boolean navigationLoading,contentReady,foreground,resumeNavigation,pageFailed;
  private LinearLayout refreshBanner;
  private String displayedActor="";
  private int navigationEpoch;
  private LoginNoticeController loginNotices;
  private LoginNoticeDialog loginNoticeDialog;
  private int generation;
  private boolean writeInFlight;
  private String displayedLocation="";
  private Runnable contentRefresh;
  private Runnable communityCommentsRefresh;
  private LinearLayout chatBar;
  private String chatOriginTab;
  private Toast currentToast;
  private static final OperationStore OPERATIONS=new OperationStore();
  private long pendingOperation;
  private boolean authRegistration;
  private boolean stateSaved;
  private Runnable deferredCompletion;
  private final Map<String,List<ReadListener>> reads=new HashMap<>();
  private final List<Runnable> deferredReads=new ArrayList<>();
  private final android.os.Handler noticeUi=new android.os.Handler(android.os.Looper.getMainLooper());
  private String noticeErrorShown="";
  private static final int REQUEST_PRIVATE_MESSAGE_NOTIFICATION = 1092;
  private String incomingPost = "";
  private boolean incomingInbox;
  private String actor(){var user=repo==null?null:repo.currentUser();return user==null?"":user.id()+":"+user.role();}
  private void drainNotices() {
    if(loginNotices==null||updateCheckPending)return;
    if(loginNotices.pending==null) {
      if(loginNoticeDialog!=null){loginNoticeDialog.dialog.dismiss();loginNoticeDialog=null;}
      if(foreground&&!loginNotices.checkError.isEmpty()&&!noticeErrorShown.equals(loginNotices.checkError)) {
        noticeErrorShown=loginNotices.checkError;message(loginNotices.checkError);
      }
      return;
    }
    // A displayed Dialog owns window focus; its save/error state must still update.
    if(loginNoticeDialog!=null){loginNoticeDialog.update();return;}
    if(!foreground||!contentReady||pageFailed||navigationLoading||stateSaved||writeInFlight||!hasWindowFocus()||repo.currentUser()==null)return;
    loginNoticeDialog=new LoginNoticeDialog(this,loginNotices);loginNoticeDialog.show();
  }
  void confirmLoginNotice(){loginNotices.confirm(repo,inboxWorker);}
  boolean loginNoticeFailed(){return loginNotices!=null&&!loginNotices.checkError.isEmpty();}
  void retryLoginNotice(){noticeErrorShown="";loginNotices.retry(repo,inboxWorker);}
  @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused)drainNotices();}
  @Override public Object onRetainNonConfigurationInstance(){return loginNotices;}
  private record ReadListener(int generation,View trigger,TextView error,Runnable success,Runnable finished) {}

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    Design.init(this);
    repo = ((RepositoryProvider) getApplication()).repository();
    feedbackDocuments = new FeedbackDocuments(this,state);
    worker = ((RepositoryProvider) getApplication()).workExecutor();
    inboxWorker = ((RepositoryProvider) getApplication()).backgroundExecutor();
    loginNotices=getLastNonConfigurationInstance() instanceof LoginNoticeController retained?retained:new LoginNoticeController();
    loginNotices.attach(this,this::drainNotices);
    registerScreens();
    for (String key : List.of("dining", "community", "profile", "admin", "dashboard")) {
      String root = key.equals("dashboard") ? "admin-dashboard" : key;
      stacks.put(key, new ArrayList<>(List.of(new Route(root, ""))));
    }
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
      avatarPickingActor = state.getString("avatarPickingActor", "");
      Bundle positions = state.getBundle("scrollPositions");
      if (positions != null) scrollPositions.putAll(positions);
      incomingPost = state.getString("incomingPost", "");
      incomingInbox = state.getBoolean("incomingInbox");
    } else {
      captureIncoming(getIntent());
    }
    restore();
  }

  @Override protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    captureIncoming(intent);
    if (contentReady && consumeIncoming()) render(true);
  }

  private void captureIncoming(Intent intent) {
    if (intent == null) return;
    if (PrivateMessageReminders.OPEN_INBOX.equals(intent.getAction())) {
      incomingInbox = true;
      incomingPost = "";
    } else if (Intent.ACTION_VIEW.equals(intent.getAction())) {
      String id = CommunityShareLink.postId(intent.getDataString());
      if (!id.isEmpty()) {
        incomingPost = id;
        incomingInbox = false;
      }
    }
  }

  private boolean consumeIncoming() {
    if (repo.currentUser() == null || writeInFlight || pendingOperation != 0
        || (!incomingInbox && incomingPost.isEmpty())) return false;
    if (contentReady) rememberScroll();
    Route target;
    if (incomingInbox) {
      tab = "profile";
      target = new Route("inbox", "");
      drafts.putString("inbox::tab", "chat");
    } else {
      tab = "community";
      target = new Route("community-detail", incomingPost);
    }
    if (!target.equals(route())) stacks.get(tab).add(target);
    incomingInbox = false;
    incomingPost = "";
    return true;
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
        else if(repo.currentUser()==null) {showAuth(authRegistration);checkAppRelease(false);}else openApp();
      });
    });
  }

  void registerScreens() {
    screens.put("venue-reviews",VenueScreen::reviews);
    screens.put("venue-write",VenueScreen::write);
    screens.put("venue-mine",VenueScreen::mine);
    screens.put("dining", DiningScreen::halls);
    screens.put("dish-search", DishSearchScreen::show);
    screens.put("favorite-dishes", FavoriteDishesScreen::show);
    screens.put("correction", CorrectionScreen::submit);
    screens.put("my-corrections", CorrectionScreen::mine);
    screens.put("admin-corrections", CorrectionScreen::admin);
    screens.put("community", CommunityScreen::feed);
    screens.put("community-following", CommunityScreen::feed);
    screens.put("community-search", CommunityDiscoveryScreen::show);
    screens.put("community-author", CommunityDiscoveryScreen::show);
    screens.put("community-detail", CommunityScreen::detail);
    screens.put("community-create", CommunityScreen::create);
    screens.put("community-mine", CommunityScreen::collection);
    screens.put("inbox", InboxScreen::inbox);
    screens.put("account-lifecycle",AccountLifecycleScreen::show);
    screens.put("direct-chat", ChatScreen::chat);
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
    screens.put("following", FollowListScreen::following);
    screens.put("user-search", UserSearchScreen::show);
    screens.put("followers", FollowListScreen::followers);
    screens.put("blocked-users", BlockedUsersScreen::show);
    screens.put("profile", ProfileScreen::profile);
    screens.put("profile-edit", ProfileScreen::edit);
    screens.put("bind-school-email", SchoolEmailScreen::binder);
    screens.put("avatar-crop", AvatarCropScreen::show);

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
    screens.put("admin-notices", AdminNoticesScreen::settings);
    screens.put("admin-reports", ReportsScreen::show);
    screens.put("feedback", FeedbackScreen::list);
    screens.put("feedback-detail", FeedbackScreen::detail);
    screens.put("admin-feedback", FeedbackScreen::list);
    screens.put("admin-feedback-detail", FeedbackScreen::detail);
    screens.put("admin-notice-create", AdminNoticesScreen::create);
    screens.put("admin-users", AdminScreen::users);
    screens.put("admin-dashboard", AdminDashboardScreen::show);
    screens.put("admin-logs", AdminScreen::logs);
  }

  private PasswordRecoveryScreen passwordRecovery;
  public void showPasswordRecovery() {
    if(writeInFlight){message("正在提交，请稍候");return;}
    cancelNavigation();contentReady=false;++generation;shell(false);
    passwordRecovery=new PasswordRecoveryScreen(this,content);
  }
  public void showAuth(boolean register) {
    if(writeInFlight) {message("正在提交，请稍候");return;}
    if(passwordRecovery!=null){passwordRecovery.close();passwordRecovery=null;}
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
    if (tab.equals("dashboard") && !repo.currentUser().role().equals("superadmin")) tab = "dining";
    consumeIncoming();
    if (PrivateMessageReminders.configured(this, repo.currentUser().id())) {
      PrivateMessageReminders.ensureScheduled(this);
    }
    loginNotices.check(repo,inboxWorker);
    render();
    updateCheckPending = true;
    checkAppRelease(false);
  }

  /** 应用内更新检查：开屏自动一次，个人页可手动再次触发。 */
  void checkAppRelease(boolean manual) {
    if (releaseChecking) return;
    if (!manual && releaseChecked) { maybeReleaseUpdateGate(); return; }
    releaseChecking = true;
    final AppRelease[] release = {null};
    final RuntimeException[] failure = {null};
    // Release metadata is public and remains valid when login changes the page generation.
    // A discarded page callback must never leave the announcement gate permanently closed.
    worker.execute(() -> {
          try { release[0] = repo.appRelease(); }
          catch (RuntimeException error) { failure[0] = error; }
          runOnUiThread(() -> {
            if(isDestroyed())return;
            Runnable complete=() -> {
          releaseChecking = false;
          releaseChecked = true;
          if (failure[0] != null) {
            if (manual) message(safeMessage(failure[0]));
            if (!manual) maybeReleaseUpdateGate();
            return;
          }
          AppRelease info = release[0];
          if (info == null || info.versionCode() <= cn.edu.bjtu.foodtime.BuildConfig.VERSION_CODE) {
            if (manual) message("已是最新版本 v" + cn.edu.bjtu.foodtime.BuildConfig.VERSION_NAME);
            if (!manual) maybeReleaseUpdateGate();
            return;
          }
          showUpdateDialog(info);
            };
            if(stateSaved)deferredReads.add(complete);else complete.run();
          });
        });
  }

  /** 更新提示优先于公告：更新弹窗和下载进度都结束前，不弹公告。 */
  private void maybeReleaseUpdateGate() {
    if (updatePrompt != null || updateProgress != null) return;
    updateCheckPending = false;
    drainNotices();
  }

  private void showUpdateDialog(AppRelease info) {
    if (isFinishing() || isDestroyed()) { maybeReleaseUpdateGate(); return; }
    boolean forced = info.minVersionCode() > cn.edu.bjtu.foodtime.BuildConfig.VERSION_CODE;
    String notes = info.notes() == null || info.notes().isBlank()
        ? "建议更新到最新版本，获得最新功能与修复。" : info.notes();
    AlertDialog dialog = new AlertDialog.Builder(this)
        .setTitle("发现新版本 v" + info.versionName())
        .setMessage(notes)
        .setCancelable(!forced)
        .setPositiveButton("立即更新", (prompt, which) -> startUpdate(info, forced))
        .setNegativeButton("稍后更新", null)
        .create();
    dialog.setOnDismissListener(closed -> { updatePrompt = null; maybeReleaseUpdateGate(); });
    dialog.setCanceledOnTouchOutside(!forced);
    updatePrompt = dialog;
    dialog.show();
  }

  private void startUpdate(AppRelease info, boolean forced) {
    final UpdateDownloader downloader = new UpdateDownloader(this, worker);
    activeDownloader = downloader;
    final ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
    bar.setMax(100); bar.setIndeterminate(true);
    LinearLayout body = new LinearLayout(this);
    body.setOrientation(LinearLayout.VERTICAL);
    int padding = Ui.dp(this, 20);
    body.setPadding(padding, padding, padding, 0);
    TextView label = Ui.text(this, "正在准备更新…", 14, Design.MUTED);
    TextView detail = Ui.text(this, "正在检查可用补丁…", 12, Design.MUTED);
    detail.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    body.addView(label); Ui.gap(body, 8); body.addView(bar); Ui.gap(body, 6); body.addView(detail);
    AlertDialog.Builder builder = new AlertDialog.Builder(this)
        .setTitle("正在更新到 v" + info.versionName())
        .setView(body)
        .setCancelable(!forced);
    if (!forced) builder.setNegativeButton("取消", (progress, which) -> downloader.cancel());
    AlertDialog dialog = builder.create();
    dialog.setOnDismissListener(closed -> { updateProgress = null; maybeReleaseUpdateGate(); });
    updateProgress = dialog;
    dialog.show();
    final boolean[] patchPlan = {false};
    final long[] planSize = {0};
    downloader.start(info, new UpdateDownloader.Listener() {
      @Override public void plan(boolean patch, long size) {
        if (updateProgress != dialog) return;
        patchPlan[0] = patch;
        planSize[0] = size;
        bar.setIndeterminate(true);
        bar.setProgress(0);
        label.setText(patch ? "正在下载增量补丁" : "正在下载完整安装包");
        detail.setText((patch ? "增量补丁" : "完整安装包") + (size > 0 ? " · " + formatBytes(size) : ""));
      }
      @Override public void progress(int percent, String text, long downloaded, long total, long bytesPerSecond) {
        if (updateProgress != dialog) return;
        if (percent < 0) bar.setIndeterminate(true);
        else { bar.setIndeterminate(false); bar.setProgress(percent); }
        label.setText(percent < 0 ? text : text + " " + percent + "%");
        long knownTotal = total > 0 ? total : planSize[0];
        StringBuilder info = new StringBuilder(patchPlan[0] ? "增量补丁" : "完整安装包");
        if (knownTotal > 0) info.append(" ").append(formatBytes(downloaded)).append(" / ").append(formatBytes(knownTotal));
        else if (downloaded > 0) info.append(" 已下载 ").append(formatBytes(downloaded));
        if (bytesPerSecond > 0) info.append(" · ").append(formatSpeed(bytesPerSecond));
        detail.setText(info);
      }
      @Override public void ready(java.io.File apk) {
        if (isFinishing() || isDestroyed() || updateProgress != dialog) return;
        updateProgress = null;
        dialog.dismiss();
        try { UpdateDownloader.install(MainActivity.this, apk); }
        catch (RuntimeException unavailable) {
          message("无法启动安装器，请允许本应用安装未知来源应用后重试");
        }
      }
      @Override public void failed(String text) {
        if (isFinishing() || isDestroyed() || updateProgress != dialog) return;
        updateProgress = null;
        dialog.dismiss();
        new AlertDialog.Builder(MainActivity.this)
            .setTitle("更新失败").setMessage(text)
            .setPositiveButton("重试", (retry, which) -> startUpdate(info, forced))
            .setNegativeButton("关闭", null).show();
      }
    });
  }

  public void go(String kind, String id) {
    if(writeInFlight) {message("正在提交，请稍候");return;}
    rememberScroll();
    if(kind.equals("direct-chat")) chatOriginTab=tab;
    stacks.get(tab).add(new Route(kind, id));
    if(kind.equals("community-detail")) {
      drafts.putString(draftKey("view-request"),java.util.UUID.randomUUID().toString());
      drafts.remove(draftKey("view-recorded")); drafts.remove(draftKey("view-count"));
    }
    render();
  }

  void replaceRoute(String kind,String id){
    if(writeInFlight){message("正在提交，请稍候");return;}
    rememberScroll();ArrayList<Route> stack=stacks.get(tab);stack.set(stack.size()-1,new Route(kind,id));render();
  }

  boolean isCommunityHub() {
    return tab.equals("community") && stacks.get(tab).size() == 1
        && CommunityPager.isSection(route().kind());
  }

  void restoreCommunityScroll(ScrollView view, String section) {
    int offset = scrollPositions.getInt("community:" + section + ":", 0);
    if (offset == 0) return;
    view.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
      @Override public void onLayoutChange(View v, int left, int top, int right, int bottom,
          int oldLeft, int oldTop, int oldRight, int oldBottom) {
        view.removeOnLayoutChangeListener(this);
        view.scrollTo(0, offset);
      }
    });
  }

  void commitCommunityPage(CommunityPager owner, String section, CommunityPager.Page page) {
    if (communityPager != owner || !isCommunityHub()) return;
    rememberScroll();
    cancelNavigation();
    stacks.get(tab).set(0, new Route(section, ""));
    scroll = page.scroll();
    content = page.content();
    contentRefresh = page.update();
    displayedLocation = location();
    render();
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
    if(passwordRecovery!=null){passwordRecovery.back();return;}
    if(route().kind().equals("avatar-crop")){
      discardPhoto(route().id());
      for(String key:List.of("cropZoom","cropX","cropY","cropRotation"))drafts.remove(draftKey(key));
      drafts.remove("avatarCropTarget");
    }
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
    state.putString("avatarPickingActor", avatarPickingActor);
    feedbackDocuments.save(state);
    state.putLong("pendingOperation",pendingOperation);
    state.putBoolean("authRegistration",authRegistration);
    state.putString("tab", tab);
    state.putString("incomingPost", incomingPost);
    state.putBoolean("incomingInbox", incomingInbox);
    for (String key : stacks.keySet()) {
      ArrayList<String> saved = new ArrayList<>();
      for (Route route : stacks.get(key)) saved.add(route.kind() + "|" + route.id());
      state.putStringArrayList(key, saved);
    }
  }

  public void render() { render(false); }

  /** Force the current page to refresh, used by the community pull gesture/refresh control. */
  public void refreshCurrent() { if(!navigationLoading) render(true); }

  private static final long COMMUNITY_FRESH_MS = 60 * 1000L;
  private final Map<String,Long> communityLoadedAt=new HashMap<>();
  private String communityLoadedFor="";

  private boolean freshCommunitySection(String kind) {
    Long loaded=communityLoadedAt.get(kind);
    return loaded!=null&&communityLoadedFor.equals(actor())
        &&android.os.SystemClock.elapsedRealtime()-loaded<COMMUNITY_FRESH_MS;
  }

  private boolean isImmediate(Route target) {
    // Catalog pages already have a dedicated skeleton; every other page paints its static shell first.
    return !LoadingScreen.catalog(target);
  }

  private void render(boolean force) {
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
    Route target=route();
    if(target.kind().equals("admin")&&!repo.currentUser().isAdmin()) {tab="dining";render();return;}
    if(target.kind().equals("admin-dashboard")&&!repo.currentUser().role().equals("superadmin")) {tab="dining";render();return;}
    ContentRequest query=query(target);
    if(query==null) { renderContent(); return; }
    boolean community=CommunityPager.isSection(target.kind());
    boolean keepCommunityPage = isCommunityHub() && contentReady && contentRefresh != null
        && displayedLocation.equals(location()) && displayedActor.equals(actor);
    boolean freshCommunity=community&&!force&&freshCommunitySection(target.kind());
    if(freshCommunity) {
      if (keepCommunityPage) refreshPage();
      else renderContent();
      return;
    }
    boolean catalog=LoadingScreen.catalog(target),dashboard=target.kind().equals("admin-dashboard");
    boolean immediate=isImmediate(target);
    boolean retain=(catalog||dashboard&&repo.adminDashboard()!=null)&&contentReady&&displayedLocation.equals(location())&&displayedActor.equals(actor);
    navigationLoading=true;
    LinearLayout[] bodyHolder={null};
    if(immediate) {
      // Static/cached shell paints first; live data is merged in after the read completes.
      if (!keepCommunityPage) renderContent();
      else refreshPage();
      if(dashboard)AdminDashboardScreen.refreshing(content,true);
      navigationHandler.postDelayed(()->{
        if(ticket!=generation||epoch!=navigationEpoch||!navigationLoading)return;
        cancelNavigation();pageFailed=true;refreshPage();message("加载超时，已保留当前页面");
      },15000);
    } else {
      if(dashboard&&retain)AdminDashboardScreen.refreshing(content,true);
      if(retain) {bodyHolder[0]=Ui.col(this);refreshBanner=bodyHolder[0];content.addView(bodyHolder[0],0);TextView refresh=Ui.text(this,"更新中",12,Design.MUTED);bodyHolder[0].addView(refresh);}
      else {
        contentReady=false;shell(true);buildHeader();
        bodyHolder[0]=catalog?LoadingScreen.begin(this,content,target):LoadingScreen.general(this,content,target);
        if(catalog)navigationHandler.postDelayed(()->{if(ticket==generation&&epoch==navigationEpoch&&navigationLoading)LoadingScreen.skeleton(this,bodyHolder[0],target.kind().equals("dishes"));},180);
      }
      final LinearLayout slowBody=bodyHolder[0];
      navigationHandler.postDelayed(()->{
        if(ticket!=generation||epoch!=navigationEpoch||!navigationLoading)return;
        TextView slow=Ui.text(this,"加载较慢，请稍候",12,Design.MUTED);slow.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);slowBody.addView(slow,0);
      },3000);
      navigationHandler.postDelayed(()->{
        if(ticket!=generation||epoch!=navigationEpoch||!navigationLoading)return;
        cancelNavigation();contentReady=retain;pageFailed=true;
        if(retain){refreshBanner=slowBody;content.addView(slowBody,0);LoadingScreen.failure(this,slowBody,dashboard?"刷新失败，显示上次数据":"加载失败");}
        else if(catalog)LoadingScreen.failure(this,slowBody);
        else LoadingScreen.failure(this,slowBody);
      },15000);
    }
    CommunityPost paintedPost=target.kind().equals("community-detail")?repo.communityPost(target.id()):null;
    navigationRead=new java.util.concurrent.FutureTask<>(() -> {
      RuntimeException error=null;try {repo.refresh(query);}catch(RuntimeException e){error=e;}
      RuntimeException failure=error;
      runOnUiThread(() -> {
        if(isDestroyed()||ticket!=generation||epoch!=navigationEpoch||!foreground) return;
        cancelNavigation();
        if(repo.currentUser()==null) {clearNavigation();showAuth(false);message("请重新登录");return;}
        if(!actor.equals(actor())){Ui.clearImages();displayedActor="";}
        if(tab.equals("admin")&&!repo.currentUser().isAdmin()) {tab="dining";render();return;}
        if(tab.equals("dashboard")&&!repo.currentUser().role().equals("superadmin")) {tab="dining";render();return;}
        if(failure!=null) {
          if(immediate) {pageFailed=true;refreshPage();message(safeMessage(failure));return;}
          contentReady=false;pageFailed=true;if(retain&&(!(failure instanceof RepositoryException r)||r.status()==0||r.status()>=500)){contentReady=true;refreshBanner=bodyHolder[0];content.addView(bodyHolder[0],0);LoadingScreen.failure(this,bodyHolder[0],dashboard?"刷新失败，显示上次数据":"加载失败");}else if(retain){content.removeAllViews();LoadingScreen.failure(this,catalog?LoadingScreen.begin(this,content,target):LoadingScreen.general(this,content,target),dashboard?failure.getMessage():"加载失败");}else LoadingScreen.failure(this,bodyHolder[0],dashboard&&failure instanceof RepositoryException?failure.getMessage():"加载失败");return;
        }
        if(community) {
          if(!communityLoadedFor.equals(actor()))communityLoadedAt.clear();
          communityLoadedFor=actor();
          communityLoadedAt.put(target.kind(),android.os.SystemClock.elapsedRealtime());
        }
        boolean sameActor=actor.equals(actor());
        if(target.kind().equals("profile")&&sameActor&&updateProfileCounts()) {
          // The profile shell is static; only the two counters need to change.
        } else if(target.kind().equals("admin")&&sameActor&&updateAdminPendingCount()) {
          // The admin shell is static; only the pending counter needs to change.
        } else if(paintedPost==null||!sameActor||!paintedPost.equals(repo.communityPost(target.id()))) {
          refreshPage();
        }
        if(target.kind().equals("community-detail"))loadCommunityDetailExtras(target.id());
        if(catalog&&!retain&&!immediate&&android.animation.ValueAnimator.areAnimatorsEnabled()){content.setAlpha(.5f);content.animate().alpha(1).setDuration(150).start();}
      });
      return null;
    });
    navigationWorker.execute(navigationRead);
  }
  private void cancelNavigation(){navigationEpoch++;navigationHandler.removeCallbacksAndMessages(null);if(navigationRead!=null){navigationRead.cancel(true);navigationWorker.remove(navigationRead);navigationRead=null;}if(navigationLoading&&scroll instanceof RefreshScrollView feed)feed.finishRefresh();navigationLoading=false;AdminDashboardScreen.refreshing(content,false);if(refreshBanner!=null&&refreshBanner.getParent() instanceof android.view.ViewGroup parent)parent.removeView(refreshBanner);refreshBanner=null;}
  boolean isPageLoading(){return navigationLoading;}
  boolean pageFailed(){return pageFailed;}

  private boolean updateProfileCounts() {
    android.view.View following=content.findViewWithTag("profile-following-count");
    android.view.View followers=content.findViewWithTag("profile-follower-count");
    var user=repo.currentUser();
    if(user==null||!(following instanceof TextView followingText)||!(followers instanceof TextView followerText))return false;
    followingText.setText("关注 "+user.followingCount());
    followingText.setContentDescription("我的关注，"+user.followingCount()+"人");
    followerText.setText("粉丝 "+user.followerCount());
    followerText.setContentDescription("关注我的，"+user.followerCount()+"人");
    return true;
  }

  private boolean updateAdminPendingCount() {
    android.view.View view=content.findViewWithTag("admin-pending-count");
    if(!(view instanceof TextView text))return false;
    text.setText(AdminScreen.pendingLabel(this));
    return true;
  }

  private ContentRequest query(Route route) {
    ContentRequest.Query q=switch(route.kind()) {
      case "venue-reviews","venue-write" -> ContentRequest.Query.VENUE_REVIEWS;
      case "venue-mine" -> ContentRequest.Query.VENUE_PERSONAL;
      case "dining" -> ContentRequest.Query.HALLS;
      case "stalls" -> ContentRequest.Query.STALLS;
      case "submit-stall" -> ContentRequest.Query.HALL_TARGET;
      case "dishes" -> ContentRequest.Query.DISHES;
      case "submit-dish" -> ContentRequest.Query.STALL_TARGET;
      case "detail" -> ContentRequest.Query.DISH_DETAIL;
      case "gallery" -> ContentRequest.Query.DISH_IMAGES;
      case "upload-photo","review" -> ContentRequest.Query.DISH_TARGET;
      case "community" -> ContentRequest.Query.COMMUNITY_FEED;
      case "community-following" -> ContentRequest.Query.COMMUNITY_FOLLOWING;
      case "community-detail" -> ContentRequest.Query.COMMUNITY_POST;
      case "community-mine" -> ContentRequest.Query.MY_COMMUNITY_POSTS;
      case "community-favorites" -> ContentRequest.Query.MY_COMMUNITY_FAVORITES;
      case "inbox" -> switch(drafts.getString(draftKey("tab"),"notice")) {
        case "message" -> ContentRequest.Query.MESSAGES;
        case "chat" -> ContentRequest.Query.CONVERSATIONS;
        default -> ContentRequest.Query.NOTICES;
      };
      case "direct-chat" -> ContentRequest.Query.CONVERSATIONS;
      case "admin-community" -> ContentRequest.Query.COMMUNITY_MODERATION;
      case "admin-community-detail" -> ContentRequest.Query.COMMUNITY_MODERATION_DETAIL;
      case "following" -> ContentRequest.Query.FOLLOWING;
      case "followers" -> ContentRequest.Query.FOLLOWERS;
      case "mine-reviews" -> ContentRequest.Query.MY_REVIEWS;
      case "review-replies","reply-create" -> ContentRequest.Query.REVIEW_REPLIES;
      case "mine-review-replies" -> ContentRequest.Query.MY_REVIEW_REPLIES;
      case "mine-submissions" -> ContentRequest.Query.MY_SUBMISSIONS;
      case "admin" -> ContentRequest.Query.ADMIN_SUMMARY;
      case "admin-audit" -> ContentRequest.Query.MODERATION;
      case "admin-data","admin-new" -> ContentRequest.Query.CATALOG;
      case "admin-edit" -> ContentRequest.Query.CATALOG_ITEM;
      case "admin-reviews" -> ContentRequest.Query.ADMIN_REVIEWS;
      case "admin-users" -> ContentRequest.Query.ADMIN_USERS;
      case "admin-dashboard" -> ContentRequest.Query.ADMIN_DASHBOARD;
      case "admin-logs" -> ContentRequest.Query.AUDITS;
      case "profile" -> ContentRequest.Query.PROFILE;
      default -> null;
    };
    return q==null?null:new ContentRequest(q,route.id());
  }

  void redraw() { rememberScroll();renderContent(); }

  private void renderContent() {
    contentRefresh=null;
    communityCommentsRefresh=null;
    shell(true);
    displayedLocation=location();
    displayedActor=actor();contentReady=true;buildHeader();
    renderPage(true);
  }

  /** Rebuild only the page body after a background refresh, keeping the shell and scroll view stable. */
  private void refreshPage() {
    if(!displayedLocation.equals(location())) { renderContent(); return; }
    if(isChatRoute() || isCommunityDetailRoute()) buildHeader();
    if(displayedActor.equals(actor())&&contentRefresh!=null) {
      contentRefresh.run();
      noticeUi.post(this::drainNotices);
      return;
    }
    rememberScroll();
    contentRefresh=null;
    communityCommentsRefresh=null;
    if(isCommunityDetailRoute() && chatBar!=null) chatBar.removeAllViews();
    content.removeAllViews();
    displayedActor=actor();contentReady=true;
    renderPage(false);
  }

  /** A screen can own its changing region while keeping its controls and input views mounted. */
  void onContentRefresh(Runnable refresh) { contentRefresh=refresh; }
  void onCommunityCommentsRefresh(Runnable refresh) { communityCommentsRefresh=refresh; }

  private void loadCommunityDetailExtras(String postId) {
    CommunityPost post=repo.communityPost(postId);
    if(post==null)return;
    if(post.status().equals("approved"))read("community-comments:"+postId,null,null,
        ()->repo.reloadCommunityComments(postId),()->{
          if(communityCommentsRefresh!=null)communityCommentsRefresh.run();
        },()->{});
    if(!post.userId().isBlank()&&!post.userId().equals(repo.currentUser().id()))
      read("community-author-follow:"+post.userId(),null,new TextView(this),
          ()->repo.loadCommunityAuthorFollow(post.userId()),this::buildHeader,()->{});
  }
  void refreshContent() { refreshPage(); }
  private boolean isChatRoute() {
    return route()!=null&&"direct-chat".equals(route().kind())&&repo!=null&&repo.currentUser()!=null;
  }
  /** Fixed actions for chat and community detail, outside the scroll view. */
  void setDetailBottomBar(LinearLayout composer) {
    if(chatBar==null)return;
    chatBar.removeAllViews();
    chatBar.addView(composer,new LinearLayout.LayoutParams(-1,-2));
  }

  private void renderPage(boolean firstPaint) {
    try {
      if (stacks.get(tab).size() > 1 && !LoadingScreen.catalog(route()) && !isCommunityDetailRoute())
        Ui.decorateSubpage(content, route().kind().hashCode());
      Screen s = screens.get(route().kind());
      if (s == null) content.addView(Ui.title(this, tab.equals("profile") ? "我的" : tab.equals("dashboard") ? "数据" : tab.equals("admin") ? "管理" : tab.equals("community") ? "社区" : "食堂", 30));
      else s.show(this, content, route());
    } catch (RuntimeException e) {
      if(firstPaint&&navigationLoading) {
        staticPageShell();
      } else {
        contentReady=false;Ui.empty(content, "页面加载失败", e.getMessage() == null ? "请重试" : e.getMessage());
        Ui.action(content, "重新加载", true, this::render);
      }
    }
    int offset = scrollPositions.getInt(location(), 0);
    ScrollView displayedScroll = scroll;
    displayedScroll.post(() -> displayedScroll.scrollTo(0, offset));
    noticeUi.post(this::drainNotices);
  }

  /** Instant shell for pages whose first data read has not returned yet. */
  private void staticPageShell() {
    content.removeAllViews();
    content.addView(Ui.title(this, routeTitle(route()), 30));
    Ui.gap(content, 8);
    content.addView(Ui.note(this, "页面已打开，内容正在后台加载。"));
    Ui.gap(content, 24);
    LinearLayout card=Ui.card(content);
    card.addView(Ui.text(this, "正在读取内容…", 14, Design.MUTED));
  }

  private String routeTitle(Route route) {
    return switch (route.kind()) {
      case "venue-reviews" -> "点评";
      case "venue-write" -> "写点评";
      case "venue-mine" -> "我的点评";
      case "community-detail" -> "社区食记";
      case "community-following" -> "只看关注";
      case "community-search" -> "搜索食记";
      case "community-author" -> "作者主页";
      case "community-create" -> "写一页美食手账";
      case "community-mine" -> "我的食记";
      case "community-favorites" -> "我的收藏";
      case "inbox" -> "通知信箱";
      case "direct-chat" -> "私信";
      case "admin-community" -> "社区审核";
      case "admin-community-detail" -> "审核详情";
      case "gallery" -> "菜品图册";
      case "upload-photo" -> "上传菜品图片";
      case "submit-dish" -> "投稿新菜品";
      case "submit-stall" -> "投稿新档口";
      case "mine-submissions" -> "我的提交";
      case "correction" -> "目录纠错";
      case "my-corrections" -> "我的纠错";
      case "admin-corrections" -> "目录纠错审核";
      case "following" -> "我的关注";
      case "user-search" -> "搜索用户";
      case "followers" -> "关注我的";
      case "blocked-users" -> "黑名单";
      case "profile-edit" -> "编辑资料";
      case "bind-school-email" -> "绑定学校邮箱";
      case "avatar-crop" -> "裁剪头像";
      case "password" -> "修改密码";
      case "review" -> "写点评";
      case "review-replies" -> "点评回复";
      case "reply-create" -> "回复点评";
      case "mine-reviews" -> "我的评论";
      case "mine-review-replies" -> "我的回复";
      case "detail" -> "菜品详情";
      case "dish-search" -> "跨食堂搜菜";
      case "favorite-dishes" -> "想吃清单";
      case "admin-audit" -> "审核投稿";
      case "admin-data" -> "目录管理";
      case "admin-edit" -> "编辑目录";
      case "admin-new" -> "新增目录";
      case "admin-reviews" -> "评论管理";
      case "admin-users" -> "用户管理";
      case "admin-logs" -> "审核日志";
      case "admin-notices" -> "公告管理";
      case "admin-reports" -> "举报处理";
      case "feedback", "admin-feedback" -> "建议与反馈";
      case "feedback-detail", "admin-feedback-detail" -> "反馈详情";
      default -> "正在加载";
    };
  }
  private void buildHeader() {
    header.removeAllViews();
    if (isCommunityDetailRoute()) {
      CommunityScreen.detailHeader(this, header, repo.communityPost(route().id()));
      return;
    }
    if (isChatRoute()) {
      buildChatHeader();
      return;
    }
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
    if (communityPager != null) {
      bar.addView(CommunityScreen.iconButton(this, cn.edu.bjtu.foodtime.R.drawable.ic_community_search,
          "搜索食记", () -> go("community-search", route().kind().equals("community-following") ? "following" : "")),
          new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
      bar.addView(CommunityScreen.iconButton(this, cn.edu.bjtu.foodtime.R.drawable.ic_community_home,
          "回到食堂首页", this::home), new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
      communityPager.bindTabs(header);
    } else {
      TextView home = Ui.button(this, "⌂", false, this::home);
      home.setContentDescription("回到食堂首页");
      bar.addView(home, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
    }
  }

  private void buildChatHeader() {
    LinearLayout bar=Ui.row(this);
    bar.setGravity(Gravity.CENTER_VERTICAL);
    header.addView(bar,new LinearLayout.LayoutParams(-1,-2));
    TextView back=Ui.button(this,"←",false,this::back);
    back.setContentDescription("返回上一级");
    bar.addView(back,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)));
    Conversation conversation=repo.conversation(route().id());
    String name=conversation==null?"私信":conversation.other().displayName();
    TextView title=Ui.title(this,name.isBlank()?"同学":name,20);
    title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);
    title.setGravity(Gravity.CENTER);
    bar.addView(title,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));
    TextView refresh=Ui.button(this,"↻",false,()->{});
    refresh.setContentDescription("刷新私信");
    refresh.setOnClickListener(v->read("chat-header-refresh:"+route().id(),v,null,
        ()->repo.refresh(new ContentRequest(ContentRequest.Query.CONVERSATIONS,route().id())),
        this::refreshContent,()->{}));
    bar.addView(refresh,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)));
    if (conversation != null) {
      TextView safety = Ui.button(this, "⋯", false, () -> {});
      safety.setContentDescription("私信拉黑管理");
      safety.setOnClickListener(view -> ContentSafety.manageBlock(this, view, conversation.other().id(), blocked -> {
        String version = draftKey("chatBlockVersion");
        drafts.putLong(version, drafts.getLong(version) + 1);
        drafts.putBoolean(draftKey("chatBlocked"), blocked);
        refreshContent();
      }));
      bar.addView(safety, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
    }
  }

  void pinNoticeActions(LinearLayout actions) {
    actions.setPadding(Ui.dp(this,14),Ui.dp(this,8),Ui.dp(this,14),Ui.dp(this,8));
    actions.setBackgroundColor(Design.PAPER);
    root.addView(actions,Math.max(0,root.getChildCount()-1),new LinearLayout.LayoutParams(-1,-2));
  }
  private void shell(boolean navigation) {
    boolean chat=isChatRoute();
    boolean post=isCommunityDetailRoute();
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
    header.setPadding(Ui.dp(this, post ? 4 : 14), Ui.dp(this, post ? 3 : 8),
        Ui.dp(this, post ? 8 : 14), Ui.dp(this, post ? 3 : 8));
    root.addView(header, new LinearLayout.LayoutParams(-1, -2));
    View rule = new View(this);
    rule.setBackgroundColor(Design.BORDER);
    root.addView(rule, new LinearLayout.LayoutParams(-1, Ui.dp(this, 1)));
    boolean hub = navigation && isCommunityHub();
    communityPager = hub ? new CommunityPager(this, route().kind()) : null;
    if (hub) {
      scroll = communityPager.current().scroll();
      content = communityPager.current().content();
      root.addView(communityPager.frame, new LinearLayout.LayoutParams(-1, 0, 1));
    } else {
      scroll = new ScrollView(this);
      scroll.setFillViewport(true);
      content = Ui.col(this);
      content.setBackgroundColor(Design.PAPER);
      if (chat || post) content.setPadding(0, 0, 0, 0);
      else content.setPadding(Ui.dp(this, 14), Ui.dp(this, 24), Ui.dp(this, 14), Ui.dp(this, 24));
      scroll.addView(content);
      root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    }
    if (chat || post) {
      chatBar = Ui.col(this);
      chatBar.setBackgroundColor(Design.PAPER);
      root.addView(chatBar, new LinearLayout.LayoutParams(-1, -2));
    } else chatBar = null;
    if (navigation && !chat && !post) {
      LinearLayout nav = Ui.row(this);
      nav.setBackground(Ui.shape(this, Design.PAPER, Design.BORDER, 0));
      addTab(nav, "食堂", "dining");
      addTab(nav, "社区", "community");
      if (repo.currentUser().isAdmin()) addTab(nav, "管理", "admin");
      if (repo.currentUser().role().equals("superadmin")) addTab(nav, "数据", "dashboard");
      addTab(nav, "我的", "profile");
      root.addView(nav, new LinearLayout.LayoutParams(-1, -2));
    }
    setContentView(root);
    root.requestApplyInsets();
  }

  private boolean isCommunityDetailRoute() {
    return route()!=null && "community-detail".equals(route().kind()) && repo!=null && repo.currentUser()!=null;
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

  void finishVenueWrite(String type,String id) {
    var stack=stacks.get(tab);
    if(stack.size()>1&&stack.get(stack.size()-2).kind().equals("venue-reviews")&&stack.get(stack.size()-2).id().startsWith(type+"~"+id+"~"))back();
    else replaceRoute("venue-reviews",type+"~"+id+"~all");
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
    if(!ImagePicker.start(this,ImagePicker.intent(this,false,1,null),
        ImagePicker.fallback(false,null),101)) {
      pickingKey=null;
      message("无法打开图片选择器，请从文件选择");
    }
  }
  public void pickAvatar(String key){
    if(repo.currentUser()==null||writeInFlight)return;
    pickingKey=key;avatarPickingActor=repo.currentUser().id();
    String[] mime={"image/jpeg","image/png"};
    if(!ImagePicker.start(this,ImagePicker.intent(this,false,1,mime),
        ImagePicker.fallback(false,mime),103)) {
      pickingKey=null;avatarPickingActor="";
      message("无法打开图片选择器，请从文件选择");
    }
  }
  void finishAvatarCrop(View trigger,TextView error,String target,Supplier<String> encode){
    startOperation(trigger,error,"裁剪头像",target,encode,path->{
      String previous=drafts.getString(target,"");drafts.putString(target,path);discardPhoto(previous);back();
    });
  }

  public void pickCommunityPhotos() {
    pickingKey=draftKey("photos");
    int count=0;
    for(int i=0;i<9;i++)if(!drafts.getString(draftKey("photo:"+i+":image"),"").isBlank())count++;
    int remaining=Math.max(1,9-count);
    if(!ImagePicker.start(this,ImagePicker.intent(this,true,remaining,null),
        ImagePicker.fallback(true,null),102)) {
      pickingKey=null;
      message("无法打开图片选择器，请从文件选择");
    }
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
    // Kept for existing instrumentation harnesses; production uses ImagePicker's album-first adapter.
    return ImagePicker.fallback(false,null);
  }

  @Override
  protected void onActivityResult(int request, int result, android.content.Intent data) {
    super.onActivityResult(request, result, data);
    if(feedbackDocuments.result(request,result,data))return;
    if(request==103){
      String target=pickingKey,actor=avatarPickingActor;pickingKey=null;avatarPickingActor="";
      android.net.Uri selected=result==RESULT_OK?ImagePicker.firstUri(data):null;
      if(selected==null||target==null)return;
      if(repo.currentUser()==null||!repo.currentUser().id().equals(actor)||!route().kind().equals("profile-edit")||!target.equals(draftKey("image"))){message("登录状态或页面已变化，请重新选择头像");return;}
      startOperation(null,null,"导入头像",target,()->PhotoImport.prepareAvatar(this,selected),path->{
        if(repo.currentUser()==null||!repo.currentUser().id().equals(actor)){discardPhoto(path);message("登录状态已变化，请重新选择头像");return;}
        drafts.putString("avatarCropTarget",target);go("avatar-crop",path);
      });return;
    }
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
        && pickingKey != null) {
      String selectedKey=pickingKey;
      android.net.Uri selected=ImagePicker.firstUri(data);
      if(selected==null)return;
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

  void requestCommunityComment(View trigger, TextView error, boolean reply, Supplier<String> work, Consumer<String> success) {
    startOperation(trigger, error, reply ? "提交回复" : "提交评论", null, work, success);
  }
  boolean operationInFlight() { return writeInFlight; }
  /** Coalesce reads per resource, without dropping other floors or blocking navigation/writes. */
  void read(String key,View trigger,TextView error,Runnable work,Runnable success,Runnable finished) {
    read(worker,key,trigger,error,work,success,finished);
  }
  void recordPostView(String key,Runnable work,Runnable success) {
    read(statisticsWorker,key,null,new TextView(this),work,success,() -> {});
  }
  private void read(Executor executor,String key,View trigger,TextView error,Runnable work,Runnable success,Runnable finished) {
    if(trigger!=null) trigger.setEnabled(false);
    if(error!=null) error.setText("");
    ReadListener listener=new ReadListener(generation,trigger,error,success,finished);
    List<ReadListener> existing=reads.get(key);
    if(existing!=null) {existing.add(listener);return;}
    List<ReadListener> listeners=new ArrayList<>();listeners.add(listener);reads.put(key,listeners);
    executor.execute(() -> {
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
    communityLoadedAt.clear();
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
          if (consumeIncoming()) render(true);
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
    if(repo.currentUser()==null) {signedOut();if(task.origin().kind().equals("account-lifecycle")&&task.action().equals("注销账号"))message("账号已注销，两个邮箱冷却 1 小时后可重新使用；新账号不继承旧数据。");return;}
    if(task.origin().kind().equals("account-lifecycle")&&task.action().equals("解绑学校邮箱")){clearDraft(task.origin());message("学校邮箱已解绑，账号保留并恢复访客身份；学校邮箱冷却 1 小时后可重新绑定。");}
    if(task.action().equals("导入头像")){
      drafts.putString("avatarCropTarget",task.imageDraft());go("avatar-crop",outcome.value());return;
    }
    if(task.origin().kind().equals("community-detail")&&Set.of("提交评论","提交回复").contains(task.action())){
      String prefix=task.origin().kind()+":"+task.origin().id()+":";
      CommunityCommentComposer.completeSubmission(drafts, prefix);
      message("已提交审核，通过前仅自己可见");
    }
    if(task.action().equals("发布回复")) {EmbeddedReviewReplies.completeSubmission(this);message("回复已提交，审核通过后公开");}
    if(task.imageDraft()!=null && task.imageDraft().endsWith(":photos")) {applyCommunityPhotos(task.imageDraft(),outcome.value());}
    else if(task.imageDraft()!=null) {
      String previous=drafts.getString(task.imageDraft(),"");drafts.putString(task.imageDraft(),outcome.value());
      worker.execute(() -> PhotoImport.discard(this,previous));
      if(task.action().equals("裁剪头像")&&route().equals(task.origin())){back();return;}
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
  private static String formatBytes(long bytes) {
    if(bytes<1024)return bytes+" B";
    if(bytes<1024*1024)return String.format(Locale.CHINA,"%.1f KB",bytes/1024.0);
    return String.format(Locale.CHINA,"%.1f MB",bytes/1048576.0);
  }
  private static String formatSpeed(long bytesPerSecond) {
    if(bytesPerSecond<1024)return bytesPerSecond+" B/s";
    if(bytesPerSecond<1024*1024)return String.format(Locale.CHINA,"%.0f KB/s",bytesPerSecond/1024.0);
    return String.format(Locale.CHINA,"%.1f MB/s",bytesPerSecond/1048576.0);
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
    if(accountActionConfirmation!=null){accountActionConfirmation.dismiss();accountActionConfirmation=null;}
    feedbackDocuments.reset();
    PrivateMessageReminders.disable(this);
    for(var stack:stacks.values())for(Route item:stack)if(item.kind().equals("avatar-crop"))discardPhoto(item.id());
    pickingKey=null;avatarPickingActor="";
    cancelNavigation();contentReady=false;displayedActor="";communityLoadedAt.clear();communityLoadedFor="";
    if(loginNoticeDialog!=null){loginNoticeDialog.dialog.dismiss();loginNoticeDialog=null;}
    loginNotices.reset();noticeErrorShown="";
    for(String key:drafts.keySet()) if(key.endsWith(":image")) {
      String photo=drafts.getString(key,"");worker.execute(() -> PhotoImport.discard(this,photo));
    }
    drafts.clear();
    scrollPositions.clear();
    for (String key : stacks.keySet()) {
      stacks.get(key).clear();
      stacks.get(key).add(new Route(key.equals("dashboard") ? "admin-dashboard" : key, ""));
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
    for(Runnable callback:callbacks) callback.run();
    if(contentReady&&consumeIncoming()){resumeNavigation=false;render(true);}
    else if(resumeNavigation&&repo!=null&&repo.currentUser()!=null){resumeNavigation=false;render(true);}
    else if(isCommunityHub()&&repo!=null&&repo.currentUser()!=null&&communityLoadedAt.containsKey(route().kind())
        &&!freshCommunitySection(route().kind())){render(true);}
    noticeUi.post(this::drainNotices);
    if(contentReady&&repo!=null&&repo.currentUser()!=null&&loginNotices!=null)loginNotices.foreground(repo,inboxWorker);
    syncPrivateMessageUnread();
  }

  private void syncPrivateMessageUnread() {
    var user = repo.currentUser();
    if (user == null || !contentReady) return;
    Route location = route();
    boolean chat = location.kind().equals("direct-chat");
    read("resume-direct-unread:" + location.kind() + ":" + location.id(), null, new TextView(this), () -> {
      long unread = repo.refreshDirectUnread();
      if (chat && unread > 0) repo.refresh(new ContentRequest(ContentRequest.Query.CONVERSATIONS, location.id()));
    }, () -> {
      ProfileScreen.updateUnreadBadge(this);
      InboxScreen.updateDirectBadge(this);
      if (chat && repo.directUnreadCount() > 0) refreshContent();
      else PrivateMessageReminders.update(this, user.id(), repo.directUnreadCount());
    }, () -> {});
  }

  boolean privateMessageRemindersConfigured() {
    var user = repo.currentUser();
    return user != null && PrivateMessageReminders.configured(this, user.id());
  }

  void togglePrivateMessageReminders() {
    if (privateMessageRemindersConfigured()) {
      PrivateMessageReminders.disable(this);
      updateReminderToggle();
      return;
    }
    if (android.os.Build.VERSION.SDK_INT >= 33
        && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
      requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, REQUEST_PRIVATE_MESSAGE_NOTIFICATION);
      return;
    }
    enablePrivateMessageReminders();
  }

  @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] grantResults) {
    super.onRequestPermissionsResult(code, permissions, grantResults);
    if (code == REQUEST_PRIVATE_MESSAGE_NOTIFICATION) {
      if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
        enablePrivateMessageReminders();
      } else {
        message("系统未允许通知，可在系统设置中开启。");
      }
    }
  }

  private void enablePrivateMessageReminders() {
    var user = repo.currentUser();
    if (user == null) return;
    if (PrivateMessageReminders.enable(this, user.id(), repo.directUnreadCount())) {
      updateReminderToggle();
    } else {
      message("未能开启私信提醒，请检查系统通知设置。");
    }
  }

  private void updateReminderToggle() {
    TextView control = root == null ? null : root.findViewWithTag("private-message-reminder-toggle");
    if (control != null) {
      control.setText(privateMessageRemindersConfigured() ? "关闭私信提醒" : "开启私信提醒");
    }
  }
  @Override protected void onPause() { super.onPause();foreground=false;if(communityPager!=null)communityPager.cancelSwipe();resumeNavigation=navigationLoading||tab.equals("dashboard");cancelNavigation();Ui.pauseImages(); }
  @Override public void onTrimMemory(int level) {super.onTrimMemory(level);if(level==android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW||level==android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL||level>=android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)Ui.trimImages();}
  @Override protected void onDestroy() {if(accountActionConfirmation!=null)accountActionConfirmation.dismiss();if(activeDownloader!=null)activeDownloader.cancel();if(passwordRecovery!=null)passwordRecovery.close();if(feedbackDocuments!=null)feedbackDocuments.close();++generation;cancelNavigation();navigationWorker.shutdownNow();statisticsWorker.shutdownNow();noticeUi.removeCallbacksAndMessages(null);if(loginNotices!=null)loginNotices.detach(this);if(loginNoticeDialog!=null)loginNoticeDialog.dialog.dismiss();deferredCompletion=null;deferredReads.clear();reads.clear();super.onDestroy();}
}
