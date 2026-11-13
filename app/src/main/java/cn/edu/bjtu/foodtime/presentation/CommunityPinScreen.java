package cn.edu.bjtu.foodtime.presentation;

import android.widget.*;
import android.view.View;
import android.text.*;
import cn.edu.bjtu.foodtime.domain.model.CommunityPins;
import cn.edu.bjtu.foodtime.domain.model.Models.CommunityPost;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

final class CommunityPinScreen {
  private final MainActivity a;private final LinearLayout rows;private final TextView error,refresh;
  private final MainActivity.Route route;private final String owner;private final List<View> controls=new ArrayList<>();
  private CommunityPins.Snapshot data;private boolean busy,loading;
  private CommunityPinScreen(MainActivity a,LinearLayout page,MainActivity.Route route){
    this.a=a;this.route=route;owner=a.repo.currentUser().id()+":"+a.repo.currentUser().role();
    a.heading(page,"社区手账","置顶管理","最多保留3条，按列表顺序展示。待审核或被拒时保留名额，审核通过后恢复；删除或下架会取消置顶。");
    error=Ui.text(a,"",14,Design.RED);Ui.observeError(error);page.addView(error);
    refresh=Ui.button(a,"刷新置顶列表",false,this::load);refresh.setTag("community-pins-refresh");page.addView(refresh);controls.add(refresh);
    rows=Ui.col(a);page.addView(rows);
    error.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){}public void afterTextChanged(Editable text){if(text.length()>0&&current()){busy=false;loading=false;paint();}}});
    page.post(this::load);
  }
  static boolean allowed(MainActivity a){return a.repo.currentUser()!=null&&a.repo.capabilities().can("community.pin.manage");}
  static void show(MainActivity a,LinearLayout page,MainActivity.Route route){if(!allowed(a))throw new IllegalStateException("无权管理置顶");new CommunityPinScreen(a,page,route);}
  private boolean current(){var user=a.repo.currentUser();return rows.isAttachedToWindow()&&a.route().equals(route)&&user!=null&&owner.equals(user.id()+":"+user.role())&&allowed(a);}
  private void disable(){for(var control:controls)control.setEnabled(false);}
  private void load(){if(busy||loading||!current())return;loading=true;disable();var result=new AtomicReference<CommunityPins.Snapshot>();a.read("community-pins:"+UUID.randomUUID(),refresh,error,()->result.set(a.repo.managedPins()),()->{if(current()){loading=false;data=result.get();paint();}},()->{if(current()){loading=false;paint();}});}
  private void change(View button,Supplier<CommunityPins.Snapshot> work){if(busy||loading||!current()||data==null)return;busy=true;disable();var result=new AtomicReference<CommunityPins.Snapshot>();a.request(button,error,()->{if(a.repo.currentUser()==null||!owner.equals(a.repo.currentUser().id()+":"+a.repo.currentUser().role()))throw new IllegalStateException("账号已变化，请重新打开置顶管理");result.set(work.get());},()->{if(current()){busy=false;data=result.get();a.requireFreshContent("community","");paint();a.message("置顶列表已更新");}});}
  private void move(View button,int index,int offset){var ids=new ArrayList<String>();for(var row:data.items())ids.add(row.id());Collections.swap(ids,index,index+offset);long revision=data.revision();change(button,()->a.repo.reorderPins(ids,revision));}
  private void paint(){rows.removeAllViews();controls.clear();controls.add(refresh);refresh.setEnabled(!busy&&!loading);if(data==null)return;
    CommunityPost target=route.id().isBlank()?null:a.repo.communityPost(route.id());
    if(target!=null&&target.status().equals("approved")&&data.items().stream().noneMatch(row->row.id().equals(target.id()))){TextView pin=Ui.button(a,"置顶当前帖子",true,()->{});pin.setTag("community-pin-current");pin.setEnabled(!busy&&!loading&&data.items().size()<3);rows.addView(pin);controls.add(pin);long rev=data.revision();pin.setOnClickListener(v->change(v,()->a.repo.pinPost(target.id(),rev)));Ui.gap(rows,12);}
    if(data.items().isEmpty())Ui.empty(rows,"当前没有置顶食记","");
    for(int i=0;i<data.items().size();i++){
      int index=i;var row=data.items().get(i);LinearLayout card=Ui.card(rows);card.setTag("community-pin:"+row.id());card.addView(Ui.title(a,(i+1)+". "+row.title(),18));
      card.addView(Ui.text(a,"@"+row.userDisplayName()+" · "+(row.status().equals("approved")?"公开中":row.status().equals("pending")?"待审核，保留名额":"未通过，保留名额"),14,Design.MUTED));Ui.gap(card,10);
      LinearLayout actions=Ui.row(a);TextView up=Ui.button(a,"上移",false,()->{}),down=Ui.button(a,"下移",false,()->{});up.setTag("community-pin-up:"+row.id());down.setTag("community-pin-down:"+row.id());
      up.setEnabled(!busy&&!loading&&i>0);down.setEnabled(!busy&&!loading&&i<data.items().size()-1);up.setOnClickListener(v->move(v,index,-1));down.setOnClickListener(v->move(v,index,1));
      actions.addView(up,new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.leftMargin=Ui.dp(a,10);actions.addView(down,lp);card.addView(actions);Ui.gap(card,8);
      TextView remove=Ui.button(a,"取消置顶",false,()->{});remove.setTag("community-unpin:"+row.id());remove.setEnabled(!busy&&!loading);long rev=data.revision();remove.setOnClickListener(v->change(v,()->a.repo.unpinPost(row.id(),rev)));card.addView(remove);controls.addAll(List.of(up,down,remove));
    }
  }
}
