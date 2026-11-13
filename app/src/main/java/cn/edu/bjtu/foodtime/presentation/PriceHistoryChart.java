package cn.edu.bjtu.foodtime.presentation;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.TextView;
import cn.edu.bjtu.foodtime.domain.model.PriceHistory;

/** Canvas is a renderer only; units, gaps and duplicate observations are resolved by PriceHistory. */
final class PriceHistoryChart extends View {
  private final PriceHistory.Run run;private final TextView detail;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
  private int selected;private float downX,downY,left,right,top,bottom;private double maximum;
  PriceHistoryChart(Context context,PriceHistory.Run run,TextView detail){super(context);this.run=run;this.detail=detail;setFocusable(true);setFocusableInTouchMode(true);setClickable(true);setContentDescription("价格阶梯折线图，点击选择记录，左右方向键切换");describe();}
  private void describe(){var row=run.points().get(selected).entry();detail.setText(SubmissionScreen.date(row.recordedAt())+" · "+PriceHistory.price(row.price(),row.priceUnit())+(row.carriedForward()?"（沿用区间开始前的记录）":""));}
  private float x(long time){return left+(float)((double)(time-run.start())/Math.max(1,run.end()-run.start())*(right-left));}
  private float y(double price){return bottom-(float)(price/maximum*(bottom-top));}
  @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float density=getResources().getDisplayMetrics().density;
    paint.setTextSize(14*getResources().getDisplayMetrics().scaledDensity);maximum=Math.max(1,run.points().stream().mapToDouble(v->v.entry().price().doubleValue()).max().orElse(1))*1.1;
    left=Math.min(getWidth()*.42f,Math.max(44*density,Math.max(paint.measureText(axis(0)),Math.max(paint.measureText(axis(maximum/2)),paint.measureText(axis(maximum))))+8*density));right=getWidth()-12*density;top=20*density;bottom=getHeight()-34*density;
    paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(density);paint.setColor(Design.BORDER);
    for(int i=0;i<3;i++){float yy=y(maximum*i/2);canvas.drawLine(left,yy,right,yy,paint);paint.setStyle(Paint.Style.FILL);paint.setColor(Design.MUTED);paint.setTextAlign(Paint.Align.RIGHT);canvas.drawText(axis(maximum*i/2),left-8*density,yy+4*density,paint);paint.setStyle(Paint.Style.STROKE);paint.setColor(Design.BORDER);}
    Path path=new Path();int index=0;for(var point:run.points()){float xx=x(point.time()),yy=y(point.entry().price().doubleValue());if(index++==0)path.moveTo(xx,yy);else{path.lineTo(xx,y(run.points().get(index-2).entry().price().doubleValue()));path.lineTo(xx,yy);}}
    path.lineTo(right,y(run.points().get(run.points().size()-1).entry().price().doubleValue()));paint.setStyle(Paint.Style.STROKE);paint.setColor(Design.RED);paint.setStrokeWidth(2.5f*density);canvas.drawPath(path,paint);
    paint.setStyle(Paint.Style.FILL);for(var point:run.points())canvas.drawCircle(x(point.time()),y(point.entry().price().doubleValue()),4*density,paint);
    var point=run.points().get(selected);paint.setColor(Design.PAPER);canvas.drawCircle(x(point.time()),y(point.entry().price().doubleValue()),7*density,paint);paint.setStyle(Paint.Style.STROKE);paint.setColor(Design.RED);canvas.drawCircle(x(point.time()),y(point.entry().price().doubleValue()),7*density,paint);
    paint.setStyle(Paint.Style.FILL);paint.setColor(Design.MUTED);paint.setTextAlign(Paint.Align.LEFT);canvas.drawText(date(run.start()),left,getHeight()-8*density,paint);paint.setTextAlign(Paint.Align.RIGHT);canvas.drawText(date(run.end()),right,getHeight()-8*density,paint);
  }
  private String axis(double value){return value>=10000?new java.math.BigDecimal(value>=100000000?value/100000000:value/10000).setScale(2,java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()+(value>=100000000?"亿":"万"):String.format(java.util.Locale.ROOT,"%.2f",value);}
  private String date(long time){return java.time.Instant.ofEpochMilli(time).atZone(java.time.ZoneId.of("Asia/Shanghai")).toLocalDate().toString();}
  @Override public boolean onTouchEvent(MotionEvent event){if(event.getActionMasked()==MotionEvent.ACTION_DOWN){downX=event.getX();downY=event.getY();return true;}if(event.getActionMasked()==MotionEvent.ACTION_UP){if(Math.hypot(event.getX()-downX,event.getY()-downY)<ViewConfiguration.get(getContext()).getScaledTouchSlop()){int best=0;for(int i=0;i<run.points().size();i++)if(Math.abs(x(run.points().get(i).time())-event.getX())<=Math.abs(x(run.points().get(best).time())-event.getX()))best=i;selected=best;describe();invalidate();performClick();}return true;}return true;}
  @Override public boolean performClick(){super.performClick();return true;}
  @Override public boolean onKeyDown(int key,KeyEvent event){if(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT){selected=Math.max(0,Math.min(run.points().size()-1,selected+(key==KeyEvent.KEYCODE_DPAD_LEFT?-1:1)));describe();invalidate();return true;}return super.onKeyDown(key,event);}
}
