package cn.edu.bjtu.foodtime.presentation;

import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.function.IntConsumer;

/** Width-aware page window. It only emits a page number; query and request ownership stay with the screen. */
final class NumberedPagination extends LinearLayout {
  private final LinearLayout numbers,actions;
  private final IntConsumer change;
  private int page=1,total=1;
  private boolean ready;
  NumberedPagination(Context context,IntConsumer change){
    super(context);this.change=change;setOrientation(VERTICAL);setTag("numbered-pagination");
    numbers=Ui.row(context);addView(numbers,new LayoutParams(-1,-2));
    actions=Ui.row(context);LayoutParams controls=new LayoutParams(-1,-2);controls.topMargin=Ui.dp(context,8);addView(actions,controls);
    addAction("«","首页",()->move(1));addAction("‹","上一页",()->move(page-1));
    addAction("›","下一页",()->move(page+1));addAction("»","末页",()->move(total));
  }
  private void addAction(String symbol,String label,Runnable action){
    TextView item=Ui.button(getContext(),symbol,false,action);item.setContentDescription(label);item.setTag("pagination:"+label);item.setTextSize(22);
    item.setPadding(0,0,0,0);LayoutParams layout=new LayoutParams(0,Ui.dp(getContext(),48),1);
    if(actions.getChildCount()>0)layout.leftMargin=Ui.dp(getContext(),8);actions.addView(item,layout);
  }
  void state(int current,int pages,boolean enabled){page=Math.max(1,current);total=Math.max(page,pages);ready=enabled;paint();}
  private void move(int value){if(ready&&value>=1&&value<=total&&value!=page)change.accept(value);}
  @Override protected void onSizeChanged(int width,int height,int oldWidth,int oldHeight){super.onSizeChanged(width,height,oldWidth,oldHeight);if(width!=oldWidth)post(this::paint);}
  private void paint(){
    numbers.removeAllViews();int width=getWidth()-getPaddingLeft()-getPaddingRight();if(width<=0)return;
    int gap=Ui.dp(getContext(),8),minimum=Ui.dp(getContext(),44),count=Math.min(total,Math.max(1,(width+gap)/(minimum+gap))),start=1,itemWidth=minimum;
    TextView sample=Ui.text(getContext(),"",16,Design.INK);
    for(;count>=1;count--){
      start=Math.max(1,Math.min(page-count/2,total-count+1));
      itemWidth=Math.max(minimum,(int)Math.ceil(sample.getPaint().measureText(String.valueOf(start+count-1)))+Ui.dp(getContext(),20));
      if(count==1||(long)count*itemWidth+(long)(count-1)*gap<=width)break;
    }
    for(int i=0;i<count;i++){
      int value=start+i;TextView item=Ui.button(getContext(),String.valueOf(value),value==page,()->move(value));
      item.setTextSize(16);item.setPadding(0,0,0,0);item.setGravity(Gravity.CENTER);item.setSelected(value==page);
      item.setContentDescription("第 "+value+" 页"+(value==page?"，当前页":""));item.setTag("pagination:page:"+value);item.setEnabled(ready);
      LayoutParams layout=new LayoutParams(itemWidth,Ui.dp(getContext(),48));if(i>0)layout.leftMargin=gap;numbers.addView(item,layout);
    }
    actions.getChildAt(0).setEnabled(ready&&page>1);actions.getChildAt(1).setEnabled(ready&&page>1);
    actions.getChildAt(2).setEnabled(ready&&page<total);actions.getChildAt(3).setEnabled(ready&&page<total);
  }
}
