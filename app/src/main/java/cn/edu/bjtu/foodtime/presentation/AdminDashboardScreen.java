package cn.edu.bjtu.foodtime.presentation;

import android.graphics.*;
import android.view.*;
import android.widget.*;
import cn.edu.bjtu.foodtime.domain.model.Models.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Superadmin-only platform overview; data shape is ready for the future Backend endpoint. */
final class AdminDashboardScreen {
  static void show(MainActivity a,LinearLayout page,MainActivity.Route route) {
    User actor=a.repo.currentUser();
    if(actor==null||!actor.role().equals("superadmin")) throw new IllegalStateException("需要平台管理员权限");
    AdminDashboard data=a.repo.adminDashboard();
    page.setBackground(new DashboardBackdrop(a));
    a.heading(page,"","数据看板","校园里的每一份热闹，都在这里慢慢长大。");
    overview(a,page,data);Ui.gap(page,20);
    page.addView(Ui.title(a,"平台总览",24));Ui.gap(page,12);
    metricGrid(a,page,data);Ui.gap(page,22);
    trend(a,page,data);Ui.gap(page,22);
    activityTable(a,page,data);Ui.gap(page,28);
  }

  private static void overview(MainActivity a,LinearLayout page,AdminDashboard data) {
    LinearLayout hero=Ui.row(a);hero.setGravity(Gravity.CENTER_VERTICAL);
    hero.setPadding(Ui.dp(a,20),Ui.dp(a,20),Ui.dp(a,18),Ui.dp(a,20));hero.setBackground(Ui.shape(a,Design.ALT,0,26));
    LinearLayout copy=Ui.col(a);copy.addView(Ui.text(a,"当前在线",14,Design.MUTED));Ui.gap(copy,5);
    TextView online=Ui.title(a,format(data.onlineUsers()),36);online.setTextColor(Design.RED);copy.addView(online);
    Ui.gap(copy,7);copy.addView(Ui.text(a,"约占注册用户 "+percent(data.onlineUsers(),data.registeredUsers()),13,Design.GREEN));
    hero.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
    View doodle=Ui.foodDoodle(a,3,0x99c9341e);hero.addView(doodle,new LinearLayout.LayoutParams(Ui.dp(a,92),Ui.dp(a,74)));
    page.addView(hero,new LinearLayout.LayoutParams(-1,-2));
  }

  private static void metricGrid(MainActivity a,LinearLayout page,AdminDashboard d) {
    String[] labels={"注册用户","评论","回复","社区食记","食堂","档口","菜品","在线用户"};
    long[] values={d.registeredUsers(),d.reviews(),d.replies(),d.posts(),d.halls(),d.stalls(),d.dishes(),d.onlineUsers()};
    int[] colors={Design.RED,Design.BLUE,Design.GREEN,Design.RED,Design.GREEN,Design.BLUE,Design.RED,Design.GREEN};
    for(int row=0;row<4;row++) {
      LinearLayout line=Ui.row(a);line.setGravity(Gravity.TOP);
      for(int column=0;column<2;column++) {
        int index=row*2+column;LinearLayout card=Ui.col(a);
        card.setPadding(Ui.dp(a,16),Ui.dp(a,16),Ui.dp(a,14),Ui.dp(a,17));
        card.setBackground(Ui.shape(a,index%3==0?Design.ALT:Design.PAPER,Design.BORDER,22));
        TextView label=Ui.text(a,labels[index],13,Design.MUTED);card.addView(label);Ui.gap(card,9);
        TextView value=Ui.title(a,format(values[index]),27);value.setTextColor(colors[index]);card.addView(value);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);if(column==0)lp.rightMargin=Ui.dp(a,6);else lp.leftMargin=Ui.dp(a,6);line.addView(card,lp);
      }
      page.addView(line,new LinearLayout.LayoutParams(-1,-2));if(row<3)Ui.gap(page,12);
    }
  }

  private static void trend(MainActivity a,LinearLayout page,AdminDashboard data) {
    LinearLayout section=Ui.col(a);section.setPadding(Ui.dp(a,18),Ui.dp(a,18),Ui.dp(a,18),Ui.dp(a,16));section.setBackground(Ui.shape(a,Design.PAPER,Design.BORDER,24));
    LinearLayout heading=Ui.row(a);heading.addView(Ui.title(a,"近 7 日趋势",22),new LinearLayout.LayoutParams(0,-2,1));
    heading.addView(Ui.stamp(a,data.preview()?"预览":"实时",data.preview()?Design.BLUE:Design.GREEN));section.addView(heading);Ui.gap(section,14);
    TrendChart chart=new TrendChart(a,data.trend());
    LinearLayout tabs=Ui.row(a);tabs.setPadding(Ui.dp(a,3),Ui.dp(a,3),Ui.dp(a,3),Ui.dp(a,3));tabs.setBackground(Ui.shape(a,Design.KRAFT_SOFT,0,20));
    String[] labels={"新增用户","评论互动","社区食记"};TextView[] buttons=new TextView[3];int[] chosen={0};
    Runnable refresh=()->{for(int i=0;i<3;i++){boolean active=i==chosen[0];buttons[i].setTextColor(active?Design.ON_RED:Design.INK);buttons[i].setBackground(Ui.ripple(a,active?Design.RED:Color.TRANSPARENT,0,18));}chart.setMetric(chosen[0]);};
    for(int i=0;i<3;i++){int index=i;TextView button=Ui.text(a,labels[i],13,Design.INK);button.setGravity(Gravity.CENTER);button.setMinHeight(Ui.dp(a,40));button.setClickable(true);button.setFocusable(true);button.setOnClickListener(v->{chosen[0]=index;refresh.run();});buttons[i]=button;tabs.addView(button,new LinearLayout.LayoutParams(0,-2,1));}
    section.addView(tabs);Ui.gap(section,14);section.addView(chart,new LinearLayout.LayoutParams(-1,Ui.dp(a,210)));refresh.run();page.addView(section);
  }

  private static void activityTable(MainActivity a,LinearLayout page,AdminDashboard data) {
    page.addView(Ui.title(a,"今日活跃",24));Ui.gap(page,12);
    LinearLayout table=Ui.col(a);table.setPadding(Ui.dp(a,17),Ui.dp(a,16),Ui.dp(a,17),Ui.dp(a,12));table.setBackground(Ui.shape(a,Design.ALT,0,24));
    DashboardPoint today=data.trend().isEmpty()?new DashboardPoint(0,0,0,0,0,0):data.trend().get(data.trend().size()-1);
    table.addView(row(a,"内容","今日新增","累计",true));divider(a,table);
    table.addView(row(a,"注册用户",format(today.newUsers()),format(data.registeredUsers()),false));divider(a,table);
    table.addView(row(a,"评论",format(today.newReviews()),format(data.reviews()),false));divider(a,table);
    table.addView(row(a,"回复",format(today.newReplies()),format(data.replies()),false));divider(a,table);
    table.addView(row(a,"社区食记",format(today.newPosts()),format(data.posts()),false));
    page.addView(table);
  }

  private static LinearLayout row(MainActivity a,String name,String today,String total,boolean header) {
    LinearLayout row=Ui.row(a);row.setPadding(0,Ui.dp(a,10),0,Ui.dp(a,10));
    int color=header?Design.MUTED:Design.INK;float size=header?12:14;
    TextView first=Ui.text(a,name,size,color);if(!header)first.setTypeface(Design.title());row.addView(first,new LinearLayout.LayoutParams(0,-2,1.25f));
    TextView second=Ui.text(a,today,size,header?color:Design.RED);second.setGravity(Gravity.END);row.addView(second,new LinearLayout.LayoutParams(0,-2,1));
    TextView third=Ui.text(a,total,size,color);third.setGravity(Gravity.END);row.addView(third,new LinearLayout.LayoutParams(0,-2,1));return row;
  }
  private static void divider(MainActivity a,LinearLayout table){View line=new View(a);line.setBackgroundColor(0x22d5ccbc);table.addView(line,new LinearLayout.LayoutParams(-1,Ui.dp(a,1)));}
  private static String format(long value){return String.format(Locale.CHINA,"%,d",value);}
  private static String percent(long value,long total){return total<=0?"0%":Math.round(value*100.0/total)+"%";}

  private static final class TrendChart extends View {
    final List<DashboardPoint> points;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final SimpleDateFormat day=new SimpleDateFormat("M/d",Locale.CHINA);int metric;
    TrendChart(MainActivity a,List<DashboardPoint> points){super(a);this.points=points;paint.setTypeface(Design.body());setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);}
    void setMetric(int value){metric=value;setContentDescription(new String[]{"近七日新增用户趋势","近七日评论与回复趋势","近七日社区食记趋势"}[value]);invalidate();}
    long value(DashboardPoint point){return metric==0?point.newUsers():metric==1?point.newReviews()+point.newReplies():point.newPosts();}
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);if(points.isEmpty())return;float left=Ui.dp(getContext(),8),right=getWidth()-Ui.dp(getContext(),8),top=Ui.dp(getContext(),18),bottom=getHeight()-Ui.dp(getContext(),28);
      paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Ui.dp(getContext(),1));paint.setColor(0x33d5ccbc);for(int i=0;i<4;i++){float y=top+(bottom-top)*i/3f;canvas.drawLine(left,y,right,y,paint);}
      long max=1;for(DashboardPoint point:points)max=Math.max(max,value(point));Path path=new Path();
      for(int i=0;i<points.size();i++){float x=left+(right-left)*i/Math.max(1,points.size()-1);float y=bottom-(bottom-top)*value(points.get(i))/max;if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}
      paint.setColor(metric==0?Design.RED:metric==1?Design.BLUE:Design.GREEN);paint.setStrokeWidth(Ui.dp(getContext(),3));paint.setStrokeCap(Paint.Cap.ROUND);canvas.drawPath(path,paint);
      paint.setStyle(Paint.Style.FILL);for(int i=0;i<points.size();i++){float x=left+(right-left)*i/Math.max(1,points.size()-1);float y=bottom-(bottom-top)*value(points.get(i))/max;canvas.drawCircle(x,y,Ui.dp(getContext(),4),paint);}
      paint.setColor(Design.MUTED);paint.setTextSize(Ui.dp(getContext(),11));paint.setTextAlign(Paint.Align.CENTER);for(int i=0;i<points.size();i+=Math.max(1,points.size()-1)){float x=left+(right-left)*i/Math.max(1,points.size()-1);canvas.drawText(day.format(new Date(points.get(i).day())),x,getHeight()-Ui.dp(getContext(),7),paint);}
      paint.setTextAlign(Paint.Align.RIGHT);canvas.drawText(format(max),right,top-Ui.dp(getContext(),5),paint);
    }
  }

  private static final class DashboardBackdrop extends android.graphics.drawable.Drawable {
    final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final float unit;
    DashboardBackdrop(MainActivity a){unit=Ui.dp(a,120);paint.setColor(0x12c9341e);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Ui.dp(a,1.3f));}
    @Override public void draw(Canvas canvas){canvas.drawColor(Design.PAPER);Rect b=getBounds();canvas.drawCircle(b.right-unit*.25f,unit*.8f,unit*.18f,paint);canvas.drawArc(new RectF(unit*.08f,unit*4.3f,unit*.58f,unit*4.65f),0,180,false,paint);canvas.drawLine(unit*.1f,unit*4.48f,unit*.56f,unit*4.48f,paint);}
    @Override public void setAlpha(int alpha){}@Override public void setColorFilter(android.graphics.ColorFilter filter){}@Override public int getOpacity(){return android.graphics.PixelFormat.OPAQUE;}
  }
  private AdminDashboardScreen(){}
}
