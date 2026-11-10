package cn.edu.bjtu.foodtime.presentation;

import android.app.AlertDialog;
import android.graphics.Color;
import android.text.*;
import android.view.*;
import android.widget.*;
import cn.edu.bjtu.foodtime.domain.model.Models.*;
import java.util.*;

/** Clear list layout; updates rows in place without replacing the page or its scroll view. */
final class FollowListScreen {
  static void following(MainActivity a,LinearLayout page,MainActivity.Route route){new ListView(a,page,false);}
  static void followers(MainActivity a,LinearLayout page,MainActivity.Route route){new ListView(a,page,true);}

  private static Button button(MainActivity a,String label) {
    Button b=new Button(a);b.setText(label);b.setAllCaps(false);b.setTypeface(Design.body());b.setTextSize(14);
    b.setTextColor(Design.INK);b.setMinHeight(Ui.dp(a,48));b.setMinimumHeight(Ui.dp(a,48));
    b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(Ui.dp(a,10),Ui.dp(a,8),Ui.dp(a,10),Ui.dp(a,8));
    b.setStateListAnimator(null);b.setElevation(0);
    b.setBackground(Ui.ripple(a,Design.PAPER,Design.BORDER,4));return b;
  }
  private static TextView error(MainActivity a) {
    TextView v=Ui.text(a,"",14,Design.RED);v.setVisibility(View.GONE);Ui.observeError(v);
    v.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);return v;
  }
  private static final class Person {
    FollowUser user;LinearLayout view;TextView relation,error;Button action;
  }
  private static final class ListView {
    final MainActivity a;final boolean fans;final LinearLayout rows;
    final Button refresh,more;final Button[] tabs=new Button[2];
    final TextView status,headerError,footerError;
    final Map<String,Person> people=new LinkedHashMap<>();
    boolean loading,writing;
    ListView(MainActivity activity,LinearLayout page,boolean followers) {
      a=activity;fans=followers;
      a.heading(page,"","我的关系","");
      Ui.action(page,"搜索用户",false,()->a.go("user-search",""));
      LinearLayout tabRow=Ui.row(a);
      for(int i=0;i<2;i++) {
        boolean selected=(i==1)==fans;int index=i;
        LinearLayout group=Ui.col(a);Button tab=button(a,"");tabs[i]=tab;
        tab.setTextSize(16);tab.setSelected(selected);tab.setTextColor(selected?Design.RED:Design.MUTED);
        tab.setBackground(Ui.ripple(a,Color.TRANSPARENT,0,0));
        tab.setTag(i==0?"following-tab":"followers-tab");
        if(android.os.Build.VERSION.SDK_INT>=30)tab.setStateDescription(selected?"已选择":"未选择");
        tab.setOnClickListener(v->{if((index==1)!=fans)a.replaceRoute(index==0?"following":"followers","");});
        group.addView(tab,new LinearLayout.LayoutParams(-1,-2));
        View underline=new View(a);underline.setBackgroundColor(selected?Design.RED:Design.BORDER);
        group.addView(underline,new LinearLayout.LayoutParams(-1,Ui.dp(a,2)));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);if(i==0)lp.rightMargin=Ui.dp(a,8);tabRow.addView(group,lp);
      }
      page.addView(tabRow);Ui.gap(page,12);
      LinearLayout caption=Ui.row(a);TextView order=Ui.text(a,fans?"最近关注你的用户":"按关注时间从新到旧",14,Design.MUTED);
      caption.addView(order,new LinearLayout.LayoutParams(0,-2,1));
      refresh=button(a,"刷新");refresh.setTag("follow-refresh");refresh.setTextColor(Design.RED);
      refresh.setOnClickListener(v->load(false));caption.addView(refresh);page.addView(caption);
      headerError=error(a);headerError.setTag("follow-refresh-error");page.addView(headerError);Ui.gap(page,8);
      rows=Ui.col(a);rows.setTag("follow-rows");page.addView(rows,new LinearLayout.LayoutParams(-1,-2));
      Ui.gap(page,16);status=Ui.text(a,"",14,Design.MUTED);status.setTag("follow-status");
      status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);page.addView(status);
      footerError=error(a);footerError.setTag("follow-page-error");page.addView(footerError);Ui.gap(page,8);
      more=button(a,"加载更多");more.setTag("follow-more");more.setOnClickListener(v->load(true));page.addView(more,new LinearLayout.LayoutParams(-1,-2));
      paint();
    }
    FollowPage data(){return fans?a.repo.followers():a.repo.following();}
    void controls() {
      boolean busy=loading||writing;
      User actor=a.repo.currentUser();if(actor!=null){tabs[0].setText("关注 · "+actor.followingCount());tabs[1].setText("粉丝 · "+actor.followerCount());}
      for(Button tab:tabs){tab.setEnabled(!busy);tab.setAlpha(busy?.55f:1f);}
      refresh.setEnabled(!busy);refresh.setText(loading?"加载中…":"刷新");refresh.setAlpha(busy?.55f:1f);
      more.setEnabled(!busy);more.setText(loading?"加载中…":"加载更多");more.setAlpha(busy?.55f:1f);
      for(Person person:people.values())paintPerson(person);
    }
    void paint() {
      FollowPage data=data();Set<String> ids=new HashSet<>();for(var user:data.items())ids.add(user.id());
      for(String id:new ArrayList<>(people.keySet()))if(!ids.contains(id)){rows.removeView(people.remove(id).view);}
      if(people.isEmpty())rows.removeAllViews();
      for(int i=0;i<data.items().size();i++) {
        FollowUser user=data.items().get(i);Person person=people.get(user.id());
        if(person==null){person=create(user);people.put(user.id(),person);}
        person.user=user;
        if(rows.indexOfChild(person.view)!=i){if(person.view.getParent()==rows)rows.removeView(person.view);rows.addView(person.view,i);}
      }
      if(data.items().isEmpty())Ui.empty(rows,fans?"还没有粉丝":"你还没有关注任何人",fans?"分享食记，让更多同学发现你。":"遇到喜欢的食记作者时，可以关注对方。");
      status.setText("已显示 "+data.items().size()+" / "+data.total()+" 人"+(!data.hasMore()&&!data.items().isEmpty()?" · 已经到底了":""));
      more.setVisibility(data.hasMore()?View.VISIBLE:View.GONE);controls();
    }
    void load(boolean append) {
      if(loading||writing||a.operationInFlight())return;
      headerError.setText("");footerError.setText("");
      loading=true;controls();status.setText(append?"正在加载更多…":"正在刷新…");
      TextView error=append?footerError:headerError;
      a.read("follow-list:"+fans,append?more:refresh,error,
          ()->{if(append)a.repo.loadMoreFollows(fans);else a.repo.loadFollows(fans);},
          ()->{for(Person p:people.values())p.error.setText("");paint();if(!append)a.scroll.post(()->a.scroll.scrollTo(0,0));},
          ()->{loading=false;status.setText("已显示 "+data().items().size()+" / "+data().total()+" 人");controls();});
    }
    Person create(FollowUser user) {
      Person person=new Person();person.user=user;person.view=Ui.col(a);person.view.setTag("follow-row:"+user.id());
      LinearLayout line=Ui.row(a);line.setPadding(0,Ui.dp(a,16),0,Ui.dp(a,16));
      String name=user.displayName().isBlank()?"校园干饭人":user.displayName();
      View avatar=Ui.image(a,Ui.thumb(user.avatarUrl()),new String(Character.toChars(name.codePointAt(0))),false);
      avatar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);avatar.setBackground(Ui.shape(a,Design.ALT,Design.BORDER,999));avatar.setClipToOutline(true);
      LinearLayout.LayoutParams avatarLp=new LinearLayout.LayoutParams(Ui.dp(a,44),Ui.dp(a,44));avatarLp.rightMargin=Ui.dp(a,12);line.addView(avatar,avatarLp);
      LinearLayout copy=Ui.col(a);TextView title=Ui.text(a,name,16,Design.INK);copy.addView(title);Ui.gap(copy,4);
      title.setFocusable(true);
      title.setOnClickListener(view -> a.go("community-author", user.id()));
      person.relation=Ui.text(a,"",12,Design.MUTED);copy.addView(person.relation);
      line.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
      person.action=button(a,"");person.action.setTag("follow-action:"+user.id());
      LinearLayout.LayoutParams actionLp=new LinearLayout.LayoutParams(-2,-2);actionLp.leftMargin=Ui.dp(a,8);line.addView(person.action,actionLp);
      person.action.setOnClickListener(v->{
        if(loading||writing||a.operationInFlight())return;
        if(!person.user.followedByMe()){write(person);return;}
        new AlertDialog.Builder(a).setTitle("取消关注？").setMessage("将不再关注“"+name+"”，之后仍可重新关注。")
            .setNegativeButton("再想想",null).setPositiveButton("取消关注",(dialog,which)->write(person)).show();
      });
      person.view.addView(line);person.error=error(a);person.error.setTag("follow-error:"+user.id());person.view.addView(person.error);
      person.error.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){if(s.length()>0){writing=false;controls();}}public void afterTextChanged(Editable value){}});
      View divider=new View(a);divider.setBackgroundColor(Design.BORDER);person.view.addView(divider,new LinearLayout.LayoutParams(-1,Ui.dp(a,1)));return person;
    }
    void paintPerson(Person person) {
      boolean followed=person.user.followedByMe();Button action=person.action;
      action.setText(followed?(fans?"互相关注":"已关注"):"+ 关注");action.setTextColor(followed?Design.MUTED:Design.RED);
      action.setEnabled(!loading&&!writing);action.setAlpha(loading||writing?.55f:1f);
      action.setContentDescription((followed?"取消关注":"关注")+person.user.displayName());
      person.relation.setText(fans?(followed?"你们已互相关注":"关注了你"):"已关注的用户");
    }
    void write(Person person) {
      if(loading||writing||a.operationInFlight())return;
      boolean next=!person.user.followedByMe();writing=true;controls();person.action.setText("处理中…");
      a.request(person.action,person.error,()->a.repo.setFollow(person.user.id(),next),()->{
        writing=false;int y=a.scroll.getScrollY();paint();status.setText(next?"关注成功":"已取消关注");a.scroll.post(()->a.scroll.scrollTo(0,y));
      });
    }
  }
  private FollowListScreen(){}
}
