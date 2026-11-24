package cn.edu.bjtu.foodtime.presentation;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.edu.bjtu.foodtime.domain.model.Models.CommunityPostPage;
import cn.edu.bjtu.foodtime.domain.model.Models.FavoriteGroup;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** The index loads folders only; a separate route owns each folder's private posts. */
final class FavoritePostsScreen {
  private final MainActivity a;
  private final LinearLayout controls,rows;
  private final TextView status,error;
  private final String key="favorite-posts:"+UUID.randomUUID(),selected;
  private final boolean index,unfiled;
  private List<FavoriteGroup> groups=List.of();
  private int epoch,groupEpoch;

  FavoritePostsScreen(MainActivity a,LinearLayout page){this(a,page,null);}
  FavoritePostsScreen(MainActivity a,LinearLayout page,String folder){
    this.a=a;index=folder==null;unfiled="unfiled".equals(folder);selected=index||unfiled||"all".equals(folder)?null:folder;
    controls=Ui.col(a);rows=Ui.col(a);status=Ui.text(a,"",13,Design.MUTED);error=Ui.text(a,"",13,Design.RED);
    page.addView(controls);page.addView(status);page.addView(error);page.addView(rows);paintControls();
    page.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View v){}public void onViewDetachedFromWindow(View v){epoch++;groupEpoch++;}});
  }
  void refresh(){error.setText("");loadGroups();if(!index)load(1);}
  private void loadGroups(){
    int ticket=++groupEpoch;var value=new AtomicReference<List<FavoriteGroup>>();if(index)status.setText("正在加载收藏夹…");
    a.read(key+":groups:"+ticket,null,error,()->value.set(a.repo.favoriteGroups("post")),()->{
      if(ticket!=groupEpoch)return;groups=value.get();paintControls();if(index)status.setText("");
    },()->{if(index&&ticket==groupEpoch&&value.get()==null){status.setText("收藏夹加载失败");TextView retry=Ui.button(a,"重试",false,this::refresh);rows.removeAllViews();rows.addView(retry);}});
  }
  private void paintControls(){
    controls.removeAllViews();
    if(!index){String title=unfiled?"默认分组":selected==null?"全部收藏":groups.stream().filter(g->g.id().equals(selected)).map(FavoriteGroup::name).findFirst().orElse("收藏夹");TextView heading=Ui.title(a,title,23);heading.setTag("favorite-post-folder-title");controls.addView(heading);return;}
    TextView create=Ui.button(a,"+ 新建收藏夹",false,()->FavoriteGroupTools.create(a,"post",this::refresh));create.setTag("favorite-post-create-group");controls.addView(create);Ui.gap(controls,12);
    addFolder("全部","all",null);addFolder("默认分组","unfiled",null);
    for(FavoriteGroup group:groups)addFolder(group.name(),group.id(),group.itemCount());
  }
  private void addFolder(String title,String id,Long count){
    LinearLayout card=Ui.card(controls);card.setTag("favorite-post-folder:"+id);LinearLayout row=Ui.row(a);card.addView(row);
    TextView icon=Ui.text(a,"",24,Design.RED);icon.setCompoundDrawablesWithIntrinsicBounds(cn.edu.bjtu.foodtime.R.drawable.ic_favorite_folder,0,0,0);row.addView(icon,new LinearLayout.LayoutParams(Ui.dp(a,38),Ui.dp(a,36)));
    LinearLayout copy=Ui.col(a);row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));TextView name=Ui.text(a,title,16,Design.INK);name.setSingleLine();name.setEllipsize(android.text.TextUtils.TruncateAt.END);copy.addView(name);if(count!=null)copy.addView(Ui.text(a,count+" 篇",12,Design.MUTED));
    row.addView(Ui.text(a,"›",22,Design.MUTED));card.setContentDescription(title);card.setFocusable(true);card.setClickable(true);card.setOnClickListener(v->a.go("community-favorite-folder",id));
  }
  private void load(int page){
    int ticket=++epoch;rows.removeAllViews();status.setText("正在加载收藏…");var value=new AtomicReference<CommunityPostPage>();
    a.read(key+":"+ticket,null,error,()->value.set(a.repo.favoritePosts(page,selected,unfiled)),()->{
      if(ticket!=epoch)return;var result=value.get();status.setText(result.items().isEmpty()?"这个收藏夹还没有食记":"共 "+result.total()+" 篇收藏");
      if(!result.items().isEmpty())CommunityScreen.waterfall(a,rows,result.items());
      if(page>1||result.hasMore()){NumberedPagination pager=new NumberedPagination(a,this::load);rows.addView(pager,new LinearLayout.LayoutParams(-1,-2));pager.state(page,(int)Math.max(1,(result.total()+19)/20),true);}
    },()->{if(ticket==epoch&&value.get()==null){status.setText("收藏加载失败");rows.addView(Ui.button(a,"重试",false,()->load(page)));}});
  }
}
