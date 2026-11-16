package cn.edu.bjtu.foodtime.presentation;

import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.edu.bjtu.foodtime.domain.model.Models.CommunityPostPage;
import cn.edu.bjtu.foodtime.domain.model.Models.FavoriteGroup;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** Favorites own their filter/page state; the discovery feed and dish favorites stay independent. */
final class FavoritePostsScreen {
  private final MainActivity a;
  private final LinearLayout controls,rows;
  private final TextView status,error;
  private final String key="favorite-posts:"+UUID.randomUUID();
  private List<FavoriteGroup> groups=List.of();
  private String selected;
  private boolean unfiled;
  private int epoch;
  private int groupEpoch;

  FavoritePostsScreen(MainActivity a,LinearLayout page) {
    this.a=a;controls=Ui.col(a);rows=Ui.col(a);
    status=Ui.text(a,"正在加载收藏…",14,Design.MUTED);error=Ui.text(a,"",14,Design.RED);
    page.addView(controls);page.addView(status);page.addView(error);
    Ui.action(page,"刷新收藏与分组",false,this::refresh);page.addView(rows);paintControls();
  }
  void refresh(){loadGroups();load(1);}
  private void loadGroups(){
    int ticket=++groupEpoch;
    AtomicReference<List<FavoriteGroup>> value=new AtomicReference<>();
    a.read(key+":groups:"+ticket,null,error,()->value.set(a.repo.favoriteGroups("post")),()->{
      if(ticket!=groupEpoch)return;
      groups=value.get();
      if(selected!=null&&groups.stream().noneMatch(group->group.id().equals(selected))){selected=null;unfiled=false;load(1);}
      paintControls();
    },()->{});
  }
  private void paintControls(){
    controls.removeAllViews();LinearLayout actions=Ui.row(a);
    TextView create=Ui.button(a,"+ 新建分组",false,()->FavoriteGroupTools.create(a,"post",this::refresh));
    create.setTag("favorite-post-create-group");actions.addView(create);
    controls.addView(actions);Ui.gap(controls,8);
    LinearLayout chips=Ui.row(a);
    addChip(chips,"全部",selected==null&&!unfiled,()->select(null,false));
    addChip(chips,"默认分组",unfiled,()->select(null,true));
    for(var group:groups)addChip(chips,group.name()+" ("+group.itemCount()+")",group.id().equals(selected),()->select(group.id(),false));
    HorizontalScrollView scroll=new HorizontalScrollView(a);scroll.setHorizontalScrollBarEnabled(false);
    scroll.addView(chips);controls.addView(scroll,new LinearLayout.LayoutParams(-1,-2));
    controls.addView(Ui.text(a,"直接收藏即可存入默认分组，无需新建。帖子和菜品分别管理。",13,Design.MUTED));
    Ui.gap(controls,8);
  }
  private void addChip(LinearLayout row,String title,boolean checked,Runnable action){
    TextView button=Ui.button(a,title,checked,action);button.setSelected(checked);
    LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-2,-2);params.rightMargin=Ui.dp(a,8);row.addView(button,params);
  }
  private void select(String id,boolean onlyUnfiled){selected=id;unfiled=onlyUnfiled;paintControls();load(1);}
  private void load(int page){
    int ticket=++epoch;String id=selected;boolean onlyUnfiled=unfiled;
    rows.removeAllViews();status.setText("正在加载收藏…");AtomicReference<CommunityPostPage> value=new AtomicReference<>();
    a.read(key+":"+ticket,null,error,()->value.set(a.repo.favoritePosts(page,id,onlyUnfiled)),()->{
      if(ticket!=epoch)return;
      var result=value.get();status.setText(result.items().isEmpty()?"这个分组还没有收藏食记。":"共 "+result.total()+" 篇收藏");
      if(!result.items().isEmpty())CommunityScreen.waterfall(a,rows,result.items());
      if(page>1||result.hasMore()){
        NumberedPagination pager=new NumberedPagination(a,this::load);rows.addView(pager,new LinearLayout.LayoutParams(-1,-2));
        pager.state(page,(int)Math.max(1,(result.total()+19)/20),true);
      }
      if(result.items().isEmpty())Ui.action(rows,"刷新收藏",false,this::refresh);
    },()->{if(ticket==epoch&&value.get()==null)status.setText("收藏加载失败，可点击刷新重试。");});
  }
}
