package cn.edu.bjtu.foodtime.presentation;

import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.text.*;
import android.view.*;
import android.widget.*;
import cn.edu.bjtu.foodtime.domain.model.Models.*;
import java.util.*;

/** Only result rows change while searching; the editor remains mounted. */
final class UserSearchScreen {
  static void show(MainActivity a,LinearLayout page,MainActivity.Route route) { new Search(a,page); }
  private static Button button(MainActivity a,String label) {
    Button b=new Button(a);b.setText(label);b.setAllCaps(false);b.setTextSize(14);b.setTypeface(Design.body());
    b.setTextColor(Design.INK);b.setMinHeight(Ui.dp(a,48));b.setMinimumHeight(Ui.dp(a,48));
    b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(Ui.dp(a,8),Ui.dp(a,8),Ui.dp(a,8),Ui.dp(a,8));
    b.setStateListAnimator(null);b.setBackground(Ui.ripple(a,Design.PAPER,Design.BORDER,4));return b;
  }
  private static final class Person {
    FollowUser user;LinearLayout view;TextView name,error;Button follow,chat;
  }
  private static final class Search {
    final MainActivity a;final LinearLayout page,rows;final EditText input;final Button submit,more;
    final TextView status,error;final Handler handler=new Handler(Looper.getMainLooper());
    final Map<String,Person> people=new LinkedHashMap<>();final String draftKey;
    UserSearchPage displayed=UserSearchPage.empty();String submitted="";int version;boolean loading,writing,loaded;
    final Runnable delayed=()->load(false);
    Search(MainActivity activity,LinearLayout container) {
      a=activity;page=container;
      draftKey=a.draftKey("userSearchQuery")+":"+a.repo.currentUser().id()+":"+a.repo.currentUser().role();
      a.heading(page,"","搜索用户","按昵称查找同学，关注或发一条私信。");
      input=Ui.field(page,"用户名",false,false);input.setHint("输入同学的昵称");input.setTag("user-search-input");
      input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(50)});
      input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
      input.setText(a.drafts.getString(draftKey,""));
      submit=button(a,"搜索");submit.setTag("user-search-submit");submit.setOnClickListener(v->load(false));
      page.addView(submit,new LinearLayout.LayoutParams(-1,-2));Ui.gap(page,12);
      if(visitor()) {
        page.addView(Ui.text(a,"绑定学校邮箱后即可关注和私信。",14,Design.MUTED));
        Ui.action(page,"去绑定学校邮箱",false,()->a.go("bind-school-email",""));
      }
      error=Ui.text(a,"",14,Design.RED);error.setTag("user-search-error");Ui.observeError(error);page.addView(error);
      status=Ui.text(a,"输入昵称，找到后可以关注或发私信。",14,Design.MUTED);status.setTag("user-search-status");
      status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);page.addView(status);
      rows=Ui.col(a);rows.setTag("user-search-rows");page.addView(rows,new LinearLayout.LayoutParams(-1,-2));
      more=button(a,"加载更多");more.setTag("user-search-more");more.setVisibility(View.GONE);more.setOnClickListener(v->load(true));
      page.addView(more,new LinearLayout.LayoutParams(-1,-2));
      input.addTextChangedListener(new TextWatcher(){
        public void beforeTextChanged(CharSequence s,int start,int count,int after){}
        public void onTextChanged(CharSequence value,int start,int before,int count) {
          a.drafts.putString(draftKey,value.toString());handler.removeCallbacks(delayed);version++;loading=false;loaded=false;
          rows.removeAllViews();people.clear();more.setVisibility(View.GONE);error.setText("");
          if(value.toString().isBlank()){submitted="";displayed=UserSearchPage.empty();status.setText("输入昵称，找到后可以关注或发私信。");}
          else {status.setText("输入完成后自动搜索");handler.postDelayed(delayed,300);}
          controls();
        }
        public void afterTextChanged(Editable value){}
      });
      input.setOnEditorActionListener((view,action,event)->{
        if(action==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH){load(false);return true;}return false;
      });
      page.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){
        public void onViewAttachedToWindow(View view){}
        public void onViewDetachedFromWindow(View view){handler.removeCallbacks(delayed);version++;}
      });
      controls();if(!input.getText().toString().isBlank())page.post(()->load(false));
    }
    boolean visitor(){return a.repo.currentUser()==null||"visitor".equals(a.repo.currentUser().role());}
    void controls() {
      boolean busy=writing||a.operationInFlight();input.setEnabled(!busy);submit.setEnabled(!loading&&!busy);more.setEnabled(!loading&&!busy);
      submit.setText(loading?"搜索中…":"搜索");more.setText(loading?"加载中…":"加载更多");
      for(Person person:people.values()) {
        person.follow.setEnabled(!busy&&!visitor());person.chat.setEnabled(!busy&&!visitor());
        person.follow.setText(person.user.followedByMe()?"已关注":"关注");
        person.follow.setTextColor(person.user.followedByMe()?Design.MUTED:Design.RED);
        person.follow.setContentDescription((person.user.followedByMe()?"取消关注":"关注")+person.user.displayName());
      }
    }
    void load(boolean append) {
      handler.removeCallbacks(delayed);String query=input.getText().toString().strip();
      if(query.isEmpty()||loading||writing||a.operationInFlight()||!page.isAttachedToWindow())return;
      if(!append&&loaded&&query.equals(submitted))return;
      int number=append?displayed.page()+1:1,ticket=++version;submitted=query;loading=true;error.setText("");controls();
      status.setText(append?"正在加载更多…":"正在查找同学…");
      TextView requestError=new TextView(a);
      requestError.addTextChangedListener(new TextWatcher(){
        public void beforeTextChanged(CharSequence s,int start,int count,int after){}
        public void onTextChanged(CharSequence value,int start,int before,int count) {
          if(ticket==version&&query.equals(input.getText().toString().strip())) {
            error.setText(value);
            if(value.length()>0)status.setText(append?"加载失败，已保留现有结果。":"搜索失败，请重试。");
          }
        }
        public void afterTextChanged(Editable value){}
      });
      a.read("user-search:"+query+":"+number,append?more:submit,requestError,
          ()->a.repo.loadUserSearch(query,number),()->{
            if(ticket!=version||!query.equals(input.getText().toString().strip()))return;
            UserSearchPage result=a.repo.userSearchResults();if(!query.equals(result.query()))return;
            displayed=result;loaded=true;paint();
          },()->{if(ticket==version){loading=false;controls();}});
    }
    void paint() {
      for(FollowUser user:displayed.items()) {
        Person person=people.get(user.id());
        if(person==null){person=create(user);people.put(user.id(),person);rows.addView(person.view,new LinearLayout.LayoutParams(-1,-2));}
        person.user=user;person.name.setText(user.displayName());
      }
      status.setText(displayed.items().isEmpty()?"没有找到匹配的用户，试试其他昵称。":"已显示 "+displayed.items().size()+" / "+displayed.total()+" 位同学");
      more.setVisibility(displayed.hasMore()?View.VISIBLE:View.GONE);controls();
    }
    Person create(FollowUser user) {
      Person person=new Person();person.user=user;person.view=Ui.col(a);person.view.setTag("user-search-row:"+user.id());
      LinearLayout line=Ui.row(a);line.setGravity(Gravity.CENTER_VERTICAL);line.setPadding(0,Ui.dp(a,12),0,Ui.dp(a,12));
      String name=user.displayName().isBlank()?"同学":user.displayName();
      View avatar=Ui.image(a,Ui.thumb(user.avatarUrl()),new String(Character.toChars(name.codePointAt(0))),false);
      avatar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);avatar.setBackground(Ui.shape(a,Design.ALT,Design.BORDER,999));avatar.setClipToOutline(true);
      LinearLayout.LayoutParams avatarLp=new LinearLayout.LayoutParams(Ui.dp(a,40),Ui.dp(a,40));avatarLp.rightMargin=Ui.dp(a,8);line.addView(avatar,avatarLp);
      person.name=Ui.text(a,name,16,Design.INK);line.addView(person.name,new LinearLayout.LayoutParams(0,-2,1));
      person.follow=button(a,"关注");person.follow.setTag("user-search-follow:"+user.id());
      person.chat=button(a,"私信");person.chat.setTag("user-search-chat:"+user.id());person.chat.setContentDescription("私信"+name);
      LinearLayout.LayoutParams actionLp=new LinearLayout.LayoutParams(-2,-2);actionLp.leftMargin=Ui.dp(a,8);line.addView(person.follow,actionLp);
      LinearLayout.LayoutParams chatLp=new LinearLayout.LayoutParams(-2,-2);chatLp.leftMargin=Ui.dp(a,8);line.addView(person.chat,chatLp);
      person.view.addView(line);person.error=Ui.text(a,"",13,Design.RED);Ui.observeError(person.error);person.view.addView(person.error);
      person.error.addTextChangedListener(new TextWatcher(){
        public void beforeTextChanged(CharSequence s,int start,int count,int after){}
        public void onTextChanged(CharSequence s,int start,int before,int count){if(s.length()>0){writing=false;controls();}}
        public void afterTextChanged(Editable value){}
      });
      person.follow.setOnClickListener(v->{
        if(writing||visitor()||a.operationInFlight())return;
        if(!person.user.followedByMe())follow(person);
        else new AlertDialog.Builder(a).setTitle("取消关注？").setMessage("将不再关注“"+person.user.displayName()+"”。")
            .setNegativeButton("再想想",null).setPositiveButton("取消关注",(dialog,which)->follow(person)).show();
      });
      person.chat.setOnClickListener(v->{
        if(writing||visitor()||a.operationInFlight())return;
        writing=true;controls();String[] conversation={""};
        a.request(person.chat,person.error,()->conversation[0]=a.repo.startConversation(person.user.id()).id(),
            ()->{writing=false;controls();a.go("direct-chat",conversation[0]);});
      });
      View divider=new View(a);divider.setBackgroundColor(Design.BORDER);person.view.addView(divider,new LinearLayout.LayoutParams(-1,Ui.dp(a,1)));return person;
    }
    void follow(Person person) {
      boolean next=!person.user.followedByMe();writing=true;controls();
      a.request(person.follow,person.error,()->a.repo.setFollow(person.user.id(),next),()->{
        writing=false;displayed=a.repo.userSearchResults();paint();
      });
    }
  }
  private UserSearchScreen(){}
}
