package cn.edu.bjtu.foodtime.presentation;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/** Wrap actions instead of shrinking touch targets or clipping enlarged system text. */
final class ReviewActionRow extends ViewGroup {
  ReviewActionRow(Context context) { super(context); }
  @Override protected void onMeasure(int widthSpec,int heightSpec) {
    int width=MeasureSpec.getSize(widthSpec),x=0,y=0,line=0,gap=Ui.dp(getContext(),8);
    for(int i=0;i<getChildCount();i++) {
      View child=getChildAt(i);if(child.getVisibility()==GONE) continue;
      child.measure(MeasureSpec.makeMeasureSpec(width,MeasureSpec.AT_MOST),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));
      if(x>0&&x+child.getMeasuredWidth()>width) {y+=line;x=0;line=0;}
      x+=child.getMeasuredWidth()+gap;line=Math.max(line,child.getMeasuredHeight());
    }
    setMeasuredDimension(resolveSize(width,widthSpec),resolveSize(y+line,heightSpec));
  }
  @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom) {
    int width=right-left,x=0,y=0,line=0,gap=Ui.dp(getContext(),8);
    for(int i=0;i<getChildCount();i++) {
      View child=getChildAt(i);if(child.getVisibility()==GONE) continue;
      if(x>0&&x+child.getMeasuredWidth()>width) {y+=line;x=0;line=0;}
      child.layout(x,y,x+child.getMeasuredWidth(),y+child.getMeasuredHeight());
      x+=child.getMeasuredWidth()+gap;line=Math.max(line,child.getMeasuredHeight());
    }
  }
  @Override protected LayoutParams generateDefaultLayoutParams() {return new LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT);}
}
