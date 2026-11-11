package cn.edu.bjtu.foodtime.presentation;

import android.text.*;
import android.view.View;
import android.widget.*;
import cn.edu.bjtu.foodtime.domain.model.Models.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

final class BlockedUsersScreen {
  static void show(MainActivity a,LinearLayout page,MainActivity.Route route) {
    if(a.repo.currentUser()!=null)new BlockedUsersScreen(a,page);
  }
  private final MainActivity a;
  private final LinearLayout rows,controls;
  private final TextView error,status,refresh;
  private final List<TextView> actions=new ArrayList<>();
  private final String key="blocked-users:"+UUID.randomUUID();
  private BlockedUserPage current;
  private boolean busy;

  private BlockedUsersScreen(MainActivity a,LinearLayout page) {
    this.a=a;
    a.heading(page,"","黑名单","拉黑期间双方无法发送私信，已有会话记录会保留。");
    refresh=Ui.button(a,"刷新列表",false,()->load(current==null?1:current.page()));page.addView(refresh);
    error=Ui.text(a,"",14,Design.RED);error.setTag("blocklist-error");Ui.observeError(error);page.addView(error);
    error.addTextChangedListener(new TextWatcher(){
      public void beforeTextChanged(CharSequence s,int start,int count,int after){}
      public void onTextChanged(CharSequence s,int start,int before,int count){if(s.length()>0)setBusy(false);}
      public void afterTextChanged(Editable s){}
    });
    status=Ui.text(a,"",14,Design.MUTED);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);page.addView(status);
    rows=Ui.col(a);rows.setTag("blocklist-rows");page.addView(rows);
    controls=Ui.col(a);page.addView(controls);
    page.post(()->{if(page.isAttachedToWindow())load(1);});
  }
  private void setBusy(boolean value) {
    busy=value;refresh.setEnabled(!value);
    for(TextView action:actions)action.setEnabled(!value);
  }
  private void load(int page) {
    if(busy||a.operationInFlight())return;
    setBusy(true);status.setText("加载中…");
    AtomicReference<BlockedUserPage> result=new AtomicReference<>();
    a.read(key,refresh,error,()->result.set(a.repo.blockedUsers(page)),()->{
      BlockedUserPage next=result.get();
      if(next.items().isEmpty()&&page>1){load(Math.max(1,(int)((next.total()+19)/20)));return;}
      current=next;paint();
    },()->{setBusy(false);status.setText(current==null?"":current.total()+" 位用户");});
  }
  private void paint() {
    rows.removeAllViews();controls.removeAllViews();actions.clear();
    if(current.items().isEmpty())Ui.empty(rows,"黑名单为空","你还没有拉黑任何用户。");
    for(BlockedUser user:current.items()) {
      LinearLayout row=Ui.row(a);row.setPadding(0,Ui.dp(a,12),0,Ui.dp(a,12));
      String name=user.displayName().isBlank()?"校园干饭人":user.displayName();
      View avatar=Ui.image(a,Ui.thumb(user.avatarUrl()),new String(Character.toChars(name.codePointAt(0))),false);
      row.addView(avatar,new LinearLayout.LayoutParams(Ui.dp(a,44),Ui.dp(a,44)));
      TextView label=Ui.text(a,name,16,Design.INK);label.setPadding(Ui.dp(a,12),0,Ui.dp(a,8),0);
      row.addView(label,new LinearLayout.LayoutParams(0,-2,1));
      TextView action=Ui.button(a,"解除拉黑",false,()->{
        if(!busy&&!a.operationInFlight())Ui.confirm(a,"解除拉黑？","解除对“"+name+"”的拉黑后，你们可以重新发送私信。",()->unblock(user));
      });
      action.setTag("blocklist-unblock:"+user.id());row.addView(action);actions.add(action);rows.addView(row);
    }
    if(current.page()>1)addPage("上一页",current.page()-1);
    if(current.hasMore())addPage("下一页",current.page()+1);
    status.setText(current.total()+" 位用户 · 第 "+current.page()+" 页");setBusy(false);
  }
  private void addPage(String label,int page) {
    TextView action=Ui.button(a,label,false,()->load(page));actions.add(action);controls.addView(action);
  }
  private void unblock(BlockedUser user) {
    if(busy||a.operationInFlight())return;
    setBusy(true);
    a.request(null,error,()->a.repo.setBlocked(user.id(),false),()->{
      setBusy(false);a.message("已解除拉黑");
      int page=current.items().size()==1?Math.max(1,current.page()-1):current.page();load(page);
    });
  }
}
