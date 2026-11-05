package cn.edu.bjtu.foodtime.presentation;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import cn.edu.bjtu.foodtime.domain.model.Models.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** The complete 30-day server snapshot supplies both chart ranges and every summary. */
final class AdminDashboardScreen {
  private static final String[] METRICS={"用户总量","新增用户","评论互动","社区食记","日活用户"};
  private static final String[] UNITS={"人","人","条","篇","人"};
  private static final String OWNER="dashboard:owner",DAYS="dashboard:days",METRIC="dashboard:metric";
  private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("yyyy/MM/dd",Locale.CHINA);

  static void show(MainActivity a,LinearLayout page,MainActivity.Route route) {
    User actor=a.repo.currentUser();
    if(actor==null||!actor.active()||!actor.role().equals("superadmin"))throw new IllegalStateException("需要平台管理员权限");
    AdminDashboard data=a.repo.adminDashboard();
    if(data==null||data.trend().size()!=30) {placeholder(a,page);return;}
    String owner=actor.id()+":"+actor.role();
    if(!owner.equals(a.drafts.getString(OWNER,""))) {
      a.drafts.putString(OWNER,owner);a.drafts.putInt(DAYS,7);a.drafts.putInt(METRIC,0);
    }
    page.setBackgroundColor(Design.PAPER);
    a.heading(page,"","数据看板","");
    LinearLayout meta=Ui.col(a);
    String updated=DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm",Locale.CHINA).withZone(ZoneId.of("Asia/Shanghai")).format(Instant.ofEpochMilli(data.generatedAt()));
    meta.addView(Ui.text(a,"更新于 "+updated+"（北京时间）",12,Design.MUTED));Ui.gap(meta,8);
    TextView refresh=Ui.button(a,"刷新数据",false,()->{if(!a.isPageLoading())a.render();});
    refresh.setTag("dashboard-refresh");refresh.setContentDescription("刷新数据");refresh.setMinHeight(Ui.dp(a,48));
    meta.addView(refresh);page.addView(meta);Ui.gap(page,14);
    overview(a,page,data);Ui.gap(page,22);
    page.addView(Ui.title(a,"平台总览",22));Ui.gap(page,12);
    metricGrid(a,page,data);Ui.gap(page,22);
    trend(a,page,data);Ui.gap(page,22);
    activityTable(a,page,data);Ui.gap(page,28);
  }

  /** Static shell while the first statistics snapshot is still on its way. */
  private static void placeholder(MainActivity a,LinearLayout page) {
    page.setBackgroundColor(Design.PAPER);
    a.heading(page,"","数据看板","");
    boolean loading=a.isPageLoading();
    LinearLayout meta=Ui.col(a);
    meta.addView(Ui.text(a,loading?"正在汇总平台数据…":a.pageFailed()?"数据加载失败，请重试":"暂无完整统计快照",12,Design.MUTED));
    Ui.gap(meta,8);
    TextView refresh=Ui.button(a,loading?"加载中…":"刷新数据",false,()->{if(!a.isPageLoading())a.render();});
    refresh.setTag("dashboard-refresh");refresh.setContentDescription("刷新数据");refresh.setMinHeight(Ui.dp(a,48));
    refresh.setEnabled(!loading);meta.addView(refresh);page.addView(meta);Ui.gap(page,14);
    LinearLayout total=card(a,Design.ALT,16);
    total.addView(Ui.text(a,"总用户量",14,Design.INK));Ui.gap(total,6);
    TextView totalValue=Ui.title(a,"—",38);totalValue.setTextColor(Design.RED);total.addView(totalValue);Ui.gap(total,6);
    total.addView(Ui.text(a,"正在读取服务端统计快照",12,Design.MUTED));page.addView(total);Ui.gap(page,12);
    LinearLayout online=card(a,Design.ALT,16);
    online.addView(Ui.text(a,"当前在线",14,Design.INK));Ui.gap(online,6);
    TextView onlineValue=Ui.title(a,"—",38);onlineValue.setTextColor(Design.GREEN);online.addView(onlineValue);Ui.gap(online,6);
    online.addView(Ui.text(a,"数据就绪后自动更新，无需停留在加载页",12,Design.MUTED));page.addView(online);
  }

  private static LinearLayout card(MainActivity a,int color,int padding) {
    LinearLayout card=Ui.col(a);int space=Ui.dp(a,padding);
    card.setPadding(space,space,space,space);card.setBackground(Ui.shape(a,color,Design.BORDER,0));return card;
  }
  private static void overview(MainActivity a,LinearLayout page,AdminDashboard data) {
    LinearLayout total=card(a,Design.ALT,16);
    total.addView(Ui.text(a,"总用户量",14,Design.INK));Ui.gap(total,6);
    TextView totalValue=Ui.title(a,format(data.totalUsers()),38);totalValue.setTextColor(Design.RED);totalValue.setTag("dashboard-total-users");total.addView(totalValue);
    Ui.gap(total,6);total.addView(Ui.text(a,"全部已注册账号，含停用账号",12,Design.MUTED));page.addView(total);Ui.gap(page,12);
    LinearLayout online=card(a,Design.ALT,16);online.addView(Ui.text(a,"当前在线",14,Design.INK));Ui.gap(online,6);
    TextView value=Ui.title(a,format(data.onlineUsers()),38);value.setTextColor(Design.GREEN);online.addView(value);Ui.gap(online,6);
    online.addView(Ui.text(a,"最近 5 分钟活跃用户 · 占有效用户 "+percent(data.onlineUsers(),data.registeredUsers()),12,Design.MUTED));page.addView(online);
  }
  private static void metricGrid(MainActivity a,LinearLayout page,AdminDashboard data) {
    String[] labels={"有效用户","正式点评","正式回复","已发布食记","食堂","档口","菜品","在线用户"};
    String[] descriptions={"当前启用账号","正式菜品点评","菜品点评回复","审核通过且未删除","当前有效食堂","当前有效档口","当前有效菜品","最近 5 分钟活跃"};
    long[] values={data.registeredUsers(),data.reviews(),data.replies(),data.posts(),data.halls(),data.stalls(),data.dishes(),data.onlineUsers()};
    for(int row=0;row<4;row++) {
      LinearLayout line=Ui.row(a);line.setGravity(Gravity.TOP);
      for(int column=0;column<2;column++) {
        int index=row*2+column;LinearLayout cell=card(a,Design.PAPER,12);
        cell.addView(Ui.text(a,labels[index],14,Design.INK));Ui.gap(cell,5);
        TextView value=Ui.title(a,format(values[index]),26);value.setTag("dashboard-metric-"+index);cell.addView(value);Ui.gap(cell,5);
        cell.addView(Ui.text(a,descriptions[index],12,Design.MUTED));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1);
        if(column==0)params.rightMargin=Ui.dp(a,6);else params.leftMargin=Ui.dp(a,6);line.addView(cell,params);
      }
      page.addView(line);if(row<3)Ui.gap(page,12);
    }
  }

  private static TextView choice(MainActivity a,String label,Runnable action) {
    TextView view=Ui.text(a,label,14,Design.INK);view.setGravity(Gravity.CENTER);
    view.setPadding(Ui.dp(a,6),Ui.dp(a,8),Ui.dp(a,6),Ui.dp(a,8));view.setMinHeight(Ui.dp(a,48));
    view.setClickable(true);view.setFocusable(true);view.setOnClickListener(ignored->action.run());return view;
  }
  private static void selected(MainActivity a,TextView button,boolean active) {
    button.setSelected(active);button.setTextColor(active?Design.PAPER:Design.INK);
    button.setBackground(Ui.ripple(a,active?Design.INK:Design.ALT,Design.BORDER,0));
    button.setContentDescription(button.getText()+(active?"，已选中":""));
  }
  private static void trend(MainActivity a,LinearLayout page,AdminDashboard data) {
    page.addView(Ui.title(a,"运营数据",22));Ui.gap(page,8);
    TextView dates=Ui.text(a,"",12,Design.MUTED);dates.setTag("dashboard-date-range");page.addView(dates);Ui.gap(page,10);
    LinearLayout ranges=Ui.row(a);TextView[] rangeButtons=new TextView[2];int[] rangesInDays={7,30};
    LinearLayout section=card(a,Design.PAPER,12);
    TextView title=Ui.title(a,"",22);title.setTag("dashboard-range-title");section.addView(title);Ui.gap(section,12);
    LinearLayout tabs=Ui.col(a);TextView[] metrics=new TextView[METRICS.length];
    TextView detail=Ui.text(a,"",13,Design.MUTED);detail.setTag("dashboard-point-detail");detail.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    TrendChart chart=new TrendChart(a,detail);chart.setTag("dashboard-chart");
    Runnable render=()->{
      int days=a.drafts.getInt(DAYS,7),metric=a.drafts.getInt(METRIC,0);
      if(days!=7&&days!=30)days=7;if(metric<0||metric>=METRICS.length)metric=0;
      List<DashboardPoint> points=data.trend(days);
      title.setText("近 "+days+" 日趋势");dates.setText(DATE.format(points.get(0).day())+" — "+DATE.format(points.get(points.size()-1).day())+"\n北京时间，含今日");
      for(int index=0;index<rangeButtons.length;index++)selected(a,rangeButtons[index],days==rangesInDays[index]);
      for(int index=0;index<metrics.length;index++)selected(a,metrics[index],metric==index);
      chart.setData(points,metric,days);
    };
    for(int index=0;index<rangesInDays.length;index++) {
      int days=rangesInDays[index];TextView button=choice(a,"近 "+days+" 日",()->{a.drafts.putInt(DAYS,days);render.run();});
      button.setTag("dashboard-range-"+days);rangeButtons[index]=button;
      LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1);if(index==0)params.rightMargin=Ui.dp(a,8);ranges.addView(button,params);
    }
    page.addView(ranges);Ui.gap(page,12);
    for(int row=0;row<2;row++) {
      LinearLayout line=Ui.row(a);int start=row*3,end=Math.min(METRICS.length,start+3);
      for(int index=start;index<end;index++) {
        int metric=index;TextView button=choice(a,METRICS[index],()->{a.drafts.putInt(METRIC,metric);render.run();});
        button.setTag("dashboard-metric-tab-"+metric);metrics[index]=button;
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1);if(index<end-1)params.rightMargin=Ui.dp(a,6);line.addView(button,params);
      }
      tabs.addView(line);if(row==0)Ui.gap(tabs,6);
    }
    section.addView(tabs);Ui.gap(section,10);
    section.addView(chart,new LinearLayout.LayoutParams(-1,Ui.dp(a,260)));Ui.gap(section,4);section.addView(detail);
    render.run();page.addView(section);Ui.gap(page,10);
    page.addView(Ui.text(a,"今日数据截至本次统计时间；点评互动为投稿与回复之和，日活为当日行为去重人数。新增记录含待审等状态，累计内容仅计正式发布。",12,Design.MUTED));
  }

  private static void activityTable(MainActivity a,LinearLayout page,AdminDashboard data) {
    page.addView(Ui.title(a,"新增汇总",22));Ui.gap(page,6);
    page.addView(Ui.text(a,"截至 "+DATE.format(data.trend().get(data.trend().size()-1).day())+"，按北京时间统计",12,Design.MUTED));Ui.gap(page,12);
    LinearLayout table=card(a,Design.PAPER,8);table.setTag("dashboard-summary");
    table.addView(summaryRow(a,new String[]{"内容类别","今日\n新增","近 7 日\n新增","近 30 日\n新增"},true,"header"));
    DashboardAdditions today=data.additions(1),week=data.additions(7),month=data.additions(30);
    String[] names={"注册用户","菜品点评","互动回复","社区食记"},keys={"users","reviews","replies","posts"};
    long[][] values={{today.newUsers(),week.newUsers(),month.newUsers()},{today.newReviews(),week.newReviews(),month.newReviews()},
      {today.newReplies(),week.newReplies(),month.newReplies()},{today.newPosts(),week.newPosts(),month.newPosts()}};
    for(int index=0;index<names.length;index++) {
      View line=new View(a);line.setBackgroundColor(Design.BORDER);table.addView(line,new LinearLayout.LayoutParams(-1,Ui.dp(a,1)));
      table.addView(summaryRow(a,new String[]{names[index],"+"+format(values[index][0]),"+"+format(values[index][1]),"+"+format(values[index][2])},false,keys[index]));
    }
    page.addView(table);
  }
  private static LinearLayout summaryRow(MainActivity a,String[] values,boolean header,String key) {
    LinearLayout row=Ui.row(a);row.setGravity(Gravity.CENTER_VERTICAL);
    if(header)row.setBackgroundColor(Design.ALT);
    for(int index=0;index<values.length;index++) {
      TextView cell=Ui.text(a,values[index],header?12:14,Design.INK);cell.setGravity(index==0?Gravity.START:Gravity.END);
      cell.setPadding(Ui.dp(a,4),Ui.dp(a,12),Ui.dp(a,4),Ui.dp(a,12));
      cell.setTag("dashboard-summary-"+key+"-"+index);row.addView(cell,new LinearLayout.LayoutParams(0,-2,1));
    }
    return row;
  }
  private static String format(long value){return String.format(Locale.CHINA,"%,d",value);}
  private static String percent(long value,long total){return total<=0?"0%":Math.round(value*100.0/total)+"%";}
  static void refreshing(LinearLayout page,boolean busy) {
    if(page==null)return;View action=page.findViewWithTag("dashboard-refresh");
    if(action instanceof TextView button){button.setEnabled(!busy);button.setText(busy?"刷新中…":"刷新数据");}
  }

  private static final class TrendChart extends View {
    private List<DashboardPoint> points=List.of();
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path line=new Path(),area=new Path();
    private final TextView detail;
    private final DateTimeFormatter day=DateTimeFormatter.ofPattern("M/d",Locale.CHINA);
    private int metric,days,selectedIndex;
    private float plotLeft,plotRight;
    TrendChart(MainActivity a,TextView detail){super(a);this.detail=detail;setFocusable(true);setClickable(true);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);}
    void setData(List<DashboardPoint> data,int metric,int days){this.points=data;this.metric=metric;this.days=days;select(points.size()-1);}
    private long value(DashboardPoint point){return switch(metric){case 0->point.totalUsers();case 1->point.newUsers();case 2->Math.addExact(point.newReviews(),point.newReplies());case 3->point.newPosts();default->point.onlinePeak();};}
    private void select(int index){
      if(points.isEmpty())return;selectedIndex=Math.max(0,Math.min(points.size()-1,index));
      DashboardPoint point=points.get(selectedIndex);String text=DATE.format(point.day())+" · "+METRICS[metric]+" "+format(value(point))+" "+UNITS[metric];
      detail.setText(text);setContentDescription("近 "+days+" 日"+METRICS[metric]+"趋势，"+text);invalidate();
    }
    private float sp(float value){return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,value,getResources().getDisplayMetrics());}
    @Override protected void onDraw(Canvas canvas){
      super.onDraw(canvas);if(points.isEmpty())return;
      paint.setTypeface(Design.body());paint.setTextSize(sp(12));
      long high=0,low=Long.MAX_VALUE;for(DashboardPoint point:points){high=Math.max(high,value(point));low=Math.min(low,value(point));}
      double minimum=metric==0?Math.max(0,low-Math.max(1,(high-low)*.15)):0;
      double maximum=axisMaximum(Math.max(1,high-minimum));
      float left=Math.max(Ui.dp(getContext(),48),paint.measureText(format((long)Math.ceil(minimum+maximum)))+Ui.dp(getContext(),12));
      left=Math.min(left,getWidth()*.45f);float right=getWidth()-Ui.dp(getContext(),14),top=Math.max(Ui.dp(getContext(),30),sp(30)),bottom=getHeight()-Math.max(Ui.dp(getContext(),44),sp(44));
      plotLeft=left;plotRight=right;
      paint.setStyle(Paint.Style.FILL);paint.setColor(Design.MUTED);paint.setTextAlign(Paint.Align.LEFT);canvas.drawText(METRICS[metric]+"（"+UNITS[metric]+"）",left,sp(16),paint);
      int ySteps=bottom-top<sp(70)?2:4;
      for(int index=0;index<=ySteps;index++) {
        float y=bottom-(bottom-top)*index/ySteps;paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Ui.dp(getContext(),1));paint.setColor(Design.BORDER);canvas.drawLine(left,y,right,y,paint);
        paint.setStyle(Paint.Style.FILL);paint.setColor(Design.MUTED);paint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(format(Math.round(minimum+maximum*index/ySteps)),left-Ui.dp(getContext(),7),y+sp(4),paint);
      }
      line.reset();area.reset();int color=metric<=1?Design.RED:metric==2?Design.BLUE:metric==3?Design.GREEN:Design.GOLD;
      for(int index=0;index<points.size();index++) {
        float x=left+(right-left)*index/(points.size()-1),y=(float)(bottom-(bottom-top)*(value(points.get(index))-minimum)/maximum);
        if(index==0){line.moveTo(x,y);area.moveTo(x,y);}else {line.lineTo(x,y);area.lineTo(x,y);}
      }
      area.lineTo(right,bottom);area.lineTo(left,bottom);area.close();paint.setColor(color);paint.setAlpha(20);paint.setStyle(Paint.Style.FILL);canvas.drawPath(area,paint);
      paint.setAlpha(255);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Ui.dp(getContext(),2.5f));paint.setStrokeJoin(Paint.Join.ROUND);canvas.drawPath(line,paint);
      paint.setStyle(Paint.Style.FILL);for(int index=0;index<points.size();index++) {
        float x=left+(right-left)*index/(points.size()-1),y=(float)(bottom-(bottom-top)*(value(points.get(index))-minimum)/maximum);
        canvas.drawCircle(x,y,Ui.dp(getContext(),index==selectedIndex?5:days==7?4:2.5f),paint);
      }
      paint.setColor(Design.MUTED);paint.setTextSize(sp(12));int ticks=getWidth()<Ui.dp(getContext(),500)?3:days==7?7:6;
      for(int tick=0;tick<ticks;tick++) {
        int index=Math.round(tick*(points.size()-1f)/(ticks-1));float x=left+(right-left)*index/(points.size()-1);
        paint.setTextAlign(tick==0?Paint.Align.LEFT:tick==ticks-1?Paint.Align.RIGHT:Paint.Align.CENTER);canvas.drawText(day.format(points.get(index).day()),x,bottom+sp(22),paint);
      }
      paint.setTextAlign(Paint.Align.RIGHT);canvas.drawText("日期（月/日）",right,getHeight()-sp(3),paint);
    }
    @Override public boolean onTouchEvent(MotionEvent event){
      if(event.getActionMasked()==MotionEvent.ACTION_UP){if(!points.isEmpty()&&plotRight>plotLeft)select(Math.round((event.getX()-plotLeft)/(plotRight-plotLeft)*(points.size()-1)));performClick();return true;}
      return super.onTouchEvent(event);
    }
    @Override public boolean performClick(){super.performClick();return true;}
    @Override public boolean onKeyDown(int key,KeyEvent event){
      if(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT){select(selectedIndex+(key==KeyEvent.KEYCODE_DPAD_LEFT?-1:1));return true;}return super.onKeyDown(key,event);
    }
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);}
    @Override public boolean performAccessibilityAction(int action,Bundle args){
      if(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD||action==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD){select(selectedIndex+(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD?1:-1));return true;}return super.performAccessibilityAction(action,args);
    }
    private double axisMaximum(double value){double raw=Math.max(1,value/4),magnitude=Math.pow(10,Math.floor(Math.log10(raw)));double unit=raw/magnitude;double nice=unit<=1?1:unit<=2?2:unit<=3?3:unit<=5?5:10;return Math.max(4,nice*magnitude*4);}
  }
  private AdminDashboardScreen(){}
}
