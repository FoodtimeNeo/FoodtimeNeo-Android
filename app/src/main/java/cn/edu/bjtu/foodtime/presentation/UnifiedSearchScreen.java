package cn.edu.bjtu.foodtime.presentation;

import android.app.AlertDialog;
import android.text.*;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import cn.edu.bjtu.foodtime.domain.model.SearchModels;
import cn.edu.bjtu.foodtime.domain.model.SearchModels.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Shared search entry for catalog and community; request coordination is local to this page. */
final class UnifiedSearchScreen {
  static void show(MainActivity a,LinearLayout page,MainActivity.Route route){new UnifiedSearchScreen(a,page,route);}
  private final MainActivity a;
  private final LinearLayout page,toolbar,tabs,tools,results,discovery,history;
  private final EditText input;
  private final TextView error,status,sortButton,distanceButton,filterButton;
  private final NumberedPagination pagination;
  private final String key,requestKey="unified-search:"+UUID.randomUUID();
  private final List<String> halls=new ArrayList<>();
  private String tab,sort="relevance",type="all",submitted="";
  private BigDecimal min,max,rating;
  private Double latitude,longitude;
  private long locatedAt,revision;
  private boolean searched,loading,editingHistory,discoveryLoaded,locating;
  private int currentPage=1,columns=4;
  private AlertDialog dialog;
  private PopupWindow popup;
  private Page<Stall> stallPage;
  private Page<Note> notePage;
  private final List<cn.edu.bjtu.foodtime.domain.model.Models.Hall> availableHalls=new ArrayList<>();
  private UnifiedSearchScreen(MainActivity a,LinearLayout page,MainActivity.Route route){
    this.a=a;this.page=page;key="dish-search::unified-search:"+a.repo.currentUser().id();
    tab=route.kind().equals("community-search")?"notes":a.drafts.getString(key+":tab","dishes");
    submitted=a.drafts.getString(key+":q","");
    toolbar=a.searchToolbar();toolbar.setTag("unified-search-toolbar");
    LinearLayout bar=Ui.row(a);toolbar.addView(bar);
    TextView back=small("‹",()->a.back());back.setContentDescription("返回");bar.addView(back,new LinearLayout.LayoutParams(Ui.dp(a,36),Ui.dp(a,44)));
    LinearLayout field=Ui.row(a);field.setPadding(Ui.dp(a,10),0,Ui.dp(a,4),0);field.setBackground(Ui.shape(a,Design.ALT,Design.BORDER,24));bar.addView(field,new LinearLayout.LayoutParams(0,-2,1));
    input=new EditText(a);input.setSingleLine();input.setTextSize(15);input.setTypeface(Design.body());input.setTextColor(Design.INK);input.setHintTextColor(Design.MUTED);input.setBackgroundColor(android.graphics.Color.TRANSPARENT);input.setHint("菜品、笔记");input.setContentDescription("搜索菜品或笔记");input.setTag("dish-search-keyword");input.setPadding(0,Ui.dp(a,6),0,Ui.dp(a,6));input.setMinHeight(Ui.dp(a,40));input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(50)});input.setText(submitted);input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);field.addView(input,new LinearLayout.LayoutParams(0,-2,1));
    input.setOnEditorActionListener((v,action,event)->{if(action!=EditorInfo.IME_ACTION_SEARCH)return false;submit();return true;});
    TextView search=small("搜索",this::submit);search.setTag("dish-search-submit");search.setTextColor(android.graphics.Color.WHITE);search.setBackground(Ui.ripple(a,Design.RED,0,24));field.addView(search);
    bar.addView(CommunityScreen.iconButton(a,cn.edu.bjtu.foodtime.R.drawable.ic_community_home,"回到食堂首页",a::home),new LinearLayout.LayoutParams(Ui.dp(a,44),Ui.dp(a,44)));
    tabs=Ui.row(a);toolbar.addView(tabs);paintTabs();
    tools=Ui.row(a);toolbar.addView(tools);sortButton=small("综合排序⌄",this::showSort);tools.addView(sortButton);
    distanceButton=small("距离最近",this::locate);tools.addView(distanceButton);
    Space space=new Space(a);tools.addView(space,new LinearLayout.LayoutParams(0,1,1));
    filterButton=small("",this::showFilters);filterButton.setCompoundDrawablesWithIntrinsicBounds(cn.edu.bjtu.foodtime.R.drawable.ic_search_filter,0,0,0);filterButton.setContentDescription("筛选");filterButton.setTag("dish-search-filter-toggle");tools.addView(filterButton,new LinearLayout.LayoutParams(Ui.dp(a,44),Ui.dp(a,40)));
    error=Ui.text(a,"",13,Design.RED);Ui.observeError(error);page.addView(error);status=Ui.text(a,"",12,Design.MUTED);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);page.addView(status);
    history=Ui.col(a);page.addView(history);discovery=Ui.col(a);discovery.setTag("dish-search-discovery");page.addView(discovery);results=Ui.col(a);results.setTag("dish-search-results");page.addView(results);
    pagination=new NumberedPagination(a,this::load);page.addView(pagination);
    page.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{int width=(int)((r-l)/a.getResources().getDisplayMetrics().density);int next=SearchModels.columns(width);if(next!=columns){columns=next;if(stallPage!=null)load(currentPage);}});
    page.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View v){}public void onViewDetachedFromWindow(View v){revision++;a.searchLocation.clear();latitude=null;longitude=null;if(dialog!=null)dialog.dismiss();if(popup!=null)popup.dismiss();}});
    var hallResponse=new AtomicReference<List<cn.edu.bjtu.foodtime.domain.model.Models.Hall>>();a.read(requestKey+":halls",null,error,()->hallResponse.set(a.repo.loadHalls()),()->{availableHalls.clear();availableHalls.addAll(hallResponse.get());},()->{});
    restoreFilters();sortButton.setText(label(sort)+"⌄");
    searched=a.drafts.getBoolean(key+":searched",false);
    if(searched)page.post(()->load(Math.max(1,a.drafts.getInt(key+":page",1))));else showDiscovery();
  }
  private TextView small(String label,Runnable action){TextView view=Ui.button(a,label,false,action);view.setTextSize(14);view.setMinHeight(Ui.dp(a,40));view.setPadding(Ui.dp(a,8),Ui.dp(a,6),Ui.dp(a,8),Ui.dp(a,6));view.setBackground(Ui.ripple(a,Design.PAPER,0,8));return view;}
  private void paintTabs(){tabs.removeAllViews();for(String value:List.of("dishes","notes")){TextView button=small(value.equals("dishes")?"菜品":"笔记",()->switchTab(value));button.setTag("search-tab:"+value);button.setTextSize(17);button.setSelected(tab.equals(value));if(tab.equals(value))button.setTextColor(Design.RED);tabs.addView(button,new LinearLayout.LayoutParams(0,Ui.dp(a,44),1));}}
  private void switchTab(String next){if(next.equals(tab))return;saveFilters();revision++;loading=false;tab=next;sort="relevance";type="all";min=max=rating=null;restoreFilters();stallPage=null;notePage=null;paintTabs();sortButton.setText(label(sort)+"⌄");a.drafts.putString(key+":tab",tab);if(searched)load(1);}
  private void submit(){submitted=input.getText().toString().strip();SearchHistoryStore.remember(a,a.repo.currentUser().id(),submitted);searched=true;load(1);}
  private Query query(){
    if(sort.equals("distance")&&(latitude==null||android.os.SystemClock.elapsedRealtime()-locatedAt>300000))throw new IllegalStateException("请点击距离最近，重新定位");
    return new Query(submitted,List.copyOf(halls),min,max,rating,sort,type,sort.equals("distance")?latitude:null,sort.equals("distance")?longitude:null,Math.min(8,columns*2));
  }
  private void load(int number){
    final Query query;try{query=query();}catch(IllegalStateException failure){error.setText(failure.getMessage());return;}
    updateTools();long request=++revision;loading=true;searched=true;currentPage=number;results.setVisibility(View.VISIBLE);history.setVisibility(View.GONE);discovery.setVisibility(View.GONE);results.removeAllViews();stallPage=null;notePage=null;error.setText("");status.setText("正在搜索…");pagination.state(number,1,false);
    AtomicReference<Page<Stall>> stalls=new AtomicReference<>();AtomicReference<Page<Note>> notes=new AtomicReference<>();String selected=tab;
    TextView requestError=new TextView(a);
    a.read(requestKey+":"+request,null,requestError,()->{if(selected.equals("notes"))notes.set(a.repo.searchNotes(query,number));else stalls.set(a.repo.searchStalls(query,number));},()->{
      if(request!=revision)return;stallPage=stalls.get();notePage=notes.get();save();
      if(selected.equals("notes"))paintNotes();else paintStalls();
      long total=selected.equals("notes")?notePage.total():stallPage.total();int size=selected.equals("notes")?notePage.size():stallPage.size();
      int last=(int)Math.min(Integer.MAX_VALUE,Math.max(1,(total-1)/Math.max(1,size)+1));if(number>last){load(last);return;}
      status.setText(total+(selected.equals("notes")?"篇笔记":"个档口"));pagination.state(number,last,true);
    },()->{if(request==revision){loading=false;if(requestError.length()>0)error.setText(requestError.getText());if(stallPage==null&&notePage==null)status.setText("");}});
  }
  private void save(){saveFilters();a.drafts.putString(key+":q",submitted);a.drafts.putString(key+":tab",tab);a.drafts.putBoolean(key+":searched",true);a.drafts.putInt(key+":page",currentPage);}
  private String filterKey(){return key+":"+tab;}
  private void saveFilters(){String prefix=filterKey();a.drafts.putString(prefix+":min",min==null?"":min.toPlainString());a.drafts.putString(prefix+":max",max==null?"":max.toPlainString());a.drafts.putString(prefix+":rating",rating==null?"":rating.toPlainString());a.drafts.putString(prefix+":type",type);a.drafts.putString(prefix+":sort",sort);a.drafts.putStringArrayList(prefix+":halls",new ArrayList<>(halls));}
  private void restoreFilters(){String prefix=filterKey();min=savedAmount(prefix+":min");max=savedAmount(prefix+":max");rating=savedAmount(prefix+":rating");type=a.drafts.getString(prefix+":type","all");sort=a.drafts.getString(prefix+":sort","relevance");if(sort.equals("distance")&&latitude==null)sort="relevance";halls.clear();var saved=a.drafts.getStringArrayList(prefix+":halls");if(saved!=null)halls.addAll(saved);}
  private BigDecimal savedAmount(String name){String value=a.drafts.getString(name,"");try{return value.isBlank()?null:new BigDecimal(value);}catch(NumberFormatException e){return null;}}
  private void showDiscovery(){searched=false;status.setText("");pagination.state(1,1,false);results.setVisibility(View.GONE);discovery.setVisibility(View.VISIBLE);history.setVisibility(View.VISIBLE);paintHistory();if(!discoveryLoaded){new SearchDiscovery(a,discovery);discoveryLoaded=true;}}
  private void paintHistory(){history.removeAllViews();List<String> values=SearchHistoryStore.read(a,a.repo.currentUser().id());if(values.isEmpty())return;LinearLayout title=Ui.row(a);title.addView(Ui.text(a,"历史搜索",16,Design.INK),new LinearLayout.LayoutParams(0,-2,1));title.addView(small(editingHistory?"完成":"编辑",()->{editingHistory=!editingHistory;paintHistory();}));if(editingHistory)title.addView(small("清空",()->Ui.confirm(a,"清空搜索历史？","",()->{SearchHistoryStore.purge(a,a.repo.currentUser().id());paintHistory();})));history.addView(title);
    LinearLayout row=Ui.row(a);history.addView(row);int used=0,limit=a.getResources().getConfiguration().screenWidthDp-28;
    for(String value:values){TextView chip=small(value+(editingHistory?" ×":""),()->{if(editingHistory){SearchHistoryStore.remove(a,a.repo.currentUser().id(),value);paintHistory();}else{input.setText(value);submit();}});chip.setTextSize(13);chip.setMaxWidth(Ui.dp(a,limit));chip.measure(View.MeasureSpec.makeMeasureSpec(Ui.dp(a,limit),View.MeasureSpec.AT_MOST),0);int width=chip.getMeasuredWidth()/Math.max(1,(int)a.getResources().getDisplayMetrics().density)+8;if(used>0&&used+width>limit){row=Ui.row(a);history.addView(row);used=0;}row.addView(chip);used+=width;}
  }
  private void paintStalls(){
    results.removeAllViews();if(stallPage==null)return;
    for(Stall stall:stallPage.items()){
      LinearLayout group=Ui.col(a);group.setPadding(0,Ui.dp(a,10),0,Ui.dp(a,10));group.setTag("search-stall:"+stall.id());results.addView(group);
      LinearLayout identity=Ui.row(a);group.addView(identity);View cover=Ui.image(a,Ui.thumb(stall.imageUrl()),stall.name(),false);identity.addView(cover,new LinearLayout.LayoutParams(Ui.dp(a,44),Ui.dp(a,44)));LinearLayout copy=Ui.col(a);copy.setPadding(Ui.dp(a,8),0,0,0);identity.addView(copy,new LinearLayout.LayoutParams(0,-2,1));TextView name=Ui.text(a,stall.name()+"（"+stall.hallName()+"）",15,Design.INK);name.setMaxLines(2);copy.addView(name);copy.addView(Ui.text(a,(stall.averageScore()==null?"暂无评分":"★ "+stall.averageScore().stripTrailingZeros())+" · "+stall.reviewCount()+"条点评",12,Design.MUTED));identity.setOnClickListener(v->a.go("dishes",stall.id()));identity.setContentDescription(stall.name()+"（"+stall.hallName()+"）");
      if(stall.distanceMeters()!=null)identity.addView(Ui.text(a,SearchModels.distance(stall.distanceMeters()),11,Design.MUTED));
      Ui.gap(group,8);HorizontalScrollView strip=new HorizontalScrollView(a);strip.setHorizontalScrollBarEnabled(false);LinearLayout row=Ui.row(a);row.setGravity(Gravity.TOP);strip.addView(row);group.addView(strip);
      int available=page.getWidth()>0?page.getWidth():Ui.dp(a,a.getResources().getConfiguration().screenWidthDp-28);int width=Math.max(Ui.dp(a,64),(available-Ui.dp(a,8)*(columns-1))/columns);
      for(Dish dish:stall.dishes())appendDish(row,dish,width);
      final int[] next={2};final boolean[] pending={false};final long[] total={stall.matchCount()};final int[] count={stall.dishes().size()};long groupRevision=revision;Query snapshot=query();
      strip.setOnScrollChangeListener((v,x,y,oldX,oldY)->{
        if(pending[0]||count[0]>=total[0]||x+strip.getWidth()<row.getWidth()-Ui.dp(a,36)||groupRevision!=revision)return;
        pending[0]=true;var response=new AtomicReference<Page<Dish>>();TextView requestError=new TextView(a);
        a.read(requestKey+":strip:"+stall.id()+":"+next[0],null,requestError,()->response.set(a.repo.searchStallDishes(snapshot,stall.id(),next[0])),()->{
          if(groupRevision!=revision||!row.isAttachedToWindow())return;var data=response.get();var seen=new HashSet<String>();for(int i=0;i<row.getChildCount();i++)seen.add(String.valueOf(row.getChildAt(i).getTag()));
          for(Dish dish:data.items())if(!seen.contains("search-dish:"+dish.id())){appendDish(row,dish,width);count[0]++;}next[0]++;total[0]=data.items().isEmpty()?count[0]:data.total();
        },()->{pending[0]=false;if(groupRevision==revision&&requestError.length()>0)error.setText(requestError.getText());});
      });
      View divider=new View(a);divider.setBackgroundColor(Design.BORDER);group.addView(divider,new LinearLayout.LayoutParams(-1,Ui.dp(a,1)));
    }
  }
  private void appendDish(LinearLayout row,Dish dish,int width){
    LinearLayout card=Ui.col(a);card.setTag("search-dish:"+dish.id());LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(width,-2);lp.rightMargin=Ui.dp(a,8);row.addView(card,lp);
    card.addView(Ui.image(a,Ui.thumb(dish.imageUrl()),dish.name(),false),new LinearLayout.LayoutParams(-1,width));TextView title=Ui.text(a,dish.name(),13,Design.INK);title.setMaxLines(2);title.setLineSpacing(0,1);title.setPadding(0,Ui.dp(a,2),0,0);title.setEllipsize(TextUtils.TruncateAt.END);card.addView(title);
    TextView price=Ui.text(a,dish.price()==null?"价格未知":"¥"+dish.price().stripTrailingZeros()+(dish.priceUnit().equals("unspecified")?"":"/"+cn.edu.bjtu.foodtime.domain.model.PriceHistory.unit(dish.priceUnit())),13,Design.RED);price.setLineSpacing(0,1);card.addView(price);
    if(dish.averageScore()!=null){TextView score=Ui.text(a,dish.averageScore().stripTrailingZeros()+"分",11,Design.MUTED);score.setLineSpacing(0,1);card.addView(score);}
    card.setContentDescription(dish.name());card.setOnClickListener(v->a.go("detail",dish.id()));
  }
  private void paintNotes(){
    results.removeAllViews();if(notePage==null)return;LinearLayout row=Ui.row(a);row.setGravity(Gravity.TOP);results.addView(row);
    LinearLayout left=Ui.col(a),right=Ui.col(a);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.rightMargin=Ui.dp(a,5);row.addView(left,lp);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,-2,1);rp.leftMargin=Ui.dp(a,5);row.addView(right,rp);
    int i=0;for(Note note:notePage.items()){LinearLayout column=i++%2==0?left:right;CommunityScreen.postCard(a,column,note.post());if(!note.hallName().isBlank())column.addView(Ui.text(a,note.hallName()+" "+SearchModels.distance(note.distanceMeters()),11,Design.MUTED));}
  }
  private void showSort(){if(popup!=null)popup.dismiss();LinearLayout choices=Ui.col(a);String[] values=tab.equals("notes")?new String[]{"relevance","latest","likes","distance"}:new String[]{"relevance","rating_desc","price_asc","price_desc","distance"};for(String value:values){TextView item=small(label(value)+(sort.equals(value)?" ✓":""),()->{popup.dismiss();if(value.equals("distance"))locate();else{sort=value;sortButton.setText(label(value)+"⌄");load(1);}});choices.addView(item,new LinearLayout.LayoutParams(-1,Ui.dp(a,44)));}popup=new PopupWindow(choices,Ui.dp(a,230),-2,true);popup.setBackgroundDrawable(Ui.shape(a,Design.PAPER,Design.BORDER,12));popup.setOutsideTouchable(true);popup.showAsDropDown(sortButton);}
  private String label(String value){return switch(value){case "price_asc"->"价格从低到高";case "price_desc"->"价格从高到低";case "rating_desc"->"评分优先";case "latest"->"最新发布";case "likes"->"最多点赞";case "distance"->"距离最近";default->"综合排序";};}
  private void updateTools(){sortButton.setText(sort.equals("distance")?"综合排序⌄":label(sort)+"⌄");sortButton.setTextColor(sort.equals("distance")?Design.INK:Design.RED);distanceButton.setTextColor(sort.equals("distance")?Design.RED:Design.INK);int count=halls.size()+(min==null?0:1)+(max==null?0:1)+(rating==null?0:1)+(type.equals("all")?0:1);filterButton.setContentDescription(count==0?"筛选":"筛选，已选"+count+"项");var icon=filterButton.getCompoundDrawables()[0];if(icon!=null)icon.mutate().setTint(count==0?Design.INK:Design.RED);}
  private void locationFinished(){locating=false;distanceButton.setEnabled(true);distanceButton.setText("距离最近");}
  private void locate(){
    if(loading||locating)return;locating=true;distanceButton.setEnabled(false);distanceButton.setText("定位中…");final boolean[] enabled={false};
    a.read(requestKey+":location-capability",null,error,()->enabled[0]=a.repo.searchDistanceAvailable(),()->{
      if(!enabled[0]){locationFinished();error.setText("食堂位置待补充，暂不能按距离排序");return;}
      a.searchLocation.request(a,position->{locationFinished();latitude=position.getLatitude();longitude=position.getLongitude();locatedAt=android.os.SystemClock.elapsedRealtime();sort="distance";load(1);},message->{locationFinished();error.setText(message);});
    },()->{if(!enabled[0])locationFinished();});
  }
  private void showFilters(){
    if(loading)return;LinearLayout box=Ui.col(a),body=Ui.row(a),nav=Ui.col(a),fields=Ui.col(a);box.addView(body);body.addView(nav,new LinearLayout.LayoutParams(Ui.dp(a,80),-1));ScrollView scroll=new ScrollView(a);scroll.addView(fields);body.addView(scroll,new LinearLayout.LayoutParams(0,Ui.dp(a,300),1));
    LinearLayout hallBox=Ui.col(a),priceBox=Ui.col(a),ratingBox=Ui.col(a),typeBox=Ui.col(a);fields.addView(hallBox);fields.addView(priceBox);fields.addView(ratingBox);fields.addView(typeBox);priceBox.setVisibility(View.GONE);ratingBox.setVisibility(View.GONE);typeBox.setVisibility(View.GONE);
    var chosen=new ArrayList<>(halls);var checks=new ArrayList<CheckBox>();for(var hall:availableHalls){CheckBox check=new CheckBox(a);check.setText(hall.name());check.setTextSize(13);check.setTextColor(Design.INK);check.setChecked(chosen.contains(hall.id()));check.setOnCheckedChangeListener((v,selected)->{if(selected){if(!chosen.contains(hall.id()))chosen.add(hall.id());}else chosen.remove(hall.id());});checks.add(check);hallBox.addView(check);}if(availableHalls.isEmpty())hallBox.addView(Ui.text(a,"食堂列表尚未加载，请稍后重试",13,Design.MUTED));
    EditText low=Ui.field(priceBox,"最低价",false,false),high=Ui.field(priceBox,"最高价",false,false);low.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);high.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);low.setHint("不限");high.setHint("不限");low.setText(min==null?"":min.toPlainString());high.setText(max==null?"":max.toPlainString());
    RadioGroup ratings=new RadioGroup(a);ratingBox.addView(ratings);String[] ratingValues={"","3.0","3.5","4.0","4.5"};for(int i=0;i<ratingValues.length;i++){RadioButton radio=new RadioButton(a);radio.setId(View.generateViewId());radio.setText(i==0?"不限":ratingValues[i]+"分");radio.setTextColor(Design.INK);ratings.addView(radio);if((rating==null&&i==0)||(rating!=null&&i>0&&rating.compareTo(new BigDecimal(ratingValues[i]))==0))ratings.check(radio.getId());}
    RadioGroup types=new RadioGroup(a);typeBox.addView(types);String[] typeValues={"all","original","repost"},typeNames={"全部","原创","转发"};for(int i=0;i<typeValues.length;i++){RadioButton radio=new RadioButton(a);radio.setId(View.generateViewId());radio.setText(typeNames[i]);radio.setTextColor(Design.INK);types.addView(radio);if(type.equals(typeValues[i]))types.check(radio.getId());}
    var sections=new LinkedHashMap<String,View>();sections.put("食堂",hallBox);if(tab.equals("dishes")){sections.put("价格",priceBox);sections.put("评分",ratingBox);}else sections.put("笔记类型",typeBox);sections.forEach((label,view)->nav.addView(small(label,()->sections.values().forEach(target->target.setVisibility(target==view?View.VISIBLE:View.GONE)))));
    TextView failure=Ui.text(a,"",13,Design.RED);box.addView(failure);LinearLayout actions=Ui.row(a);box.addView(actions);actions.addView(small("重置",()->{chosen.clear();checks.forEach(v->v.setChecked(false));low.setText("");high.setText("");ratings.check(ratings.getChildAt(0).getId());types.check(types.getChildAt(0).getId());failure.setText("");}),new LinearLayout.LayoutParams(0,-2,1));actions.addView(small("完成",()->{try{BigDecimal lower=amount(low),upper=amount(high);if(lower!=null&&upper!=null&&lower.compareTo(upper)>0)throw new IllegalArgumentException("最低价格不能高于最高价格");min=lower;max=upper;rating=selectedIndex(ratings)==0?null:new BigDecimal(ratingValues[selectedIndex(ratings)]);type=typeValues[selectedIndex(types)];halls.clear();halls.addAll(chosen);dialog.dismiss();load(1);}catch(IllegalArgumentException problem){failure.setText(problem.getMessage());}}),new LinearLayout.LayoutParams(0,-2,1));
    dialog=new AlertDialog.Builder(a).setTitle("筛选").setView(box).create();dialog.show();
  }
  private int selectedIndex(RadioGroup group){for(int i=0;i<group.getChildCount();i++)if(group.getChildAt(i).getId()==group.getCheckedRadioButtonId())return i;return 0;}
  private BigDecimal amount(EditText field){String value=field.getText().toString().strip();if(value.isEmpty())return null;try{BigDecimal amount=new BigDecimal(value);if(amount.signum()<0)throw new IllegalArgumentException("价格不能为负数");return amount;}catch(NumberFormatException e){throw new IllegalArgumentException("价格格式不正确");}}
}
