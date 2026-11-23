package cn.edu.bjtu.foodtime.presentation;

import android.view.*;
import android.widget.*;
import cn.edu.bjtu.foodtime.domain.model.Models.*;
import cn.edu.bjtu.foodtime.domain.model.PriceHistory;
import java.util.*;

/** Hall menu: independent stall rail and continuous dishes; list requests are bounded and route scoped. */
final class CanteenMenu {
  private final MainActivity activity;
  private final LinearLayout host, sections, rail;
  private final ScrollView dishes, navigation;
  private final List<Stall> stalls;
  private final Map<String,LinearLayout> groups=new LinkedHashMap<>();
  private final Map<String,TextView> buttons=new HashMap<>();
  private final Set<String> loaded=new HashSet<>(), loading=new HashSet<>();
  private final ArrayDeque<Stall> queue=new ArrayDeque<>();
  private final String key, requestKey="hall-menu:"+UUID.randomUUID();
  private String selected="", requested="";
  private int inFlight;
  private final int savedOffset;
  private boolean restored;
  private boolean detached;

  CanteenMenu(MainActivity activity,LinearLayout page,List<Stall> stalls) {
    this.activity=activity;this.stalls=List.copyOf(stalls);key=activity.draftKey("menu-scroll");savedOffset=activity.drafts.getInt(key,0);
    host=Ui.row(activity);host.setTag("canteen-menu");host.setGravity(Gravity.TOP);
    navigation=viewport(activity);navigation.setTag("canteen-menu-rail");navigation.setBackgroundColor(Design.ALT);
    rail=Ui.col(activity);navigation.addView(rail);host.addView(navigation,new LinearLayout.LayoutParams(Ui.dp(activity,92),-1));
    dishes=viewport(activity);dishes.setTag("canteen-menu-dishes");sections=Ui.col(activity);dishes.addView(sections);host.addView(dishes,new LinearLayout.LayoutParams(0,-1,1));
    dishes.setOnTouchListener((v,event)->{if(event.getActionMasked()==MotionEvent.ACTION_DOWN||event.getActionMasked()==MotionEvent.ACTION_MOVE){requested="";restored=true;}return false;});
    for(Stall stall:stalls){
      TextView tab=Ui.button(activity,stall.name(),false,()->select(stall.id(),true));tab.setTag("menu-stall:"+stall.id());tab.setTextSize(14);tab.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);tab.setMinHeight(Ui.dp(activity,60));tab.setPadding(Ui.dp(activity,10),Ui.dp(activity,14),Ui.dp(activity,8),Ui.dp(activity,14));tab.setBackground(Ui.ripple(activity,Design.ALT,0,0));
      rail.addView(tab,new LinearLayout.LayoutParams(-1,-2));buttons.put(stall.id(),tab);
      LinearLayout group=Ui.col(activity);group.setTag("menu-section:"+stall.id());group.setPadding(Ui.dp(activity,12),Ui.dp(activity,10),Ui.dp(activity,12),Ui.dp(activity,12));groups.put(stall.id(),group);sections.addView(group);
      header(group,stall);placeholder(group,"加载中…");queue.add(stall);
    }
    page.addView(host,new LinearLayout.LayoutParams(-1,Ui.dp(activity,540)));
    host.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View v){detached=false;}public void onViewDetachedFromWindow(View v){detached=true;queue.clear();}});
    dishes.setOnScrollChangeListener((v,x,y,oldX,oldY)->{activity.drafts.putInt(key,y);follow();});
    host.post(()->{
      if(activity.scroll!=null&&activity.scroll.getHeight()>0){host.getLayoutParams().height=Math.max(Ui.dp(activity,300),activity.scroll.getHeight()-Ui.dp(activity,8));host.requestLayout();}
      if(!stalls.isEmpty())highlight(stalls.get(0).id());
      pump();
    });
  }
  private static ScrollView viewport(MainActivity a){
    ScrollView scroll=new ScrollView(a){
      @Override public boolean onInterceptTouchEvent(MotionEvent event){
        boolean intercepted=super.onInterceptTouchEvent(event);
        // Down can belong to a clickable dish row, so onTouchEvent alone is too late.
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN)getParent().requestDisallowInterceptTouchEvent(true);
        return intercepted;
      }
      @Override public boolean onTouchEvent(MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_DOWN)getParent().requestDisallowInterceptTouchEvent(true);if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL)getParent().requestDisallowInterceptTouchEvent(false);return super.onTouchEvent(e);}
    };
    scroll.setFillViewport(false);scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);return scroll;
  }
  private void header(LinearLayout group,Stall stall){
    LinearLayout title=Ui.row(activity);title.addView(Ui.title(activity,stall.name(),19),new LinearLayout.LayoutParams(0,-2,1));
    TextView info=Ui.button(activity,"详情 ›",false,()->activity.go("dishes",stall.id()));info.setTextSize(12);info.setBackground(Ui.ripple(activity,Design.PAPER,0,8));info.setContentDescription(stall.name()+"档口详情");title.addView(info);group.addView(title);
  }
  private void placeholder(LinearLayout group,String value){TextView state=Ui.text(activity,value,14,Design.MUTED);state.setMinHeight(Ui.dp(activity,100));state.setGravity(Gravity.CENTER_VERTICAL);group.addView(state);}
  private void pump(){
    if(detached||!host.isAttachedToWindow())return;
    while(inFlight<2&&!queue.isEmpty()){
      Stall stall=queue.removeFirst();if(loaded.contains(stall.id())||loading.contains(stall.id()))continue;
      inFlight++;loading.add(stall.id());LinearLayout group=groups.get(stall.id());
      TextView error=Ui.text(activity,"",13,Design.RED);group.addView(error);
      activity.read(requestKey+":"+stall.id(),null,error,()->activity.repo.loadDishes(stall.id()),()->{
        if(detached)return;
        group.removeAllViews();header(group,stall);loaded.add(stall.id());
        List<Dish> items=activity.repo.dishes(stall.id());
        if(items.isEmpty())placeholder(group,"暂无菜品");
        else for(Dish dish:items)append(group,dish);
        restoreRequested();
      },()->{
        inFlight--;loading.remove(stall.id());
        group.post(()->{if(detached)return;if(!loaded.contains(stall.id())){group.removeAllViews();header(group,stall);group.addView(error);TextView retry=Ui.button(activity,"重试",false,()->{group.removeAllViews();header(group,stall);placeholder(group,"加载中…");queue.addFirst(stall);pump();});group.addView(retry);}
          pump();if(queue.isEmpty()&&inFlight==0){if(!restored&&requested.isBlank()){restored=true;dishes.scrollTo(0,savedOffset);}restoreRequested();}
        });
      });
    }
  }
  private void append(LinearLayout group,Dish dish){
    LinearLayout row=Ui.row(activity);row.setGravity(Gravity.TOP);row.setPadding(0,Ui.dp(activity,12),0,Ui.dp(activity,12));row.setBackground(Ui.ripple(activity,Design.PAPER,0,10));
    View image;
    if(dish.coverImageUrl()!=null&&!dish.coverImageUrl().isBlank()){
      image=Ui.image(activity,Ui.thumb(dish.coverImageUrl()),"",false);
      if(image instanceof ViewGroup frame)for(int i=0;i<frame.getChildCount();i++)if(frame.getChildAt(i) instanceof ImageView photo)photo.setScaleType(ImageView.ScaleType.FIT_CENTER);
    }else image=Ui.foodDoodle(activity,0,Design.MUTED);
    image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);row.addView(image,new LinearLayout.LayoutParams(Ui.dp(activity,76),Ui.dp(activity,76)));
    LinearLayout copy=Ui.col(activity);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.leftMargin=Ui.dp(activity,10);
    TextView name=Ui.text(activity,dish.name(),16,Design.INK);name.setTypeface(Design.body(),android.graphics.Typeface.BOLD);copy.addView(name);Ui.gap(copy,6);
    if(!dish.description().isBlank()){TextView description=Ui.text(activity,dish.description(),12,Design.MUTED);description.setMaxLines(2);description.setEllipsize(android.text.TextUtils.TruncateAt.END);copy.addView(description);Ui.gap(copy,6);}
    copy.addView(Ui.text(activity,dish.averageScore()==null?"暂无评分":dish.averageScore().stripTrailingZeros().toPlainString()+" 分",12,Design.MUTED));Ui.gap(copy,6);copy.addView(Ui.text(activity,PriceHistory.price(dish.price(),dish.priceUnit()),18,Design.RED));row.addView(copy,lp);
    row.setTag("menu-dish:"+dish.id());row.setFocusable(true);row.setClickable(true);row.setContentDescription(dish.name()+"，"+PriceHistory.price(dish.price(),dish.priceUnit()));row.setOnClickListener(v->activity.go("detail",dish.id()));group.addView(row);
    View line=new View(activity);line.setBackgroundColor(Design.BORDER);group.addView(line,new LinearLayout.LayoutParams(-1,Ui.dp(activity,1)));
  }
  private void select(String id,boolean smooth){
    requested=id;highlight(id);LinearLayout target=groups.get(id);if(target==null)return;
    if(smooth)dishes.smoothScrollTo(0,target.getTop());else dishes.scrollTo(0,target.getTop());
    for(Stall stall:stalls)if(stall.id().equals(id)&&!loaded.contains(id)&&!loading.contains(id)){queue.remove(stall);queue.addFirst(stall);pump();}
  }
  private void restoreRequested(){if(!requested.isBlank())sections.post(()->{if(!detached){LinearLayout target=groups.get(requested);if(target!=null)dishes.scrollTo(0,target.getTop());}});}
  private void follow(){
    if(stalls.isEmpty())return;
    String id=stalls.get(0).id();for(Stall stall:stalls)if(groups.get(stall.id()).getTop()<=dishes.getScrollY()+Ui.dp(activity,24))id=stall.id();
    if(sections.getHeight()>dishes.getHeight()&&dishes.getScrollY()+dishes.getHeight()>=sections.getHeight()-2)id=stalls.get(stalls.size()-1).id();
    highlight(id);
  }
  private void highlight(String id){
    if(id.equals(selected))return;selected=id;
    for(var entry:buttons.entrySet()){boolean active=entry.getKey().equals(id);TextView button=entry.getValue();button.setSelected(active);button.setTextColor(active?Design.RED:Design.MUTED);button.setBackground(Ui.ripple(activity,active?Design.PAPER:Design.ALT,0,0));}
    TextView button=buttons.get(id);if(button!=null)navigation.smoothScrollTo(0,Math.max(0,button.getTop()-navigation.getHeight()/2));
  }
}
