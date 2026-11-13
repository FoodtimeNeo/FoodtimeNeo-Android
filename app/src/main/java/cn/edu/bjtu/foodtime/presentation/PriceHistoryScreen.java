package cn.edu.bjtu.foodtime.presentation;

import android.app.AlertDialog;
import cn.edu.bjtu.foodtime.domain.validation.Validation;
import android.text.*;
import android.view.View;
import android.widget.*;
import cn.edu.bjtu.foodtime.domain.model.PriceHistory;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

final class PriceHistoryScreen {
  private final MainActivity a;private final String dish,actor;private final LinearLayout page,rows,ranges;
  private final TextView error,refresh;private PriceHistory.Snapshot data;private int days,shown=20;private boolean busy,loading,dialogOpen;
  private PriceHistoryScreen(MainActivity a,LinearLayout page,String dish){this.a=a;this.page=page;this.dish=dish;actor=a.repo.currentUser().id();days=a.drafts.getInt(a.draftKey("price-history-days"),0);
    ranges=Ui.row(a);rows=Ui.col(a);error=Ui.text(a,"",14,Design.RED);Ui.observeError(error);
    refresh=Ui.button(a,"刷新价格记录",false,this::load);refresh.setTag("price-history-refresh");
    for(int value:new int[]{0,30,90}){TextView button=Ui.button(a,value==0?"全部":"近"+value+"天",false,()->{if(!busy&&!loading&&!dialogOpen){days=value;a.drafts.putInt(a.draftKey("price-history-days"),value);load();}});button.setPadding(Ui.dp(a,4),Ui.dp(a,8),Ui.dp(a,4),Ui.dp(a,8));button.setTag("price-range:"+value);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);if(ranges.getChildCount()>0)lp.leftMargin=Ui.dp(a,8);ranges.addView(button,lp);}
  }
  static void show(MainActivity a,LinearLayout page,MainActivity.Route route){a.heading(page,"价格手账","价格变化","系统记录时间 · 北京时间");page.addView(Ui.text(a,"起点不代表开始售卖日期。相邻同价合并直线，单位变化或价格未知时分段。",14,Design.MUTED));var screen=new PriceHistoryScreen(a,page,route.id());Ui.gap(page,12);page.addView(screen.ranges);page.addView(screen.error);Ui.gap(page,8);page.addView(screen.refresh);Ui.gap(page,12);page.addView(screen.rows);screen.load();}
  private boolean current(){var user=a.repo.currentUser();return rows.isAttachedToWindow()&&user!=null&&actor.equals(user.id());}
  private void controls(){boolean enabled=!busy&&!loading&&!dialogOpen;refresh.setEnabled(enabled);for(int i=0;i<ranges.getChildCount();i++){View item=ranges.getChildAt(i);item.setEnabled(enabled);boolean selected=item.getTag().equals("price-range:"+days);item.setSelected(selected);item.setBackground(Ui.ripple(a,selected?Design.RED:Design.PAPER,Design.INK,16));((TextView)item).setTextColor(selected?Design.ON_RED:Design.INK);item.setAlpha(enabled?1f:.4f);}enable(rows,enabled);}
  private static void enable(View view,boolean value){view.setEnabled(value);if(view instanceof android.view.ViewGroup group)for(int i=0;i<group.getChildCount();i++)enable(group.getChildAt(i),value);}
  private void load(){if(busy||loading)return;loading=true;shown=20;rows.removeAllViews();rows.addView(Ui.text(a,"正在读取价格记录…",14,Design.MUTED));controls();var value=new AtomicReference<PriceHistory.Snapshot>();a.read("dish-prices:"+dish+":"+UUID.randomUUID(),null,error,()->value.set(a.repo.priceHistory(dish,days)),()->{if(current()){data=value.get();a.requireFreshContent("detail",dish);paint();}},()->{if(current()){loading=false;rows.removeAllViews();controls();}});}
  private void paint(){rows.removeAllViews();rows.addView(Ui.title(a,data.dishName(),22));rows.addView(Ui.text(a,"当前 "+PriceHistory.price(data.currentPrice(),data.priceUnit()),20,Design.RED));Ui.gap(rows,12);
    List<PriceHistory.Run> runs=PriceHistory.runs(data);for(var run:runs){LinearLayout card=Ui.card(rows);card.addView(Ui.text(a,"价格（元"+(run.unit().equals("unspecified")?"，单位未注明":"/"+PriceHistory.unit(run.unit()))+"）",15,Design.INK));TextView detail=Ui.text(a,"",14,Design.MUTED);detail.setTag("price-history-selection");detail.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);var chart=new PriceHistoryChart(a,run,detail);chart.setTag("price-history-chart");card.addView(chart,new LinearLayout.LayoutParams(-1,Ui.dp(a,260)));card.addView(detail);}
    if(runs.isEmpty())Ui.empty(rows,"暂无可绘制的价格","价格未知时不会按零元绘制。");
    if(data.records().size()==1)rows.addView(Ui.text(a,"当前仅有一条有效记录，暂无涨跌变化。",14,Design.MUTED));
    rows.addView(Ui.title(a,"记录明细",20));var reverse=new ArrayList<>(data.records());Collections.reverse(reverse);for(var row:reverse.subList(0,Math.min(shown,reverse.size())))record(row);
    if(shown<reverse.size())Ui.action(rows,"显示更早记录",false,()->{shown+=20;paint();});controls();
  }
  private void record(PriceHistory.Entry entry){LinearLayout card=Ui.card(rows);card.setTag("price-history-entry:"+entry.id());card.addView(Ui.text(a,PriceHistory.price(entry.price(),entry.priceUnit()),17,Design.INK));card.addView(Ui.text(a,SubmissionScreen.date(entry.recordedAt())+(entry.origin().equals("baseline")?" · 上线起点":"")+(entry.carriedForward()?" · 区间前沿用":""),13,Design.MUTED));
    if(data.canManage()){LinearLayout actions=Ui.row(a);TextView edit=Ui.button(a,"更正",false,()->editor(entry,false));edit.setTag("price-correct:"+entry.id());actions.addView(edit,new LinearLayout.LayoutParams(0,-2,1));if(data.totalRecords()>1){TextView remove=Ui.button(a,"删除",false,()->editor(entry,true));remove.setTag("price-delete:"+entry.id());LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.leftMargin=Ui.dp(a,12);actions.addView(remove,lp);}card.addView(actions);}
  }
  private void editor(PriceHistory.Entry entry,boolean remove){if(busy||loading||dialogOpen||!current()||!data.canManage())return;dialogOpen=true;controls();long revision=data.revision();boolean latest=entry.id().equals(data.records().get(data.records().size()-1).id());LinearLayout form=Ui.col(a);form.setPadding(Ui.dp(a,20),Ui.dp(a,12),Ui.dp(a,20),0);
    form.addView(Ui.text(a,SubmissionScreen.date(entry.recordedAt())+" · "+PriceHistory.price(entry.price(),entry.priceUnit()),14,Design.MUTED));form.addView(Ui.text(a,latest?"这会同步菜品当前价格和单位；删除将恢复为剩余最新记录。":"此操作只更正历史，当前价格保持原值。",14,Design.RED));
    EditText price=remove?null:Ui.field(form,"更正金额（元，留空为未知）",false,false);Spinner unit=remove?null:PriceUnits.field(a,form,entry.priceUnit());if(price!=null){price.setText(entry.price()==null?"":entry.price().toPlainString());price.setInputType(8194);}else form.addView(Ui.text(a,"删除后用户不再看到该值，内部操作审计保留。",14,Design.INK));
    TextView failure=Ui.text(a,"",14,Design.RED);Ui.observeError(failure);form.addView(failure);TextView reload=Ui.button(a,"刷新记录",false,()->{});reload.setVisibility(View.GONE);form.addView(reload);
    ScrollView scroll=new ScrollView(a);scroll.addView(form);
    var dialog=new AlertDialog.Builder(a).setTitle(remove?"删除价格记录":"更正价格记录（保留原时间）").setView(scroll).setNegativeButton("取消",null).setPositiveButton("确认",null).create();
    reload.setOnClickListener(v->{if(!busy){dialog.dismiss();load();}});
    View.OnAttachStateChangeListener lifecycle=new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View view){}public void onViewDetachedFromWindow(View view){dialog.dismiss();}};
    rows.addOnAttachStateChangeListener(lifecycle);
    dialog.setOnDismissListener(v->{rows.removeOnAttachStateChangeListener(lifecycle);dialogOpen=false;controls();});
    failure.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){}public void afterTextChanged(Editable text){if(text.length()>0){busy=false;reload.setVisibility(View.VISIBLE);dialog.setCancelable(true);dialog.getButton(-2).setEnabled(true);enable(form,true);}}});
    dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(clicked->{if(busy||a.operationInFlight()||!current())return;BigDecimal amount=null;try{if(!remove&&!price.getText().toString().strip().isEmpty())amount=Validation.price(price.getText().toString());}catch(RuntimeException invalid){failure.setText("请输入有效的非负金额，最多两位小数");return;}BigDecimal value=amount;String wire=remove?null:PriceUnits.value(unit);busy=true;enable(form,false);dialog.setCancelable(false);dialog.getButton(-2).setEnabled(false);a.request(dialog.getButton(-1),failure,()->{var owner=a.repo.currentUser();if(owner==null||!actor.equals(owner.id()))throw new IllegalStateException("账号已变化，请重新打开记录");if(remove)a.repo.deletePriceHistory(dish,entry.id(),revision);else a.repo.correctPriceHistory(dish,entry.id(),value,wire,revision);},()->{if(current()){busy=false;dialog.dismiss();a.message("价格记录已更新");load();}});}));dialog.show();
  }
}
