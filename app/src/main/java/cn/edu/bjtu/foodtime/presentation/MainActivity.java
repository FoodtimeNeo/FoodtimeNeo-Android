package cn.edu.bjtu.foodtime.presentation;

import android.app.Activity;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import cn.edu.bjtu.foodtime.domain.repository.FoodtimeRepository;
import cn.edu.bjtu.foodtime.domain.repository.RepositoryProvider;
import cn.edu.bjtu.foodtime.domain.repository.ContentRequest;
import cn.edu.bjtu.foodtime.domain.repository.RepositoryException;
import java.util.*;
import java.util.concurrent.Executor;

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
  private int generation;
  private boolean writeInFlight;
  private String displayedLocation="";
  private Toast currentToast;

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    Design.init(this);
    repo = ((RepositoryProvider) getApplication()).repository();
    worker = ((RepositoryProvider) getApplication()).workExecutor();
    registerScreens();
    for (String key : List.of("dining", "profile", "admin"))
      stacks.put(key, new ArrayList<>(List.of(new Route(key, ""))));
    if (state != null) {
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
        else if(repo.currentUser()==null) showAuth(false);else openApp();
      });
    });
  }

  void registerScreens() {
    screens.put("dining", DiningScreen::halls);
    screens.put("stalls", DiningScreen::stalls);
    screens.put("dishes", DishScreen::dishes);
    screens.put("gallery", DishScreen::gallery);
    screens.put("upload-photo", UploadScreen::photo);
    screens.put("submit-dish", UploadScreen::dish);
    screens.put("submit-stall", UploadScreen::stall);
    screens.put("mine-submissions", SubmissionScreen::mine);
    screens.put("profile", ProfileScreen::profile);
    screens.put("mine-reviews", ProfileScreen::reviews);
    screens.put("password", PasswordScreen::show);
    screens.put("detail", DishScreen::detail);
    screens.put("review", ReviewScreen::create);
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
    render();
  }

  public void go(String kind, String id) {
    if(writeInFlight) {message("正在提交，请稍候");return;}
    rememberScroll();
    stacks.get(tab).add(new Route(kind, id));
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
    rememberScroll();
    state.putBundle("drafts", drafts);
    state.putBundle("scrollPositions", scrollPositions);
    state.putString("pickingKey", pickingKey);
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
    int ticket=++generation;
    ContentRequest query=query(route());
    shell(true);content.addView(Ui.text(this,"加载中…",16,Design.MUTED));
    worker.execute(() -> {
      RuntimeException error=null;try {repo.refresh(query);}catch(RuntimeException e){error=e;}
      RuntimeException failure=error;
      runOnUiThread(() -> {
        if(isDestroyed()||ticket!=generation) return;
        if(repo.currentUser()==null) {clearNavigation();showAuth(false);message("请重新登录");return;}
        if(tab.equals("admin")&&!repo.currentUser().isAdmin()) {tab="dining";render();return;}
        if(failure!=null) {content.removeAllViews();Ui.empty(content,"加载失败",safeMessage(failure));Ui.action(content,"重试",true,this::render);return;}
        renderContent();
      });
    });
  }

  private ContentRequest query(Route route) {
    ContentRequest.Query q=switch(route.kind()) {
      case "dining" -> ContentRequest.Query.HALLS;
      case "stalls","submit-stall" -> ContentRequest.Query.STALLS;
      case "dishes","submit-dish" -> ContentRequest.Query.DISHES;
      case "detail","gallery","upload-photo","review" -> ContentRequest.Query.DISH_DETAIL;
      case "mine-reviews" -> ContentRequest.Query.MY_REVIEWS;
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

  private void renderContent() {
    shell(true);
    displayedLocation=location();
    LinearLayout bar = Ui.row(this);
    header.addView(bar, new LinearLayout.LayoutParams(-1, -2));
    TextView back = Ui.button(this, "←", false, this::back);
    back.setContentDescription("返回上一级");
    if (stacks.get(tab).size() > 1)
      bar.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
    TextView brand = Ui.title(this, "北交干饭吧", 22);
    brand.setGravity(Gravity.CENTER_VERTICAL);
    bar.addView(brand, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
    TextView home = Ui.button(this, "⌂", false, this::home);
    home.setContentDescription("回到食堂首页");
    bar.addView(home, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
    try {
      Screen s = screens.get(route().kind());
      if (s == null) {
        content.addView(
            Ui.title(
                this, tab.equals("profile") ? "我的" : tab.equals("admin") ? "管理" : "食堂", 30));
      } else s.show(this, content, route());
    } catch (RuntimeException e) {
      Ui.empty(content, "页面加载失败", e.getMessage() == null ? "请重试" : e.getMessage());
      Ui.action(content, "重新加载", true, this::render);
    }
    int offset = scrollPositions.getInt(location(), 0);
    scroll.post(() -> scroll.scrollTo(0, offset));
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
    if (request == 101
        && result == RESULT_OK
        && data != null
        && data.getData() != null
        && pickingKey != null) {
      String selectedKey=pickingKey;
      android.net.Uri selected=data.getData();
      java.util.concurrent.atomic.AtomicReference<String> resultPath=new java.util.concurrent.atomic.AtomicReference<>();
      request(null,null,() -> resultPath.set(PhotoImport.prepare(this,selected)),() -> {
        rememberScroll();drafts.putString(selectedKey,resultPath.get());renderContent();
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
    if(writeInFlight) return;
    writeInFlight=true;if(trigger!=null) trigger.setEnabled(false);if(error!=null) error.setText("");
    int ticket=generation;boolean loggedIn=repo.currentUser()!=null;
    worker.execute(() -> {
      RuntimeException problem=null;try {work.run();}catch(RuntimeException e){problem=e;}
      RuntimeException failure=problem;
      runOnUiThread(() -> {
        if(isDestroyed()) return;
        writeInFlight=false;if(trigger!=null) trigger.setEnabled(true);
        if(ticket!=generation) return;
        if(failure==null) success.run();
        else if(loggedIn&&repo.currentUser()==null) {
          clearNavigation();showAuth(false);message("登录已失效，请重新登录");
        } else if(error!=null) error.setText(safeMessage(failure));else message(safeMessage(failure));
      });
    });
  }
  private String safeMessage(RuntimeException e) {return e.getMessage()==null?"操作失败，请重试":e.getMessage();}
  public void clearDraft(Route route) {
    String prefix=route.kind()+":"+route.id()+":";
    for(String key:new HashSet<>(drafts.keySet())) if(key.startsWith(prefix)) drafts.remove(key);
  }

  public void logout() {
    request(null,null,repo::logout,() -> {clearNavigation();showAuth(false);});
  }
  public void signedOut() {clearNavigation();showAuth(false);}
  private void clearNavigation() {
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
  @Override protected void onDestroy() {++generation;super.onDestroy();}
}
